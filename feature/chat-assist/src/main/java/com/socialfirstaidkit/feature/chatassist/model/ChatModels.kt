package com.socialfirstaidkit.feature.chatassist.model

import com.socialfirstaidkit.core.data.local.entity.ContactEntity
import com.socialfirstaidkit.core.data.local.entity.ChatSessionEntity

data class ContactWithLastMessage(
    val contact: ContactEntity,
    val lastMessageText: String,
    val lastMessageTime: Long
)

data class SessionWithInfo(
    val session: ChatSessionEntity,
    val messageCount: Int,
    val lastMessageText: String
)
