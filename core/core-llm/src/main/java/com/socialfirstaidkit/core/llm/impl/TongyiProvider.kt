package com.socialfirstaidkit.core.llm.impl

import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 通义千问 LLM Provider
 *
 * 支持多模态 (VL) 和 文本模型。
 * Base URL: https://dashscope.aliyuncs.com/compatible-mode/v1
 */
@Singleton
class TongyiProvider @Inject constructor(
    @Named("tongyi_api_key") private val apiKey: String
) : LlmProvider {

    override val providerName = "通义千问"
    override val providerType = LlmProviderType.TONGYI
    override val defaultModel = "qwen-plus-2025-01-25" // 使用最新的 Qwen-Plus

    private val baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // ========== 阿里多模态兼容格式数据类 ==========

    @Serializable
    data class OpenAiRequest(
        val model: String,
        val messages: List<JsonElement>,
        val max_tokens: Int = 1024,
        val temperature: Float = 0.7f,
        val stream: Boolean = false
    )

    @Serializable
    data class OpenAiResponse(
        val choices: List<OpenAiChoice>? = null,
        val usage: OpenAiUsage? = null,
        val error: OpenAiError? = null
    )

    @Serializable
    data class OpenAiChoice(
        val message: OpenAiMessage? = null,
        val delta: OpenAiMessage? = null,
        val finish_reason: String? = null
    )

    @Serializable
    data class OpenAiMessage(
        val role: String? = null,
        val content: String? = null
    )

    @Serializable
    data class OpenAiUsage(
        val prompt_tokens: Int = 0,
        val completion_tokens: Int = 0,
        val total_tokens: Int = 0
    )

    @Serializable
    data class OpenAiError(
        val message: String? = null,
        val code: String? = null
    )

    override suspend fun chat(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        try {
            val messages = request.messages.map { msg ->
                buildJsonObject {
                    put("role", (if (msg.role == LlmRole.SYSTEM) "system" else if (msg.role == LlmRole.USER) "user" else "assistant"))
                    
                    if (msg.imageUrl.isNullOrEmpty()) {
                        put("content", msg.content)
                    } else {
                        // 构建多模态 content 数组
                        put("content", buildJsonArray {
                            addJsonObject {
                                put("type", "text")
                                put("text", msg.content)
                            }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    // 阿里 VL 要求的 base64 格式
                                    put("url", "data:image/jpeg;base64,${msg.imageUrl}")
                                }
                            }
                        })
                    }
                }
            }

            val openAiRequest = OpenAiRequest(
                model = request.model.ifEmpty { defaultModel },
                messages = messages,
                max_tokens = request.maxTokens,
                temperature = request.temperature,
                stream = false
            )

            val requestBody = json.encodeToString(OpenAiRequest.serializer(), openAiRequest)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()
                ?: return@withContext LlmResponse.Error("NETWORK_ERROR", "Empty response body")

            if (!response.isSuccessful) {
                return@withContext LlmResponse.Error(
                    "HTTP_${response.code}",
                    "Tongyi API failed: $responseBody"
                )
            }

            val openAiResponse = json.decodeFromString<OpenAiResponse>(responseBody)

            openAiResponse.error?.let {
                return@withContext LlmResponse.Error(it.code ?: "API_ERROR", it.message ?: "Unknown error")
            }

            val content = openAiResponse.choices?.firstOrNull()?.message?.content
                ?: return@withContext LlmResponse.Error("EMPTY_RESPONSE", "No content in response")

            LlmResponse.Complete(content = content)
        } catch (e: Exception) {
            LlmResponse.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    override fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk> = flow {
        // 当前调用返回完整响应。
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
