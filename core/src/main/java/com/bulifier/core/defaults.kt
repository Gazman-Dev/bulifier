package com.bulifier.core

import com.bulifier.core.db.Content
import com.bulifier.core.db.File

data class FileContent(val file: File, val content: Content)

val defaultSchemaFiles by lazy {
    arrayOf(
        fileContent(
            "bulify.schema", """
            
        """.trimIndent()
        )
    )
}

private const val path = ".schema/"
private fun fileContent(content: String, fileName: String) =
    FileContent(
        File(
            fileName = fileName,
            path = path,
            isFile = true,
            projectId = -1
        ),
        Content(
            content = content,
            fileId = -1,
            type = Content.Type.SCHEMA
        )
    )
