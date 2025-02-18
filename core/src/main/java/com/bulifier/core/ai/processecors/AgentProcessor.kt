package com.bulifier.core.ai.processecors

import com.bulifier.core.ai.ResponseData
import com.bulifier.core.ai.parsers.AgentAction
import com.bulifier.core.ai.parsers.AgentCommand
import com.bulifier.core.ai.parsers.AgentData
import com.bulifier.core.ai.parsers.parseAgentResponse
import com.bulifier.core.db.AppDatabase
import com.bulifier.core.db.File
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.ui.main.actions.markForDeleteAction
import com.bulifier.core.ui.main.actions.moveAction
import com.bulifier.core.utils.Logger

class AgentProcessor(
    private val db: AppDatabase,
    private val logger: Logger,
    private val historyItem: HistoryItem
) {

    suspend fun processAgent(
        data: ResponseData
    ) {
        val agentData = parseAgentResponse(data.response)
        logger.d("Actions parsed: ${agentData.actions.size} Commands parsed: ${agentData.commands.size} for promptId=${historyItem.promptId}")

        db.fileDao().deleteFilesMarkedForDeletion(historyItem.projectId)
        executeActions(agentData.actions)
        executeAgentCommands(agentData, data.historyItem.isNativeCode)
    }

    private suspend fun executeActions(actions: List<AgentAction>) {
        for (action in actions) {
            when (action) {
                is AgentAction.Move -> {
                    logger.d("Executing move action: ${action.from} -> ${action.to}")

                    getFile(action.from)?.let { from ->
                        moveAction(from, action.to, db.fileDao(), logger)
                    } ?: logger.e("File not found in move action FROM")
                }

                is AgentAction.Delete -> {
                    logger.d("Executing delete action: ${action.file}")

                    getFile(action.file)?.let {
                        markForDeleteAction(it, db.fileDao(), logger)
                    } ?: logger.e("File not found in delete action")
                }
            }
        }
    }

    private suspend fun getFile(fileName: String): File? {
        return db.fileDao().getFile(
            fileName.substringBeforeLast("/", ""),
            fileName.substringAfterLast("/"),
            historyItem.projectId
        )
    }

    private suspend fun executeAgentCommands(data: AgentData, nativeCode: Boolean) {
        data.commands.forEach { agentCommand ->
            val contextFiles: List<Long> = extractContextFiles(agentCommand).run {
                if (isNotEmpty()) {
                    val dependenciesIds =
                        db.fileDao().getDependenciesIds(this, historyItem.projectId)
                    logger.d("Dependencies ids: $dependenciesIds")
                    this + dependenciesIds
                } else {
                    emptyList()
                }
            }
            val schema = if (agentCommand.command == "bulify" && nativeCode) {
                "bulify_native"
            } else {
                agentCommand.command
            }

            logger.d("Adding new history for agent command $schema with no files")
            db.historyDao().addHistory(
                historyItem.copy(
                    promptId = 0,
                    prompt = agentCommand.instructions,
                    schema = schema,
                    contextFiles = contextFiles,
                    status = HistoryStatus.SUBMITTED
                )
            )
        }
    }

    private suspend fun extractContextFiles(agentCommand: AgentCommand): List<Long> {
        val contextFiles = db.fileDao()
            .getFilesIds(agentCommand.context, historyItem.projectId)
        logger.d(
            "Context files ids: for ${agentCommand.context.joinToString()} project ${historyItem.projectId} -> ${
                contextFiles.joinToString(
                    ","
                )
            }"
        )
        return contextFiles
    }
}