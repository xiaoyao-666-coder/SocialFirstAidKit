package com.socialfirstaidkit.core.data.local.dao

import androidx.room.*
import com.socialfirstaidkit.core.data.local.entity.CachedMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageCacheDao {
    @Query("SELECT * FROM message_cache WHERE sourceApp = :app ORDER BY timestamp DESC LIMIT 20")
    fun getRecentMessages(app: String): Flow<List<CachedMessage>>

    @Query("SELECT * FROM message_cache WHERE sourceApp = :app ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentMessagesOnce(app: String): List<CachedMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: CachedMessage)

    @Query("DELETE FROM message_cache WHERE expireAt < :currentTime")
    suspend fun deleteExpired(currentTime: Long): Int

    @Query("DELETE FROM message_cache")
    suspend fun deleteAll()
}
