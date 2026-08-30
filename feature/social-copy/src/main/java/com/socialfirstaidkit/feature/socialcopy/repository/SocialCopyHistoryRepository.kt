package com.socialfirstaidkit.feature.socialcopy.repository

import android.content.Context
import com.socialfirstaidkit.core.llm.model.MomentCopySuggestion
import com.socialfirstaidkit.core.llm.model.MomentImageAnalysis
import com.socialfirstaidkit.feature.socialcopy.model.SocialCopyHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class SocialCopyHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val historyDir: File by lazy {
        File(context.filesDir, "social_copy_history").apply { mkdirs() }
    }

    suspend fun getHistory(): List<SocialCopyHistory> = withContext(Dispatchers.IO) {
        historyDir.listFiles { _, name -> name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<SocialCopyHistory>(file.readText())
                }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    suspend fun saveHistory(
        isTextOnly: Boolean,
        imageUriString: String?,
        imageDescription: String,
        analysis: MomentImageAnalysis,
        styleName: String,
        styleDescription: String,
        additionalInfo: String?,
        suggestions: List<MomentCopySuggestion>
    ): SocialCopyHistory? = withContext(Dispatchers.IO) {
        val usableSuggestions = suggestions.filter { it.content.isNotBlank() }
        if (usableSuggestions.isEmpty()) return@withContext null

        val history = SocialCopyHistory(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            isTextOnly = isTextOnly,
            imageUriString = imageUriString,
            imageDescription = imageDescription,
            analysis = analysis,
            styleName = styleName,
            styleDescription = styleDescription,
            additionalInfo = additionalInfo?.trim()?.takeIf { it.isNotBlank() },
            suggestions = usableSuggestions
        )
        File(historyDir, "${history.id}.json").writeText(json.encodeToString(history))
        history
    }

    suspend fun updateHistoryGeneratedImageUrl(id: String, generatedImageUrl: String) = withContext(Dispatchers.IO) {
        val file = File(historyDir, "$id.json")
        if (file.exists()) {
            runCatching {
                val current = json.decodeFromString<SocialCopyHistory>(file.readText())
                val updated = current.copy(generatedImageUrl = generatedImageUrl)
                file.writeText(json.encodeToString(updated))
            }
        }
    }

    suspend fun deleteHistory(id: String) = withContext(Dispatchers.IO) {
        File(historyDir, "$id.json").delete()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { it.delete() }
    }
}
