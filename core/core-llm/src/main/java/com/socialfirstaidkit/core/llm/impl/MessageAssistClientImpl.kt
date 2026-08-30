package com.socialfirstaidkit.core.llm.impl

import com.socialfirstaidkit.core.data.personalexpression.EmojiUsage
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionFeedbackSample
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionProfile
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionPromptContext
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionUpdateSuggestion
import com.socialfirstaidkit.core.data.personalexpression.clampStyleValue
import com.socialfirstaidkit.core.data.personalexpression.sentenceLengthLabel
import com.socialfirstaidkit.core.llm.model.ChatMessage
import com.socialfirstaidkit.core.llm.model.ChatSession
import com.socialfirstaidkit.core.llm.model.SenderType
import com.socialfirstaidkit.core.llm.api.LlmClient
import com.socialfirstaidkit.core.llm.api.MessageAssistClient
import com.socialfirstaidkit.core.llm.api.ReplyStyle
import com.socialfirstaidkit.core.llm.api.ScreenshotAnalysisResult
import com.socialfirstaidkit.core.llm.api.SingleMessageContextAnalysis
import com.socialfirstaidkit.core.llm.api.SenderType as ApiSenderType
import com.socialfirstaidkit.core.llm.api.AnalyzedMessage
import com.socialfirstaidkit.core.llm.model.LlmMessage
import com.socialfirstaidkit.core.llm.model.LlmRequest
import com.socialfirstaidkit.core.llm.model.LlmResponse
import com.socialfirstaidkit.core.llm.model.LlmRole
import com.socialfirstaidkit.core.llm.model.ReplySuggestions
import com.socialfirstaidkit.core.llm.model.StyledReplyResult
import com.socialfirstaidkit.core.llm.model.ScreenshotAnalysisJson
import com.socialfirstaidkit.core.llm.model.MessageJson
import com.socialfirstaidkit.core.llm.model.SingleMessageContextAnalysisJson
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息助手LLM客户端实现
 */
