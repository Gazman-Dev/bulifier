package com.bulifier.core.ai

data class AgentCommand(
    val command: String,
    val files: List<String>,
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
    val currentFiles = mutableListOf<String>()
    val currentContext = mutableListOf<String>()
    var currentInstructions = StringBuilder()
    var processingAction = false
    var currentActionType: String? = null
    var currentBlock: String? = null // Tracks the current section: "files", "context", "instructions", etc.
    var currentPath:String? = null

    for (line in lines) {
        when {
            line.lowercase().startsWith("command:") -> {
                // Finish previous command
                if (currentCommand != null) {
                    agentCommands.add(
                        AgentCommand(
                            currentCommand,
                            currentFiles.toList(),
                            currentContext.toList(),
                            currentInstructions.toString().trim(),
                            currentPath
                        )
                    )
                }
                // Start new command
                currentCommand = line.substringAfter(":").trim()
                currentFiles.clear()
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
                currentInstructions.append(line.substringAfter(":").trim()).append(" ")
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
                    "files" -> currentFiles.add(line.removePrefix("-").trim())
                    "context" -> currentContext.add(line.removePrefix("-").trim())
                }
            }

            line.isNotEmpty() && currentBlock == "instructions" -> {
                // Collect instructions explicitly
                currentInstructions.append(line).append(" ")
            }

            line.isNotEmpty() && currentBlock == null -> {
                // Lines outside of specific blocks are treated as instructions
                currentInstructions.append(line).append(" ")
            }
        }
    }

    // Add the last command if it exists
    if (currentCommand != null) {
        agentCommands.add(
            AgentCommand(
                currentCommand,
                currentFiles.toList(),
                currentContext.toList(),
                currentInstructions.toString().trim(),
                currentPath
            )
        )
    }

    return AgentData(agentCommands, agentActions)
}

