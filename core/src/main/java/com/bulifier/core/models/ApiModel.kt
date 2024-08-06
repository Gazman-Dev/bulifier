package com.bulifier.core.models

import com.bulifier.core.api.MessageRequest

interface ApiModel {
    suspend fun sendMessage(request: MessageRequest): String?
}