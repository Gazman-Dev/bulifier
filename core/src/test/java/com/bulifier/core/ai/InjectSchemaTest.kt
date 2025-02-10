package com.bulifier.core.ai

import junit.framework.TestCase.assertEquals
import org.junit.Test

class InjectSchemaTest {

    @Test
    fun `test simple schema injection`() {
        val result = injectSchema(userPrompt, mapOf("prompt" to "Play Ball"))
        assertEquals(userPromptInjected, result)
    }

    @Test
    fun `test conditional schema injection when satisfied`() {
        val result = injectSchema(userPromptWithCondition, mapOf(
            "prompt" to "Play Ball",
            "file" to "index.html",
            "schema" to "lazy",
        ))
        assertEquals(userPromptWithConditionInjected, result)
    }

    @Test
    fun `test conditional schema injection when not satisfied`() {
        val result = injectSchema(userPromptWithCondition, mapOf("prompt" to "Play Ball"))
        assertEquals(userPromptInjected, result)
    }

    @Test
    fun `test nested conditional schema injection`() {
        val result = injectSchema(nestedPromptWithCondition, mapOf(
            "prompt" to "Play Ball",
            "file" to "index.html",
            "schema" to "lazy",
        ))
        assertEquals(nestedPromptWithConditionInjected, result)
    }

    @Test
    fun `test vendors`() {
        val result = injectSchema(windowsControlChar, mapOf(
            "bullets_file_name" to "index.html.bul"
        ))
        assertEquals(windowsControlCharInjected, result)
    }
}

private val userPrompt = """
    # User
    ⟪prompt⟫
""".trimIndent()

private val userPromptInjected = """
    # User
    Play Ball
""".trimIndent()

private val userPromptWithCondition = """
    # User
    ⟪prompt⟫
    ⟪file = index.html and schema=lazy
    But not to far
    ⟫
""".trimIndent()

private val userPromptWithConditionInjected = """
    # User
    Play Ball
    But not to far
""".trimIndent()

private val nestedPromptWithCondition = """
    # User
    ⟪prompt⟫
    ⟪file = index.html and schema=lazy
    Be nice to ⟪file⟫
    ⟫
""".trimIndent()

private val nestedPromptWithConditionInjected = """
    # User
    Play Ball
    Be nice to index.html
""".trimIndent()


private val windowsControlChar = "" +
    "Please create a raw file and output its content based on the project details.\r\n" + 
    "⟪bullets_file_name = index.html.bul\r\n" +
    "Make sure the body contains the following comment so vendor dependencies get injected\r\n" +
    "<!-- Vendor libraries -->\r\n" +
    "⟫"


private val windowsControlCharInjected = """
    Please create a raw file and output its content based on the project details.
    Make sure the body contains the following comment so vendor dependencies get injected
    <!-- Vendor libraries -->
""".trimIndent()