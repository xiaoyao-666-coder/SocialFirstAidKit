package com.socialfirstaidkit.core.network.imagegen

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "ImageGenerationClient"

private data class OpenAiImageRuntimeConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String
)

private enum class MomentShareVisualMode {
    HAND_DRAWN,
    FRESH_DIARY,
    CUTE_STICKER,
    HIGHLIGHT_FOCUS,
    MINIMAL_CARD,
    GENERAL
}

@Serializable
private data class VivoImageGenerationRequest(
    @SerialName("model")
    val model: String,

    @SerialName("prompt")
    val prompt: String,

    @SerialName("image")
    val image: JsonElement? = null,

    @SerialName("parameters")
    val parameters: ImageGenerationParameters? = null
)

@Serializable
private data class AgnesImageGenerationRequest(
    @SerialName("model")
    val model: String,

    @SerialName("prompt")
    val prompt: String,

    @SerialName("size")
    val size: String,

    @SerialName("extra_body")
    val extraBody: AgnesImageExtraBody? = null
)

@Serializable
private data class AgnesImageExtraBody(
    @SerialName("image")
    val image: List<String>? = null,

    @SerialName("response_format")
    val responseFormat: String = "url"
)

@Serializable
private data class ArkImageGenerationResponse(
    @SerialName("data")
    val data: List<ArkGeneratedImage> = emptyList(),

    @SerialName("usage")
    val usage: ArkImageUsage? = null,

    @SerialName("error")
    val error: ArkError? = null
)

@Serializable
private data class ArkGeneratedImage(
    @SerialName("url")
    val url: String? = null,

    @SerialName("b64_json")
    val base64Json: String? = null,

    @SerialName("size")
    val size: String? = null
)

