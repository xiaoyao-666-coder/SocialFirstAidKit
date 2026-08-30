package com.socialfirstaidkit.core.llm.routing

import kotlinx.serialization.Serializable

@Serializable
enum class AiProviderId(val displayName: String) {
    VIVO("vivo 官方平台"),
    VIVO_ON_DEVICE("vivo 端侧 3B"),
    MIMO("小米 MiMo"),
    GEMINI("Google Gemini"),
    WENXIN("百度文心"),
    LANXIN("蓝心大模型"),
    TONGYI("通义千问"),
    AGNES("Agnes AI"),
    CUSTOM_OPENAI("自定义 OpenAI")
}

@Serializable
enum class AiCapability(val label: String) {
    TEXT_CHAT("文本"),
    VISION_CHAT("图文理解"),
    OCR("OCR"),
    IMAGE_GENERATION("图片生成"),
    VIDEO_GENERATION("视频生成"),
    ASR("语音识别"),
    TTS("语音合成")
}

@Serializable
data class AiModelRoute(
    val provider: AiProviderId,
    val modelId: String,
    val modelName: String
)

data class AiModelSpec(
    val provider: AiProviderId,
    val modelId: String,
    val modelName: String,
    val displayName: String,
    val capabilities: Set<AiCapability>,
    val defaultMaxTokens: Int = 1024,
    val defaultTemperature: Float = 0.7f,
    val recommended: Boolean = false,
    val note: String = ""
) {
    fun toRoute(): AiModelRoute = AiModelRoute(
        provider = provider,
        modelId = modelId,
        modelName = modelName
    )
}

enum class AiFunctionModule(val title: String, val order: Int) {
    CHAT_ASSISTANT("聊天助手", 10),
    MOMENT_CREATION("朋友圈创作", 20),
    IMAGE_CREATION("图片生成", 30),
    ADVANCED("高级/预留能力", 40)
}

enum class AiFunctionSlot(
    val key: String,
    val title: String,
    val module: AiFunctionModule,
    val requiredCapability: AiCapability,
    val description: String
) {
    CHAT_SCREENSHOT_OCR(
        key = "chat.screenshot_ocr",
        title = "截图文字提取",
        module = AiFunctionModule.CHAT_ASSISTANT,
        requiredCapability = AiCapability.OCR,
        description = "从聊天截图中提取文字内容"
    ),
    CHAT_SCREENSHOT_DIALOGUE_ANALYSIS(
        key = "chat.screenshot_dialogue_analysis",
        title = "截图对话分析",
        module = AiFunctionModule.CHAT_ASSISTANT,
        requiredCapability = AiCapability.TEXT_CHAT,
        description = "根据 OCR 文本还原聊天对象、消息和语境"
    ),
    CHAT_SINGLE_CONTEXT_ANALYSIS(
        key = "chat.single_context_analysis",
        title = "单条语境分析",
        module = AiFunctionModule.CHAT_ASSISTANT,
        requiredCapability = AiCapability.TEXT_CHAT,
        description = "分析单条消息的意图、情绪和回复策略"
    ),
    CHAT_SINGLE_REPLY_GENERATION(
        key = "chat.single_reply_generation",
        title = "单条回复建议",
        module = AiFunctionModule.CHAT_ASSISTANT,
        requiredCapability = AiCapability.TEXT_CHAT,
        description = "为单条消息生成多种回复建议"
    ),
    CHAT_CONTEXTUAL_REPLY_GENERATION(
        key = "chat.contextual_reply_generation",
        title = "上下文回复建议",
        module = AiFunctionModule.CHAT_ASSISTANT,
        requiredCapability = AiCapability.TEXT_CHAT,
        description = "结合多轮聊天上下文生成回复建议"
    ),
    CHAT_IDENTITY_STYLE_DESCRIPTION(
        key = "chat.identity_style_description",
        title = "身份/风格描述",
        module = AiFunctionModule.CHAT_ASSISTANT,
        requiredCapability = AiCapability.TEXT_CHAT,
        description = "生成身份关系或回复风格说明"
    ),
    PROFILE_EXPRESSION_UPDATE_SUMMARY(
        key = "profile.expression_update_summary",
        title = "个人表达档案更新总结",
        module = AiFunctionModule.CHAT_ASSISTANT,
        requiredCapability = AiCapability.TEXT_CHAT,
        description = "根据复制反馈样本总结个人表达档案更新建议"
    ),
    MOMENT_IMAGE_UNDERSTANDING(
        key = "moment.image_understanding",
        title = "朋友圈图片理解",
        module = AiFunctionModule.MOMENT_CREATION,
        requiredCapability = AiCapability.VISION_CHAT,
        description = "理解图片主体、场景、氛围和适合表达的主题"
    ),
    MOMENT_COPY_GENERATION(
        key = "moment.copy_generation",
        title = "朋友圈文案生成",
        module = AiFunctionModule.MOMENT_CREATION,
        requiredCapability = AiCapability.TEXT_CHAT,
        description = "根据图片理解和补充信息生成朋友圈文案"
    ),
    MOMENT_SHARE_IMAGE_GENERATION(
        key = "moment.share_image_generation",
        title = "朋友圈分享图生成",
        module = AiFunctionModule.MOMENT_CREATION,
        requiredCapability = AiCapability.IMAGE_GENERATION,
        description = "生成可发布到朋友圈的分享图"
    ),
    PROFILE_AVATAR_IMAGE_GENERATION(
        key = "profile.avatar_image_generation",
        title = "个人头像/数字形象生成",
        module = AiFunctionModule.IMAGE_CREATION,
        requiredCapability = AiCapability.IMAGE_GENERATION,
        description = "根据照片或提示生成个人数字形象"
    ),
    VOICE_SHORT_RECOGNITION(
        key = "voice.short_recognition",
        title = "短语音识别",
        module = AiFunctionModule.ADVANCED,
        requiredCapability = AiCapability.ASR,
        description = "预留：短语音输入识别"
    )
}

