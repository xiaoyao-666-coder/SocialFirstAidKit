package com.socialfirstaidkit.core.data.personalexpression

import com.socialfirstaidkit.core.data.local.dao.PersonalExpressionDao
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionFeedbackSampleEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionProfileEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionQuestionnaireDraftEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionUpdateProposalEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionVersionEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PersonalExpressionRepository @Inject constructor(
    private val dao: PersonalExpressionDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    private val proposalMutex = Mutex()

    fun observeProfile(): Flow<PersonalExpressionProfile?> {
        return dao.observeProfile().map { it?.toProfileOrNull() }
    }

    fun observeVersions(): Flow<List<PersonalExpressionVersion>> {
        return dao.observeVersions().map { versions ->
            versions.mapNotNull { entity ->
                entity.toVersionOrNull()
            }
        }
    }

    fun observePendingProposal(): Flow<PersonalExpressionUpdateProposal?> {
        return dao.observePendingProposal().map { it?.toProposalOrNull() }
    }

    suspend fun getProfile(): PersonalExpressionProfile? {
        return dao.getProfile()?.toProfileOrNull()
    }

    suspend fun getPromptContext(relationship: String): PersonalExpressionPromptContext? {
        val profile = getProfile() ?: return null
        if (!profile.enabled || !profile.questionnaireCompleted) return null
        return PersonalExpressionPromptContext(
            profile = profile,
            relationship = relationship,
            summaryTags = profile.summaryTags()
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        val profile = getProfile()
        if (profile == null && enabled) {
            saveProfile(PersonalExpressionQuestionnaire.defaultProfile(enabled = true), reason = "默认档案")
            return
        }
        dao.setProfileEnabled(enabled)
    }

    suspend fun setAutoLearningEnabled(enabled: Boolean) {
        val profile = getProfile()
        if (profile == null) {
            saveProfile(PersonalExpressionQuestionnaire.defaultProfile(enabled = false).copy(autoLearningEnabled = enabled), reason = "默认档案")
            return
        }
        dao.setAutoLearningEnabled(enabled)
    }

    suspend fun createDefaultProfile(enabled: Boolean) {
        saveProfile(PersonalExpressionQuestionnaire.defaultProfile(enabled = enabled), reason = "默认档案")
        dao.deleteDraft()
    }

    suspend fun completeQuestionnaire(answers: Map<String, List<String>>): PersonalExpressionProfile {
        val oldProfile = getProfile()
        if (oldProfile != null) saveVersion(oldProfile, "问卷重做前")
        val nextVersion = (oldProfile?.version ?: 0) + 1
        val profile = PersonalExpressionQuestionnaire.buildProfileFromAnswers(
            answers = answers,
            enabled = false,
            autoLearningEnabled = oldProfile?.autoLearningEnabled ?: true
        ).copy(
            learningThreshold = oldProfile?.learningThreshold ?: LearningThreshold.BALANCED,
            version = nextVersion
        )
        saveProfile(profile, reason = "问卷生成")
        dao.deleteDraft()
        return profile
    }

    suspend fun saveEditedProfile(profile: PersonalExpressionProfile, reason: String = "手动编辑") {
        getProfile()?.let { saveVersion(it, reason) }
        saveProfile(
            profile.copy(
                version = profile.version + 1,
                lastUpdatedAt = System.currentTimeMillis()
            ),
            reason = reason
        )
    }

    suspend fun saveQuestionnaireDraft(draft: PersonalExpressionDraft) {
        dao.upsertDraft(
            PersonalExpressionQuestionnaireDraftEntity(
                answersJson = json.encodeToString(draft.answers),
                currentPage = draft.currentPage,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getQuestionnaireDraft(): PersonalExpressionDraft {
        val entity = dao.getDraft() ?: return PersonalExpressionDraft()
        val answers = runCatching {
            json.decodeFromString<Map<String, List<String>>>(entity.answersJson)
        }.getOrDefault(emptyMap())
        return PersonalExpressionDraft(
            answers = answers,
            currentPage = entity.currentPage,
            updatedAt = entity.updatedAt
        )
    }

    suspend fun clearQuestionnaireDraft() {
        dao.deleteDraft()
    }

    suspend fun recordFeedbackSample(input: PersonalExpressionFeedbackInput): Boolean {
        val profile = getProfile()
        if (profile?.autoLearningEnabled != true || input.copiedText.isBlank()) return false
        dao.insertFeedbackSample(
            PersonalExpressionFeedbackSampleEntity(
                id = UUID.randomUUID().toString(),
                source = input.source,
                relation = input.relation,
                style = input.style,
                scene = input.scene,
                suggestionSlot = input.suggestionSlot,
                copiedText = input.copiedText.trim(),
                sourceMessage = input.sourceMessage.trim().take(280),
                profileVersion = profile.version
            )
        )
        return true
    }

    suspend fun countUnconsumedSamples(): Int = dao.countUnconsumedSamples()

    suspend fun shouldCreateUpdateProposal(): Boolean {
        val profile = getProfile() ?: return false
        if (!profile.autoLearningEnabled || !profile.questionnaireCompleted) return false
        if (dao.getPendingProposal() != null) return false
        return countUnconsumedSamples() >= profile.learningThreshold.sampleCount
    }

    suspend fun getUnconsumedSamples(limit: Int = 60): List<PersonalExpressionFeedbackSample> {
        return dao.getUnconsumedSamples(limit).map { it.toSample() }
    }

    private suspend fun saveUpdateProposal(
        suggestion: PersonalExpressionUpdateSuggestion,
        sampleIds: List<String>
    ): PersonalExpressionUpdateProposal {
        val proposal = PersonalExpressionUpdateProposal(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            observations = suggestion.observations,
            changes = suggestion.changes,
            previewBefore = suggestion.previewBefore,
            previewAfter = suggestion.previewAfter,
            updatedProfile = suggestion.updatedProfile,
            sampleIds = sampleIds
        )
        dao.upsertProposal(proposal.toEntity())
        return proposal
    }

    suspend fun createLocalUpdateProposal(): PersonalExpressionUpdateProposal? {
        return createLocalUpdateProposalWithLlmResult(null)
    }

    suspend fun createLocalUpdateProposalWithLlmResult(
        llmSuggestion: PersonalExpressionUpdateSuggestion?
    ): PersonalExpressionUpdateProposal? = proposalMutex.withLock {
        val profile = getProfile() ?: return@withLock null
        if (dao.getPendingProposal() != null) return@withLock null

        val threshold = profile.learningThreshold.sampleCount
        val samples = getUnconsumedSamples(limit = 60)
        if (samples.size < threshold) return@withLock null

        val suggestion = llmSuggestion ?: buildLocalSuggestion(profile, samples)
        val suggestionWithCurrentThreshold = suggestion.copy(
            updatedProfile = suggestion.updatedProfile.copy(
                learningThreshold = profile.learningThreshold
            )
        )
        saveUpdateProposal(suggestionWithCurrentThreshold, samples.map { it.id })
    }

    private fun buildLocalSuggestion(
        profile: PersonalExpressionProfile,
        samples: List<PersonalExpressionFeedbackSample>
    ): PersonalExpressionUpdateSuggestion {
        val avgLength = samples.map { it.copiedText.length }.average()

        val emojiRanges = listOf(
            0x1F600..0x1F64F, 0x1F300..0x1F5FF, 0x1F680..0x1F6FF,
            0x2600..0x26FF, 0x2700..0x27BF, 0x1F900..0x1F9FF, 0x1FA70..0x1FAFF
        )
        val samplesWithEmoji = samples.count { sample ->
            var hasEmoji = false
            var i = 0
            val text = sample.copiedText
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                if (emojiRanges.any { range -> codePoint in range }) { hasEmoji = true; break }
                i += Character.charCount(codePoint)
            }
            hasEmoji
        }
        val emojiRate = samplesWithEmoji.toFloat() / samples.size
        val newEmojiUsage = when {
            emojiRate == 0f -> EmojiUsage.NONE
            emojiRate <= 0.2f -> EmojiUsage.LOW
            emojiRate <= 0.6f -> EmojiUsage.MEDIUM
            else -> EmojiUsage.HIGH
        }

        val fillers = listOf("哈", "呀", "啦", "呢", "嗷", "嘛")
        val samplesWithFiller = samples.count { sample ->
            fillers.any { filler -> sample.copiedText.contains(filler) }
        }
        val fillerRate = samplesWithFiller.toFloat() / samples.size
        val fillerAdjustment = when {
            fillerRate >= 0.8f -> 0.3f
            fillerRate == 0f -> -0.2f
            else -> 0f
        }

        val updated = profile.copy(
            coreStyle = profile.coreStyle.copy(
                sentenceLength = clampStyleValue(
                    when {
                        avgLength < 18 -> profile.coreStyle.sentenceLength - 0.3f
                        avgLength > 60 -> profile.coreStyle.sentenceLength + 0.3f
                        else -> profile.coreStyle.sentenceLength
                    }
                )
            ),
            languageMarkers = profile.languageMarkers.copy(
                fillerWords = clampStyleValue(profile.languageMarkers.fillerWords + fillerAdjustment),
                emojiUsage = newEmojiUsage
            ),
            version = profile.version + 1,
            lastUpdatedAt = System.currentTimeMillis()
        )

        val baseComfort = profile.sampleReplies["comfort"].orEmpty()
        fun formatPreviewReply(baseText: String, emojiUsage: EmojiUsage, fillerWords: Float): String {
            var text = baseText
            if (fillerWords > 3.8f && !text.endsWith("？") && !text.endsWith("！")) {
                text = text.removeSuffix("。") + "呀。"
            }
            text = when (emojiUsage) {
                EmojiUsage.NONE -> text
                EmojiUsage.LOW -> text.removeSuffix("。") + "😊"
                EmojiUsage.MEDIUM, EmojiUsage.HIGH -> text.removeSuffix("。") + "😊✨"
            }
            return text
        }
        val previewBefore = formatPreviewReply(baseComfort, profile.languageMarkers.emojiUsage, profile.languageMarkers.fillerWords)
        val previewAfter = formatPreviewReply(baseComfort, updated.languageMarkers.emojiUsage, updated.languageMarkers.fillerWords)

        return PersonalExpressionUpdateSuggestion(
            observations = listOf(
                "最近复制了 ${samples.size} 条回复建议。",
                if (avgLength < 18) "你更常采用短句。" else if (avgLength > 60) "你更常采用较完整的解释。" else "你的回复长度保持在中等范围。",
                if (samplesWithFiller > 0) "复制内容里出现了一些语气词。" else "复制内容整体较少使用语气词。"
            ),
            changes = listOf("微调句子长度倾向。", "微调 emoji 和语气词使用倾向。"),
            previewBefore = previewBefore,
            previewAfter = previewAfter,
            updatedProfile = updated
        )
    }

    suspend fun applyPendingProposal(): Boolean {
        val proposal = dao.getPendingProposal()?.toProposalOrNull() ?: return false
        val oldProfile = getProfile()
        if (oldProfile != null) saveVersion(oldProfile, "应用学习建议前")
        saveProfile(
            proposal.updatedProfile.copy(
                enabled = oldProfile?.enabled ?: proposal.updatedProfile.enabled,
                autoLearningEnabled = oldProfile?.autoLearningEnabled ?: proposal.updatedProfile.autoLearningEnabled,
                learningThreshold = oldProfile?.learningThreshold ?: proposal.updatedProfile.learningThreshold,
                version = (oldProfile?.version ?: proposal.updatedProfile.version) + 1,
                lastUpdatedAt = System.currentTimeMillis()
            ),
            reason = "学习建议更新"
        )
        dao.markSamplesConsumed(proposal.sampleIds, proposal.id)
        dao.setProposalStatus(proposal.id, "applied")
        return true
    }

    suspend fun ignorePendingProposal(): Boolean {
        val proposal = dao.getPendingProposal()?.toProposalOrNull() ?: return false
        dao.markSamplesConsumed(proposal.sampleIds, proposal.id)
        dao.setProposalStatus(proposal.id, "ignored")
        return true
    }

    suspend fun restoreVersion(versionId: String): Boolean {
        val version = dao.getVersion(versionId)?.toVersionOrNull() ?: return false
        getProfile()?.let { saveVersion(it, "版本回退前") }
        saveProfile(
            version.profile.copy(
                version = (getProfile()?.version ?: version.profile.version) + 1,
                lastUpdatedAt = System.currentTimeMillis()
            ),
            reason = "恢复版本"
        )
        return true
    }

    suspend fun deleteAll() {
        dao.deleteProfile()
        dao.deleteDraft()
        dao.clearFeedbackSamples()
        dao.clearVersions()
        dao.clearProposals()
    }

    private suspend fun saveProfile(profile: PersonalExpressionProfile, reason: String) {
        dao.upsertProfile(
            PersonalExpressionProfileEntity(
                enabled = profile.enabled,
                autoLearningEnabled = profile.autoLearningEnabled,
                questionnaireCompleted = profile.questionnaireCompleted,
                profileJson = json.encodeToString(profile),
                version = profile.version,
                lastUpdatedAt = profile.lastUpdatedAt
            )
        )
        if (reason == "问卷生成" || reason == "默认档案") {
            dao.insertVersion(
                PersonalExpressionVersionEntity(
                    id = UUID.randomUUID().toString(),
                    version = profile.version,
                    reason = reason,
                    profileJson = json.encodeToString(profile)
                )
            )
        }
    }

    private suspend fun saveVersion(profile: PersonalExpressionProfile, reason: String) {
        dao.insertVersion(
            PersonalExpressionVersionEntity(
                id = UUID.randomUUID().toString(),
                version = profile.version,
                reason = reason,
                profileJson = json.encodeToString(profile)
            )
        )
    }

    private fun PersonalExpressionProfileEntity.toProfileOrNull(): PersonalExpressionProfile? {
        return runCatching {
            json.decodeFromString<PersonalExpressionProfile>(profileJson).copy(
                enabled = enabled,
                autoLearningEnabled = autoLearningEnabled,
                questionnaireCompleted = questionnaireCompleted,
                version = version,
                lastUpdatedAt = lastUpdatedAt
            )
        }.getOrNull()
    }

    private fun PersonalExpressionVersionEntity.toVersionOrNull(): PersonalExpressionVersion? {
        return runCatching {
            PersonalExpressionVersion(
                id = id,
                createdAt = createdAt,
                version = version,
                reason = reason,
                profile = json.decodeFromString(profileJson)
            )
        }.getOrNull()
    }

    private fun PersonalExpressionFeedbackSampleEntity.toSample(): PersonalExpressionFeedbackSample {
        return PersonalExpressionFeedbackSample(
            id = id,
            createdAt = createdAt,
            source = source,
            relation = relation,
            style = style,
            scene = scene,
            suggestionSlot = suggestionSlot,
            copiedText = copiedText,
            sourceMessage = sourceMessage,
            profileVersion = profileVersion
        )
    }

    private fun PersonalExpressionUpdateProposalEntity.toProposalOrNull(): PersonalExpressionUpdateProposal? {
        return runCatching {
            PersonalExpressionUpdateProposal(
                id = id,
                createdAt = createdAt,
                observations = json.decodeFromString(observationsJson),
                changes = json.decodeFromString(changesJson),
                previewBefore = previewBefore,
                previewAfter = previewAfter,
                updatedProfile = json.decodeFromString(updatedProfileJson),
                sampleIds = json.decodeFromString(sampleIdsJson)
            )
        }.getOrNull()
    }

    private fun PersonalExpressionUpdateProposal.toEntity(): PersonalExpressionUpdateProposalEntity {
        return PersonalExpressionUpdateProposalEntity(
            id = id,
            createdAt = createdAt,
            observationsJson = json.encodeToString(observations),
            changesJson = json.encodeToString(changes),
            previewBefore = previewBefore,
            previewAfter = previewAfter,
            updatedProfileJson = json.encodeToString(updatedProfile),
            sampleIdsJson = json.encodeToString(sampleIds)
        )
    }
}

data class PersonalExpressionFeedbackInput(
    val source: String,
    val relation: String,
    val style: String,
    val scene: String,
    val suggestionSlot: String,
    val copiedText: String,
    val sourceMessage: String
)

data class PersonalExpressionFeedbackSample(
    val id: String,
    val createdAt: Long,
    val source: String,
    val relation: String,
    val style: String,
    val scene: String,
    val suggestionSlot: String,
    val copiedText: String,
    val sourceMessage: String,
    val profileVersion: Int?
)

data class PersonalExpressionVersion(
    val id: String,
    val createdAt: Long,
    val version: Int,
    val reason: String,
    val profile: PersonalExpressionProfile
)

data class PersonalExpressionUpdateProposal(
    val id: String,
    val createdAt: Long,
    val observations: List<String>,
    val changes: List<String>,
    val previewBefore: String,
    val previewAfter: String,
    val updatedProfile: PersonalExpressionProfile,
    val sampleIds: List<String>
)
