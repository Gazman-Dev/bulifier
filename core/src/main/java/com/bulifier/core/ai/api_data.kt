package com.bulifier.core.ai

import com.google.gson.annotations.SerializedName

data class MessageRequest(
    @SerializedName("messages")
    val messages: List<MessageData>,
)

data class MessageData(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String
)