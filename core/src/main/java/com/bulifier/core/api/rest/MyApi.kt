package com.bulifier.core.api.rest

import com.bulifier.core.api.MessageRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface MyApi {
    @POST("api/ai")
    suspend fun sendMessage(
        @Body request: MessageRequest
    ): Response<ResponseMessage>
}

data class ResponseMessage(val message:String)
