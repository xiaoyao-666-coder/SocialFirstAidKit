package com.socialfirstaidkit.feature.chatassist.repository

import android.content.Context
import com.socialfirstaidkit.feature.chatassist.model.OcrHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val historyDir: File by lazy {
        File(context.filesDir, "ocr_history").apply { mkdirs() }
    }

    suspend fun getHistory(maxSize: Int): List<OcrHistory> = withContext(Dispatchers.IO) {
        historyDir.listFiles { _, name -> name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<OcrHistory>(file.readText())
                }.getOrNull()
            }
            ?.sortedByDescending { it.timestamp }
            ?.take(maxSize)
            ?: emptyList()
    }

    suspend fun saveHistory(history: OcrHistory, maxSize: Int) = withContext(Dispatchers.IO) {
        File(historyDir, "${history.id}.json").writeText(json.encodeToString(history))
        trimHistory(maxSize)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { it.delete() }
    }

    private fun trimHistory(maxSize: Int) {
        historyDir.listFiles { _, name -> name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(maxSize)
            ?.forEach { it.delete() }
    }
}
