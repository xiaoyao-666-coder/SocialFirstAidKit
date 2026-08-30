package com.socialfirstaidkit.core.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.socialfirstaidkit.core.data.model.DetectionMethod
import com.socialfirstaidkit.core.data.model.OcrMessage
import com.socialfirstaidkit.core.data.model.OcrResult
import com.socialfirstaidkit.core.data.model.Rect
import com.socialfirstaidkit.core.data.model.SenderType
import com.socialfirstaidkit.core.data.model.TextBlock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Vivo通用OCR处理器
 * 使用Vivo OCR API进行图片文字识别
 * 
 * 消息发送者采用三层识别机制：
 * 1. 基于位置判断（主要方案）
 * 2. 基于头像检测（辅助方案）
 * 3. LLM二次确认（最终验证）
 */
@Singleton
class VivoOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("ocr_okhttp") private val okHttpClient: OkHttpClient,
    @Named("vivo_aigc_api_key") private val vivoApiKey: String
) {

    companion object {
        private const val TAG = "VivoOcrProcessor"
        private const val DOMAIN = "api-ai.vivo.com.cn"
        private const val URI = "/ocr/general_recognition"
        private const val URL = "https://$DOMAIN$URI"

        // Competition accounts are not redistributed. Supply your own authorized business ID.
        private const val BUSINESS_ID = ""

        // 长截图分割参数。
        private const val SPLIT_HEIGHT = 1500  // 每块高度（像素）
        private const val OVERLAP_HEIGHT = 200  // 重叠高度（像素），避免文字被截断

        // 头像检测参数。
        private const val AVATAR_SIZE_MIN = 40  // 最小头像尺寸
        private const val AVATAR_SIZE_MAX = 150 // 最大头像尺寸
        private const val AVATAR_CIRCLE_THRESHOLD = 0.7f // 圆形度阈值
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 从URI识别图片中的文字
     * 支持长截屏：如果图片高度超过阈值，会分割成多块识别
     * 
     * 返回带发送者信息的消息列表。
     */
    suspend fun recognizeText(imageUri: Uri): OcrResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadBitmapFromUri(imageUri)
                ?: throw OcrException("Failed to load image from URI")

            // 高度超过宽度三倍且大于 3000 像素时按长截图处理。
            val isLongScreenshot = bitmap.height > bitmap.width * 3 && bitmap.height > 3000

            val result = if (isLongScreenshot) {
                Log.d(TAG, "检测到长截屏，使用智能识别: ${bitmap.width}x${bitmap.height}")
                recognizeLongBitmapOptimized(bitmap)
            } else {
                recognizeBitmap(bitmap)
            }

            // 返回原始文本块和位置，由上层判断发送者。
            val simpleMessages = result.blocks.map { block ->
                OcrMessage(
                    content = block.text,
                    senderType = SenderType.UNKNOWN, // 让LLM判断
                    boundingBox = block.boundingBox ?: Rect(0, 0, 0, 0),
                    confidence = 1.0f,
                    detectionMethod = DetectionMethod.POSITION
                )
            }.sortedBy { it.boundingBox.top }

            result.copy(
                messages = simpleMessages,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            throw OcrException("OCR processing failed: ${e.message}")
        }
    }

    /**
     * 从Bitmap识别文字
     * 
     * 返回带发送者信息的消息列表。
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val result = recognizeBitmap(bitmap)
        
        // 返回原始文本块和位置，由上层判断发送者。
        val simpleMessages = result.blocks.map { block ->
            OcrMessage(
                content = block.text,
                senderType = SenderType.UNKNOWN, // 让LLM判断
                boundingBox = block.boundingBox ?: Rect(0, 0, 0, 0),
                confidence = 1.0f,
                detectionMethod = DetectionMethod.POSITION
            )
        }.sortedBy { it.boundingBox.top }
        
        result.copy(
            messages = simpleMessages,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }

    /**
     * 使用三层识别机制分析消息发送者。
     * 
     * 第一层：基于位置判断（主要方案）
     * 第二层：基于头像检测（辅助方案）
     * 第三层：LLM二次确认（由上层截图分析流程完成）
     * 
     * 优先使用位置判断，降低中心点判断造成的误判。
     */
    private fun analyzeMessageSenders(blocks: List<TextBlock>, bitmap: Bitmap): List<OcrMessage> {
        if (blocks.isEmpty()) return emptyList()

        val imageWidth = bitmap.width
        val imageHeight = bitmap.height

        Log.d(TAG, "开始分析消息发送者，图片尺寸: ${imageWidth}x${imageHeight}, 文本块数: ${blocks.size}")

        // 检测头像位置。
        val avatarPositions = detectAvatars(bitmap)
        Log.d(TAG, "检测到 ${avatarPositions.size} 个头像")

        // 按 Y 坐标从上到下排序。
        val sortedBlocks = blocks
            .filter { it.boundingBox != null }
            .sortedBy { it.boundingBox!!.top }

        // 分析每个文本块的发送者。
        val messages = mutableListOf<OcrMessage>()
        var lastKnownSender: SenderType? = null // 记录上一个已知发送者，用于辅助判断
        
        sortedBlocks.forEachIndexed { index, block ->
            val rect = block.boundingBox!!
            
            // 优先使用气泡颜色判断。
            val colorBasedSender = determineSenderByBubbleColor(bitmap, rect)
            
            // 2. 备选：头像检测
            val avatarBasedSender = determineSenderByAvatar(rect, avatarPositions, imageWidth)
            
            // 3. 备选：基于位置判断
            val positionBasedSender = determineSenderByPosition(rect, imageWidth)
            
            // 综合位置、头像和气泡颜色结果。
            val (senderType, detectionMethod, confidence) = when {
                // 颜色检测成功时，最优先使用
                colorBasedSender != null -> {
                    Triple(colorBasedSender, DetectionMethod.CONTENT, 0.95f)
                }
                // 颜色检测失败，使用头像检测
                avatarBasedSender != null -> {
                    Triple(avatarBasedSender.first, DetectionMethod.AVATAR, 0.8f)
                }
                // 头像检测失败，使用位置判断
                positionBasedSender != SenderType.UNKNOWN -> {
                    Triple(positionBasedSender, DetectionMethod.POSITION, 0.6f)
                }
                // 都不明确，使用上下文推断
                lastKnownSender != null -> {
                    val inferredSender = inferSenderFromContext(rect, imageWidth, lastKnownSender)
                    Triple(inferredSender, DetectionMethod.CONTENT, 0.4f)
                }
                // 完全无法判断，默认对方
                else -> {
                    Triple(SenderType.CONTACT, DetectionMethod.POSITION, 0.3f)
                }
            }

            // 记录已知发送者（非UNKNOWN）
            if (senderType != SenderType.UNKNOWN) {
                lastKnownSender = senderType
            }

            messages.add(
                OcrMessage(
                    content = block.text,
                    senderType = senderType,
                    boundingBox = rect,
                    confidence = confidence,
                    detectionMethod = detectionMethod
                )
            )
        }

        // 合并同一发送者的连续消息。
        val mergedMessages = mergeConsecutiveMessages(messages)
        
        Log.d(TAG, "消息分析完成，共 ${mergedMessages.size} 条消息")
        mergedMessages.forEachIndexed { index, msg ->
            val senderLabel = when (msg.senderType) {
                SenderType.USER -> "我"
                SenderType.CONTACT -> "对方"
                SenderType.UNKNOWN -> "未知"
            }
            val methodLabel = when (msg.detectionMethod) {
                DetectionMethod.BUBBLE -> "Bubble"
                DetectionMethod.POSITION -> "位置"
                DetectionMethod.AVATAR -> "头像"
                DetectionMethod.CONTENT -> "内容"
                DetectionMethod.LLM_CONFIRMED -> "LLM"
            }
            Log.d(TAG, "消息${index + 1}: [$senderLabel/$methodLabel]")
        }

        return mergedMessages
    }

    /**
     * 根据位置判断发送者。
     * 原理：聊天界面中，自己的消息通常在右侧，对方的消息在左侧
     * 
     * 聊天界面特征：
     * - 绿色气泡（右侧）= 我（USER）
     * - 白色/灰色气泡（左侧）= 对方（CONTACT）
     */
    private fun analyzeMessageSendersV2(blocks: List<TextBlock>, bitmap: Bitmap): List<OcrMessage> {
        if (blocks.isEmpty()) return emptyList()

        val imageWidth = bitmap.width
        val avatarPositions = detectAvatars(bitmap)
        val clusters = buildMessageClusters(blocks, imageWidth)
        val messages = mutableListOf<OcrMessage>()
        var lastKnownSender: SenderType? = null

        Log.d(TAG, "Sender V2: ${blocks.size} blocks -> ${clusters.size} clusters")

        clusters.forEach { cluster ->
            val rect = cluster.boundingBox
            val content = cluster.text.trim()
            if (content.isBlank()) return@forEach

            if (isLikelySystemOrTimestamp(content, rect, imageWidth)) {
                messages.add(
                    OcrMessage(
                        content = content,
                        senderType = SenderType.UNKNOWN,
                        boundingBox = rect,
                        confidence = 0.2f,
                        detectionMethod = DetectionMethod.CONTENT
                    )
                )
                return@forEach
            }

            val bubbleEvidence = determineSenderByBubbleStructure(rect, imageWidth)
            val avatarEvidence = determineSenderByAvatar(rect, avatarPositions, imageWidth)
                ?.let { SenderEvidence(it.first, it.second, DetectionMethod.AVATAR) }
            val colorEvidence = determineSenderByBubbleColorV2(bitmap, rect)
            val positionBasedSender = determineSenderByPositionV2(rect, imageWidth)

            val resolved = when {
                bubbleEvidence != null &&
                    avatarEvidence != null &&
                    bubbleEvidence.senderType == avatarEvidence.senderType -> {
                    SenderEvidence(
                        senderType = bubbleEvidence.senderType,
                        confidence = maxOf(bubbleEvidence.confidence, avatarEvidence.confidence).coerceAtLeast(0.88f),
                        detectionMethod = DetectionMethod.BUBBLE
                    )
                }

                colorEvidence.senderType == SenderType.USER &&
                    (bubbleEvidence == null ||
                        bubbleEvidence.senderType == SenderType.USER ||
                        bubbleEvidence.confidence < 0.78f) -> {
                    SenderEvidence(
                        senderType = SenderType.USER,
                        confidence = maxOf(colorEvidence.confidence, bubbleEvidence?.confidence ?: 0.0f).coerceAtLeast(0.72f),
                        detectionMethod = DetectionMethod.BUBBLE
                    )
                }

                bubbleEvidence != null &&
                    colorEvidence.senderType == SenderType.CONTACT &&
                    bubbleEvidence.senderType == SenderType.CONTACT -> {
                    SenderEvidence(
                        senderType = SenderType.CONTACT,
                        confidence = maxOf(bubbleEvidence.confidence, colorEvidence.confidence).coerceAtLeast(0.74f),
                        detectionMethod = DetectionMethod.BUBBLE
                    )
                }

                bubbleEvidence != null -> bubbleEvidence
                avatarEvidence != null -> avatarEvidence

                colorEvidence.senderType == SenderType.CONTACT &&
                    positionBasedSender == SenderType.CONTACT -> {
                    SenderEvidence(SenderType.CONTACT, 0.55f, DetectionMethod.CONTENT)
                }

                positionBasedSender != SenderType.UNKNOWN -> {
                    SenderEvidence(positionBasedSender, 0.45f, DetectionMethod.POSITION)
                }

                lastKnownSender != null -> {
                    SenderEvidence(
                        senderType = inferSenderFromContextV2(rect, imageWidth, lastKnownSender),
                        confidence = 0.35f,
                        detectionMethod = DetectionMethod.CONTENT
                    )
                }

                else -> SenderEvidence(SenderType.UNKNOWN, 0.2f, DetectionMethod.CONTENT)
            }

            if (resolved.senderType != SenderType.UNKNOWN) {
                lastKnownSender = resolved.senderType
            }

            messages.add(
                OcrMessage(
                    content = content,
                    senderType = resolved.senderType,
                    boundingBox = rect,
                    confidence = resolved.confidence,
                    detectionMethod = resolved.detectionMethod
                )
            )
        }

        return mergeConsecutiveMessages(messages)
    }

    private fun buildMessageClusters(blocks: List<TextBlock>, imageWidth: Int): List<MessageCluster> {
        val sortedBlocks = blocks
            .filter { it.boundingBox != null && it.text.isNotBlank() }
            .sortedWith(compareBy<TextBlock> { it.boundingBox!!.top }.thenBy { it.boundingBox!!.left })

        val clusters = mutableListOf<MessageCluster>()
        sortedBlocks.forEach { block ->
            val rect = block.boundingBox ?: return@forEach
            val lastCluster = clusters.lastOrNull()
            if (lastCluster != null && shouldMergeIntoCluster(lastCluster, rect, imageWidth)) {
                lastCluster.blocks.add(block)
                lastCluster.boundingBox = mergeRects(lastCluster.boundingBox, rect)
            } else {
                clusters.add(MessageCluster(mutableListOf(block), rect))
            }
        }
        return clusters
    }

    private fun shouldMergeIntoCluster(cluster: MessageCluster, nextRect: Rect, imageWidth: Int): Boolean {
        val currentRect = cluster.boundingBox
        val averageHeight = cluster.averageBlockHeight().coerceAtLeast(16f)
        val verticalGap = nextRect.top - currentRect.bottom
        if (verticalGap.toFloat() > minOf(maxOf(18f, averageHeight * 0.45f), 30f)) return false

        val clusterSide = determineSenderByMargins(currentRect, imageWidth)
        val nextSide = determineSenderByMargins(nextRect, imageWidth)
        if (clusterSide != SenderType.UNKNOWN &&
            nextSide != SenderType.UNKNOWN &&
            clusterSide != nextSide
        ) {
            return false
        }

        val horizontalOverlap = minOf(currentRect.right, nextRect.right) - maxOf(currentRect.left, nextRect.left)
        val minWidth = minOf(currentRect.widthValue(), nextRect.widthValue()).coerceAtLeast(1)
        val overlapRatio = horizontalOverlap.toFloat() / minWidth.toFloat()
        val leftAligned = abs(nextRect.left - currentRect.left).toFloat() < imageWidth * 0.06f
        val rightAligned = abs(nextRect.right - currentRect.right).toFloat() < imageWidth * 0.06f
        return leftAligned || rightAligned || overlapRatio > 0.55f
    }

    private fun determineSenderByBubbleStructure(rect: Rect, imageWidth: Int): SenderEvidence? {
        if (rect.widthValue().toFloat() >= imageWidth * 0.86f) return null

        val leftGap = rect.left.toFloat()
        val rightGap = (imageWidth - rect.right).toFloat()
        val bias = leftGap - rightGap
        val absBias = abs(bias)
        val softThreshold = imageWidth * 0.09f
        val strongThreshold = imageWidth * 0.14f

        val senderType = when {
            rect.right.toFloat() >= imageWidth * 0.70f && bias >= softThreshold -> SenderType.USER
            rect.left.toFloat() <= imageWidth * 0.30f && bias <= -softThreshold -> SenderType.CONTACT
            bias >= strongThreshold -> SenderType.USER
            bias <= -strongThreshold -> SenderType.CONTACT
            else -> SenderType.UNKNOWN
        }

        if (senderType == SenderType.UNKNOWN) return null

        val confidence = (0.46f + absBias / imageWidth * 1.6f).coerceIn(0.46f, 0.90f)
        return SenderEvidence(senderType, confidence, DetectionMethod.BUBBLE)
    }

    private fun determineSenderByPositionV2(rect: Rect, imageWidth: Int): SenderType {
        val centerX = (rect.left + rect.right) / 2f
        return when {
            centerX >= imageWidth * 0.60f -> SenderType.USER
            centerX <= imageWidth * 0.40f -> SenderType.CONTACT
            else -> SenderType.UNKNOWN
        }
    }

    private fun determineSenderByMargins(rect: Rect, imageWidth: Int): SenderType {
        val marginBias = rect.left - (imageWidth - rect.right)
        val threshold = (imageWidth * 0.10f).toInt()
        return when {
            rect.widthValue().toFloat() >= imageWidth * 0.86f -> SenderType.UNKNOWN
            marginBias >= threshold -> SenderType.USER
            marginBias <= -threshold -> SenderType.CONTACT
            else -> SenderType.UNKNOWN
        }
    }

    private fun determineSenderByBubbleColorV2(bitmap: Bitmap, rect: Rect): ColorEvidence {
        val sampleRect = expandRectWithinBitmap(rect, 24, 14, bitmap.width, bitmap.height)
        val stepX = maxOf(4, sampleRect.widthValue() / 6)
        val stepY = maxOf(4, sampleRect.heightValue() / 4)

        var greenSamples = 0
        var lightSamples = 0
        var usableSamples = 0

        for (y in sampleRect.top..sampleRect.bottom step stepY) {
            for (x in sampleRect.left..sampleRect.right step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (brightness < 55) continue

                usableSamples++
                if (isLikelyChatGreen(pixel)) greenSamples++
                if (isLikelyLightBubble(pixel)) lightSamples++
            }
        }

        if (usableSamples == 0) {
            return ColorEvidence(null, 0.0f, 0.0f, 0.0f)
        }

        val greenRatio = greenSamples.toFloat() / usableSamples.toFloat()
        val lightRatio = lightSamples.toFloat() / usableSamples.toFloat()

        return when {
            greenRatio > 0.18f -> ColorEvidence(SenderType.USER, (0.55f + greenRatio).coerceIn(0.55f, 0.92f), greenRatio, lightRatio)
            lightRatio > 0.58f -> ColorEvidence(SenderType.CONTACT, (0.38f + lightRatio * 0.4f).coerceIn(0.38f, 0.72f), greenRatio, lightRatio)
            else -> ColorEvidence(null, 0.0f, greenRatio, lightRatio)
        }
    }

    private fun isLikelyChatGreen(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return g >= 150 && g >= r + 15 && g >= b + 10 && (maxOf(r, g, b) - minOf(r, g, b)) >= 25
    }

    private fun isLikelyLightBubble(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r >= 210 &&
            g >= 210 &&
            b >= 210 &&
            maxOf(abs(r - g), abs(g - b), abs(r - b)) <= 25
    }

    private fun inferSenderFromContextV2(rect: Rect, imageWidth: Int, lastKnownSender: SenderType): SenderType {
        val marginSender = determineSenderByMargins(rect, imageWidth)
        if (marginSender != SenderType.UNKNOWN) return marginSender

        val centerX = (rect.left + rect.right) / 2f
        return when {
            centerX >= imageWidth * 0.55f -> SenderType.USER
            centerX <= imageWidth * 0.45f -> SenderType.CONTACT
            else -> lastKnownSender
        }
    }

    private fun isLikelySystemOrTimestamp(text: String, rect: Rect, imageWidth: Int): Boolean {
        val compactText = text.replace("\\s+".toRegex(), "")
        val centerX = (rect.left + rect.right) / 2f
        val centered = abs(centerX - imageWidth / 2f) < imageWidth * 0.16f
        val narrow = rect.widthValue().toFloat() < imageWidth * 0.42f
        val timeLike = compactText.matches(Regex("""^\d{1,2}:\d{2}$""")) ||
            compactText.matches(Regex("""^\d{4}[/-]\d{1,2}[/-]\d{1,2}.*$""")) ||
            compactText.matches(Regex("""^\d{1,2}[/-]\d{1,2}.*$"""))

        return (centered && narrow && timeLike) || (centered && narrow && compactText.length <= 12)
    }

    private fun expandRectWithinBitmap(rect: Rect, dx: Int, dy: Int, imageWidth: Int, imageHeight: Int): Rect {
        return Rect(
            left = (rect.left - dx).coerceIn(0, imageWidth - 1),
            top = (rect.top - dy).coerceIn(0, imageHeight - 1),
            right = (rect.right + dx).coerceIn(0, imageWidth - 1),
            bottom = (rect.bottom + dy).coerceIn(0, imageHeight - 1)
        )
    }

    private fun mergeRects(first: Rect, second: Rect): Rect {
        return Rect(
            left = minOf(first.left, second.left),
            top = minOf(first.top, second.top),
            right = maxOf(first.right, second.right),
            bottom = maxOf(first.bottom, second.bottom)
        )
    }

    private fun Rect.widthValue(): Int = (right - left).coerceAtLeast(0)

    private fun Rect.heightValue(): Int = (bottom - top).coerceAtLeast(0)

    private data class MessageCluster(
        val blocks: MutableList<TextBlock>,
        var boundingBox: Rect
    ) {
        val text: String
            get() = blocks.joinToString("\n") { it.text.trim() }.trim()

        fun averageBlockHeight(): Float {
            val average = blocks
                .mapNotNull { block -> block.boundingBox?.let { (it.bottom - it.top).toFloat() } }
                .average()
            return if (average.isNaN()) 0f else average.toFloat()
        }
    }

    private data class SenderEvidence(
        val senderType: SenderType,
        val confidence: Float,
        val detectionMethod: DetectionMethod
    )

    private data class ColorEvidence(
        val senderType: SenderType?,
        val confidence: Float,
        val greenRatio: Float,
        val lightRatio: Float
    )

    private fun determineSenderByPosition(rect: Rect, imageWidth: Int): SenderType {
        val blockCenterX = (rect.left + rect.right) / 2
        
        // 简单判断：以屏幕中心为界
        return if (blockCenterX > imageWidth * 0.50) {
            SenderType.USER
        } else {
            SenderType.CONTACT
        }
    }

    /**
     * 根据气泡颜色判断发送者。
     * 
     * 微信气泡颜色特征：
     * - 我的消息：绿色气泡（RGB: 约 149, 236, 105 或 #95EC69）
     * - 对方消息：白色/浅灰色气泡（RGB: 约 255, 255, 255 或 #FFFFFF）
     */
    private fun determineSenderByBubbleColor(bitmap: Bitmap, rect: Rect): SenderType? {
        try {
            // 在文本框周围采样颜色（气泡内部）
            val samplePoints = listOf(
                // 文本框左侧中间
                Pair(rect.left - 20, (rect.top + rect.bottom) / 2),
                // 文本框右侧中间
                Pair(rect.right + 20, (rect.top + rect.bottom) / 2),
                // 文本框上方中间
                Pair((rect.left + rect.right) / 2, rect.top - 10),
                // 文本框下方中间
                Pair((rect.left + rect.right) / 2, rect.bottom + 10)
            )

            var greenPixelCount = 0
            var whitePixelCount = 0
            var totalSampled = 0

            for ((x, y) in samplePoints) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // 检测绿色（我的消息）- 微信绿色气泡特征
                    // 绿色分量高，红色和蓝色分量相对较低
                    val isGreen = g > 180 && g > r + 30 && g > b + 30

                    // 检测白色/浅灰（对方消息）
                    val isWhite = r > 200 && g > 200 && b > 200

                    if (isGreen) greenPixelCount++
                    if (isWhite) whitePixelCount++
                    totalSampled++
                }
            }

            if (totalSampled == 0) return null

            val greenRatio = greenPixelCount.toFloat() / totalSampled
            val whiteRatio = whitePixelCount.toFloat() / totalSampled

            Log.d(TAG, "颜色检测: 绿色比例=$greenRatio, 白色比例=$whiteRatio")

            return when {
                greenRatio > 0.3f -> {
                    Log.d(TAG, "-> 颜色判断为 USER（我）: 检测到绿色气泡")
                    SenderType.USER
                }
                whiteRatio > 0.3f -> {
                    Log.d(TAG, "-> 颜色判断为 CONTACT（对方）: 检测到白色气泡")
                    SenderType.CONTACT
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "颜色检测失败", e)
            return null
        }
    }

    /**
     * 根据头像位置判断发送者。
     * 返回：发送者类型和置信度
     */
    private fun determineSenderByAvatar(
        textRect: Rect, 
        avatarPositions: List<AvatarInfo>,
        imageWidth: Int
    ): Pair<SenderType, Float>? {
        if (avatarPositions.isEmpty()) return null

        val textCenterX = (textRect.left + textRect.right) / 2
        val textCenterY = (textRect.top + textRect.bottom) / 2

        // 找到距离文本最近的头像
        var nearestAvatar: AvatarInfo? = null
        var minDistance = Float.MAX_VALUE

        avatarPositions.forEach { avatar ->
            val distance = calculateDistance(
                textCenterX.toFloat(), textCenterY.toFloat(),
                avatar.centerX, avatar.centerY
            )
            
            // 只考虑水平距离不太远的头像（同一行或相邻行）
            val verticalDistance = abs(textCenterY - avatar.centerY)
            if (verticalDistance < 150 && distance < minDistance) {
                minDistance = distance
                nearestAvatar = avatar
            }
        }

        nearestAvatar?.let { avatar ->
            // 根据头像位置判断发送者
            // 头像在左侧 -> 对方，头像在右侧 -> 我
            val senderType = if (avatar.centerX < imageWidth / 2) {
                SenderType.CONTACT
            } else {
                SenderType.USER
            }
            
            // 根据距离计算置信度
            val confidence = (1.0f - (minDistance / 200.0f)).coerceIn(0.5f, 1.0f)
            
            return Pair(senderType, confidence)
        }

        return null
    }

    /**
     * 根据上下文推断发送者。
     * 当位置和头像检测都不明确时，根据上一个已知发送者和当前位置推断
     */
    private fun inferSenderFromContext(
        rect: Rect,
        imageWidth: Int,
        lastKnownSender: SenderType
    ): SenderType {
        val blockCenterX = (rect.left + rect.right) / 2
        
        // 根据上一个发送者和当前位置推断
        return when (lastKnownSender) {
            SenderType.USER -> {
                // 上一条是我发的，这条如果也在右侧，可能还是我
                if (blockCenterX > imageWidth * 0.55) SenderType.USER
                else SenderType.CONTACT
            }
            SenderType.CONTACT -> {
                // 上一条是对方发的，这条如果也在左侧，可能还是对方
                if (blockCenterX < imageWidth * 0.45) SenderType.CONTACT
                else SenderType.USER
            }
            else -> SenderType.UNKNOWN
        }
    }

    /**
     * 检测头像位置。
     * 使用简单的图像处理检测圆形头像区域
     */
    private fun detectAvatars(bitmap: Bitmap): List<AvatarInfo> {
        val avatars = mutableListOf<AvatarInfo>()
        val width = bitmap.width
        val height = bitmap.height
        
        // 采样步长（减少计算量）
        val step = 5
        
        // 检测区域：左右两侧（头像通常在边缘）
        val leftRegion = 0..(width * 0.25).toInt()
        val rightRegion = (width * 0.75).toInt()..width
        
        val regions = listOf(leftRegion, rightRegion)
        
        regions.forEach { region ->
            for (y in 100 until height - 100 step step) {
                for (x in region step step) {
                    if (isLikelyAvatarCenter(bitmap, x, y)) {
                        val avatarSize = estimateAvatarSize(bitmap, x, y)
                        if (avatarSize in AVATAR_SIZE_MIN..AVATAR_SIZE_MAX) {
                            // 检查是否已经被检测过（去重）
                            val isDuplicate = avatars.any { existing ->
                                calculateDistance(
                                    existing.centerX, existing.centerY,
                                    x.toFloat(), y.toFloat()
                                ) < 50
                            }
                            
                            if (!isDuplicate) {
                                avatars.add(AvatarInfo(x.toFloat(), y.toFloat(), avatarSize))
                            }
                        }
                    }
                }
            }
        }
        
        return avatars
    }

    /**
     * 检查某点是否可能是头像中心
     * 基于颜色特征：头像通常是圆形，有特定的颜色分布
     */
    private fun isLikelyAvatarCenter(bitmap: Bitmap, centerX: Int, centerY: Int): Boolean {
        val radius = 30
        var skinColorCount = 0
        var totalCount = 0
        
        for (dy in -radius..radius step 3) {
            for (dx in -radius..radius step 3) {
                val x = centerX + dx
                val y = centerY + dy
                
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    if (dx * dx + dy * dy <= radius * radius) {
                        val pixel = bitmap.getPixel(x, y)
                        if (isSkinColor(pixel)) {
                            skinColorCount++
                        }
                        totalCount++
                    }
                }
            }
        }
        
        // 如果圆形区域内有一定比例的皮肤色，可能是头像
        return totalCount > 0 && skinColorCount.toFloat() / totalCount > 0.3f
    }

    /**
     * 判断是否为皮肤色
     */
    private fun isSkinColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        // 简化的皮肤色判断
        return r > 60 && g > 40 && b > 20 &&
               r > g && r > b &&
               abs(r - g) > 15 &&
               r > 95 && g > 40 && b > 20
    }

    /**
     * 估计头像大小
     */
    private fun estimateAvatarSize(bitmap: Bitmap, centerX: Int, centerY: Int): Int {
        // 从中心向外扩展，找到边界
        var size = AVATAR_SIZE_MIN
        
        while (size < AVATAR_SIZE_MAX) {
            val radius = size / 2
            var edgePixelCount = 0
            var nonSkinPixelCount = 0
            
            // 检查圆周上的像素
            for (angle in 0 until 360 step 10) {
                val rad = Math.toRadians(angle.toDouble())
                val x = (centerX + radius * kotlin.math.cos(rad)).toInt()
                val y = (centerY + radius * kotlin.math.sin(rad)).toInt()
                
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    edgePixelCount++
                    if (!isSkinColor(bitmap.getPixel(x, y))) {
                        nonSkinPixelCount++
                    }
                }
            }
            
            // 如果圆周上太多非皮肤色像素，说明到了边界
            if (edgePixelCount > 0 && nonSkinPixelCount.toFloat() / edgePixelCount > 0.5f) {
                break
            }
            
            size += 5
        }
        
        return size
    }

    /**
     * 计算两点距离
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
    }

    /**
     * 保持每条识别消息独立。
     * 
     * 每个 OCR 文本块作为独立消息交由上层处理。
     */
    private fun mergeConsecutiveMessages(messages: List<OcrMessage>): List<OcrMessage> {
        // 保留全部独立消息。
        // 这样可以避免错误地将不同发送者的消息混在一起
        Log.d(TAG, "消息处理: 共 ${messages.size} 条独立消息，不做合并")
        return messages
    }

    /**
     * 识别Bitmap中的文字
     */
    private suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        if (BUSINESS_ID.isBlank()) {
            throw OcrException("vivo OCR business ID is not configured")
        }

        // 1. 压缩并编码图片为Base64
        val base64Image = bitmapToBase64(bitmap)

        // 2. 构建请求参数
        val requestId = UUID.randomUUID().toString()
        val formBody = FormBody.Builder()
            .add("image", base64Image)
            .add("pos", "2") // pos=2 同时提供文字信息和坐标信息（相对值）
            .add("businessid", BUSINESS_ID)
            .build()

        // 3. 构建请求
        val request = Request.Builder()
            .url("$URL?requestId=$requestId")
            .post(formBody)
            .header("Authorization", "Bearer $vivoApiKey")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        // 4. 发送请求
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw OcrException("HTTP error: ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw OcrException("Empty response body")

        // 5. 解析响应
        parseOcrResponse(responseBody)
    }

    /**
     * 识别长截图的关键区域。
     * 对于聊天截图，只识别顶部（联系人名称）和底部（最近消息）
     */
    private suspend fun recognizeLongBitmapOptimized(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val width = bitmap.width
        val height = bitmap.height

        Log.d(TAG, "开始智能识别长截屏: ${width}x${height}")

        // 聊天长截图按三个关键区域识别。
        // 1. 顶部区域（联系人名称）
        // 2. 中间区域（部分历史消息）
        // 3. 底部区域（最近消息）

        val topHeight = (height * 0.15).toInt().coerceAtMost(800)  // 顶部15%，最多800像素
        val bottomHeight = (height * 0.25).toInt().coerceAtMost(1200)  // 底部25%，最多1200像素
        val middleStart = (height * 0.35).toInt()  // 中间区域开始
        val middleHeight = (height * 0.15).toInt().coerceAtMost(800)  // 中间15%，最多800像素

        // 并行识别3个区域
        val deferredResults = listOf(
            // 顶部区域
            async {
                Log.d(TAG, "识别顶部区域: 0-$topHeight")
                val chunk = Bitmap.createBitmap(bitmap, 0, 0, width, topHeight)
                try {
                    recognizeBitmap(chunk)
                } finally {
                    chunk.recycle()
                }
            },
            // 中间区域（可选，用于获取更多上下文）
            async {
                if (height > 3000) {
                    Log.d(TAG, "识别中间区域: $middleStart-${middleStart + middleHeight}")
                    val chunk = Bitmap.createBitmap(bitmap, 0, middleStart, width, middleHeight)
                    try {
                        val result = recognizeBitmap(chunk)
                        // 调整坐标
                        result.copy(blocks = result.blocks.map { block ->
                            block.copy(
                                boundingBox = block.boundingBox?.let { rect ->
                                    Rect(rect.left, rect.top + middleStart, rect.right, rect.bottom + middleStart)
                                }
                            )
                        })
                    } finally {
                        chunk.recycle()
                    }
                } else {
                    OcrResult("", 100f, emptyList())
                }
            },
            // 底部区域（最重要）
            async {
                Log.d(TAG, "识别底部区域: ${height - bottomHeight}-$height")
                val startY = height - bottomHeight
                val chunk = Bitmap.createBitmap(bitmap, 0, startY, width, bottomHeight)
                try {
                    val result = recognizeBitmap(chunk)
                    // 调整坐标
                    result.copy(blocks = result.blocks.map { block ->
                        block.copy(
                            boundingBox = block.boundingBox?.let { rect ->
                                Rect(rect.left, rect.top + startY, rect.right, rect.bottom + startY)
                            }
                        )
                    })
                } finally {
                    chunk.recycle()
                }
            }
        )

        // 等待所有识别完成
        val results = deferredResults.awaitAll()

        // 合并结果
        val allBlocks = mutableListOf<TextBlock>()
        val fullTextBuilder = StringBuilder()

        results.forEachIndexed { index, result ->
            val regionName = when (index) {
                0 -> "顶部"
                1 -> "中间"
                else -> "底部"
            }
            Log.d(TAG, "$regionName 区域识别结果: ${result.blocks.size} 个文本块")
            allBlocks.addAll(result.blocks)
            if (result.text.isNotBlank()) {
                if (fullTextBuilder.isNotEmpty()) {
                    fullTextBuilder.append("\n")
                }
                fullTextBuilder.append(result.text)
            }
        }

        Log.d(TAG, "智能识别完成，共 ${allBlocks.size} 个文本块")

        OcrResult(
            text = fullTextBuilder.toString().trim(),
            confidence = 100f,
            blocks = allBlocks
        )
    }

    /**
     * 分块识别完整长截图。
     */
    private suspend fun recognizeLongBitmap(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val width = bitmap.width
        val height = bitmap.height

        Log.d(TAG, "开始分割长截屏: ${width}x${height}")

        // 计算需要分割成几块
        val effectiveHeight = SPLIT_HEIGHT - OVERLAP_HEIGHT
        val numChunks = kotlin.math.ceil(height.toFloat() / effectiveHeight).toInt()

        Log.d(TAG, "分割成 $numChunks 块进行识别")

        // 分割图片并并行识别
        val deferredResults = (0 until numChunks).map { index ->
            async {
                // 计算当前块的坐标
                val startY = index * effectiveHeight
                val endY = kotlin.math.min(startY + SPLIT_HEIGHT, height)
                val actualHeight = endY - startY

                Log.d(TAG, "处理第 ${index + 1}/$numChunks 块: y=$startY, height=$actualHeight")

                // 裁剪图片块
                val chunkBitmap = Bitmap.createBitmap(bitmap, 0, startY, width, actualHeight)

                // 识别这一块
                try {
                    val result = recognizeBitmap(chunkBitmap)
                    // 调整坐标（加上偏移量）
                    val adjustedBlocks = result.blocks.map { block ->
                        block.copy(
                            boundingBox = block.boundingBox?.let { rect ->
                                Rect(
                                    left = rect.left,
                                    top = rect.top + startY,
                                    right = rect.right,
                                    bottom = rect.bottom + startY
                                )
                            }
                        )
                    }
                    result.copy(blocks = adjustedBlocks)
                } finally {
                    // 释放临时bitmap
                    if (chunkBitmap != bitmap) {
                        chunkBitmap.recycle()
                    }
                }
            }
        }

        // 等待所有识别完成
        val results = deferredResults.awaitAll()

        // 合并结果
        val allBlocks = mutableListOf<TextBlock>()
        val fullTextBuilder = StringBuilder()

        results.forEachIndexed { index, result ->
            Log.d(TAG, "第 ${index + 1} 块识别结果: ${result.blocks.size} 个文本块")
            allBlocks.addAll(result.blocks)
            if (result.text.isNotBlank()) {
                if (fullTextBuilder.isNotEmpty()) {
                    fullTextBuilder.append("\n")
                }
                fullTextBuilder.append(result.text)
            }
        }

        Log.d(TAG, "长截屏识别完成，共 ${allBlocks.size} 个文本块")

        OcrResult(
            text = fullTextBuilder.toString().trim(),
            confidence = 100f,
            blocks = allBlocks
        )
    }

    /**
     * 从URI加载Bitmap
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将Bitmap转换为Base64字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        // 压缩图片以控制大小
        val compressedBitmap = compressBitmap(bitmap)
        val outputStream = ByteArrayOutputStream()
        compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 压缩Bitmap
     * 限制最大尺寸为1920x1920，避免请求过大
     */
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val maxSize = 1920
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 解析OCR响应
     */
    private fun parseOcrResponse(responseBody: String): OcrResult {
        return try {
            val response = json.decodeFromString<VivoOcrResponse>(responseBody)

            when (response.error_code) {
                0 -> {
                    // 成功
                    val blocks = mutableListOf<TextBlock>()
                    var fullText = ""

                    // 解析pos=2的结果（包含OCR数组）
                    response.result?.OCR?.forEach { ocrItem ->
                        blocks.add(
                            TextBlock(
                                text = ocrItem.words,
                                boundingBox = ocrItem.location?.let { loc ->
                                    Rect(
                                        left = loc.top_left.x.toInt(),
                                        top = loc.top_left.y.toInt(),
                                        right = loc.down_right.x.toInt(),
                                        bottom = loc.down_right.y.toInt()
                                    )
                                }
                            )
                        )
                        fullText += ocrItem.words + "\n"
                    }

                    // 如果没有OCR数组，尝试解析words数组（pos=0的情况）
                    if (blocks.isEmpty()) {
                        response.result?.words?.forEach { wordItem ->
                            blocks.add(TextBlock(text = wordItem.words))
                            fullText += wordItem.words + "\n"
                        }
                    }

                    OcrResult(
                        text = fullText.trim(),
                        confidence = 100f, // Vivo OCR不返回置信度，默认100
                        blocks = blocks
                    )
                }
                1 -> throw OcrException("OCR recognition failed: ${response.error_msg}")
                2 -> throw OcrException("Image error: ${response.error_msg}")
                else -> throw OcrException("Unknown error: ${response.error_msg}")
            }
        } catch (e: Exception) {
            if (e is OcrException) throw e
            throw OcrException("Failed to parse OCR response: ${e.message}")
        }
    }
}

/**
 * 头像信息数据类
 */
private data class AvatarInfo(
    val centerX: Float,
    val centerY: Float,
    val size: Int
)

/**
 * Vivo OCR响应数据类
 */
@Serializable
private data class VivoOcrResponse(
    val error_code: Int = 0,
    val error_msg: String? = null,
    val result: VivoOcrResult? = null,
    val version: String? = null,
    val support: String? = null
)

@Serializable
private data class VivoOcrResult(
    val words: List<VivoWordItem>? = null,
    val OCR: List<VivoOcrItem>? = null,
    val angle: Int? = null
)

@Serializable
private data class VivoWordItem(
    val words: String
)

@Serializable
private data class VivoOcrItem(
    val words: String,
    val location: VivoLocation? = null
)

@Serializable
private data class VivoLocation(
    val top_left: VivoPoint,
    val top_right: VivoPoint,
    val down_left: VivoPoint,
    val down_right: VivoPoint
)

@Serializable
private data class VivoPoint(
    val x: Double,
    val y: Double
)
