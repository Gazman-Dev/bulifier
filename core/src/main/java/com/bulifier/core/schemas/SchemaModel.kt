package com.bulifier.core.schemas

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.bulifier.core.db.AppDatabase
import com.bulifier.core.db.Schema
import com.bulifier.core.db.SchemaType
import com.bulifier.core.db.db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SchemaModel {
    const val KEY_PACKAGE = "package"
    const val KEY_CONTEXT = "context"
    const val KEY_FILE_NAME = "fileName"
    const val KEY_PROMPT = "prompt"
    const val KEY_BULLET_FILE = "bullet_file"

    val keys = setOf(
        KEY_PACKAGE,
        KEY_CONTEXT,
        KEY_FILE_NAME,
        KEY_PROMPT,
        KEY_BULLET_FILE
    )

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var db: AppDatabase

    fun init(context: Context) {
        db = context.db
        val preferences = context.getSharedPreferences("schema", 0)
        if (preferences.getBoolean("prepared_2", false)) {
            return
        }
        preferences.edit().putBoolean("prepared_2", true).apply()

        scope.launch {
            prepareSchemasNow(context)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun prepareSchemasNow(context: Context) {
        withContext(Dispatchers.IO) {
            context.assets.list("schemas")?.forEach { fileName ->
                context.assets.open("schemas/$fileName").bufferedReader().use {
                    val schemaData = it.readText()
                    val schemas = parseSchema(
                        schemaData, fileName
                            .split(".")
                            .first()
                    )
                    context.db.schemaDao().addSchemas(schemas)
                }
            }
        }
    }

    suspend fun getSchemaNames() = db.schemaDao().getSchemaNames()

    private fun parseSchema(schema: String, name: String): List<Schema> {
        val pattern = """#(\s*)[0-9a-zA-Z_\-]+""".toRegex()

        // Find all matches of the pattern
        val matches = pattern.findAll(schema)

        // Split the input string into sections based on the matches
        val sections = mutableListOf<String>()
        val headers = mutableListOf<String>()
        var lastIndex = 0
        for (match in matches) {
            headers.add(match.value.substring(1).trim())
            val section = schema.substring(lastIndex, match.range.first).trim()
            if (section.isNotEmpty()) {
                sections.add(section)
            }
            lastIndex = match.range.last + 1
        }
        val section = schema.substring(lastIndex).trim()
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
                schemaName = name,
                content = sections[index],
                type = type,
                keys = LinkedHashSet(keys)
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