@Serializable
private data class ArkImageUsage(
    @SerialName("generated_images")
    val generatedImages: Int? = null,

    @SerialName("image_count")
    val imageCount: Int? = null,

    @SerialName("input_tokens")
    val inputTokens: Int? = null,

    @SerialName("output_tokens")
    val outputTokens: Int? = null,

    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

@Serializable
private data class ArkError(
    @SerialName("message")
    val message: String? = null
)

/**
 * 图片生成 API 客户端实现
 */
@Singleton
class ImageGenerationClient @Inject constructor(
    @Named("default_okhttp") private val okHttpClient: OkHttpClient,
    @Named("vivo_aigc_api_key") private val apiKey: String,
    @Named("agnes_base_url") private val agnesBaseUrl: String,
    @Named("agnes_api_key") private val agnesApiKey: String
) : ImageGenerationApi {

    private companion object {
        const val AGNES_FREE_IMAGE_MODEL = "agnes-image-2.0-flash"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val mediaType = "application/json".toMediaType()

    private val imageOkHttpClient = okHttpClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * vivo accepts URLs, data URLs, or bare base64 strings.
     */
    private fun normalizeImageInput(image: ImageGenerationInput?): ImageGenerationInput? {
        val normalizedImages = image?.images
            ?.mapNotNull { normalizeSingleImageInput(it) }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return ImageGenerationInput.multiple(normalizedImages)
    }

    private fun normalizeSingleImageInput(image: String): String? {
        val value = image.trim()
        if (value.isBlank()) return null

        return when {
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) ||
                value.startsWith("data:image/", ignoreCase = true) -> value
            else -> "data:image/jpeg;base64,$value"
        }
    }

    private fun ImageGenerationInput?.toImageJsonElement(): JsonElement? {
        val values = this?.images.orEmpty()
        return when {
            values.isEmpty() -> null
            values.size == 1 -> JsonPrimitive(values.first())
            else -> JsonArray(values.map { JsonPrimitive(it) })
        }
    }

    override suspend fun generateImage(request: ImageGenerationRequest): ImageGenerationResponse {
        val requestId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000
        val normalizedRequest = request.copy(
            image = normalizeImageInput(request.image)
        )

        if (normalizedRequest.model.startsWith("agnes-", ignoreCase = true)) {
            return generateAgnesImage(
                request = normalizedRequest,
                requestId = requestId
            )
        }

        val openAiConfig = openAiImageRuntimeConfig(normalizedRequest)
        if (openAiConfig != null) {
            return generateOpenAiCompatibleImage(
                request = normalizedRequest.copy(model = openAiConfig.model),
                config = openAiConfig,
                requestId = requestId
            )
        }

        val vivoRequest = VivoImageGenerationRequest(
            model = normalizedRequest.model,
            prompt = normalizedRequest.prompt,
            image = normalizedRequest.image.toImageJsonElement(),
            parameters = normalizedRequest.parameters
        )

        Log.d(TAG, "=== generateImage 开始 ===")
        Log.d(TAG, "requestId: $requestId")
        Log.d(TAG, "timestamp: $timestamp")
        Log.d(TAG, "model: ${normalizedRequest.model}")
        Log.d(TAG, "image数量: ${normalizedRequest.image?.images?.size ?: 0}")

        if (apiKey.isBlank()) {
            return ImageGenerationResponse(
                code = -1,
                message = "API key is not configured",
                traceId = requestId
            )
        }

        val requestBodyStr = try {
            json.encodeToString(vivoRequest)
        } catch (e: Exception) {
            Log.e(TAG, "序列化请求失败", e)
            return ImageGenerationResponse(
                code = -1,
                message = "序列化失败: ${e.message}",
                traceId = requestId
            )
        }
        
        Log.d(TAG, "请求体JSON长度: ${requestBodyStr.length}")

        val requestBody = requestBodyStr.toRequestBody(mediaType)

        val httpRequest = Request.Builder()
            .url("${ImageGenerationApi.BASE_URL}/api/v1/image_generation?module=aigc&request_id=$requestId&system_time=$timestamp")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        Log.d(TAG, "发送HTTP请求...")

        // 在IO线程中执行网络请求
        return withContext(Dispatchers.IO) {
            try {
                imageOkHttpClient.newCall(httpRequest).execute().use { response ->
                    Log.d(TAG, "收到HTTP响应: code=${response.code}")
                    
                    if (!response.isSuccessful) {
                        response.body?.close()
                        Log.e(TAG, "HTTP请求失败: code=${response.code}")
                        return@withContext ImageGenerationResponse(
                            code = response.code,
                            message = "HTTP Error: ${response.code}",
                            traceId = requestId
                        )
                    }

                    val responseBody = response.body?.string()
                    Log.d(TAG, "响应体长度: ${responseBody?.length ?: 0}")
                    
                    if (responseBody == null) {
                        Log.e(TAG, "响应体为空")
                        return@withContext ImageGenerationResponse(
                            code = -1,
                            message = "Empty response",
                            traceId = requestId
                        )
                    }
                    
                    try {
                        val result = normalizeVivoResponse(
                            responseBody = responseBody,
                            traceId = requestId,
                            fallbackSize = normalizedRequest.parameters?.size ?: "2K"
                        )
                        Log.d(TAG, "解析响应成功: code=${result.code}, message=${result.message}")
                        Log.d(TAG, "images数量: ${result.data?.images?.size ?: 0}")
                        result
                    } catch (e: Exception) {
                        Log.e(TAG, "解析响应失败", e)
                        ImageGenerationResponse(
                            code = -1,
                            message = "解析响应失败: ${e.message}",
                            traceId = requestId
                        )
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "网络请求失败", e)
                ImageGenerationResponse(
                    code = -1,
                    message = "Network error: ${e.message}",
                    traceId = requestId
                )
            } finally {
                Log.d(TAG, "=== generateImage 结束 ===")
            }
        }
    }


    private fun openAiImageRuntimeConfig(request: ImageGenerationRequest): OpenAiImageRuntimeConfig? {
        val baseUrl = request.openAiBaseUrl?.trim().orEmpty()
        val apiKey = request.openAiApiKey?.trim().orEmpty()
        if (baseUrl.isBlank() && apiKey.isBlank()) return null
        return OpenAiImageRuntimeConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = request.model.trim()
        )
    }

    private suspend fun generateOpenAiCompatibleImage(
        request: ImageGenerationRequest,
        config: OpenAiImageRuntimeConfig,
        requestId: String
    ): ImageGenerationResponse {
        if (config.baseUrl.isBlank()) {
            return ImageGenerationResponse(code = -1, message = "自定义图片 API 地址不能为空", traceId = requestId)
        }
        if (config.apiKey.isBlank()) {
            return ImageGenerationResponse(code = -1, message = "自定义图片 API Key 不能为空", traceId = requestId)
        }
        if (config.model.isBlank()) {
            return ImageGenerationResponse(code = -1, message = "自定义图片模型名称不能为空", traceId = requestId)
        }

        if (usesOpenAiChatCompletionsForImage(config)) {
            return generateOpenAiCompatibleChatImage(request, config, requestId)
        }

        val size = normalizeOpenAiImageSize(request.parameters?.size)
        val inputImages = request.image?.images.orEmpty()
        val imageArray = JsonArray(inputImages.map { JsonPrimitive(it) })
        val requestBodyStr = buildJsonObject {
            put("model", config.model)
            put("prompt", request.prompt)
            put("size", size)
            put("n", 1)
            request.image.toImageJsonElement()?.let { imageElement ->
                put("image", imageElement)
            }
            if (inputImages.isNotEmpty()) {
                put("images", imageArray)
                put("extra_body", buildJsonObject {
                    put("image", imageArray)
                    put("response_format", "url")
                })
            }
        }.toString()
        val httpRequest = Request.Builder()
            .url(openAiImagesUrl(config.baseUrl))
            .post(requestBodyStr.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        Log.d(TAG, "发送 OpenAI-compatible 图片生成请求 model=${config.model} image数量=${request.image?.images?.size ?: 0}")
        return withContext(Dispatchers.IO) {
            try {
                imageOkHttpClient.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext ImageGenerationResponse(
                            code = response.code,
                            message = responseBody.ifBlank { "HTTP Error: ${response.code}" },
                            traceId = requestId
                        )
                    }
                    normalizeOpenAiCompatibleImageResponse(
                        responseBody = responseBody,
                        traceId = requestId,
                        fallbackSize = size
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "OpenAI-compatible 图片网络请求失败", e)
                ImageGenerationResponse(
                    code = -1,
                    message = "Network error: ${e.message}",
                    traceId = requestId
                )
            }
        }
    }
    private suspend fun generateOpenAiCompatibleChatImage(
        request: ImageGenerationRequest,
        config: OpenAiImageRuntimeConfig,
        requestId: String
    ): ImageGenerationResponse {
        val size = normalizeOpenAiImageSize(request.parameters?.size)
        val inputImages = request.image?.images.orEmpty()
        val content = buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", request.prompt)
            })
            inputImages.forEach { image ->
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject {
                        put("url", image)
                    })
                })
            }
        }
        val requestBodyStr = buildJsonObject {
            put("model", config.model)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", content)
                })
            })
        }.toString()
        val httpRequest = Request.Builder()
            .url(openAiChatCompletionsUrl(config.baseUrl))
            .post(requestBodyStr.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        Log.d(TAG, "发送 OpenAI-compatible chat 图片生成请求 model=${config.model} image数量=${inputImages.size}")
        return withContext(Dispatchers.IO) {
            try {
                imageOkHttpClient.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext ImageGenerationResponse(
                            code = response.code,
                            message = responseBody.ifBlank { "HTTP Error: ${response.code}" },
                            traceId = requestId
                        )
                    }
                    normalizeOpenAiCompatibleChatImageResponse(
                        responseBody = responseBody,
                        traceId = requestId,
                        fallbackSize = size
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "OpenAI-compatible chat 图片网络请求失败", e)
                ImageGenerationResponse(
                    code = -1,
                    message = "Network error: ${e.message}",
                    traceId = requestId
                )
            }
        }
    }
    private suspend fun generateAgnesImage(
        request: ImageGenerationRequest,
        requestId: String
    ): ImageGenerationResponse {
        if (!request.model.equals(AGNES_FREE_IMAGE_MODEL, ignoreCase = true)) {
            return ImageGenerationResponse(
                code = -1,
                message = "Agnes 仅配置永久免费图片模型 $AGNES_FREE_IMAGE_MODEL",
                traceId = requestId
            )
        }

        if (agnesApiKey.isBlank()) {
            return ImageGenerationResponse(
                code = -1,
                message = "Agnes API key is not configured",
                traceId = requestId
            )
        }

        val inputImages = request.image?.images.orEmpty()
        val agnesRequest = AgnesImageGenerationRequest(
            model = AGNES_FREE_IMAGE_MODEL,
            prompt = request.prompt,
            size = normalizeAgnesSize(request.parameters?.size),
            extraBody = AgnesImageExtraBody(
                image = inputImages.takeIf { it.isNotEmpty() },
                responseFormat = "url"
            )
        )

        val requestBodyStr = try {
            json.encodeToString(AgnesImageGenerationRequest.serializer(), agnesRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Agnes 图片请求序列化失败", e)
            return ImageGenerationResponse(
                code = -1,
                message = "序列化失败: ${e.message}",
                traceId = requestId
            )
        }

        val httpRequest = Request.Builder()
            .url(agnesImagesUrl(agnesBaseUrl))
            .post(requestBodyStr.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${agnesApiKey.trim()}")
            .build()

        Log.d(TAG, "发送 Agnes 图片生成请求 model=$AGNES_FREE_IMAGE_MODEL image数量=${inputImages.size}")

        return withContext(Dispatchers.IO) {
            try {
                imageOkHttpClient.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Agnes 图片请求失败: code=${response.code}")
                        return@withContext ImageGenerationResponse(
                            code = response.code,
                            message = "Agnes HTTP Error: ${response.code}",
                            traceId = requestId
                        )
                    }

                    if (responseBody == null) {
                        return@withContext ImageGenerationResponse(
                            code = -1,
                            message = "Empty Agnes response",
                            traceId = requestId
                        )
                    }

                    normalizeAgnesResponse(
                        responseBody = responseBody,
                        traceId = requestId,
                        fallbackSize = agnesRequest.size
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Agnes 图片网络请求失败", e)
                ImageGenerationResponse(
                    code = -1,
                    message = "Agnes network error: ${e.message}",
                    traceId = requestId
                )
            }
        }
    }


    private fun normalizeOpenAiCompatibleImageResponse(
        responseBody: String,
        traceId: String,
        fallbackSize: String
    ): ImageGenerationResponse {
        val trimmed = responseBody.trimStart()
        if (trimmed.startsWith("<!doctype", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            return ImageGenerationResponse(
                code = -1,
                message = "当前地址返回的是 HTML 网页，不是 OpenAI-compatible 图片生成响应。Base URL 可能少了 /v1。",
                traceId = traceId
            )
        }

        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }
            .getOrElse { error ->
                return ImageGenerationResponse(
                    code = -1,
                    message = "无法解析图片生成响应：${error.message}",
                    traceId = traceId
                )
            }
        openAiErrorMessage(root)?.let { message ->
            return ImageGenerationResponse(code = -1, message = message, traceId = traceId)
        }

        val imageObjects = mutableListOf<kotlinx.serialization.json.JsonObject>()
        runCatching {
            root["data"]?.jsonArray?.mapNotNullTo(imageObjects) { item ->
                runCatching { item.jsonObject }.getOrNull()
            }
        }
        runCatching {
            root["data"]?.jsonObject?.get("images")?.jsonArray?.mapNotNullTo(imageObjects) { item ->
                runCatching { item.jsonObject }.getOrNull()
            }
        }
        runCatching {
            root["images"]?.jsonArray?.mapNotNullTo(imageObjects) { item ->
                runCatching { item.jsonObject }.getOrNull()
            }
        }

        val images = imageObjects.mapNotNull { image ->
            val url = image["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val b64 = image["b64_json"]?.jsonPrimitive?.contentOrNull.orEmpty()
            when {
                url.isNotBlank() -> url
                b64.isNotBlank() -> "data:image/png;base64,$b64"
                else -> null
            }?.let { value -> GeneratedImage(url = value, size = fallbackSize) }
        }

        if (images.isEmpty()) {
            return ImageGenerationResponse(
                code = -1,
                message = "OpenAI-compatible 图片响应中未包含图片 URL 或 b64_json",
                traceId = traceId
            )
        }

        return ImageGenerationResponse(
            code = 0,
            message = "success",
            traceId = traceId,
            data = ImageGenerationData(images = images)
        )
    }

    private fun normalizeOpenAiCompatibleChatImageResponse(
        responseBody: String,
        traceId: String,
        fallbackSize: String
    ): ImageGenerationResponse {
        val trimmed = responseBody.trimStart()
        if (trimmed.startsWith("<!doctype", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            return ImageGenerationResponse(
                code = -1,
                message = "当前地址返回的是 HTML 网页，不是 OpenAI-compatible 聊天生成响应。Base URL 可能少了 /v1。",
                traceId = traceId
            )
        }

        if (trimmed.lineSequence().any { it.trimStart().startsWith("data:") }) {
            findOpenAiGeneratedImageInSse(responseBody)?.let { imageValue ->
                return ImageGenerationResponse(
                    code = 0,
                    message = "success",
                    traceId = traceId,
                    data = ImageGenerationData(
                        images = listOf(GeneratedImage(url = imageValue, size = fallbackSize))
                    )
                )
            }
        }

        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }
            .getOrElse { error ->
                return ImageGenerationResponse(
                    code = -1,
                    message = "无法解析图片聊天生成响应：${error.message}",
                    traceId = traceId
                )
            }
        openAiErrorMessage(root)?.let { message ->
            return ImageGenerationResponse(code = -1, message = message, traceId = traceId)
        }

        val imageValue = findOpenAiGeneratedImage(root)
        if (imageValue.isNullOrBlank()) {
            return ImageGenerationResponse(
                code = -1,
                message = "OpenAI-compatible 聊天响应中未包含图片 URL 或 b64_json",
                traceId = traceId
            )
        }

        return ImageGenerationResponse(
            code = 0,
            message = "success",
            traceId = traceId,
            data = ImageGenerationData(
                images = listOf(GeneratedImage(url = imageValue, size = fallbackSize))
            )
        )
    }

    private fun findOpenAiGeneratedImageInSse(responseBody: String): String? {
        val contentBuilder = StringBuilder()
        responseBody.lineSequence().forEach { line ->
            val payload = line.trim().removePrefix("data:").trim()
            if (payload.isBlank() || payload == "[DONE]") return@forEach
            val element = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return@forEach
            findOpenAiGeneratedImage(element)?.let { return it }
            collectOpenAiContentText(element).takeIf { it.isNotBlank() }?.let { contentBuilder.append(it) }
        }
        return extractImageFromText(contentBuilder.toString())
    }

    private fun collectOpenAiContentText(element: JsonElement): String {
        return when (element) {
            is JsonObject -> element.entries.joinToString(separator = "") { (key, value) ->
                if (key == "content" && value is JsonPrimitive) {
                    value.contentOrNull.orEmpty()
                } else {
                    collectOpenAiContentText(value)
                }
            }
            is JsonArray -> element.joinToString(separator = "") { item -> collectOpenAiContentText(item) }
            is JsonPrimitive -> ""
        }
    }
    private fun findOpenAiGeneratedImage(element: JsonElement): String? {
        return when (element) {
            is JsonObject -> {
                element["b64_json"]?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return ensureImageDataUrl(it) }

                val imageUrl = element["image_url"]
                when (imageUrl) {
                    is JsonObject -> imageUrl["url"]?.jsonPrimitive?.contentOrNull
                        ?.trim()
                        ?.takeIf { looksLikeGeneratedImageValue(it) }
                        ?.let { return it }
                    is JsonPrimitive -> imageUrl.contentOrNull
                        ?.trim()
                        ?.takeIf { looksLikeGeneratedImageValue(it) }
                        ?.let { return it }
                    else -> Unit
                }

                element["url"]?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.takeIf { looksLikeGeneratedImageValue(it) }
                    ?.let { return it }

                element["content"]?.let { content ->
                    findOpenAiGeneratedImage(content)?.let { return it }
                }

                element.values.firstNotNullOfOrNull { value -> findOpenAiGeneratedImage(value) }
            }
            is JsonArray -> element.firstNotNullOfOrNull { item -> findOpenAiGeneratedImage(item) }
            is JsonPrimitive -> extractImageFromText(element.contentOrNull.orEmpty())
        }
    }

    private fun extractImageFromText(text: String): String? {
        Regex("""data:image/[^;\s"')]+;base64,[A-Za-z0-9+/=_-]+""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.value
            ?.let { return it }

        Regex("""!\[[^\]]*]\(([^)\s]+)\)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { looksLikeGeneratedImageValue(it) }
            ?.let { return it }

        return Regex("""https?://[^\s"'<>)]+""").find(text)?.value
            ?.trim()
            ?.trimEnd('.', ',', ';')
            ?.takeIf { looksLikeGeneratedImageValue(it) }
    }

    private fun ensureImageDataUrl(value: String): String {
        return if (value.startsWith("data:image/", ignoreCase = true)) value else "data:image/png;base64,$value"
    }

    private fun looksLikeGeneratedImageValue(value: String): Boolean {
        return value.startsWith("data:image/", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
    }
    private fun openAiErrorMessage(root: kotlinx.serialization.json.JsonObject): String? {
        return runCatching {
            val error = root["error"]?.jsonObject ?: return null
            error["message"]?.jsonPrimitive?.contentOrNull
                ?: error["code"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun usesOpenAiChatCompletionsForImage(config: OpenAiImageRuntimeConfig): Boolean {
        val baseUrl = config.baseUrl.trim().trimEnd('/').lowercase()
        if (baseUrl.endsWith("/images/generations")) return false
        if (baseUrl.endsWith("/chat/completions")) return true

        val model = config.model.trim().lowercase()
        return model.startsWith("gpt-image") || model.contains("gpt-4o-image")
    }

    private fun openAiChatCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return when {
            normalized.endsWith("/chat/completions") -> normalized
            normalized.endsWith("/images/generations") -> normalized.removeSuffix("/images/generations") + "/chat/completions"
            normalized.endsWith("/responses") -> normalized.removeSuffix("/responses") + "/chat/completions"
            else -> "$normalized/chat/completions"
        }
    }
    private fun openAiImagesUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return when {
            normalized.endsWith("/images/generations") -> normalized
            normalized.endsWith("/chat/completions") -> normalized.removeSuffix("/chat/completions") + "/images/generations"
            normalized.endsWith("/responses") -> normalized.removeSuffix("/responses") + "/images/generations"
            else -> "$normalized/images/generations"
        }
    }

    private fun normalizeOpenAiImageSize(size: String?): String {
        val value = size?.trim().orEmpty()
        return if (Regex("""^\d+x\d+$""").matches(value)) value else "1024x1024"
    }
    private fun normalizeVivoResponse(
        responseBody: String,
        traceId: String,
        fallbackSize: String
    ): ImageGenerationResponse {
        val vivoResponse = json.decodeFromString<ImageGenerationResponse>(responseBody)
        val normalizedImages = vivoResponse.data?.images.orEmpty().map { image ->
            image.copy(size = image.size.ifBlank { fallbackSize })
        }
        return vivoResponse.copy(
            traceId = vivoResponse.traceId.ifBlank { traceId },
            data = vivoResponse.data?.copy(images = normalizedImages)
        )
    }

    private fun normalizeAgnesResponse(
        responseBody: String,
        traceId: String,
        fallbackSize: String
    ): ImageGenerationResponse {
        val agnesResponse = json.decodeFromString<ArkImageGenerationResponse>(responseBody)
        agnesResponse.error?.message?.let { errorMessage ->
            return ImageGenerationResponse(
                code = -1,
                message = errorMessage,
                traceId = traceId
            )
        }

        val images = agnesResponse.data.mapNotNull { image ->
            val url = image.url?.takeIf { it.isNotBlank() }
                ?: image.base64Json?.takeIf { it.isNotBlank() }?.let { "data:image/png;base64,$it" }
            url?.let {
                GeneratedImage(
                    url = it,
                    size = image.size?.takeIf { value -> value.isNotBlank() } ?: fallbackSize
                )
            }
        }

        if (images.isEmpty()) {
            return ImageGenerationResponse(
                code = -1,
                message = "Agnes 响应中未包含图片 URL",
                traceId = traceId
            )
        }

        return ImageGenerationResponse(
            code = 0,
            message = "success",
            traceId = traceId,
            data = ImageGenerationData(
                images = images,
                usage = agnesResponse.usage?.toImageUsage()
            )
        )
    }

    private fun agnesImagesUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().ifBlank { "https://apihub.agnes-ai.com/v1" }.trimEnd('/')
        return if (normalized.endsWith("/v1")) {
            "$normalized/images/generations"
        } else {
            "$normalized/v1/images/generations"
        }
    }

    private fun normalizeAgnesSize(size: String?): String {
        val value = size?.trim().orEmpty()
        return if (Regex("""^\d+x\d+$""").matches(value)) value else "1024x1024"
    }

    private fun ArkImageUsage.toImageUsage(): ImageUsage {
        return ImageUsage(
            imageCount = generatedImages ?: imageCount ?: 0,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens
        )
    }

    /**
     * 生成漫画风格的人物形象
     * @param originalImageUrl 原始图片URL或Base64
     * @param prompt 额外的提示词
     * @return 生成的图片URL
     */
    suspend fun generateComicStyleImage(
        originalImageUrl: String? = null,
        prompt: String = "将参考图片转换成漫画风格，同时保持主体构图一致"
    ): Result<String> {
        Log.d(TAG, "=== generateComicStyleImage 开始 ===")
        Log.d(TAG, "originalImageUrl长度: ${originalImageUrl?.length ?: 0}")

        // 按服务端接口格式组装请求。
        val request = ImageGenerationRequest(
            model = ImageGenerationApi.DEFAULT_MODEL,
            prompt = prompt,
            image = ImageGenerationInput.single(originalImageUrl),
            parameters = ImageGenerationParameters(
                size = "2K",
                promptExtend = false
            )
        )

        Log.d(TAG, "使用的model: ${request.model}")
        Log.d(TAG, "图片尺寸: 2K")
        Log.d(TAG, "promptExtend: false")

        return try {
            val response = generateImage(request)
            Log.d(TAG, "generateImage返回: code=${response.code}, message=${response.message}")

            if (response.code == 0 && response.data?.images?.isNotEmpty() == true) {
                val url = response.data.images.first().url
                Result.success(url)
            } else {
                val errorMsg = "API返回错误: code=${response.code}, message=${response.message}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成过程发生异常", e)
            Result.failure(e)
        } finally {
            Log.d(TAG, "=== generateComicStyleImage 结束 ===")
        }
    }

    suspend fun generateStyledMomentImage(
        originalImageBase64: String?,
        analysisSummary: String,
        additionalInfo: String?,
        copyText: String?,
        styleName: String,
        stylePrompt: String,
        model: String = ImageGenerationApi.DEFAULT_MODEL,
        openAiBaseUrl: String? = null,
        openAiApiKey: String? = null
    ): Result<String> {
        Log.d(TAG, "=== generateStyledMomentImage 开始 ===")
        Log.d(TAG, "styleName: $styleName")
        Log.d(TAG, "originalImageBase64长度: ${originalImageBase64?.length ?: 0}")

        val prompt = buildMomentShareImagePrompt(
            analysisSummary = analysisSummary,
            additionalInfo = additionalInfo,
            copyText = copyText,
            styleName = styleName,
            stylePrompt = stylePrompt,
            compact = false
        )

        val request = ImageGenerationRequest(
            model = model,
            prompt = prompt,
            image = ImageGenerationInput.single(originalImageBase64),
            parameters = ImageGenerationParameters(
                size = "2K",
                watermark = false,
                promptExtend = false
            ),
            openAiBaseUrl = openAiBaseUrl,
            openAiApiKey = openAiApiKey
        )

        return try {
            val response = generateImage(request)
            if (response.code == 0 && response.data?.images?.isNotEmpty() == true) {
                Result.success(response.data.images.first().url)
            } else {
                Result.failure(Exception(response.toUserFacingImageError()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "朋友圈分享图生成失败", e)
            Result.failure(e)
        } finally {
            Log.d(TAG, "=== generateStyledMomentImage 结束 ===")
        }
    }

    private fun buildMomentShareImagePrompt(
        analysisSummary: String,
        additionalInfo: String?,
        copyText: String?,
        styleName: String,
        stylePrompt: String,
        compact: Boolean
    ): String {
        val mode = resolveMomentShareVisualMode(styleName, stylePrompt)
        val summary = analysisSummary.toPolicySafeMomentPromptText(maxLength = if (compact) 180 else 360)
        val info = additionalInfo?.toPolicySafeMomentPromptText(maxLength = if (compact) 80 else 160).orEmpty()
        val copy = copyText?.toPolicySafeMomentPromptText(maxLength = if (compact) 80 else 180).orEmpty()
        val style = stylePrompt.toPolicySafeMomentStylePrompt(maxLength = if (compact) 120 else 260)

        return buildString {
            appendLine("基于输入原图生成一张适合朋友圈发布的竖版生活分享图，必须以原图为唯一视觉基础，保持真实手机摄影和生活照片质感。")
            appendLine("画面依据：$summary")
            if (info.isNotBlank()) {
                appendLine("补充说明：$info")
            }
            if (copy.isNotBlank()) {
                appendLine("已选朋友圈文案参考：$copy")
            }
            appendLine("视觉风格：$styleName。$style")
            appendLine("真实感底线：主体外观、食物形态、餐具锅具、空间关系和光线逻辑必须跟原图一致；不能脱离原图重新构图，不能变成漫画、插画、3D 渲染、卡通、AI 绘画或广告海报。")
            appendLine(mode.toPromptDirective(compact))
            appendLine("文字规则：优先少字或无字；如需文字，只能从已选文案、补充说明或画面细节中提炼一两处短中文，不写拼音、乱码、英文拟声词或大段说明；除非补充说明或已选文案明确提供日期，否则不得生成任何具体年月日、时间、编号式日期或无来源数字。")
            appendLine("标注文案要具体、松弛、有变化，围绕真实细节写，例如热气、菌菇、汤底、刚出锅、这一锅；避免模板化美食俗语，不要反复使用鲜掉眉、绝绝子、好吃哭了这类套话。")
            appendLine("整体要求：自然、克制、干净，像用户自己愿意发到朋友圈的真实生活记录。")
        }.trim()
    }

    private fun resolveMomentShareVisualMode(styleName: String, stylePrompt: String): MomentShareVisualMode {
        val text = "$styleName\n$stylePrompt".lowercase()
        return when {
            text.contains("手账") || text.contains("手绘") || text.contains("标注") -> MomentShareVisualMode.HAND_DRAWN
            text.contains("日记") || text.contains("diary") || text.contains("清爽") -> MomentShareVisualMode.FRESH_DIARY
            text.contains("贴纸") || text.contains("sticker") || text.contains("可爱") -> MomentShareVisualMode.CUTE_STICKER
            text.contains("重点") || text.contains("focus") || text.contains("突出") -> MomentShareVisualMode.HIGHLIGHT_FOCUS
            text.contains("卡片") || text.contains("card") || text.contains("简约") -> MomentShareVisualMode.MINIMAL_CARD
            else -> MomentShareVisualMode.GENERAL
        }
    }

    private fun MomentShareVisualMode.toPromptDirective(compact: Boolean): String {
        return when (this) {
            MomentShareVisualMode.HAND_DRAWN -> {
                if (compact) {
                    "风格执行：以真实照片为底，只加极少量细白手绘线条，最多一两处短中文标注；标注指向不同真实细节，不能重绘主体。"
                } else {
                    "风格执行：以真实照片为底，只做轻微提亮和色调统一；允许 1-3 条细白手绘圈线或箭头，最多一两处短中文标注，每处不超过 4 个字；标注必须指向不同真实细节，主体必须保持真实摄影质感，不能重绘成插画或漫画。"
                }
            }
            MomentShareVisualMode.FRESH_DIARY -> {
                "风格执行：清爽日记版式，只做留白、细线、柔和色调和少量生活短句；不加贴纸、不加手写箭头、不重绘主体，食物和器皿保持真实照片质感；禁止自行生成年份、月日、具体时间或类似 2024.12.26 的日期数字。"
            }
            MomentShareVisualMode.CUTE_STICKER -> {
                "风格执行：真实照片上添加少量小贴纸和柔和装饰，贴纸只能在边缘或留白处；主体、食物和场景必须保持真实，不做卡通化主体。"
            }
            MomentShareVisualMode.HIGHLIGHT_FOCUS -> {
                "风格执行：只用真实摄影调色、轻微景深、局部亮度和对比度突出主体；不加贴纸、不加手写字、不改变主体外观。"
            }
            MomentShareVisualMode.MINIMAL_CARD -> {
                "风格执行：简约分享卡版式，可增加留白、边距、圆角和一行短标题；照片区域保持真实摄影，不加贴纸、不做插画化。"
            }
            MomentShareVisualMode.GENERAL -> {
                "风格执行：真实照片增强和轻量排版为主，只做克制的色彩、边距和构图整理；不做漫画化或夸张重绘。"
            }
        }
    }

    private fun String.toPolicySafeMomentStylePrompt(maxLength: Int): String {
        return lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                val lower = line.lowercase()
                line.contains("禁忌") ||
                    line.contains("禁止") ||
                    line.contains("不要") ||
                    line.contains("不能") ||
                    line.contains("绝不") ||
                    line.contains("二维码") ||
                    lower.contains("logo") ||
                    line.contains("水印") ||
                    line.contains("人脸") ||
                    line.contains("身份") ||
                    line.contains("品牌") ||
                    line.contains("价格")
            }
            .joinToString("；")
            .toPolicySafeMomentPromptText(maxLength)
    }

    private fun String.toPolicySafeMomentPromptText(maxLength: Int): String {
        return replace("原图", "参考画面")
            .replace("真实照片", "生活影像")
            .replace("照片", "画面")
            .replace("人物", "主体")
            .replace("人脸", "主体细节")
            .replace("二维码", "关键信息")
            .replace("Logo", "标识")
            .replace("logo", "标识")
            .replace("水印", "角标")
            .replace("品牌", "名称")
            .replace("身份", "背景")
            .replace("价格", "信息")
            .replace("复制", "参考")
            .replace("编辑", "生成")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)
    }

    private fun ImageGenerationResponse.isPolicyBlocked(): Boolean {
        return code == 1004 || message.contains("violates policy", ignoreCase = true)
    }

    private fun ImageGenerationResponse.toUserFacingImageError(): String {
        return if (isPolicyBlocked()) {
            "图片生成被平台策略拦截：生成要求可能触发审核。请调整补充说明，或选择更克制的风格后重试。"
        } else {
            "API返回错误: code=$code, message=$message"
        }
    }

    suspend fun generateDigitalAvatarImage(
        originalImageBase64: String?,
        prompt: String,
        model: String = ImageGenerationApi.DEFAULT_MODEL
    ): Result<String> {
        Log.d(TAG, "=== generateDigitalAvatarImage 开始 ===")
        Log.d(TAG, "originalImageBase64长度: ${originalImageBase64?.length ?: 0}")

        val avatarPrompt = buildString {
            appendLine("你是一个负责创建用户专属数字形象的专业视觉设计师。请生成一张适合作为个人中心数字形象展示的成图。")
            appendLine("用户要求：${prompt.trim()}")
            appendLine()
            appendLine("创作要求：")
            appendLine("1. 生成清晰、精致、友好的数字形象，适合在 App 个人中心和后续配图场景中使用。")
            appendLine("2. 如果提供了参考照片，保留人物主要面部特征、发型气质和整体辨识度，但不要生成写实证件照。")
            appendLine("3. 如果没有参考照片，按用户描述自由设计，不要编造真实身份、品牌、文字、水印或平台 Logo。")
            appendLine("4. 画面主体居中，背景干净，有轻微空间层次，适合正方形或头像场景裁切。")
            appendLine("5. 风格自然、现代、温和，不要过度夸张，不要出现多个人物、乱码文字、二维码和无关元素。")
        }

        val request = ImageGenerationRequest(
            model = model,
            prompt = avatarPrompt,
            image = ImageGenerationInput.single(originalImageBase64),
            parameters = ImageGenerationParameters(
                size = "2K",
                watermark = false,
                promptExtend = false
            )
        )

        return try {
            val response = generateImage(request)
            if (response.code == 0 && response.data?.images?.isNotEmpty() == true) {
                Result.success(response.data.images.first().url)
            } else {
                Result.failure(Exception("API返回错误: code=${response.code}, message=${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "数字形象生成失败", e)
            Result.failure(e)
        } finally {
            Log.d(TAG, "=== generateDigitalAvatarImage 结束 ===")
        }
    }

    /**
     * 生成用户专属漫画形象
     * @param userPhotoUrl 用户照片URL或Base64
     * @return 生成的漫画形象URL
     */
    suspend fun generateUserComicAvatar(userPhotoUrl: String): Result<String> {
        Log.d(TAG, "=== generateUserComicAvatar 开始 ===")
        Log.d(TAG, "userPhotoUrl长度: ${userPhotoUrl.length}")
        
        val prompt = "将照片中的人物转换成精美的漫画头像，保留人物的主要面部特征，日式动漫风格，" +
                "色彩明亮，线条清晰，适合作为社交媒体头像使用，高清质量"
        
        Log.d(TAG, "调用generateComicStyleImage...")
        val result = generateComicStyleImage(userPhotoUrl, prompt)
        
        result.onSuccess {
            Log.d(TAG, "generateUserComicAvatar成功")
        }.onFailure {
            Log.e(TAG, "generateUserComicAvatar失败: ${it.message}")
        }
        
        Log.d(TAG, "=== generateUserComicAvatar 结束 ===")
        return result
    }

    /**
     * 替换图片中的人物为漫画形象
     * @param originalImageUrl 原图URL
     * @param comicAvatarUrl 用户漫画形象URL
     * @return 替换后的图片URL
     */
    suspend fun replacePersonWithComicStyle(
        originalImageUrl: String,
        comicAvatarUrl: String
    ): Result<String> {
        val prompt = "将图片中的人物替换成参考的漫画形象，保持原图的背景、构图和场景不变，" +
                "只将人物替换为漫画风格，确保漫画形象与参考图一致，融合自然"
        
        return generateComicStyleImage(originalImageUrl, prompt)
    }
}
