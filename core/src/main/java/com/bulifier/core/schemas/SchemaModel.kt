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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SchemaModel {
    const val KEY_PACKAGE = "package"
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
        KEY_BULLET_RAW_FILE_PAIR
    )

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var db: AppDatabase
    private lateinit var appContext: Context
    private var gitRoots: GitRoots? = null

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
            }
            gitRoots = GitRoots(db, projectId).apply {
                load()
            }
        }
    }

    fun addRoot(root: String) {
        scope.launch {
            gitRoots?.addRoot(root)
        }
    }

    suspend fun getRoots(projectId: Long) =
        db.fileDao().getContent("", GIT_ROOTS_FILE_NAME, projectId)?.content?.lines()?.filter {
            it.isNotBlank()
        }

    private suspend fun createGitRoots(projectId: Long) {
        db.fileDao().insertFile(
            File(
                path = "",
                fileName = "git_roots.settings",
                isFile = true,
                projectId = projectId
            )
        )
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
            appContext.assets.list("schemas")?.map { fileName ->
                appContext.assets.open("schemas/$fileName").bufferedReader().use {
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

    suspend fun reloadSchemas(projectId: Long) {
        withContext(Dispatchers.IO) {
            db.fileDao().loadFilesByPath("schemas", projectId).map {
                parseSchema(
                    it.content, it.fileName.substringBeforeLast("."), projectId
                )
            }.run {
                val schemas = flatten()
                val settings = schemas.filter { it.type == SchemaType.SETTINGS }.map {
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun extractKeys(input: String): Set<String> {
        // Regex pattern to find {key} - it looks for anything enclosed in {}
        val pattern = """⟪(\w+)⟫""".toRegex()
        // Finds all matches, maps them to keep only the content without braces, and collects them into a set to avoid duplicates
        val results = pattern.findAll(input)
        return results.map {
            if (it.groupValues.size > 1) {
                it.groupValues[1]
            } else {
                null
            }
        }.filterNotNull().toSet()
    }
}