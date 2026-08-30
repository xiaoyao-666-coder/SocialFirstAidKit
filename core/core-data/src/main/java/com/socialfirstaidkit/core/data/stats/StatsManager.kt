package com.socialfirstaidkit.core.data.stats

import com.socialfirstaidkit.core.data.local.dao.AppStatsDao
import com.socialfirstaidkit.core.data.local.entity.AppStatsEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsManager @Inject constructor(
    private val appStatsDao: AppStatsDao
) {
    val stats: Flow<AppStatsEntity?> = appStatsDao.getStats()

    suspend fun incrementRepliesGenerated() {
        try {
            appStatsDao.incrementRepliesGenerated()
            android.util.Log.d("StatsManager", "回复生成计数+1")
        } catch (e: Exception) {
            android.util.Log.e("StatsManager", "增加回复生成计数失败", e)
        }
    }

    suspend fun incrementCopiesGenerated() {
        try {
            appStatsDao.incrementCopiesGenerated()
            android.util.Log.d("StatsManager", "文案生成计数+1")
        } catch (e: Exception) {
            android.util.Log.e("StatsManager", "增加文案生成计数失败", e)
        }
    }

    suspend fun incrementMessagesAnalyzed() {
        try {
            appStatsDao.incrementMessagesAnalyzed()
            android.util.Log.d("StatsManager", "消息分析计数+1")
        } catch (e: Exception) {
            android.util.Log.e("StatsManager", "增加消息分析计数失败", e)
        }
    }

    suspend fun incrementAppOpenCount() {
        try {
            appStatsDao.incrementAppOpenCount()
            android.util.Log.d("StatsManager", "应用打开计数+1")
        } catch (e: Exception) {
            android.util.Log.e("StatsManager", "增加应用打开计数失败", e)
        }
    }

    suspend fun clearStats() {
        appStatsDao.delete()
    }
}
