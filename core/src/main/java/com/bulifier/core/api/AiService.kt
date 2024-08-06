package com.bulifier.core.api

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
import com.bulifier.core.schemas.SchemaModel.KEY_BULLET_FILE
import com.bulifier.core.schemas.SchemaModel.KEY_CONTEXT
import com.bulifier.core.schemas.SchemaModel.KEY_PACKAGE
import com.bulifier.core.schemas.SchemaModel.KEY_PROMPT
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiService : LifecycleService() {

    private val workingQueue = mutableSetOf<Long>()
    private val responseProcessor = ResponseProcessor()

    override fun onCreate() {
        super.onCreate()
        db.historyDao().getHistory().observe(this) {
            it.forEach { historyItem ->
                if (historyItem.modelId == null) {
                    lifecycleScope.launch {
                        db.historyDao()
                            .markError(historyItem.promptId, "No model selected")
                    }
                    return@forEach
                }
                if (workingQueue.add(historyItem.promptId)) {
                    lifecycleScope.launch {
                        val filesContext = if (historyItem.contextFiles.isNotEmpty()) {
                            db.fileDao().getContent(historyItem.contextFiles)
                        } else {
                            emptyList()
                        }
                        try {
                            process(historyItem, filesContext)
                        }
                        catch (e: Exception) {
                            e.printStackTrace()
                            db.historyDao().markError(historyItem.promptId, e.message ?: "Unknown error")
                        }
                    }
                }
            }
        }
    }

    private suspend fun process(historyItem: HistoryItem, filesContext: List<FileData>) {
        when (historyItem.schema.lowercase()) {
            HistoryItem.SCHEMA_DEBULLIFY -> {
                handleDebulify(historyItem)
            }

            HistoryItem.SCHEMA_BULLIFY -> {
                handleBulify(filesContext, historyItem)
            }

            else -> throw Error("Unknown schema ${historyItem.schema}")
        }
    }

    private suspend fun handleDebulify(historyItem: HistoryItem) = withContext(Dispatchers.IO) {
        val model = loadModel(historyItem)
        db.fileDao().fetchFilesListByPathAndProjectId(historyItem.path, historyItem.projectId)
            .forEach { file ->
                val schemas: List<Schema> = prepareSchema(
                    historyItem.schema,
                    userPrompt = historyItem.prompt,
                    packageName = historyItem.path,
                    fileContent = db.fileDao().getContent(file.fileId)
                )

                val messages = toMessages(schemas)
                sendMessages(model, messages, historyItem)?.let { responseData ->
                    applyDebulifyResponse(responseData, file)
                }
            }
    }

    private suspend fun handleBulify(
        filesContext: List<FileData>,
        historyItem: HistoryItem
    ) {
        val schemas: List<Schema> = prepareSchema(historyItem.schema,
            userPrompt = historyItem.prompt,
            packageName = historyItem.path,
            filesContext = filesContext.map {
                "file name: ${it.fileName}\n${it.content}"
            })

        val messages = toMessages(schemas)
        val model = loadModel(historyItem)

        sendMessages(model, messages, historyItem)?.let {
            applyBulifyResponse(it)
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
             model.createApiModel().sendMessage(
                MessageRequest(
                    messages
                )
            ) ?: run {
                db.historyDao().markError(historyItem.promptId, "No response")
                return null
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            val errorMessage = e.message ?: "Unknown error"
            db.historyDao().markError(historyItem.promptId, errorMessage)

            val responseItem = ResponseItem(
                promptId = historyItem.promptId,
                request = Gson().toJson(messages),
                response = errorMessage
            )

            db.historyDao().addResponse(
                responseItem
            )
            return null
        }

        val responseItem = ResponseItem(
            promptId = historyItem.promptId,
            request = Gson().toJson(messages)
        )

        val responseId = db.historyDao().addResponse(
            responseItem
        )

        val newHistoryItem = historyItem.copy(
            status = HistoryStatus.RESPONDED
        )

        db.historyDao().updateHistory(
            newHistoryItem, responseItem.copy(
                id = responseId,
                response = response
            )
        )

        return ResponseData(historyItem, response)
    }

    private suspend fun applyDebulifyResponse(responseData: ResponseData, file: File) {
        val projectId = responseData.historyItem.projectId
        val fileName = (extractNewFileName(responseData.response, file.fileName) ?: "").ifBlank {
            throw Error("Couldn't extract file name from response \n${responseData.response} \n${file.fileName}")
        }

        val fileId = db.fileDao().insertFileAndUpdateParent(
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
        // Extract the base name (without extension) from the input file name
        val baseName = inputFileName.lowercase().substringBeforeLast(".")

        // Extract the new extension from the first line of the code
        codeInput.trim().lowercase().split("\n").forEach { line->
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

    private suspend fun applyBulifyResponse(responseData: ResponseData) {
        val projectId = responseData.historyItem.projectId

        val files = responseProcessor.parseResponse(responseData.response)
        files.forEach { item ->
            val pathParts = item.fullPath.split("/").toMutableList()
            val fileName = pathParts.removeLast().run {
                if (this.endsWith(".bul")) {
                    this
                }
                else{
                    split(".")[0] + ".bul"
                }
            }

            val path = pathParts.joinToString("/")
            val fileId = db.fileDao().insertFileAndUpdateParent(
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
                    content = item.imports.run {
                        if (this.isEmpty()) {
                            ""
                        } else {
                            this.joinToString("\n") {
                                "import $it"
                            } + "\n\n"
                        }
                    } + item.content,
                    type = Content.Type.BULLET
                )
            )
        }
    }

    private fun toMessages(schemas: List<Schema>): List<MessageData> {
        val messages = schemas.map {
            MessageData(
                if (it.type != SchemaType.USER) {
                    "system"
                } else {
                    "user"
                },
                it.content
            )
        }
        return messages
    }

    private suspend fun prepareSchema(
        name: String, userPrompt: String,
        packageName: String,
        filesContext: List<String>? = null,
        fileContent: FileData? = null
    ) = withContext(Dispatchers.Default) {
        getSchemas(name).map { schema ->
            if (schema.type == SchemaType.CONTEXT) {
                val list = mutableListOf<Schema>()
                filesContext?.forEach {
                    val content = updateSchemaContent(
                        schema.content,
                        schema.keys, userPrompt, packageName, fileContent
                    ).replace("{$KEY_CONTEXT}", it)

                    list.add(
                        schema.copy(
                            content = content
                        )
                    )
                }
                list
            } else {
                listOf(
                    schema.copy(
                        content = updateSchemaContent(
                            schema.content,
                            schema.keys, userPrompt, packageName, fileContent
                        )
                    )
                )
            }
        }
    }.flatten()

    private suspend fun getSchemas(name: String) = db.schemaDao().getSchema(name)

    private fun updateSchemaContent(
        schemaText: String,
        keys: LinkedHashSet<String>,
        userPrompt: String,
        packageName: String,
        fileContent: FileData?
    ): String {
        var result = schemaText
        if (keys.contains(KEY_PACKAGE)) {
            result = result.replace("{$KEY_PACKAGE}", packageName)
        }
        if (keys.contains(KEY_PROMPT)) {
            result = result.replace("{$KEY_PROMPT}", userPrompt)
        }
        if (keys.contains(KEY_BULLET_FILE)) {
            result = result.replace("{$KEY_BULLET_FILE}", fileContent!!.content)
        }
        return result
    }

    private data class ResponseData(
        val historyItem: HistoryItem,
        val response: String
    )
}