object AiModelRegistry {
    // These vivo, MiMo, and Agnes identifiers describe competition-provided
    // routes. Public users should map each function slot to an accessible
    // provider instead; see docs/API_REPLACEMENT.md.
    const val VIVO_DEEPSEEK_ID = "vivo.volc.deepseek.v3_2"
    const val VIVO_DOUBAO_MINI_ID = "vivo.doubao.seed_2_0_mini"
    const val VIVO_DOUBAO_LITE_ID = "vivo.doubao.seed_2_0_lite"
    const val VIVO_DOUBAO_PRO_ID = "vivo.doubao.seed_2_0_pro"
    const val VIVO_QWEN_PLUS_ID = "vivo.qwen3_5_plus"
    const val VIVO_OCR_ROTATED_ID = "vivo.ocr.general_rotated"
    const val VIVO_SEEDREAM_5_LITE_ID = "vivo.seedream.5_0_lite"
    const val VIVO_SEEDREAM_4_5_ID = "vivo.seedream.4_5"
    const val VIVO_SHORT_ASR_ID = "vivo.asr.shortasrinput"
    const val VIVO_ON_DEVICE_3B_ID = "vivo.ondevice.bluelm_3b"

    const val MIMO_TEXT_ID = "mimo.mimo-v2.5-pro"
    const val MIMO_VISION_ID = "mimo.mimo-v2.5"
    const val MIMO_ASR_ID = "mimo.mimo-asr"

    const val GEMINI_FLASH_ID = "gemini.gemini-2.0-flash"
    const val WENXIN_TEXT_ID = "wenxin.ernie"
    const val LANXIN_TEXT_ID = "lanxin.default"
    const val TONGYI_TEXT_ID = "tongyi.qwen-plus"
    const val AGNES_2_FLASH_ID = "agnes.agnes-2.0-flash"
    const val AGNES_IMAGE_2_FLASH_ID = "agnes.agnes-image-2.0-flash"
    const val AGNES_VIDEO_2_ID = "agnes.agnes-video-v2.0"
    const val CUSTOM_OPENAI_TEXT_ID = "custom.openai.compatible"

