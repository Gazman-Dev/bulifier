package com.bulifier.core.ai

import DbSyncHelper
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bulifier.core.ai.parsers.BulletFilesParser
import com.bulifier.core.ai.parsers.ParsedFileContent
import com.bulifier.core.ai.parsers.RawFilesParser
import com.bulifier.core.ai.processecors.AgentProcessor
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
import com.bulifier.core.utils.Logger
import com.bulifier.core.utils.hasMore
import com.bulifier.core.utils.ifNull
import com.bulifier.core.utils.readUntil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader

data class ResponseData(
    val historyItem: HistoryItem,
    val response: String
)

class AiWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val logger = Logger("AiWorker")
    private val db = context.db

    override suspend fun doWork(): Result {
        val historyItem = fetchHistoryItem() ?: return Result.failure()

        logger.d("Processing: promptId=${historyItem.promptId}, status=${historyItem.status}")
        if (!verifyModel(historyItem)) {
            return Result.failure()
        }

        if (!lockJob(historyItem)) {
            return Result.failure()
        }

        val settings = loadSettings(historyItem) ?: return Result.failure()

        try {
            process(historyItem, settings)
            return Result.success()
        } catch (e: Exception) {
            logger.e("Error processing history item", e)
            reportError(historyItem.promptId, e.message ?: "Unknown error")
            return Result.failure()
        }
    }

    private suspend fun process(historyItem: HistoryItem, settings: SchemaSettings) {
        logger.d("Processing details: promptId=${historyItem.promptId}")
        val schemas = getSchemas(historyItem.schema, projectId = historyItem.projectId)
        val apiModel = loadApiModel(historyItem)

        logger.d("processSingleCall started for promptId=${historyItem.promptId}")
        val processedSchemas: List<Schema> = prepareSchema(
            schemas = schemas,
            historyItem = historyItem
        )
        val messages = toMessages(processedSchemas)
        logger.d("Sending messages for processing for promptId=${historyItem.promptId}")

        sendMessages(messages, historyItem, apiModel, 1)?.let { data ->
            when {
                settings.isAgent -> AgentProcessor(db, logger, historyItem).processAgent(data)
                settings.multiFilesOutput -> applyMultiFilesOutput(
                    historyItem,
                    data,
                    parse = BulletFilesParser::parse,
                    postProcess = { files ->
                        DbSyncHelper(context).extractDependencies(files, historyItem.projectId)
                    }
                )

                settings.multiRawFilesOutput -> applyMultiFilesOutput(
                    historyItem,
                    data,
                    parse = RawFilesParser::parse,
                )

                else -> throw Error("Unknown output type")
            }
        }
    }

    private suspend fun lockJob(historyItem: HistoryItem): Boolean {
        if (db.historyDao().startProcessingHistoryItem(
                historyItem.promptId, statuses = listOf(
                    HistoryStatus.SUBMITTED,
                    HistoryStatus.RE_APPLYING
                )
            ) <= 0
        ) {
            logger.d("Already processing or not found. Id: ${historyItem.promptId}")
            return false
        }
        return true
    }

    private fun fetchHistoryItem(): HistoryItem? {
        val historyItemId = inputData.getLong("historyItemId", -1L)
        if (historyItemId == -1L) {
            return null
        }

        return db.historyDao().getHistoryItem(historyItemId)
    }

    private suspend fun verifyModel(historyItem: HistoryItem): Boolean {
        if (historyItem.modelId == null) {
            reportError(historyItem.promptId, "No model selected")
            logger.e("No model selected for promptId=${historyItem.promptId}")
            return false
        }
        return true
    }

    private suspend fun reportError(promptId: Long, errorMessage: String) {
        logger.e("Reporting error for promptId=$promptId: $errorMessage")
        db.historyDao().markError(promptId, errorMessage)
    }

    private suspend fun loadApiModel(historyItem: HistoryItem): ApiModel {
        val model = withContext(Dispatchers.IO) {
            loadModel(historyItem)
        }
        val apiModel = model.createApiModel()
        return apiModel
    }

    private suspend fun loadSettings(historyItem: HistoryItem): SchemaSettings? {
        return db.schemaDao().getSettings(
            historyItem.schema.trim(),
            projectId = historyItem.projectId
        ).ifNull {
            val errorMessage = "No settings found for schema: ${historyItem.schema}"
            logger.e(errorMessage)
            reportError(historyItem.promptId, errorMessage)
        }?.also {
            logger.d("Settings loaded for promptId=${historyItem.promptId}: $it")
        }
    }


    private suspend fun applyMultiFilesOutput(
        historyItem: HistoryItem,
        responseData: ResponseData,
        postProcess: (suspend (List<FileData>) -> Unit)? = null,
        parse: (String) -> List<ParsedFileContent>
    ) {
        val files = parse(responseData.response)
        logger.d("Parsed ${files.size} files for promptId=${responseData.historyItem.promptId}")

        val output = mutableListOf<FileData>()

        files.forEach { item ->
            val projectId = responseData.historyItem.projectId
            val fileName = item.fullPath.substringAfterLast("/")
            val path = item.fullPath.substringBeforeLast("/", "")

            val fileId = insertFile(projectId, fileName, path) ?: return@forEach.apply {
                logger.e("Error inserting a file")
            }

            db.fileDao().insertContentAndUpdateFileMetaData(
                Content(
                    fileId = fileId,
                    content = item.content
                )
            )

            postProcess?.let {
                output += FileData(
                    fileId = fileId,
                    fileName = fileName,
                    path = path,
                    isFile = true,
                    content = item.content
                )
            }

            logger.d("File content saved for fileId=$fileId, fileName=$fileName")
        }

        val syncFiles = historyItem.syncBulletFiles
        db.fileDao().markSynced(syncFiles)

        postProcess?.invoke(output)
    }

    private suspend fun insertFile(
        projectId: Long,
        fileName: String,
        path: String
    ): Long? {
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
        return fileId
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

                    val newHistoryItem = historyItem.copy(
                        status = HistoryStatus.RESPONDED,
                        cost = apiResponse.cost
                    )
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

    private fun toMessages(schemas: List<Schema>): List<MessageData> {
        logger.d("Converting schemas to messages")
        return schemas.map {
            MessageData(if (it.type != SchemaType.USER) "system" else "user", it.content)
        }
    }

    private suspend fun prepareSchema(
        schemas: List<Schema>,
        historyItem: HistoryItem,
    ) = withContext(Dispatchers.Default) {
        val schemaLoader = SchemaLoader(db, logger, historyItem)

        logger.d("Preparing schema for projectId=${historyItem.projectId}, promptId=${historyItem.promptId}")
        schemas.mapNotNull { schema ->
            when (schema.type) {
                SchemaType.SETTINGS -> null

                else -> {
                    val keysMap = schemaLoader.loadSchemaKeys(
                        schema.keys
                    )
                    injectSchema(schema.content, keysMap, logger).let {
                        schema.copy(content = it)
                    }
                }
            }
        }
    }

    private suspend fun getSchemas(name: String, projectId: Long) =
        db.schemaDao().getSchema(name, projectId).also {
            logger.d("Fetched schemas for name=$name, projectId=$projectId")
        }
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
fun injectSchema(schemaText: String, keysMap: Map<String, String?>, logger: Logger): String {
    val reader = StringReader(schemaText)
    val output = StringBuilder()

    while (true) {
        // Read and append text up to the next "⟪"
        output.append(reader.readUntil("⟪"))
        if (!reader.hasMore()) {
            break
        }

        var nextPart = reader.readUntil("⟫")
        if (nextPart.contains("\n")) {
            val blockBuilder = StringBuilder(nextPart)
            while (!nextPart.endsWith("\n")) {
                blockBuilder.append("⟫")
                if (!reader.hasMore()) {
                    throw RuntimeException("Error injecting schema")
                }
                nextPart = reader.readUntil("⟫")
                blockBuilder.append(nextPart)
            }
            output.append(processBlock(blockBuilder.toString(), keysMap, logger))
        } else {
            output.append(keysMap[nextPart] ?: "")
        }
    }
    return output.toString().trim().replace("\r", "")
}

private fun processBlock(block: String, keysMap: Map<String, String?>, logger: Logger): String {
    val reader = StringReader(block)
    val output = StringBuilder()

    val condition = reader.readUntil("\n").trim()
    if (!checkCondition(condition, keysMap, logger)) {
        return ""
    }

    while (true) {
        output.append(reader.readUntil("⟪"))
        if (!reader.hasMore()) {
            break
        }
        val nextPart = reader.readUntil("⟫")
        output.append(keysMap[nextPart] ?: "")
    }

    return output.toString()
}

private fun checkCondition(
    condition: String,
    map: Map<String, String?>,
    logger: Logger
) = condition.split("""[\t ]+and[\t ]+""".toRegex()).map {
    val (key, value) = it.split("""[\t ]*=[\t ]*""".toRegex())
    ((value == "exists" && map[key]?.isNotEmpty() == true) || map[key] == value).apply {
        if (!this) {
            logger.d("Condition failed: $condition -> $key != $value")
        }
    }
}.reduce { a, b ->
    a && b
}
