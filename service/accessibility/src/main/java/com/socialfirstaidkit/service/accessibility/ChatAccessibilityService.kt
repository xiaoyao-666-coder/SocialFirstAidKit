package com.socialfirstaidkit.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.socialfirstaidkit.core.llm.model.ChatMessage
import com.socialfirstaidkit.core.llm.model.SenderType
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import com.socialfirstaidkit.service.accessibility.NewRealtimeAssistCoordinator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Optional enhancement for user-authorized visible-screen context.
 *
 * The service never reads chat app databases. It only inspects visible
 * accessibility text while the user has explicitly enabled the service.
 */
@AndroidEntryPoint
class ChatAccessibilityService : AccessibilityService() {

    @Inject lateinit var realtimeCoordinator: NewRealtimeAssistCoordinator

    companion object {
        private const val TAG = "ChatAccessibility"

        @Volatile
        private var activeService: ChatAccessibilityService? = null

        @Volatile
        private var lastChatSnapshot: VisibleChatSnapshot? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        private const val WECHAT_MSG_LIST_ID = "com.tencent.mm:id/b3q"
        private const val WECHAT_INPUT_BOX_ID = "com.tencent.mm:id/bkk"
        private const val SNAPSHOT_TTL_MS = 15_000L
        private const val UNKNOWN_CONTACT_NAME = "\u672a\u77e5\u8054\u7cfb\u4eba"
        private const val CHAT_WINDOW_PLACEHOLDER = "\u804a\u5929\u7a97\u53e3"
        private const val NON_CHAT_RECHECK_MS = 1_200L

        private val CHROME_TEXT = setOf(
            "\u53d1\u9001",
            "\u8868\u60c5",
            "\u66f4\u591a",
            "\u8bed\u97f3",
            "\u6309\u4f4f \u8bf4\u8bdd",
            "\u804a\u5929\u4fe1\u606f",
            "\u8fd4\u56de",
            "\u6211\u7684\u8d44\u6599\u5361",
            "\u5220\u9664",
            "\u8df3\u8f6c\u56de\u539f\u6d88\u606f",
            "\u590d\u5236",
            "\u56de\u590d",
            "\u8f6c\u53d1",
            "\u6536\u85cf",
            "\u591a\u9009",
            "\u64a4\u56de",
            "\u7f6e\u9876",
            "\u6807\u4e3a\u672a\u8bfb",
            "\u5728\u7ebf",
            "WiFi"
        )
        fun pasteReplyToActiveChat(text: String): Boolean {
            return activeService?.pasteToActiveInputBox(text) ?: false
        }

        fun isServiceConnected(): Boolean = activeService != null

        fun isFloatingBallVisible(): Boolean {
            return activeService?.realtimeCoordinator?.isFloatingBallVisible() == true
        }

        fun showFloatingBallNow(contactId: String? = null, sessionId: String? = null): Boolean {
            val service = activeService ?: return false
            return service.realtimeCoordinator.showFloatingBallNow(
                overlayContext = service,
                boundContactId = contactId ?: RealtimeOverlayPrefs.getBoundContactId(service.applicationContext),
                boundSessionId = sessionId ?: RealtimeOverlayPrefs.getBoundSessionId(service.applicationContext)
            )
        }

        fun closeFloatingBallIfBoundToDeleted(
            contactId: String? = null,
            sessionIds: Collection<String> = emptyList()
        ): Boolean {
            val service = activeService ?: return false
            val closeAction: () -> Unit = {
                runCatching {
                    service.realtimeCoordinator.closeIfBoundToDeleted(contactId, sessionIds)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to close floating ball after bound chat deletion", error)
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                closeAction()
            } else {
                mainHandler.post { closeAction() }
            }
            return true
        }

        fun getVisibleMessagesAndContact(): Pair<String, List<ChatMessage>>? {
            val service = activeService
            val now = System.currentTimeMillis()
            val rootNode = service?.rootInActiveWindow
            val liveSnapshot = rootNode
                ?.takeIf { node -> ChatNotificationListener.isRealtimeAccessibilityPackage(node.packageName?.toString()) }
                ?.let { node ->
                    val packageName = node.packageName?.toString() ?: "com.tencent.mm"
                    service.buildVisibleChatSnapshot(node, packageName).also { snapshot ->
                        if (snapshot == null) {
                            Log.d(TAG, "Visible window is not a chat page; trying cached snapshot")
                        }
                    }
                }
                ?.also { snapshot ->
                    lastChatSnapshot = snapshot
                    Log.d(TAG, "Using live chat snapshot messages=${snapshot.messages.size}")
                }

            val snapshot = liveSnapshot ?: lastChatSnapshot
                ?.takeIf { cached -> now - cached.capturedAt <= SNAPSHOT_TTL_MS }
                ?.also { cached ->
                    Log.d(TAG, "Using cached chat snapshot messages=${cached.messages.size}, age=${now - cached.capturedAt}ms")
                }

            return snapshot?.let { Pair(it.contactName, it.messages) }
        }
    }

    private data class VisibleTextNode(
        val text: String,
        val bounds: Rect
    )

    private data class MessageCluster(
        val senderType: SenderType,
        val lines: MutableList<VisibleTextNode>,
        val bounds: Rect
    )

    private data class VisibleChatSnapshot(
        val packageName: String,
        val contactName: String,
        val messages: List<ChatMessage>,
        val capturedAt: Long
    )

    
    private var lastNonChatPackageName: String? = null
    private var lastNonChatAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        Log.d(TAG, "Accessibility service connected")
        if (RealtimeOverlayPrefs.isEnabled(applicationContext) &&
            RealtimeOverlayPrefs.isManualStartRequested(applicationContext)
        ) {
            realtimeCoordinator.showFloatingBallNow(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (!RealtimeOverlayPrefs.isEnabled(applicationContext)) {
            realtimeCoordinator.onChatNoLongerVisible()
            return
        }

        val eventPackageName = event.packageName?.toString()
        val activeRoot = rootInActiveWindow
        val activePackageName = activeRoot?.packageName?.toString()
        val packageName = activePackageName?.takeIf { ChatNotificationListener.isRealtimeAccessibilityPackage(it) }

        if (packageName == null) {
            val transientPackage = eventPackageName.orEmpty()
            Log.d(TAG, "Ignoring unsupported active window event=${event.eventType}, eventPkg=$eventPackageName, activePkg=$activePackageName")
            if (transientPackage.isNotBlank() && !shouldIgnoreTransientWindow(transientPackage)) {
                realtimeCoordinator.onChatNoLongerVisible()
            }
            return
        }

        if (shouldSkipRecentNonChat(packageName, event.eventType)) {
            realtimeCoordinator.onChatNoLongerVisible()
            return
        }

        Log.d(TAG, "Chat-capable active window event=${event.eventType}, eventPkg=$eventPackageName, activePkg=$activePackageName")
        if (eventPackageName == applicationContext.packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                Log.d(TAG, "Overlay accessibility click fallback event=${event.eventType}, activePkg=$activePackageName")
                realtimeCoordinator.onFloatingBallAccessibilityClick()
            } else {
                Log.d(TAG, "Ignoring overlay self event=${event.eventType}, activePkg=$activePackageName")
            }
            return
        }
        val chatRoot = activeRoot ?: return
        val snapshot = cachedSnapshotForLightEvent(packageName, event.eventType)
            ?: buildVisibleChatSnapshot(chatRoot, packageName)
        if (snapshot == null) {
            lastChatSnapshot = null
            rememberNonChat(packageName)
            Log.d(TAG, "Active window is not a chat page; hiding floating ball")
            realtimeCoordinator.onChatNoLongerVisible()
            return
        }

        lastNonChatPackageName = null
        lastChatSnapshot = snapshot
        realtimeCoordinator.onChatVisible(snapshot.contactName, this)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> realtimeCoordinator.onChatContentChanged()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeService === this) {
            activeService = null
        }
    }

