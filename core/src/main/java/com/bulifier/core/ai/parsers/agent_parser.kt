package com.bulifier.core.ai.parsers

data class AgentCommand(
    val command: String,
    val context: List<String>,
    val instructions: String,
    val path: String?
)

sealed class AgentAction {
    data class Move(val from: String, val to: String) : AgentAction()
    data class Delete(val file: String) : AgentAction()
}

data class AgentData(
    val commands: List<AgentCommand>,
    val actions: List<AgentAction>
)

fun parseAgentResponse(response: String): AgentData {
    val agentCommands = mutableListOf<AgentCommand>()
    val agentActions = mutableListOf<AgentAction>()

    val lines = response.lines().map { it.trim() }
    var currentCommand: String? = null
    val currentContext = mutableListOf<String>()
    var currentInstructions = mutableListOf<String>()
    var processingAction = false
    var currentActionType: String? = null
    var currentBlock: String? =
        null // Tracks the current section: "files", "context", "instructions", etc.
    var currentPath: String? = null

    for (line in lines) {
        when {
            line.lowercase().startsWith("command:") -> {
                // Finish previous command
                if (currentCommand != null) {
                    agentCommands.add(
                        AgentCommand(
                            currentCommand,
                            currentContext.toList(),
                            currentInstructions.toString().trim(),
                            currentPath
                        )
                    )
                }
                // Start new command
                currentCommand = line.substringAfter(":").trim()
                currentContext.clear()
                currentInstructions.clear()
                currentBlock = null
                processingAction = false
            }

            line.lowercase().startsWith("files:") -> {
                currentBlock = "files"
            }

            line.lowercase().startsWith("path:") -> {
                currentPath = line.substringAfter(":").trim()
            }

            line.lowercase().startsWith("context:") -> {
                currentBlock = "context"
            }

            line.lowercase().startsWith("instructions:") -> {
                currentBlock = "instructions"
                currentInstructions += (line.substringAfter(":").trim())
            }

            line.lowercase().startsWith("action:") -> {
                processingAction = true
                currentActionType = line.substringAfter(":").trim().lowercase()
                currentBlock = "action"
            }

            line.startsWith("-") && processingAction && currentActionType != null -> {
                // Handle actions
                val parts = line.removePrefix("-").trim().split(" ", limit = 2)
                when (currentActionType) {
                    "move" -> if (parts.size == 2) {
                        agentActions.add(AgentAction.Move(parts[0], parts[1]))
                    }

                    "delete" -> if (parts.size == 1) {
                        agentActions.add(AgentAction.Delete(parts[0]))
                    }
                }
            }

            line.startsWith("-") -> {
                // Add to current block
                when (currentBlock) {
                    "context" -> currentContext.add(normalizeFilePath(line))
                }
            }

            line.isNotEmpty() && currentBlock == "instructions" -> {
                // Collect instructions explicitly
                currentInstructions += line
            }

            line.isNotEmpty() && currentBlock == null -> {
                // Lines outside of specific blocks are treated as instructions
                currentInstructions += line
            }
        }
    }

    // Add the last command if it exists
    if (currentCommand != null) {
        agentCommands.add(
            AgentCommand(
                currentCommand,
                currentContext.toList(),
                currentInstructions.joinToString("\n").trim(),
                currentPath
            )
        )
    }

    return AgentData(agentCommands, agentActions)
}

private fun normalizeFilePath(pathLine: String): String {
    return pathLine.replace("""[^a-zA-Z_/.]+""".toRegex(), "").run {
        if (!this.endsWith(".bul")) {
            "$this.bul"
        } else {
            this
        }
    }
}

