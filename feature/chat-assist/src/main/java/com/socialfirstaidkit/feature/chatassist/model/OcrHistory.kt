package com.socialfirstaidkit.feature.chatassist.model

import com.socialfirstaidkit.core.data.model.OcrResult
import kotlinx.serialization.Serializable

/**
 * OCR 识别历史记录。
 */
@Serializable
data class OcrHistory(
    val id: String,
    val timestamp: Long,
    val ocrResult: OcrResult,
    val contactName: String,
    val messageCount: Int
)

/**
 * 重复检测结果。
 */
sealed class DuplicateCheckResult {
    data class NoDuplicate(val similarity: Float) : DuplicateCheckResult()
    data class Duplicate(
        val historyId: String,
        val similarity: Float,
        val existingHistory: OcrHistory
    ) : DuplicateCheckResult()
}
