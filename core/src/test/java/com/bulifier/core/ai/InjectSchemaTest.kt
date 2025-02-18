package com.bulifier.core.ai

import com.bulifier.core.utils.Logger
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class InjectSchemaTest {

    private val logger = mock<Logger>()

    @Test
    fun `test simple schema injection`() {
        val result = injectSchema(simple_in, mapOf("prompt" to "Play Ball"), logger)
        assertEquals(simple_out, result)
    }

    @Test
    fun `test conditional schema injection when satisfied`() {
        val result = injectSchema(
            condition_in, mapOf(
                "prompt" to "Play Ball",
                "file" to "index.html",
                "schema" to "lazy",
            ), logger
        )
        assertEquals(condition_out, result)
    }

    @Test
    fun `test conditional schema injection when not satisfied`() {
        val result = injectSchema(condition_in, mapOf("prompt" to "Play Ball"), logger)
        assertEquals(simple_out, result)
    }

    @Test
    fun `test nested conditional schema injection`() {
        val result = injectSchema(
            nestedCondition_in, mapOf(
                "prompt" to "Play Ball",
                "file" to "index.html",
                "schema" to "lazy",
            ), logger
        )
        assertEquals(nestedCondition_out, result)
    }

    @Test
    fun `test exists true in condition`() {
        val result = injectSchema(
            conditionExists_in, mapOf(
                "prompt" to "Play Ball",
                "file" to "index.html",
                "schema" to "lazy",
            ), logger
        )
        assertEquals(conditionExists_out, result)
    }

    @Test
    fun `test exists false in condition`() {
        val result = injectSchema(
            conditionExists_in, mapOf(
                "prompt" to "Play Ball",
                "schema" to "lazy",
            ), logger
        )
        assertEquals(conditionExists_out2, result)
    }

    @Test
    fun `test vendors`() {
        val result = injectSchema(
            windows_in, mapOf(
                "bullets_file_name" to "index.html.bul"
            ), logger
        )
        assertEquals(windows_out, result)
    }
}

private val simple_in = """
    # User
    ⟪prompt⟫
""".trimIndent()

private val simple_out = """
    # User
    Play Ball
""".trimIndent()

private val condition_in = """
    # User
    ⟪prompt⟫
    ⟪file = index.html and schema=lazy
    But not to far
    ⟫
""".trimIndent()

private val condition_out = """
    # User
    Play Ball
    But not to far
""".trimIndent()

private val conditionExists_in = """
    # User
    ⟪prompt⟫
    ⟪file = exists and schema=lazy
    But not to far
    ⟫
""".trimIndent()

private val conditionExists_out = """
    # User
    Play Ball
    But not to far
""".trimIndent()

private val conditionExists_out2 = """
    # User
    Play Ball
""".trimIndent()

private val nestedCondition_in = """
    # User
    ⟪prompt⟫
    ⟪file = index.html and schema=lazy
    Be nice to ⟪file⟫
    ⟫
""".trimIndent()

private val nestedCondition_out = """
    # User
    Play Ball
    Be nice to index.html
""".trimIndent()


private const val windows_in = "" +
        "Please create a raw file and output its content based on the project details.\r\n" +
        "⟪bullets_file_name = index.html.bul\r\n" +
        "Make sure the body contains the following comment so vendor dependencies get injected\r\n" +
        "<!-- Vendor libraries -->\r\n" +
        "⟫"


private val windows_out = """
    Please create a raw file and output its content based on the project details.
    Make sure the body contains the following comment so vendor dependencies get injected
    <!-- Vendor libraries -->
""".trimIndent()

private val cool = """
    ⟪files_for_context = exists
    Please review the below files for context.

    ⟪files_for_context⟫
    ⟫

    ⟪files_to_update = exists
    The below pseudo code files were updated, please update the corresponding native code files.
    Respond with full content of the native code files.

    ⟪files_to_update⟫
    ⟫

    ⟪files_to_create = exists
    I am attaching new pseudo code files. I need you to convert them into native code files.

    ⟪files_to_create⟫
    ⟫

    ---

    Please provide the full native code files for the below pseudo code files.

    ⟪files_list⟫
""".trimIndent()


/*
 "files_list" -> " - js/main.js\n - js/path.js\n - js/enemy.js\n - js/wave.js\n - js/ui/toolbar.js\n - js/tower.js\n - js/gameover.js\n - index.html\n - css/game-styles.css"
"files_to_update" -> "### js/main.js\n            ```pseudo\n            import js/scene.js (GameScene)\nimport js/ui/toolbar.js (ToolbarUI)\n\n- Purpose: Main entry point to initialize the Pitcher game, configure canvas, and start game logic.\n- Methods:\n  - init():\n    - Set up HTML canvas for rendering.\n    - Initialize the game engine with canvas dimensions.\n    - Create instances for game scene and toolbar UI.\n  - startGameLoop():\n    - Initiates animation loop for continuous game rendering and updates.\n    - Calls update and render functions at each iteration.\n  - update():\n    - Updates game logic such as enemy movement and tower interactions.\n  - render():\n    - Clears the canvas and redraws the game scene components.\n            ```\n            \n            ```native\n            document.addEventListener("DOMContentLoaded", function () {\n    initializeGame();\n\n    function initializeGame() {\n        const canvas = document.getElementById('gameCanvas');\n        const context = canvas.getContext('2d');\n   "
"files_to_create" -> ""
"prompt" -> ""
"main_file" -> null
"main_file_name" -> null
"project_details" -> null
"project_name" -> null
"files" -> null
"schemas" -> null
 */