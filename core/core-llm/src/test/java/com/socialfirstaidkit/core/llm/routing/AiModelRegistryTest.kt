package com.socialfirstaidkit.core.llm.routing

import org.junit.Assert.*
import org.junit.Test

class AiModelRegistryTest {

    @Test
    fun `all AiProviderId values exist`() {
        val expected = setOf(
            AiProviderId.VIVO, AiProviderId.VIVO_ON_DEVICE, AiProviderId.MIMO,
            AiProviderId.GEMINI, AiProviderId.WENXIN, AiProviderId.LANXIN,
            AiProviderId.TONGYI, AiProviderId.AGNES, AiProviderId.CUSTOM_OPENAI
        )
        assertEquals(expected, AiProviderId.entries.toSet())
    }

    @Test
    fun `all AiCapability values exist`() {
        val expected = setOf(
            AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT, AiCapability.OCR,
            AiCapability.IMAGE_GENERATION, AiCapability.VIDEO_GENERATION,
            AiCapability.ASR, AiCapability.TTS
        )
        assertEquals(expected, AiCapability.entries.toSet())
    }

    @Test
    fun `models list is not empty`() {
        assertTrue(AiModelRegistry.models.isNotEmpty())
    }

    @Test
    fun `findModel returns correct model`() {
        val model = AiModelRegistry.findModel(AiModelRegistry.VIVO_DEEPSEEK_ID)
        assertNotNull(model)
        assertEquals(AiProviderId.VIVO, model!!.provider)
        assertEquals("Volc-DeepSeek-V3.2", model.modelName)
        assertTrue(model.recommended)
        assertTrue(AiCapability.TEXT_CHAT in model.capabilities)
        assertFalse(AiCapability.VISION_CHAT in model.capabilities)
    }

    @Test
    fun `findModel returns null for unknown model`() {
        assertNull(AiModelRegistry.findModel("nonexistent.model"))
    }

    @Test
    fun `modelsForProvider filters correctly`() {
        val vivoModels = AiModelRegistry.modelsForProvider(AiProviderId.VIVO)
        assertTrue(vivoModels.isNotEmpty())
        assertTrue(vivoModels.all { it.provider == AiProviderId.VIVO })

        val mimoModels = AiModelRegistry.modelsForProvider(AiProviderId.MIMO)
        assertTrue(mimoModels.isNotEmpty())
        assertTrue(mimoModels.all { it.provider == AiProviderId.MIMO })
    }

    @Test
    fun `modelsForCapability filters correctly`() {
        val textModels = AiModelRegistry.modelsForCapability(AiCapability.TEXT_CHAT)
        assertTrue(textModels.isNotEmpty())
        assertTrue(textModels.all { AiCapability.TEXT_CHAT in it.capabilities })

        val ocrModels = AiModelRegistry.modelsForCapability(AiCapability.OCR)
        assertEquals(1, ocrModels.size)
        assertEquals(AiModelRegistry.VIVO_OCR_ROTATED_ID, ocrModels[0].modelId)

        val imageModels = AiModelRegistry.modelsForCapability(AiCapability.IMAGE_GENERATION)
        assertTrue(imageModels.isNotEmpty())
        assertTrue(imageModels.all { AiCapability.IMAGE_GENERATION in it.capabilities })
    }

    @Test
    fun `Agnes registry only exposes free models`() {
        val agnesModels = AiModelRegistry.modelsForProvider(AiProviderId.AGNES)
        assertEquals(
            setOf(
                AiModelRegistry.AGNES_2_FLASH_ID,
                AiModelRegistry.AGNES_IMAGE_2_FLASH_ID,
                AiModelRegistry.AGNES_VIDEO_2_ID
            ),
            agnesModels.map { it.modelId }.toSet()
        )
        assertTrue(agnesModels.none { it.modelName == "agnes-1.5-flash" })
        assertTrue(agnesModels.none { it.modelName == "agnes-image-2.1-flash" })
    }

