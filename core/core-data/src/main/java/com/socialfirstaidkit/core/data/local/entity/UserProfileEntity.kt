package com.socialfirstaidkit.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val nickname: String = "",
    val avatarUri: String? = null,
    val gender: String = "unknown",
    val age: Int = 0,
    val comicAvatarUrl: String? = null, // 用户专属漫画形象URL
    val comicAvatarGeneratedAt: Long? = null, // 漫画形象生成时间
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
