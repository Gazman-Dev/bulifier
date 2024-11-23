package com.bulifier.core.api

import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.db.Content
import com.bulifier.core.db.File
import com.bulifier.core.db.FileData
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.db.ResponseItem
import com.bulifier.core.db.Schema
import com.bulifier.core.db.SchemaSettings
import com.bulifier.core.db.SchemaType
import com.bulifier.core.db.db
import com.bulifier.core.models.ApiModel
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.schemas.SchemaModel.KEY_CONTEXT
import com.bulifier.core.schemas.SchemaModel.KEY_MAIN_FILE
import com.bulifier.core.schemas.SchemaModel.KEY_PACKAGE
import com.bulifier.core.schemas.SchemaModel.KEY_PROMPT
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class AiService : LifecycleService() {

    private val responseProcessor = ResponseProcessor()

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            Prefs.projectId.flow
                .collectLatest { projectId ->
                    Log.d("AiService", "Project id: $projectId")
                    db.historyDao().getHistoryByStatuses(
                        statuses = listOf(
                            HistoryStatus.SUBMITTED,
                            HistoryStatus.RE_APPLYING
                        ),
                        projectId = projectId
                    ).collect { jobs ->
                        Log.d("AiService", "Jobs: ${jobs.size}")
                        jobs.forEach {
                            process(it)
                        }
                    }
                }
        }
    }

    private suspend fun process(historyItem: HistoryItem) {
        Log.d("AiService", "processing: ${historyItem.promptId} ${historyItem.status}")
        if (historyItem.modelId == null) {
            reportError(historyItem.promptId, "No model selected")
            return
        }

        if (db.historyDao().startProcessingHistoryItem(
                historyItem.promptId, statuses = listOf(
                    HistoryStatus.SUBMITTED,
                    HistoryStatus.RE_APPLYING
                )
            ) <= 0
        ) {
            Log.d("AiService", "Already processing or not found. Id: ${historyItem.promptId}")
            return
        }

        val filesContext = if (historyItem.contextFiles.isNotEmpty()) {
            db.fileDao().getContent(historyItem.contextFiles)
        } else {
            emptyList()
        }
        val responses = db.historyDao().getResponses(historyItem.promptId)
        try {
            process(historyItem, filesContext, responses)
        } catch (e: Exception) {
            Log.e("AiService", "Error processing history item", e)
            e.printStackTrace()
            reportError(historyItem.promptId, e.message ?: "Unknown error")
        }
    }

    private suspend fun reportError(promptId: Long, errorMessage: String) {
        db.historyDao().markError(promptId, errorMessage)
    }

    private suspend fun process(
        historyItem: HistoryItem,
        filesContext: List<FileData>,
        responses: List<ResponseItem>?
    ) {
        Log.d(
            "AiService",
            "Processing: ${historyItem.promptId} has fileContext: ${filesContext.isNotEmpty()} has responses: ${responses?.isNotEmpty()}"
        )
        val schemas = getSchemas(historyItem.schema, projectId = historyItem.projectId)
        val settings = db.schemaDao().getSettings(
            historyItem.schema,
            projectId = historyItem.projectId
        )
        Log.d("AiService", "Settings: ${historyItem.promptId}> $settings")

        val keys = schemas.flatMap { it.keys }.toSet()

        val model = withContext(Dispatchers.IO) {
            loadModel(historyItem)
        }
        val apiModel = model.createApiModel()

        val fileData =
            if (keys.contains(KEY_MAIN_FILE) && historyItem.fileName?.isNotBlank() == true) {
                db.fileDao()
                    .getContent(historyItem.path, historyItem.fileName, historyItem.projectId)
            } else null

        if (settings.runForEachFile) {
            processMultipleFiles(historyItem, settings, apiModel)
        } else {
            processSingleCall(
                responses,
                historyItem,
                settings,
                filesContext,
                fileData,
                schemas,
                apiModel
            )
        }
    }

    private suspend fun processSingleCall(
        responses: List<ResponseItem>?,
        historyItem: HistoryItem,
        settings: SchemaSettings,
        filesContext: List<FileData>,
        fileData: FileData?,
        schemas: List<Schema>,
        apiModel: ApiModel
    ) {
        val response = responses?.firstOrNull()?.run {
            Log.d("AiService", "Response: $this")
            ResponseData(historyItem, response).also {
                val newHistoryItem = historyItem.copy(status = HistoryStatus.RESPONDED)
                db.historyDao().updateHistory(newHistoryItem)
            }
        } ?: kotlin.run {
            val processedSchemas: List<Schema> = prepareSchema(
                userPrompt = historyItem.prompt,
                packageName = historyItem.path,
                filesContext = filesContext.map {
                    "file name: ${it.fileName}\n${it.content}"
                },
                fileData = fileData,
                schemas = schemas
            )
            val messages = toMessages(processedSchemas)

            sendMessages(messages, historyItem, apiModel, 1)
        }

        response?.let { data ->
            if (settings.multiFilesOutput) {
                applyMultiFileOutput(data)
            } else {
                overrideFile(fileData, data)
            }
        }
    }

    private suspend fun overrideFile(
        fileData: FileData?,
        data: ResponseData
    ) {
        fileData?.let {
            Log.d("AiService", "insertContentAndUpdateFileSize ${it.fileId}")
            val normalizeContent = normalizeContent(data.response)
            val content = it.toContent().copy(content = normalizeContent)
            val count = db.fileDao().updateContentAndFileSize(content)
            Log.d("AiService", "Count: $count File content: $normalizeContent")
        }
    }

    private suspend fun processMultipleFiles(
        historyItem: HistoryItem,
        settings: SchemaSettings,
        apiModel: ApiModel
    ) = coroutineScope {
        val files =
            db.fileDao().fetchFilesListByPathAndProjectId(
                historyItem.path,
                settings.inputExtension,
                historyItem.projectId
            )
        val progress = AtomicInteger(-1)
        updateProgress(progress, files.size, historyItem)
        files.forEach { file ->
            if (file.fileName == "update-schema.schema" && file.path == "schemas") {
                // don't mess up with the master schema
                updateProgress(progress, files.size, historyItem)
            } else {
                launch {
                    val fileData = db.fileDao().getContent(file.fileId)
                    val schemas: List<Schema> = prepareSchema(
                        userPrompt = historyItem.prompt,
                        packageName = historyItem.path,
                        fileData = fileData,
                        schemas = getSchemas(historyItem.schema, projectId = historyItem.projectId)
                    )

                    val messages = toMessages(schemas)
                    sendMessages(
                        messages,
                        historyItem,
                        apiModel,
                        file.fileId
                    )?.let { responseData ->
                        if (settings.overrideFiles) {
                            overrideFile(fileData, responseData)
                        } else {
                            insertFile(responseData, file, settings)
                        }
                        updateProgress(progress, files.size, historyItem)
                    }
                }
            }
        }
    }

    private suspend fun updateProgress(count: AtomicInteger, total: Int, historyItem: HistoryItem) {
        val progress = count.incrementAndGet() / total.toFloat()
        Log.d("AiService", "Progress: $progress")
        db.historyDao().updateProgress(historyItem.promptId, progress)
    }

    private fun loadModel(historyItem: HistoryItem) =
        QuestionsModel.deserialize(historyItem.modelId!!)
            ?: throw Error("Model not found ${historyItem.modelId}")

    private suspend fun sendMessages(
        messages: List<MessageData>,
        historyItem: HistoryItem,
        apiModel: ApiModel,
        id: Long
    ): ResponseData? = withContext(Dispatchers.IO) {
        try {
            val apiResponse =
                apiModel.sendMessage(MessageRequest(messages), historyItem, id)
            if (apiResponse.isDone) {
                if (apiResponse.message == null) {
                    db.historyDao().markError(historyItem.promptId, "No response")
                    null
                } else {
                    val responseItem = ResponseItem(
                        promptId = historyItem.promptId,
                        request = Gson().toJson(messages),
                        response = apiResponse.message
                    )

                    db.historyDao().addResponse(responseItem)

                    val newHistoryItem = historyItem.copy(status = HistoryStatus.RESPONDED)
                    db.historyDao().updateHistory(newHistoryItem)

                    ResponseData(historyItem, apiResponse.message)
                }
            }
            else{
                if (apiResponse.error) {
                    db.historyDao().markError(historyItem.promptId, apiResponse.message ?: "Internal Error")
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            db.historyDao().markError(historyItem.promptId, e.message ?: "Unknown error")

            val responseItem = ResponseItem(
                promptId = historyItem.promptId,
                request = Gson().toJson(messages),
                response = e.message ?: "Unknown error"
            )
            db.historyDao().addResponse(responseItem)
            null
        }
    }

    private suspend fun insertFile(
        responseData: ResponseData,
        file: File,
        settings: SchemaSettings
    ) {
        val projectId = responseData.historyItem.projectId
        val fileName = extractNewFileName(file.fileName, settings)

        val fileId = db.fileDao().insertFile(
            File(
                projectId = projectId,
                fileName = fileName,
                isFile = true,
                path = file.path
            )
        ).run {
            if (this == -1L) {
                db.fileDao().getFileId(
                    path = "schemas",
                    fileName = fileName,
                    projectId = projectId
                ) ?: return
            } else {
                this
            }
        }
        db.fileDao().insertContentAndUpdateFileSize(
            Content(
                fileId = fileId,
                content = removeFirstLine(responseData.response).trim() + "\n",
                type = Content.Type.RAW
            )
        )
    }

    private fun extractNewFileName(
        inputFileName: String,
        settings: SchemaSettings
    ): String {
        val baseName = inputFileName.lowercase().substringBeforeLast(".")
        return "$baseName.${settings.outputExtension}"
    }

    private fun removeFirstLine(input: String): String {
        val lines = input.trim().split("\n")
        return if (lines.size > 1) {
            lines.drop(1).joinToString("\n")
        } else {
            ""
        }
    }

    private suspend fun applyMultiFileOutput(responseData: ResponseData) {
        val projectId = responseData.historyItem.projectId
        val files = responseProcessor.parseResponse(responseData.response)
        files.forEach { item ->
            val pathParts = item.fullPath.split("/").toMutableList()
            val last = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                pathParts.removeLast()
            } else {
                pathParts.removeAt(pathParts.size - 1)
            }
            val fileName = last.run {
                if (this.endsWith(".bul")) this else split(".")[0] + ".bul"
            }

            val path = pathParts.joinToString("/")
            val fileId = db.fileDao().insertFile(
                File(
                    projectId = projectId,
                    fileName = fileName,
                    isFile = true,
                    path = path
                )
            )
            db.fileDao().insertContentAndUpdateFileSize(
                Content(
                    fileId = fileId,
                    content = item.imports.joinToString("\n") { "import $it" } + "\n\n" + item.content,
                    type = Content.Type.BULLET
                )
            )
        }
    }

    private fun normalizeContent(input: String): String {
        // Trim the input first
        var normalized = input.trim()

        // Remove code block markers with or without a language specification
        val codeBlockRegex = Regex("```(\\w+)?\\s*([\\s\\S]*?)\\s*```")
        normalized = codeBlockRegex.replace(normalized) { matchResult ->
            // The match result contains only the content within the code block
            matchResult.groups[2]?.value?.trim() ?: ""
        }

        return normalized
    }


    private fun toMessages(schemas: List<Schema>): List<MessageData> {
        return schemas.map {
            MessageData(if (it.type != SchemaType.USER) "system" else "user", it.content)
        }
    }

    private suspend fun prepareSchema(
        userPrompt: String, packageName: String,
        filesContext: List<String>? = null,
        fileData: FileData? = null,
        schemas: List<Schema>
    ) = withContext(Dispatchers.Default) {
        schemas.flatMap { schema ->
            when (schema.type) {
                SchemaType.SETTINGS -> emptyList()
                SchemaType.CONTEXT -> {
                    filesContext?.map {
                        schema.copy(
                            content = updateSchemaContent(
                                schema.content,
                                schema.keys,
                                userPrompt,
                                packageName,
                                fileData
                            )
                                .replace("{$KEY_CONTEXT}", it)
                        )
                    } ?: emptyList()
                }

                else -> {
                    listOf(
                        schema.copy(
                            content = updateSchemaContent(
                                schema.content,
                                schema.keys,
                                userPrompt,
                                packageName,
                                fileData
                            )
                        )
                    )
                }
            }
        }
    }

    private suspend fun getSchemas(name: String, projectId: Long) =
        db.schemaDao().getSchema(name, projectId)

    private fun updateSchemaContent(
        schemaText: String,
        keys: LinkedHashSet<String>,
        userPrompt: String,
        packageName: String,
        fileData: FileData?
    ): String {
        var result = schemaText
        if (keys.contains(KEY_PACKAGE)) {
            result = result.replace("{$KEY_PACKAGE}", packageName)
        }
        if (keys.contains(KEY_PROMPT)) {
            result = result.replace("{$KEY_PROMPT}", userPrompt)
        }
        if (keys.contains(KEY_MAIN_FILE)) {
            result = result.replace("{$KEY_MAIN_FILE}", fileData?.content ?: "")
        }
        return result
    }

    private data class ResponseData(
        val historyItem: HistoryItem,
        val response: String
    )
}
