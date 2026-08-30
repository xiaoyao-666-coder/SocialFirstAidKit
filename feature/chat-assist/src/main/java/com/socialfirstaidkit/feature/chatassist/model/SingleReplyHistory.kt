package com.socialfirstaidkit.feature.chatassist.model

import kotlinx.serialization.Serializable

@Serializable
data class SingleReplyHistory(
    val id: String,
    val createdAt: Long,
    val sourceMessage: String,
    val formal: String,
    val casual: String,
    val humorous: String
)
