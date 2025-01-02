package com.bulifier.core.models

import com.bulifier.core.ai.MessageRequest
import com.bulifier.core.db.HistoryItem

interface ApiModel {
    suspend fun sendMessage(
        request: MessageRequest,
        historyITem: HistoryItem,
        id: Long
    ): ApiResponse
}

data class ApiResponse(
    val message: String?,
    val isDone: Boolean = true, // Used for async requests where the response is not immediately available
    val error: Boolean = false // Used for async requests where the response is not immediately available
)