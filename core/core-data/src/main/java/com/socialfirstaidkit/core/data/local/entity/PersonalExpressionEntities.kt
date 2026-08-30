package com.socialfirstaidkit.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_expression_profile")
data class PersonalExpressionProfileEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val autoLearningEnabled: Boolean = true,
    val questionnaireCompleted: Boolean = false,
    val profileJson: String,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "personal_expression_questionnaire_draft")
data class PersonalExpressionQuestionnaireDraftEntity(
    @PrimaryKey val id: Int = 1,
    val answersJson: String,
    val currentPage: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "personal_expression_feedback_sample")
data class PersonalExpressionFeedbackSampleEntity(
    @PrimaryKey val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String,
    val relation: String,
    val style: String,
    val scene: String,
    val suggestionSlot: String,
    val copiedText: String,
    val sourceMessage: String,
    val profileVersion: Int?,
    val consumedByProposalId: String? = null
)

@Entity(tableName = "personal_expression_version")
data class PersonalExpressionVersionEntity(
    @PrimaryKey val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val version: Int,
    val reason: String,
    val profileJson: String
)

@Entity(tableName = "personal_expression_update_proposal")
data class PersonalExpressionUpdateProposalEntity(
    @PrimaryKey val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "pending",
    val observationsJson: String,
    val changesJson: String,
    val previewBefore: String,
    val previewAfter: String,
    val updatedProfileJson: String,
    val sampleIdsJson: String
)
