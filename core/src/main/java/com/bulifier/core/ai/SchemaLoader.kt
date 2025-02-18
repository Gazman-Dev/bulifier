package com.bulifier.core.ai

import com.bulifier.core.db.AppDatabase
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.schemas.SchemaModel.KEY_FILES
import com.bulifier.core.schemas.SchemaModel.KEY_FILES_FOR_CONTEXT
import com.bulifier.core.schemas.SchemaModel.KEY_FILES_LIST
import com.bulifier.core.schemas.SchemaModel.KEY_FILES_TO_CREATE
import com.bulifier.core.schemas.SchemaModel.KEY_FILES_TO_UPDATE
import com.bulifier.core.schemas.SchemaModel.KEY_MAIN_FILE
import com.bulifier.core.schemas.SchemaModel.KEY_MAIN_FILE_NAME
import com.bulifier.core.schemas.SchemaModel.KEY_PROJECT_DETAILS
import com.bulifier.core.schemas.SchemaModel.KEY_PROJECT_NAME
import com.bulifier.core.schemas.SchemaModel.KEY_PROMPT
import com.bulifier.core.schemas.SchemaModel.KEY_SCHEMAS
import com.bulifier.core.utils.Logger
import kotlin.collections.component1
import kotlin.collections.component2

class SchemaLoader(
    private val db: AppDatabase,
    private val logger: Logger,
    private val historyItem: HistoryItem
) {

    suspend fun loadSchemaKeys(
        keys: Set<String>
    ): Map<String, String?> {
        logger.d("Updating schema content for promptId=${historyItem.promptId}")
        val purposeData = loadPurposeData(keys)
        val schemas = loadSchemas(keys)
        var fileData = loadFileData(keys)
        val project = loadProject(keys)
        val fileToUpdate = loadFilesToUpdate(keys)
        val fileToCreate = loadFilesToCreate(keys)
        val filesList = loadFilesList(keys)
        val filesForContext = loadFilesForContext(keys)

        return mapOf(
            KEY_FILES_LIST to filesList,
            KEY_FILES_TO_UPDATE to fileToUpdate,
            KEY_FILES_TO_CREATE to fileToCreate,
            KEY_PROMPT to historyItem.prompt,
            KEY_MAIN_FILE to fileData?.content,
            KEY_MAIN_FILE_NAME to fileData?.fullPath,
            KEY_PROJECT_DETAILS to project?.projectDetails,
            KEY_PROJECT_NAME to project?.projectName,
            KEY_FILES to purposeData,
            KEY_SCHEMAS to schemas,
            KEY_FILES_FOR_CONTEXT to filesForContext
        )
    }

    private suspend fun loadFilesForContext(keys: Set<String>) = keys.runIfContainsAny(
        KEY_FILES_FOR_CONTEXT
    ) {
        val contextFiles = if (historyItem.isNativeCode) {
            db.fileDao().getNativeFiles(historyItem.contextFiles)
        } else {
            historyItem.contextFiles
        }

        if (contextFiles.isEmpty()) {
            return@runIfContainsAny null
        }

        db.fileDao().getContent(contextFiles).map {
            """
            FileName: ${it.fullPath.removeSuffix(".bul")}
            
            ${it.content}
            
            """.trimIndent()
        }.joinToString("\n\n")
    }


    private suspend fun loadFilesList(keys: Set<String>) = keys.runIfContainsAny(
        KEY_FILES_LIST
    ) {
        db.fileDao().getFiles(historyItem.syncBulletFiles).map {
            " - ${it.fullPath.removeSuffix(".bul")}"
        }.joinToString("\n")
    }

    private suspend fun loadFilesToCreate(keys: Set<String>) = keys.runIfContainsAny(
        KEY_FILES_TO_CREATE
    ) {
        db.fileDao().getContent(historyItem.syncBulletFiles).mapIndexedNotNull { index, it ->
            if (historyItem.syncRawFiles[index] != -1L) {
                null
            } else {
                """
                ### ${it.path}/${it.fileName.removeSuffix(".bul")}
                ```
                ${it.content}
                ```""".trimIndent()
            }
        }.joinToString("\n\n---\n\n")
    }

    private suspend fun loadFilesToUpdate(keys: Set<String>) = keys.runIfContainsAny(
        KEY_FILES_TO_UPDATE
    ) {
        val ids = historyItem.syncBulletFiles.mapIndexedNotNull { index, it ->
            val rawFileId = historyItem.syncRawFiles[index]
            if (rawFileId == -1L) {
                null
            } else {
                listOf(it, rawFileId)
            }
        }.flatten()

        val map = db.fileDao().getContent(ids).associateBy {
            it.fileId
        }

        historyItem.syncBulletFiles.mapIndexedNotNull { index, it ->
            val rawFileId = historyItem.syncRawFiles[index]
            if (rawFileId == -1L) {
                null
            } else {
                val bulletFile = map[it]
                val rawFile = map[rawFileId]

                if (bulletFile == null || rawFile == null) {
                    null
                } else {
                    """
                        ### ${bulletFile.path}/${bulletFile.fileName.removeSuffix(".bul")}
                        ```pseudo
                        ${bulletFile.content}
                        ```
                        
                        ```native
                        ${rawFile.content}
                        ```""".trimIndent()
                }
            }
        }.joinToString("\n\n---\n\n")
    }


    private suspend fun loadProject(
        keys: Set<String>
    ) = keys.runIfContainsAny(KEY_PROJECT_DETAILS, KEY_PROJECT_NAME) {
        db.fileDao().getProject(historyItem.projectId)
    }

    private suspend fun loadFileData(
        keys: Set<String>
    ) = keys.runIfContainsAny(KEY_MAIN_FILE, KEY_MAIN_FILE_NAME) {
        if (historyItem.fileName?.isNotBlank() == true) {
            db.fileDao()
                .getContent(historyItem.path, historyItem.fileName, historyItem.projectId)
        } else null
    }

    private suspend fun loadSchemas(keys: Set<String>) =
        keys.runIfContainsAny(KEY_SCHEMAS) {
            SchemaModel.getSchemaPurposes()
                .joinToString("\n") {
                    " - " + it.schemaName + ": " + it.purpose
                }
        }

    private suspend fun loadPurposeData(
        keys: Set<String>
    ) = keys.runIfContainsAny(KEY_FILES) {
        db.fileDao().loadBulletFiles(historyItem.projectId)
            .groupBy { it.path }
            .map { (path, files) ->
                if (files.size == 1) {
                    val fileName = files[0].fileName.removeSuffix(".bul")
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
                        " - $path${it.fileName.removeSuffix(".bul")}${extractPurpose(it.content)}"
                    }
                }
            }
    }?.joinToString("\n")


    private suspend fun <T> Set<String>.runIfContainsAny(
        vararg key: String,
        block: suspend () -> T
    ): T? {
        return if (key.any { contains(it) }) {
            block()
        } else {
            null
        }
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
}