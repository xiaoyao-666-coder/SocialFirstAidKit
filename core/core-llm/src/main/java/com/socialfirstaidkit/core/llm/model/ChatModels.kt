package com.socialfirstaidkit.core.llm.model

import kotlinx.serialization.Serializable

/**
 * 聊天消息数据类
 * 用于表示对话中的一条消息
 */
@Serializable
data class ChatMessage(
    val id: String,
    val senderType: SenderType,
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 发送者类型
 */
@Serializable
enum class SenderType {
    USER,       // 用户本人
    CONTACT     // 聊天对象
}

/**
 * 对话会话
 * 用于存储完整的对话上下文
 */
@Serializable
data class ChatSession(
    val sessionId: String,
    val contactName: String,
    val contactAvatarUri: String? = null,
    val preferredRelationId: String = "friend",
    val preferredStyleId: String = "default",
    val messages: List<ChatMessage>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
