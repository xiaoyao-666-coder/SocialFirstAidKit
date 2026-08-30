package com.socialfirstaidkit.core.llm.impl

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionPromptContext
import com.socialfirstaidkit.core.data.personalexpression.sentenceLengthLabel
import com.socialfirstaidkit.core.llm.api.LlmClient
import com.socialfirstaidkit.core.llm.api.LlmProvider
import com.socialfirstaidkit.core.llm.api.ReplyStyle
import com.socialfirstaidkit.core.llm.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import com.socialfirstaidkit.core.llm.routing.AiCapability
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import com.socialfirstaidkit.core.llm.routing.AiModelRegistry
import com.socialfirstaidkit.core.llm.routing.AiProviderId
import com.socialfirstaidkit.core.llm.routing.AiRouteResolver
import com.socialfirstaidkit.core.llm.routing.AiRouteStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM 客户端实现
 *
 * 封装统一的 LLM 调用逻辑，提供场景化的 Prompt 构建和响应解析。
 */
@Singleton
class LlmClientImpl @Inject constructor(
    private val vivoAigcProvider: VivoAigcProvider,
    private val vivoOnDeviceProvider: VivoOnDeviceProvider,
    private val aiRouteStore: AiRouteStore,
    private val aiRouteResolver: AiRouteResolver
) : LlmClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val activeProvider: LlmProvider
        get() = aiRouteResolver.resolve(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION).first

    override fun switchProvider(type: LlmProviderType) {
        val providerId = type.toProviderId() ?: AiProviderId.VIVO
        val textModel = AiModelRegistry.modelsForProvider(providerId)
            .firstOrNull { AiCapability.TEXT_CHAT in it.capabilities }
            ?: AiModelRegistry.findModel(AiModelRegistry.VIVO_DEEPSEEK_ID)
            ?: return
        AiFunctionSlot.entries
            .filter { it.requiredCapability == AiCapability.TEXT_CHAT }
            .forEach { slot -> aiRouteStore.saveRouteByModelId(slot, textModel.modelId) }
    }

    override suspend fun chat(request: LlmRequest): LlmResponse {
        return chatFor(inferFunctionSlot(request), request)
    }

    override suspend fun chatFor(slot: AiFunctionSlot, request: LlmRequest): LlmResponse {
        val (provider, modelName) = aiRouteResolver.resolve(slot)
        val routedRequest = request.copy(model = modelName)
        return chatWithFallback(
            slot = slot,
            primaryProvider = provider,
            request = routedRequest,
            purpose = "slot_${slot.key}"
        )
    }

    private fun inferFunctionSlot(request: LlmRequest): AiFunctionSlot {
        val systemPrompt = request.messages.firstOrNull { it.role == LlmRole.SYSTEM }?.content.orEmpty()
        val hasImageInput = request.messages.any { !it.imageUrl.isNullOrBlank() }
        return when {
            systemPrompt.contains("朋友圈") || systemPrompt.contains("文案") ->
                if (hasImageInput) AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING else AiFunctionSlot.MOMENT_COPY_GENERATION
            systemPrompt.contains("OCR", ignoreCase = true) ->
                AiFunctionSlot.CHAT_SCREENSHOT_DIALOGUE_ANALYSIS
            else -> AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION
        }
    }

    private fun LlmProviderType.toProviderId(): AiProviderId? {
        return when (this) {
            LlmProviderType.VIVO -> AiProviderId.VIVO
            LlmProviderType.VIVO_ON_DEVICE -> AiProviderId.VIVO_ON_DEVICE
            LlmProviderType.MIMO -> AiProviderId.MIMO
            LlmProviderType.GEMINI -> AiProviderId.GEMINI
            LlmProviderType.WENXIN -> AiProviderId.WENXIN
            LlmProviderType.LANXIN -> AiProviderId.LANXIN
            LlmProviderType.TONGYI -> AiProviderId.TONGYI
            LlmProviderType.AGNES -> AiProviderId.AGNES
            LlmProviderType.CUSTOM -> AiProviderId.CUSTOM_OPENAI
        }
    }

    private val fallbackProvider: LlmProvider
        get() = vivoAigcProvider

    private suspend fun chatWithFallback(
        slot: AiFunctionSlot,
        primaryProvider: LlmProvider,
        request: LlmRequest,
        purpose: String
    ): LlmResponse {
        val primaryResponse = try {
            primaryProvider.chat(request)
        } catch (e: Exception) {
            Log.e(TAG, "$purpose primary exception provider=${primaryProvider.providerName}", e)
            LlmResponse.Error("EXCEPTION", e.message ?: "Unknown exception")
        }

        if (primaryResponse !is LlmResponse.Error) {
            return primaryResponse
        }

        if (primaryProvider.providerType == fallbackProvider.providerType ||
            !primaryResponse.shouldFallbackToBackup(primaryProvider)
        ) {
            return primaryResponse
        }

        if (!shouldUseFallback(slot, fallbackProvider)) {
            Log.w(
                TAG,
                "$purpose primary failed code=${primaryResponse.code}, fallback skipped"
            )
            return primaryResponse
        }

        Log.w(
            TAG,
            "$purpose primary failed code=${primaryResponse.code}, switching to ${fallbackProvider.providerName}"
        )

        val fallbackRequest = request.copy(model = fallbackProvider.defaultModel)
        return try {
            fallbackProvider.chat(fallbackRequest)
        } catch (e: Exception) {
            Log.e(TAG, "$purpose fallback exception provider=${fallbackProvider.providerName}", e)
            LlmResponse.Error("EXCEPTION", e.message ?: "Unknown fallback exception")
        }
    }

    private fun LlmResponse.Error.shouldFallbackToBackup(primaryProvider: LlmProvider): Boolean {
        if (primaryProvider.providerType == LlmProviderType.VIVO_ON_DEVICE) {
            return true
        }
        val normalizedCode = code.uppercase()
        return normalizedCode == "EXCEPTION" ||
            normalizedCode == "FORCED_FALLBACK" ||
            normalizedCode == "NETWORK_ERROR" ||
            normalizedCode == "EMPTY_RESPONSE" ||
            normalizedCode == "API_ERROR" ||
            (normalizedCode.startsWith("HTTP_") &&
                normalizedCode != "HTTP_401" &&
                normalizedCode != "HTTP_403")
    }

    // ========== 社交回复建议 Prompt ==========

    companion object {
        private const val TAG = "LlmClientImpl"

        /** 高情商社交大师系统 Prompt */
        val SYSTEM_PROMPT_CHAT = """
你是一个懂分寸、懂语气的社交回复助手。你的核心任务是根据真实语境生成自然、好用、不尴尬的回复建议：普通日常邀请按友好沟通处理，确实越界或施压时再帮助用户保护边界感。

# 核心原则（最高优先级）
1. 默认先按普通、友好的日常沟通理解对方消息。朋友约打球、吃饭、聊天、见面等普通邀请，不能解读成“攻击性”“越界”“道德绑架”，除非对方明确施压、责备、威胁、PUA 或连续强迫。
2. 只有在对方确实越界、道德绑架、甩锅或强迫时，才使用边界保护策略。
3. 拒绝或暂缓时要轻量、自然、友好，不要给对方贴负面标签，不要上升到攻击性或动机评判。
4. 保留回旋余地：不要把话说死，让用户保有选择空间。

# 抉择类问题处理
当对方消息是在邀请、询问或催促用户做选择，例如“去不去”“来不来”“要不要”“答不答应”“一起吗”“有空吗”“打球吗”“吃饭吗”，且用户没有明确给出立场时，三条建议必须覆盖三个方向：
1. formal：同意/愿意去，语气自然积极。
2. casual：拒绝/不去，语气委婉友好。
3. humorous：再想想/待确认，保留余地，说明稍后确认。
不要把三条都写成拒绝，也不要都写成防御或推脱。

# 严格约束
直接按以下 JSON 格式输出结果，不要加任何废话：
{
  "formal": "礼貌正式版的回复",
  "casual": "轻松随意版的回复",
  "humorous": "幽默化解版的回复"
}

拒绝表达应轻量、自然、友好，优先使用“今天不太方便/我先看看安排/下次约”这类日常说法；不要上升到攻击性、边界防御或对对方动机的评判。
        """.trimIndent()

        /** 朋友圈文案生成系统 Prompt */
        val SYSTEM_PROMPT_SOCIAL_COPY = """
你是一个朋友圈文案创作大师，擅长根据图片内容生成吸引人的社交媒体文案。

# 要求
1. 根据图片描述生成3种不同风格的文案
2. 文艺版：诗意、含蓄、有画面感
3. 搞笑版：幽默、接地气、带点网络热梗
4. 专业版：正能量、有深度、适合职场人设
5. 每版不超过50字，适合朋友圈阅读

# 输出格式（严格 JSON）
{
  "literary": "文艺版文案",
  "funny": "搞笑版文案",
  "professional": "专业版文案"
}
        """.trimIndent()

        /** 朋友圈文案生成系统 Prompt - 带风格 */
        fun buildSocialCopyPrompt(style: ReplyStyle?, customStyleDescription: String?): String {
            // 如果没有选择风格，生成默认的3种不同风格文案
            if (style == null) {
                return SYSTEM_PROMPT_SOCIAL_COPY
            }
            
            // 根据选择的风格，生成该风格的3个不同版本
            val (styleName, styleDesc) = when (style) {
                ReplyStyle.FORMAL -> "正式" to "正式、优雅、有格调的风格，适合商务场合或展示专业形象"
                ReplyStyle.CASUAL -> "随意" to "轻松、随意、生活化的风格，像朋友间的分享"
                ReplyStyle.HUMOROUS -> "幽默" to "幽默、搞笑、有趣的风格，带点网络热梗和俏皮感"
                ReplyStyle.EMPATHETIC -> "共情" to "温暖、感性、有共鸣的风格，能引发情感共鸣"
                ReplyStyle.ASSERTIVE -> "坚定" to "自信、坚定、有力量的风格，展现个人态度"
                ReplyStyle.DAI_ZHOU_LI -> "大周礼" to """
                    【文风强制约束，必须100%遵守】
                    1. 不固定起手式；请使用自然的古文/雅正文言表达，可依语境变化开头。
                    2. 整体采用半文言语体，用词克制、端庄、守礼，符合周代君子言行风范。
                    3. 必须引用周礼、古君子典故、先贤言行作为说理依据，不可空泛发言。
                    4. 核心精神必须遵循「发乎情止乎礼」：体面、克制，只用道义和礼法说理。
                    5. 禁止出现任何现代网络用语、表情包梗、英文缩写，禁止直白口语化表达。
                    6. 结尾必须收束得体，留有余地，不把话说死，符合君子绝交不出恶语的风范。
                    
                    【禁止项，违反即不合格】
                    ❌ 禁止机械套用固定开头
                    ❌ 禁止直白说出现代大白话
                    ❌ 禁止出现情绪化用词、辱骂、阴阳怪气、嘲讽语气
                    ❌ 禁止脱离周礼内核，写成普通古风鸡汤或现代白话套古词
                """.trimIndent()
                ReplyStyle.OTHER -> "自定义" to (customStyleDescription ?: "用户自定义风格")
            }
            
            return """
你是一个朋友圈文案创作大师，擅长根据图片内容生成吸引人的社交媒体文案。

# 任务要求
用户选择了【$styleName】风格，请根据图片内容生成3个该风格的文案版本。

# 风格说明
$styleDesc

# 具体要求
1. 生成3个不同角度的$styleName 风格文案
2. 每个文案不超过50字，适合朋友圈阅读
3. 3个文案要有差异化，从不同角度切入
4. 保持$styleName 风格的一致性

# 输出格式（严格 JSON）
{
  "literary": "${styleName}风格文案版本1",
  "funny": "${styleName}风格文案版本2",
  "professional": "${styleName}风格文案版本3"
}
            """.trimIndent()
        }

        /** 朋友圈创作图片理解系统 Prompt */
        val SYSTEM_PROMPT_MOMENT_IMAGE_ANALYSIS = """
你是一个朋友圈内容创作前的图片理解助手。请根据用户上传的图片和本地识别信息，判断画面主体、场景、氛围以及适合表达的朋友圈主题。

# 要求
1. 必须优先观察图片本身，尽量利用画面细节提高识别精度；本地识别标签只作为辅助参考，如果标签和画面冲突，以图片画面为准。
2. 先判断图片大类和主体类型，例如人物、食物、宠物、植物、风景、建筑、交通工具、电子设备、书本文具、截图文字、票据证件、商品物件或室内外场景，再描述最醒目的真实对象。
3. 不要编造图片中看不见的事实，不要把宠物、动物、植物、风景、物品、截图等粗暴归为食物或商品，也不要因为本地标签出现泛化词就直接照抄。
4. 如果无法确定具体名称，必须使用“类别 + 可见特征 + 位置/关系”的保守描述，例如按颜色、形状、材质、数量、动作、环境、摆放关系来描述；不要硬猜具体菜名、品牌、地点、人物身份、商品型号、动植物品种或事件名称。
5. 只有画面非常明确时才写具体名称；如果只是相似或局部可见，要使用“疑似”“像是”“一类”等谨慎表达，或者退回到更上位的可见描述。
6. 对不同图片都要做同样约束：食物描述可见组成和摆放；人物描述动作、姿态和场景，不猜身份关系；物品描述外观和用途线索，不猜品牌型号；风景建筑描述可见环境，不猜具体地点；截图/票据优先识别真实文字，不补全看不清的内容。
7. 主体、场景、氛围和 themes 必须全部使用简体中文，不要输出英文标签。
8. 主体要具体到画面中最醒目的真实对象，避免使用“Food”“Metal”“Object”“生活照片”这类泛化词。
9. themes 给出 2 到 4 个适合朋友圈表达的中文主题标签，例如：日常记录、美食、学习办公、旅行风景、周末放松。
10. insight 用一句话说明这张图适合怎样发布，语气自然，不要像技术报告。

# 输出格式（严格 JSON）
{
  "subject": "画面主体",
  "scene": "场景",
  "mood": "氛围",
  "themes": ["主题1", "主题2"],
  "insight": "一句发布理解"
}
        """.trimIndent()

        private fun buildMomentPersonalExpressionPrompt(
            personalExpression: PersonalExpressionPromptContext?
        ): String {
            val context = personalExpression ?: return ""
            val profile = context.profile
            val avoided = profile.avoidedExpressions
                .take(8)
                .joinToString("\n") { "- $it" }
                .ifBlank { "- 无额外禁用表达" }
            val preferred = profile.preferredExpressions
                .take(8)
                .joinToString("、")
                .ifBlank { "无固定常用表达" }
            val samples = profile.sampleReplies.entries
                .take(4)
                .joinToString("\n") { "- ${it.key}: ${it.value}" }
                .ifBlank { "- 暂无样例" }

            return """

# 个人表达档案（已启用，低于用户补充信息和当前朋友圈风格）
请让朋友圈文案更接近用户本人的日常表达，但不要为了模仿而编造图片、地点、人物关系、品牌或事件。
- 摘要标签：${context.summaryTags.joinToString("、")}
- 直接程度：${profile.coreStyle.directness}/5
- 温暖程度：${profile.coreStyle.warmth}/5
- 正式程度：${profile.coreStyle.formality}/5
- 句子长度：${profile.coreStyle.sentenceLength}/5（${sentenceLengthLabel(profile.coreStyle.sentenceLength)}）
- 幽默程度：${profile.coreStyle.humor}/5
- emoji 使用：${profile.languageMarkers.emojiUsage.label}
- 语气词密度：${profile.languageMarkers.fillerWords}/5
- 常用表达参考：$preferred

必须避开的表达：
$avoided

表达样例：
$samples

如果当前朋友圈风格与个人表达档案冲突，以当前朋友圈风格为准；不冲突时参考档案中的措辞、长度、语气词、emoji 和避雷项。
            """.trimIndent()
        }

        /** 朋友圈创作文案生成系统 Prompt */
        fun buildMomentCopyPrompt(
            styleName: String,
            styleDescription: String,
            personalExpression: PersonalExpressionPromptContext? = null
        ): String {
            val personalExpressionPart = buildMomentPersonalExpressionPrompt(personalExpression)
            return """
你是一个懂真实朋友圈表达习惯的内容创作助手。你的任务不是简单描述图片，而是把图片内容、用户补充信息和发布意图变成自然、生活化、可直接发布的朋友圈文案。

# 当前文案风格
$styleName：$styleDescription

# 生成要求
1. 生成 4 条候选文案，全部使用当前风格，但切入角度要有差异。
2. 文案要像真实用户会发的朋友圈，避免广告腔、硬夸、过度抒情和 AI 感。
3. 每条不超过 60 字，可轻微使用标点，不要堆砌标签。
4. 如果用户补充了地点、心情、意图或禁忌，请优先遵守。
5. 不要编造图片或补充信息里没有的具体人物关系、品牌、地点和事件。
$personalExpressionPart

# 输出格式（严格 JSON）
{
  "suggestions": [
    { "styleName": "$styleName", "content": "候选文案1" },
    { "styleName": "$styleName", "content": "候选文案2" },
    { "styleName": "$styleName", "content": "候选文案3" },
    { "styleName": "$styleName", "content": "候选文案4" }
  ]
}
            """.trimIndent()
        }

    }

    override suspend fun generateReplySuggestions(
        context: String,
        relation: String,
        styleHistory: String?
    ): ReplySuggestions {
        val userMessage = buildString {
            appendLine("对方身份：$relation")
            appendLine("对方消息：\"$context\"")
            if (!styleHistory.isNullOrBlank()) {
                appendLine("我的历史风格：$styleHistory")
            }
        }

        val request = LlmRequest(
            model = "", // Will be filled by chatFor
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = SYSTEM_PROMPT_CHAT),
                LlmMessage(role = LlmRole.USER, content = userMessage)
            ),
            maxTokens = 512,
            temperature = 0.7f
        )

        val slot = AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION
        Log.d(TAG, "generateReplySuggestions via slot=${slot.key}")
        val response = chatFor(slot, request)
        Log.d(TAG, "generateReplySuggestions responseType=${response.javaClass.simpleName}")

        return when (response) {
            is LlmResponse.Complete -> parseReplySuggestions(response.content)
            is LlmResponse.Error -> ReplySuggestions(
                formal = "（生成失败：${response.message}）",
                casual = "（生成失败：${response.message}）",
                humorous = "（生成失败：${response.message}）"
            )
            is LlmResponse.StreamChunk -> ReplySuggestions("Unexpected stream response", "", "")
        }
    }

    override suspend fun generateSocialCopy(
        imageDescription: String,
        imageBase64: String?,
        style: ReplyStyle?,
        customStyleDescription: String?,
        additionalInfo: String?
    ): SocialCopySuggestions {
        val systemPrompt = buildSocialCopyPrompt(style, customStyleDescription)
        val userContent = buildString {
            appendLine("图片信息：$imageDescription")
            if (!additionalInfo.isNullOrBlank()) {
                appendLine()
                appendLine("用户补充信息：${additionalInfo.trim()}")
                appendLine("请优先结合用户补充信息，不要编造与补充信息冲突的内容。")
            }
        }
        val request = LlmRequest(
            model = "", // Will be filled by chatFor
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = systemPrompt),
                LlmMessage(
                    role = LlmRole.USER,
                    content = userContent,
                    imageUrl = imageBase64
                )
            ),
            maxTokens = 512,
            temperature = 0.8f
        )

        val slot = AiFunctionSlot.MOMENT_COPY_GENERATION
        val response = chatFor(slot, request)

        return when (response) {
            is LlmResponse.Complete -> parseSocialCopySuggestions(response.content)
            is LlmResponse.Error -> SocialCopySuggestions(
                literary = "（生成失败：${response.message}）",
                funny = "（生成失败：${response.message}）",
                professional = "（生成失败：${response.message}）"
            )
            is LlmResponse.StreamChunk -> SocialCopySuggestions("Unexpected stream response", "", "")
        }
    }

    override suspend fun analyzeMomentImage(
        imageDescription: String,
        imageBase64: String?
    ): MomentImageAnalysis {
        val userContent = buildString {
            appendLine("本地图片识别信息：")
            appendLine(imageDescription)
            appendLine()
            appendLine("请优先观察图片本身，再参考本地标签，输出适合朋友圈创作的结构化理解结果。")
            appendLine("本地标签只作为弱参考，不能因为 Duck、Waterfowl、Bird 标签就默认写鸭子。")
            appendLine("请根据体型、颈部、嘴形、姿态和环境判断是鸭、鹅、天鹅或其他水禽；如果无法确定具体物种，使用“白色水禽”等保守表述。")
            appendLine("不要编造图片中看不见的品牌、地点、人物身份、事件或物种细节。")
        }

        val slot = AiFunctionSlot.MOMENT_IMAGE_UNDERSTANDING
        val (provider, modelName) = aiRouteResolver.resolve(slot)

        val primary = analyzeMomentImageWithProvider(
            provider = provider,
            imageDescription = imageDescription,
            userContent = userContent,
            imageBase64 = imageBase64,
            modelOverride = modelName
        )

        Log.d(
            TAG,
            "analyzeMomentImage slot=${slot.key} provider=${provider.providerName} " +
                "type=${provider.providerType} model=$modelName " +
                "hasImage=${!imageBase64.isNullOrBlank()}"
        )
        if (primary != null) {
            return primary
        }
        if (!imageBase64.isNullOrBlank()) {
            throw IllegalStateException("图片理解失败：未获得可解析的图文理解结果，请检查当前图片理解模型的 API Key、模型权限或网络后重试")
        }
        return fallbackMomentImageAnalysis(imageDescription)
    }
    override suspend fun generateMomentCopy(
        imageDescription: String,
        analysis: MomentImageAnalysis,
        imageBase64: String?,
        styleName: String,
        styleDescription: String,
        additionalInfo: String?,
        personalExpression: PersonalExpressionPromptContext?
    ): MomentCopyResult {
        val systemPrompt = buildMomentCopyPrompt(styleName, styleDescription, personalExpression)
        val userContent = buildString {
            appendLine("图片本地识别信息：")
            appendLine(imageDescription)
            appendLine()
            appendLine("AI 图片理解摘要：")
            appendLine("主体：${analysis.subject}")
            appendLine("场景：${analysis.scene}")
            appendLine("氛围：${analysis.mood}")
            appendLine("适合主题：${analysis.themes.joinToString("、").ifBlank { "日常记录" }}")
            appendLine("发布理解：${analysis.insight}")
            if (!additionalInfo.isNullOrBlank()) {
                appendLine()
                appendLine("用户补充信息：${additionalInfo.trim()}")
            }
        }

        val slot = AiFunctionSlot.MOMENT_COPY_GENERATION
        val (provider, modelName) = aiRouteResolver.resolve(slot)
        if (provider.providerType == LlmProviderType.VIVO_ON_DEVICE && !imageBase64.isNullOrBlank()) {
            val bitmap = decodeBase64Bitmap(imageBase64)
            if (bitmap != null) {
                val prompt = buildMomentCopyVisionPrompt(
                    systemPrompt = systemPrompt,
                    userContent = userContent,
                    styleName = styleName
                )
                val visionResult = vivoOnDeviceProvider.describeBitmap(bitmap, prompt)
                bitmap.recycle()
                if (visionResult.success) {
                    return parseMomentCopyResult(visionResult.content, styleName)
                }
                Log.w(
                    TAG,
                    "moment copy vivo on-device vision failed code=${visionResult.errorCode} " +
                        "content=${visionResult.content.take(200)}, fallback to text prompt"
                )
            } else {
                Log.w(TAG, "moment copy vivo on-device image decode failed, fallback to text prompt")
            }
        }

        val imageForCopy = imageBase64.takeUnless {
            provider.providerType == LlmProviderType.VIVO_ON_DEVICE
        }

        val request = LlmRequest(
            model = modelName,
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = systemPrompt),
                LlmMessage(
                    role = LlmRole.USER,
                    content = userContent,
                    imageUrl = imageForCopy
                )
            ),
            maxTokens = 768,
            temperature = 0.82f
        )

        val response = chatWithFallback(
            slot = slot,
            primaryProvider = provider,
            request = request,
            purpose = "slot_${slot.key}"
        )

        return when (response) {
            is LlmResponse.Complete -> parseMomentCopyResult(response.content, styleName)
            is LlmResponse.Error -> MomentCopyResult(
                suggestions = listOf(
                    MomentCopySuggestion(styleName = styleName, content = "（生成失败：${response.message}）")
                )
            )
            is LlmResponse.StreamChunk -> MomentCopyResult(
                suggestions = listOf(MomentCopySuggestion(styleName = styleName, content = "Unexpected stream response"))
            )
        }
    }

    override fun chatStream(systemPrompt: String, userMessage: String): Flow<LlmResponse.StreamChunk> {
        return chatStreamFor(inferFunctionSlot(systemPrompt, userMessage), systemPrompt, userMessage)
    }

    override fun chatStreamFor(
        slot: AiFunctionSlot,
        systemPrompt: String,
        userMessage: String
    ): Flow<LlmResponse.StreamChunk> {
        val (provider, modelName) = aiRouteResolver.resolve(slot)
        val request = LlmRequest(
            model = modelName,
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = systemPrompt),
                LlmMessage(role = LlmRole.USER, content = userMessage)
            ),
            stream = true
        )
        return flow {
            var emittedContent = false
            var fallbackError: LlmResponse.Error? = null

            provider.chatStream(request).collect { chunk ->
                if (!emittedContent &&
                    chunk.finished &&
                    chunk.delta.startsWith("Error:", ignoreCase = true)
                ) {
                    fallbackError = LlmResponse.Error("API_ERROR", chunk.delta.removePrefix("Error:").trim())
                    return@collect
                }
                if (chunk.delta.isNotEmpty()) {
                    emittedContent = true
                }
                emit(chunk)
            }

            val primaryError = fallbackError
            if (!emittedContent &&
                primaryError != null &&
                provider.providerType != fallbackProvider.providerType &&
                primaryError.shouldFallbackToBackup(provider)
            ) {
                Log.w(TAG, "stream primary failed for slot=${slot.key}, switching to ${fallbackProvider.providerName}")
                fallbackProvider.chatStream(
                    request.copy(model = fallbackProvider.defaultModel)
                ).collect { emit(it) }
            }
        }
    }

    private suspend fun shouldUseFallback(
        slot: AiFunctionSlot,
        provider: LlmProvider
    ): Boolean {
        if (slot == AiFunctionSlot.CHAT_SCREENSHOT_OCR) {
            return false
        }
        return try {
            provider.validateApiKey()
        } catch (e: Exception) {
            Log.w(TAG, "fallback provider validation failed provider=${provider.providerName}", e)
            false
        }
    }

    private fun inferFunctionSlot(systemPrompt: String, userMessage: String): AiFunctionSlot {
        val combined = "$systemPrompt\n$userMessage"
        return when {
            combined.contains("朋友圈") || combined.contains("文案") ->
                AiFunctionSlot.MOMENT_COPY_GENERATION
            combined.contains("OCR", ignoreCase = true) || combined.contains("截图") ->
                AiFunctionSlot.CHAT_SCREENSHOT_DIALOGUE_ANALYSIS
            else -> AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION
        }
    }

    // ========== JSON 解析辅助方法 ==========

    /**
     * 从 LLM 响应中解析回复建议
     * 支持从 markdown 代码块中提取 JSON
     */
    private fun parseReplySuggestions(content: String): ReplySuggestions {
        val jsonString = extractJson(content)
        return try {
            json.decodeFromString<ReplySuggestions>(jsonString)
        } catch (e: Exception) {
            // 尝试宽松解析
            tryParseReplySuggestions(content)
        }
    }

    /**
     * 从 LLM 响应中解析朋友圈文案
     */
    private fun parseSocialCopySuggestions(content: String): SocialCopySuggestions {
        val jsonString = extractJson(content)
        return try {
            json.decodeFromString<SocialCopySuggestions>(jsonString)
        } catch (e: Exception) {
            tryParseSocialCopySuggestions(content)
        }
    }

    /**
     * 从 LLM 响应中解析朋友圈创作图片理解结果
     */
    private suspend fun analyzeMomentImageWithProvider(
        provider: LlmProvider,
        imageDescription: String,
        userContent: String,
        imageBase64: String?,
        modelOverride: String? = null
    ): MomentImageAnalysis? {
        if (provider is VivoOnDeviceProvider && !imageBase64.isNullOrBlank()) {
            val bitmap = decodeBase64Bitmap(imageBase64)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode moment image for vivo on-device provider")
                return null
            }
            return try {
                val prompt = buildString {
                    appendLine(SYSTEM_PROMPT_MOMENT_IMAGE_ANALYSIS)
                    appendLine()
                    appendLine(userContent)
                }
                val result = provider.describeBitmap(bitmap, prompt)
                if (result.success && result.content.isNotBlank()) {
                    Log.d(
                        TAG,
                        "analyzeMomentImage vivo on-device complete " +
                            "callVit=${result.callVitMs}ms generation=${result.generationMs}ms"
                    )
                    parseMomentImageAnalysis(result.content, imageDescription)
                } else {
                    Log.e(
                        TAG,
                        "analyzeMomentImage vivo on-device failed code=${result.errorCode} content=${result.content.take(200)}"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "analyzeMomentImage vivo on-device exception", e)
                null
            } finally {
                bitmap.recycle()
            }
        }

        val request = LlmRequest(
            model = modelOverride ?: provider.defaultModel,
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = SYSTEM_PROMPT_MOMENT_IMAGE_ANALYSIS),
                LlmMessage(
                    role = LlmRole.USER,
                    content = userContent,
                    imageUrl = imageBase64
                )
            ),
            maxTokens = 768,
            temperature = 0.2f
        )

        Log.d(
            TAG,
            "analyzeMomentImageWithProvider provider=${provider.providerName} " +
                "type=${provider.providerType} model=${request.model} hasImage=${request.messages.any { !it.imageUrl.isNullOrBlank() }}"
        )
        return when (val response = try {
            provider.chat(request)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeMomentImage provider exception provider=${provider.providerName}", e)
            LlmResponse.Error("EXCEPTION", e.message ?: "Unknown image understanding exception")
        }) {
            is LlmResponse.Complete -> {
                Log.d(TAG, "analyzeMomentImage complete")
                parseMomentImageAnalysis(response.content, imageDescription)
            }
            is LlmResponse.Error -> {
                Log.e(TAG, "analyzeMomentImage error code=${response.code} message=${response.message}")
                null
            }
            is LlmResponse.StreamChunk -> {
                Log.d(TAG, "analyzeMomentImage unexpected stream chunk")
                null
            }
        }
    }

    private fun decodeBase64Bitmap(imageBase64: String): android.graphics.Bitmap? {
        val payload = imageBase64.substringAfter(',', imageBase64).trim()
        if (payload.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun buildMomentCopyVisionPrompt(
        systemPrompt: String,
        userContent: String,
        styleName: String
    ): String {
        return """
$systemPrompt

请直接观察图片，并结合下面的本地识别、图片理解摘要和用户补充信息生成朋友圈文案。
不要只描述图片，不要输出分析过程，不要输出 Markdown。
输出严格 JSON：
{"suggestions":[{"styleName":"$styleName","content":"文案1"},{"styleName":"$styleName","content":"文案2"},{"styleName":"$styleName","content":"文案3"},{"styleName":"$styleName","content":"文案4"}]}

要求：
1. 文案必须适合直接发朋友圈，真实自然，不要广告腔。
2. 不要编造图片里看不到的地点、人物、品牌、事件。
3. 每条文案角度不同，尽量短一点。

$userContent
        """.trimIndent()
    }

    private fun parseMomentImageAnalysis(
        content: String,
        fallbackDescription: String
    ): MomentImageAnalysis? {
        val jsonString = extractJson(content)
        return try {
            json.decodeFromString<MomentImageAnalysis>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse vivo image analysis response", e)
            null
        }
    }

    /**
     * 从 LLM 响应中解析朋友圈创作文案
     */
    private fun parseMomentCopyResult(content: String, styleName: String): MomentCopyResult {
        val jsonString = extractJson(content)
        val parsed = try {
            json.decodeFromString<MomentCopyResult>(jsonString)
        } catch (e: Exception) {
            tryParseMomentCopyResult(content, styleName)
        }
        val normalized = parsed.suggestions
            .mapNotNull { suggestion ->
                val text = suggestion.content.trim()
                if (text.isBlank()) null else suggestion.copy(
                    styleName = suggestion.styleName.ifBlank { styleName },
                    content = text
                )
            }
        return MomentCopyResult(normalized)
    }

    /**
     * 从文本中提取 JSON（处理 markdown 代码块包裹的情况）
     */
    private fun extractJson(content: String): String {
        // 尝试从 ```json ... ``` 代码块中提取
        val jsonBlockRegex = """```(?:json)?\s*([\s\S]*?)```""".toRegex()
        val match = jsonBlockRegex.find(content)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        // 尝试直接找到 JSON 对象
        val startIndex = content.indexOf('{')
        val endIndex = content.lastIndexOf('}')
        if (startIndex >= 0 && endIndex > startIndex) {
            return content.substring(startIndex, endIndex + 1)
        }
        return content
    }

    /**
     * 宽松解析回复建议（当标准 JSON 解析失败时）
     */
    private fun tryParseReplySuggestions(content: String): ReplySuggestions {
        val lines = content.lines().filter { it.contains("：") || it.contains(":") }
        var formal = ""
        var casual = ""
        var humorous = ""

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.contains("formal") || lower.contains("正式") -> {
                    formal = line.substringAfter("：").substringAfter(":").trim()
                }
                lower.contains("casual") || lower.contains("轻松") -> {
                    casual = line.substringAfter("：").substringAfter(":").trim()
                }
                lower.contains("humorous") || lower.contains("幽默") -> {
                    humorous = line.substringAfter("：").substringAfter(":").trim()
                }
            }
        }

        return ReplySuggestions(
            formal = formal.ifEmpty { content.take(100) },
            casual = casual.ifEmpty { content.take(100) },
            humorous = humorous.ifEmpty { content.take(100) }
        )
    }

    /**
     * 宽松解析朋友圈文案
     */
    private fun tryParseSocialCopySuggestions(content: String): SocialCopySuggestions {
        val lines = content.lines().filter { it.contains("：") || it.contains(":") }
        var literary = ""
        var funny = ""
        var professional = ""

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.contains("literary") || lower.contains("文艺") -> {
                    literary = line.substringAfter("：").substringAfter(":").trim()
                }
                lower.contains("funny") || lower.contains("搞笑") -> {
                    funny = line.substringAfter("：").substringAfter(":").trim()
                }
                lower.contains("professional") || lower.contains("专业") -> {
                    professional = line.substringAfter("：").substringAfter(":").trim()
                }
            }
        }

        return SocialCopySuggestions(
            literary = literary.ifEmpty { content.take(100) },
            funny = funny.ifEmpty { content.take(100) },
            professional = professional.ifEmpty { content.take(100) }
        )
    }

    private fun fallbackMomentImageAnalysis(description: String): MomentImageAnalysis {
        val labels = description.substringAfter("图片内容标签：", "")
            .lineSequence()
            .firstOrNull()
            ?.split("、", ",")
            ?.map { it.trim() }
            ?.mapNotNull { normalizeLocalVisualLabel(it) }
            ?.filter { it.isNotBlank() && it != "未识别到标签" }
            ?.distinct()
            .orEmpty()

        val hasFoodSignal = labels.any { it == "食物" || it == "餐食" || it == "蔬菜" }
        val nonGenericLabels = labels.filterNot {
            it in setOf("金属", "物体", "未知内容", "生活照片") ||
                (hasFoodSignal && it == "植物")
        }
        val subject = when {
            hasFoodSignal -> "餐食内容"
            nonGenericLabels.contains("水禽") -> "水禽"
            nonGenericLabels.contains("水鸟") -> "水鸟"
            else -> nonGenericLabels.firstOrNull() ?: "画面内容"
        }
        val themes = if (hasFoodSignal) {
            listOf("美食", "日常记录")
        } else {
            nonGenericLabels.take(3).ifEmpty { listOf("日常记录") }
        }

        return MomentImageAnalysis(
            subject = subject,
            scene = "生活场景",
            mood = "自然、真实",
            themes = themes,
            insight = "这张图片适合先确认画面主体后，再用轻松自然的方式记录当下。"
        )
    }

    private fun normalizeLocalVisualLabel(label: String): String? {
        val value = label.trim()
        if (value.isBlank()) return null

        return when (value.lowercase()) {
            "food", "cuisine", "dish", "meal", "vegetable", "vegetables", "produce" -> "食物"
            "duck", "ducks" -> "水禽"
            "waterfowl" -> "水鸟"
            "bird", "birds" -> "鸟"
            "metal" -> "金属"
            "animal", "animals", "pet", "pets" -> "动物"
            "fish", "goldfish", "koi", "carp" -> "鱼"
            "plant", "plants", "flower", "flowers" -> "植物"
            "water" -> "水面"
            "sky" -> "天空"
            "tree", "trees" -> "树木"
            "person", "people", "human", "face" -> "人物"
            "vehicle", "car", "transport" -> "交通工具"
            "building", "architecture" -> "建筑"
            "object", "objects" -> "物体"
            "tire", "tyre", "wheel" -> null
            "unknown", "unidentified" -> "未知内容"
            else -> value
        }
    }

    private fun tryParseMomentCopyResult(content: String, styleName: String): MomentCopyResult {
        val lines = content.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .trim()
                    .substringAfter("：")
                    .substringAfter(":")
                    .trim()
                    .trim('"')
            }
            .filter { it.length >= 4 }
            .take(4)

        val suggestions = if (lines.isNotEmpty()) {
            lines.map { MomentCopySuggestion(styleName = styleName, content = it) }
        } else {
            listOf(MomentCopySuggestion(styleName = styleName, content = content.take(100)))
        }

        return MomentCopyResult(suggestions)
    }
}
