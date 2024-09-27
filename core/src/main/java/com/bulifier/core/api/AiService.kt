package com.bulifier.core.api

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
import com.bulifier.core.db.SchemaType
import com.bulifier.core.db.db
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.schemas.SchemaModel.KEY_BULLET_FILE
import com.bulifier.core.schemas.SchemaModel.KEY_CONTEXT
import com.bulifier.core.schemas.SchemaModel.KEY_PACKAGE
import com.bulifier.core.schemas.SchemaModel.KEY_PROMPT
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiService : LifecycleService() {

    private val responseProcessor = ResponseProcessor()

    override fun onCreate() {
        super.onCreate()

        lifecycleScope.launch {
            Prefs.projectId.flow.collect { projectId ->
                db.historyDao().getHistoryByStatuses(
                    statuses = listOf(
                        HistoryStatus.SUBMITTED,
                        HistoryStatus.RE_APPLYING
                    ),
                    projectId = projectId
                ).collect { jobs ->
                    jobs.forEach {
                        process(it)
                    }
                }
            }
        }
    }

    private suspend fun process(historyItem: HistoryItem) {
        if (historyItem.modelId == null) {
            db.historyDao()
                .markError(historyItem.promptId, "No model selected")
            return
        }

        if (db.historyDao().startProcessingHistoryItem(
                historyItem.promptId, statuses = listOf(
                    HistoryStatus.SUBMITTED,
                    HistoryStatus.RE_APPLYING
                )
            ) == 0
        ) {
            Log.d("AiService", "Already processing or not found. Id: ${historyItem.promptId}")
            return
        }

        lifecycleScope.launch {
            val filesContext = if (historyItem.contextFiles.isNotEmpty()) {
                db.fileDao().getContent(historyItem.contextFiles)
            } else {
                emptyList()
            }
            val responses = db.historyDao().getResponses(historyItem.promptId)
            try {
                process(historyItem, filesContext, responses)
            } catch (e: Exception) {
                e.printStackTrace()
                db.historyDao()
                    .markError(historyItem.promptId, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun process(
        historyItem: HistoryItem,
        filesContext: List<FileData>,
        responses: List<ResponseItem>?
    ) {
        val schemas = getSchemas(historyItem.schema)
        val settings = db.schemaDao().getSettings(historyItem.schema)
        val keys = schemas.flatMap { it.keys }.toSet()
        val fileData =
            if (keys.contains(KEY_BULLET_FILE) && historyItem.fileName?.isNotBlank() == true) {
                db.fileDao()
                    .getContent(historyItem.path, historyItem.fileName, historyItem.projectId)
            } else null

        val response = responses?.firstOrNull()?.run {
            ResponseData(historyItem, response).also {
                val newHistoryItem = historyItem.copy(status = HistoryStatus.RESPONDED)
                db.historyDao().updateHistory(newHistoryItem)
            }
        } ?: kotlin.run {
            if (!settings.runForEachFile) {
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
                val model = loadModel(historyItem)

                sendMessages(model, messages, historyItem)
            } else null
        }

        response?.let { data ->
            if (settings.multiFilesOutput) {
                applyMultiFileOutput(data)
            } else {
                fileData?.let {
                    db.fileDao()
                        .insertContentAndUpdateFileSize(
                            it.toContent().copy(content = data.response)
                        )
                }
            }
        }
    }

    private suspend fun handleDebulify(historyItem: HistoryItem, responses: List<ResponseItem>?) =
        withContext(Dispatchers.IO) {
            val model = loadModel(historyItem)
            db.fileDao().fetchFilesListByPathAndProjectId(historyItem.path, historyItem.projectId)
                .forEach { file ->
                    val schemas: List<Schema> = prepareSchema(
                        userPrompt = historyItem.prompt,
                        packageName = historyItem.path,
                        fileData = db.fileDao().getContent(file.fileId),
                        schemas = getSchemas(name)
                    )

                    val messages = toMessages(schemas)
                    sendMessages(model, messages, historyItem)?.let { responseData ->
                        applyDebulifyResponse(responseData, file)
                    }
                }
        }

    private fun loadModel(historyItem: HistoryItem) =
        QuestionsModel.deserialize(historyItem.modelId!!)
            ?: throw Error("Model not found ${historyItem.modelId}")

    private suspend fun sendMessages(
        model: QuestionsModel,
        messages: List<MessageData>,
        historyItem: HistoryItem
    ): ResponseData? {
        val response = try {
            model.createApiModel().sendMessage(MessageRequest(messages))
                ?: run {
                    db.historyDao().markError(historyItem.promptId, "No response")
                    return null
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
            return null
        }

        val responseItem = ResponseItem(
            promptId = historyItem.promptId,
            request = Gson().toJson(messages),
            response = response
        )

        val responseId = db.historyDao().addResponse(responseItem)

        val newHistoryItem = historyItem.copy(status = HistoryStatus.RESPONDED)
        db.historyDao().updateHistory(newHistoryItem)

        return ResponseData(historyItem, response)
    }

    private suspend fun applyDebulifyResponse(responseData: ResponseData, file: File) {
        val projectId = responseData.historyItem.projectId
        val fileName = extractNewFileName(responseData.response, file.fileName)
            ?: throw Error("Couldn't extract file name from response\n${responseData.response}\n${file.fileName}")

        val fileId = db.fileDao().insertFile(
            File(
                projectId = projectId,
                fileName = fileName,
                isFile = true,
                path = file.path
            )
        )
        db.fileDao().insertContentAndUpdateFileSize(
            Content(
                fileId = fileId,
                content = removeFirstLine(responseData.response).trim() + "\n",
                type = Content.Type.RAW
            )
        )
    }

    private fun extractNewFileName(codeInput: String, inputFileName: String): String? {
        val baseName = inputFileName.lowercase().substringBeforeLast(".")
        codeInput.trim().lowercase().split("\n").forEach { line ->
            val extensionPattern = """\.(\w+)""".toRegex()
            val matchResult = extensionPattern.find(line)
            if (matchResult != null) {
                return "$baseName.${matchResult.groupValues[1].lowercase()}"
            }
        }
        return null
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
            val fileName = pathParts.removeLast().run {
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

    private suspend fun getSchemas(name: String) = db.schemaDao().getSchema(name)

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
        if (keys.contains(KEY_BULLET_FILE)) {
            result = result.replace("{$KEY_BULLET_FILE}", fileData?.content ?: "")
        }
        return result
    }

    private data class ResponseData(
        val historyItem: HistoryItem,
        val response: String
    )
}
