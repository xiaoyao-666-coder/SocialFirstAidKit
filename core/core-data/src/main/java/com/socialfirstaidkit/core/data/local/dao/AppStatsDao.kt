package com.socialfirstaidkit.core.data.local.dao

import androidx.room.*
import com.socialfirstaidkit.core.data.local.entity.AppStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppStatsDao {
    @Query("SELECT * FROM app_stats WHERE id = 1")
    fun getStats(): Flow<AppStatsEntity?>

    @Query("SELECT * FROM app_stats WHERE id = 1")
    suspend fun getStatsSync(): AppStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: AppStatsEntity)

    @Update
    suspend fun update(stats: AppStatsEntity)

    /**
     * 获取当前统计记录，如果不存在则创建
     */
    @Transaction
    suspend fun getOrCreateStats(): AppStatsEntity {
        android.util.Log.d("AppStatsDao", "获取或创建统计记录")
        var stats = getStatsSync()
        if (stats == null) {
            android.util.Log.d("AppStatsDao", "记录不存在，创建新记录")
            stats = AppStatsEntity()
            insert(stats)
            android.util.Log.d("AppStatsDao", "新记录已创建，ID=${stats.id}")
        } else {
            android.util.Log.d("AppStatsDao", "找到现有记录，ID=${stats.id}")
        }
        return stats
    }

    /**
     * 增加回复生成计数
     */
    @Transaction
    suspend fun incrementRepliesGenerated() {
        val stats = getOrCreateStats()
        stats.totalRepliesGenerated += 1
        update(stats)
    }

    /**
     * 增加文案生成计数
     */
    @Transaction
    suspend fun incrementCopiesGenerated() {
        val stats = getOrCreateStats()
        stats.totalCopiesGenerated += 1
        update(stats)
    }

    /**
     * 增加消息分析计数
     */
    @Transaction
    suspend fun incrementMessagesAnalyzed() {
        val stats = getOrCreateStats()
        stats.totalMessagesAnalyzed += 1
        update(stats)
    }

    /**
     * 增加应用打开计数
     */
    @Transaction
    suspend fun incrementAppOpenCount() {
        val stats = getOrCreateStats()
        stats.appOpenCount += 1
        stats.lastOpenDate = System.currentTimeMillis()
        update(stats)
    }

    @Query("DELETE FROM app_stats WHERE id = 1")
    suspend fun delete()
}
