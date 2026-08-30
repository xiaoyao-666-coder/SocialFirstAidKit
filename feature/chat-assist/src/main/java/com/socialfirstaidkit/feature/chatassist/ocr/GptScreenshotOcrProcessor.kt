package com.socialfirstaidkit.feature.chatassist.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.socialfirstaidkit.core.data.model.DetectionMethod
import com.socialfirstaidkit.core.data.model.OcrMessage
import com.socialfirstaidkit.core.data.model.OcrResult
import com.socialfirstaidkit.core.data.model.Rect
import com.socialfirstaidkit.core.data.model.SenderType
import com.socialfirstaidkit.core.data.model.TextBlock
import com.socialfirstaidkit.core.data.ocr.OcrException
import com.socialfirstaidkit.core.data.ocr.OcrProcessor
import com.socialfirstaidkit.core.data.ocr.VivoOcrProcessor
import com.socialfirstaidkit.core.llm.api.LlmClient
import com.socialfirstaidkit.core.llm.model.LlmMessage
import com.socialfirstaidkit.core.llm.model.LlmRequest
import com.socialfirstaidkit.core.llm.model.LlmResponse
import com.socialfirstaidkit.core.llm.model.LlmRole
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import com.socialfirstaidkit.core.llm.routing.AiModelRegistry
import com.socialfirstaidkit.core.llm.routing.AiRouteStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GptScreenshotOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmClient: LlmClient,
    private val localOcrProcessor: OcrProcessor,
    private val vivoOcrProcessor: VivoOcrProcessor,
    private val aiRouteStore: AiRouteStore
) {
    companion object {
        private const val TAG = "GptScreenshotOcr"
        private const val LONG_SCREENSHOT_THRESHOLD = 2500
        private const val SPLIT_HEIGHT = 1500
        private const val OVERLAP_HEIGHT = 150
        private const val MAX_IMAGE_SIZE = 3200
        private const val JPEG_QUALITY = 98
        private const val VISION_MAX_RETRIES = 2
        private const val VISION_RETRY_BASE_DELAY_MS = 700L
        val SCREENSHOT_OCR_PROMPT = """
你是一个聊天截图OCR专家。请只识别图片中真实可见的聊天内容，不要猜测、不要补全、不要改写。

任务：
1. 识别聊天对象名称；看不清就返回空字符串。
2. 提取图片中真实存在的聊天消息。
3. 根据气泡位置判断发送者：右侧通常是 USER（我），左侧通常是 CONTACT（对方）。
4. 过滤系统状态、输入框、表情按钮、状态栏、撤回提示等非聊天正文。
5. 图片消息不识别：如果某条消息是图片、表情包、视频、文件卡片、转发卡片、聊天记录卡片，或者气泡主体是图片预览，请完全跳过，不要描述图片内容，不要输出“图片”“[图片]”“表情包”等占位文本。
6. 引用内容不识别：如果气泡中包含被引用的旧消息、回复引用块、折叠的引用预览、聊天记录引用摘要，只能忽略引用部分；如果同一个气泡里还有本次真正发送的新正文，只输出新正文。
7. 如果无法判断某段文字是当前真实消息还是引用/卡片/图片里的内容，宁可跳过，不要输出到 messages。
8. 如果不确定发送者，senderType 返回 UNKNOWN。

严格输出 JSON，不要 markdown，不要解释：
{
  "contactName": "聊天对象名称",
  "messages": [
    {
      "senderType": "USER 或 CONTACT 或 UNKNOWN",
      "content": "图片中实际看到的消息正文"
    }
  ]
}
        """.trimIndent()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun recognizeText(imageUri: Uri): OcrResult = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(imageUri)
            ?: throw OcrException("Failed to load image from URI")

        try {
            recognizeBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "GPT screenshot OCR failed", e)
            recognizeWithLocalFallback(bitmap, e)
        }
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult {
        val route = aiRouteStore.getRoute(AiFunctionSlot.CHAT_SCREENSHOT_OCR)
        if (route.modelId == AiModelRegistry.VIVO_OCR_ROTATED_ID) {
            return vivoOcrProcessor.recognizeText(bitmap)
        }
        return if (bitmap.height > LONG_SCREENSHOT_THRESHOLD) {
            recognizeLongBitmap(bitmap)
        } else {
            recognizeSingleBitmap(bitmap, yOffset = 0, fullWidth = bitmap.width, fullHeight = bitmap.height)
        }
    }

    private suspend fun recognizeLongBitmap(bitmap: Bitmap): OcrResult {
        val chunks = mutableListOf<Pair<Int, Bitmap>>()
        var currentTop = 0

        while (currentTop < bitmap.height) {
            val chunkHeight = minOf(SPLIT_HEIGHT, bitmap.height - currentTop)
            chunks.add(currentTop to Bitmap.createBitmap(bitmap, 0, currentTop, bitmap.width, chunkHeight))
            currentTop += (SPLIT_HEIGHT - OVERLAP_HEIGHT)
        }

        val chunkResults = chunks.mapIndexed { index, (top, chunk) ->
            try {
                Log.d(
                    TAG,
                    "Recognizing long screenshot chunk ${index + 1}/${chunks.size} " +
                        "top=$top size=${chunk.width}x${chunk.height}"
                )
                recognizeSingleBitmap(
                    bitmap = chunk,
                    yOffset = top,
                    fullWidth = bitmap.width,
                    fullHeight = bitmap.height
                )
            } finally {
                chunk.recycle()
            }
        }

        val messages = removeDuplicateMessages(chunkResults.flatMap { it.messages })
        val contactName = chunkResults
            .mapNotNull { result -> result.text.lineSequence().firstOrNull { it.startsWith("聊天对象：") } }
            .firstOrNull()
            ?.removePrefix("聊天对象：")
            .orEmpty()
        return OcrResult(
            text = buildRichOcrText(messages, contactName),
            confidence = 95f,
            messages = messages,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    private suspend fun recognizeSingleBitmap(
        bitmap: Bitmap,
        yOffset: Int,
        fullWidth: Int,
        fullHeight: Int
    ): OcrResult {
        val compressed = compressBitmap(bitmap)
        val encodedImage = bitmapToBase64(compressed)
        Log.d(
            TAG,
            "Prepared screenshot OCR image original=${bitmap.width}x${bitmap.height} " +
                "sent=${compressed.width}x${compressed.height} jpegKb=${encodedImage.byteSize / 1024} " +
                "base64Chars=${encodedImage.base64.length}"
        )
        val response = callGptVisionWithRetry(encodedImage.base64)
        if (compressed !== bitmap) {
            compressed.recycle()
        }
        val parsed = parseGptResponse(response, fullWidth, fullHeight, yOffset)
        return correctMessagePositionsWithLocalOcr(parsed, bitmap, yOffset, fullWidth)
    }

    private suspend fun callGptVisionWithRetry(base64Image: String): String {
        var lastError: OcrException? = null
        repeat(VISION_MAX_RETRIES + 1) { attempt ->
            try {
                return callGptVision(base64Image)
            } catch (e: OcrException) {
                lastError = e
                if (attempt >= VISION_MAX_RETRIES || !isRetryableVisionError(e.message.orEmpty())) {
                    throw e
                }
                val delayMs = VISION_RETRY_BASE_DELAY_MS * (attempt + 1)
                Log.w(TAG, "Vision OCR transient failure, retry ${attempt + 1}/$VISION_MAX_RETRIES after ${delayMs}ms: ${e.message}")
                delay(delayMs)
            }
        }
        throw lastError ?: OcrException("Vision OCR failed")
    }

    private suspend fun callGptVision(base64Image: String): String {
        val request = LlmRequest(
            model = "",
            messages = listOf(
                LlmMessage(
                    role = LlmRole.SYSTEM,
                    content = SCREENSHOT_OCR_PROMPT
                ),
                LlmMessage(
                    role = LlmRole.USER,
                    content = "请识别这张聊天截图中的聊天对象、消息发送者和消息正文，只返回严格JSON。不要识别图片消息、表情包、视频/文件/转发卡片，也不要把引用回复里的被引用旧内容放进messages。",
                    imageUrl = base64Image
                )
            ),
            maxTokens = 2048,
            temperature = 0.1f
        )

        return when (val response = llmClient.chatFor(AiFunctionSlot.CHAT_SCREENSHOT_OCR, request)) {
            is LlmResponse.Complete -> response.content
            is LlmResponse.Error -> throw OcrException(response.message)
            is LlmResponse.StreamChunk -> throw OcrException("Unexpected stream response")
        }
    }

    private fun isRetryableVisionError(message: String): Boolean {
        val lower = message.lowercase()
        return listOf(
            "temporarily unavailable",
            "upstream_error",
            "timeout",
            "timed out",
            "connection",
            "network",
            "too many requests",
            "rate limit",
            "http_429",
            "http_500",
            "http_502",
            "http_503",
            "http_504"
        ).any { it in lower }
    }

    private suspend fun recognizeWithLocalFallback(bitmap: Bitmap, cause: Exception): OcrResult {
        return try {
            Log.w(TAG, "Falling back to local ML Kit OCR after vision OCR failure", cause)
            val localResult = localOcrProcessor.recognizeText(bitmap)
            val messages = localResult.blocks
                .ifEmpty {
                    localResult.text
                        .lineSequence()
                        .map { line -> line.trim() }
                        .filter { it.isNotBlank() }
                        .map { line -> TextBlock(line, null) }
                        .toList()
                }
                .mapIndexedNotNull { index, block ->
                    val content = block.text.trim()
                    if (shouldIgnoreRecognizedMessage(content)) return@mapIndexedNotNull null
                    val box = block.boundingBox ?: Rect(
                        left = 0,
                        top = index * 80,
                        right = bitmap.width,
                        bottom = (index + 1) * 80
                    )
                    val senderType = when {
                        box.right <= bitmap.width / 2 -> SenderType.CONTACT
                        box.left >= bitmap.width / 2 -> SenderType.USER
                        else -> SenderType.UNKNOWN
                    }
                    OcrMessage(
                        content = content,
                        senderType = senderType,
                        boundingBox = box,
                        confidence = localResult.confidence / 100f,
                        detectionMethod = DetectionMethod.POSITION
                    )
                }

            if (messages.isEmpty() && localResult.text.isBlank()) {
                throw OcrException("Local OCR did not recognize readable chat text")
            }

            localResult.copy(
                text = if (messages.isNotEmpty()) {
                    buildRichOcrText(messages, contactName = "")
                } else {
                    localResult.text
                },
                messages = messages,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } catch (fallbackError: Exception) {
            throw OcrException(
                "GPT screenshot OCR failed: ${cause.message}; local OCR fallback failed: ${fallbackError.message}"
            )
        }
    }

    private fun parseGptResponse(content: String, imageWidth: Int, imageHeight: Int, yOffset: Int): OcrResult {
        val parsed = json.decodeFromString<GptScreenshotOcrJson>(extractJson(content))
        val filteredMessages = parsed.messages
            .map { it.copy(content = it.content.trim()) }
            .filterNot { shouldIgnoreRecognizedMessage(it.content) }
        val messages = filteredMessages.mapIndexed { index, message ->
            val senderType = when (message.senderType.uppercase()) {
                "USER" -> SenderType.USER
                "CONTACT" -> SenderType.CONTACT
                else -> SenderType.UNKNOWN
            }
            OcrMessage(
                content = message.content,
                senderType = senderType,
                boundingBox = Rect(
                    left = if (senderType == SenderType.USER) imageWidth / 2 else 0,
                    top = yOffset + index * 100,
                    right = if (senderType == SenderType.USER) imageWidth else imageWidth / 2,
                    bottom = yOffset + (index + 1) * 100
                ),
                confidence = 0.95f,
                detectionMethod = DetectionMethod.LLM_CONFIRMED
            )
        }

        return OcrResult(
            text = buildRichOcrText(messages, parsed.contactName),
            confidence = 95f,
            messages = messages,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    private suspend fun correctMessagePositionsWithLocalOcr(
        result: OcrResult,
        bitmap: Bitmap,
        yOffset: Int,
        fullWidth: Int
    ): OcrResult {
        if (result.messages.isEmpty()) return result
        val localBlocks = runCatching { localOcrProcessor.recognizeText(bitmap).blocks }
            .getOrElse { error ->
                Log.w(TAG, "Local OCR position correction skipped: ${error.message}")
                emptyList()
            }
        if (localBlocks.isEmpty()) return result

        val corrected = result.messages.map { message ->
            val matchedBlock = localBlocks.firstOrNull { block ->
                val blockText = normalizeForMatch(block.text)
                val messageText = normalizeForMatch(message.content)
                blockText.isNotBlank() &&
                    messageText.isNotBlank() &&
                    (blockText == messageText || blockText.contains(messageText) || messageText.contains(blockText))
            } ?: return@map message

            val localBox = matchedBlock.boundingBox ?: return@map message
            val adjustedBox = Rect(
                left = localBox.left,
                top = localBox.top + yOffset,
                right = localBox.right,
                bottom = localBox.bottom + yOffset
            )
            val centerX = (localBox.left + localBox.right) / 2f
            val correctedSender = when {
                centerX >= fullWidth * 0.58f -> SenderType.USER
                centerX <= fullWidth * 0.42f -> SenderType.CONTACT
                else -> message.senderType
            }
            message.copy(
                senderType = correctedSender,
                boundingBox = adjustedBox,
                detectionMethod = DetectionMethod.POSITION
            )
        }

        return result.copy(
            text = buildRichOcrText(corrected, contactName = ""),
            messages = corrected
        )
    }

    private fun normalizeForMatch(text: String): String {
        return text.trim().replace(Regex("\\s+"), "")
    }

    private fun shouldIgnoreRecognizedMessage(content: String): Boolean {
        val normalized = content.trim().replace(Regex("\\s+"), "")
        if (normalized.isBlank()) return true
        if (normalized.matches(Regex("^(昨天|今天|上午|下午|晚上)?\\d{1,2}:\\d{2}$"))) return true
        if (normalized.matches(Regex("^(Contact|Me|USER|CONTACT|UNKNOWN)[·.。:：]?\\d{1,2}:\\d{2}$", RegexOption.IGNORE_CASE))) return true
        if (normalized.contains(Regex("(4G|5G|Wi[-]?Fi|KB/s|MB/s|电量|电池)", RegexOption.IGNORE_CASE))) return true
        if (looksLikeStatusBarNoise(normalized)) return true

        val mediaOrCardPlaceholders = setOf(
            "图片",
            "图片消息",
            "图片预览",
            "[图片]",
            "【图片】",
            "[照片]",
            "照片",
            "照片消息",
            "表情",
            "[表情]",
            "表情包",
            "[表情包]",
            "视频",
            "视频消息",
            "[视频]",
            "文件",
            "文件消息",
            "[文件]",
            "转发",
            "转发卡片",
            "[转发]",
            "聊天记录",
            "聊天记录卡片",
            "[聊天记录]",
            "合并转发",
            "[合并转发]"
        )
        return normalized in mediaOrCardPlaceholders
    }

    private fun looksLikeStatusBarNoise(text: String): Boolean {
        val chineseCount = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val letterOrSymbolCount = text.count { it.isLetterOrDigit() || !it.isWhitespace() } - chineseCount
        if (text.length <= 14 && chineseCount <= 1 && letterOrSymbolCount >= 3) return true
        if (text.length <= 18 && chineseCount <= 2 && text.count { it in "%/.,。:+-_" } >= 2) return true
        return false
    }

    private fun buildRichOcrText(messages: List<OcrMessage>, contactName: String): String {
        return buildString {
            if (contactName.isNotBlank()) {
                appendLine("聊天对象：$contactName")
            }
            messages.forEach { message ->
                val senderLabel = when (message.senderType) {
                    SenderType.USER -> "USER（我）"
                    SenderType.CONTACT -> "CONTACT（对方）"
                    SenderType.UNKNOWN -> "UNKNOWN"
                }
                appendLine("$senderLabel: ${message.content}")
            }
        }.trim()
    }

    private fun removeDuplicateMessages(messages: List<OcrMessage>): List<OcrMessage> {
        val unique = mutableListOf<OcrMessage>()
        messages.forEach { message ->
            val duplicate = unique.any { existing ->
                existing.content == message.content ||
                    calculateSimilarity(existing.content, message.content) > 0.86f
            }
            if (!duplicate) unique.add(message)
        }
        return unique
    }

    private fun calculateSimilarity(text1: String, text2: String): Float {
        if (text1 == text2) return 1f
        val longer = if (text1.length >= text2.length) text1 else text2
        val shorter = if (text1.length >= text2.length) text2 else text1
        if (longer.isEmpty()) return 0f
        val distance = levenshteinDistance(longer, shorter)
        return (longer.length - distance).toFloat() / longer.length
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
                }
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }

    private data class EncodedImage(
        val base64: String,
        val byteSize: Int
    )

    private fun bitmapToBase64(bitmap: Bitmap): EncodedImage {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val bytes = outputStream.toByteArray()
        return EncodedImage(
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            byteSize = bytes.size
        )
    }

    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = MAX_IMAGE_SIZE
            newHeight = (MAX_IMAGE_SIZE / ratio).toInt()
        } else {
            newHeight = MAX_IMAGE_SIZE
            newWidth = (MAX_IMAGE_SIZE * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun extractJson(content: String): String {
        val jsonBlockRegex = """```(?:json)?\s*([\s\S]*?)```""".toRegex()
        jsonBlockRegex.find(content)?.let { return it.groupValues[1].trim() }
        val startIndex = content.indexOf('{')
        val endIndex = content.lastIndexOf('}')
        return if (startIndex >= 0 && endIndex > startIndex) {
            content.substring(startIndex, endIndex + 1)
        } else {
            content
        }
    }
}

@Serializable
private data class GptScreenshotOcrJson(
    val contactName: String = "",
    val messages: List<GptScreenshotOcrMessageJson> = emptyList()
)

@Serializable
private data class GptScreenshotOcrMessageJson(
    val senderType: String = "UNKNOWN",
    val content: String = ""
)
