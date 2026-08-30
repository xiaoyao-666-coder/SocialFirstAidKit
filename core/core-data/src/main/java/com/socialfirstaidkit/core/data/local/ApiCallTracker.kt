package com.socialfirstaidkit.core.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API 调用计数追踪器
 * 用于追踪 vivo 图片生成 API 的每日调用次数
 */
@Singleton
class ApiCallTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "api_call_tracker"
        private const val KEY_CALL_COUNT = "image_generation_call_count"
        private const val KEY_LAST_CALL_DATE = "image_generation_last_date"
        private const val KEY_TOTAL_CALL_COUNT = "image_generation_total_count"

        // 限制配置
        const val DAILY_LIMIT = 10  // 每天限制 10 次
        const val TOTAL_LIMIT = 300 // 总共限制 300 次

        private const val DEFAULT_SCOPE = ""
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取今天的日期字符串
     */
    private fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun scopedKey(baseKey: String, scope: String): String {
        return if (scope.isBlank()) baseKey else "${baseKey}_$scope"
    }

    /**
     * 获取今天的调用次数
     */
    fun getTodayCallCount(scope: String = DEFAULT_SCOPE): Int {
        val lastCallDate = prefs.getString(scopedKey(KEY_LAST_CALL_DATE, scope), "") ?: ""
        val today = getTodayString()

        return if (lastCallDate == today) {
            prefs.getInt(scopedKey(KEY_CALL_COUNT, scope), 0)
        } else {
            // 新的一天，重置计数
            0
        }
    }

    /**
     * 获取总调用次数
     */
    fun getTotalCallCount(scope: String = DEFAULT_SCOPE): Int {
        return prefs.getInt(scopedKey(KEY_TOTAL_CALL_COUNT, scope), 0)
    }

    /**
     * 检查是否可以调用 API
     * @return Pair<Boolean, String?> - 是否可以调用，以及不能调用的原因
     */
    fun canMakeCall(scope: String = DEFAULT_SCOPE): Pair<Boolean, String?> {
        val todayCount = getTodayCallCount(scope)
        val totalCount = getTotalCallCount(scope)

        return when {
            todayCount >= DAILY_LIMIT -> {
                Pair(false, "今日图片生成次数已用完（${DAILY_LIMIT}/${DAILY_LIMIT}次），请明天再试")
            }
            totalCount >= TOTAL_LIMIT -> {
                Pair(false, "总图片生成次数已用完（${TOTAL_LIMIT}/${TOTAL_LIMIT}次）")
            }
            else -> {
                Pair(true, null)
            }
        }
    }

    /**
     * 记录一次 API 调用
     * @return 记录后的今日调用次数
     */
    fun recordCall(scope: String = DEFAULT_SCOPE): Int {
        val today = getTodayString()
        val lastCallDate = prefs.getString(scopedKey(KEY_LAST_CALL_DATE, scope), "") ?: ""

        val editor = prefs.edit()

        val newTodayCount = if (lastCallDate == today) {
            // 同一天，增加计数
            val currentCount = prefs.getInt(scopedKey(KEY_CALL_COUNT, scope), 0)
            currentCount + 1
        } else {
            // 新的一天，重置为 1
            1
        }

        val newTotalCount = prefs.getInt(scopedKey(KEY_TOTAL_CALL_COUNT, scope), 0) + 1

        editor.putString(scopedKey(KEY_LAST_CALL_DATE, scope), today)
        editor.putInt(scopedKey(KEY_CALL_COUNT, scope), newTodayCount)
        editor.putInt(scopedKey(KEY_TOTAL_CALL_COUNT, scope), newTotalCount)
        editor.apply()

        return newTodayCount
    }

    /**
     * 获取调用次数信息（显示已用/限制）
     */
    fun getCallsInfo(scope: String = DEFAULT_SCOPE): String {
        val todayCount = getTodayCallCount(scope)
        val totalCount = getTotalCallCount(scope)

        return "今日已用 ${todayCount}/${DAILY_LIMIT} 次，总共已用 ${totalCount}/${TOTAL_LIMIT} 次"
    }

    /**
     * 重置计数（仅用于测试）
     */
    fun resetCounts(scope: String = DEFAULT_SCOPE) {
        prefs.edit()
            .putInt(scopedKey(KEY_CALL_COUNT, scope), 0)
            .putInt(scopedKey(KEY_TOTAL_CALL_COUNT, scope), 0)
            .putString(scopedKey(KEY_LAST_CALL_DATE, scope), "")
            .apply()
    }

    /**
     * 设置初始调用次数（用于同步服务器实际使用情况）
     * @param todayCount 今天已用次数
     * @param totalCount 总共已用次数
     */
    fun setInitialCounts(todayCount: Int, totalCount: Int, scope: String = DEFAULT_SCOPE) {
        val today = getTodayString()
        prefs.edit()
            .putString(scopedKey(KEY_LAST_CALL_DATE, scope), today)
            .putInt(scopedKey(KEY_CALL_COUNT, scope), todayCount)
            .putInt(scopedKey(KEY_TOTAL_CALL_COUNT, scope), totalCount)
            .apply()
    }

    /**
     * 获取详细的调用次数信息
     */
    fun getDetailedInfo(scope: String = DEFAULT_SCOPE): String {
        val todayCount = getTodayCallCount(scope)
        val totalCount = getTotalCallCount(scope)
        val todayRemaining = (DAILY_LIMIT - todayCount).coerceAtLeast(0)
        val totalRemaining = (TOTAL_LIMIT - totalCount).coerceAtLeast(0)

        return """
            本地记录：
            - 今日已用: $todayCount / $DAILY_LIMIT 次
            - 今日剩余: $todayRemaining 次
            - 总共已用: $totalCount / $TOTAL_LIMIT 次
            - 总共剩余: $totalRemaining 次
            
            注意：如果其他功能（如朋友圈创作）也调用了API，
            实际剩余次数可能少于显示值。
        """.trimIndent()
    }
}