@Singleton
class MessageAssistClientImpl @Inject constructor(
    private val llmClient: LlmClient
) : MessageAssistClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private fun buildPersonalExpressionPrompt(
            personalExpression: PersonalExpressionPromptContext?
        ): String {
            val context = personalExpression ?: return ""
            val profile = context.profile
            val relationshipKey = normalizeRelationshipKey(context.relationship)
            val offset = profile.relationshipOffsets[relationshipKey]
                ?: profile.relationshipOffsets["friend"]
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

# 个人表达档案（已启用，低于用户本次明确要求和当前身份/风格）
请让回复更贴近用户本人的日常表达，但不要为了模仿而违背事实边界、用户补充说明或当前选择的身份/风格。
当前关系：${context.relationship}
摘要标签：${context.summaryTags.joinToString("、")}

核心表达基线：
- 直接程度：${profile.coreStyle.directness}/5
- 温暖程度：${profile.coreStyle.warmth}/5
- 正式程度：${profile.coreStyle.formality}/5
- 句子长度：${profile.coreStyle.sentenceLength}/5（${sentenceLengthLabel(profile.coreStyle.sentenceLength)}）
- 幽默程度：${profile.coreStyle.humor}/5

语言细节：
- emoji 使用：${profile.languageMarkers.emojiUsage.label}
- 语气词密度：${profile.languageMarkers.fillerWords}/5
- 常用表达参考：$preferred

场景策略：
- 安慰：${profile.sceneStyles.comfortStyle.label}
- 拒绝：${profile.sceneStyles.refusalStyle.label}
- 道歉：${profile.sceneStyles.apologyStyle.label}
- 冲突：${profile.sceneStyles.conflictStyle.label}

关系偏移：
- 正式度偏移：${offset?.formalityOffset ?: 0f}
- 直接度偏移：${offset?.directnessOffset ?: 0f}
- 温暖度偏移：${offset?.warmthOffset ?: 0f}

必须避开的表达：
$avoided

表达样例：
$samples

如果当前选择的身份或风格与个人表达档案冲突，以当前选择为准；不冲突的措辞、长度、语气词和避雷项继续参考档案。
            """.trimIndent()
        }

        private fun normalizeRelationshipKey(relationship: String): String {
            return when {
                relationship.contains("领导") || relationship.contains("上级") || relationship.contains("老师") -> "leader"
                relationship.contains("同事") || relationship.contains("客户") || relationship.contains("合作") -> "colleague"
                relationship.contains("家人") || relationship.contains("父母") -> "family"
                relationship.contains("恋人") || relationship.contains("伴侣") -> "romantic"
                relationship.contains("陌生") || relationship.contains("其他") -> "stranger"
                else -> "friend"
            }
        }


        private fun defaultStyleVariationRules(contextPrefix: String = ""): String {
            val prefix = if (contextPrefix.isBlank()) "" else contextPrefix
            return """

# 风格差异硬性要求（必须明显）
${prefix}三条建议不能只是同义改写，必须让用户一眼看出风格差异：
1. option1 礼貌正式：完整句，称呼和语气更稳妥，少用语气词，不使用玩笑；适合发给领导、同事或不太熟的人。
2. option2 轻松随意：像朋友聊天，短句自然，可以有“好呀/行/没问题”这类口语，但不要太冷淡。
3. option3 幽默化解：必须有轻巧的玩笑、俏皮转折或自嘲式表达，让气氛更松；不能只把“好呀”换成“好嘞”。
如果用户明确要求同意、答应或接受，三条都保持同意立场，但仍必须按以上三种风格写出明显差异。
禁止三条使用相同开头、相同句式或只替换一两个词。
            """.trimIndent()
        }

        private fun selectedStyleHardRules(style: ReplyStyle, styleDesc: String): String {
            return when (style) {
                ReplyStyle.FORMAL -> """

# ${styleDesc}风格硬性要求
三条都必须显得礼貌、稳妥、得体：用完整句，少用语气词和玩笑；表达尊重、配合和分寸感。
三条之间要分别体现：简洁确认 / 稳妥补充 / 更周到体面。禁止写成普通朋友口语。
                """.trimIndent()
                ReplyStyle.CASUAL -> """

# ${styleDesc}风格硬性要求
三条都必须像熟人聊天：自然、松弛、有口语感，可以用“好呀”“行”“没问题”等，但不能敷衍。
三条之间要分别体现：爽快答应 / 轻松热络 / 带一点期待感。禁止写得正式或像通知。
                """.trimIndent()
                ReplyStyle.HUMOROUS -> """

# ${styleDesc}风格硬性要求
三条都必须明显有幽默感：每条至少包含一个轻巧的玩笑、俏皮转折、夸张但不离谱的口语梗或自嘲式表达。
幽默只能来自语气和表达，不得编造地点、装备、身体状态、具体安排等未提供事实。
三条之间要分别体现：俏皮爽快 / 轻松调侃 / 有画面感但不新增事实。禁止只输出“好嘞、没问题、可以呀”这类普通同意句。
                """.trimIndent()
                ReplyStyle.EMPATHETIC -> """

# ${styleDesc}风格硬性要求
三条都必须先接住对方情绪或关系氛围，再回应事情本身；语气温和、有理解感。
三条之间要分别体现：理解对方 / 表达在意 / 温柔确认。禁止写成冷冰冰的任务确认。
                """.trimIndent()
                ReplyStyle.ASSERTIVE -> """

# ${styleDesc}风格硬性要求
三条都必须立场清楚、短而有边界，不绕弯，不讨好，也不攻击。
三条之间要分别体现：直接明确 / 友好坚定 / 留少量余地。禁止含糊、拖延或过度解释。
                """.trimIndent()
                ReplyStyle.DAI_ZHOU_LI -> ""
                ReplyStyle.OTHER -> """

# 自定义风格硬性要求
三条都必须明显服从用户给出的自定义风格，不要退回普通回复。
三条之间要分别在句式、长度和表达角度上拉开差异，不能只做同义改写。
                """.trimIndent()
            }
        }

        /**
         * 获取单文本回复模式的系统Prompt
         * 根据风格动态生成
         */
        fun getSingleReplyPrompt(
            style: ReplyStyle?,
            customStyleDescription: String = "",
            additionalNotes: String = "",
            personalExpression: PersonalExpressionPromptContext? = null
        ): String {
            // 构建补充说明部分（放在最前面，优先级最高）
            val additionalNotesPart = if (additionalNotes.isNotBlank()) {
                """
# 用户补充背景与明确要求（最高优先级，必须严格遵守）
${additionalNotes}

如果这里包含用户希望表达的真实态度、结果或立场，例如“同意”“答应”“接受”“愿意去”“可以参加”“想拒绝”“不要答应”等，生成的所有回复都必须与该立场一致，三条候选只能做语气和措辞差异，不能拆成同意/拒绝/待确认三个立场。
补充说明不是可选参考，而是硬约束；当它和默认社交策略冲突时，以补充说明为准。
如果用户要求同意或接受，绝对不要生成拒绝、推脱、身体不适、请假、下次再说等相反含义。

            """.trimIndent()
            } else ""

            val personalExpressionPart = buildPersonalExpressionPrompt(personalExpression)

            val basePrompt = """
你是一个深谙人性和社交潜规则的"高情商社交大师"。你的核心任务是帮助用户生成合适的回复建议。

# 基本原则
1. 如果用户有补充背景或明确要求（见上方"用户补充背景与明确要求"），必须优先遵循用户的真实意图。
2. 默认先按普通、友好的日常沟通理解对方消息。朋友约打球、吃饭、聊天、见面、帮忙看一眼这类普通邀请，不能解读成“攻击性”“越界”“道德绑架”，除非对方明确施压、责备、威胁、PUA 或连续强迫。
3. 如果用户没有特殊要求，只有在对方确实越界、道德绑架、甩锅或强迫时，才帮助用户保护边界感。
4. 打太极法则只用于需要拒绝或暂缓的场景：语气友好、理由轻量、不给对方贴负面标签。不要把普通邀请说成“攻击性很强”“差点答应了”这类过度防御表达。
5. 保留回旋余地：永远不要把话说死，让用户在社交中立于不败之地。

# 抉择类问题处理（最高优先级之一）
当对方消息是在邀请、询问或催促用户做选择，例如“去不去”“来不来”“要不要”“答不答应”“一起吗”“有空吗”“打球吗”“吃饭吗”，且用户没有在补充说明中明确给出立场时，三条建议才覆盖三个方向：
1. option1：同意/愿意去，语气自然积极。
2. option2：拒绝/不去，语气委婉友好。
3. option3：再想想/待确认，保留余地，说明稍后确认。
不要把这三条都写成拒绝，也不要都写成防御或推脱。

# 事实边界（必须遵守）
1. 只能使用用户输入、对方消息、补充背景中已经出现的信息，不要自行脑补未提供的事实。
2. 不要编造任何未出现的具体细节，包括但不限于：具体活动类型、地点、工具/装备、人物、时间、原因、身体状态、工作安排、承诺、历史背景、进度或结果。
3. 如果原话本身比较模糊，要保留模糊表达，用对方原话或更泛化的措辞承接，不要为了显得生动而添加新事实。
4. 幽默、礼貌和共情只能来自语气和表达方式，不能来自虚构事实。
5. 示例：对方只说“打球”，不能写“球拍”“球场”“篮球”“羽毛球”；对方只说“帮个忙”，不能编具体任务；对方只说“下午见”，不能编具体地点。

只有当用户没有要求同意/接受，且语境确实需要拒绝时，才使用拒绝策略。拒绝表达应轻量、自然、友好，优先使用“今天不太方便/我先看看安排/下次约”这类日常说法；不要上升到攻击性、边界防御或对对方动机的评判。
            """.trimIndent()

            return if (style == null) {
                // 默认模式：三种不同风格
                additionalNotesPart + basePrompt + personalExpressionPart + """

# 任务要求
如果用户在补充说明中明确要求同意、答应、接受、愿意去或可以参加，则三条都必须是同意/接受方向，只做风格差异，不得生成拒绝或待确认。只有当这是抉择类问题且用户没有明确立场时，才按上方“抉择类问题处理”输出：option1 同意、option2 拒绝、option3 再想想。
如果不是抉择类问题，请生成三种不同风格的回复建议：
1. option1：礼貌正式版 - 适合正式场合，语气礼貌得体
2. option2：轻松随意版 - 适合朋友之间，语气自然轻松
3. option3：幽默化解版 - 用幽默的方式化解尴尬或压力
${defaultStyleVariationRules()}
# 输出格式（严格JSON）
{
  "option1": "第一条回复",
  "option2": "第二条回复",
  "option3": "第三条回复"
}
                """.trimIndent()
            } else if (style == ReplyStyle.DAI_ZHOU_LI) {
                // 大周礼风格模式
                additionalNotesPart + basePrompt + personalExpressionPart + """

# 任务要求
请生成三条大周礼风格的回复建议，内容要有差异，给用户多个选择。如果用户在补充说明中明确给出立场，三条都必须服从该立场；只有抉择类问题且用户没有明确立场时，三条才分别覆盖：同意、拒绝、再想想。

【大周礼风格强制约束，必须100%遵守】
1. 不固定起手式；请使用自然的古文/雅正文言表达，可依语境变化开头。
2. 整体采用半文言语体，用词克制、端庄、守礼，符合周代君子言行风范。
3. 必须引用周礼、古君子典故、先贤言行作为说理依据，不可空泛发言。
4. 核心精神必须遵循「发乎情止乎礼」：哪怕表达拒绝、反驳、不满，也必须体面、克制、不骂人、不带脏字、不情绪化，只用道义和礼法说理。
5. 禁止出现任何现代网络用语、表情包梗、英文缩写，禁止直白口语化表达。
6. 全文长度控制在3-5句话，80-150字之间，短小精悍，适合作为消息回复。
7. 结尾必须收束得体，留有余地，不把话说死，符合君子绝交不出恶语的风范。

【禁止项，违反即不合格】
❌ 禁止机械套用固定开头
❌ 禁止直白说出现代大白话（如：我不做、我没钱、你错了）
❌ 禁止出现情绪化用词、辱骂、阴阳怪气、嘲讽语气
❌ 禁止太长，超过200字
❌ 禁止脱离周礼内核，写成普通古风鸡汤或现代白话套古词
❌ 禁止写成诗歌、骈文，必须是可以直接发送的对话回复语气。

# 输出格式（严格JSON）
{
  "option1": "第一条大周礼风格的回复",
  "option2": "第二条大周礼风格的回复",
  "option3": "第三条大周礼风格的回复"
}
                """.trimIndent()
            } else if (style == ReplyStyle.OTHER && customStyleDescription.isNotBlank()) {
                // 自定义风格模式
                additionalNotesPart + basePrompt + personalExpressionPart + """

# 任务要求
请生成三条回复建议，风格要求如下：
${customStyleDescription}

内容要有差异，给用户多个选择。如果用户在补充说明中明确给出立场，三条都必须服从该立场；只有抉择类问题且用户没有明确立场时，三条才分别覆盖：同意、拒绝、再想想。
${selectedStyleHardRules(ReplyStyle.OTHER, "自定义")}
# 输出格式（严格JSON）
{
  "option1": "第一条回复",
  "option2": "第二条回复",
  "option3": "第三条回复"
}
                """.trimIndent()
            } else {
                // 特定风格模式：三条都是同风格
                val styleDesc = when (style) {
                    ReplyStyle.FORMAL -> "礼貌正式"
                    ReplyStyle.CASUAL -> "轻松随意"
                    ReplyStyle.HUMOROUS -> "幽默化解"
                    ReplyStyle.EMPATHETIC -> "共情理解"
                    ReplyStyle.ASSERTIVE -> "坚定明确"
                    ReplyStyle.DAI_ZHOU_LI -> "大周礼"
                    ReplyStyle.OTHER -> "自定义"
                }
                additionalNotesPart + basePrompt + personalExpressionPart + """

# 任务要求
请生成三条${styleDesc}风格的回复建议，内容要有差异，给用户多个选择。如果用户在补充说明中明确给出立场，三条都必须服从该立场；只有抉择类问题且用户没有明确立场时，三条才分别覆盖：同意、拒绝、再想想。
${selectedStyleHardRules(style, styleDesc)}
# 输出格式（严格JSON）
{
  "option1": "第一条${styleDesc}风格的回复",
  "option2": "第二条${styleDesc}风格的回复",
  "option3": "第三条${styleDesc}风格的回复"
}
                """.trimIndent()
            }
        }

        /**
         * 获取上下文记忆回复模式的系统Prompt
         * 根据风格动态生成
         */
        fun getContextualReplyPrompt(
            style: ReplyStyle?,
            customStyleDescription: String = "",
            additionalNotes: String = "",
            personalExpression: PersonalExpressionPromptContext? = null
        ): String {
            // 构建补充说明部分（放在最前面，优先级最高）
            val additionalNotesPart = if (additionalNotes.isNotBlank()) {
                """
# 用户补充背景与明确要求（最高优先级，必须严格遵守）
${additionalNotes}

如果这里包含用户希望表达的真实态度、结果或立场，例如“同意”“答应”“接受”“愿意去”“可以参加”“想拒绝”“不要答应”等，生成的所有回复都必须与该立场一致，三条候选只能做语气和措辞差异，不能拆成同意/拒绝/待确认三个立场。
补充说明不是可选参考，而是硬约束；当它和默认社交策略冲突时，以补充说明为准。
如果用户要求同意或接受，绝对不要生成拒绝、推脱、身体不适、请假、下次再说等相反含义。

            """.trimIndent()
            } else ""

            val personalExpressionPart = buildPersonalExpressionPrompt(personalExpression)

            val basePrompt = """
你是一个深谙人性和社交潜规则的"高情商社交大师"。你的核心任务是帮助用户生成合适的回复建议。

# 基本原则
1. 如果用户有补充背景或明确要求（见上方"用户补充背景与明确要求"），必须优先遵循用户的真实意图。
2. 默认先按普通、友好的日常沟通理解对方消息。朋友约打球、吃饭、聊天、见面、帮忙看一眼这类普通邀请，不能解读成“攻击性”“越界”“道德绑架”，除非对方明确施压、责备、威胁、PUA 或连续强迫。
3. 如果用户没有特殊要求，只有在对方确实越界、道德绑架、甩锅或强迫时，才帮助用户保护边界感。
4. 打太极法则只用于需要拒绝或暂缓的场景：语气友好、理由轻量、不给对方贴负面标签。不要把普通邀请说成“攻击性很强”“差点答应了”这类过度防御表达。
5. 保留回旋余地：永远不要把话说死，让用户在社交中立于不败之地。
6. 上下文感知：请仔细分析对话历史，理解双方的关系动态、话题进展和情绪变化，给出最贴合当前情境的回复。
7. 回复目标：优先回复“当前需要回复的目标消息”，不要把对话历史里较早的话题当成最新诉求；如果最后一条是我方消息，也不要替对方回复我方消息。
8. 质量要求：回复必须具体承接目标消息里的关键词或诉求，避免“好的、收到、没问题、可以呀”这类空泛短句；每条建议至少包含一个明确动作、态度、边界或情绪承接点。
9. 像真人微信：句子要能直接发送，避免客服腔、总结腔、AI 分析腔；不要解释“我理解你的意思是”。

# 抉择类问题处理（最高优先级之一）
当对方消息是在邀请、询问或催促用户做选择，例如“去不去”“来不来”“要不要”“答不答应”“一起吗”“有空吗”“打球吗”“吃饭吗”，且用户没有在补充说明中明确给出立场时，三条建议才覆盖三个方向：
1. option1：同意/愿意去，语气自然积极。
2. option2：拒绝/不去，语气委婉友好。
3. option3：再想想/待确认，保留余地，说明稍后确认。
不要把这三条都写成拒绝，也不要都写成防御或推脱。

# 对话分析要点
- 识别对方的核心诉求和潜在意图
- 分析对话中的权力关系和社交压力
- 考虑历史对话中提到的约定、承诺或背景信息
- 根据关系亲疏调整回复的边界感

# 事实边界（必须遵守）
1. 只能使用用户输入、对方消息、补充背景中已经出现的信息，不要自行脑补未提供的事实。
2. 不要编造任何未出现的具体细节，包括但不限于：具体活动类型、地点、工具/装备、人物、时间、原因、身体状态、工作安排、承诺、历史背景、进度或结果。
3. 如果原话本身比较模糊，要保留模糊表达，用对方原话或更泛化的措辞承接，不要为了显得生动而添加新事实。
4. 幽默、礼貌和共情只能来自语气和表达方式，不能来自虚构事实。
5. 示例：对方只说“打球”，不能写“球拍”“球场”“篮球”“羽毛球”；对方只说“帮个忙”，不能编具体任务；对方只说“下午见”，不能编具体地点。

只有当用户没有要求同意/接受，且语境确实需要拒绝时，才使用拒绝策略。拒绝表达应轻量、自然、友好，优先使用“今天不太方便/我先看看安排/下次约”这类日常说法；不要上升到攻击性、边界防御或对对方动机的评判。
            """.trimIndent()

            return if (style == null) {
                // 默认模式：三种不同风格
                additionalNotesPart + basePrompt + personalExpressionPart + """

# 任务要求
如果用户在补充说明中明确要求同意、答应、接受、愿意去或可以参加，则三条都必须是同意/接受方向，只做风格差异，不得生成拒绝或待确认。只有当这是抉择类问题且用户没有明确立场时，才按上方“抉择类问题处理”输出：option1 同意、option2 拒绝、option3 再想想。
如果不是抉择类问题，请基于对话上下文生成三种不同风格的回复建议：
1. option1：礼貌正式版 - 适合正式场合，语气礼貌得体
2. option2：轻松随意版 - 适合朋友之间，语气自然轻松
3. option3：幽默化解版 - 用幽默的方式化解尴尬或压力
${defaultStyleVariationRules("基于对话上下文，")}
# 输出格式（严格JSON）
{
  "option1": "第一条回复",
  "option2": "第二条回复",
  "option3": "第三条回复"
}
                """.trimIndent()
            } else if (style == ReplyStyle.DAI_ZHOU_LI) {
                // 大周礼风格模式
                additionalNotesPart + basePrompt + personalExpressionPart + """

# 任务要求
请基于对话上下文，生成三条大周礼风格的回复建议，内容要有差异，给用户多个选择。如果用户在补充说明中明确给出立场，三条都必须服从该立场；只有抉择类问题且用户没有明确立场时，三条才分别覆盖：同意、拒绝、再想想。

【大周礼风格强制约束，必须100%遵守】
1. 不固定起手式；请使用自然的古文/雅正文言表达，可依语境变化开头。
2. 整体采用半文言语体，用词克制、端庄、守礼，符合周代君子言行风范。
3. 必须引用周礼、古君子典故、先贤言行作为说理依据，不可空泛发言。
4. 核心精神必须遵循「发乎情止乎礼」：哪怕表达拒绝、反驳、不满，也必须体面、克制、不骂人、不带脏字、不情绪化，只用道义和礼法说理。
5. 禁止出现任何现代网络用语、表情包梗、英文缩写，禁止直白口语化表达。
6. 全文长度控制在3-5句话，80-150字之间，短小精悍，适合作为消息回复。
7. 结尾必须收束得体，留有余地，不把话说死，符合君子绝交不出恶语的风范。

【禁止项，违反即不合格】
❌ 禁止机械套用固定开头
❌ 禁止直白说出现代大白话（如：我不做、我没钱、你错了）
❌ 禁止出现情绪化用词、辱骂、阴阳怪气、嘲讽语气
❌ 禁止太长，超过200字
❌ 禁止脱离周礼内核，写成普通古风鸡汤或现代白话套古词
❌ 禁止写成诗歌、骈文，必须是可以直接发送的对话回复语气。

# 输出格式（严格JSON）
{
  "option1": "第一条大周礼风格的回复",
  "option2": "第二条大周礼风格的回复",
  "option3": "第三条大周礼风格的回复"
}
                """.trimIndent()
            } else if (style == ReplyStyle.OTHER && customStyleDescription.isNotBlank()) {
                // 自定义风格模式
                additionalNotesPart + basePrompt + personalExpressionPart + """

# 任务要求
请基于对话上下文，生成三条回复建议，风格要求如下：
${customStyleDescription}

内容要有差异，给用户多个选择。如果用户在补充说明中明确给出立场，三条都必须服从该立场；只有抉择类问题且用户没有明确立场时，三条才分别覆盖：同意、拒绝、再想想。
${selectedStyleHardRules(ReplyStyle.OTHER, "自定义")}
# 输出格式（严格JSON）
{
  "option1": "第一条回复",
  "option2": "第二条回复",
  "option3": "第三条回复"
}
                """.trimIndent()
            } else {
                // 特定风格模式：三条都是同风格
                val styleDesc = when (style) {
                    ReplyStyle.FORMAL -> "礼貌正式"
                    ReplyStyle.CASUAL -> "轻松随意"
                    ReplyStyle.HUMOROUS -> "幽默化解"
                    ReplyStyle.EMPATHETIC -> "共情理解"
                    ReplyStyle.ASSERTIVE -> "坚定明确"
                    ReplyStyle.DAI_ZHOU_LI -> "大周礼"
                    ReplyStyle.OTHER -> "自定义"
                }
                additionalNotesPart + basePrompt + personalExpressionPart + """

# 任务要求
请基于对话上下文，生成三条${styleDesc}风格的回复建议，内容要有差异，给用户多个选择。如果用户在补充说明中明确给出立场，三条都必须服从该立场；只有抉择类问题且用户没有明确立场时，三条才分别覆盖：同意、拒绝、再想想。
${selectedStyleHardRules(style, styleDesc)}
# 输出格式（严格JSON）
{
  "option1": "第一条${styleDesc}风格的回复",
  "option2": "第二条${styleDesc}风格的回复",
  "option3": "第三条${styleDesc}风格的回复"
}
                """.trimIndent()
            }
        }

        /**
         * 截图分析系统Prompt
         */
        val SCREENSHOT_ANALYSIS_PROMPT = """
你是一个对话截图分析专家。请分析用户提供的聊天截图OCR文本，提取以下信息：

# 分析任务
1. 识别聊天对象的名称（通常是聊天界面顶部的标题）
2. 提取对话内容，区分"我"（用户本人）和"对方"（聊天对象）的消息
3. 总结对话的主题和当前状态

# 输出格式（严格JSON）
{
  "contactName": "聊天对象名称",
  "messages": [
    {
      "senderType": "USER 或 CONTACT",
      "senderName": "发送者名称",
      "content": "消息内容",
      "timestamp": "时间戳（如果有）"
    }
  ],
  "conversationSummary": "对话主题和状态总结"
}

# 识别规则（非常重要）
1. 微信聊天界面特征：
   - 顶部标题栏显示聊天对象名称
   - 右侧气泡（通常是绿色/蓝色背景）是"我"（USER）发送的消息
   - 左侧气泡（通常是白色/灰色背景）是"对方"（CONTACT）发送的消息
   - 头像在左侧表示对方，头像在右侧表示自己

2. 消息识别技巧：
   - 寻找时间戳作为分隔点（如：凌晨1:06、7:14等）
   - 同一发送者的连续消息通常在一起
   - 注意消息内容的连贯性，判断是谁在说话
   - 消息内容可能是中文、英文、数字、缩写或中英混排；只要位于真实聊天气泡中，都必须按原文保留，不翻译、不补全、不统一大小写；不要输出 emoji 或纯 emoji 消息
   - 如果某条气泡里出现“姓名：内容”这类被引用的上一条消息片段，随后还有该气泡真正想发送的新内容，那么前者是引用回复片段，不是新的聊天消息
   - 这类引用回复片段只用于帮助理解上下文，绝对不要单独放入messages数组

3. 排除干扰信息：
   - 过滤掉系统提示（如"对方正在输入..."）
   - 过滤掉界面元素（如表情按钮、输入框等），但不要因为内容是英文短句就过滤真实聊天消息
   - 过滤掉状态栏信息（时间、电量、信号等）
   - 过滤掉引用回复中的被引用内容，例如“逍遥：你先洗澡吧”这种嵌在气泡里的引用片段
   - 过滤掉转发卡片、聊天记录引用标题、撤回提示等非当前轮次实际发送的正文
   - 过滤掉图片消息、表情包、emoji、视频、文件卡片、图片预览和“[图片]”这类占位内容；不要把图片里的文字或图片描述当作聊天正文

4. 角色判断优先级：
   - 首先根据气泡位置判断（右=我，左=对方）
   - 其次根据上下文逻辑判断
   - 最后根据消息内容中的自称判断

5. 引用回复专项规则：
   - 如果同一气泡中同时出现“被引用内容”和“当前真正发送的正文”，messages里只保留当前正文
   - 如果无法完全确定是否是引用回复，优先保守处理：宁可不输出该引用片段，也不要把它当作独立聊天消息输出
   - 引用卡片、聊天记录卡片、图片卡片只作为界面噪声处理，不进入messages
        """.trimIndent()

        /**
         * Single-message context analysis prompt.
         */
        private val SINGLE_MESSAGE_CONTEXT_ANALYSIS_PROMPT = """
You are a Chinese conversation analyst for a reply assistant.

Analyze one incoming message and produce the exact structure used by the app's conversation analysis card.
The user needs practical social-reading guidance before generating a reply.

Return strict JSON only:
{
  "scene": "当前沟通场景",
  "tone": "对方语气判断",
  "emotion": "对方情绪判断",
  "intent": "对方真实意图",
  "strategy": "建议回复策略"
}

Rules:
- Write all field values in Chinese.
- Keep each field concise and concrete.
- Do not generate reply suggestions here.
- Do not add markdown, explanations, or extra keys.
        """.trimIndent()

        /**
         * 生成身份/风格描述的系统Prompt
         */
        val IDENTITY_DESCRIPTION_PROMPT = """
你是一个深谙人性和社交潜规则的"高情商社交大师"。
你的任务是为给定的身份名称（如：领导、婆婆、暗恋对象）或风格名称（如：绿茶风格、硬核拒绝、高情商回复）生成一段简短但精准的描述。

# 任务要求
1. 如果是身份模式：描述该身份在社交中的特点、心理诉求、以及与用户交往时需要注意的边界感。
2. 如果是风格模式：描述该回复风格的特点、适用场景、以及能达到的社交效果。
3. 语气要专业、洞察深刻。
4. 字数控制在100字以内。

# 输出格式
直接输出描述文本，不要包含任何JSON或其他标签。
        """.trimIndent()
    }

    override suspend fun generateSingleReply(
        message: String,
        relation: String,
        style: ReplyStyle?,
        customStyleDescription: String,
        additionalNotes: String,
        personalExpression: PersonalExpressionPromptContext?
    ): ReplySuggestions {
        val userMessage = buildString {
            appendLine("对方身份：$relation")
            appendLine("对方最新消息：\"$message\"")
            appendLine("请直接生成可发送给对方的回复，不要总结、解释或输出分析。")
            if (additionalNotes.isNotBlank()) {
                appendLine()
                appendLine("用户补充背景与明确要求：$additionalNotes")
                appendLine("请确保回复结果严格符合上述补充说明。")
            }
            appendLine()
            appendLine("事实边界：不要加入对方消息和补充说明中没有出现的新事实；任何具体活动、地点、工具、人物、时间、原因、身体状态、工作安排、承诺、背景、进度或结果，未明确出现就不能写。")
        }

        val request = LlmRequest(
            model = "",
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = getSingleReplyPrompt(style, customStyleDescription, additionalNotes, personalExpression)),
                LlmMessage(role = LlmRole.USER, content = userMessage)
            ),
            maxTokens = 512,
            temperature = 0.7f
        )

        val response = llmClient.chatFor(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION, request)

        return when (response) {
            is LlmResponse.Complete -> parseStyledReplySuggestions(response.content, style)
            is LlmResponse.Error -> ReplySuggestions(
                formal = "（生成失败：${response.message}）",
                casual = "（生成失败：${response.message}）",
                humorous = "（生成失败：${response.message}）"
            )
            is LlmResponse.StreamChunk -> ReplySuggestions("Unexpected stream response", "", "")
        }
    }

    override suspend fun generateContextualReply(
        session: ChatSession,
        relation: String,
        style: ReplyStyle?,
        customStyleDescription: String,
        additionalNotes: String,
        personalExpression: PersonalExpressionPromptContext?
    ): ReplySuggestions {
        val conversationContext = buildConversationContext(session)
        val replyTarget = session.messages.asReversed().firstOrNull { it.senderType == SenderType.CONTACT }
            ?: session.messages.lastOrNull()
        val userMessage = buildString {
            appendLine("对方身份：$relation")
            appendLine("聊天对象：${session.contactName}")
            appendLine("\n=== 对话历史（按时间顺序） ===")
            appendLine(conversationContext)
            appendLine("\n=== 必须回复的目标消息 ===")
            if (replyTarget != null) {
                appendLine("${replyTarget.senderName}: ${replyTarget.content}")
            }
            appendLine("\n=== 回复任务 ===")
            appendLine("请直接替用户回复上方目标消息，而不是总结对话、评价对方或泛泛附和。")
            appendLine("如果对话最后一条是我方消息，请不要回复我方消息，要回到最近一条对方消息的诉求上继续回应。")
            if (additionalNotes.isNotBlank()) {
                appendLine("\n=== 用户补充背景与明确要求 ===")
                appendLine(additionalNotes)
                appendLine("请确保回复结果严格符合上述补充说明。")
            }
            appendLine("\n=== 事实边界 ===")
            appendLine("不要加入对方消息和补充说明中没有出现的新事实；任何具体活动、地点、工具、人物、时间、原因、身体状态、工作安排、承诺、背景、进度或结果，未明确出现就不能写。")
        }

        val request = LlmRequest(
            model = "",
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = getContextualReplyPrompt(style, customStyleDescription, additionalNotes, personalExpression)),
                LlmMessage(role = LlmRole.USER, content = userMessage)
            ),
            maxTokens = 640,
            temperature = 0.65f
        )

        val response = llmClient.chatFor(AiFunctionSlot.CHAT_CONTEXTUAL_REPLY_GENERATION, request)

        return when (response) {
            is LlmResponse.Complete -> parseStyledReplySuggestions(response.content, style)
            is LlmResponse.Error -> ReplySuggestions(
                formal = "（生成失败：${response.message}）",
                casual = "（生成失败：${response.message}）",
                humorous = "（生成失败：${response.message}）"
            )
            is LlmResponse.StreamChunk -> ReplySuggestions("Unexpected stream response", "", "")
        }
    }

    override suspend fun analyzeSingleMessageContext(
        message: String,
        relation: String,
        style: ReplyStyle?,
        customStyleDescription: String,
        additionalNotes: String
    ): SingleMessageContextAnalysis {
        val userMessage = buildString {
            appendLine("Incoming message: $message")
            appendLine("Relationship context: $relation")
            appendLine("Reply style: ${style?.name ?: "DEFAULT"}")
            if (customStyleDescription.isNotBlank()) {
                appendLine("Custom style description: $customStyleDescription")
            }
            if (additionalNotes.isNotBlank()) {
                appendLine("User notes: $additionalNotes")
            }
        }

        val request = LlmRequest(
            model = "",
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = SINGLE_MESSAGE_CONTEXT_ANALYSIS_PROMPT),
                LlmMessage(role = LlmRole.USER, content = userMessage)
            ),
            maxTokens = 384,
            temperature = 0.2f
        )

        return try {
            when (val response = llmClient.chatFor(AiFunctionSlot.CHAT_SINGLE_CONTEXT_ANALYSIS, request)) {
                is LlmResponse.Complete -> parseSingleMessageContextAnalysis(response.content)
                is LlmResponse.Error -> singleMessageContextAnalysisError(response.message)
                is LlmResponse.StreamChunk -> singleMessageContextAnalysisError("Unexpected stream response")
            }
        } catch (e: Exception) {
            singleMessageContextAnalysisError(e.message ?: "Analysis failed")
        }
    }

    override fun generateSingleReplyStream(
        message: String,
        relation: String,
        style: ReplyStyle?,
        customStyleDescription: String,
        additionalNotes: String,
        personalExpression: PersonalExpressionPromptContext?
    ): Flow<String> = flow {
        val userMessage = buildString {
            appendLine("对方身份：$relation")
            appendLine("对方最新消息：\"$message\"")
        }

        llmClient.chatStreamFor(AiFunctionSlot.CHAT_SINGLE_REPLY_GENERATION, getSingleReplyPrompt(style, customStyleDescription, additionalNotes, personalExpression), userMessage).collect { chunk ->
            emit(chunk.delta)
        }
    }

    override fun generateContextualReplyStream(
        session: ChatSession,
        relation: String,
        style: ReplyStyle?,
        customStyleDescription: String,
        additionalNotes: String,
        personalExpression: PersonalExpressionPromptContext?
    ): Flow<String> = flow {
        val conversationContext = buildConversationContext(session)

        val userMessage = buildString {
            appendLine("对方身份：$relation")
            appendLine("聊天对象：${session.contactName}")
            appendLine("\n=== 对话历史 ===")
            appendLine(conversationContext)
            appendLine("\n=== 当前需要回复的消息 ===")
            val lastMessage = session.messages.lastOrNull()
            if (lastMessage != null) {
                appendLine("${lastMessage.senderName}: ${lastMessage.content}")
            }
        }

        llmClient.chatStreamFor(AiFunctionSlot.CHAT_CONTEXTUAL_REPLY_GENERATION, getContextualReplyPrompt(style, customStyleDescription, additionalNotes, personalExpression), userMessage).collect { chunk ->
            emit(chunk.delta)
        }
    }

    override suspend fun analyzeScreenshot(ocrText: String): ScreenshotAnalysisResult {
        val request = LlmRequest(
            model = "",
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = SCREENSHOT_ANALYSIS_PROMPT),
                LlmMessage(role = LlmRole.USER, content = "OCR识别结果：\n$ocrText")
            ),
            maxTokens = 1024,
            temperature = 0.3f
        )

        return try {
            when (val response = llmClient.chatFor(AiFunctionSlot.CHAT_SCREENSHOT_DIALOGUE_ANALYSIS, request)) {
                is LlmResponse.Complete -> parseScreenshotAnalysis(response.content)
                is LlmResponse.Error -> ScreenshotAnalysisResult(
                    success = false,
                    errorMessage = response.message
                )
                is LlmResponse.StreamChunk -> ScreenshotAnalysisResult(
                    success = false,
                    errorMessage = "Unexpected stream response"
                )
            }
        } catch (e: Exception) {
            ScreenshotAnalysisResult(
                success = false,
                errorMessage = e.message ?: "Analysis failed"
            )
        }
    }

    override suspend fun generateIdentityDescription(name: String, isRelationMode: Boolean): String {
        val userMessage = if (isRelationMode) {
            "请为身份\"$name\"生成一段社交洞察描述。"
        } else {
            "请为回复风格\"$name\"生成一段特点描述。"
        }

        val request = LlmRequest(
            model = "",
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = IDENTITY_DESCRIPTION_PROMPT),
                LlmMessage(role = LlmRole.USER, content = userMessage)
            ),
            maxTokens = 256,
            temperature = 0.7f
        )

        return try {
            when (val response = llmClient.chatFor(AiFunctionSlot.CHAT_IDENTITY_STYLE_DESCRIPTION, request)) {
                is LlmResponse.Complete -> response.content.trim()
                is LlmResponse.Error -> "生成描述失败: ${response.message}"
                is LlmResponse.StreamChunk -> "Unexpected stream response"
            }
        } catch (e: Exception) {
            "生成描述发生异常: ${e.message}"
        }
    }

    /**
     * 构建对话上下文文本
     */
    override suspend fun summarizePersonalExpressionUpdate(
        currentProfile: PersonalExpressionProfile,
        samples: List<PersonalExpressionFeedbackSample>
    ): PersonalExpressionUpdateSuggestion? {
        if (samples.isEmpty()) return null
        val sampleText = samples.take(30).joinToString("\n") { sample ->
            "- [${sample.source}/${sample.relation}/${sample.style}/${sample.suggestionSlot}] ${sample.copiedText}"
        }
        val systemPrompt = """
你是一个中文社交表达风格分析助手。根据用户近期复制采用的 AI 回复样本，分析用户的真实表达偏好，与当前档案对比，给出 5 个核心维度的微调建议。

当前档案的 5 个核心维度（1-5 分）：
- directness（直接程度）：${currentProfile.coreStyle.directness}
- warmth（温暖程度）：${currentProfile.coreStyle.warmth}
- formality（正式程度）：${currentProfile.coreStyle.formality}
- sentenceLength（句子长度）：${currentProfile.coreStyle.sentenceLength}
- humor（幽默程度）：${currentProfile.coreStyle.humor}

分析要求：
1. 观察复制文本的整体特征：句式长短、语气冷暖、正式随意、是否幽默
2. 与当前档案对比，判断哪些维度需要微调
3. 每个维度给出 -0.5 到 +0.5 之间的调整值（0 表示不需要调整）
4. 提取用户偏好的常用表达和希望避开的表达
5. 不要做人格诊断，不要评价用户

返回严格 JSON：
{
  "observations": ["观察1", "观察2"],
  "changes": ["建议修改1", "建议修改2"],
  "directnessAdjust": 0.0,
  "warmthAdjust": 0.0,
  "formalityAdjust": 0.0,
  "sentenceLengthAdjust": 0.0,
  "humorAdjust": 0.0,
  "preferredExpressions": ["常用表达1"],
  "avoidedExpressions": ["避雷表达1"]
}
        """.trimIndent()
        val userMessage = """
当前档案摘要：
${currentProfile.readableSummary()}
当前避雷项：${currentProfile.avoidedExpressions.joinToString("、")}
当前常用表达：${currentProfile.preferredExpressions.joinToString("、")}

近期复制样本（共 ${samples.size} 条）：
$sampleText
        """.trimIndent()

        val request = LlmRequest(
            model = "",
            messages = listOf(
                LlmMessage(role = LlmRole.SYSTEM, content = systemPrompt),
                LlmMessage(role = LlmRole.USER, content = userMessage)
            ),
            maxTokens = 800,
            temperature = 0.2f
        )

        return try {
            when (val response = llmClient.chatFor(AiFunctionSlot.PROFILE_EXPRESSION_UPDATE_SUMMARY, request)) {
                is LlmResponse.Complete -> {
                    val parsed = json.decodeFromString<ExpressionUpdateSummaryJson>(extractJson(response.content))
                    val updated = buildUpdatedProfileFromSummary(currentProfile, parsed)
                    PersonalExpressionUpdateSuggestion(
                        observations = parsed.observations.ifEmpty { listOf("最近复制的回复呈现出更稳定的表达偏好。") },
                        changes = parsed.changes.ifEmpty { listOf("微调表达维度。") },
                        previewBefore = currentProfile.sampleReplies["comfort"].orEmpty(),
                        previewAfter = updated.sampleReplies["comfort"].orEmpty()
                            .ifBlank { currentProfile.sampleReplies["comfort"].orEmpty() },
                        updatedProfile = updated
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildUpdatedProfileFromSummary(
        profile: PersonalExpressionProfile,
        summary: ExpressionUpdateSummaryJson
    ): PersonalExpressionProfile {
        return profile.copy(
            coreStyle = profile.coreStyle.copy(
                directness = clampStyleValue(profile.coreStyle.directness + summary.directnessAdjust),
                warmth = clampStyleValue(profile.coreStyle.warmth + summary.warmthAdjust),
                formality = clampStyleValue(profile.coreStyle.formality + summary.formalityAdjust),
                sentenceLength = clampStyleValue(profile.coreStyle.sentenceLength + summary.sentenceLengthAdjust),
                humor = clampStyleValue(profile.coreStyle.humor + summary.humorAdjust)
            ),
            preferredExpressions = (profile.preferredExpressions + summary.preferredExpressions)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(12),
            avoidedExpressions = (profile.avoidedExpressions + summary.avoidedExpressions)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(12),
            version = profile.version + 1,
            lastUpdatedAt = System.currentTimeMillis()
        )
    }

    @Serializable
    private data class ExpressionUpdateSummaryJson(
        val observations: List<String> = emptyList(),
        val changes: List<String> = emptyList(),
        val directnessAdjust: Float = 0f,
        val warmthAdjust: Float = 0f,
        val formalityAdjust: Float = 0f,
        val sentenceLengthAdjust: Float = 0f,
        val humorAdjust: Float = 0f,
        val preferredExpressions: List<String> = emptyList(),
        val avoidedExpressions: List<String> = emptyList()
    )

    private fun buildConversationContext(session: ChatSession): String {
        return session.messages.takeLast(20).joinToString("\n") { msg ->
            val senderLabel = when (msg.senderType) {
                SenderType.USER -> "我"
                SenderType.CONTACT -> msg.senderName
            }
            "$senderLabel: ${msg.content}"
        }
    }

    /**
     * 获取风格描述
     */
    private fun getStyleDescription(style: ReplyStyle): String {
        return when (style) {
            ReplyStyle.FORMAL -> "正式礼貌"
            ReplyStyle.CASUAL -> "轻松随意"
            ReplyStyle.HUMOROUS -> "幽默化解"
            ReplyStyle.EMPATHETIC -> "共情理解"
            ReplyStyle.ASSERTIVE -> "坚定明确"
            ReplyStyle.DAI_ZHOU_LI -> "大周礼"
            ReplyStyle.OTHER -> "自定义"
        }
    }

    /**
     * 解析带风格的回复建议
     * 根据风格决定如何映射 option1/2/3 到 formal/casual/humorous
     */
    private fun parseStyledReplySuggestions(content: String, style: ReplyStyle?): ReplySuggestions {
        val jsonString = extractJson(content)
        return try {
            // 尝试解析为新的格式（option1, option2, option3）
            val styledResult = json.decodeFromString<StyledReplyResult>(jsonString)

            if (style == null) {
                // 默认模式：option1=formal, option2=casual, option3=humorous
                ReplySuggestions(
                    formal = styledResult.option1,
                    casual = styledResult.option2,
                    humorous = styledResult.option3
                )
            } else {
                // 特定风格模式：三条都是同风格，直接映射
                ReplySuggestions(
                    formal = styledResult.option1,
                    casual = styledResult.option2,
                    humorous = styledResult.option3
                )
            }
        } catch (e: Exception) {
            // 尝试兼容旧格式（formal, casual, humorous）
            try {
                json.decodeFromString<ReplySuggestions>(jsonString)
            } catch (e2: Exception) {
                // 宽松解析
                val fallback = tryParseStyledReplySuggestions(content)
                if (fallback.formal.isEmpty() && fallback.casual.isEmpty() && fallback.humorous.isEmpty()) {
                    ReplySuggestions(
                        formal = content.take(200),
                        casual = "（解析失败，请重试）",
                        humorous = "（解析失败，请重试）"
                    )
                } else {
                    fallback
                }
            }
        }
    }

    /**
     * 宽松解析带风格的回复建议
     */
    private fun tryParseStyledReplySuggestions(content: String): ReplySuggestions {
        val lines = content.lines().filter { it.contains(":") || it.contains("：") }
        var option1 = ""
        var option2 = ""
        var option3 = ""

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.contains("option1") || lower.contains("option 1") || lower.contains("第一条") -> {
                    option1 = line.substringAfter(":").substringAfter("：").trim()
                }
                lower.contains("option2") || lower.contains("option 2") || lower.contains("第二条") -> {
                    option2 = line.substringAfter(":").substringAfter("：").trim()
                }
                lower.contains("option3") || lower.contains("option 3") || lower.contains("第三条") -> {
                    option3 = line.substringAfter(":").substringAfter("：").trim()
                }
                // 兼容旧格式
                lower.contains("formal") || lower.contains("正式") -> {
                    option1 = line.substringAfter(":").substringAfter("：").trim()
                }
                lower.contains("casual") || lower.contains("轻松") || lower.contains("随意") -> {
                    option2 = line.substringAfter(":").substringAfter("：").trim()
                }
                lower.contains("humorous") || lower.contains("幽默") -> {
                    option3 = line.substringAfter(":").substringAfter("：").trim()
                }
            }
        }

        return ReplySuggestions(
            formal = option1.ifEmpty { content.take(100) },
            casual = option2.ifEmpty { content.take(100) },
            humorous = option3.ifEmpty { content.take(100) }
        )
    }

    /**
     * 解析截图分析结果
     */
    private fun parseSingleMessageContextAnalysis(content: String): SingleMessageContextAnalysis {
        val jsonString = extractJson(content)
        return try {
            val parsed = json.decodeFromString<SingleMessageContextAnalysisJson>(jsonString)
            SingleMessageContextAnalysis(
                scene = parsed.scene.ifBlank { "日常沟通场景" },
                tone = parsed.tone.ifBlank { "语气需要结合上下文判断" },
                emotion = parsed.emotion.ifBlank { "情绪倾向不明显" },
                intent = parsed.intent.ifBlank { "可能希望得到回应或确认" },
                strategy = parsed.strategy.ifBlank { "先接住话题，再给出简短自然的回应" }
            )
        } catch (e: Exception) {
            singleMessageContextAnalysisError("Failed to parse analysis: ${e.message}")
        }
    }

    private fun singleMessageContextAnalysisError(message: String): SingleMessageContextAnalysis {
        return SingleMessageContextAnalysis(
            scene = "模型分析失败",
            tone = message.take(80),
            emotion = "未能获取有效分析",
            intent = "未能获取有效分析",
            strategy = "请稍后重试或直接重新生成回复"
        )
    }

    private fun parseScreenshotAnalysis(content: String): ScreenshotAnalysisResult {
        val jsonString = extractJson(content)
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.decodeFromString<ScreenshotAnalysisJson>(jsonString)

            ScreenshotAnalysisResult(
                success = true,
                contactName = parsed.contactName,
                messages = parsed.messages.map {
                    AnalyzedMessage(
                        senderType = if (it.senderType == "USER") ApiSenderType.USER else ApiSenderType.CONTACT,
                        senderName = it.senderName,
                        content = it.content,
                        timestamp = it.timestamp
                    )
                },
                conversationSummary = parsed.conversationSummary
            )
        } catch (e: Exception) {
            ScreenshotAnalysisResult(
                success = false,
                errorMessage = "Failed to parse analysis: ${e.message}"
            )
        }
    }

    /**
     * 宽松解析回复建议
     */
    private fun tryParseReplySuggestions(content: String): ReplySuggestions {
        val lines = content.lines().filter { it.contains(":") || it.contains("：") }
        var formal = ""
        var casual = ""
        var humorous = ""

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.contains("formal") || lower.contains("正式") -> {
                    formal = line.substringAfter(":").substringAfter("：").trim()
                }
                lower.contains("casual") || lower.contains("轻松") || lower.contains("随意") -> {
                    casual = line.substringAfter(":").substringAfter("：").trim()
                }
                lower.contains("humorous") || lower.contains("幽默") -> {
                    humorous = line.substringAfter(":").substringAfter("：").trim()
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
     * 从文本中提取JSON
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
}
