package com.bulifier.core.schemas

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.bulifier.core.db.AppDatabase
import com.bulifier.core.db.Content
import com.bulifier.core.db.File
import com.bulifier.core.db.ProcessingMode
import com.bulifier.core.db.Schema
import com.bulifier.core.db.SchemaSettings
import com.bulifier.core.db.SchemaType
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.utils.readUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.StringReader

object SchemaModel {
    const val KEY_PACKAGE = "package"
    const val KEY_BULLETS_FILE_NAME = "bullets_file_name"
    const val KEY_CONTEXT = "context"
    const val KEY_PROMPT = "prompt"
    const val KEY_MAIN_FILE = "main_file"
    const val KEY_MAIN_FILE_NAME = "main_file_name"
    const val KEY_PROJECT_DETAILS = "project_details"
    const val KEY_SCHEMAS = "schemas"
    const val KEY_FOLDER_NAME = "folder_name"
    const val KEY_FILES = "files"
    const val KEY_PROJECT_NAME = "project_name"
    const val KEY_BULLET_RAW_FILE_PAIR = "bullet_raw_file_pair"

    private val keys = setOf(
        KEY_PACKAGE,
        KEY_CONTEXT,
        KEY_PROMPT,
        KEY_MAIN_FILE,
        KEY_MAIN_FILE_NAME,
        KEY_PROJECT_DETAILS,
        KEY_SCHEMAS,
        KEY_FOLDER_NAME,
        KEY_FILES,
        KEY_PROJECT_NAME,
        KEY_BULLET_RAW_FILE_PAIR,
        KEY_BULLETS_FILE_NAME
    )

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var db: AppDatabase
    private lateinit var appContext: Context

    fun init(appContext: Context) {
        db = appContext.db
        this.appContext = appContext

        scope.launch {
            Prefs.projectId.flow.collectLatest {
                if (it != -1L) {
                    verifySchemas(it)
                }
            }
        }
    }

    fun verifySchemasRequest(projectId: Long) {
        scope.launch {
            verifySchemas(Prefs.projectId.flow.value)
            reloadSchemas(projectId)
        }
    }

    private suspend fun verifySchemas(projectId: Long) {
        withContext(Dispatchers.IO) {
            if (!db.fileDao().isPathExists("schemas", projectId)) {
                createDefaultSchemas(projectId)
            } else {
                reloadSchemas(projectId)
            }
        }
    }

    private suspend fun createDefaultSchemas(projectId: Long) {
        loadSystemDefaults(projectId)
        reloadSchemas(projectId)
    }

    private suspend fun loadSystemDefaults(projectId: Long) {
        db.fileDao().insertFile(
            File(
                path = "",
                fileName = "schemas",
                isFile = false,
                projectId = projectId
            )
        )
        resetSystemSchemas(projectId)
    }

    suspend fun resetSystemSchemas(projectId: Long) {
        withContext(Dispatchers.IO) {
            val project = db.fileDao().getProjectById(projectId)
            var schemasPath = "schemas/"
            if (project.template != null) {
                val assetsPath = "templates/${project.template}/schemas/"
                if (appContext.assets.list(assetsPath) != null) {
                    schemasPath = assetsPath
                }
            }
            appContext.assets.list(schemasPath)?.map { fileName ->
                appContext.assets.open("$schemasPath$fileName").bufferedReader().use {
                    val schemaData = it.readText()
                    val fileId = db.fileDao().insertFile(
                        File(
                            path = "schemas",
                            fileName = fileName,
                            isFile = true,
                            projectId = projectId
                        )
                    ).run {
                        if (this == -1L) {
                            db.fileDao().getFileId(
                                path = "schemas",
                                fileName = fileName,
                                projectId = projectId
                            ) ?: return@withContext
                        } else {
                            this
                        }
                    }


                    db.fileDao().insertContentAndUpdateFileMetaData(
                        Content(
                            fileId = fileId,
                            content = schemaData
                        )
                    )
                }
            }
        }
    }

