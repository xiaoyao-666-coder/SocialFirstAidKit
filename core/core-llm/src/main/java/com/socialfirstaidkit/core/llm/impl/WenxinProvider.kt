package com.socialfirstaidkit.core.llm.impl

import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * 文心一言 LLM Provider
 *
 * 认证方式：API Key + Secret Key → Access Token（30天有效）
 * Base URL: https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/
 */
@Singleton
class WenxinProvider @Inject constructor(
    @Named("wenxin_api_key") private val apiKey: String,
    @Named("wenxin_secret_key") private val secretKey: String,
    @Named("default_okhttp") private val okHttpClient: OkHttpClient
) : LlmProvider {

    override val providerName = "文心一言"
    override val providerType = LlmProviderType.WENXIN
    override val defaultModel = "ernie-4.5-8k"

    private val tokenUrl = "https://aip.baidubce.com/oauth/2.0/token"
    private val chatBaseUrl = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Token 缓存
    private var cachedAccessToken: String? = null
    private var tokenExpireTime: Long = 0

    // ========== 文心一言格式数据类 ==========

    @Serializable
    data class WenxinMessage(
        val role: String,
        val content: String
    )

    @Serializable
    data class WenxinRequest(
        val messages: List<WenxinMessage>
    )

    @Serializable
    data class WenxinResponse(
        val result: String? = null,
        val error_code: Int? = null,
        val error_msg: String? = null,
        val usage: WenxinUsage? = null
    )

    @Serializable
    data class WenxinUsage(
        val prompt_tokens: Int = 0,
        val completion_tokens: Int = 0,
        val total_tokens: Int = 0
    )

    @Serializable
    data class TokenResponse(
        val access_token: String? = null,
        val expires_in: Long = 0,
        val error: String? = null,
        val error_description: String? = null
    )

    /**
     * 获取 Access Token（带缓存）
     */
    private suspend fun getAccessToken(): String {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return cachedAccessToken!!
        }

        val url = "$tokenUrl?grant_type=client_credentials&client_id=$apiKey&client_secret=$secretKey"
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw IllegalStateException("Failed to get access token: empty response")

        val tokenResponse = json.decodeFromString<TokenResponse>(body)

        tokenResponse.error?.let {
            throw IllegalStateException("Failed to get access token: $it - ${tokenResponse.error_description}")
        }

        val token = tokenResponse.access_token
            ?: throw IllegalStateException("Failed to get access token: no token in response")

        cachedAccessToken = token
        // 提前 1 小时过期，避免边界问题
        tokenExpireTime = System.currentTimeMillis() + (tokenResponse.expires_in * 1000) - 3600000

        return token
    }

    override suspend fun chat(request: LlmRequest): LlmResponse {
        val token = try {
            getAccessToken()
        } catch (e: Exception) {
            return LlmResponse.Error("TOKEN_ERROR", e.message ?: "Failed to get access token")
        }

        // 文心一言模型名映射
        val modelPath = when (request.model) {
            "ernie-4.5-8k" -> "ernie-4.5-8k"
            "ernie-4.0-8k" -> "ernie-4.0-8k"
            "ernie-speed-128k" -> "ernie-speed-128k"
            "ernie-lite-8k" -> "ernie-lite-8k"
            else -> defaultModel
        }

        val wenxinRequest = WenxinRequest(
            messages = request.messages.map {
                WenxinMessage(
                    role = it.role.name.lowercase(),
                    content = it.content
                )
            }
        )

        val requestBody = json.encodeToString(WenxinRequest.serializer(), wenxinRequest)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$chatBaseUrl/$modelPath?access_token=$token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: return LlmResponse.Error("NETWORK_ERROR", "Empty response body")

        if (!response.isSuccessful) {
            return LlmResponse.Error(
                "HTTP_${response.code}",
                "API request failed: ${response.code} - $responseBody"
            )
        }

        val wenxinResponse = try {
            json.decodeFromString(WenxinResponse.serializer(), responseBody)
        } catch (e: Exception) {
            return LlmResponse.Error("PARSE_ERROR", "Failed to parse response: ${e.message}")
        }

        // 检查错误
        wenxinResponse.error_code?.let { code ->
            return LlmResponse.Error(
                code.toString(),
                wenxinResponse.error_msg ?: "Unknown API error"
            )
        }

        val content = wenxinResponse.result
            ?: return LlmResponse.Error("EMPTY_RESPONSE", "No content in response")

        val usage = wenxinResponse.usage?.let {
            TokenUsage(
                promptTokens = it.prompt_tokens,
                completionTokens = it.completion_tokens,
                totalTokens = it.total_tokens
            )
        } ?: TokenUsage()

        return LlmResponse.Complete(content = content, usage = usage)
    }

    override fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk> = flow {
        val token = try {
            getAccessToken()
        } catch (e: Exception) {
            emit(LlmResponse.StreamChunk(delta = "", finished = true))
            return@flow
        }

        val modelPath = when (request.model) {
            "ernie-4.5-8k" -> "ernie-4.5-8k"
            "ernie-4.0-8k" -> "ernie-4.0-8k"
            "ernie-speed-128k" -> "ernie-speed-128k"
            "ernie-lite-8k" -> "ernie-lite-8k"
            else -> defaultModel
        }

        val wenxinRequest = WenxinRequest(
            messages = request.messages.map {
                WenxinMessage(
                    role = it.role.name.lowercase(),
                    content = it.content
                )
            }
        )

        val requestBody = json.encodeToString(WenxinRequest.serializer(), wenxinRequest)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$chatBaseUrl/$modelPath?access_token=$token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            emit(LlmResponse.StreamChunk(delta = "", finished = true))
            return@flow
        }

        val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (!currentLine.startsWith("data:")) continue

                val data = currentLine.removePrefix("data:").trim()
                if (data == "[DONE]") {
                    emit(LlmResponse.StreamChunk(delta = "", finished = true))
                    break
                }

                try {
                    val chunk = json.decodeFromString(WenxinResponse.serializer(), data)
                    val delta = chunk.result ?: ""
                    val finished = chunk.error_code != null
                    emit(LlmResponse.StreamChunk(delta = delta, finished = finished))
                } catch (_: Exception) {
                    // 跳过解析失败的行
                }
            }
        } finally {
            reader.close()
        }
    }

    override suspend fun validateApiKey(): Boolean {
        return try {
            val result = chat(
                LlmRequest(
                    model = defaultModel,
                    messages = listOf(
                        LlmMessage(role = LlmRole.USER, content = "hi")
                    ),
                    maxTokens = 5
                )
            )
            result is LlmResponse.Complete
        } catch (_: Exception) {
            false
        }
    }
}
