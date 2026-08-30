package com.socialfirstaidkit.feature.socialcopy.model

import com.socialfirstaidkit.core.llm.model.MomentCopySuggestion
import com.socialfirstaidkit.core.llm.model.MomentImageAnalysis
import kotlinx.serialization.Serializable

@Serializable
data class SocialCopyHistory(
    val id: String,
    val createdAt: Long,
    val isTextOnly: Boolean,
    val imageUriString: String? = null,
    val imageDescription: String,
    val analysis: MomentImageAnalysis,
    val styleName: String,
    val styleDescription: String,
    val additionalInfo: String? = null,
    val suggestions: List<MomentCopySuggestion> = emptyList(),
    val generatedImageUrl: String? = null
)
