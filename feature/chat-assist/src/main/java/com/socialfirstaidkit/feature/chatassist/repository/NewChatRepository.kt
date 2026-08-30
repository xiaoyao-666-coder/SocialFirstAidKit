package com.socialfirstaidkit.feature.chatassist.repository

import android.content.Context
import android.net.Uri
import com.socialfirstaidkit.core.data.local.dao.ContactDao
import com.socialfirstaidkit.core.data.local.dao.ChatSessionDao
import com.socialfirstaidkit.core.data.local.dao.ChatMessageDao
import com.socialfirstaidkit.core.data.local.entity.ContactEntity
import com.socialfirstaidkit.core.data.local.entity.ChatSessionEntity
import com.socialfirstaidkit.core.data.local.entity.ChatMessageEntity
import com.socialfirstaidkit.core.data.util.MessageFingerprintUtil
import com.socialfirstaidkit.service.accessibility.ChatAccessibilityService
import com.socialfirstaidkit.service.accessibility.RealtimeOverlayPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao
) {
    private val contactAvatarsDir: File by lazy {
        File(context.filesDir, "chat_contact_avatars").apply {
            if (!exists()) mkdirs()
        }
    }

    // 1. 联系人操作
    fun getAllContactsFlow(): Flow<List<ContactEntity>> = contactDao.getAllContacts()

    fun getContactFlow(contactId: String): Flow<ContactEntity?> = contactDao.observeContactById(contactId)

    suspend fun getContact(contactId: String): ContactEntity? = contactDao.getContactById(contactId)

    suspend fun getContactByName(name: String): ContactEntity? = contactDao.findByName(name)

    suspend fun saveContact(contact: ContactEntity) = contactDao.upsert(contact)

    suspend fun deleteContact(contact: ContactEntity) = withContext(Dispatchers.IO) {
        val deletedSessionIds = sessionDao.getSessionsByContactSuspend(contact.id).map { it.sessionId }
        contactDao.delete(contact)
        clearRealtimeAssistBindingIfDeleted(contact.id, deletedSessionIds)
    }

    suspend fun saveContactAvatar(contactId: String, sourceUri: Uri, oldAvatarPath: String?): String? = withContext(Dispatchers.IO) {
        try {
            val destFile = File(contactAvatarsDir, "contact_${contactId}_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            oldAvatarPath?.let { path ->
                runCatching { File(path).delete() }
            }

            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // 2. 会话操作
    fun getSessionsByContact(contactId: String): Flow<List<ChatSessionEntity>> = sessionDao.getSessionsByContact(contactId)

    suspend fun getSession(sessionId: String): ChatSessionEntity? = sessionDao.getSessionById(sessionId)

    suspend fun createSession(contactId: String, topicName: String): ChatSessionEntity = withContext(Dispatchers.IO) {
        val session = ChatSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            contactId = contactId,
            topicName = topicName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        sessionDao.insertSession(session)
        session
    }

    suspend fun updateSession(session: ChatSessionEntity) = sessionDao.updateSession(session)

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        sessionDao.deleteSession(sessionId)
        clearRealtimeAssistBindingIfDeleted(contactId = null, sessionIds = listOf(sessionId))
    }

    private fun clearRealtimeAssistBindingIfDeleted(contactId: String?, sessionIds: Collection<String>) {
        RealtimeOverlayPrefs.clearIfBoundTo(context, contactId, sessionIds)
        ChatAccessibilityService.closeFloatingBallIfBoundToDeleted(contactId, sessionIds)
    }

    // 3. 消息操作
    fun getMessagesFlow(sessionId: String): Flow<List<ChatMessageEntity>> = messageDao.getMessagesBySession(sessionId)

    suspend fun getMessagesSuspend(sessionId: String): List<ChatMessageEntity> = messageDao.getMessagesBySessionSuspend(sessionId)

    suspend fun addMessage(
        sessionId: String,
        senderType: String,
        senderName: String,
        content: String,
        status: String = "NORMAL"
    ): ChatMessageEntity = withContext(Dispatchers.IO) {
        val fingerprint = MessageFingerprintUtil.calculateFingerprint(senderType, content)
        val message = ChatMessageEntity(
            messageId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            senderType = senderType,
            senderName = senderName,
            content = content,
            fingerprint = fingerprint,
            timestamp = System.currentTimeMillis(),
            status = status
        )
        messageDao.insertMessage(message)
        sessionDao.updateSessionTimestamp(sessionId, System.currentTimeMillis())
        message
    }

    suspend fun addMessageAfter(
        targetMessageId: String,
        senderType: String,
        senderName: String,
        content: String,
        status: String = "NORMAL"
    ): ChatMessageEntity? = withContext(Dispatchers.IO) {
        val target = messageDao.getMessageById(targetMessageId) ?: return@withContext null
        val nextTimestamp = messageDao.getNextTimestampAfter(target.sessionId, target.timestamp)
        val insertTimestamp = when {
            nextTimestamp == null -> maxOf(System.currentTimeMillis(), target.timestamp + 1L)
            nextTimestamp - target.timestamp > 1L -> target.timestamp + ((nextTimestamp - target.timestamp) / 2L)
            else -> {
                messageDao.shiftMessagesAfterTimestamp(target.sessionId, target.timestamp, 1L)
                target.timestamp + 1L
            }
        }
        val fingerprint = MessageFingerprintUtil.calculateFingerprint(senderType, content)
        val message = ChatMessageEntity(
            messageId = UUID.randomUUID().toString(),
            sessionId = target.sessionId,
            senderType = senderType,
            senderName = senderName,
            content = content,
            fingerprint = fingerprint,
            timestamp = insertTimestamp,
            status = status
        )
        messageDao.insertMessage(message)
        sessionDao.updateSessionTimestamp(target.sessionId, System.currentTimeMillis())
        message
    }

    suspend fun clearMessages(sessionId: String) = messageDao.clearMessagesBySession(sessionId)

    suspend fun correctPendingMessage(sessionId: String, actualContent: String) = withContext(Dispatchers.IO) {
        val fingerprint = MessageFingerprintUtil.calculateFingerprint("USER", actualContent)
        messageDao.correctPendingMessage(sessionId, actualContent, fingerprint)
    }

    // 4. 获取用于LLM的会话上下文
    suspend fun getContextForLLM(sessionId: String, maxMessages: Int = 20): String = withContext(Dispatchers.IO) {
        val messages = messageDao.getMessagesBySessionSuspend(sessionId)
        messages.takeLast(maxMessages).joinToString("\n") { msg ->
            val sender = if (msg.senderType == "USER") "我" else msg.senderName
            "$sender: ${msg.content}"
        }
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun updateMessage(messageId: String, content: String, senderType: String, senderName: String) = withContext(Dispatchers.IO) {
        messageDao.updateMessage(messageId, content, senderType, senderName)
    }

    // 获取最近消息列表
    suspend fun getRecentMessages(sessionId: String, limit: Int = 20): List<ChatMessageEntity> {
        return messageDao.getMessagesBySessionSuspend(sessionId).takeLast(limit)
    }

    suspend fun getAllSessions(): List<ChatSessionEntity> = withContext(Dispatchers.IO) {
        sessionDao.getAllSessionsSuspend()
    }
}
