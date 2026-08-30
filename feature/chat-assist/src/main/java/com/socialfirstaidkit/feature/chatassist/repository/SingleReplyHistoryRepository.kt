package com.socialfirstaidkit.feature.chatassist.repository

import android.content.Context
import com.socialfirstaidkit.core.llm.model.ReplySuggestions
import com.socialfirstaidkit.feature.chatassist.model.SingleReplyHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SingleReplyHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val historyDir: File by lazy {
        File(context.filesDir, "single_reply_history").apply { mkdirs() }
    }

    suspend fun getHistory(): List<SingleReplyHistory> = withContext(Dispatchers.IO) {
        historyDir.listFiles { _, name -> name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<SingleReplyHistory>(file.readText())
                }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    suspend fun saveHistory(
        sourceMessage: String,
        suggestions: ReplySuggestions
    ): SingleReplyHistory? = withContext(Dispatchers.IO) {
        if (sourceMessage.isBlank()) return@withContext null

        val history = SingleReplyHistory(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            sourceMessage = sourceMessage.trim(),
            formal = suggestions.formal,
            casual = suggestions.casual,
            humorous = suggestions.humorous
        )
        File(historyDir, "${history.id}.json").writeText(json.encodeToString(history))
        history
    }

    suspend fun deleteHistory(id: String) = withContext(Dispatchers.IO) {
        File(historyDir, "$id.json").delete()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { it.delete() }
    }
}
