package com.socialfirstaidkit.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.socialfirstaidkit.core.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySessionSuspend(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE fingerprint = :fingerprint")
    suspend fun getMessageByFingerprint(fingerprint: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): ChatMessageEntity?

    @Query("SELECT timestamp FROM chat_messages WHERE sessionId = :sessionId AND timestamp > :timestamp ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNextTimestampAfter(sessionId: String, timestamp: Long): Long?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND status = 'PENDING_SEND' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPendingMessage(sessionId: String): ChatMessageEntity?

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearMessagesBySession(sessionId: String)

    @Transaction
    suspend fun correctPendingMessage(sessionId: String, actualContent: String, actualFingerprint: String) {
        val pending = getLastPendingMessage(sessionId) ?: return
        deleteMessageById(pending.messageId)
        val corrected = pending.copy(
            content = actualContent,
            fingerprint = actualFingerprint,
            status = "NORMAL",
            timestamp = System.currentTimeMillis()
        )
        insertMessage(corrected)
    }

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("UPDATE chat_messages SET content = :content, senderType = :senderType, senderName = :senderName WHERE messageId = :messageId")
    suspend fun updateMessage(messageId: String, content: String, senderType: String, senderName: String)

    @Query("UPDATE chat_messages SET timestamp = timestamp + :delta WHERE sessionId = :sessionId AND timestamp > :timestamp")
    suspend fun shiftMessagesAfterTimestamp(sessionId: String, timestamp: Long, delta: Long)
}
