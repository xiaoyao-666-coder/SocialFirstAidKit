package com.socialfirstaidkit.core.llm.impl

import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.collections.HashMap

/**
 * 蓝心大模型 (Vivo BlueLM) Provider
 *
 * 认证方式: AppId + AppKey
 * Base URL: https://api-ai.vivo.com.cn/vic-itp/vivogpt/completions
 */
@Singleton
class LanxinProvider @Inject constructor(
    @Named("lanxin_app_id") private val appId: String,
    @Named("lanxin_app_key") private val appKey: String,
    @Named("default_okhttp") private val okHttpClient: OkHttpClient
) : LlmProvider {

    override val providerName = "蓝心大模型"
    override val providerType = LlmProviderType.LANXIN
    override val defaultModel = "vivogpt-7b-chat"

    private val baseUrl = "https://api-ai.vivo.com.cn/vic-itp/vivogpt/completions"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class LanxinMessage(
        val role: String,
        val content: String
    )

    @Serializable
    data class LanxinRequest(
        val messages: List<LanxinMessage>,
        val model: String? = null,
        val requestId: String = UUID.randomUUID().toString()
    )

    @Serializable
    data class LanxinResponse(
        val code: Int = 0,
        val msg: String = "",
        val data: LanxinData? = null
    )

    @Serializable
    data class LanxinData(
        val content: String = "",
        val usage: LanxinUsage? = null
    )

    @Serializable
    data class LanxinUsage(
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0
    )

    override suspend fun chat(request: LlmRequest): LlmResponse {
        val lanxinRequest = LanxinRequest(
            messages = request.messages.map {
                LanxinMessage(
                    role = it.role.name.lowercase(),
                    content = it.content
                )
            },
            model = request.model.ifEmpty { defaultModel }
        )

        val requestBody = json.encodeToString(LanxinRequest.serializer(), lanxinRequest)
            .toRequestBody("application/json".toMediaType())

        // 蓝心大模型通常需要在 Header 中包含 AppId 和 AppKey
        // 请求签名由当前蓝心接口参数生成。
        val httpRequest = Request.Builder()
            .url(baseUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("AppId", appId)
            .addHeader("AppKey", appKey)
            .post(requestBody)
            .build()

        return try {
            val response = okHttpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()
                ?: return LlmResponse.Error("NETWORK_ERROR", "Empty response body")

            if (!response.isSuccessful) {
                return LlmResponse.Error(
                    "HTTP_${response.code}",
                    "API request failed: ${response.code} - $responseBody"
                )
            }

            val lanxinResponse = json.decodeFromString<LanxinResponse>(responseBody)

            if (lanxinResponse.code != 0) {
                return LlmResponse.Error(
                    lanxinResponse.code.toString(),
                    lanxinResponse.msg
                )
            }

            val content = lanxinResponse.data?.content
                ?: return LlmResponse.Error("EMPTY_RESPONSE", "No content in response")

            val usage = lanxinResponse.data.usage?.let {
                TokenUsage(
                    promptTokens = it.promptTokens,
                    completionTokens = it.completionTokens,
                    totalTokens = it.totalTokens
                )
            } ?: TokenUsage()

            LlmResponse.Complete(content = content, usage = usage)
        } catch (e: Exception) {
            LlmResponse.Error("EXCEPTION", e.message ?: "Unknown error")
        }
    }

    override fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk> = flow {
        // 当前调用使用非流式端点。
        val response = chat(request)
        if (response is LlmResponse.Complete) {
            emit(LlmResponse.StreamChunk(delta = response.content, finished = true))
        } else if (response is LlmResponse.Error) {
            emit(LlmResponse.StreamChunk(delta = "Error: ${response.message}", finished = true))
        }
    }

    override suspend fun validateApiKey(): Boolean {
        return appId.isNotEmpty() && appKey.isNotEmpty()
    }
}