    private fun cachedSnapshotForLightEvent(packageName: String, eventType: Int): VisibleChatSnapshot? {
        if (eventType != AccessibilityEvent.TYPE_VIEW_CLICKED && eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            return null
        }
        val now = System.currentTimeMillis()
        return lastChatSnapshot
            ?.takeIf { cached -> cached.packageName == packageName && now - cached.capturedAt <= SNAPSHOT_TTL_MS }
            ?.also { cached ->
                Log.d(TAG, "Reuse cached snapshot for light event=$eventType messages=${cached.messages.size}, age=${now - cached.capturedAt}ms")
            }
    }
    private fun publishVisibleSnapshot(
        packageName: String,
        triggerRealtimeSuggestion: Boolean
    ) {
        val rootNode = rootInActiveWindow ?: return
        if (!ChatNotificationListener.isRealtimeAccessibilityPackage(rootNode.packageName?.toString())) return
        val snapshot = buildVisibleChatSnapshot(rootNode, packageName) ?: return

        lastChatSnapshot = snapshot
        realtimeCoordinator.onChatVisible(snapshot.contactName)

        realtimeCoordinator.onChatContentChanged()
    }

    private fun buildVisibleChatSnapshot(
        rootNode: AccessibilityNodeInfo,
        packageName: String
    ): VisibleChatSnapshot? {
        if (!isChatInterface(rootNode, packageName)) return null

        val contactName = extractContactName(rootNode, packageName)
        val messages = extractRecentMessages(rootNode, packageName, contactName)
        val hasChatInput = hasBottomEditableInput(rootNode) || hasBottomInputControls(rootNode) ||
            (packageName == "com.tencent.mm" && findNodeById(rootNode, WECHAT_INPUT_BOX_ID) != null)
        if (contactName == UNKNOWN_CONTACT_NAME && messages.isEmpty() && !hasChatInput) {
            Log.d(TAG, "Ignoring empty chat snapshot without chat input")
            return null
        }

        val visibleContactName = if (contactName == UNKNOWN_CONTACT_NAME && hasChatInput) {
            CHAT_WINDOW_PLACEHOLDER
        } else {
            contactName
        }

        Log.d(TAG, "Captured chat snapshot messages=${messages.size}, hasInput=$hasChatInput")
        return VisibleChatSnapshot(packageName, visibleContactName, messages, System.currentTimeMillis())
    }

