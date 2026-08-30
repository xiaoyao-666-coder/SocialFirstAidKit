package com.socialfirstaidkit.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_stats")
data class AppStatsEntity(
    @PrimaryKey val id: Int = 1,
    var totalRepliesGenerated: Int = 0,
    var totalCallsAssisted: Int = 0,
    var totalCopiesGenerated: Int = 0,
    var totalMessagesAnalyzed: Int = 0,
    var appOpenCount: Int = 0,
    var firstOpenDate: Long = System.currentTimeMillis(),
    var lastOpenDate: Long = System.currentTimeMillis()
)
