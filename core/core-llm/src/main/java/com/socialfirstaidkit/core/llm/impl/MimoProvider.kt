package com.socialfirstaidkit.core.llm.impl

import android.util.Log
import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.model.LlmMessage
import com.socialfirstaidkit.core.llm.model.LlmProviderType
import com.socialfirstaidkit.core.llm.model.LlmRequest
import com.socialfirstaidkit.core.llm.model.LlmResponse
import com.socialfirstaidkit.core.llm.model.LlmRole
import com.socialfirstaidkit.core.llm.model.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class MimoProvider @Inject constructor(
    @Named("mimo_base_url") private val injectedBaseUrl: String,
    @Named("mimo_api_key") private val injectedApiKey: String
) : LlmProvider {

    private companion object {
        const val TAG = "MimoProvider"
        const val MODEL_PRO = "mimo-v2.5-pro"
        const val MODEL_OMNI = "mimo-v2.5"
    }

    override val providerName = "小米 MiMo 大模型"
    override val providerType = LlmProviderType.MIMO
    override val defaultModel = MODEL_PRO

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Serializable
    data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatCompletionMessage>,
        @SerialName("max_completion_tokens")
        val maxCompletionTokens: Int = 1024,
        val temperature: Float = 1.0f,
        @SerialName("top_p")
        val topP: Float = 0.95f,
        val stream: Boolean = false,
        val thinking: ThinkingConfig = ThinkingConfig()
    )

    @Serializable
    data class ThinkingConfig(
        val type: String = "disabled"
    )

    @Serializable
    data class ChatCompletionMessage(
        val role: String,
        val content: JsonElement
    )

    @Serializable
    data class ChatCompletionResponse(
        val choices: List<ChatChoice>? = null,
        val usage: ChatUsage? = null,
        val error: MimoError? = null
    )

    @Serializable
    data class ChatChoice(
        val message: ChatMessage? = null,
        val delta: ChatMessage? = null
    )

    @Serializable
    data class ChatMessage(
        val content: String? = null
    )

    @Serializable
    data class ChatUsage(
        @SerialName("prompt_tokens")
        val promptTokens: Int = 0,
        @SerialName("completion_tokens")
        val completionTokens: Int = 0,
        @SerialName("total_tokens")
        val totalTokens: Int = 0
    )

    @Serializable
    data class MimoError(
        val message: String? = null,
        val code: String? = null
    )

    data class ParsedChatCompletion(
        val content: String,
        val usage: TokenUsage = TokenUsage()
    )

    override suspend fun chat(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val apiKey = configuredApiKey()
        val baseUrl = configuredBaseUrl()
        if (apiKey.isBlank()) {
            return@withContext LlmResponse.Error("NO_API_KEY", "MiMo API key is empty")
        }

        val hasImageInput = request.messages.any { !it.imageUrl.isNullOrBlank() }
        // 如果包含图片输入，自动切换为 mimo-v2.5 多模态模型；否则使用传入的模型或默认的 mimo-v2.5-pro
        val activeModel = when {
            hasImageInput -> MODEL_OMNI
            request.model.isBlank() || request.model == defaultModel -> defaultModel
            else -> request.model
        }

        try {
            val completionRequest = ChatCompletionRequest(
                model = activeModel,
                messages = request.messages.map { it.toChatCompletionMessage() },
                maxCompletionTokens = request.maxTokens,
                // 朋友圈文案需要直接输出短 JSON，关闭 thinking 可避免 token 花在推理内容里导致 content 为空。
                temperature = request.temperature,
                stream = false
            )

            val requestBody = json.encodeToString(
                ChatCompletionRequest.serializer(),
                completionRequest
            ).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(chatCompletionsUrl(baseUrl))
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Log.d(
                TAG,
                "chat request model=$activeModel hasImage=$hasImageInput thinking=disabled"
            )

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()
                    ?: return@withContext LlmResponse.Error("NETWORK_ERROR", "Empty MiMo response body")

                Log.d(TAG, "chat response http=${response.code}")

                if (!response.isSuccessful) {
                    return@withContext LlmResponse.Error(
                        "HTTP_${response.code}",
                        "MiMo API failed: $responseBody"
                    )
                }

                val parsed = parseChatCompletionResponse(responseBody)
                    ?: if (hasImageInput) {
                        return@withContext LlmResponse.Error(
                            "EMPTY_IMAGE_RESPONSE",
                            "MiMo image response was empty"
                        )
                    } else {
                        return@withContext LlmResponse.Error("EMPTY_RESPONSE", "No content in MiMo response")
                    }

                LlmResponse.Complete(content = parsed.content, usage = parsed.usage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "chat exception", e)
            LlmResponse.Error("EXCEPTION", e.message ?: "Unknown MiMo exception")
        }
    }

    override fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk> = flow {
        val apiKey = configuredApiKey()
        val baseUrl = configuredBaseUrl()
        if (apiKey.isBlank()) {
            emit(LlmResponse.StreamChunk(delta = "Error: MiMo API key is empty", finished = true))
            return@flow
        }

        val activeModel = if (request.model.isBlank() || request.model == defaultModel) defaultModel else request.model

        try {
            val completionRequest = ChatCompletionRequest(
                model = activeModel,
                messages = request.messages.map { it.toChatCompletionMessage() },
                maxCompletionTokens = request.maxTokens,
                temperature = request.temperature,
                stream = true
            )

            val requestBody = json.encodeToString(
                ChatCompletionRequest.serializer(),
                completionRequest
            ).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(chatCompletionsUrl(baseUrl))
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Log.d(
                TAG,
                "chat stream request model=$activeModel hasImage=false thinking=disabled"
            )

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string().orEmpty()
                    emit(
                        LlmResponse.StreamChunk(
                            delta = "Error: MiMo API failed: $responseBody",
                            finished = true
                        )
                    )
                    return@use
                }

                val reader = response.body?.charStream()?.buffered()
                if (reader == null) {
                    emit(LlmResponse.StreamChunk(delta = "Error: Empty MiMo response body", finished = true))
                    return@use
                }
                emitStreamingResponse(reader)
            }
        } catch (e: Exception) {
            Log.e(TAG, "chat stream exception", e)
            emit(LlmResponse.StreamChunk(delta = "Error: ${e.message ?: "Unknown MiMo stream exception"}", finished = true))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun validateApiKey(): Boolean {
        return configuredApiKey().isNotBlank() && configuredBaseUrl().isNotBlank()
    }

    private fun configuredApiKey(): String = injectedApiKey.trim()

    private fun configuredBaseUrl(): String =
        injectedBaseUrl.trim().ifBlank { "https://api.xiaomimimo.com" }

    private fun parseChatCompletionResponse(responseBody: String): ParsedChatCompletion? {
        return if (responseBody.trimStart().startsWith("data:")) {
            parseServerSentEvents(responseBody)
        } else {
            parseChatCompletionJson(responseBody)
        }
    }

    private fun parseChatCompletionJson(responseBody: String): ParsedChatCompletion? {
        val completionResponse = json.decodeFromString<ChatCompletionResponse>(responseBody)
        completionResponse.error?.let {
            throw IllegalStateException(it.message ?: "Unknown MiMo API error")
        }

        val content = completionResponse.choices
            ?.asSequence()
            ?.mapNotNull { it.message?.content ?: it.delta?.content }
            ?.joinToString("\n")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return ParsedChatCompletion(
            content = content,
            usage = completionResponse.usage.toTokenUsage()
        )
    }

    private fun parseServerSentEvents(responseBody: String): ParsedChatCompletion? {
        val contentBuilder = StringBuilder()
        var usage = TokenUsage()

        responseBody.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotBlank() && it != "[DONE]" }
            .forEach { payload ->
                val chunk = json.decodeFromString<ChatCompletionResponse>(payload)
                chunk.error?.let {
                    throw IllegalStateException(it.message ?: "Unknown MiMo API stream error")
                }
                chunk.usage?.let {
                    usage = it.toTokenUsage()
                }
                chunk.choices.orEmpty().forEach { choice ->
                    val delta = choice.delta?.content ?: choice.message?.content
                    if (!delta.isNullOrBlank()) {
                        contentBuilder.append(delta)
                    }
                }
            }

        val content = contentBuilder.toString().trim().takeIf { it.isNotBlank() }
            ?: return null
        return ParsedChatCompletion(content = content, usage = usage)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LlmResponse.StreamChunk>.emitStreamingResponse(
        reader: BufferedReader
    ) {
        var emittedAny = false
        reader.useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .filter { it.isNotBlank() }
                .forEach { payload ->
                    if (payload == "[DONE]") {
                        emit(LlmResponse.StreamChunk(delta = "", finished = true))
                        return
                    }
                    val chunk = json.decodeFromString<ChatCompletionResponse>(payload)
                    chunk.error?.let {
                        throw IllegalStateException(it.message ?: "Unknown MiMo API stream error")
                    }
                    chunk.choices.orEmpty().forEach { choice ->
                        val delta = choice.delta?.content ?: choice.message?.content
                        if (!delta.isNullOrEmpty()) {
                            emittedAny = true
                            emit(LlmResponse.StreamChunk(delta = delta, finished = false))
                        }
                    }
                }
        }
        if (!emittedAny) {
            emit(LlmResponse.StreamChunk(delta = "Error: Empty MiMo stream response", finished = true))
        } else {
            emit(LlmResponse.StreamChunk(delta = "", finished = true))
        }
    }

    private fun ChatUsage?.toTokenUsage(): TokenUsage {
        return this?.let {
            TokenUsage(
                promptTokens = it.promptTokens,
                completionTokens = it.completionTokens,
                totalTokens = it.totalTokens
            )
        } ?: TokenUsage()
    }

    private fun chatCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/v1")) {
            "$normalized/chat/completions"
        } else {
            "$normalized/v1/chat/completions"
        }
    }

    private fun LlmMessage.toChatCompletionMessage(): ChatCompletionMessage {
        return ChatCompletionMessage(
            role = when (role) {
                LlmRole.SYSTEM -> "system"
                LlmRole.USER -> "user"
                LlmRole.ASSISTANT -> "assistant"
            },
            content = imageUrl?.takeIf { it.isNotBlank() }?.let { image ->
                buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", content)
                    })
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", normalizeImageUrl(image))
                        })
                    })
                }
            } ?: JsonPrimitive(content)
        )
    }

    private fun normalizeImageUrl(image: String): String {
        return if (image.startsWith("http", ignoreCase = true) ||
            image.startsWith("data:image/", ignoreCase = true)
        ) {
            image
        } else {
            "data:image/jpeg;base64,$image"
        }
    }
}
