package com.bulifier.core.models.questions

import com.bulifier.core.ai.MessageRequest
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.models.ApiModel
import com.bulifier.core.models.ApiResponse
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.ui.utils.Question

const val CLASS_GROUP_DEBUG = "DEBUG_GROUP"

class DebugQuestionsModel() : QuestionsModel(
    CLASS_GROUP_DEBUG,
    listOf(
        Question("Your prompt will be the response", options = listOf("Got it"))
    )
) {
    override val modelName = "echo"

    override fun createApiModel() = object : ApiModel {
        override suspend fun sendMessage(
            request: MessageRequest,
            historyITem: HistoryItem,
            id: Long
        ): ApiResponse {
            return ApiResponse(request.messages.filter {
                it.role == "user"
            }.joinToString {
                it.content
            }, isDone = true)
        }

    }
}