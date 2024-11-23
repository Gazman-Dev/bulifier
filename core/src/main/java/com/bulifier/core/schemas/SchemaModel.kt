package com.bulifier.core.schemas

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.bulifier.core.db.AppDatabase
import com.bulifier.core.db.Content
import com.bulifier.core.db.File
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

    private val keys = setOf(
        KEY_PACKAGE,
        KEY_CONTEXT,
        KEY_PROMPT,
        KEY_MAIN_FILE
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

    private suspend fun verifySchemas(projectId: Long) {
        withContext(Dispatchers.IO) {
            if (!db.fileDao().isPathExists("schemas", projectId)) {
                createDefaultSchemas(projectId)
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


                    db.fileDao().insertContentAndUpdateFileSize(
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
                    val map = it.content.split("\n").associate { line ->
                        val values = line.substring(" - ".length - 1).lowercase().split(":")
                        values[0].trim() to values[1].trim()
                    }
                    SchemaSettings(
                        schemaName = it.schemaName,
                        outputExtension = map["output extension"] ?: "txt",
                        inputExtension = map["input extension"] ?: "bul",
                        runForEachFile = map["run for each file"] == "true",
                        multiFilesOutput = map["multi files output"] == "true",
                        overrideFiles = map["override files"] == "true",
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

    private fun parseSchema(content: String, schemaName: String, projectId: Long): List<Schema> {
        val pattern = """#(\s*)[0-9a-zA-Z_\-]+""".toRegex()

        // Find all matches of the pattern
        val matches = pattern.findAll(content)

        // Split the input string into sections based on the matches
        val sections = mutableListOf<String>()
        val headers = mutableListOf<String>()
        var lastIndex = 0
        for (match in matches) {
            val value = match.value
            headers.add(value.substring(1).trim())
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
                throw IllegalArgumentException("Invalid keys in schema ${keys.joinToString()}")
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
        val pattern = """\{(\w+)\}""".toRegex()
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