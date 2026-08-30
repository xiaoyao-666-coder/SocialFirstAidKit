package com.socialfirstaidkit.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_cache")
data class CachedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceApp: String,       // chat app key
    val senderName: String,
    val messageContent: String,
    val timestamp: Long,
    val expireAt: Long           // 24小时后过期
)
