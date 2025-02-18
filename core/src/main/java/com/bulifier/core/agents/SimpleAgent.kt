package com.bulifier.core.agents

import com.bulifier.core.ai.MessageData
import com.bulifier.core.models.ApiModel


class SimpleAgent(userMessage: String, private val apiModel: ApiModel) {
    private val messages = mutableListOf(MessageData("user", userMessage))

    suspend fun execute() {

    }
}