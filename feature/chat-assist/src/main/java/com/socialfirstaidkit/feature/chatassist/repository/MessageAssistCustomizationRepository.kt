package com.socialfirstaidkit.feature.chatassist.repository

import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.feature.chatassist.model.CustomMessageAssistOption
import com.socialfirstaidkit.feature.chatassist.model.defaultRelationManagedOptions
import com.socialfirstaidkit.feature.chatassist.model.defaultStyleManagedOptions
import com.socialfirstaidkit.feature.chatassist.model.sortedForDisplay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageAssistCustomizationRepository @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun getRelationOptions(): List<CustomMessageAssistOption> {
        return readOptions(RELATION_OPTIONS_KEY, defaultRelationManagedOptions())
    }

    fun saveRelationOptions(options: List<CustomMessageAssistOption>) {
        writeOptions(RELATION_OPTIONS_KEY, normalizeOrder(options))
    }

    fun getDeletedRelationOptions(): List<CustomMessageAssistOption> {
        return readDeletedOptions(DELETED_RELATION_OPTIONS_KEY)
    }

    fun saveDeletedRelationOptions(options: List<CustomMessageAssistOption>) {
        writeOptions(DELETED_RELATION_OPTIONS_KEY, options.sortedByDescending { it.createdAt })
    }

    fun getStyleOptions(): List<CustomMessageAssistOption> {
        return readOptions(STYLE_OPTIONS_KEY, defaultStyleManagedOptions())
    }

    fun saveStyleOptions(options: List<CustomMessageAssistOption>) {
        writeOptions(STYLE_OPTIONS_KEY, normalizeOrder(options))
    }

    fun getDeletedStyleOptions(): List<CustomMessageAssistOption> {
        return readDeletedOptions(DELETED_STYLE_OPTIONS_KEY)
    }

    fun saveDeletedStyleOptions(options: List<CustomMessageAssistOption>) {
        writeOptions(DELETED_STYLE_OPTIONS_KEY, options.sortedByDescending { it.createdAt })
    }

    private fun readOptions(
        key: String,
        defaultOptions: List<CustomMessageAssistOption>
    ): List<CustomMessageAssistOption> {
        val rawValue = secureStorage.getString(key).orEmpty()
        if (rawValue.isBlank()) {
            val seededOptions = normalizeOrder(defaultOptions)
            writeOptions(key, seededOptions)
            return seededOptions
        }

        val decoded = runCatching {
            json.decodeFromString<List<CustomMessageAssistOption>>(rawValue)
        }.getOrElse {
            emptyList()
        }

        if (decoded.isEmpty()) {
            val seededOptions = normalizeOrder(defaultOptions)
            writeOptions(key, seededOptions)
            return seededOptions
        }

        // Merge stored options with default presets
        val presetById = defaultOptions.associateBy { it.id }
        val seenIds = mutableSetOf<String>()
        val merged = mutableListOf<CustomMessageAssistOption>()

        decoded.sortedForDisplay().forEach { stored ->
            val preset = presetById[stored.id]
            val option = when {
                preset != null -> preset.copy(order = stored.order, createdAt = stored.createdAt)
                !stored.isSystemPreset -> stored
                else -> null
            }
            if (option != null && seenIds.add(option.id)) {
                merged += option
            }
        }

        defaultOptions.forEach { preset ->
            if (seenIds.add(preset.id)) {
                merged += preset.copy(order = merged.size)
            }
        }

        val finalOptions = normalizeOrder(merged)
        writeOptions(key, finalOptions)
        return finalOptions
    }

    private fun writeOptions(key: String, options: List<CustomMessageAssistOption>) {
        secureStorage.saveString(key, json.encodeToString(options))
    }

    private fun readDeletedOptions(key: String): List<CustomMessageAssistOption> {
        val rawValue = secureStorage.getString(key).orEmpty()
        if (rawValue.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CustomMessageAssistOption>>(rawValue)
        }.getOrElse { emptyList() }
    }

    private fun normalizeOrder(options: List<CustomMessageAssistOption>): List<CustomMessageAssistOption> {
        return options.sortedForDisplay().mapIndexed { index, option ->
            option.copy(order = index)
        }
    }

    private companion object {
        const val RELATION_OPTIONS_KEY = "message_assist_relation_options"
        const val STYLE_OPTIONS_KEY = "message_assist_style_options"
        const val DELETED_RELATION_OPTIONS_KEY = "message_assist_deleted_relation_options"
        const val DELETED_STYLE_OPTIONS_KEY = "message_assist_deleted_style_options"
    }
}
