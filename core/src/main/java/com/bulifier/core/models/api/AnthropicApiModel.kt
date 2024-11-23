package com.bulifier.core.models.api

import com.bulifier.core.api.MessageData
import com.bulifier.core.api.MessageRequest
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.models.ApiModel
import com.bulifier.core.models.ApiResponse
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit


class AnthropicApiModel(
    private val apiKey: String,
    private val model: String,
    private val anthropicVersion: String
) : ApiModel {
    private val client = OkHttpClient.Builder()
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()
    private val baseUrl = "https://api.anthropic.com/v1/messages"
    private val gson = Gson()

    override suspend fun sendMessage(request: MessageRequest, historyITem: HistoryItem, id: Long) =
        ApiResponse(withContext(Dispatchers.IO) {
            val requestBody = ConversationRequest(
                model = model,
                maxTokens = 4096,
                messages = request.messages.filter { it.role != "system" },
                system = request.messages.dropLast(1).filter { it.role == "system" }
                    .joinToString("\n") { it.content }
            )

            val apiRequest = Request.Builder()
                .url(baseUrl)
                .post(
                    gson.toJson(requestBody).toRequestBody("application/json".toMediaTypeOrNull())
                )
                .addHeader("content-type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", anthropicVersion)
                .build()

            val response = client.newCall(apiRequest).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                try {
                    val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)
                    apiResponse.content.firstOrNull { it.type == "text" }!!.text
                } catch (e: Exception) {
                    throwError(response, apiRequest, requestBody, responseBody)
                }
            } else {
                throwError(response, apiRequest, requestBody, responseBody)
            }
        })

    private fun throwError(
        response: Response,
        request: Request,
        requestBody: ConversationRequest,
        responseBody: String?
    ): String {
        throw IOException(
            """API error: ${response.code} 
                    |url: 
                    |${request.url}
                    |headers: 
                    |${request.headers.toString().replace(apiKey, "*****")}
                    |body:
                    |${gson.toJson(requestBody)}
                    |
                    |$responseBody
                    |
                    |$response""".trimMargin()
        )
    }

    data class ConversationRequest(
        @SerializedName("model")
        val model: String,

        @SerializedName("max_tokens")
        val maxTokens: Int,

        @SerializedName("messages")
        val messages: List<MessageData>,

        @SerializedName("system")
        val system: String? = null
    )

    data class ApiResponse(
        val id: String,
        val type: String,
        val role: String,
        val content: List<Content>,
        @SerializedName("stop_reason") val stopReason: String,
        @SerializedName("stop_sequence") val stopSequence: String?,
        val usage: Usage
    )

    data class Content(
        val type: String,
        val text: String
    )

    data class Usage(
        @SerializedName("input_tokens") val inputTokens: Int,
        @SerializedName("output_tokens") val outputTokens: Int
    )
}