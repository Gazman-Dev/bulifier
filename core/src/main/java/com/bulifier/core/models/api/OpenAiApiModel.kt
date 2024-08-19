package com.bulifier.core.models.api

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.bulifier.core.api.MessageRequest
import com.bulifier.core.models.ApiModel
import kotlin.time.Duration.Companion.minutes

class OpenAiApiModel(openAiKey: String, openAiOrg: String, private val modelName: String) : ApiModel {


    private val openAI = OpenAI(
        token = openAiKey,
        organization = openAiOrg,
        timeout = Timeout(socket = 2.minutes),
    )

    override suspend fun sendMessage(request: MessageRequest): String? {
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(modelName),
            messages = request.messages.map {
                ChatMessage(
                    role = if (it.role == "user") {
                        ChatRole.User
                    } else {
                        ChatRole.System
                    },
                    content = it.content
                )
            }
        )
        val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
        return completion.choices[0].message.content
    }
}