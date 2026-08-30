package com.socialfirstaidkit.feature.chatassist.voice

sealed class ShortVoiceInputResult {
    data class Partial(val text: String) : ShortVoiceInputResult()
    data class Final(val text: String, val isLast: Boolean = false) : ShortVoiceInputResult()
    data class Error(val message: String) : ShortVoiceInputResult()
}