    val models: List<AiModelSpec> = listOf(
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_DEEPSEEK_ID,
            modelName = "Volc-DeepSeek-V3.2",
            displayName = "Volc-DeepSeek-V3.2",
            capabilities = setOf(AiCapability.TEXT_CHAT),
            defaultTemperature = 0.2f,
            recommended = true,
            note = "vivo 文本模型"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_DOUBAO_MINI_ID,
            modelName = "Doubao-Seed-2.0-mini",
            displayName = "Doubao Seed 2.0 mini",
            capabilities = setOf(AiCapability.TEXT_CHAT),
            note = "快速文本候选"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_DOUBAO_LITE_ID,
            modelName = "Doubao-Seed-2.0-lite",
            displayName = "Doubao Seed 2.0 lite",
            capabilities = setOf(AiCapability.TEXT_CHAT),
            note = "轻量文本候选"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_DOUBAO_PRO_ID,
            modelName = "Doubao-Seed-2.0-pro",
            displayName = "Doubao Seed 2.0 pro",
            capabilities = setOf(AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT),
            recommended = true,
            note = "支持图文理解的高质量候选"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_QWEN_PLUS_ID,
            modelName = "qwen3.5-plus",
            displayName = "qwen3.5-plus",
            capabilities = setOf(AiCapability.TEXT_CHAT),
            note = "文本候选"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_OCR_ROTATED_ID,
            modelName = "vivo-general-ocr-rotated",
            displayName = "vivo 通用 OCR",
            capabilities = setOf(AiCapability.OCR),
            note = "Requires a developer-provided vivo OCR business ID"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_SEEDREAM_5_LITE_ID,
            modelName = "Doubao-Seedream-5.0-lite",
            displayName = "Doubao Seedream 5.0 lite",
            capabilities = setOf(AiCapability.IMAGE_GENERATION),
            recommended = true,
            note = "默认图片生成/图生图模型"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_SEEDREAM_4_5_ID,
            modelName = "Doubao-Seedream-4.5",
            displayName = "Doubao Seedream 4.5",
            capabilities = setOf(AiCapability.IMAGE_GENERATION),
            note = "图片生成候选"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO,
            modelId = VIVO_SHORT_ASR_ID,
            modelName = "shortasrinput",
            displayName = "shortasrinput",
            capabilities = setOf(AiCapability.ASR),
            recommended = true,
            note = "短语音识别"
        ),
        AiModelSpec(
            provider = AiProviderId.VIVO_ON_DEVICE,
            modelId = VIVO_ON_DEVICE_3B_ID,
            modelName = "BlueLM-OnDevice-3B",
            displayName = "BlueLM 端侧 3B",
            capabilities = setOf(AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT),
            defaultMaxTokens = 512,
            defaultTemperature = 0.0f,
            note = "支持端侧文本和图文理解；需要 vivo 云真机/支持 MTK APU 且模型目录完整"
        ),
        AiModelSpec(
            provider = AiProviderId.MIMO,
            modelId = MIMO_TEXT_ID,
            modelName = "mimo-v2.5-pro",
            displayName = "MiMo v2.5 Pro",
            capabilities = setOf(AiCapability.TEXT_CHAT),
            note = "小米文本模型"
        ),
        AiModelSpec(
            provider = AiProviderId.MIMO,
            modelId = MIMO_VISION_ID,
            modelName = "mimo-v2.5",
            displayName = "MiMo v2.5",
            capabilities = setOf(AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT),
            recommended = true,
            note = "小米图文理解模型"
        ),
        AiModelSpec(
            provider = AiProviderId.MIMO,
            modelId = MIMO_ASR_ID,
            modelName = "mimo-asr",
            displayName = "MiMo ASR",
            capabilities = setOf(AiCapability.ASR),
            note = "小米语音识别候选"
        ),
        AiModelSpec(
            provider = AiProviderId.GEMINI,
            modelId = GEMINI_FLASH_ID,
            modelName = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            capabilities = setOf(AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT)
        ),
        AiModelSpec(
            provider = AiProviderId.WENXIN,
            modelId = WENXIN_TEXT_ID,
            modelName = "ernie",
            displayName = "文心默认文本模型",
            capabilities = setOf(AiCapability.TEXT_CHAT)
        ),
        AiModelSpec(
            provider = AiProviderId.LANXIN,
            modelId = LANXIN_TEXT_ID,
            modelName = "lanxin-default",
            displayName = "蓝心默认文本模型",
            capabilities = setOf(AiCapability.TEXT_CHAT)
        ),
        AiModelSpec(
            provider = AiProviderId.TONGYI,
            modelId = TONGYI_TEXT_ID,
            modelName = "qwen-plus",
            displayName = "通义 qwen-plus",
            capabilities = setOf(AiCapability.TEXT_CHAT)
        ),
        AiModelSpec(
            provider = AiProviderId.AGNES,
            modelId = AGNES_2_FLASH_ID,
            modelName = "agnes-2.0-flash",
            displayName = "Agnes 2.0 Flash",
            capabilities = setOf(AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT),
            defaultTemperature = 0.7f,
            recommended = true,
            note = "免费文本/图文模型"
        ),
        AiModelSpec(
            provider = AiProviderId.AGNES,
            modelId = AGNES_IMAGE_2_FLASH_ID,
            modelName = "agnes-image-2.0-flash",
            displayName = "Agnes Image 2.0 Flash",
            capabilities = setOf(AiCapability.IMAGE_GENERATION),
            recommended = true,
            note = "图片生成/图生图模型"
        ),
        AiModelSpec(
            provider = AiProviderId.AGNES,
            modelId = AGNES_VIDEO_2_ID,
            modelName = "agnes-video-v2.0",
            displayName = "Agnes Video V2.0",
            capabilities = setOf(AiCapability.VIDEO_GENERATION),
            recommended = true,
            note = "异步视频生成模型；当前项目暂未接入视频功能"
        ),
        AiModelSpec(
            provider = AiProviderId.CUSTOM_OPENAI,
            modelId = CUSTOM_OPENAI_TEXT_ID,
            modelName = "custom",
            displayName = "自定义 OpenAI 兼容模型",
            capabilities = setOf(AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT)
        )
    )

    val functionSlots: List<AiFunctionSlot> = AiFunctionSlot.entries.toList()

    fun findModel(modelId: String): AiModelSpec? = models.firstOrNull { it.modelId == modelId }

    fun customOpenAiSpec(profile: CustomOpenAiProfile): AiModelSpec = AiModelSpec(
        provider = AiProviderId.CUSTOM_OPENAI,
        modelId = CustomOpenAiProfiles.modelId(profile.id),
        modelName = profile.model,
        displayName = profile.displayName,
        capabilities = inferCustomOpenAiCapabilities(profile.model),
        note = profile.baseUrl
    )

    fun inferCustomOpenAiCapabilities(modelName: String): Set<AiCapability> {
        val normalized = modelName.trim().lowercase()
        if (normalized.isBlank()) {
            return setOf(AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT)
        }

        val videoKeywords = listOf("video", "sora", "seedance", "kling", "hailuo", "wan2")
        if (videoKeywords.any { it in normalized }) {
            return setOf(AiCapability.VIDEO_GENERATION)
        }

        val imageKeywords = listOf(
            "gpt-image",
            "image",
            "dall-e",
            "dalle",
            "imagen",
            "seedream",
            "stable-diffusion",
            "sdxl",
            "flux",
            "midjourney"
        )
        if (imageKeywords.any { it in normalized }) {
            return setOf(AiCapability.IMAGE_GENERATION)
        }

        return setOf(AiCapability.TEXT_CHAT, AiCapability.VISION_CHAT)
    }

    fun primaryCustomOpenAiCapability(modelName: String): AiCapability {
        val capabilities = inferCustomOpenAiCapabilities(modelName)
        return when {
            AiCapability.IMAGE_GENERATION in capabilities -> AiCapability.IMAGE_GENERATION
            AiCapability.VIDEO_GENERATION in capabilities -> AiCapability.VIDEO_GENERATION
            AiCapability.TEXT_CHAT in capabilities -> AiCapability.TEXT_CHAT
            else -> capabilities.first()
        }
    }

    fun modelsForProvider(providerId: AiProviderId): List<AiModelSpec> =
        models.filter { it.provider == providerId }

    fun modelsForCapability(capability: AiCapability): List<AiModelSpec> =
        models.filter { capability in it.capabilities }

    fun isSlotAndModelCompatible(slot: AiFunctionSlot, spec: AiModelSpec): Boolean {
        return if (slot == AiFunctionSlot.CHAT_SCREENSHOT_OCR) {
            AiCapability.OCR in spec.capabilities || AiCapability.VISION_CHAT in spec.capabilities
        } else {
            slot.requiredCapability in spec.capabilities
        }
    }

    fun modelsForSlot(slot: AiFunctionSlot): List<AiModelSpec> =
        models.filter { isSlotAndModelCompatible(slot, it) }.sortedWith(
            compareByDescending<AiModelSpec> { it.recommended }
                .thenBy { it.provider.ordinal }
                .thenBy { it.displayName }
        )

    fun defaultRoute(slot: AiFunctionSlot): AiModelRoute {
        val modelId = when (slot) {
            AiFunctionSlot.CHAT_SCREENSHOT_OCR -> MIMO_VISION_ID
            AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING -> VIVO_DOUBAO_PRO_ID
            AiFunctionSlot.MOMENT_COPY_GENERATION -> VIVO_DOUBAO_PRO_ID
            AiFunctionSlot.MOMENT_SHARE_IMAGE_GENERATION,
            AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION -> VIVO_SEEDREAM_5_LITE_ID
            AiFunctionSlot.VOICE_SHORT_RECOGNITION -> VIVO_SHORT_ASR_ID
            else -> VIVO_DEEPSEEK_ID
        }
        return requireNotNull(findModel(modelId)) { "Missing default model for $slot" }.toRoute()
    }

    fun isRouteCompatible(slot: AiFunctionSlot, route: AiModelRoute): Boolean {
        if (route.provider == AiProviderId.CUSTOM_OPENAI &&
            CustomOpenAiProfiles.profileIdFromModelId(route.modelId) != null
        ) {
            val capabilities = inferCustomOpenAiCapabilities(route.modelName)
            return if (slot == AiFunctionSlot.CHAT_SCREENSHOT_OCR) {
                AiCapability.OCR in capabilities || AiCapability.VISION_CHAT in capabilities
            } else {
                slot.requiredCapability in capabilities
            }
        }
        val spec = findModel(route.modelId) ?: return false
        return spec.provider == route.provider && isSlotAndModelCompatible(slot, spec)
    }

    fun routeOrDefault(slot: AiFunctionSlot, route: AiModelRoute?): AiModelRoute {
        return if (route != null && isRouteCompatible(slot, route)) route else defaultRoute(slot)
    }
}
