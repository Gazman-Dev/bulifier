package com.bulifier.core.ai

import com.bulifier.core.ai.parsers.AgentAction
import com.bulifier.core.ai.parsers.AgentData
import com.bulifier.core.ai.parsers.parseAgentResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentParserTest {

    @Test
    fun `test bulify command`() {
        val response = """command: bulify
context:
 - index.html
instructions: 
Uncaught SyntaxError: Cannot use import statement outside a module [js/main.js:1]

Please update the index.html to fix this. I updated index.html to set the script tag's type to "module" to allow the use of ES6 import statements."""
        val parseAgentResponse = parseAgentResponse(response)
        parseAgentResponse.verifyContext("index.html.bul")
    }

    @Test
    fun `test bulify command with native code`() {
        val response = """command: bulify
context:
 - index.html
instructions: Uncaught SyntaxError: Cannot use import statement outside a module [js/main.js:1] Please update the index.html to fix this. I updated the index.html to add type="module" to the script tag to enable ES module support."""

        val parseAgentResponse = parseAgentResponse(response)
        parseAgentResponse.verifyContext("index.html.bul")
    }


    @Test
    fun `test update current file`() {
        parseAgentResponse(
            """
        command: bulify
        context:
         - src/main.kt
        instructions: User requested: "Update the current file." No file operations required; updated the current open file.
        """.trimIndent()
        )
            .verifyContext(
                "src/main.kt.bul"
            )
    }

    @Test
    fun `test rename file operation`() {
        parseAgentResponse(
            """
        action: move
         - src/Logger.kt src/ErrorTracker.kt

        command: bulify
        context:
         - src/ErrorTracker.kt
        instructions: User requested: "Rename Logger.kt to ErrorTracker.kt." Renamed Logger.kt to ErrorTracker.kt.
        """.trimIndent()
        ).verifyActions(
            AgentAction.Move("src/Logger.kt", "src/ErrorTracker.kt")
        ).verifyContext(
            "src/ErrorTracker.kt.bul"
        )
    }

    @Test
    fun `test move file operation`() {
        parseAgentResponse(
            """
        action: move
         - src/db/DatabaseOps.kt data/DatabaseOps.kt

        command: bulify
        context:
         - data/DatabaseOps.kt
        instructions: User requested: "Move the file handling database operations to the data folder." Moved DatabaseOps.kt to the data folder.
        """.trimIndent()
        )
            .verifyActions(
                AgentAction.Move("src/db/DatabaseOps.kt", "data/DatabaseOps.kt")
            )
            .verifyContext(
                "data/DatabaseOps.kt.bul"
            )
    }

    @Test
    fun `test delete file operation`() {
        parseAgentResponse(
            """
        action: delete
         - src/temp/TempTest.kt

        command: bulify
        context:
         - src/temp/TempTest.kt
        instructions: User requested: "Delete the temporary file." Deleted TempTest.kt.
        """.trimIndent()
        )
            .verifyActions(
                AgentAction.Delete("src/temp/TempTest.kt")
            )
            .verifyContext(
                "src/temp/TempTest.kt.bul"
            )
    }

    @Test
    fun `test combined move and delete operations`() {
        parseAgentResponse(
            """
        action: move
         - src/A.kt new_folder/A.kt

        action: delete
         - src/B.kt

        command: bulify
        context:
         - new_folder/A.kt
         - src/B.kt
        instructions: User requested: "Move file A to new folder and delete file B." Moved A.kt to new_folder and deleted B.kt.
        """.trimIndent()
        )
            .verifyActions(
                AgentAction.Move("src/A.kt", "new_folder/A.kt"),
                AgentAction.Delete("src/B.kt")
            )
            .verifyContext(
                "new_folder/A.kt.bul",
                "src/B.kt.bul"
            )


    }

    @Test
    fun `test restructure utilities`() {
        parseAgentResponse(
            """
        action: move
         - src/utils.ts src/utilities/utils.ts
         - src/logging.ts src/utilities/logging.ts

        command: bulify
        context:
         - src/utilities/utils.ts
         - src/utilities/logging.ts
        instructions: User requested: "Restructure utilities by grouping related files under a utilities folder." Moved utils.ts and logging.ts to the utilities folder.
        """.trimIndent()
        ).verifyActions(
            AgentAction.Move("src/utils.ts", "src/utilities/utils.ts"),
            AgentAction.Move("src/logging.ts", "src/utilities/logging.ts")
        ).verifyContext(
            "src/utilities/utils.ts.bul",
            "src/utilities/logging.ts.bul"
        )
    }

    @Test
    fun `test multiline instructions`() {
        parseAgentResponse(
            """
        action: move
         - src/oldDir/Module.kt src/newDir/Module.kt

        action: delete
         - src/temp/OldModule.kt

        command: bulify
        context:
         - src/newDir/Module.kt
         - src/temp/OldModule.kt
        instructions: User requested: "Refactor and clean up modules."
         Moved Module.kt to newDir.
         Deleted OldModule.kt as it was deprecated.
         Please ensure the project is updated accordingly.
        """.trimIndent()
        )
            .verifyActions(
                AgentAction.Move("src/oldDir/Module.kt", "src/newDir/Module.kt"),
                AgentAction.Delete("src/temp/OldModule.kt")
            )
            .verifyContext(
                "src/newDir/Module.kt.bul",
                "src/temp/OldModule.kt.bul"
            )
            .verifyInstructions(
                """
                    User requested: "Refactor and clean up modules."
                    Moved Module.kt to newDir.
                    Deleted OldModule.kt as it was deprecated.
                    Please ensure the project is updated accordingly.
                """.trimIndent()
            )
    }

}

private fun AgentData.verifyInstructions(prompt: String) {
    assertEquals(1, commands.size)
    val instructions = commands[0].instructions.lines().map {
        it.trim()
    }.joinToString("\n")
    val prompt = prompt.lines().map {
        it.trim()
    }.joinToString("\n")
    assertEquals(prompt, instructions)
}

private fun AgentData.verifyContext(vararg files: String): AgentData {
    assertEquals(1, commands.size)
    assertEquals(files.map {
        it.trim()
    }, commands[0].context)

    return this
}

private fun AgentData.verifyActions(vararg actions: AgentAction): AgentData {
    actions.forEach {
        assertTrue("$it not found", this.actions.any { action -> action == it })
    }
    assertEquals(actions.size, this.actions.size)
    return this
}
