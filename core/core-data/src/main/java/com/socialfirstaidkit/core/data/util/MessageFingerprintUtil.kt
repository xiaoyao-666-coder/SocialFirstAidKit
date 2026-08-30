package com.socialfirstaidkit.core.data.util

import java.security.MessageDigest

object MessageFingerprintUtil {
    fun calculateFingerprint(senderType: String, content: String, messageType: String = "TEXT"): String {
        val raw = "$senderType|${content.trim()}|$messageType"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
