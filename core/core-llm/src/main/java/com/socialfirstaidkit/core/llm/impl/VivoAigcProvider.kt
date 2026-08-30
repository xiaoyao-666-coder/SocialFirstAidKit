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
class VivoAigcProvider @Inject constructor(
    @Named("vivo_aigc_base_url") private val baseUrl: String,
    @Named("vivo_aigc_api_key") private val apiKey: String
) : LlmProvider {

    private companion object {
        const val TAG = "VivoAigcProvider"
    }

    override val providerName = "Vivo AIGC"
    override val providerType = LlmProviderType.VIVO
    override val defaultModel = "Volc-DeepSeek-V3.2"

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
        @SerialName("max_tokens")
        val maxTokens: Int = 1024,
        val temperature: Float = 0.7f,
        val stream: Boolean = false,
        @SerialName("reasoning_effort")
        val reasoningEffort: String? = null
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
        val error: OpenAiError? = null
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
    data class OpenAiError(
        val message: String? = null,
        val code: String? = null
    )

    data class ParsedChatCompletion(
        val content: String,
        val usage: TokenUsage = TokenUsage()
    )

    override suspend fun chat(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext LlmResponse.Error("NO_API_KEY", "vivo AIGC API key is empty")
        }

        val hasImageInput = request.messages.any { !it.imageUrl.isNullOrBlank() }
        val systemContent = request.messages.firstOrNull { it.role == LlmRole.SYSTEM }?.content.orEmpty()
        val needMinimalReasoning = systemContent.contains("JSON") || systemContent.contains("OCR") || systemContent.contains("文案")

        try {
            val completionRequest = ChatCompletionRequest(
                model = request.model.ifEmpty { defaultModel },
                messages = request.messages.map { it.toChatCompletionMessage() },
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                stream = false,
                reasoningEffort = if (request.model.contains("deepseek", ignoreCase = true) && needMinimalReasoning) "minimal" else null
            )

            val requestBody = json.encodeToString(
                ChatCompletionRequest.serializer(),
                completionRequest
            ).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()
                    ?: return@withContext LlmResponse.Error("NETWORK_ERROR", "Empty vivo AIGC response body")

                Log.d(
                    TAG,
                    "chat response http=${response.code} bodyPreview=${responseBody.take(300)}"
                )

                if (!response.isSuccessful) {
                    return@withContext LlmResponse.Error(
                        "HTTP_${response.code}",
                        "vivo AIGC API failed: $responseBody"
                    )
                }

                val parsed = parseChatCompletionResponse(responseBody)
                    ?: if (hasImageInput) {
                        return@withContext LlmResponse.Error(
                            "EMPTY_IMAGE_RESPONSE",
                            "vivo AIGC image response was empty"
                        )
                    } else {
                        return@withContext LlmResponse.Error("EMPTY_RESPONSE", "No content in vivo AIGC response")
                    }

                LlmResponse.Complete(content = parsed.content, usage = parsed.usage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "chat exception", e)
            LlmResponse.Error("EXCEPTION", e.message ?: "Unknown vivo AIGC exception")
        }
    }

    override fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk> = flow {
        if (apiKey.isBlank()) {
            emit(LlmResponse.StreamChunk(delta = "Error: vivo AIGC API key is empty", finished = true))
            return@flow
        }

        val systemContent = request.messages.firstOrNull { it.role == LlmRole.SYSTEM }?.content.orEmpty()
        val needMinimalReasoning = systemContent.contains("JSON") || systemContent.contains("OCR") || systemContent.contains("文案")

        try {
            val completionRequest = ChatCompletionRequest(
                model = request.model.ifEmpty { defaultModel },
                messages = request.messages.map { it.toChatCompletionMessage() },
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                stream = true,
                reasoningEffort = if (request.model.contains("deepseek", ignoreCase = true) && needMinimalReasoning) "minimal" else null
            )

            val requestBody = json.encodeToString(
                ChatCompletionRequest.serializer(),
                completionRequest
            ).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string().orEmpty()
                    emit(
                        LlmResponse.StreamChunk(
                            delta = "Error: vivo AIGC API failed: $responseBody",
                            finished = true
                        )
                    )
                    return@use
                }

                val reader = response.body?.charStream()?.buffered()
                if (reader == null) {
                    emit(LlmResponse.StreamChunk(delta = "Error: Empty vivo AIGC response body", finished = true))
                    return@use
                }
                emitStreamingResponse(reader)
            }
        } catch (e: Exception) {
            Log.e(TAG, "chat stream exception", e)
            emit(LlmResponse.StreamChunk(delta = "Error: ${e.message ?: "Unknown vivo AIGC stream exception"}", finished = true))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun validateApiKey(): Boolean {
        return apiKey.isNotBlank()
    }

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
            throw IllegalStateException(it.message ?: "Unknown vivo AIGC API error")
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
                    throw IllegalStateException(it.message ?: "Unknown vivo AIGC API stream error")
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
                        throw IllegalStateException(it.message ?: "Unknown vivo AIGC API stream error")
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
            emit(LlmResponse.StreamChunk(delta = "Error: Empty vivo AIGC stream response", finished = true))
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
