package com.socialfirstaidkit.core.llm.impl

import android.util.Log
import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.model.LlmMessage
import com.socialfirstaidkit.core.llm.model.LlmProviderType
import com.socialfirstaidkit.core.llm.model.LlmRequest
import com.socialfirstaidkit.core.llm.model.LlmResponse
import com.socialfirstaidkit.core.llm.model.LlmRole
import com.socialfirstaidkit.core.llm.model.TokenUsage
import com.socialfirstaidkit.core.llm.routing.CustomOpenAiProfiles
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
import javax.inject.Singleton

@Singleton
class CustomLlmProvider @Inject constructor(
    private val secureStorage: SecureStorage
) : LlmProvider {

    private companion object {
        const val TAG = "CustomLlmProvider"
        const val KEY_CUSTOM_NAME = "custom_llm_name"
        const val KEY_CUSTOM_BASE_URL = "custom_llm_base_url"
        const val KEY_CUSTOM_API_KEY = "custom_llm_api_key"
        const val KEY_CUSTOM_MODEL = "custom_llm_default_model"
    }

    override val providerName: String
        get() = secureStorage.getString(KEY_CUSTOM_NAME)?.takeIf { it.isNotBlank() } ?: "自定义大模型"

    override val providerType = LlmProviderType.CUSTOM

    override val defaultModel: String
        get() = secureStorage.getString(KEY_CUSTOM_MODEL)?.takeIf { it.isNotBlank() } ?: "custom-model"

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
        val stream: Boolean = false
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

    private fun isGpt5CompatibleModel(model: String): Boolean {
        return model.trim().startsWith("gpt-5", ignoreCase = true)
    }

    private fun getBaseUrl(): String {
        return secureStorage.getString(KEY_CUSTOM_BASE_URL).orEmpty().trim()
    }

    private fun getApiKey(): String {
        return secureStorage.getString(KEY_CUSTOM_API_KEY).orEmpty().trim()
    }
    private data class RuntimeConfig(
        val baseUrl: String,
        val apiKey: String,
        val model: String
    )

    private fun runtimeConfig(encodedModel: String): RuntimeConfig {
        val (profileId, decodedModel) = CustomOpenAiProfiles.decodeRoutedModel(encodedModel)
        val profile = CustomOpenAiProfiles.find(secureStorage, profileId)
        return RuntimeConfig(
            baseUrl = profile?.baseUrl?.trim().orEmpty().ifBlank { getBaseUrl() },
            apiKey = profile?.apiKey?.trim().orEmpty().ifBlank { getApiKey() },
            model = decodedModel.trim().ifBlank { profile?.model?.trim().orEmpty().ifBlank { defaultModel } }
        )
    }
    override suspend fun chat(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val config = runtimeConfig(request.model)
        val apiKey = config.apiKey
        val baseUrl = config.baseUrl

        if (apiKey.isBlank()) {
            return@withContext LlmResponse.Error("NO_API_KEY", "自定义 API Key 不能为空")
        }
        if (baseUrl.isBlank()) {
            return@withContext LlmResponse.Error("NO_BASE_URL", "自定义 API 地址不能为空")
        }

        val hasImageInput = request.messages.any { !it.imageUrl.isNullOrBlank() }

        try {
            val messages = request.messages.map { it.toChatCompletionMessage() }
            val requestBody = if (isGpt5CompatibleModel(config.model)) {
                buildJsonObject {
                    put("model", config.model)
                    put("temperature", request.temperature)
                    put("messages", buildJsonArray {
                        messages.forEach { message ->
                            add(buildJsonObject {
                                put("role", message.role)
                                put("content", message.content)
                            })
                        }
                    })
                }.toString().toRequestBody("application/json".toMediaType())
            } else {
                val completionRequest = ChatCompletionRequest(
                    model = config.model,
                    messages = messages,
                    maxTokens = request.maxTokens,
                    temperature = request.temperature,
                    stream = false
                )

                json.encodeToString(
                    ChatCompletionRequest.serializer(),
                    completionRequest
                ).toRequestBody("application/json".toMediaType())
            }

            val httpRequest = Request.Builder()
                .url(chatCompletionsUrl(baseUrl))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()
                    ?: return@withContext LlmResponse.Error("NETWORK_ERROR", "收到空数据响应")

                Log.d(
                    TAG,
                    "chat response http=${response.code} bodyPreview=${responseBody.take(300)}"
                )

                if (!response.isSuccessful) {
                    return@withContext LlmResponse.Error(
                        "HTTP_${response.code}",
                        "自定义 API 请求失败: $responseBody"
                    )
                }
                if (responseBody.isHtmlResponse()) {
                    return@withContext LlmResponse.Error(
                        "INVALID_API_BASE_URL",
                        "自定义 API 地址返回的是网页 HTML，不是模型接口响应。Base URL 可能少了 /v1，请按服务商文档填写 API 根地址。"
                    )
                }

                val parsed = parseChatCompletionResponse(responseBody)
                    ?: if (hasImageInput) {
                        return@withContext LlmResponse.Error(
                            "EMPTY_IMAGE_RESPONSE",
                            "自定义图文模型响应中未包含有效文本内容"
                        )
                    } else {
                        return@withContext LlmResponse.Error("EMPTY_RESPONSE", "自定义 API 响应中未包含有效文本内容")
                    }

                LlmResponse.Complete(content = parsed.content, usage = parsed.usage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "chat exception", e)
            LlmResponse.Error("EXCEPTION", e.message ?: "未知自定义请求异常")
        }
    }

    override fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk> = flow {
        val config = runtimeConfig(request.model)
        val apiKey = config.apiKey
        val baseUrl = config.baseUrl

        if (apiKey.isBlank()) {
            emit(LlmResponse.StreamChunk(delta = "Error: 自定义 API Key 不能为空", finished = true))
            return@flow
        }
        if (baseUrl.isBlank()) {
            emit(LlmResponse.StreamChunk(delta = "Error: 自定义 API 地址不能为空", finished = true))
            return@flow
        }

        try {
            val completionRequest = ChatCompletionRequest(
                model = config.model,
                messages = request.messages.map { it.toChatCompletionMessage() },
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                stream = true
            )

            val requestBody = json.encodeToString(
                ChatCompletionRequest.serializer(),
                completionRequest
            ).toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(chatCompletionsUrl(baseUrl))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string().orEmpty()
                    emit(
                        LlmResponse.StreamChunk(
                            delta = "Error: 自定义 API 请求失败: $responseBody",
                            finished = true
                        )
                    )
                    return@use
                }

                val reader = response.body?.charStream()?.buffered()
                if (reader == null) {
                    emit(LlmResponse.StreamChunk(delta = "Error: 收到空数据流响应", finished = true))
                    return@use
                }
                emitStreamingResponse(reader)
            }
        } catch (e: Exception) {
            Log.e(TAG, "chat stream exception", e)
            emit(LlmResponse.StreamChunk(delta = "Error: ${e.message ?: "未知流式请求异常"}", finished = true))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun validateApiKey(): Boolean {
        return CustomOpenAiProfiles.load(secureStorage).any { it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() } ||
            (getApiKey().isNotBlank() && getBaseUrl().isNotBlank())
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
            throw IllegalStateException(it.message ?: "未知自定义 API 错误")
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
                    throw IllegalStateException(it.message ?: "未知自定义流式接口错误")
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
                        throw IllegalStateException(it.message ?: "未知自定义流错误")
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
            emit(LlmResponse.StreamChunk(delta = "Error: 未收到有效数据流响应", finished = true))
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

    private fun chatCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/chat/completions", ignoreCase = true)) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }

    private fun String.isHtmlResponse(): Boolean {
        val trimmed = trimStart()
        return trimmed.startsWith("<!doctype", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
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
