package com.bulifier.core.ai

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bulifier.core.db.Content
import com.bulifier.core.db.File
import com.bulifier.core.db.FileData
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.db.ProcessingMode
import com.bulifier.core.db.Project
import com.bulifier.core.db.ResponseItem
import com.bulifier.core.db.Schema
import com.bulifier.core.db.SchemaSettings
import com.bulifier.core.db.SchemaType
import com.bulifier.core.db.SyncFile
import com.bulifier.core.db.db
import com.bulifier.core.models.ApiModel
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.schemas.SchemaModel.KEY_BULLETS_FILE_NAME
import com.bulifier.core.schemas.SchemaModel.KEY_BULLET_RAW_FILE_PAIR
import com.bulifier.core.schemas.SchemaModel.KEY_CONTEXT
import com.bulifier.core.schemas.SchemaModel.KEY_FILES
import com.bulifier.core.schemas.SchemaModel.KEY_FOLDER_NAME
import com.bulifier.core.schemas.SchemaModel.KEY_MAIN_FILE
import com.bulifier.core.schemas.SchemaModel.KEY_MAIN_FILE_NAME
import com.bulifier.core.schemas.SchemaModel.KEY_PACKAGE
import com.bulifier.core.schemas.SchemaModel.KEY_PROJECT_DETAILS
import com.bulifier.core.schemas.SchemaModel.KEY_PROJECT_NAME
import com.bulifier.core.schemas.SchemaModel.KEY_PROMPT
import com.bulifier.core.schemas.SchemaModel.KEY_SCHEMAS
import com.bulifier.core.ui.main.actions.markForDeleteAction
import com.bulifier.core.ui.main.actions.moveAction
import com.bulifier.core.utils.Logger
import com.bulifier.core.utils.hasMore
import com.bulifier.core.utils.readUntil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.StringReader
import java.util.concurrent.atomic.AtomicInteger

class AiWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val logger = Logger("AiWorker")
    private val responseProcessor = ResponseProcessor()
    private val db = context.db

    override suspend fun doWork(): Result {
        val historyItemId = inputData.getLong("historyItemId", -1L)
        if (historyItemId == -1L) {
            return Result.failure()
        }

        val historyItem = db.historyDao().getHistoryItem(historyItemId)

        logger.d("Processing: promptId=${historyItem.promptId}, status=${historyItem.status}")
        if (historyItem.modelId == null) {
            reportError(historyItem.promptId, "No model selected")
            logger.e("No model selected for promptId=${historyItem.promptId}")
            return Result.failure()
        }

        if (db.historyDao().startProcessingHistoryItem(
                historyItem.promptId, statuses = listOf(
                    HistoryStatus.SUBMITTED,
                    HistoryStatus.RE_APPLYING
                )
            ) <= 0
        ) {
            logger.d("Already processing or not found. Id: ${historyItem.promptId}")
            return Result.failure()
        }

        val filesContext = if (historyItem.contextFiles.isNotEmpty()) {
            db.fileDao().getContent(historyItem.contextFiles)
        } else {
            emptyList()
        }
        val responses = db.historyDao().getResponses(historyItem.promptId)
        try {
            process(historyItem, filesContext, responses)
            return Result.success()
        } catch (e: Exception) {
            logger.e("Error processing history item", e)
            reportError(historyItem.promptId, e.message ?: "Unknown error")
            return Result.failure()
        }
    }

    private suspend fun reportError(promptId: Long, errorMessage: String) {
        logger.e("Reporting error for promptId=$promptId: $errorMessage")
        db.historyDao().markError(promptId, errorMessage)
    }

    private suspend fun process(
        historyItem: HistoryItem,
        filesContext: List<FileData>,
        responses: List<ResponseItem>?
    ) {
        logger.d(
            "Processing details: promptId=${historyItem.promptId}, " +
                    "hasFileContext=${filesContext.isNotEmpty()}, hasResponses=${responses?.isNotEmpty()}"
        )
        val schemas = getSchemas(historyItem.schema, projectId = historyItem.projectId)
        val settings = db.schemaDao().getSettings(
            historyItem.schema.trim(),
            projectId = historyItem.projectId
        )
        if (settings == null) {
            val errorMessage = "No settings found for schema: ${historyItem.schema}"
            logger.e(errorMessage)
            reportError(historyItem.promptId, errorMessage)
            return
        }
        logger.d("Settings loaded for promptId=${historyItem.promptId}: $settings")

        val model = withContext(Dispatchers.IO) {
            loadModel(historyItem)
        }
        val apiModel = model.createApiModel()

        val fileData =
            if (historyItem.fileName?.isNotBlank() == true) {
                db.fileDao()
                    .getContent(historyItem.path, historyItem.fileName, historyItem.projectId)
            } else null

        val project = db.fileDao().getProject(historyItem.projectId)

        when {
            settings.isAgent -> {
                logger.i("handleAgentFlow")
                handleAgentFlow(project, historyItem, filesContext, fileData, schemas, apiModel)
            }

            settings.processingMode == ProcessingMode.PER_FILE -> {
                logger.i("processMultipleFiles")
                processMultipleFiles(historyItem, settings, apiModel, project)
            }

            settings.processingMode == ProcessingMode.SYNC_BULLETS || settings.processingMode == ProcessingMode.SYNC_RAW -> {
                logger.i("syncing")
                sync(
                    historyItem, apiModel, project
                )
            }

            // settings.processingMode == ProcessingMode.SINGLE
            else -> {
                logger.i("processSingleCall")
                processSingleCall(
                    responses,
                    historyItem,
                    settings,
                    filesContext,
                    fileData,
                    schemas,
                    apiModel,
                    project
                )
            }
        }
    }

    private suspend fun handleAgentFlow(
        project: Project,
        historyItem: HistoryItem,
        filesContext: List<FileData>,
        fileData: FileData?,
        schemas: List<Schema>,
        apiModel: ApiModel
    ) {
        logger.d("handleAgentFlow started for promptId=${historyItem.promptId}")
        val processedSchemas: List<Schema> = prepareSchema(
            project = project,
            filesContext = filesContext.map {
                "file name: ${it.fileName}\n${it.content}"
            },
            fileData = fileData,
            schemas = schemas,
            historyItem = historyItem
        )
        val messages = toMessages(processedSchemas)

        sendMessages(messages, historyItem, apiModel, 1)?.response?.let { response ->
            val data = parseAgentResponse(response)
            logger.d("Actions parsed: ${data.actions.size} Commands parsed: ${data.commands.size} for promptId=${historyItem.promptId}")

            db.fileDao().deleteFilesMarkedForDeletion(historyItem.projectId)
            executeActions(historyItem, data.actions)
            executeAgentCommands(data, historyItem)
        }
    }

    private suspend fun executeAgentCommands(
        data: AgentData,
        historyItem: HistoryItem
    ) {
        val settings = db.schemaDao().getSettings(
            data.commands.map {
                it.command
            }.toSet().toList(),
            projectId = historyItem.projectId
        ).associateBy {
            it.schemaName
        }
        data.commands.forEach { agentCommand ->
            if (agentCommand.command == "update-schema") {
                db.historyDao().addHistory(
                    historyItem.copy(
                        promptId = 0,
                        fileName = null,
                        prompt = agentCommand.instructions,
                        schema = "update-schema",
                        contextFiles = emptyList(),
                        status = HistoryStatus.SUBMITTED,
                        path = "schemas"
                    )
                )
                return@forEach
            }
            val contextFiles = extractContextFiles(agentCommand, historyItem)
            val commandSettings = settings[agentCommand.command]
            if (commandSettings?.processingMode == ProcessingMode.PER_FILE) {
                logger.d("Adding new history for agent command ${agentCommand.command}")
                db.historyDao().addHistory(
                    historyItem.copy(
                        promptId = 0,
                        fileName = null,
                        prompt = agentCommand.instructions,
                        schema = agentCommand.command,
                        contextFiles = contextFiles,
                        status = HistoryStatus.SUBMITTED
                    )
                )
            } else {
                val files = db.fileDao()
                    .verifyFiles(agentCommand.files, historyItem.projectId)
                logger.d(
                    "Files found for agent command ${agentCommand.command}: ${
                        files.joinToString {
                            "\n - $it"
                        }
                    }")
                if (agentCommand.files.size != files.size) {
                    logger.d(
                        "Files not found for agent command ${agentCommand.command}: ${
                            (agentCommand.files - files).joinToString {
                                "\n - $it"
                            }
                        }")
                }

                if (files.isEmpty()) {
                    logger.d("Adding new history for agent command ${agentCommand.command} with no files")
                    db.historyDao().addHistory(
                        historyItem.copy(
                            promptId = 0,
                            path = agentCommand.path ?: "",
                            fileName = null,
                            prompt = agentCommand.instructions,
                            schema = agentCommand.command,
                            contextFiles = contextFiles,
                            status = HistoryStatus.SUBMITTED
                        )
                    )
                } else {
                    files.forEach { file ->
                        logger.d("Adding new history for agent command ${agentCommand.command} with file $file")
                        db.historyDao().addHistory(
                            historyItem.copy(
                                path = file.substringBeforeLast("/", ""),
                                promptId = 0,
                                fileName = file.substringAfterLast("/"),
                                prompt = agentCommand.instructions,
                                schema = agentCommand.command,
                                contextFiles = contextFiles,
                                status = HistoryStatus.SUBMITTED
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun extractContextFiles(
        agentCommand: AgentCommand,
        historyItem: HistoryItem
    ): List<Long> {
        val contextFiles = db.fileDao()
            .getFilesIds(agentCommand.context, historyItem.projectId)
        logger.d(
            "Context files ids: ${contextFiles.joinToString(",")}"
        )
        logger.d(
            "Context files: ${
                agentCommand.files.joinToString("\n") {
                    "- $it"
                }
            }"
        )
        return contextFiles
    }

    private suspend fun executeActions(
        historyItem: HistoryItem,
        actions: List<AgentAction>
    ) {
        for (action in actions) {
            when (action) {
                is AgentAction.Move -> {
                    logger.d("Executing move action: ${action.from} -> ${action.to}")

                    getFile(action.from, historyItem)?.let { from ->
                        moveAction(from, action.to, db.fileDao(), logger)
                    } ?: logger.e("File not found in move action FROM")
                }

                is AgentAction.Delete -> {
                    logger.d("Executing delete action: ${action.file}")

                    getFile(action.file, historyItem)?.let {
                        markForDeleteAction(it, db.fileDao(), logger)
                    } ?: logger.e("File not found in delete action")
                }
            }
        }
    }

    private suspend fun getFile(fileName: String, historyItem: HistoryItem): File? {
        return db.fileDao().getFile(
            fileName.substringBeforeLast("/", ""),
            fileName.substringAfterLast("/"),
            historyItem.projectId
        )
    }

    private suspend fun processSingleCall(
        responses: List<ResponseItem>?,
        historyItem: HistoryItem,
        settings: SchemaSettings,
        filesContext: List<FileData>,
        fileData: FileData?,
        schemas: List<Schema>,
        apiModel: ApiModel,
        project: Project
    ) {
        logger.d("processSingleCall started for promptId=${historyItem.promptId}")
        val response = responses?.firstOrNull()?.run {
            logger.d("Using existing response for promptId=${historyItem.promptId}")
            ResponseData(historyItem, response).also {
                val newHistoryItem = historyItem.copy(status = HistoryStatus.RESPONDED)
                db.historyDao().updateHistory(newHistoryItem)
            }
        } ?: kotlin.run {
            val processedSchemas: List<Schema> = prepareSchema(
                project = project,
                filesContext = filesContext.map {
                    "file name: ${it.fileName}\n${it.content}"
                },
                fileData = fileData,
                schemas = schemas,
                historyItem = historyItem
            )
            val messages = toMessages(processedSchemas)
            logger.d("Sending messages for processing for promptId=${historyItem.promptId}")
            sendMessages(messages, historyItem, apiModel, 1)
        }

        response?.let { data ->
            if (settings.multiFilesOutput) {
                applyMultiFileOutput(data)
            } else {
                logger.d("Overriding file for promptId=${historyItem.promptId}")
                overrideFile(fileData, data)
            }
        }
    }

    private suspend fun overrideFile(
        fileData: FileData?,
        data: ResponseData
    ) {
        fileData?.let {
            logger.d("Overriding file content for fileId=${it.fileId}")
            val normalizeContent = normalizeContent(data.response)
            val content = it.toContent().copy(content = normalizeContent)
            db.fileDao().updateContentAndFileMetaData(content).apply {
                logger.d("File content updated. Rows affected: $this")
            }
        }
    }

    private suspend fun sync(
        historyItem: HistoryItem,
        apiModel: ApiModel,
        project: Project
    ) = coroutineScope {
        logger.d("syncing started for promptId=${historyItem.promptId}")
        val files =
            db.syncDao().getFilesToSync(
                historyItem.projectId,
                historyItem.schema,
            )
        val progress = AtomicInteger(-1)
        var total = files.size
        updateProgress(progress, total, historyItem)
        files.forEach { file ->
            launch {
                val schemas: List<Schema> = prepareSchema(
                    project = project,
                    schemas = getSchemas(historyItem.schema, projectId = historyItem.projectId),
                    historyItem = historyItem,
                    syncFile = file
                )

                val messages = toMessages(schemas)
                logger.d("Sending messages for pair $file")
                sendMessages(
                    messages,
                    historyItem,
                    apiModel,
                    file.fileId ?: file.bulletsFileId
                )?.let { responseData ->
                    if (file.fileId == null) {
                        val bulletsFile = db.fileDao().getFile(file.bulletsFileId)
                        insertRawFile(
                            responseData, File(
                                projectId = historyItem.projectId,
                                fileName = bulletsFile.fileName,
                                isFile = true,
                                path = bulletsFile.path,
                                syncHash = bulletsFile.hash
                            )
                        )
                    } else {
                        val syncFileId = db.fileDao().getFile(
                            if (file.fileId == file.bulletsFileId) {
                                file.rawFileId!!
                            } else {
                                file.bulletsFileId
                            }
                        ).hash
                        db.fileDao().insertContentAndUpdateFileMetaData(
                            Content(
                                fileId = file.fileId,
                                content = normalizeContent(responseData.response)
                            ),
                            syncFileId
                        )
                    }
                    logger.d("Sync file inserted")
                    updateProgress(progress, total, historyItem)
                }
            }
        }
    }

    private suspend fun processMultipleFiles(
        historyItem: HistoryItem,
        settings: SchemaSettings,
        apiModel: ApiModel,
        project: Project
    ) = coroutineScope {
        logger.d("processMultipleFiles started for promptId=${historyItem.promptId}")
        val files =
            db.fileDao().fetchFilesListByPathAndProjectId(
                historyItem.path,
                settings.inputExtension,
                historyItem.projectId
            )
        val progress = AtomicInteger(-1)
        var total = files.size
        updateProgress(progress, total, historyItem)
        files.forEach { file ->
            if (file.fileName == "update-schema.schema" && file.path == "schemas") {
                logger.d("Skipping master schema update")
                total--
            } else {
                launch {
                    val fileData = db.fileDao().getContent(file.fileId)
                    val schemas: List<Schema> = prepareSchema(
                        project = project,
                        fileData = fileData,
                        schemas = getSchemas(historyItem.schema, projectId = historyItem.projectId),
                        historyItem = historyItem
                    )

                    val messages = toMessages(schemas)
                    logger.d("Sending messages for file ${file.fileName}")
                    sendMessages(
                        messages,
                        historyItem,
                        apiModel,
                        file.fileId
                    )?.let { responseData ->
                        if (settings.overrideFiles) {
                            logger.d("Overriding file for fileId=${file.fileId}")
                            overrideFile(fileData, responseData)
                        } else {
                            logger.d("Inserting new file for fileId=${file.fileId}")
                            insertRawFile(responseData, file)
                        }
                        updateProgress(progress, total, historyItem)
                    }
                }
            }
        }
    }

    private suspend fun updateProgress(count: AtomicInteger, total: Int, historyItem: HistoryItem) {
        val progress = count.incrementAndGet() / total.toFloat()
        logger.d("Updating progress for promptId=${historyItem.promptId}: $progress")
        db.historyDao().updateProgress(historyItem.promptId, progress)
    }

    private fun loadModel(historyItem: HistoryItem): QuestionsModel =
        historyItem.modelId?.let {
            logger.d("Loading model for promptId=${historyItem.promptId}")
            QuestionsModel.deserialize(it)
        } ?: throw Error("Model id is null")

    private suspend fun sendMessages(
        messages: List<MessageData>,
        historyItem: HistoryItem,
        apiModel: ApiModel,
        id: Long
    ): ResponseData? = withContext(Dispatchers.IO) {
        logger.d("Sending messages for promptId=${historyItem.promptId}, messageCount=${messages.size}")
        try {
            val apiResponse =
                apiModel.sendMessage(MessageRequest(messages), historyItem, id)
            if (apiResponse.isDone) {
                if (apiResponse.message == null) {
                    logger.e("No response received for promptId=${historyItem.promptId}")
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
                    logger.d("Response received and processed for promptId=${historyItem.promptId}")
                    ResponseData(historyItem, apiResponse.message)
                }
            } else {
                if (apiResponse.error) {
                    val errorMsg = apiResponse.message ?: "Internal Error"
                    logger.e("API error for promptId=${historyItem.promptId}: $errorMsg")
                    db.historyDao().markError(historyItem.promptId, errorMsg)
                }
                null
            }
        } catch (e: Exception) {
            logger.e("Exception during sendMessages for promptId=${historyItem.promptId}", e)
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

    private suspend fun insertRawFile(
        responseData: ResponseData,
        file: File
    ) {
        logger.d("Inserting file for responseData promptId=${responseData.historyItem.promptId}")
        val projectId = responseData.historyItem.projectId
        val fileName = extractNewFileName(file.fileName)

        val fileId = db.fileDao().insertFile(
            File(
                projectId = projectId,
                fileName = fileName,
                isFile = true,
                path = file.path,
                syncHash = file.syncHash
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
        db.fileDao().insertContentAndUpdateFileMetaData(
            Content(
                fileId = fileId,
                content = normalizeContent(responseData.response)
            ),
            file.syncHash
        )
        logger.d("File inserted: fileId=$fileId, fileName=$fileName")
    }

    private fun extractNewFileName(
        inputFileName: String
    ): String {
        val baseName = inputFileName.substringBeforeLast(".")
        return if (baseName.contains(".")) {
            baseName
        } else {
            "$baseName.txt"
        }
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
        logger.d("Applying multi-file output for promptId=${responseData.historyItem.promptId}")
        val projectId = responseData.historyItem.projectId
        val files = responseProcessor.parseResponse(responseData.response)
        logger.d("Parsed ${files.size} files for promptId=${responseData.historyItem.promptId}")
        files.forEach { item ->
            val fileName = "${item.fullPath.substringAfterLast("/")}.bul"
            val path = item.fullPath.substringBeforeLast("/", "")

            val fileId = db.fileDao().insertFileAndVerifyPath(
                File(
                    projectId = projectId,
                    fileName = fileName,
                    isFile = true,
                    path = path
                )
            ).run {
                if (this == -1L) {
                    db.fileDao().getFileId(
                        path = path,
                        fileName = fileName,
                        projectId = projectId
                    )
                } else {
                    this
                }
            }

            if (fileId == null) {
                logger.e("Error inserting a file")
                return@forEach
            }

            val content = trimNonEnglishLines(item.content)
            db.fileDao().insertContentAndUpdateFileMetaData(
                Content(
                    fileId = fileId,
                    content = (item.imports.joinToString("\n") { "import $it" } + "\n\n" + content).trim()
                )
            )
            logger.d("Multi-file content saved for fileId=$fileId, fileName=$fileName")
        }
    }

    private fun trimNonEnglishLines(fileContent: String): String {
        val englishRegex = Regex("[a-zA-Z]")
        val lines = fileContent.lines()

        val startIndex = lines.indexOfFirst { it.contains(englishRegex) }
        val endIndex = lines.indexOfLast { it.contains(englishRegex) }

        return when {
            startIndex != -1 && endIndex != -1 -> lines.subList(startIndex, endIndex + 1)
                .joinToString("\n")

            startIndex != -1 -> lines.subList(startIndex, lines.size).joinToString("\n")
            endIndex != -1 -> lines.subList(0, endIndex + 1).joinToString("\n")
            else -> "" // Return an empty string if no lines contain English characters
        }
    }

    private fun normalizeContent(input: String): String {
        // Regex to capture a single code block with optional language spec,
        // using DOT_MATCHES_ALL so `.` matches newline.
        val codeBlockPattern = Regex("(?s)```(?:\\w+)?\\s*(.*?)\\s*```")
        val matchResult = codeBlockPattern.find(input)

        val codeInsideFence = matchResult?.groups?.get(1)?.value?.trim()

        // If we found no match or the fenced content is blank, return original
        return if (codeInsideFence.isNullOrBlank()) {
            input.trim() + "\n"
        } else {
            codeInsideFence + "\n"
        }
    }


    private fun toMessages(schemas: List<Schema>): List<MessageData> {
        logger.d("Converting schemas to messages")
        return schemas.map {
            MessageData(if (it.type != SchemaType.USER) "system" else "user", it.content)
        }
    }

    private suspend fun prepareSchema(
        project: Project,
        filesContext: List<String>? = null,
        fileData: FileData? = null,
        schemas: List<Schema>,
        historyItem: HistoryItem,
        syncFile: SyncFile? = null,
    ) = withContext(Dispatchers.Default) {
        logger.d("Preparing schema for projectId=${project.projectId}, promptId=${historyItem.promptId}")
        schemas.flatMap { schema ->
            when (schema.type) {
                SchemaType.SETTINGS -> emptyList()
                SchemaType.CONTEXT -> {
                    filesContext?.mapNotNull {
                        val content = updateSchemaContent(
                            project,
                            schema.content,
                            schema.keys,
                            historyItem,
                            fileData,
                            it,
                            syncFile
                        )
                        content?.let {
                            schema.copy(content = it)
                        }

                    } ?: emptyList()
                }

                else -> {
                    updateSchemaContent(
                        project,
                        schema.content,
                        schema.keys,
                        historyItem,
                        fileData,
                        syncFile = syncFile
                    )?.let {
                        listOf(
                            schema.copy(content = it)
                        )
                    } ?: emptyList()
                }
            }
        }
    }

    private suspend fun getSchemas(name: String, projectId: Long) =
        db.schemaDao().getSchema(name, projectId).also {
            logger.d("Fetched schemas for name=$name, projectId=$projectId")
        }

    private suspend fun updateSchemaContent(
        project: Project,
        schemaText: String,
        keys: LinkedHashSet<String>,
        historyItem: HistoryItem,
        fileData: FileData?,
        context: String? = null,
        syncFile: SyncFile? = null
    ): String? {
        logger.d("Updating schema content for promptId=${historyItem.promptId}")
        val filesData = keys.runIfContains(KEY_FILES) {
            db.fileDao().loadBulletFiles(historyItem.projectId)
                .groupBy { it.path }
                .map { (path, files) ->
                    if (files.size == 1) {
                        val fileName = files[0].fileName
                        val path = if (files[0].path.trim().isEmpty()) {
                            ""
                        } else {
                            "${files[0].path}/"
                        }
                        " - $path$fileName${extractPurpose(files[0].content)}"
                    } else {
                        files.joinToString("\n") {
                            val path = if (path.trim().isEmpty()) {
                                ""
                            } else {
                                "${it.path}/"
                            }
                            " - $path${it.fileName}${extractPurpose(it.content)}"
                        }
                    }
                }
        }?.joinToString("\n")
        val schemas = keys.runIfContains(KEY_SCHEMAS) {
            SchemaModel.getSchemaPurposes()
                .joinToString("\n") {
                    " - " + it.schemaName + ": " + it.purpose
                }
        }

        var bulletsFileName = syncFile?.let {
            db.fileDao().getFile(it.bulletsFileId).run {
                if (path.trim().isEmpty()) {
                    fileName
                } else {
                    "$path/$fileName"
                }
            }
        }

        val bulletRawFilePair = keys.runIfContains(KEY_BULLET_RAW_FILE_PAIR) {
            if (syncFile == null) {
                throw RuntimeException("Bullet raw file pair requires file name")
            }


            val bulletsContent = db.fileDao()
                .getContent(syncFile.bulletsFileId)?.content
            val rawContent = syncFile.rawFileId?.let {
                db.fileDao().getContent(it)?.content
            }

            if (rawContent == null) {
                """
                Bullet points file:
                $bulletsContent
            """.trimIndent().trim()
            } else {
                """
                Raw file:
                $rawContent
                
                Bullet points file:
                $bulletsContent
            """.trimIndent().trim()
            }
        }

        val folderName = historyItem.path.substringAfterLast(".")
        val keysMap = mapOf(
            KEY_PACKAGE to historyItem.path,
            KEY_BULLETS_FILE_NAME to bulletsFileName,
            KEY_PROMPT to historyItem.prompt,
            KEY_MAIN_FILE to fileData?.content,
            KEY_MAIN_FILE_NAME to fileData?.let {
                if (it.path.isEmpty()) {
                    it.fileName
                } else {
                    it.path + "/" + it.fileName
                }
            },
            KEY_PROJECT_DETAILS to project.projectDetails,
            KEY_CONTEXT to context,
            KEY_FOLDER_NAME to folderName,
            KEY_FILES to filesData,
            KEY_SCHEMAS to schemas,
            KEY_PROJECT_NAME to project.projectName,
            KEY_BULLET_RAW_FILE_PAIR to bulletRawFilePair
        )
        val result = injectSchema(schemaText, keysMap)
        return result
    }

    private fun extractPurpose(content: String): String {
        val prefix = "- Purpose:"
        val purpose = content.lines().firstOrNull { it.trim().startsWith(prefix) }?.trim()
            ?.substringAfter(prefix)?.trim()
        return if (purpose == null) {
            ""
        } else {
            ": $purpose"
        }
    }

    private suspend fun <T> LinkedHashSet<String>.runIfContains(
        key: String,
        block: suspend () -> T
    ): T? {
        return if (contains(key)) {
            block()
        } else {
            null
        }
    }

    private data class ResponseData(
        val historyItem: HistoryItem,
        val response: String
    )
}

/**
 * Scans through the schema text and injects values from [keysMap].
 *
 * It works as follows:
 *   - Reads until the next opening marker "⟪" and appends that text to the output.
 *   - Consumes the "⟪" marker.
 *   - Marks the reader and reads a candidate block until "⟫".
 *     - If the block contains a newline, resets and reads the block until "\n⟫"
 *       (i.e. a conditional block) and passes it to processBlock.
 *     - Otherwise, treats it as a simple placeholder.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun injectSchema(schemaText: String, keysMap: Map<String, String?>): String {
    val reader = StringReader(schemaText)
    val output = StringBuilder()

    while (true) {
        // Read and append text up to the next "⟪"
        output.append(reader.readUntil("⟪"))
        if (!reader.hasMore()){
            break
        }

        var nextPart = reader.readUntil("⟫")
        if (nextPart.contains("\n")) {
            val blockBuilder = StringBuilder(nextPart)
            while (!nextPart.endsWith("\n")) {
                blockBuilder.append("⟫")
                if(!reader.hasMore()){
                    throw RuntimeException("Error injecting schema")
                }
                nextPart = reader.readUntil("⟫")
                blockBuilder.append(nextPart)
            }
            output.append(processBlock(blockBuilder.toString(), keysMap))
        } else {
            output.append(keysMap[nextPart] ?: "")
        }
    }
    return output.toString().trim().replace("\r", "")
}

private fun processBlock(block: String, keysMap: Map<String, String?>): String {
    val reader = StringReader(block)
    val output = StringBuilder()

    val condition = reader.readUntil("\n").trim()
    if(!checkCondition(condition, keysMap)){
        return ""
    }

    while (true) {
        output.append(reader.readUntil("⟪"))
        if (!reader.hasMore()){
            break
        }
        val nextPart = reader.readUntil("⟫")
        output.append(keysMap[nextPart] ?: "")
    }

    return output.toString()
}

private fun checkCondition(
    condition: String,
    map: Map<String, String?>
) = condition.split("""[\t ]+and[\t ]+""".toRegex()).map {
    val (key, value) = it.split("""[\t ]*=[\t ]*""".toRegex())
    if(map[key] != value){
        Log.d("AiWorker", "Condition failed: $condition -> $key != $value")
    }
    map[key] == value
}.reduce { a,b->
    a && b
}
