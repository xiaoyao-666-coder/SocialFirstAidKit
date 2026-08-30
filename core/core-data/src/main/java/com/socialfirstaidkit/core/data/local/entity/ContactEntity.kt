package com.socialfirstaidkit.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,           // 联系人ID
    val displayName: String,
    val relationType: String,             // 'leader', 'colleague', 'friend', 'family'
    val intimacyScore: Int = 5,           // 亲密度1-10
    val lastInteraction: Long = System.currentTimeMillis(),
    val avatarUri: String? = null,        // 联系人头像路径
    val defaultRelationId: String? = null, // 联系人全局默认身份ID
    val defaultStyleId: String? = null    // 联系人全局默认风格ID
)