    @Test
    fun `modelsForSlot filters by required capability`() {
        val ocrSlotModels = AiModelRegistry.modelsForSlot(AiFunctionSlot.CHAT_SCREENSHOT_OCR)
        assertTrue(ocrSlotModels.isNotEmpty())
        assertTrue(ocrSlotModels.all { AiCapability.OCR in it.capabilities || AiCapability.VISION_CHAT in it.capabilities })

        val textSlotModels = AiModelRegistry.modelsForSlot(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION)
        assertTrue(textSlotModels.isNotEmpty())
        assertTrue(textSlotModels.all { AiCapability.TEXT_CHAT in it.capabilities })

        val imageGenModels = AiModelRegistry.modelsForSlot(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION)
        assertTrue(imageGenModels.isNotEmpty())
        assertTrue(imageGenModels.all { AiCapability.IMAGE_GENERATION in it.capabilities })

        val visionModels = AiModelRegistry.modelsForSlot(AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING)
        assertTrue(visionModels.isNotEmpty())
        assertTrue(visionModels.all { AiCapability.VISION_CHAT in it.capabilities })
    }

    @Test
    fun `modelsForSlot returns recommended models first`() {
        val models = AiModelRegistry.modelsForSlot(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION)
        val recommendedIndex = models.indexOfFirst { it.recommended }
        if (recommendedIndex >= 0) {
            // Recommended model should be before any non-recommended model
            val firstNonRecommended = models.indexOfFirst { !it.recommended }
            if (firstNonRecommended >= 0) {
                assertTrue(recommendedIndex < firstNonRecommended)
            }
        }
    }

    @Test
    fun `defaultRoute for OCR slot returns MiMo vision model`() {
        val route = AiModelRegistry.defaultRoute(AiFunctionSlot.CHAT_SCREENSHOT_OCR)
        assertEquals(AiProviderId.MIMO, route.provider)
        assertEquals(AiModelRegistry.MIMO_VISION_ID, route.modelId)
        assertEquals("mimo-v2.5", route.modelName)
    }

    @Test
    fun `defaultRoute for image generation slots returns vivo Seedream`() {
        val momentImageRoute = AiModelRegistry.defaultRoute(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION)
        assertEquals(AiProviderId.VIVO, momentImageRoute.provider)
        assertEquals(AiModelRegistry.VIVO_SEEDREAM_5_LITE_ID, momentImageRoute.modelId)

        val avatarRoute = AiModelRegistry.defaultRoute(AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION)
        assertEquals(AiProviderId.VIVO, avatarRoute.provider)
        assertEquals(AiModelRegistry.VIVO_SEEDREAM_5_LITE_ID, avatarRoute.modelId)
    }

    @Test
    fun `defaultRoute for short ASR slot returns vivo ASR model`() {
        val shortAsrRoute = AiModelRegistry.defaultRoute(AiFunctionSlot.VOICE_SHORT_RECOGNITION)
        assertEquals(AiProviderId.VIVO, shortAsrRoute.provider)
        assertEquals(AiModelRegistry.VIVO_SHORT_ASR_ID, shortAsrRoute.modelId)
    }

    @Test
    fun `defaultRoute for moment image understanding returns vivo Doubao Pro`() {
        val route = AiModelRegistry.defaultRoute(AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING)
        assertEquals(AiProviderId.VIVO, route.provider)
        assertEquals(AiModelRegistry.VIVO_DOUBAO_PRO_ID, route.modelId)
        assertEquals("Doubao-Seed-2.0-pro", route.modelName)
    }

    @Test
    fun `defaultRoute for text chat slots returns vivo DeepSeek`() {
        val textSlots = listOf(
            AiFunctionSlot.CHAT_SCREENSHOT_DIALOGUE_ANALYSIS,
            AiFunctionSlot.CHAT_SINGLE_CONTEXT_ANALYSIS,
            AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION,
            AiFunctionSlot.CHAT_CONTEXTUAL_REPLY_GENERATION,
            AiFunctionSlot.CHAT_IDENTITY_STYLE_DESCRIPTION
        )
        textSlots.forEach { slot ->
            val route = AiModelRegistry.defaultRoute(slot)
            assertEquals("Slot ${slot.key} should default to VIVO", AiProviderId.VIVO, route.provider)
            assertEquals("Slot ${slot.key} should default to DeepSeek", AiModelRegistry.VIVO_DEEPSEEK_ID, route.modelId)
        }
    }

