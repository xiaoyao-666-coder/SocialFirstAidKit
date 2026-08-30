package com.socialfirstaidkit.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "fingerprint"], unique = true)
    ]
)
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val sessionId: String,
    val senderType: String, // "USER" or "CONTACT"
    val senderName: String,
    val content: String,
    val messageType: String = "TEXT",
    val fingerprint: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "NORMAL" // "NORMAL" or "PENDING_SEND"
)
