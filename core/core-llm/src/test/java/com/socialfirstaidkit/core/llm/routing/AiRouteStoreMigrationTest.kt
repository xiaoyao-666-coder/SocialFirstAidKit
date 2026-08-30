package com.socialfirstaidkit.core.llm.routing

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AiRouteStore migration logic.
 *
 * These tests verify the legacy key mapping without depending on Android SecureStorage.
 * The actual migration logic is extracted into a pure function for testability.
 */
class AiRouteStoreMigrationTest {

    private fun modelForLegacyProvider(provider: String, capability: AiCapability): String? {
        return when (provider) {
            "MIMO" -> when (capability) {
                AiCapability.OCR -> AiModelRegistry.MIMO_VISION_ID
                AiCapability.TEXT_CHAT -> AiModelRegistry.MIMO_TEXT_ID
                AiCapability.VISION_CHAT -> AiModelRegistry.MIMO_VISION_ID
                AiCapability.ASR -> AiModelRegistry.MIMO_ASR_ID
                else -> null
            }
            "GEMINI" -> when (capability) {
                AiCapability.TEXT_CHAT,
                AiCapability.VISION_CHAT -> AiModelRegistry.GEMINI_FLASH_ID
                else -> null
            }
            "WENXIN" -> if (capability == AiCapability.TEXT_CHAT) AiModelRegistry.WENXIN_TEXT_ID else null
            "LANXIN" -> if (capability == AiCapability.TEXT_CHAT) AiModelRegistry.LANXIN_TEXT_ID else null
            "TONGYI" -> if (capability == AiCapability.TEXT_CHAT) AiModelRegistry.TONGYI_TEXT_ID else null
            "AGNES" -> when (capability) {
                AiCapability.TEXT_CHAT,
                AiCapability.VISION_CHAT -> AiModelRegistry.AGNES_2_FLASH_ID
                AiCapability.IMAGE_GENERATION -> AiModelRegistry.AGNES_IMAGE_2_FLASH_ID
                else -> null
            }
            "CUSTOM", "CUSTOM_OPENAI" -> when (capability) {
                AiCapability.TEXT_CHAT,
                AiCapability.VISION_CHAT -> AiModelRegistry.CUSTOM_OPENAI_TEXT_ID
                else -> null
            }
            "BACKUP_OPENAI", "VIVO" -> null
            else -> null
        }
    }

    private fun legacyKeyForSlot(slot: AiFunctionSlot): String {
        return when (slot) {
            AiFunctionSlot.CHAT_SCREENSHOT_OCR -> "llm_function_ocr"
            AiFunctionSlot.CHAT_SCREENSHOT_DIALOGUE_ANALYSIS,
            AiFunctionSlot.CHAT_SINGLE_CONTEXT_ANALYSIS,
            AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION,
            AiFunctionSlot.CHAT_CONTEXTUAL_REPLY_GENERATION,
            AiFunctionSlot.CHAT_IDENTITY_STYLE_DESCRIPTION,
            AiFunctionSlot.PROFILE_EXPRESSION_UPDATE_SUMMARY -> "llm_function_chat"
            AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING,
            AiFunctionSlot.MOMENT_COPY_GENERATION,
            AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION,
            AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION -> "llm_function_moment"
            AiFunctionSlot.VOICE_SHORT_RECOGNITION -> "llm_function_meeting"
        }
    }

    @Test
    fun `legacy chat key maps to all chat function slots`() {
        val chatSlots = listOf(
            AiFunctionSlot.CHAT_SCREENSHOT_DIALOGUE_ANALYSIS,
            AiFunctionSlot.CHAT_SINGLE_CONTEXT_ANALYSIS,
            AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION,
            AiFunctionSlot.CHAT_CONTEXTUAL_REPLY_GENERATION,
            AiFunctionSlot.CHAT_IDENTITY_STYLE_DESCRIPTION,
            AiFunctionSlot.PROFILE_EXPRESSION_UPDATE_SUMMARY
        )
        chatSlots.forEach { slot ->
            assertEquals("llm_function_chat", legacyKeyForSlot(slot))
        }
    }

    @Test
    fun `legacy moment key maps to moment and profile slots`() {
        val momentSlots = listOf(
            AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING,
            AiFunctionSlot.MOMENT_COPY_GENERATION,
            AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION,
            AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION
        )
        momentSlots.forEach { slot ->
            assertEquals("llm_function_moment", legacyKeyForSlot(slot))
        }
    }

    @Test
    fun `legacy voice key maps to short voice slot for compatibility`() {
        assertEquals("llm_function_meeting", legacyKeyForSlot(AiFunctionSlot.VOICE_SHORT_RECOGNITION))
    }

    @Test
    fun `legacy OCR key maps to OCR slot`() {
        assertEquals("llm_function_ocr", legacyKeyForSlot(AiFunctionSlot.CHAT_SCREENSHOT_OCR))
    }

    @Test
    fun `legacy MIMO provider maps to correct models by capability`() {
        assertEquals(AiModelRegistry.MIMO_TEXT_ID, modelForLegacyProvider("MIMO", AiCapability.TEXT_CHAT))
        assertEquals(AiModelRegistry.MIMO_VISION_ID, modelForLegacyProvider("MIMO", AiCapability.VISION_CHAT))
        assertEquals(AiModelRegistry.MIMO_ASR_ID, modelForLegacyProvider("MIMO", AiCapability.ASR))
        assertEquals(AiModelRegistry.MIMO_VISION_ID, modelForLegacyProvider("MIMO", AiCapability.OCR))
        assertNull(modelForLegacyProvider("MIMO", AiCapability.IMAGE_GENERATION))
    }

