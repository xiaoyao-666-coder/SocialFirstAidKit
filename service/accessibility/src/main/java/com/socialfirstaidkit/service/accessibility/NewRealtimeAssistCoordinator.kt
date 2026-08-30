package com.socialfirstaidkit.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Base64
import android.view.Display
import android.widget.Toast
import com.socialfirstaidkit.core.data.local.dao.ContactDao
import com.socialfirstaidkit.core.data.local.dao.ChatSessionDao
import com.socialfirstaidkit.core.data.local.dao.ChatMessageDao
import com.socialfirstaidkit.core.data.local.entity.ContactEntity
import com.socialfirstaidkit.core.data.local.entity.ChatSessionEntity
import com.socialfirstaidkit.core.data.local.entity.ChatMessageEntity
import com.socialfirstaidkit.core.data.ocr.VivoOcrProcessor
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionRepository
import com.socialfirstaidkit.core.llm.api.MessageAssistClient
import com.socialfirstaidkit.core.llm.api.ReplyStyle
import com.socialfirstaidkit.core.llm.api.SenderType as AnalysisSenderType
import com.socialfirstaidkit.core.llm.impl.MimoProvider
import com.socialfirstaidkit.core.llm.impl.VivoOnDeviceProvider
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import com.socialfirstaidkit.core.llm.routing.AiModelRegistry
import com.socialfirstaidkit.core.llm.routing.AiProviderId
import com.socialfirstaidkit.core.llm.routing.AiRouteResolver
import com.socialfirstaidkit.core.llm.routing.AiRouteStore
import com.socialfirstaidkit.core.llm.model.ChatMessage
import com.socialfirstaidkit.core.llm.model.ChatSession
import com.socialfirstaidkit.core.llm.model.SenderType
import com.socialfirstaidkit.core.llm.model.LlmMessage
import com.socialfirstaidkit.core.llm.model.LlmRequest
import com.socialfirstaidkit.core.llm.model.LlmResponse
import com.socialfirstaidkit.core.llm.model.LlmRole
import com.socialfirstaidkit.service.accessibility.overlay.NewFloatingBall
import com.socialfirstaidkit.core.data.util.MessageFingerprintUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class NewRealtimeAssistCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val messageAssistClient: MessageAssistClient,
    private val mimoProvider: MimoProvider,
    private val vivoOnDeviceProvider: VivoOnDeviceProvider,
    private val vivoOcrProcessor: VivoOcrProcessor,
    private val aiRouteStore: AiRouteStore,
    private val aiRouteResolver: AiRouteResolver,
    private val expressionRepository: PersonalExpressionRepository
) {
    companion object {
        private const val TAG = "NewRealtimeAssist"
        private const val DEFAULT_RELATION_ID = "friend"
        private const val DEFAULT_STYLE_ID = "default"
        private const val UNKNOWN_CONTACT_NAME = "\u672a\u77e5\u8054\u7cfb\u4eba"
        private const val DEFAULT_CHAT_CONTACT_NAME = "\u804a\u5929\u597d\u53cb"
        private const val OFF_CHAT_KEEP_TOUCHABLE_MS = 1_000L
        private const val CONTENT_CHANGED_CHECK_THROTTLE_MS = 300L
        private const val MIMO_SCREENSHOT_MAX_EDGE = 2200
        private const val MIMO_SCREENSHOT_JPEG_QUALITY = 96
        private val MIMO_SCREENSHOT_OCR_PROMPT = """
You are a chat screenshot recognition expert.
Read the real chat contact name from the top bar/title if it is visible.
Extract only real visible chat message bodies from the screenshot.
Preserve the original text exactly, including Chinese, English, numbers, abbreviations, and mixed-language content. Do not translate, rewrite, summarize, normalize casing, or guess missing words. Do not output emoji or emoji-only messages.
Determine senderType by bubble position: right-side bubbles are USER, left-side bubbles are CONTACT.
Create a concise chat topicName from the visible conversation content, preferably 2 to 10 Chinese characters. Do not use the contact name, "截图识别", or "默认会话话题" as the topic.
Ignore app UI, titles, session cards, status bars, timestamps, input bars, buttons, image cards, emoji-only placeholders, quoted/forwarded cards, and non-chat text. Do not discard English text when it is inside a real chat bubble.
A quoted/replied preview inside a bubble is not a new message. It may be smaller, grayer, boxed, or placed above the main bubble text.
If one bubble contains both a quoted preview and new text, output only the new text.
Never output quote fragments like "name: old message" or "name：old message" as messages. When unsure, skip the possible quote.
Return strict JSON only, with no markdown and no explanation:
{"contactName":"","topicName":"","messages":[{"senderType":"USER|CONTACT|UNKNOWN","content":"message text"}]}
        """.trimIndent()
        private val VIVO_ON_DEVICE_SCREENSHOT_OCR_PROMPT = """
请识别这张聊天截图，只输出严格 JSON。
规则：
1. 只提取真实聊天气泡里的文字，忽略标题、时间、输入框、按钮、状态栏。
2. 右侧或绿色气泡是 USER，表示“我”；左侧或浅色/深色气泡是 CONTACT，表示“对方”。
3. 不确定左右时 senderType 写 UNKNOWN，不要全部猜成同一个人。
4. 不要改写聊天内容，不要总结；中文、英文、数字、缩写、中英混排都按截图原文保留，不翻译、不补全、不统一大小写；不要输出 emoji 或纯 emoji 消息。
5. 引用/回复预览不是新消息：气泡内较小、较浅、被框住或位于正文上方的旧消息引用必须忽略。
6. 如果同一气泡同时有引用预览和新正文，只输出新正文。
7. 不要把“姓名：旧消息”这类引用片段放进 messages；不确定时宁可跳过。
格式：
{"contactName":"","topicName":"","messages":[{"senderType":"USER|CONTACT|UNKNOWN","content":"原文"}]}
        """.trimIndent()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var floatingBall: NewFloatingBall? = null
    private var overlayService: AccessibilityService? = null
    private var pendingHideJob: Job? = null
    private var lastOverlayAccessibilityClickAt = 0L
    private var lastOffChatKeepTouchableAt = 0L
    private var lastContentChangedCheckAt = 0L
    private var contentChangedCheckJob: Job? = null
    private var lastVisibleContactName: String? = null
    private var lastVisibleAt = 0L
    // 内存缓存当前处于聊天辅助绑定中的会话 ID
    @Volatile
    private var activeSessionId: String? = null
    @Volatile
    private var activeContactId: String? = null
    @Volatile
    private var activeSessionIsExplicit: Boolean = false

    // 临时微调意图指令缓存
    private var tuningText: String? = null

    // 用于缓存复制的 pre-sent 建议，在无障碍监测到发送后校准
    private var lastCopiedSuggestion: String? = null

    // 开启聊天辅助
    fun showFloatingBallNow(
        overlayContext: Context = context,
        boundContactId: String? = RealtimeOverlayPrefs.getBoundContactId(context),
        boundSessionId: String? = RealtimeOverlayPrefs.getBoundSessionId(context)
    ): Boolean {
        activeContactId = boundContactId
        activeSessionId = boundSessionId
        activeSessionIsExplicit = !boundSessionId.isNullOrBlank()
        overlayService = overlayContext as? AccessibilityService ?: overlayService
        pendingHideJob?.cancel()
        pendingHideJob = null

        val ball = ensureFloatingBall(overlayContext)
        val canShowOverlay = ball.hasOverlayPermission()
        Log.d(TAG, "showFloatingBallNow contactId=$boundContactId sessionId=$boundSessionId permission=$canShowOverlay")
        return if (canShowOverlay) {
            ball.show()
            ball.isVisible()
        } else {
            Log.w(TAG, "No overlay permission to display floating ball immediately")
            false
        }
    }

    fun isFloatingBallVisible(): Boolean {
        return floatingBall?.isVisible() == true
    }

    fun onChatVisible(contactName: String, overlayContext: Context = context) {
        overlayService = overlayContext as? AccessibilityService ?: overlayService
        val now = System.currentTimeMillis()
        val sameContact = lastVisibleContactName == contactName
        val deltaMs = now - lastVisibleAt
        Log.d(
            TAG,
            "onChatVisible contact=$contactName overlayContext=${overlayContext::class.java.simpleName} sameContact=$sameContact deltaMs=$deltaMs"
        )
        lastVisibleContactName = contactName
        lastVisibleAt = now
        pendingHideJob?.cancel()
        pendingHideJob = null

        if (floatingBall == null) {
            floatingBall = NewFloatingBall(
                context = overlayContext,
                onPrimaryActionClick = { tune -> handleCaptureAndGenerate(tune) },
                onManualScreenshotClick = { handleManualScreenshot() },
                onCloseClick = { closeFloatingBall() },
                onTuneConfirm = { text ->
                    tuningText = text
                    Toast.makeText(context, "已暂存微调指令: $text", Toast.LENGTH_SHORT).show()
                },
                onSuggestionCopied = { text -> handleSuggestionCopied(text) }
            )
        }

        val canShowOverlay = floatingBall?.hasOverlayPermission() == true
        Log.d(TAG, "floating ball permission=$canShowOverlay")
        if (canShowOverlay) {
            floatingBall?.show()
            floatingBall?.onChatVisiblePing()
        } else {
            Log.w(TAG, "No overlay permission to display floating ball. context=${overlayContext::class.java.name}")
        }
    }

    // 退出聊天界面时，延迟隐藏悬浮球
    private fun ensureFloatingBall(overlayContext: Context): NewFloatingBall {
        return floatingBall ?: NewFloatingBall(
            context = overlayContext,
            onPrimaryActionClick = { tune -> handleCaptureAndGenerate(tune) },
            onManualScreenshotClick = { handleManualScreenshot() },
            onCloseClick = { closeFloatingBall() },
            onTuneConfirm = { text ->
                tuningText = text
                Toast.makeText(context, "\u5df2\u6682\u5b58\u5fae\u8c03\u6307\u4ee4: $text", Toast.LENGTH_SHORT).show()
            },
            onSuggestionCopied = { text -> handleSuggestionCopied(text) }
        ).also { floatingBall = it }
    }

    fun onFloatingBallAccessibilityClick() {
        val now = System.currentTimeMillis()
        if (now - lastOverlayAccessibilityClickAt < 700L) {
            Log.d(TAG, "ignore duplicate overlay accessibility click")
            return
        }
        lastOverlayAccessibilityClickAt = now
        Log.d(TAG, "floating ball accessibility click fallback")
        floatingBall?.triggerPrimaryActionFromAccessibility()
    }

    fun closeIfBoundToDeleted(contactId: String? = null, sessionIds: Collection<String> = emptyList()): Boolean {
        val shouldClose = (!contactId.isNullOrBlank() && activeContactId == contactId) ||
            (!activeSessionId.isNullOrBlank() && activeSessionId in sessionIds) ||
            RealtimeOverlayPrefs.clearIfBoundTo(context, contactId, sessionIds)
        if (shouldClose) {
            closeFloatingBall(showToast = false)
        }
        return shouldClose
    }

    private fun closeFloatingBall(showToast: Boolean = true) {
        RealtimeOverlayPrefs.setEnabled(context, false)
        activeContactId = null
        activeSessionId = null
        activeSessionIsExplicit = false
        pendingHideJob?.cancel()
        pendingHideJob = null
        floatingBall?.hide()
        floatingBall = null
        if (showToast) {
            Toast.makeText(context, "\u5df2\u5173\u95ed\u804a\u5929\u5b9e\u65f6\u8f85\u52a9\u60ac\u6d6e\u7403", Toast.LENGTH_SHORT).show()
        }
    }

    fun onChatNoLongerVisible() {
        if (RealtimeOverlayPrefs.isManualStartRequested(context)) {
            val now = System.currentTimeMillis()
            if (now - lastOffChatKeepTouchableAt >= OFF_CHAT_KEEP_TOUCHABLE_MS) {
                lastOffChatKeepTouchableAt = now
                Log.d(TAG, "manual floating ball is active; keep overlay visible outside chat")
                floatingBall?.keepTouchableOnScreen()
            }
            return
        }
        pendingHideJob?.cancel()
        pendingHideJob = scope.launch {
            delay(200)
            floatingBall?.hide()
            floatingBall = null
        }
    }

    // 聊天内容改变监听 (双轨同步数据校准)
    fun onChatContentChanged() {
        if (lastCopiedSuggestion == null || activeSessionId == null) return
        val now = System.currentTimeMillis()
        val waitMs = CONTENT_CHANGED_CHECK_THROTTLE_MS - (now - lastContentChangedCheckAt)
        if (waitMs > 0) {
            if (contentChangedCheckJob?.isActive != true) {
                contentChangedCheckJob = scope.launch {
                    delay(waitMs)
                    lastContentChangedCheckAt = System.currentTimeMillis()
                    processChatContentChanged()
                }
            }
            return
        }
        lastContentChangedCheckAt = now
        processChatContentChanged()
    }

    private fun processChatContentChanged() {
        val copied = lastCopiedSuggestion ?: return
        val currentSid = activeSessionId ?: return
        val info = ChatAccessibilityService.getVisibleMessagesAndContact() ?: return
        val myLastMessage = info.second.lastOrNull { it.senderType == SenderType.USER }
        if (myLastMessage != null) {
            val screenText = myLastMessage.content.trim()
            // 根据已复制建议校准最新的己方消息。
            if (screenText.contains(copied.take(5)) || copied.contains(screenText.take(5))) {
                scope.launch(Dispatchers.IO) {
                    val fp = MessageFingerprintUtil.calculateFingerprint("USER", screenText, "TEXT")
                    messageDao.correctPendingMessage(currentSid, screenText, fp)
                    // 同时更新对应的会话更新时间，以便于列表排序
                    sessionDao.updateSessionTimestamp(currentSid, System.currentTimeMillis())
                }
                lastCopiedSuggestion = null // 校准完毕，重置
            }
        }
    }

    private suspend fun ensureBoundAndSynced(): BoundSessionContext? {
        val info = ChatAccessibilityService.getVisibleMessagesAndContact() ?: return null
        var chatContactName = normalizeContactName(info.first)
        val visibleMsgs = info.second
        if (chatContactName == UNKNOWN_CONTACT_NAME && visibleMsgs.isEmpty()) {
            Log.w(TAG, "Skip binding empty unknown chat snapshot")
            return null
        }
        if (chatContactName == UNKNOWN_CONTACT_NAME) {
            chatContactName = DEFAULT_CHAT_CONTACT_NAME
        }
        Log.d(TAG, "visible chat messages=${visibleMsgs.size}")

        val contact = resolveBoundContact() ?: findOrCreateContact(chatContactName)
        val session = resolveBoundSession(contact, chatContactName)
        activeSessionId = session.sessionId
        activeContactId = contact.id
        RealtimeOverlayPrefs.setBoundContext(context, contact.id, session.sessionId)

        withContext(Dispatchers.IO) {
            val localMsgs = messageDao.getMessagesBySessionSuspend(session.sessionId)
            val existingFingerprints = localMsgs.map { it.fingerprint }.toMutableSet()
            visibleMsgs.forEach { msg ->
                val content = msg.content.trim()
                if (content.isBlank()) return@forEach
                val senderType = if (msg.senderType == SenderType.USER) "USER" else "CONTACT"
                val senderName = if (senderType == "USER") "\u6211" else chatContactName
                val fingerprint = MessageFingerprintUtil.calculateFingerprint(senderType, content, "TEXT")
                if (fingerprint !in existingFingerprints) {
                    val insertedId = messageDao.insertMessage(
                        ChatMessageEntity(
                            messageId = UUID.randomUUID().toString(),
                            sessionId = session.sessionId,
                            senderType = senderType,
                            senderName = senderName,
                            content = content,
                            messageType = "TEXT",
                            fingerprint = fingerprint,
                            timestamp = System.currentTimeMillis(),
                            status = "NORMAL"
                        )
                    )
                    if (insertedId != -1L) existingFingerprints += fingerprint
                }
            }
            sessionDao.updateSessionTimestamp(session.sessionId, System.currentTimeMillis())
        }

        return BoundSessionContext(contactName = contact.displayName, contactId = contact.id, sessionId = session.sessionId)
    }

    private suspend fun ensureBoundSessionForScreenshot(explicitSessionId: String?): BoundSessionContext {
        val requestedSessionId = explicitSessionId?.takeIf { it.isNotBlank() }
        val requestedSession = withContext(Dispatchers.IO) {
            requestedSessionId?.let { sessionDao.getSessionById(it) }
        }
        if (requestedSession != null) {
            val requestedContact = withContext(Dispatchers.IO) {
                contactDao.getContactById(requestedSession.contactId)
            }
            if (requestedContact != null) {
                activeSessionId = requestedSession.sessionId
                activeContactId = requestedContact.id
                RealtimeOverlayPrefs.setBoundContext(
                    context,
                    requestedContact.id,
                    requestedSession.sessionId.takeIf { activeSessionIsExplicit }
                )
                return BoundSessionContext(
                    contactName = requestedContact.displayName,
                    contactId = requestedContact.id,
                    sessionId = requestedSession.sessionId,
                    isExplicitSession = true
                )
            }
        }

        val contactName = normalizeContactName(lastVisibleContactName ?: DEFAULT_CHAT_CONTACT_NAME)
            .takeUnless { it == UNKNOWN_CONTACT_NAME }
            ?: DEFAULT_CHAT_CONTACT_NAME
        val contact = resolveScreenshotContact(contactName)
        activeContactId = contact.id
        activeSessionId = null
        activeSessionIsExplicit = false
        RealtimeOverlayPrefs.setBoundContext(context, contact.id, null)
        return BoundSessionContext(
            contactName = contact.displayName,
            contactId = contact.id,
            sessionId = "",
            isExplicitSession = false
        )
    }

    private suspend fun resolveScreenshotContact(fallbackName: String): ContactEntity {
        val boundContactId = activeContactId ?: RealtimeOverlayPrefs.getBoundContactId(context)
        val boundContact = withContext(Dispatchers.IO) {
            boundContactId?.let { contactDao.getContactById(it) }
        }
        if (boundContact != null) return boundContact
        return findOrCreateContact(fallbackName)
    }

    @SuppressLint("NewApi")
    private fun handleManualScreenshot() {
        val service = overlayService
        if (service == null) {
            floatingBall?.showStatus("\u8bf7\u5148\u91cd\u65b0\u5f00\u542f\u65e0\u969c\u788d\u670d\u52a1")
            Toast.makeText(context, "\u672a\u8fde\u63a5\u65e0\u969c\u788d\u670d\u52a1\uff0c\u6682\u65f6\u65e0\u6cd5\u622a\u5c4f", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            floatingBall?.showStatus("\u5f53\u524d\u7cfb\u7edf\u6682\u4e0d\u652f\u6301\u65e0\u969c\u788d\u622a\u5c4f")
            return
        }

        floatingBall?.showLoading("\u6b63\u5728\u622a\u5c4f\u8bc6\u522b")
        val mainExecutor = java.util.concurrent.Executor { command ->
            Handler(Looper.getMainLooper()).post(command)
        }
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                    val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()
                    if (bitmap == null) {
                        floatingBall?.showStatus("\u622a\u5c4f\u5931\u8d25\uff0c\u8bf7\u518d\u8bd5\u4e00\u6b21")
                        return
                    }
                    scope.launch {
                        try {
                            val result = importScreenshotMessages(bitmap)
                            val renamePromptShown = maybeOfferRenameBoundContext(
                                result.boundContext,
                                result.recognizedContactName,
                                result.importedCount
                            )
                            if (!renamePromptShown) {
                                if (result.importedCount > 0) {
                                    floatingBall?.showStatus("\u5df2\u6dfb\u52a0 ${result.importedCount} \u6761\u804a\u5929\u5185\u5bb9")
                                } else {
                                    floatingBall?.showStatus("\u672a\u4ece\u622a\u56fe\u4e2d\u8bc6\u522b\u5230\u65b0\u6d88\u606f")
                                }
                            }
                        } catch (error: Exception) {
                            Log.e(TAG, "manual screenshot OCR failed", error)
                            floatingBall?.showStatus("\u622a\u56fe\u8bc6\u522b\u5931\u8d25")
                            Toast.makeText(context, "\u622a\u56fe\u8bc6\u522b\u5931\u8d25: ${error.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot failed errorCode=$errorCode")
                    floatingBall?.showStatus("\u622a\u5c4f\u5931\u8d25\uff0c\u8bf7\u518d\u8bd5\u4e00\u6b21")
                }
            }
        )
    }

    private suspend fun importScreenshotMessages(bitmap: Bitmap): ScreenshotImportResult {
        val explicitSessionId = if (activeSessionIsExplicit) {
            activeSessionId ?: RealtimeOverlayPrefs.getBoundSessionId(context)
        } else {
            null
        }
        val boundContext = ensureBoundSessionForScreenshot(explicitSessionId)
        val recognition = recognizeScreenshotMessages(bitmap, boundContext)
        if (recognition.messages.isEmpty()) {
            return ScreenshotImportResult(
                importedCount = 0,
                boundContext = boundContext,
                recognizedContactName = recognition.contactName,
                recognizedTopicName = recognition.topicName,
                recognizedMessageCount = 0
            )
        }
        val targetContext = resolveScreenshotTargetContext(
            initialContext = boundContext,
            recognition = recognition,
            hasExplicitSession = !explicitSessionId.isNullOrBlank()
        )
        val contactSenderName = recognition.contactName
            ?.takeIf { !isPlaceholderContactName(it) }
            ?: targetContext.contactName
        val extractedMessages = recognition.messages.map { message ->
            if (message.senderType == "CONTACT") {
                message.copy(senderName = contactSenderName)
            } else {
                message
            }
        }

        return withContext(Dispatchers.IO) {
            val localMsgs = messageDao.getMessagesBySessionSuspend(targetContext.sessionId)
            val existingFingerprints = localMsgs.map { it.fingerprint }.toMutableSet()
            var importedCount = 0
            extractedMessages.forEach { message ->
                val fingerprint = MessageFingerprintUtil.calculateFingerprint(message.senderType, message.content, "TEXT")
                if (fingerprint !in existingFingerprints) {
                    val insertedId = messageDao.insertMessage(
                        ChatMessageEntity(
                            messageId = UUID.randomUUID().toString(),
                            sessionId = targetContext.sessionId,
                            senderType = message.senderType,
                            senderName = message.senderName,
                            content = message.content,
                            messageType = "TEXT",
                            fingerprint = fingerprint,
                            timestamp = System.currentTimeMillis(),
                            status = "NORMAL"
                        )
                    )
                    if (insertedId != -1L) {
                        existingFingerprints.add(fingerprint)
                        importedCount += 1
                    }
                }
            }
            if (importedCount > 0) {
                sessionDao.updateSessionTimestamp(targetContext.sessionId, System.currentTimeMillis())
            }
            applyRecognizedTopicName(targetContext, recognition.topicName)
            cleanupLegacyRecognitionSessionTitles()
            ScreenshotImportResult(
                importedCount = importedCount,
                boundContext = targetContext,
                recognizedContactName = recognition.contactName,
                recognizedTopicName = recognition.topicName,
                recognizedMessageCount = recognition.messages.size
            )
        }
    }

    private suspend fun resolveScreenshotTargetContext(
        initialContext: BoundSessionContext,
        recognition: ScreenshotRecognitionResult,
        hasExplicitSession: Boolean
    ): BoundSessionContext {
        if (hasExplicitSession) return initialContext
        if (recognition.messages.isEmpty()) return initialContext
        val topicName = recognition.topicName
            ?.takeIf { it.isNotBlank() && !isPlaceholderContactName(it) }
            ?: deriveTopicNameFromScreenshotMessages(recognition.messages)
            ?: importedSessionTopic(initialContext.contactName, System.currentTimeMillis())
        val recognitionFingerprints = recognition.messages
            .map { MessageFingerprintUtil.calculateFingerprint(it.senderType, it.content, "TEXT") }
            .toSet()

        return withContext(Dispatchers.IO) {
            val sessions = sessionDao.getSessionsByContactSuspend(initialContext.contactId)
            val matchedSession = sessions.firstOrNull { session ->
                if (recognitionFingerprints.isEmpty()) return@firstOrNull false
                val existingFingerprints = messageDao.getMessagesBySessionSuspend(session.sessionId)
                    .map { it.fingerprint }
                    .toSet()
                isStrongDuplicateScreenshot(
                    recognitionFingerprints = recognitionFingerprints,
                    existingFingerprints = existingFingerprints
                )
            }
            val initialSession = sessionDao.getSessionById(initialContext.sessionId)
            val initialMessages = messageDao.getMessagesBySessionSuspend(initialContext.sessionId)
            val uniqueTopicName = uniqueScreenshotTopicName(topicName, sessions)
            val targetSession = when {
                matchedSession != null -> matchedSession
                initialSession != null &&
                    initialMessages.isEmpty() &&
                    shouldRenameSessionTopic(initialSession.topicName, initialContext.contactName) -> {
                    initialSession.copy(topicName = uniqueTopicName, updatedAt = System.currentTimeMillis())
                        .also { sessionDao.updateSession(it) }
                }
                else -> {
                    ChatSessionEntity(
                        sessionId = UUID.randomUUID().toString(),
                        contactId = initialContext.contactId,
                        topicName = uniqueTopicName,
                        preferredRelationId = null,
                        preferredStyleId = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    ).also { sessionDao.insertSession(it) }
                }
            }
            activeSessionId = null
            activeSessionIsExplicit = false
            RealtimeOverlayPrefs.setBoundContext(context, initialContext.contactId, null)
            initialContext.copy(sessionId = targetSession.sessionId, isExplicitSession = false)
        }
    }

    private fun isStrongDuplicateScreenshot(
        recognitionFingerprints: Set<String>,
        existingFingerprints: Set<String>
    ): Boolean {
        if (recognitionFingerprints.size < 2 || existingFingerprints.isEmpty()) return false
        val overlap = recognitionFingerprints.count { it in existingFingerprints }
        return overlap == recognitionFingerprints.size
    }

    private fun uniqueScreenshotTopicName(
        topicName: String,
        sessions: List<ChatSessionEntity>
    ): String {
        val base = topicName.trim().take(10).ifBlank { "\u9ed8\u8ba4\u4f1a\u8bdd\u8bdd\u9898" }
        val existingKeys = sessions.map { canonicalNameKey(it.topicName) }.toSet()
        if (canonicalNameKey(base) !in existingKeys) return base

        val suffix = java.text.SimpleDateFormat("HH:mm", Locale.CHINA).format(java.util.Date())
        val timed = "$base $suffix"
        if (canonicalNameKey(timed) !in existingKeys) return timed

        var index = 2
        while (true) {
            val candidate = "$base $index"
            if (canonicalNameKey(candidate) !in existingKeys) return candidate
            index += 1
        }
    }

    private fun deriveTopicNameFromScreenshotMessages(messages: List<ScreenshotMessage>): String? {
        val candidate = messages
            .firstOrNull { it.senderType == "CONTACT" }
            ?: messages.firstOrNull()
            ?: return null
        val compact = candidate.content
            .replace(Regex("\\s+"), "")
            .replace(Regex("^[\\p{IsHan}A-Za-z0-9_\\-]{1,12}[:：]"), "")
            .trim()
        val withoutPunctuationTail = compact
            .split('\uFF0C', '\u3002', '\uFF01', '\uFF1F', ',', '.', '!', '?')
            .firstOrNull()
            ?.trim()
            .orEmpty()
        val source = withoutPunctuationTail.ifBlank { compact }
        return source
            .take(10)
            .takeIf { it.length >= 2 && !isPlaceholderContactName(it) }
    }

    private suspend fun recognizeScreenshotMessages(
        bitmap: Bitmap,
        boundContext: BoundSessionContext
    ): ScreenshotRecognitionResult {
        val route = aiRouteStore.getRoute(AiFunctionSlot.CHAT_SCREENSHOT_OCR)
        Log.d(
            TAG,
            "recognize screenshot via route provider=${route.provider} model=${route.modelName} modelId=${route.modelId}"
        )
        return try {
            when {
                route.provider == AiProviderId.VIVO &&
                    route.modelId == AiModelRegistry.VIVO_OCR_ROTATED_ID ->
                    recognizeScreenshotMessagesWithVivoOcr(bitmap, boundContext)

                route.provider == AiProviderId.VIVO_ON_DEVICE &&
                    route.modelId == AiModelRegistry.VIVO_ON_DEVICE_3B_ID ->
                    recognizeScreenshotMessagesWithVivoOnDevice(bitmap, boundContext)

                else ->
                    recognizeScreenshotMessagesWithRoutedVisionModel(bitmap, boundContext, route.modelName)
            }
        } catch (e: Exception) {
            if (route.provider == AiProviderId.MIMO && route.modelId == AiModelRegistry.MIMO_VISION_ID) {
                throw e
            }
            Log.w(TAG, "routed screenshot OCR failed, fallback to MiMo v2.5", e)
            recognizeScreenshotMessagesWithMimo(bitmap, boundContext)
        }
    }

    private suspend fun recognizeScreenshotMessagesWithRoutedVisionModel(
        bitmap: Bitmap,
        boundContext: BoundSessionContext,
        modelName: String
    ): ScreenshotRecognitionResult {
        val (provider, resolvedModelName) = aiRouteResolver.resolve(AiFunctionSlot.CHAT_SCREENSHOT_OCR)
        val model = modelName.ifBlank { resolvedModelName }
        val encodedImage = withContext(Dispatchers.IO) { encodeScreenshotForMimo(bitmap) }
        val request = LlmRequest(
            model = model,
            messages = listOf(
                LlmMessage(
                    role = LlmRole.SYSTEM,
                    content = MIMO_SCREENSHOT_OCR_PROMPT
                ),
                LlmMessage(
                    role = LlmRole.USER,
                    content = "Recognize this chat screenshot. Return strict JSON only.",
                    imageUrl = encodedImage
                )
            ),
            maxTokens = 2048,
            temperature = 0.1f
        )

        return when (val response = provider.chat(request)) {
            is LlmResponse.Complete -> parseMimoScreenshotMessages(response.content, boundContext)
            is LlmResponse.Error -> throw IllegalStateException(
                "Screenshot OCR failed via ${provider.providerName}/${model}: ${response.message}"
            )
            is LlmResponse.StreamChunk -> ScreenshotRecognitionResult(contactName = null, topicName = null, messages = emptyList())
        }
    }

    private suspend fun recognizeScreenshotMessagesWithMimo(
        bitmap: Bitmap,
        boundContext: BoundSessionContext
    ): ScreenshotRecognitionResult {
        val encodedImage = withContext(Dispatchers.IO) { encodeScreenshotForMimo(bitmap) }
        val request = LlmRequest(
            model = "mimo-v2.5",
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = MIMO_SCREENSHOT_OCR_PROMPT),
                LlmMessage(
                    role = LlmRole.USER,
                    content = "Recognize this chat screenshot. Return strict JSON only.",
                    imageUrl = encodedImage
                )
            ),
            maxTokens = 2048,
            temperature = 0.1f
        )

        return when (val response = mimoProvider.chat(request)) {
            is LlmResponse.Complete -> parseMimoScreenshotMessages(response.content, boundContext)
            is LlmResponse.Error -> throw IllegalStateException("MiMo v2.5 screenshot OCR failed: ${response.message}")
            is LlmResponse.StreamChunk -> ScreenshotRecognitionResult(contactName = null, topicName = null, messages = emptyList())
        }
    }

    private suspend fun recognizeScreenshotMessagesWithVivoOnDevice(
        bitmap: Bitmap,
        boundContext: BoundSessionContext
    ): ScreenshotRecognitionResult {
        val result = vivoOnDeviceProvider.describeBitmap(bitmap, VIVO_ON_DEVICE_SCREENSHOT_OCR_PROMPT)
        if (!result.success) {
            throw IllegalStateException("vivo on-device screenshot OCR failed: ${result.errorCode} ${result.content}")
        }
        val parsed = parseMimoScreenshotMessages(result.content, boundContext)
        if (parsed.looksSuspiciousForChatScreenshot()) {
            Log.w(
                TAG,
                "vivo on-device screenshot OCR looked suspicious, fallback to MiMo. " +
                    "messages=${parsed.messages.size} senders=${parsed.messages.map { it.senderType }.toSet()}"
            )
            return recognizeScreenshotMessagesWithMimo(bitmap, boundContext)
        }
        return parsed
    }

    private suspend fun recognizeScreenshotMessagesWithVivoOcr(
        bitmap: Bitmap,
        boundContext: BoundSessionContext
    ): ScreenshotRecognitionResult {
        val ocrResult = vivoOcrProcessor.recognizeText(bitmap)
        val analysis = messageAssistClient.analyzeScreenshot(ocrResult.text)
        if (!analysis.success) {
            throw IllegalStateException(analysis.errorMessage ?: "vivo OCR dialogue analysis failed")
        }
        return ScreenshotRecognitionResult(
            contactName = normalizeRecognizedContactName(analysis.contactName),
            topicName = normalizeRecognizedTopicName(analysis.conversationSummary, analysis.contactName),
            messages = analysis.messages.mapNotNull { message ->
                val content = sanitizeRecognizedScreenshotContent(
                    message.content,
                    knownContactName = analysis.contactName
                )
                if (content == null) {
                    null
                } else {
                    ScreenshotMessage(
                        senderType = when (message.senderType) {
                            AnalysisSenderType.USER -> "USER"
                            AnalysisSenderType.CONTACT -> "CONTACT"
                        },
                        senderName = message.senderName,
                        content = content
                    )
                }
            }
        )
    }

    private fun parseMimoScreenshotMessages(
        responseContent: String,
        boundContext: BoundSessionContext
    ): ScreenshotRecognitionResult {
        val root = JSONObject(extractJsonObject(responseContent))
        val contactName = normalizeRecognizedContactName(root.optString("contactName"))
        val topicName = normalizeRecognizedTopicName(root.optString("topicName"), contactName)
        val knownContactName = contactName ?: boundContext.contactName
        val messages = root.optJSONArray("messages")
            ?: return ScreenshotRecognitionResult(contactName = contactName, topicName = topicName, messages = emptyList())
        val parsed = mutableListOf<ScreenshotMessage>()
        for (index in 0 until messages.length()) {
            val item = messages.optJSONObject(index) ?: continue
            val content = sanitizeRecognizedScreenshotContent(
                item.optString("content"),
                knownContactName = knownContactName
            ) ?: continue
            val senderType = when (item.optString("senderType").uppercase()) {
                "USER" -> "USER"
                "CONTACT" -> "CONTACT"
                else -> "CONTACT"
            }
            parsed += ScreenshotMessage(
                senderType = senderType,
                senderName = if (senderType == "USER") "\u6211" else boundContext.contactName,
                content = content
            )
        }
        return ScreenshotRecognitionResult(contactName = contactName, topicName = topicName, messages = parsed)
    }

    private fun ScreenshotRecognitionResult.looksSuspiciousForChatScreenshot(): Boolean {
        if (messages.size < 2) return false
        val knownSenders = messages
            .map { it.senderType.uppercase(Locale.ROOT) }
            .filter { it == "USER" || it == "CONTACT" }
            .toSet()
        return knownSenders.size < 2
    }

    private suspend fun maybeOfferRenameBoundContext(
        boundContext: BoundSessionContext,
        recognizedContactName: String?,
        importedCount: Int
    ): Boolean {
        val suggestedName = recognizedContactName
            ?.takeIf { !isPlaceholderContactName(it) }
            ?: return false
        val currentContact = withContext(Dispatchers.IO) { contactDao.getContactById(boundContext.contactId) }
            ?: return false
        val currentName = currentContact.displayName.trim()
        val contactNeedsRename = canonicalNameKey(currentName) != canonicalNameKey(suggestedName)
        if (!contactNeedsRename) return false
        val completionMessage = importCompletionMessage(importedCount)

        withContext(Dispatchers.Main) {
            floatingBall?.showRenamePrompt(
                currentName = currentName,
                suggestedName = suggestedName,
                completionMessage = completionMessage,
                onConfirm = {
                    scope.launch {
                        renameBoundContext(boundContext, suggestedName)
                    }
                }
            )
        }
        return true
    }

    private fun importCompletionMessage(importedCount: Int): String {
        return if (importedCount > 0) {
            "\u5df2\u6dfb\u52a0 $importedCount \u6761\u804a\u5929\u5185\u5bb9"
        } else {
            "\u672a\u4ece\u622a\u56fe\u4e2d\u8bc6\u522b\u5230\u65b0\u6d88\u606f"
        }
    }

    private suspend fun renameBoundContext(
        boundContext: BoundSessionContext,
        suggestedName: String
    ) {
        val normalizedName = normalizeContactName(suggestedName).takeIf { !isPlaceholderContactName(it) } ?: return
        withContext(Dispatchers.IO) {
            val contact = contactDao.getContactById(boundContext.contactId)
            if (contact != null) {
                contactDao.update(
                    contact.copy(
                        displayName = normalizedName,
                        lastInteraction = System.currentTimeMillis()
                    )
                )
            }
        }
        activeContactId = boundContext.contactId
        if (boundContext.isExplicitSession) {
            activeSessionId = boundContext.sessionId
            activeSessionIsExplicit = true
            RealtimeOverlayPrefs.setBoundContext(context, boundContext.contactId, boundContext.sessionId)
        } else {
            activeSessionId = null
            activeSessionIsExplicit = false
            RealtimeOverlayPrefs.setBoundContext(context, boundContext.contactId, null)
        }
    }

    private fun shouldRenameSessionTopic(topicName: String, previousContactName: String): Boolean {
        val normalized = topicName.trim()
        return normalized.isBlank() ||
            normalized == previousContactName ||
            isPlaceholderContactName(normalized) ||
            containsPlaceholderContactName(normalized)
    }

    private suspend fun applyRecognizedTopicName(
        boundContext: BoundSessionContext,
        recognizedTopicName: String?
    ) {
        val topicName = recognizedTopicName
            ?.takeIf { it.isNotBlank() && !isPlaceholderContactName(it) }
            ?: return
        val session = sessionDao.getSessionById(boundContext.sessionId) ?: return
        if (!shouldRenameSessionTopic(session.topicName, boundContext.contactName)) return
        if (session.topicName.trim() == topicName) return
        sessionDao.updateSession(
            session.copy(
                topicName = topicName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun encodeScreenshotForMimo(bitmap: Bitmap): String {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        val uploadBitmap = if (maxEdge > MIMO_SCREENSHOT_MAX_EDGE) {
            val scale = MIMO_SCREENSHOT_MAX_EDGE.toFloat() / maxEdge.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        return try {
            val outputStream = ByteArrayOutputStream()
            uploadBitmap.compress(Bitmap.CompressFormat.JPEG, MIMO_SCREENSHOT_JPEG_QUALITY, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (uploadBitmap !== bitmap) uploadBitmap.recycle()
        }
    }

    private fun extractJsonObject(raw: String): String {
        val withoutFence = raw
            .replace("```json", "```")
            .substringAfter("```", raw)
            .substringBeforeLast("```", raw)
            .trim()
        val start = withoutFence.indexOf('{')
        val end = withoutFence.lastIndexOf('}')
        return if (start >= 0 && end > start) withoutFence.substring(start, end + 1) else withoutFence
    }

    private fun shouldIgnoreScreenshotContent(content: String): Boolean {
        val trimmed = content.trim()
        val normalized = trimmed.replace(Regex("\\s+"), "")
        if (normalized.isBlank()) return true
        if (normalized.matches(Regex("^\\d{1,2}:\\d{2}$"))) return true
        if (normalized.matches(Regex("^(昨天|今天|上午|下午|晚上)?\\d{1,2}:\\d{2}$"))) return true
        if (normalized.matches(Regex("^(Contact|Me|USER|CONTACT|UNKNOWN)[·.。:：]?\\d{1,2}:\\d{2}$", RegexOption.IGNORE_CASE))) return true
        if (normalized.contains(Regex("(4G|5G|Wi[-]?Fi|KB/s|MB/s|电量|电池)", RegexOption.IGNORE_CASE))) return true
        if (normalized.contains("聊天") || normalized.contains("条消息")) return true
        if (normalized.contains("默认会话话题")) return true
        if (normalized in setOf("我", "你", "对方", "图片", "[图片]", "表情", "[表情]")) return true
        if (looksLikeStandaloneQuotedReplyHint(trimmed)) return true
        if (looksLikeStatusBarNoise(normalized)) return true
        return false
    }

    private fun sanitizeRecognizedScreenshotContent(
        content: String,
        knownContactName: String? = null
    ): String? {
        val lines = content
            .trim()
            .replace("\r\n", "\n")
            .lines()
            .map { stripEmojiFromRecognizedContent(it).trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val cleanedLines = if (lines.size > 1) {
            lines.filterNot(::looksLikeQuotedReplyLine)
        } else {
            lines.filterNot { looksLikeStandaloneQuotedReplyContent(it, knownContactName) }
        }
        val cleaned = cleanedLines.joinToString("\n").trim()
        return cleaned.takeIf { it.isNotBlank() && !shouldIgnoreScreenshotContent(it) }
    }

    private fun stripEmojiFromRecognizedContent(text: String): String {
        if (text.isBlank()) return text
        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (!isEmojiCodePoint(codePoint)) {
                builder.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        return builder.toString()
            .replace(Regex("[\\uFE0F\\u200D\\u20E3]"), "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF ||
            codePoint in 0x1F3FB..0x1F3FF ||
            codePoint in 0xE0020..0xE007F
    }
    private fun looksLikeQuotedReplyLine(text: String): Boolean {
        val compact = text.trim().replace(Regex("\\s+"), "")
        if (compact.isBlank() || compact.length > 120) return false
        if (compact.contains("引用消息") || compact.contains("回复消息") || compact.contains("原消息")) return true
        if (compact.startsWith("引用:") || compact.startsWith("引用：")) return true
        if (compact.startsWith("回复:") || compact.startsWith("回复：")) return true
        return compact.matches(Regex("^[\\p{IsHan}A-Za-z0-9_\\-]{1,12}[:：].{1,90}$"))
    }

    private fun looksLikeStandaloneQuotedReplyContent(text: String, knownContactName: String?): Boolean {
        val compact = text.trim().replace(Regex("\\s+"), "")
        if (compact.length !in 4..120) return false
        if (looksLikeStandaloneQuotedReplyHint(compact)) return true
        val match = Regex("^([\\p{IsHan}A-Za-z0-9_\\-]{1,12})[:：](.{2,90})$").matchEntire(compact)
            ?: return false
        val speaker = match.groupValues[1]
        val body = match.groupValues[2]
        if (body.matches(Regex("^\\d{1,2}:\\d{2}.*$"))) return false
        val speakerKey = canonicalNameKey(speaker)
        val contactKey = canonicalNameKey(knownContactName.orEmpty())
        val commonSpeaker = speakerKey in setOf("我", "你", "对方", "自己", "ta")
        val knownSpeaker = contactKey.isNotBlank() && speakerKey == contactKey
        val humanNameLike = speaker.length in 1..6 && speaker.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        return commonSpeaker || knownSpeaker || humanNameLike
    }

    private fun looksLikeStandaloneQuotedReplyHint(text: String): Boolean {
        val compact = text.trim().replace(Regex("\\s+"), "")
        if (compact.length > 120) return false
        return compact.contains("引用消息") ||
            compact.contains("回复消息") ||
            compact.startsWith("引用:") ||
            compact.startsWith("引用：") ||
            compact.startsWith("回复:") ||
            compact.startsWith("回复：")
    }

    private fun looksLikeStatusBarNoise(text: String): Boolean {
        val compact = text.trim().replace(Regex("\\s+"), "")
        val chineseCount = compact.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val asciiLetterCount = compact.count { it in 'A'..'Z' || it in 'a'..'z' }
        val digitCount = compact.count { it.isDigit() }
        val symbolCount = compact.count { it in "%/.,:：+-_•·|" }
        val lower = compact.lowercase(Locale.ROOT)
        if (Regex("(4g|5g|wifi|wi-fi|kb/s|mb/s|volte|vpn|hd|电量|电池)", RegexOption.IGNORE_CASE).containsMatchIn(compact)) return true
        if (compact.length <= 18 && chineseCount <= 2 && digitCount >= 2 && symbolCount >= 1) return true
        if (compact.length <= 14 && chineseCount == 0 && asciiLetterCount == 0 && symbolCount >= 3) return true
        if (compact.length <= 10 && lower in setOf("contact", "user", "me", "unknown")) return true
        return false
    }

    private fun normalizeRecognizedContactName(rawName: String?): String? {
        val normalized = normalizeContactName(rawName.orEmpty())
            .removePrefix("截图识别")
            .trim()
        return normalized.takeIf { it.isNotBlank() && !isPlaceholderContactName(it) }
    }

    private fun normalizeRecognizedTopicName(rawName: String?, contactName: String?): String? {
        val normalized = rawName.orEmpty()
            .replace("\u200e", "")
            .replace("\u200f", "")
            .replace("\u202a", "")
            .replace("\u202c", "")
            .removePrefix("截图识别")
            .trim()
            .replace(Regex("\\s+"), "")
        val contactNormalized = canonicalNameKey(contactName.orEmpty())
        return normalized.takeIf {
            it.isNotBlank() &&
                canonicalNameKey(it) != contactNormalized &&
                it != "默认会话话题" &&
                !isPlaceholderContactName(it)
        }
    }

    private fun canonicalNameKey(name: String): String {
        val normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
        return normalized
            .asSequence()
            .filterNot { it.isWhitespace() || Character.getType(it) == Character.FORMAT.toInt() }
            .filterNot { it in "，。、“”’「」『』（ ）【】[]《》、·:：._-" }
            .joinToString("")
            .lowercase(Locale.ROOT)
    }

    private fun isPlaceholderContactName(name: String): Boolean {
        val normalized = name.trim().replace(Regex("\\s+"), "")
        return normalized.isBlank() ||
            normalized == UNKNOWN_CONTACT_NAME ||
            normalized == DEFAULT_CHAT_CONTACT_NAME ||
            normalized == "微信" ||
            normalized == "聊天" ||
            normalized == "聊天好友" ||
            normalized == "聊天" ||
            normalized == "我" ||
            normalized.startsWith("截图识别")
    }

    private fun containsPlaceholderContactName(text: String): Boolean {
        val normalized = text.trim().replace(Regex("\\s+"), "")
        return listOf("截图识别聊天", "截图识别聊天好友", "截图识别未知联系人", "默认会话话题")
            .any { it in normalized }
    }

    private fun importedSessionTopic(contactName: String, timestamp: Long): String {
        return contactName.takeIf { it.isNotBlank() && !isPlaceholderContactName(it) } ?: "\u9ed8\u8ba4\u4f1a\u8bdd\u8bdd\u9898"
    }

    private suspend fun cleanupLegacyRecognitionSessionTitles() {
        sessionDao.getAllSessionsSuspend()
            .filter { it.topicName.trim().startsWith("截图识别") }
            .forEach { session ->
                val contactName = contactDao.getContactById(session.contactId)?.displayName.orEmpty()
                val cleanedTopic = session.topicName
                    .trim()
                    .removePrefix("截图识别")
                    .trim()
                    .replace(Regex("\\s+\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}$"), "")
                    .trim()
                    .takeIf { it.isNotBlank() && !isPlaceholderContactName(it) }
                    ?: contactName.takeIf { it.isNotBlank() && !isPlaceholderContactName(it) }
                    ?: "\u9ed8\u8ba4\u4f1a\u8bdd\u8bdd\u9898"
                sessionDao.updateSession(session.copy(topicName = cleanedTopic))
            }
    }

    private fun normalizeContactName(rawName: String): String {
        return rawName
            .replace("\u200e", "")
            .replace("\u200f", "")
            .replace("\u202a", "")
            .replace("\u202c", "")
            .trim()
            .removeSuffix("(")
            .trim()
            .let { cleaned ->
                when {
                    cleaned.isBlank() -> DEFAULT_CHAT_CONTACT_NAME
                    cleaned == UNKNOWN_CONTACT_NAME -> UNKNOWN_CONTACT_NAME
                    else -> cleaned
                }
            }
    }
    private suspend fun findOrCreateContact(displayName: String): ContactEntity = withContext(Dispatchers.IO) {
        val normalizedName = normalizeContactName(displayName).takeUnless { it == UNKNOWN_CONTACT_NAME } ?: DEFAULT_CHAT_CONTACT_NAME
        contactDao.findByName(normalizedName) ?: ContactEntity(
            id = UUID.randomUUID().toString(),
            displayName = normalizedName,
            relationType = DEFAULT_RELATION_ID,
            intimacyScore = 5,
            lastInteraction = System.currentTimeMillis(),
            defaultRelationId = DEFAULT_RELATION_ID,
            defaultStyleId = DEFAULT_STYLE_ID
        ).also { contactDao.upsert(it) }
    }

    private suspend fun getOrCreateSessionForContact(contact: ContactEntity): ChatSessionEntity = withContext(Dispatchers.IO) {
        val sessions = sessionDao.getSessionsByContactSuspend(contact.id)
        val active = activeSessionId?.let { sessionDao.getSessionById(it) }
        if (active != null && active.contactId == contact.id) {
            active
        } else {
            sessions.firstOrNull() ?: ChatSessionEntity(
                sessionId = UUID.randomUUID().toString(),
                contactId = contact.id,
                topicName = "默认会话话题",
                preferredRelationId = null,
                preferredStyleId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ).also { sessionDao.insertSession(it) }
        }
    }

    private suspend fun resolveBoundContact(): ContactEntity? = withContext(Dispatchers.IO) {
        val contactId = activeContactId ?: RealtimeOverlayPrefs.getBoundContactId(context)
        contactId?.let { contactDao.getContactById(it) }
            ?: (activeSessionId ?: RealtimeOverlayPrefs.getBoundSessionId(context))
                ?.let { sessionDao.getSessionById(it) }
                ?.let { session -> contactDao.getContactById(session.contactId) }
    }

    private suspend fun resolveBoundSession(
        contact: ContactEntity,
        chatContactName: String
    ): ChatSessionEntity {
        val requestedSessionId = activeSessionId ?: RealtimeOverlayPrefs.getBoundSessionId(context)
        val requestedSession = withContext(Dispatchers.IO) {
            requestedSessionId?.let { sessionDao.getSessionById(it) }
        }
        if (requestedSession != null && requestedSession.contactId == contact.id) {
            return requestedSession
        }

        val boundContactId = activeContactId ?: RealtimeOverlayPrefs.getBoundContactId(context)
        if (boundContactId == contact.id && requestedSessionId.isNullOrBlank()) {
            return getOrCreateSessionForContact(contact)
        }

        return getOrCreateSessionForContact(contact)
    }
    private fun handleCaptureAndGenerate(tune: String?) {
        Log.d(TAG, "floating ball primary action clicked tune=${!tune.isNullOrBlank()}")
        scope.launch {
            floatingBall?.showLoading("读取当前聊天")
            val boundContext = ensureBoundAndSynced() ?: importCurrentScreenshotForGeneration()
            if (boundContext == null) {
                floatingBall?.showStatus("未识别到当前聊天")
                Toast.makeText(context, "未检测到聊天窗口，或当前聊天内容无法识别", Toast.LENGTH_SHORT).show()
                return@launch
            }
            floatingBall?.clearNotificationPulse()
            handleGenerateReplies(tune, boundContext)
        }
    }

    // 生成回复建议。
    @SuppressLint("NewApi")
    private suspend fun importCurrentScreenshotForGeneration(): BoundSessionContext? {
        val service = overlayService ?: run {
            floatingBall?.showStatus("请先重新开启无障碍服务")
            return null
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            floatingBall?.showStatus("当前系统暂不支持无障碍截图")
            return null
        }

        floatingBall?.showLoading("正在识别当前屏幕")
        val bitmap = takeScreenshotBitmap(service) ?: run {
            floatingBall?.showStatus("截图失败，请再试一次")
            return null
        }
        return try {
            val result = importScreenshotMessages(bitmap)
            if (result.recognizedMessageCount <= 0 || result.boundContext.sessionId.isBlank()) {
                floatingBall?.showStatus("未从当前屏幕识别到聊天内容")
                null
            } else {
                maybeOfferRenameBoundContext(
                    result.boundContext,
                    result.recognizedContactName,
                    result.importedCount
                )
                result.boundContext
            }
        } catch (error: Exception) {
            Log.e(TAG, "generate fallback screenshot import failed", error)
            floatingBall?.showStatus("截图识别失败")
            null
        } finally {
            bitmap.recycle()
        }
    }

    @SuppressLint("NewApi")
    private suspend fun takeScreenshotBitmap(service: AccessibilityService): Bitmap? = suspendCancellableCoroutine { cont ->
        val mainExecutor = java.util.concurrent.Executor { command ->
            Handler(Looper.getMainLooper()).post(command)
        }
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                    val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()
                    if (cont.isActive) cont.resume(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot for generation failed errorCode=$errorCode")
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }
    private fun handleGenerateReplies(
        tune: String?,
        boundContext: BoundSessionContext? = null
    ) {
        floatingBall?.showLoading("生成回复建议")
        scope.launch {
            try {
                val effectiveTune = tune
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: tuningText?.trim()?.takeIf { it.isNotBlank() }
                val contextInfo = boundContext ?: ensureBoundAndSynced()
                if (contextInfo == null) {
                    Toast.makeText(context, "未能识别当前聊天或联系人尚未绑定", Toast.LENGTH_SHORT).show()
                    floatingBall?.showSuggestions(emptyList())
                    return@launch
                }

                activeSessionId = contextInfo.sessionId
                val contact = withContext(Dispatchers.IO) {
                    contactDao.getContactById(contextInfo.contactId)
                } ?: run {
                    floatingBall?.showSuggestions(emptyList())
                    return@launch
                }

                val session = withContext(Dispatchers.IO) {
                    sessionDao.getSessionById(contextInfo.sessionId)
                } ?: run {
                    floatingBall?.showSuggestions(emptyList())
                    return@launch
                }

                // 读取会话专属身份设定
                val preferredRelId = session.preferredRelationId ?: contact.defaultRelationId ?: DEFAULT_RELATION_ID
                val preferredStyId = session.preferredStyleId ?: contact.defaultStyleId ?: DEFAULT_STYLE_ID

                val relationLabel = resolveRelationLabel(preferredRelId)
                val styleLabel = resolveStyleLabel(preferredStyId)

                val personalPrompt = withContext(Dispatchers.IO) {
                    expressionRepository.getPromptContext(relationLabel)
                }

                val localMsgs = withContext(Dispatchers.IO) {
                    messageDao.getMessagesBySessionSuspend(session.sessionId)
                }

                // 装配 LLM 会话上下文
                val chatSession = ChatSession(
                    sessionId = session.sessionId,
                    contactName = contextInfo.contactName,
                    messages = localMsgs.map {
                        ChatMessage(
                            id = it.messageId,
                            senderType = if (it.senderType == "USER") SenderType.USER else SenderType.CONTACT,
                            senderName = it.senderName,
                            content = it.content,
                            timestamp = it.timestamp
                        )
                    }
                )

                // 异步获取 LLM 生成回复
                val result = withContext(Dispatchers.IO) {
                    messageAssistClient.generateContextualReply(
                        session = chatSession,
                        relation = relationLabel,
                        personalExpression = personalPrompt,
                        style = styleLabel,
                        additionalNotes = buildFloatingBallGenerationNotes(effectiveTune)
                    )
                }

                val rawSuggestions = listOf(result.formal, result.casual, result.humorous).filter { it.isNotBlank() }
                var suggestions = rawSuggestions
                    .map { sanitizeFloatingSuggestion(it) }
                    .filter { it.isNotBlank() }
                    .distinct()

                if (suggestions.isEmpty()) {
                    val lastContactMessage = chatSession.messages.lastOrNull { it.senderType == SenderType.CONTACT }
                    if (lastContactMessage != null) {
                        val singleResult = withContext(Dispatchers.IO) {
                            messageAssistClient.generateSingleReply(
                                message = lastContactMessage.content,
                                relation = relationLabel,
                                personalExpression = personalPrompt,
                                style = styleLabel,
                                additionalNotes = buildFloatingBallGenerationNotes(effectiveTune)
                            )
                        }
                        suggestions = listOf(singleResult.formal, singleResult.casual, singleResult.humorous)
                            .map { sanitizeFloatingSuggestion(it) }
                            .filter { it.isNotBlank() }
                            .distinct()
                    }
                }

                if (suggestions.isEmpty()) {
                    floatingBall?.showStatus("未生成可用回复建议")
                    Toast.makeText(context, "未生成合适的建议，请调整输入内容", Toast.LENGTH_SHORT).show()
                }

                floatingBall?.showSuggestions(suggestions)

            } catch (e: Exception) {
                Log.e(TAG, "生成回复失败", e)
                Toast.makeText(context, "大模型分析回复失败: ${e.message}", Toast.LENGTH_LONG).show()
                floatingBall?.showSuggestions(emptyList())
            }
        }
    }

    private fun buildFloatingBallGenerationNotes(tune: String?): String {
        val seed = System.currentTimeMillis() % 100_000
        return buildString {
            appendLine("这是悬浮球快捷生成，请每次都生成与上一次不同的自然聊天回复。")
            appendLine("本次生成批次：$seed。这个批次号只用于增加变化，禁止在回复中提到。")
            if (!tune.isNullOrBlank()) {
                appendLine("用户微调要求：$tune")
                appendLine("必须把上述微调要求体现在措辞、语气或长短上，但不要解释“已根据要求微调”，也不要输出括号说明。")
            }
            appendLine("最终回复只能是可直接发送给对方的话，不要输出分析、标签、括号注释或说明文字。")
        }.trim()
    }

    private fun sanitizeFloatingSuggestion(text: String): String {
        return text
            .replace(Regex("[（(][^（）()]{0,60}(?:已根据|根据|微调|建议|生成批次|要求)[^（）()]{0,80}[）)]"), "")
            .replace(Regex("^\\s*(?:option\\d+|formal|casual|humorous|回复\\d*|建议\\d*)\\s*[:：]\\s*", RegexOption.IGNORE_CASE), "")
            .replace("（", "")
            .replace("）", "")
            .trim()
    }

    // 复制回复建议，并在会话中添加预备消息
    private fun handleSuggestionCopied(suggestionText: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AI建议回复", suggestionText))
        Toast.makeText(context, "建议已复制到剪贴板！", Toast.LENGTH_SHORT).show()

        // 尝试自动填充到聊天输入框
        ChatAccessibilityService.pasteReplyToActiveChat(suggestionText)

        // 双轨缓存，以便后续校准
        lastCopiedSuggestion = suggestionText

        val currentSid = activeSessionId
        if (currentSid != null) {
            scope.launch(Dispatchers.IO) {
                val fp = MessageFingerprintUtil.calculateFingerprint("USER", suggestionText, "TEXT")
                messageDao.insertMessage(
                    ChatMessageEntity(
                        messageId = UUID.randomUUID().toString(),
                        sessionId = currentSid,
                        senderType = "USER",
                        senderName = "\u6211",
                        content = suggestionText,
                        messageType = "TEXT",
                        fingerprint = fp,
                        timestamp = System.currentTimeMillis(),
                        status = "PENDING_SEND"
                    )
                )
                sessionDao.updateSessionTimestamp(currentSid, System.currentTimeMillis())
            }
        }
    }

    fun onNotificationMessage(event: ChatNotificationListener.ChatMessageEvent) {
        Log.d(TAG, "Notification capture disabled; ignoring ${event.senderName}")
    }
    private fun resolveRelationLabel(relationId: String?): String {
        return when (relationId?.lowercase()) {
            "leader", "领导" -> "领导"
            "colleague", "同事" -> "同事"
            "friend", "朋友" -> "朋友"
            "family", "家人" -> "家人"
            else -> "朋友"
        }
    }

    private fun resolveStyleLabel(styleId: String?): ReplyStyle? {
        return when (styleId?.lowercase()) {
            "default", "默认" -> null
            "serious", "严肃", "formal", "正式" -> ReplyStyle.FORMAL
            "humorous", "幽默" -> ReplyStyle.HUMOROUS
            "casual", "轻松", "随意" -> ReplyStyle.CASUAL
            "empathetic", "共情" -> ReplyStyle.EMPATHETIC
            "assertive", "坚定" -> ReplyStyle.ASSERTIVE
            "dai_zhou_li", "daizhou_li" -> ReplyStyle.DAI_ZHOU_LI
            else -> null
        }
    }
}

private data class BoundSessionContext(
    val contactName: String,
    val contactId: String,
    val sessionId: String,
    val isExplicitSession: Boolean = false
)

private data class ScreenshotRecognitionResult(
    val contactName: String?,
    val topicName: String?,
    val messages: List<ScreenshotMessage>
)

private data class ScreenshotImportResult(
    val importedCount: Int,
    val boundContext: BoundSessionContext,
    val recognizedContactName: String?,
    val recognizedTopicName: String?,
    val recognizedMessageCount: Int
)

private data class ScreenshotMessage(
    val senderType: String,
    val senderName: String,
    val content: String
)
