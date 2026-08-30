package com.socialfirstaidkit.core.llm.api

import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionFeedbackSample
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionProfile
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionPromptContext
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionUpdateSuggestion
import com.socialfirstaidkit.core.llm.model.ChatSession
import com.socialfirstaidkit.core.llm.model.ReplySuggestions
import kotlinx.coroutines.flow.Flow

/**
 * 消息助手LLM客户端接口
 * 提供两种回复模式：单文本模式和上下文记忆模式
 */
interface MessageAssistClient {

    /**
     * 单文本回复模式
     * 仅根据用户输入的单句文本生成回复建议
     *
     * @param message 对方发送的消息内容
     * @param relation 对方身份（领导/同事/朋友/家人/其他）
     * @param style 回复风格偏好（可选）
     * @param customStyleDescription 自定义回复风格描述（当style为OTHER时使用）
     * @param additionalNotes 补充说明（可选）
     * @return 三种风格的回复建议
     */
    suspend fun generateSingleReply(
        message: String,
        relation: String,
        style: ReplyStyle? = null,
        customStyleDescription: String = "",
        additionalNotes: String = "",
        personalExpression: PersonalExpressionPromptContext? = null
    ): ReplySuggestions

    /**
     * 上下文记忆回复模式
     * 根据对话历史上下文生成更贴合实际的回复建议
     *
     * @param session 对话会话（包含完整对话历史）
     * @param relation 对方身份
     * @param style 回复风格偏好（可选）
     * @param customStyleDescription 自定义回复风格描述（当style为OTHER时使用）
     * @param additionalNotes 补充说明（可选）
     * @return 三种风格的回复建议
     */
    suspend fun generateContextualReply(
        session: ChatSession,
        relation: String,
        style: ReplyStyle? = null,
        customStyleDescription: String = "",
        additionalNotes: String = "",
        personalExpression: PersonalExpressionPromptContext? = null
    ): ReplySuggestions

    suspend fun analyzeSingleMessageContext(
        message: String,
        relation: String,
        style: ReplyStyle? = null,
        customStyleDescription: String = "",
        additionalNotes: String = ""
    ): SingleMessageContextAnalysis {
        return SingleMessageContextAnalysis(
            scene = "当前分析客户端未接入",
            tone = "无法判断语气",
            emotion = "无法判断情绪",
            intent = "无法判断意图",
            strategy = "请切换到支持分析的模型客户端"
        )
    }

    /**
     * 流式生成回复建议（单文本模式）
     */
    fun generateSingleReplyStream(
        message: String,
        relation: String,
        style: ReplyStyle? = null,
        customStyleDescription: String = "",
        additionalNotes: String = "",
        personalExpression: PersonalExpressionPromptContext? = null
    ): Flow<String>

    /**
     * 流式生成回复建议（上下文模式）
     */
    fun generateContextualReplyStream(
        session: ChatSession,
        relation: String,
        style: ReplyStyle? = null,
        customStyleDescription: String = "",
        additionalNotes: String = "",
        personalExpression: PersonalExpressionPromptContext? = null
    ): Flow<String>

    /**
     * 分析对话截图内容
     * 从OCR识别结果中提取对话信息
     *
     * @param ocrText OCR识别的原始文本
     * @return 解析后的对话信息
     */
    suspend fun analyzeScreenshot(
        ocrText: String
    ): ScreenshotAnalysisResult

    /**
     * 根据身份/风格名称生成描述
     *
     * @param name 身份或风格名称
     * @param isRelationMode true=身份模式，false=风格模式
     * @return 生成的描述文本
     */
    suspend fun generateIdentityDescription(
        name: String,
        isRelationMode: Boolean
    ): String

    suspend fun summarizePersonalExpressionUpdate(
        currentProfile: PersonalExpressionProfile,
        samples: List<PersonalExpressionFeedbackSample>
    ): PersonalExpressionUpdateSuggestion? = null
}

/**
 * 回复风格
 */
enum class ReplyStyle {
    FORMAL,     // 正式礼貌
    CASUAL,     // 轻松随意
    HUMOROUS,   // 幽默化解
    EMPATHETIC, // 共情理解
    ASSERTIVE,  // 坚定明确
    DAI_ZHOU_LI, // 大周礼文风
    OTHER       // 其他（自定义）
}

/**
 * 截图分析结果
 */
data class ScreenshotAnalysisResult(
    val success: Boolean,
    val contactName: String? = null,
    val messages: List<AnalyzedMessage> = emptyList(),
    val conversationSummary: String? = null,
    val errorMessage: String? = null
)

data class SingleMessageContextAnalysis(
    val scene: String,
    val tone: String,
    val emotion: String,
    val intent: String,
    val strategy: String
)

/**
 * 分析后的消息
 */
data class AnalyzedMessage(
    val senderType: SenderType,
    val senderName: String,
    val content: String,
    val timestamp: String? = null
)

enum class SenderType {
    USER,
    CONTACT
}
