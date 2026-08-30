package com.socialfirstaidkit.core.data.model

import kotlinx.serialization.Serializable

/**
 * OCR识别结果
 */
@Serializable
data class OcrResult(
    val text: String,
    val confidence: Float,
    val blocks: List<TextBlock> = emptyList(),
    val messages: List<OcrMessage> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)

@Serializable
data class TextBlock(
    val text: String,
    val boundingBox: Rect? = null
)

@Serializable
data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * 带发送者信息的OCR消息
 * 包含位置信息和识别的发送者类型
 */
@Serializable
data class OcrMessage(
    val content: String,
    val senderType: SenderType,
    val boundingBox: Rect,
    val confidence: Float = 1.0f,
    val detectionMethod: DetectionMethod = DetectionMethod.POSITION
)

/**
 * 发送者类型
 */
@Serializable
enum class SenderType {
    USER,       // 我（用户本人）
    CONTACT,    // 对方（聊天对象）
    UNKNOWN     // 未知（无法判断）
}

/**
 * 消息发送者检测方法
 * 用于追踪消息是如何被识别出来的
 */
@Serializable
enum class DetectionMethod {
    BUBBLE,
    POSITION,       // 基于位置判断
    AVATAR,         // 基于头像检测
    CONTENT,        // 基于内容分析（如自称）
    LLM_CONFIRMED   // LLM二次确认
}