    private fun shouldIgnoreTransientWindow(packageName: String): Boolean {
        return packageName == applicationContext.packageName ||
            packageName == "android" ||
            packageName.startsWith("com.android.") ||
            packageName.contains("launcher", ignoreCase = true) ||
            packageName.contains("permissioncontroller", ignoreCase = true) ||
            packageName.contains("packageinstaller", ignoreCase = true) ||
            packageName.contains("settings", ignoreCase = true) ||
            packageName.contains("inputmethod", ignoreCase = true) ||
            packageName.contains("keyboard", ignoreCase = true)
    }


    private fun shouldSkipRecentNonChat(packageName: String, eventType: Int): Boolean {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false
        if (packageName != lastNonChatPackageName) return false
        return System.currentTimeMillis() - lastNonChatAt < NON_CHAT_RECHECK_MS
    }

    private fun rememberNonChat(packageName: String) {
        lastNonChatPackageName = packageName
        lastNonChatAt = System.currentTimeMillis()
    }

    private fun isKnownNonChatPackage(packageName: String): Boolean {
        return packageName == applicationContext.packageName ||
            packageName.contains("launcher", ignoreCase = true) ||
            packageName.contains("permissioncontroller", ignoreCase = true) ||
            packageName.contains("packageinstaller", ignoreCase = true) ||
            packageName.contains("settings", ignoreCase = true)
    }
    private fun isChatInterface(rootNode: AccessibilityNodeInfo, packageName: String): Boolean {
        if (isKnownNonChatPackage(packageName)) return false
        val texts = collectVisibleTexts(rootNode)
        val hasKnownInput = packageName == "com.tencent.mm" && findNodeById(rootNode, WECHAT_INPUT_BOX_ID) != null
        val hasKnownMessageList = packageName == "com.tencent.mm" && findNodeById(rootNode, WECHAT_MSG_LIST_ID) != null
        val hasBottomInputControls = hasBottomInputControls(rootNode)
        val hasBottomEditableInput = hasBottomEditableInput(rootNode)
        val hasTitle = bestTitleCandidate(rootNode) != null
        val hasUsefulMessages = texts
            .map(::sanitizeVisibleText)
            .filter { it.isNotBlank() && !isChromeText(it) && !shouldIgnoreMessageText(it) }
            .size >= 1
        val hasGenericInput = hasBottomInputControls || hasBottomEditableInput
        val result = hasKnownInput || hasKnownMessageList ||
            (hasGenericInput && hasTitle && hasUsefulMessages)
        Log.d(
            TAG,
            "Generic chat check pkg=$packageName result=$result inputId=$hasKnownInput msgList=$hasKnownMessageList bottomControls=$hasBottomInputControls bottomEditable=$hasBottomEditableInput title=$hasTitle usefulMessages=$hasUsefulMessages texts=${texts.take(8)}"
        )
        return result
    }

