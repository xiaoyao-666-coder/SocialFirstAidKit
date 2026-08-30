package com.socialfirstaidkit.core.network.imagegen

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 图片生成代理接口
 */
interface ImageGenerationApi {

    /**
     * 生成图片
     * @param request 图片生成请求
     * @return 图片生成响应
     */
    suspend fun generateImage(request: ImageGenerationRequest): ImageGenerationResponse

    companion object {
        const val BASE_URL = "https://api-ai.vivo.com.cn"
        const val DEFAULT_MODEL = "Doubao-Seedream-5.0-lite"
    }
}

/**
 * 图片生成请求
 */
@Serializable
data class ImageGenerationRequest(
    @SerialName("model")
    val model: String = ImageGenerationApi.DEFAULT_MODEL,
    
    @SerialName("prompt")
    val prompt: String,
    
    @SerialName("image")
    val image: ImageGenerationInput? = null,
    
    @SerialName("parameters")
    val parameters: ImageGenerationParameters? = null,

    val openAiBaseUrl: String? = null,
    val openAiApiKey: String? = null
)

/**
 * 图生图输入。第一张图作为待编辑原图，后续图片可作为风格参考图。
 */
@Serializable(with = ImageGenerationInputSerializer::class)
data class ImageGenerationInput(
    val images: List<String> = emptyList()
) {
    companion object {
        fun single(image: String?): ImageGenerationInput? {
            val value = image?.trim().orEmpty()
            return if (value.isBlank()) null else ImageGenerationInput(listOf(value))
        }

        fun multiple(images: List<String>): ImageGenerationInput? {
            val values = images.map { it.trim() }.filter { it.isNotBlank() }
            return if (values.isEmpty()) null else ImageGenerationInput(values)
        }
    }
}

object ImageGenerationInputSerializer : KSerializer<ImageGenerationInput> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ImageGenerationInput", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ImageGenerationInput) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ImageGenerationInput can only be serialized as JSON")
        jsonEncoder.encodeJsonElement(value.toJsonElement())
    }

    override fun deserialize(decoder: Decoder): ImageGenerationInput {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ImageGenerationInput can only be deserialized from JSON")
        return imageGenerationInputFromJsonElement(jsonDecoder.decodeJsonElement())
    }

    private fun ImageGenerationInput.toJsonElement(): JsonElement {
        return when {
            images.isEmpty() -> JsonPrimitive("")
            images.size == 1 -> JsonPrimitive(images.first())
            else -> JsonArray(images.map { JsonPrimitive(it) })
        }
    }

    private fun imageGenerationInputFromJsonElement(element: JsonElement): ImageGenerationInput {
        val images = when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonObject -> element["images"]
                ?.let { imageGenerationInputFromJsonElement(it).images }
                .orEmpty()
            else -> listOfNotNull(element.jsonPrimitive.contentOrNull)
        }
        return ImageGenerationInput.multiple(images) ?: ImageGenerationInput()
    }
}

/**
 * 图片生成参数
 */
@Serializable
data class ImageGenerationParameters(
    @SerialName("size")
    val size: String? = null,

    @SerialName("watermark")
    val watermark: Boolean? = null,

    @SerialName("guidance_scale")
    val guidanceScale: Double? = null,

    @SerialName("prompt_extend")
    val promptExtend: Boolean? = null
)

/**
 * 图片生成响应
 */
@Serializable
data class ImageGenerationResponse(
    @SerialName("code")
    val code: Int,
    
    @SerialName("message")
    val message: String,
    
    @SerialName("trace_id")
    val traceId: String,
    
    @SerialName("data")
    val data: ImageGenerationData? = null
)

/**
 * 图片生成数据
 */
@Serializable
data class ImageGenerationData(
    @SerialName("images")
    val images: List<GeneratedImage> = emptyList(),
    
    @SerialName("finish_reason")
    val finishReason: String? = null,
    
    @SerialName("usage")
    val usage: ImageUsage? = null
)

/**
 * 生成的图片
 */
@Serializable
data class GeneratedImage(
    @SerialName("url")
    val url: String,
    
    @SerialName("size")
    val size: String
)

/**
 * 图片使用统计
 */
@Serializable
data class ImageUsage(
    @SerialName("image_count")
    val imageCount: Int,
    
    @SerialName("input_tokens")
    val inputTokens: Int? = null,
    
    @SerialName("output_tokens")
    val outputTokens: Int? = null,
    
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)
