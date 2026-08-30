package com.socialfirstaidkit.core.llm.model

import kotlinx.serialization.Serializable

/**
 * 风格化回复结果JSON数据类（新格式：option1, option2, option3）
 */
@Serializable
data class StyledReplyResult(
    val option1: String,
    val option2: String,
    val option3: String
)

/**
 * 截图分析JSON数据类
 */
@Serializable
data class ScreenshotAnalysisJson(
    val contactName: String? = null,
    val messages: List<MessageJson> = emptyList(),
    val conversationSummary: String? = null
)

@Serializable
data class MessageJson(
    val senderType: String,
    val senderName: String,
    val content: String,
    val timestamp: String? = null
)

@Serializable
data class SingleMessageContextAnalysisJson(
    val scene: String = "",
    val tone: String = "",
    val emotion: String = "",
    val intent: String = "",
    val strategy: String = ""
)
