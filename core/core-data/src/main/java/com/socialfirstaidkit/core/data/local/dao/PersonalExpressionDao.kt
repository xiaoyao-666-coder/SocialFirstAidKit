package com.socialfirstaidkit.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionFeedbackSampleEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionProfileEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionQuestionnaireDraftEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionUpdateProposalEntity
import com.socialfirstaidkit.core.data.local.entity.PersonalExpressionVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalExpressionDao {
    @Query("SELECT * FROM personal_expression_profile WHERE id = 1")
    fun observeProfile(): Flow<PersonalExpressionProfileEntity?>

    @Query("SELECT * FROM personal_expression_profile WHERE id = 1")
    suspend fun getProfile(): PersonalExpressionProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: PersonalExpressionProfileEntity)

    @Query("UPDATE personal_expression_profile SET enabled = :enabled, lastUpdatedAt = :timestamp WHERE id = 1")
    suspend fun setProfileEnabled(enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE personal_expression_profile SET autoLearningEnabled = :enabled, lastUpdatedAt = :timestamp WHERE id = 1")
    suspend fun setAutoLearningEnabled(enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM personal_expression_profile WHERE id = 1")
    suspend fun deleteProfile()

    @Query("SELECT * FROM personal_expression_questionnaire_draft WHERE id = 1")
    suspend fun getDraft(): PersonalExpressionQuestionnaireDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: PersonalExpressionQuestionnaireDraftEntity)

    @Query("DELETE FROM personal_expression_questionnaire_draft WHERE id = 1")
    suspend fun deleteDraft()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedbackSample(sample: PersonalExpressionFeedbackSampleEntity)

    @Query("SELECT * FROM personal_expression_feedback_sample WHERE consumedByProposalId IS NULL ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getUnconsumedSamples(limit: Int = 80): List<PersonalExpressionFeedbackSampleEntity>

    @Query("SELECT COUNT(*) FROM personal_expression_feedback_sample WHERE consumedByProposalId IS NULL")
    suspend fun countUnconsumedSamples(): Int

    @Query("UPDATE personal_expression_feedback_sample SET consumedByProposalId = :proposalId WHERE id IN (:sampleIds)")
    suspend fun markSamplesConsumed(sampleIds: List<String>, proposalId: String)

    @Query("DELETE FROM personal_expression_feedback_sample")
    suspend fun clearFeedbackSamples()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: PersonalExpressionVersionEntity)

    @Query("SELECT * FROM personal_expression_version ORDER BY createdAt DESC")
    fun observeVersions(): Flow<List<PersonalExpressionVersionEntity>>

    @Query("SELECT * FROM personal_expression_version WHERE id = :id")
    suspend fun getVersion(id: String): PersonalExpressionVersionEntity?

    @Query("DELETE FROM personal_expression_version")
    suspend fun clearVersions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProposal(proposal: PersonalExpressionUpdateProposalEntity)

    @Query("SELECT * FROM personal_expression_update_proposal WHERE status = 'pending' ORDER BY createdAt DESC LIMIT 1")
    fun observePendingProposal(): Flow<PersonalExpressionUpdateProposalEntity?>

    @Query("SELECT * FROM personal_expression_update_proposal WHERE status = 'pending' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getPendingProposal(): PersonalExpressionUpdateProposalEntity?

    @Query("SELECT * FROM personal_expression_update_proposal WHERE id = :id")
    suspend fun getProposal(id: String): PersonalExpressionUpdateProposalEntity?

    @Query("UPDATE personal_expression_update_proposal SET status = :status WHERE id = :id")
    suspend fun setProposalStatus(id: String, status: String)

    @Query("DELETE FROM personal_expression_update_proposal")
    suspend fun clearProposals()
}
