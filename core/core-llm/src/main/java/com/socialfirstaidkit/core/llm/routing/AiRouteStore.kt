package com.socialfirstaidkit.core.llm.routing

import android.util.Log
import com.socialfirstaidkit.core.data.local.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class AiRouteStore @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        ensureDefaultRoutes()
    }

    fun routeKey(slot: AiFunctionSlot): String = "$KEY_PREFIX${slot.key}"

    fun ensureDefaultRoutes() {
        AiFunctionSlot.entries.forEach { slot ->
            getRoute(slot)
        }
    }

    fun getRoute(slot: AiFunctionSlot): AiModelRoute {
        val key = routeKey(slot)
        val storedRoute = secureStorage.getString(key)?.let(::decodeRoute)
        if (storedRoute != null && AiModelRegistry.isRouteCompatible(slot, storedRoute)) {
            if (shouldMigrateStoredDefault(slot, storedRoute)) {
                val newDefault = AiModelRegistry.defaultRoute(slot)
                saveRouteInternal(slot, newDefault)
                return newDefault
            }
            return storedRoute
        }

        val route = if (storedRoute == null) {
            migrateLegacyRoute(slot) ?: AiModelRegistry.defaultRoute(slot)
        } else {
            AiModelRegistry.defaultRoute(slot)
        }
        saveRouteInternal(slot, route)
        return route
    }

    fun getAllRoutes(): Map<AiFunctionSlot, AiModelRoute> =
        AiFunctionSlot.entries.associateWith { getRoute(it) }

    fun saveRoute(slot: AiFunctionSlot, route: AiModelRoute): Boolean {
        if (!AiModelRegistry.isRouteCompatible(slot, route)) {
            Log.w(TAG, "Reject incompatible route slot=${slot.key} route=$route")
            return false
        }
        saveRouteInternal(slot, route)
        return true
    }

    fun saveRouteByModelId(slot: AiFunctionSlot, modelId: String): Boolean {
        val spec = AiModelRegistry.findModel(modelId) ?: return false
        return saveRoute(slot, spec.toRoute())
    }

    fun resetRoute(slot: AiFunctionSlot) {
        saveRouteInternal(slot, AiModelRegistry.defaultRoute(slot))
    }

    fun isDefaultRoute(slot: AiFunctionSlot, route: AiModelRoute = getRoute(slot)): Boolean {
        return route == AiModelRegistry.defaultRoute(slot)
    }

    private fun saveRouteInternal(slot: AiFunctionSlot, route: AiModelRoute) {
        secureStorage.saveString(routeKey(slot), json.encodeToString(route))
    }

    private fun decodeRoute(value: String): AiModelRoute? {
        return runCatching { json.decodeFromString<AiModelRoute>(value) }
            .onFailure { Log.w(TAG, "Failed to decode ai route", it) }
            .getOrNull()
    }

    private fun shouldMigrateStoredDefault(slot: AiFunctionSlot, route: AiModelRoute): Boolean {
        return when (slot) {
            AiFunctionSlot.CHAT_SCREENSHOT_OCR ->
                route.provider == AiProviderId.VIVO &&
                    route.modelId == AiModelRegistry.VIVO_OCR_ROTATED_ID
            AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING ->
                route.provider == AiProviderId.MIMO &&
                    route.modelId == AiModelRegistry.MIMO_VISION_ID
            AiFunctionSlot.MOMENT_COPY_GENERATION ->
                route.provider == AiProviderId.VIVO &&
                    route.modelId == AiModelRegistry.VIVO_DEEPSEEK_ID
            else -> false
        }
    }

    private fun migrateLegacyRoute(slot: AiFunctionSlot): AiModelRoute? {
        val legacyKey = when (slot) {
            AiFunctionSlot.CHAT_SCREENSHOT_OCR -> LEGACY_OCR_KEY
            AiFunctionSlot.CHAT_SCREENSHOT_DIALOGUE_ANALYSIS,
            AiFunctionSlot.CHAT_SINGLE_CONTEXT_ANALYSIS,
            AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION,
            AiFunctionSlot.CHAT_CONTEXTUAL_REPLY_GENERATION,
            AiFunctionSlot.CHAT_IDENTITY_STYLE_DESCRIPTION,
            AiFunctionSlot.PROFILE_EXPRESSION_UPDATE_SUMMARY -> LEGACY_CHAT_KEY
            AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING,
            AiFunctionSlot.MOMENT_COPY_GENERATION,
            AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION,
            AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION -> LEGACY_MOMENT_KEY
            AiFunctionSlot.VOICE_SHORT_RECOGNITION -> LEGACY_VOICE_KEY
        }
        val legacyProvider = secureStorage.getString(legacyKey)?.uppercase() ?: return null
        val modelId = modelForLegacyProvider(legacyProvider, slot) ?: return null
        return AiModelRegistry.findModel(modelId)?.toRoute()
    }

    private fun modelForLegacyProvider(provider: String, slot: AiFunctionSlot): String? {
        return when (provider) {
            "MIMO" -> when (slot.requiredCapability) {
                AiCapability.OCR -> AiModelRegistry.MIMO_VISION_ID
                AiCapability.TEXT_CHAT -> AiModelRegistry.MIMO_TEXT_ID
                AiCapability.VISION_CHAT -> AiModelRegistry.MIMO_VISION_ID
                AiCapability.ASR -> AiModelRegistry.MIMO_ASR_ID
                else -> null
            }
            "GEMINI" -> when (slot.requiredCapability) {
                AiCapability.TEXT_CHAT,
                AiCapability.VISION_CHAT -> AiModelRegistry.GEMINI_FLASH_ID
                else -> null
            }
            "WENXIN" -> textOnlyModel(slot, AiModelRegistry.WENXIN_TEXT_ID)
            "LANXIN" -> textOnlyModel(slot, AiModelRegistry.LANXIN_TEXT_ID)
            "TONGYI" -> textOnlyModel(slot, AiModelRegistry.TONGYI_TEXT_ID)
            "AGNES" -> when (slot.requiredCapability) {
                AiCapability.TEXT_CHAT,
                AiCapability.VISION_CHAT -> AiModelRegistry.AGNES_2_FLASH_ID
                AiCapability.IMAGE_GENERATION -> AiModelRegistry.AGNES_IMAGE_2_FLASH_ID
                else -> null
            }
            "CUSTOM", "CUSTOM_OPENAI" -> when (slot.requiredCapability) {
                AiCapability.TEXT_CHAT,
                AiCapability.VISION_CHAT -> AiModelRegistry.CUSTOM_OPENAI_TEXT_ID
                else -> null
            }
            "BACKUP_OPENAI", "VIVO" -> null
            else -> null
        }
    }

    private fun textOnlyModel(slot: AiFunctionSlot, modelId: String): String? {
        return if (slot.requiredCapability == AiCapability.TEXT_CHAT) modelId else null
    }

    companion object {
        private const val TAG = "AiRouteStore"
        private const val KEY_PREFIX = "ai_route_"
        private const val LEGACY_CHAT_KEY = "llm_function_chat"
        private const val LEGACY_MOMENT_KEY = "llm_function_moment"
        private const val LEGACY_VOICE_KEY = "llm_function_meeting"
        private const val LEGACY_OCR_KEY = "llm_function_ocr"
    }
}
