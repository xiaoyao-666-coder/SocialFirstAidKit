package com.socialfirstaidkit.feature.chatassist.viewmodel

import com.socialfirstaidkit.core.data.model.OcrResult
import com.socialfirstaidkit.core.llm.api.ReplyStyle
import com.socialfirstaidkit.core.llm.api.ScreenshotAnalysisResult
import com.socialfirstaidkit.core.llm.model.ReplySuggestions

sealed class MessageAssistUiState {
    data object Idle : MessageAssistUiState()
    data object ProcessingImage : MessageAssistUiState()
    data object AnalyzingImage : MessageAssistUiState()
    data class ImageProcessed(
        val ocrResult: OcrResult,
        val analysisResult: ScreenshotAnalysisResult
    ) : MessageAssistUiState()
    data class ConversationAnalyzed(
        val analysis: ConversationContextAnalysis
    ) : MessageAssistUiState()
    data object Generating : MessageAssistUiState()
    data class SuggestionsReady(
        val suggestions: ReplySuggestions,
        val mode: ReplyMode,
        val sourceMessage: String,
        val replyStyle: ReplyStyle? = null,
        val contextAnalysis: ConversationContextAnalysis? = null
    ) : MessageAssistUiState()
    data class Error(val message: String) : MessageAssistUiState()
}

data class ConversationContextAnalysis(
    val scene: String,
    val tone: String,
    val emotion: String,
    val intent: String,
    val strategy: String
)

enum class ReplyMode {
    SINGLE_TEXT,
    CONTEXTUAL
}