    suspend fun reloadSchemas(projectId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            var schemas = db.fileDao().loadFilesByPath("schemas", projectId).map {
                parseSchema(
                    it.content, it.fileName.substringBeforeLast("."), projectId
                )
            }.flatten()
            reloadSchemas(schemas, projectId)
            true
        }
    }

    private suspend fun reloadSchemas(
        schemas: List<Schema>,
        projectId: Long
    ) {
        schemas.run {
            val settings = filter { it.type == SchemaType.SETTINGS }.map {
                val map = it.content.split("\n").filter {
                    it.trim().isNotEmpty()
                }.associate { line ->
                    try {
                        val values = line.substring(" - ".length - 1).lowercase().split(":")
                        values[0].trim() to values[1].trim()
                    } catch (e: Exception) {
                        throw Error("Error parsing settings schema: $line", e)
                    }
                }
                SchemaSettings(
                    schemaName = it.schemaName,
                    inputExtension = map["input extension"] ?: "bul",
                    processingMode = map["processing mode"]?.let {
                        ProcessingMode.fromString(it)
                    } ?: ProcessingMode.SINGLE,
                    multiFilesOutput = map["multi files output"] == "true",
                    overrideFiles = map["override files"] == "true",
                    visibleForAgent = map["visible for agent"] == "true",
                    isAgent = map["agent"] == "true",
                    purpose = map["purpose"] ?: "TBA",
                    projectId = projectId
                )
            }
            db.schemaDao()
                .addSchemas(schemas.filter { it.type != SchemaType.SETTINGS }, settings)
        }
    }

    suspend fun getSchemaNames() =
        db.schemaDao().getSchemaNames(projectId = Prefs.projectId.flow.value)

    suspend fun getSchemaPurposes() =
        db.schemaDao().getSchemaPurposes(projectId = Prefs.projectId.flow.value)

    private fun parseSchema(content: String, schemaName: String, projectId: Long): List<Schema> {
        val pattern = """^\s*#\s*[0-9a-zA-Z_\-]+""".toRegex(RegexOption.MULTILINE)

        // Find all matches of the pattern
        val matches = pattern.findAll(content)

        // Split the input string into sections based on the matches
        val sections = mutableListOf<String>()
        val headers = mutableListOf<String>()
        var lastIndex = 0
        for (match in matches) {
            val value = match.value.trim()
            headers.add(value.substring(1))
            val section = content.substring(lastIndex, match.range.first).trim()
            if (section.isNotEmpty()) {
                sections.add(section)
            }
            lastIndex = match.range.last + 1
        }
        val section = content.substring(lastIndex).trim()
        if (section.isNotEmpty()) {
            sections.add(section)
        }

        return headers.mapIndexed { index, header ->
            val type = SchemaType.fromString(header)
            if (type == SchemaType.COMMENT) {
                return@mapIndexed null
            }

            val keys = extractKeys(sections[index])
            if (!SchemaModel.keys.containsAll(keys)) {
                throw IllegalArgumentException("Schema error - Invalid keys: [${keys.joinToString()}]")
            }

            Schema(
                schemaName = schemaName,
                content = sections[index],
                type = type,
                keys = LinkedHashSet(keys),
                projectId = projectId
            )
        }.filterNotNull()
    }

    /**
     * Extracts keys from the input template.
     *
     * It scans until it finds an opening marker (⟪), then reads until the matching closing marker (⟫).
     * If the captured token is a single line (i.e. it doesn't contain a newline), it's added as a key.
     * Conditional blocks (which span multiple lines) are ignored.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun extractKeys(input: String): Set<String> {
        val keys = mutableSetOf<String>()
        val reader = StringReader(input)
        while (true) {
            val ch = reader.read()
            if (ch == -1) break
            if (ch.toChar() == '⟪') {
                val token = reader.readUntil('⟫')
                if (!token.contains('\n')) {
                    keys.add(token)
                }
            }
        }
        return keys
    }
}

