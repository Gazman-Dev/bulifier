package com.bulifier.core.ai

import junit.framework.TestCase.assertEquals
import org.junit.Test

class AgentParserTest {

    // Helper to validate AgentData
    private fun validateAgentData(
        result: AgentData,
        expectedCommands: List<AgentCommand>,
        expectedActions: List<AgentAction>
    ) {
        assertEquals("Command count mismatch", expectedCommands.size, result.commands.size)
        assertEquals("Action count mismatch", expectedActions.size, result.actions.size)

        // Validate commands
        for ((index, command) in expectedCommands.withIndex()) {
            val actualCommand = result.commands[index]
            assertEquals("Command name mismatch at $index", command.command, actualCommand.command)
            assertEquals("Command files mismatch at $index", command.files, actualCommand.files)
            assertEquals("Command context mismatch at $index", command.context, actualCommand.context)
            assertEquals("Command instructions mismatch at $index", command.instructions, actualCommand.instructions)
        }

        // Validate actions
        for ((index, action) in expectedActions.withIndex()) {
            val actualAction = result.actions[index]
            when (action) {
                is AgentAction.Move -> {
                    val actualMove = actualAction as AgentAction.Move
                    assertEquals("Move from mismatch at $index", action.from, actualMove.from)
                    assertEquals("Move to mismatch at $index", action.to, actualMove.to)
                }
                is AgentAction.Delete -> {
                    val actualDelete = actualAction as AgentAction.Delete
                    assertEquals("Delete file mismatch at $index", action.file, actualDelete.file)
                }
            }
        }
    }

    // Helper to create test input
    private fun generateInput(commands: List<AgentCommand>, actions: List<AgentAction>): String {
        val builder = StringBuilder()

        for (command in commands) {
            builder.appendLine("command: ${command.command}")
            if (command.files.isNotEmpty()) {
                builder.appendLine("files:")
                command.files.forEach { builder.appendLine(" - $it") }
            }
            if (command.context.isNotEmpty()) {
                builder.appendLine("context:")
                command.context.forEach { builder.appendLine(" - $it") }
            }
            if (command.instructions.isNotEmpty()) {
                builder.appendLine("instructions: ${command.instructions}")
            }
        }

        for (action in actions) {
            when (action) {
                is AgentAction.Move -> {
                    builder.appendLine("action: move")
                    builder.appendLine(" - ${action.from} ${action.to}")
                }
                is AgentAction.Delete -> {
                    builder.appendLine("action: delete")
                    builder.appendLine(" - ${action.file}")
                }
            }
        }
        return builder.toString().trim()
    }

    @Test
    fun `test all valid combinations of commands, actions, and context`() {
        val currentPath = ""
        val commandCases = listOf(
            emptyList<AgentCommand>(), // 0 commands
            listOf(AgentCommand(
                "cmd1",
                listOf("file1"),
                listOf("context1"),
                "instructions1",
                currentPath
            )), // 1 command
            listOf(
                AgentCommand(
                    "cmd1",
                    listOf("file1"),
                    listOf("context1", "context2"),
                    "instructions1",
                    currentPath
                ),
                AgentCommand(
                    "cmd2",
                    listOf("file2", "file3"),
                    listOf("context3"),
                    "instructions2",
                    currentPath
                )
            ) // 2 commands with context
        )

        val actionCases = listOf(
            emptyList<AgentAction>(), // 0 actions
            listOf(AgentAction.Delete("file1")), // 1 action
            listOf(
                AgentAction.Move("file1", "file1_new"),
                AgentAction.Delete("file2")
            ) // 2 actions
        )

        // Iterate over all combinations except (0, 0)
        for (commands in commandCases) {
            for (actions in actionCases) {
                if (commands.isEmpty() && actions.isEmpty()) continue // Skip 0,0 case

                val input = generateInput(commands, actions)
                val result = parseAgentResponse(input)

                // Validate result
                validateAgentData(result, commands, actions)
            }
        }
    }
}