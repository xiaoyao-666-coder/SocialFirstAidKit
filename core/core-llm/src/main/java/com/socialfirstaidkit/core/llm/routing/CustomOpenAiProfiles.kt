package com.socialfirstaidkit.core.llm.routing

import com.socialfirstaidkit.core.data.local.SecureStorage
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CustomOpenAiProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    val displayName: String
        get() = name.ifBlank { model.ifBlank { "Custom OpenAI" } }
}

object CustomOpenAiProfiles {
    const val STORAGE_KEY = "custom_openai_profiles_v1"
    const val ROUTE_MODEL_PREFIX = "custom.openai.profile."
    private const val LEGACY_PROFILE_ID = "legacy"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun modelId(profileId: String): String = "$ROUTE_MODEL_PREFIX$profileId"

    fun profileIdFromModelId(modelId: String): String? =
        modelId.takeIf { it.startsWith(ROUTE_MODEL_PREFIX) }?.removePrefix(ROUTE_MODEL_PREFIX)

    fun encodeRoutedModel(modelId: String, modelName: String): String {
        val profileId = profileIdFromModelId(modelId) ?: return modelName
        return "custom-profile:$profileId::$modelName"
    }

    fun decodeRoutedModel(encoded: String): Pair<String?, String> {
        if (!encoded.startsWith("custom-profile:")) return null to encoded
        val payload = encoded.removePrefix("custom-profile:")
        val splitAt = payload.indexOf("::")
        if (splitAt <= 0) return null to encoded
        val profileId = payload.substring(0, splitAt)
        val model = payload.substring(splitAt + 2)
        return profileId to model
    }

    fun load(storage: SecureStorage): List<CustomOpenAiProfile> {
        val storedProfiles = loadStoredProfiles(storage)
        val realProfiles = storedProfiles.filterNot { it.id == LEGACY_PROFILE_ID }
        if (realProfiles.isNotEmpty()) {
            return realProfiles
        }
        if (storedProfiles.isNotEmpty()) {
            return storedProfiles
        }
        return legacyProfile(storage)?.let(::listOf).orEmpty()
    }

    fun saveAll(storage: SecureStorage, profiles: List<CustomOpenAiProfile>) {
        storage.saveString(
            STORAGE_KEY,
            json.encodeToString(profiles.filterNot { it.id == LEGACY_PROFILE_ID }.distinctBy { it.id })
        )
    }

    private fun loadStoredProfiles(storage: SecureStorage): List<CustomOpenAiProfile> {
        return storage.getString(STORAGE_KEY)
            ?.let { value -> runCatching { json.decodeFromString(ListSerializer(CustomOpenAiProfile.serializer()), value) }.getOrNull() }
            .orEmpty()
            .filter { it.baseUrl.isNotBlank() && it.model.isNotBlank() }
            .distinctBy { it.id }
    }

    fun add(storage: SecureStorage, name: String, baseUrl: String, apiKey: String, model: String): CustomOpenAiProfile {
        val profiles = loadStoredProfiles(storage).filterNot { it.id == LEGACY_PROFILE_ID }.toMutableList()
        val profile = buildProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model
        )
        profiles += profile
        saveAll(storage, profiles)
        return profile
    }

    fun update(
        storage: SecureStorage,
        profileId: String,
        name: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ): CustomOpenAiProfile {
        val profiles = loadStoredProfiles(storage).filterNot { it.id == LEGACY_PROFILE_ID }.toMutableList()
        val existingIndex = profiles.indexOfFirst { it.id == profileId }
        val profile = buildProfile(
            id = if (existingIndex >= 0) profiles[existingIndex].id else profileId.ifBlank { UUID.randomUUID().toString() },
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model
        )
        if (existingIndex >= 0) profiles[existingIndex] = profile else profiles += profile
        saveAll(storage, profiles)
        return profile
    }

    fun upsert(storage: SecureStorage, name: String, baseUrl: String, apiKey: String, model: String): CustomOpenAiProfile {
        return add(storage, name, baseUrl, apiKey, model)
    }

    private fun buildProfile(
        id: String,
        name: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ): CustomOpenAiProfile {
        val trimmedName = name.trim()
        val trimmedModel = model.trim()
        return CustomOpenAiProfile(
            id = id,
            name = trimmedName.ifBlank { trimmedModel },
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = trimmedModel
        )
    }

    fun find(storage: SecureStorage, profileId: String?): CustomOpenAiProfile? {
        if (profileId.isNullOrBlank() || profileId == LEGACY_PROFILE_ID) return legacyProfile(storage)
        return load(storage).firstOrNull { it.id == profileId }
    }

    private fun legacyProfile(storage: SecureStorage): CustomOpenAiProfile? {
        val name = storage.getString("custom_llm_name").orEmpty().trim()
        val baseUrl = storage.getString("custom_llm_base_url").orEmpty().trim()
        val apiKey = storage.getString("custom_llm_api_key").orEmpty().trim()
        val model = storage.getString("custom_llm_default_model").orEmpty().trim()
        if (baseUrl.isBlank() && apiKey.isBlank() && model.isBlank()) return null
        return CustomOpenAiProfile(
            id = LEGACY_PROFILE_ID,
            name = name.ifBlank { "自定义 OpenAI" },
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model
        )
    }
}