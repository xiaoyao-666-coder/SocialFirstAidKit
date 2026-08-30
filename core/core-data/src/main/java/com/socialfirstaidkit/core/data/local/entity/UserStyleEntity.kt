package com.socialfirstaidkit.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_style")
data class UserStyleEntity(
    @PrimaryKey val phrase: String,
    val frequency: Int = 1,
    val category: String  // 'emoji_usage', 'formal_words', 'slang'
)
