package com.socialfirstaidkit.service.accessibility

/**
 * Shared package helpers for the accessibility-only realtime assistant.
 * Notification payload capture has been removed; chat content now comes only
 * from the currently visible accessibility window.
 */
object ChatNotificationListener {
    private val SUPPORTED_CHAT_PACKAGES = setOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.tencent.tim",
        "com.tencent.wework",
        "com.alibaba.android.rimet",
        "com.ss.android.lark",
        "com.larksuite.suite",
        "org.telegram.messenger",
        "org.thunderdog.challegram",
        "com.whatsapp",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.vivo.messages"
    )

    private val IGNORED_PACKAGE_PREFIXES = listOf(
        "android",
        "com.android.",
        "com.google.android.inputmethod",
        "com.sohu.inputmethod",
        "com.baidu.input",
        "com.iflytek.inputmethod",
        "com.bbk.launcher",
        "com.vivo.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.sec.android.app.launcher"
    )

    fun isSupportedPackage(packageName: String?): Boolean {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank()) return false
        if (pkg.contains("inputmethod", ignoreCase = true) || pkg.contains("keyboard", ignoreCase = true)) {
            return false
        }
        if (IGNORED_PACKAGE_PREFIXES.any { prefix -> pkg == prefix || pkg.startsWith(prefix) }) {
            return false
        }
        return pkg in SUPPORTED_CHAT_PACKAGES
    }

    fun isRealtimeAccessibilityPackage(packageName: String?): Boolean {
        return packageName?.trim() == "com.tencent.mm"
    }

    data class ChatMessageEvent(
        val sourceApp: String,
        val senderName: String,
        val messageContent: String,
        val timestamp: Long = System.currentTimeMillis()
    )
}
