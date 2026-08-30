package com.socialfirstaidkit.core.llm.impl

import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Google Gemini Provider
 *
 * 使用 Google AI SDK 兼容的 REST API
 * Base URL: https://generativelanguage.googleapis.com/v1beta/models/
 */
@Singleton
class GeminiProvider @Inject constructor(
    @Named("gemini_api_key") private val apiKey: String,
    @Named("default_okhttp") private val okHttpClient: OkHttpClient
) : LlmProvider {

    override val providerName = "Google Gemini"
    override val providerType = LlmProviderType.GEMINI
    override val defaultModel = "gemini-2.0-flash"

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ========== Gemini REST 格式数据类 ==========

    @Serializable
    data class GeminiRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null,
        val systemInstruction: Content? = null
    )

    @Serializable
    data class Content(
        val role: String? = null,
        val parts: List<Part>
    )

    @Serializable
    data class Part(
        val text: String? = null,
        val inlineData: InlineData? = null
    )

    @Serializable
    data class InlineData(
        val mimeType: String,
        val data: String // Base64 字符串
    )

    @Serializable
    data class GenerationConfig(
        val temperature: Float? = null,
        val maxOutputTokens: Int? = null
    )

    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate>? = null,
        val promptFeedback: PromptFeedback? = null,
        val error: GeminiError? = null
    )

    @Serializable
    data class Candidate(
        val content: Content? = null,
        val finishReason: String? = null
    )

    @Serializable
    data class PromptFeedback(
        val blockReason: String? = null
    )

    @Serializable
    data class GeminiError(
        val message: String? = null,
        val code: Int? = null,
        val status: String? = null
    )

    override suspend fun chat(request: LlmRequest): LlmResponse {
        val systemMessage = request.messages.find { it.role == LlmRole.SYSTEM }
        val userMessages = request.messages.filter { it.role != LlmRole.SYSTEM }

        val geminiRequest = GeminiRequest(
            contents = userMessages.map { message ->
                val partsList = mutableListOf<Part>()

                // 1. 如果有文字，塞入文字
                if (message.content.isNotEmpty()) {
                    partsList.add(Part(text = message.content))
                }

                // 2. 如果有图片，塞入图片（假设 imageUrl 存放的是 Base64）
                if (!message.imageUrl.isNullOrEmpty()) {
                    // 移除 data URI 前缀后提交 Base64 数据。
                    val base64Data = message.imageUrl.substringAfter("base64,")
                    partsList.add(
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data))
                    )
                }

                Content(
                    role = if (message.role == LlmRole.USER) "user" else "model",
                    parts = partsList
                )
            },
            systemInstruction = systemMessage?.let {
                Content(parts = listOf(Part(text = it.content)))
            },
            generationConfig = GenerationConfig(
                temperature = request.temperature,
                maxOutputTokens = request.maxTokens
            )
        )

        val modelName = request.model.ifEmpty { defaultModel }
        val url = "$baseUrl/$modelName:generateContent?key=$apiKey"

        val requestBody = json.encodeToString(GeminiRequest.serializer(), geminiRequest)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = okHttpClient.newCall(httpRequest).execute()
                val responseBody = response.body?.string()
                    ?: return@withContext LlmResponse.Error("NETWORK_ERROR", "Empty response body")

                if (!response.isSuccessful) {
                    return@withContext LlmResponse.Error(
                        "HTTP_${response.code}",
                        "Gemini API failed: $responseBody"
                    )
                }

                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)

                geminiResponse.error?.let {
                    return@withContext LlmResponse.Error(it.status ?: "API_ERROR", it.message ?: "Unknown error")
                }

                val content = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: return@withContext LlmResponse.Error("EMPTY_RESPONSE", "No content in response")

                LlmResponse.Complete(content = content)
            } catch (e: Exception) {
                android.util.Log.e("GeminiProvider", "🚨 REST API 彻底坠机", e)
                LlmResponse.Error("EXCEPTION", e.message ?: "Unknown error")
            }
        }
    }

    override fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk> = flow {
        // 当前接口返回完整响应。
        val response = chat(request)
        if (response is LlmResponse.Complete) {
            emit(LlmResponse.StreamChunk(delta = response.content, finished = true))
        } else if (response is LlmResponse.Error) {
            emit(LlmResponse.StreamChunk(delta = "Error: ${response.message}", finished = true))
        }
    }

    override suspend fun validateApiKey(): Boolean {
        return apiKey.isNotEmpty()
    }
}
