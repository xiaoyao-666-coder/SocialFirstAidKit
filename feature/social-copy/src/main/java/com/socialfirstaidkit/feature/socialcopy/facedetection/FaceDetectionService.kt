package com.socialfirstaidkit.feature.socialcopy.facedetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceDetectionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DetectedFace(
        val boundingBox: Rect,
        val trackingId: Int? = null,
        val smileProbability: Float? = null,
        val leftEyeOpenProbability: Float? = null,
        val rightEyeOpenProbability: Float? = null
    )

    data class FaceDetectionResult(
        val faces: List<DetectedFace>,
        val imageWidth: Int,
        val imageHeight: Int,
        val imageBase64: String
    ) {
        val hasFaces: Boolean get() = faces.isNotEmpty()
        val faceCount: Int get() = faces.size
    }

    suspend fun detectFaces(imageUri: Uri): Result<FaceDetectionResult> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadBitmapFromUri(imageUri)
            val detectedFace = fullImageFace(bitmap)
            Result.success(
                FaceDetectionResult(
                    faces = listOf(detectedFace),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    imageBase64 = bitmapToBase64(bitmap)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun detectFacesForPreview(imageUri: Uri): Result<List<DetectedFace>> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadBitmapFromUri(imageUri)
            Result.success(listOf(fullImageFace(bitmap)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cropFace(imageUri: Uri, face: DetectedFace): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadBitmapFromUri(imageUri)
            val rect: Rect = face.boundingBox
            val left = rect.left.coerceIn(0, bitmap.width)
            val top = rect.top.coerceIn(0, bitmap.height)
            val right = rect.right.coerceIn(0, bitmap.width)
            val bottom = rect.bottom.coerceIn(0, bitmap.height)
            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) {
                return@withContext Result.failure(Exception("Invalid image bounds"))
            }

            Result.success(Bitmap.createBitmap(bitmap, left, top, width, height))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFaceBase64(imageUri: Uri, face: DetectedFace): Result<String> = withContext(Dispatchers.IO) {
        cropFace(imageUri, face).map { bitmap ->
            bitmapToBase64(bitmap)
        }
    }

    private fun fullImageFace(bitmap: Bitmap): DetectedFace {
        return DetectedFace(
            boundingBox = Rect(0, 0, bitmap.width, bitmap.height)
        )
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("Cannot load image from uri: $uri")
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun close() = Unit
}