    @Test
    fun `defaultRoute for moment copy generation returns vivo Doubao Pro`() {
        val route = AiModelRegistry.defaultRoute(AiFunctionSlot.MOMENT_COPY_GENERATION)
        assertEquals(AiProviderId.VIVO, route.provider)
        assertEquals(AiModelRegistry.VIVO_DOUBAO_PRO_ID, route.modelId)
        assertEquals("Doubao-Seed-2.0-pro", route.modelName)
    }

    @Test
    fun `isRouteCompatible returns true for compatible route`() {
        val route = AiModelRoute(
            provider = AiProviderId.VIVO,
            modelId = AiModelRegistry.VIVO_OCR_ROTATED_ID,
            modelName = "vivo-general-ocr-rotated"
        )
        assertTrue(AiModelRegistry.isRouteCompatible(AiFunctionSlot.CHAT_SCREENSHOT_OCR, route))
    }

    @Test
    fun `isRouteCompatible returns true for vision model under OCR slot`() {
        val route = AiModelRoute(
            provider = AiProviderId.MIMO,
            modelId = AiModelRegistry.MIMO_VISION_ID,
            modelName = "mimo-v2.5"
        )
        assertTrue(AiModelRegistry.isRouteCompatible(AiFunctionSlot.CHAT_SCREENSHOT_OCR, route))
    }

    @Test
    fun `isRouteCompatible returns false for incompatible capability`() {
        val route = AiModelRoute(
            provider = AiProviderId.VIVO,
            modelId = AiModelRegistry.VIVO_DEEPSEEK_ID,
            modelName = "Volc-DeepSeek-V3.2"
        )
        // DeepSeek doesn't have OCR capability
        assertFalse(AiModelRegistry.isRouteCompatible(AiFunctionSlot.CHAT_SCREENSHOT_OCR, route))
        // vivo only exposes OCR as visual technology, so this text model must not be used as image understanding.
        assertFalse(AiModelRegistry.isRouteCompatible(AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING, route))
    }

    @Test
    fun `isRouteCompatible returns false for unknown model`() {
        val route = AiModelRoute(
            provider = AiProviderId.VIVO,
            modelId = "nonexistent.model",
            modelName = "Nonexistent"
        )
        assertFalse(AiModelRegistry.isRouteCompatible(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION, route))
    }

    @Test
    fun `custom OpenAI image model is treated as image generation`() {
        val profile = CustomOpenAiProfile(
            id = "image-profile",
            name = "OpenAI",
            baseUrl = "https://example.com/v1",
            apiKey = "sk-test",
            model = "gpt-image-2"
        )

        val spec = AiModelRegistry.customOpenAiSpec(profile)

        assertEquals(setOf(AiCapability.IMAGE_GENERATION), spec.capabilities)
        assertTrue(AiModelRegistry.isSlotAndModelCompatible(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION, spec))
        assertFalse(AiModelRegistry.isSlotAndModelCompatible(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION, spec))
    }

    @Test
    fun `custom OpenAI route compatibility follows inferred model capability`() {
        val route = AiModelRoute(
            provider = AiProviderId.CUSTOM_OPENAI,
            modelId = CustomOpenAiProfiles.modelId("image-profile"),
            modelName = "gpt-image-2"
        )

        assertTrue(AiModelRegistry.isRouteCompatible(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION, route))
        assertFalse(AiModelRegistry.isRouteCompatible(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION, route))
    }

    @Test
    fun `custom OpenAI unknown model remains text and vision compatible`() {
        val profile = CustomOpenAiProfile(
            id = "chat-profile",
            name = "OpenAI",
            baseUrl = "https://example.com/v1",
            apiKey = "sk-test",
            model = "gpt-5.5"
        )

        val spec = AiModelRegistry.customOpenAiSpec(profile)

        assertTrue(AiCapability.TEXT_CHAT in spec.capabilities)
        assertTrue(AiCapability.VISION_CHAT in spec.capabilities)
        assertTrue(AiModelRegistry.isSlotAndModelCompatible(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION, spec))
        assertFalse(AiModelRegistry.isSlotAndModelCompatible(AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION, spec))
    }
    @Test
    fun `routeOrDefault returns default when route is null`() {
        val route = AiModelRegistry.routeOrDefault(AiFunctionSlot.CHAT_SCREENSHOT_OCR, null)
        assertEquals(AiModelRegistry.MIMO_VISION_ID, route.modelId)
    }