    private fun hasBottomInputControls(rootNode: AccessibilityNodeInfo): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        val bottomStart = (screenHeight * 0.72f).toInt()
        val nodes = mutableListOf<VisibleTextNode>()
        collectTextNodesFromNode(rootNode, nodes)
        return nodes
            .map { it.copy(text = sanitizeVisibleText(it.text)) }
            .filter { it.text.isNotBlank() && it.bounds.top >= bottomStart }
            .any { node ->
                val text = node.text
                text == "\u53d1\u9001" ||
                    text == "\u6309\u4f4f \u8bf4\u8bdd" ||
                    text.contains("\u6309\u4f4f") ||
                    text.contains("\u8bf4\u8bdd") ||
                    text.contains("\u8bed\u97f3") ||
                    text.contains("\u8868\u60c5") ||
                    text.contains("\u66f4\u591a")
            }
    }

    private fun hasBottomEditableInput(rootNode: AccessibilityNodeInfo): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        val bottomStart = (screenHeight * 0.58f).toInt()
        return hasEditableNodeInBottom(rootNode, bottomStart)
    }

    private fun hasEditableNodeInBottom(node: AccessibilityNodeInfo, bottomStart: Int): Boolean {
        return findEditableNodeInBottom(node, bottomStart) != null
    }

    private fun findEditableNodeInBottom(node: AccessibilityNodeInfo, bottomStart: Int): AccessibilityNodeInfo? {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val className = node.className?.toString().orEmpty()
        if (
            bounds.top >= bottomStart &&
            bounds.width() > 0 &&
            bounds.height() > 0 &&
            (node.isEditable || className.contains("EditText", ignoreCase = true))
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableNodeInBottom(child, bottomStart)?.let { return it }
        }
        return null
    }

    private fun collectVisibleTexts(rootNode: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        collectTextFromNode(rootNode, texts)
        return texts.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun sanitizeVisibleText(text: String): String {
        return text
            .replace("\u200e", "")
            .replace("\u200f", "")
            .replace("\u202a", "")
            .replace("\u202c", "")
            .replace('\u00a0', ' ')
            .replace('\u3000', ' ')
            .trim()
    }

    private fun isChromeText(text: String): Boolean {
        val clean = sanitizeVisibleText(text)
        if (clean.isBlank()) return true
        if (clean in CHROME_TEXT) return true
        if (clean.matches(Regex("^\\d{1,2}:\\d{2}$"))) return true
        if (clean.matches(Regex("^\\d+\\u6761\\u65b0\\u6d88\\u606f$"))) return true
        return false
    }

    private fun bestTitleCandidate(rootNode: AccessibilityNodeInfo): String? {
        val rootBounds = Rect().also { rootNode.getBoundsInScreen(it) }
        val width = if (rootBounds.width() > 0) rootBounds.width() else resources.displayMetrics.widthPixels
        val topLimit = (resources.displayMetrics.heightPixels * 0.18f).toInt()
        val candidates = mutableListOf<VisibleTextNode>()
        collectTextNodesFromNode(rootNode, candidates)
        return candidates
            .map { it.copy(text = sanitizeVisibleText(it.text)) }
            .filter { it.text.isNotBlank() && !isChromeText(it.text) }
            .filter { !shouldIgnoreMessageText(it.text) }
            .filter { !looksLikeMessageBody(it.text) }
            .filter { it.bounds.top in 0..topLimit }
            .filter { it.bounds.centerX() in (width * 0.22f).toInt()..(width * 0.78f).toInt() }
            .filter { it.bounds.width() <= (width * 0.72f).toInt() }
            .maxByOrNull { it.bounds.width() * it.bounds.height() }
            ?.text
    }
    private fun extractContactName(rootNode: AccessibilityNodeInfo, packageName: String): String {
        val preferredIds = when (packageName) {
            "com.tencent.mm" -> listOf("com.tencent.mm:id/lll")
            else -> emptyList()
        }

        for (id in preferredIds) {
            val text = findNodeById(rootNode, id)?.text?.toString()?.let(::sanitizeVisibleText)
            if (!text.isNullOrBlank() && !isChromeText(text)) {
                Log.d(TAG, "Chat title by id=$id name=$text")
                return text
            }
        }

        bestTitleCandidate(rootNode)?.let { title ->
            Log.d(TAG, "Chat title by top candidate name=$title")
            return title
        }

        val texts = mutableListOf<String>()
        collectTextFromNode(rootNode, texts)
        return texts.map(::sanitizeVisibleText).firstOrNull { !isChromeText(it) } ?: UNKNOWN_CONTACT_NAME
    }
    private fun extractRecentMessages(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        contactName: String
    ): List<ChatMessage> {
        val msgListId = when (packageName) {
            "com.tencent.mm" -> WECHAT_MSG_LIST_ID
            else -> null
        }

        val msgListNode = msgListId?.let { findNodeById(rootNode, it) }
        val fallbackToRoot = msgListNode == null
        val sourceNode = msgListNode ?: rootNode
        if (fallbackToRoot && msgListId != null) {
            Log.w(TAG, "Known message list id=$msgListId not found, fallback to visible root text")
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val topLimit = (screenHeight * 0.14f).toInt()
        val bottomLimit = (screenHeight * 0.90f).toInt()
        val contentBounds = Rect().also { sourceNode.getBoundsInScreen(it) }
        val effectiveTop = max(topLimit, contentBounds.top)
        val effectiveBottom = min(bottomLimit, contentBounds.bottom.takeIf { it > 0 } ?: bottomLimit)
        val textNodes = mutableListOf<VisibleTextNode>()
        collectTextNodesFromNode(sourceNode, textNodes)

        val filteredNodes = textNodes
            .map { node -> node.copy(text = sanitizeVisibleText(node.text)) }
            .filter { it.text.isNotBlank() && !isChromeText(it.text) }
            .filter { !shouldIgnoreMessageText(it.text) }
            .filter { it.bounds.width() > 0 && it.bounds.height() > 0 }
            .filter { it.text != contactName }
            .filter { node -> node.bounds.top >= effectiveTop && node.bounds.bottom <= effectiveBottom }
            .distinctBy { Triple(it.text, it.bounds.top, it.bounds.left) }
            .sortedBy { it.bounds.top }
        val messages = buildMessageClusters(filteredNodes, screenWidth)
            .takeLast(12)
            .mapNotNull { cluster ->
                val content = cluster.lines
                    .sortedWith(compareBy<VisibleTextNode> { it.bounds.top }.thenBy { it.bounds.left })
                    .joinToString(separator = "\n") { it.text }
                    .trim()
                if (content.isBlank() || shouldIgnoreMessageText(content)) {
                    return@mapNotNull null
                }
                Log.d(
                    TAG,
                    "Chat msg cluster side=${cluster.senderType} " +
                        "x=${cluster.bounds.left}-${cluster.bounds.right} " +
                        "y=${cluster.bounds.top}-${cluster.bounds.bottom} " +
                        "lines=${cluster.lines.size} text=${content.take(40)}"
                )
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderType = cluster.senderType,
                    senderName = if (cluster.senderType == SenderType.USER) "\u6211" else contactName,
                    content = content,
                    timestamp = System.currentTimeMillis()
                )
            }
        Log.d(TAG, "Extracted visible messages=${messages.size}")
        return messages
    }

    private fun buildMessageClusters(
        nodes: List<VisibleTextNode>,
        screenWidth: Int
    ): List<MessageCluster> {
        if (nodes.isEmpty()) return emptyList()

        val verticalGapThreshold = max((resources.displayMetrics.density * 18).toInt(), 22)
        val horizontalDriftThreshold = max((screenWidth * 0.12f).toInt(), 72)
        val clusters = mutableListOf<MessageCluster>()

        nodes.forEach { node ->
            val senderType = determineSenderByBounds(node.bounds, screenWidth)
            if (senderType == SenderType.CONTACT && looksLikeTitleText(node.text)) {
                return@forEach
            }
            if (senderType != SenderType.USER && senderType != SenderType.CONTACT) {
                return@forEach
            }

            val target = clusters.lastOrNull()?.takeIf { cluster ->
                cluster.senderType == senderType &&
                    node.bounds.top - cluster.bounds.bottom <= verticalGapThreshold &&
                    horizontalAnchorDistance(node.bounds, cluster.bounds, senderType) <= horizontalDriftThreshold &&
                    !cluster.lines.any { existing -> existing.text == node.text && abs(existing.bounds.top - node.bounds.top) <= verticalGapThreshold }
            }

            if (target != null) {
                target.lines.add(node)
                target.bounds.union(node.bounds)
            } else {
                clusters.add(
                    MessageCluster(
                        senderType = senderType,
                        lines = mutableListOf(node),
                        bounds = Rect(node.bounds)
                    )
                )
            }
        }

        return clusters
    }

    private fun determineSenderByBounds(bounds: Rect, screenWidth: Int): SenderType {
        val widthRatio = bounds.width().toFloat() / screenWidth.toFloat()
        val centerX = bounds.centerX()
        val marginBias = bounds.left - (screenWidth - bounds.right)
        val marginThreshold = (screenWidth * 0.10f).toInt()

        return when {
            widthRatio >= 0.86f -> SenderType.CONTACT
            marginBias >= marginThreshold -> SenderType.USER
            marginBias <= -marginThreshold -> SenderType.CONTACT
            centerX >= screenWidth * 0.62f -> SenderType.USER
            centerX <= screenWidth * 0.38f -> SenderType.CONTACT
            else -> SenderType.CONTACT
        }
    }

    private fun horizontalAnchorDistance(a: Rect, b: Rect, senderType: SenderType): Int {
        return if (senderType == SenderType.USER) {
            abs(a.right - b.right)
        } else {
            abs(a.left - b.left)
        }
    }

    private fun looksLikeTitleText(text: String): Boolean {
        val clean = sanitizeVisibleText(text)
        return clean.isNotBlank() &&
            clean.length <= 30 &&
            !looksLikeMessageBody(clean) &&
            !clean.contains("\n")
    }

    private fun looksLikeMessageBody(text: String): Boolean {
        val clean = sanitizeVisibleText(text)
        return clean.length >= 18 ||
            clean.contains("\n") ||
            clean.contains("\uff0c") ||
            clean.contains("\u3002") ||
            clean.contains("?") ||
            clean.contains("\uff1f") ||
            clean.contains("!") ||

            clean.contains("\uff01")
    }

    private fun shouldIgnoreMessageText(text: String): Boolean {
        val clean = sanitizeVisibleText(text)
        if (clean.isBlank()) return true
        if (isChromeText(clean)) return true
        if (clean == CHAT_WINDOW_PLACEHOLDER) return true
        if (clean in CHROME_TEXT) return true
        if (looksLikeChatPresenceStatus(clean)) return true
        if (clean.matches(Regex("^.+\\s*[·•]\\s*\\d{1,2}:\\d{2}$"))) return true
        if (clean.matches(Regex("^.+\\s*[-·•]\\s*WiFi\\s*[·•]\\s*\\d{1,2}:\\d{2}$", RegexOption.IGNORE_CASE))) return true
        if (clean.matches(Regex("^.+\\s*[·•]\\s*\\u5728\\u7ebf.*$"))) return true
        if (clean.matches(Regex("^\\u5728\\u7ebf\\s*[-·•]\\s*WiFi.*$", RegexOption.IGNORE_CASE))) return true
        if (clean.matches(Regex("^\\d{4}年\\d{1,2}月\\d{1,2}�?*$"))) return true
        if (clean.matches(Regex("^星期[一二三四五六日天].*$"))) return true
        if (clean.matches(Regex("^以下�?*消息.*$"))) return true
        if (clean.matches(Regex("^以上�?*内容.*$"))) return true
        if (clean.contains("\u64a4\u56de\u4e86\u4e00\u6761\u6d88\u606f")) return true
        if (clean.contains("\u73b0\u5728\u53ef\u4ee5\u5f00\u59cb\u804a\u5929")) return true
        if (clean.contains("朋友验证")) return true
        if (clean.contains("微信转账")) return true
        if (clean.length == 1 && !clean[0].isLetterOrDigit() && clean[0] !in '\u4e00'..'\u9fff') return true
        return false
    }
    private fun looksLikeChatPresenceStatus(text: String): Boolean {
        val compact = sanitizeVisibleText(text)
            .replace(Regex("\\s+"), "")
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u00b7', '-')
            .replace('\u2022', '-')
            .replace('\u30fb', '-')
            .replace('\uff1a', ':')
        val hasClock = compact.contains(Regex("\\d{1,2}:\\d{2}"))
        if (!hasClock) return false
        if (compact.contains("\u5728\u7ebf", ignoreCase = true) && compact.contains("WiFi", ignoreCase = true)) return true
        if (compact.contains("\u79bb\u7ebf", ignoreCase = true) && compact.contains("WiFi", ignoreCase = true)) return true
        if (compact.contains("4G", ignoreCase = true) || compact.contains("5G", ignoreCase = true)) {
            return compact.length <= 24
        }
        return false
    }

    private fun collectTextFromNode(node: AccessibilityNodeInfo, result: MutableList<String>) {
        val label = node.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        label?.let(result::add)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTextFromNode(child, result)
            }
        }
    }

    private fun collectTextNodesFromNode(node: AccessibilityNodeInfo, result: MutableList<VisibleTextNode>) {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val label = node.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        label?.let { result.add(VisibleTextNode(it, Rect(bounds))) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTextNodesFromNode(child, result)
            }
        }
    }

    private fun findNodeById(rootNode: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        return runCatching {
            rootNode.findAccessibilityNodeInfosByViewId(id).firstOrNull()
        }.getOrNull()
    }

    private fun pasteToActiveInputBox(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val packageName = rootNode.packageName?.toString().orEmpty()
        val screenHeight = resources.displayMetrics.heightPixels
        val bottomStart = (screenHeight * 0.50f).toInt()
        val inputBox = if (packageName == "com.tencent.mm") {
            findNodeById(rootNode, WECHAT_INPUT_BOX_ID)
        } else {
            null
        } ?: findEditableNodeInBottom(rootNode, bottomStart) ?: return false

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        inputBox.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

}
