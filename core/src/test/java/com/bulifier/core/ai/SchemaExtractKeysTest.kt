package com.bulifier.core.ai

import com.bulifier.core.schemas.SchemaModel
import junit.framework.TestCase.assertEquals
import org.junit.Test

class SchemaExtractKeysTest {

    @Test
    fun `extract keys`() {
        assertEquals(setOf("prompt"), SchemaModel.extractKeys("⟪prompt⟫"))
        assertEquals(setOf("prompt"), SchemaModel.extractKeys("cool ⟪prompt⟫"))
        assertEquals(setOf("prompt"), SchemaModel.extractKeys("cool ⟪ prompt⟫"))
    }

    @Test
    fun `test bulify schema`() {
        assertEquals(
            setOf(
                "project_name",
                "project_details",
                "files_for_context",
                "files_to_update",
                "files_to_create",
                "files_list"
            ), SchemaModel.extractKeys(bulifySchemaTest)
        )
    }

}

private val bulifySchemaTest = """
    # Settings
     - Purpose: Generate code files from bullet points, pseudo code files and raw files
     - Multi raw files output: True

    # System
    You are a helpful developer agent working on the ⟪project_name⟫ project.
    ⟪project_details⟫

    Convert the pseudo code files the user shares with you into native code files.
    The user may share extra files with you for context, they can also include native code files.
    Pay attention to what files the user asks you to convert.

    # Files Output format

    ### path/to/file1
    ```
    file1 content
    ```

    ---

    ### path/to/file2
    ```
    file2 content
    ```


    # User
    ⟪files_for_context = exists
    Please review the below files for context.

    ⟪files_for_context⟫
    ⟫

    ⟪files_to_update = exists
    The below pseudo code files were updated, please update the corresponding native code files.
    Please respond with full content of the native code files.

    ⟪files_to_update⟫
    ⟫

    ⟪files_to_create = exists
    I am attaching new pseudo code files. I need you to convert them into native code files.
    ⟫

    ---

    Please provide the full content of the below native code files.

    ⟪files_list⟫

""".trimIndent()