    @Test
    fun `routeOrDefault returns default when route is incompatible`() {
        val incompatibleRoute = AiModelRoute(
            provider = AiProviderId.VIVO,
            modelId = AiModelRegistry.VIVO_DEEPSEEK_ID,
            modelName = "Volc-DeepSeek-V3.2"
        )
        val route = AiModelRegistry.routeOrDefault(AiFunctionSlot.CHAT_SCREENSHOT_OCR, incompatibleRoute)
        assertEquals(AiModelRegistry.MIMO_VISION_ID, route.modelId)
    }

    @Test
    fun `routeOrDefault returns provided route when compatible`() {
        val compatibleRoute = AiModelRoute(
            provider = AiProviderId.VIVO,
            modelId = AiModelRegistry.VIVO_OCR_ROTATED_ID,
            modelName = "vivo-general-ocr-rotated"
        )
        val route = AiModelRegistry.routeOrDefault(AiFunctionSlot.CHAT_SCREENSHOT_OCR, compatibleRoute)
        assertEquals(AiModelRegistry.VIVO_OCR_ROTATED_ID, route.modelId)
    }

    @Test
    fun `all function slots have a default route`() {
        AiFunctionSlot.entries.forEach { slot ->
            val route = AiModelRegistry.defaultRoute(slot)
            assertNotNull("Default route for ${slot.key} should not be null", route)
            assertNotNull("Model for ${slot.key} should exist", AiModelRegistry.findModel(route.modelId))
        }
    }

    @Test
    fun `all function slots have at least one compatible model`() {
        AiFunctionSlot.entries.forEach { slot ->
            val models = AiModelRegistry.modelsForSlot(slot)
            assertTrue("Slot ${slot.key} should have at least one compatible model", models.isNotEmpty())
        }
    }

    @Test
    fun `functionSlot requiredCapability matches slot purpose`() {
        assertEquals(AiCapability.OCR, AiFunctionSlot.CHAT_SCREENSHOT_OCR.requiredCapability)
        assertEquals(AiCapability.TEXT_CHAT, AiFunctionSlot.CHAT_SCREENSHOT_DIALOGUE_ANALYSIS.requiredCapability)
        assertEquals(AiCapability.TEXT_CHAT, AiFunctionSlot.CHAT_SINGLE_CONTEXT_ANALYSIS.requiredCapability)
        assertEquals(AiCapability.TEXT_CHAT, AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION.requiredCapability)
        assertEquals(AiCapability.TEXT_CHAT, AiFunctionSlot.CHAT_CONTEXTUAL_REPLY_GENERATION.requiredCapability)
        assertEquals(AiCapability.TEXT_CHAT, AiFunctionSlot.CHAT_IDENTITY_STYLE_DESCRIPTION.requiredCapability)
        assertEquals(AiCapability.VISION_CHAT, AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING.requiredCapability)
        assertEquals(AiCapability.TEXT_CHAT, AiFunctionSlot.MOMENT_COPY_GENERATION.requiredCapability)
        assertEquals(AiCapability.IMAGE_GENERATION, AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION.requiredCapability)
        assertEquals(AiCapability.IMAGE_GENERATION, AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION.requiredCapability)
        assertEquals(AiCapability.ASR, AiFunctionSlot.VOICE_SHORT_RECOGNITION.requiredCapability)
    }

    @Test
    fun `toRoute creates correct AiModelRoute`() {
        val spec = AiModelRegistry.findModel(AiModelRegistry.VIVO_DEEPSEEK_ID)!!
        val route = spec.toRoute()
        assertEquals(spec.provider, route.provider)
        assertEquals(spec.modelId, route.modelId)
        assertEquals(spec.modelName, route.modelName)
    }
}
