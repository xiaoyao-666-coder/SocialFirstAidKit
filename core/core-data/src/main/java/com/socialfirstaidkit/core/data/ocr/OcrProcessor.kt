package com.socialfirstaidkit.core.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.socialfirstaidkit.core.data.model.OcrResult
import com.socialfirstaidkit.core.data.model.TextBlock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR处理器
 * 使用ML Kit进行图片文字识别
 */
@Singleton
class OcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 从URI识别图片中的文字
     */
    suspend fun recognizeText(imageUri: Uri): OcrResult = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            processImage(image)
        } catch (e: IOException) {
            throw OcrException("Failed to load image: ${e.message}")
        }
    }

    /**
     * 从Bitmap识别文字
     */
    suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int = 0): OcrResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        processImage(image)
    }

    /**
     * 处理图片并返回OCR结果
     */
    private suspend fun processImage(image: InputImage): OcrResult =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        TextBlock(
                            text = block.text,
                            boundingBox = block.boundingBox?.let {
                                com.socialfirstaidkit.core.data.model.Rect(
                                    left = it.left,
                                    top = it.top,
                                    right = it.right,
                                    bottom = it.bottom
                                )
                            }
                        )
                    }

                    val result = OcrResult(
                        text = visionText.text,
                        confidence = calculateConfidence(visionText.textBlocks),
                        blocks = blocks
                    )
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(OcrException("OCR processing failed: ${e.message}"))
                }
        }

    /**
     * 计算置信度（简化版）
     */
    private fun calculateConfidence(blocks: List<com.google.mlkit.vision.text.Text.TextBlock>): Float {
        if (blocks.isEmpty()) return 0f

        // 基于文本块数量和平均长度估算置信度
        val avgLength = blocks.map { it.text.length }.average()
        val blockScore = minOf(blocks.size / 5f, 1f) // 最多5个块得满分
        val lengthScore = minOf(avgLength.toFloat() / 20f, 1f) // 平均每块20字符得满分

        return (blockScore * 0.4f + lengthScore * 0.6f) * 100f
    }

    /**
     * 清理资源
     */
    fun close() {
        recognizer.close()
    }
}

/**
 * OCR异常
 */
class OcrException(message: String) : Exception(message)
