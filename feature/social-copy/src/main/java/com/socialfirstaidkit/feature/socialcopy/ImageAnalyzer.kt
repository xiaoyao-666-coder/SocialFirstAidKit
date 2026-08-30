package com.socialfirstaidkit.feature.socialcopy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ImageAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val textRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    data class OcrResult(
        val fullText: String,
        val blocks: List<TextBlock>
    )

    data class TextBlock(
        val text: String,
        val boundingBox: android.graphics.Rect?
    )

    data class ImageAnalysisResult(
        val ocrText: String,
        val labels: List<String>,
        val description: String,
        val imageBase64: String? = null
    )

    suspend fun analyzeImage(imageUri: Uri): ImageAnalysisResult = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val ocrResult = recognizeText(inputImage)
        val labels = emptyList<String>()

        val base64 = try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            }
            bitmap?.let { bitmapToBase64ForAI(it) }
        } catch (e: Exception) {
            null
        }

        val description = buildString {
            append("图片中的文字内容：")
            append(if (ocrResult.fullText.isNotBlank()) ocrResult.fullText else "无文字")
            append("\n图片内容：已保留原图给多模态模型理解")
        }

        ImageAnalysisResult(
            ocrText = ocrResult.fullText,
            labels = labels,
            description = description,
            imageBase64 = base64
        )
    }

    private fun bitmapToBase64ForAI(bitmap: Bitmap): String {
        val maxDim = 3072f
        val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height)
        val scaledBitmap = if (scale < 1f) {
            val matrix = Matrix().apply { postScale(scale, scale) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 96, outputStream)
        val byteArray = outputStream.toByteArray()

        if (scaledBitmap != bitmap) scaledBitmap.recycle()

        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private suspend fun recognizeText(inputImage: InputImage): OcrResult {
        return suspendCancellableCoroutine { cont ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        TextBlock(
                            text = block.text,
                            boundingBox = block.boundingBox
                        )
                    }
                    cont.resume(OcrResult(fullText = visionText.text, blocks = blocks))
                }
                .addOnFailureListener {
                    cont.resume(OcrResult(fullText = "", blocks = emptyList()))
                }
        }
    }
}