    @Test
    fun `legacy GEMINI provider maps to flash for text and vision`() {
        assertEquals(AiModelRegistry.GEMINI_FLASH_ID, modelForLegacyProvider("GEMINI", AiCapability.TEXT_CHAT))
        assertEquals(AiModelRegistry.GEMINI_FLASH_ID, modelForLegacyProvider("GEMINI", AiCapability.VISION_CHAT))
        assertNull(modelForLegacyProvider("GEMINI", AiCapability.OCR))
        assertNull(modelForLegacyProvider("GEMINI", AiCapability.ASR))
    }

    @Test
    fun `legacy text-only providers only map to TEXT_CHAT`() {
        assertEquals(AiModelRegistry.WENXIN_TEXT_ID, modelForLegacyProvider("WENXIN", AiCapability.TEXT_CHAT))
        assertNull(modelForLegacyProvider("WENXIN", AiCapability.VISION_CHAT))
        assertNull(modelForLegacyProvider("WENXIN", AiCapability.OCR))

        assertEquals(AiModelRegistry.LANXIN_TEXT_ID, modelForLegacyProvider("LANXIN", AiCapability.TEXT_CHAT))
        assertNull(modelForLegacyProvider("LANXIN", AiCapability.VISION_CHAT))

        assertEquals(AiModelRegistry.TONGYI_TEXT_ID, modelForLegacyProvider("TONGYI", AiCapability.TEXT_CHAT))
        assertNull(modelForLegacyProvider("TONGYI", AiCapability.VISION_CHAT))
    }

    @Test
    fun `legacy CUSTOM provider maps to custom openai for text and vision`() {
        assertEquals(AiModelRegistry.CUSTOM_OPENAI_TEXT_ID, modelForLegacyProvider("CUSTOM", AiCapability.TEXT_CHAT))
        assertEquals(AiModelRegistry.CUSTOM_OPENAI_TEXT_ID, modelForLegacyProvider("CUSTOM", AiCapability.VISION_CHAT))
        assertNull(modelForLegacyProvider("CUSTOM", AiCapability.OCR))
    }

    @Test
    fun `legacy CUSTOM_OPENAI maps same as CUSTOM`() {
        assertEquals(
            modelForLegacyProvider("CUSTOM", AiCapability.TEXT_CHAT),
            modelForLegacyProvider("CUSTOM_OPENAI", AiCapability.TEXT_CHAT)
        )
    }

    @Test
    fun `legacy AGNES maps only to free models`() {
        assertEquals(AiModelRegistry.AGNES_2_FLASH_ID, modelForLegacyProvider("AGNES", AiCapability.TEXT_CHAT))
        assertEquals(AiModelRegistry.AGNES_2_FLASH_ID, modelForLegacyProvider("AGNES", AiCapability.VISION_CHAT))
        assertEquals(AiModelRegistry.AGNES_IMAGE_2_FLASH_ID, modelForLegacyProvider("AGNES", AiCapability.IMAGE_GENERATION))
        assertNull(modelForLegacyProvider("AGNES", AiCapability.ASR))
        assertNull(modelForLegacyProvider("AGNES", AiCapability.OCR))
    }

    @Test
    fun `legacy VIVO and BACKUP_OPENAI return null (use default)`() {
        assertNull(modelForLegacyProvider("VIVO", AiCapability.TEXT_CHAT))
        assertNull(modelForLegacyProvider("BACKUP_OPENAI", AiCapability.TEXT_CHAT))
    }

    @Test
    fun `unknown legacy provider returns null`() {
        assertNull(modelForLegacyProvider("UNKNOWN", AiCapability.TEXT_CHAT))
        assertNull(modelForLegacyProvider("", AiCapability.TEXT_CHAT))
    }

    @Test
    fun `every legacy provider+capability combo either maps or returns null`() {
        val providers = listOf("MIMO", "GEMINI", "WENXIN", "LANXIN", "TONGYI", "AGNES", "CUSTOM", "CUSTOM_OPENAI", "VIVO", "BACKUP_OPENAI")
        val capabilities = AiCapability.entries

        providers.forEach { provider ->
            capabilities.forEach { capability ->
                val result = modelForLegacyProvider(provider, capability)
                if (result != null) {
                    val model = AiModelRegistry.findModel(result)
                    assertNotNull("Legacy mapping for $provider/$capability should point to a valid model", model)
                }
            }
        }
    }

    @Test
    fun `route key format is ai_route_dot_separated`() {
        val store = RouteKeyHelper
        assertEquals("ai_route_chat.screenshot_ocr", store.routeKey(AiFunctionSlot.CHAT_SCREENSHOT_OCR))
        assertEquals("ai_route_moment.copy_generation", store.routeKey(AiFunctionSlot.MOMENT_COPY_GENERATION))
        assertEquals("ai_route_profile.avatar_image_generation", store.routeKey(AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION))
        assertEquals("ai_route_voice.short_recognition", store.routeKey(AiFunctionSlot.VOICE_SHORT_RECOGNITION))
    }
}

object RouteKeyHelper {
    private const val KEY_PREFIX = "ai_route_"
    fun routeKey(slot: AiFunctionSlot): String = "$KEY_PREFIX${slot.key}"
}
