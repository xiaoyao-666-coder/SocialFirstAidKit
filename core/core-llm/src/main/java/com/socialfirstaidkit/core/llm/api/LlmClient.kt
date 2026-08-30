package com.socialfirstaidkit.core.llm.api

import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionPromptContext
import com.socialfirstaidkit.core.llm.model.*
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import kotlinx.coroutines.flow.Flow

/**
 * LLM 客户端门面类
 *
 * 提供统一的 LLM 调用接口，内部根据配置选择具体的 Provider 实现。
 * 支持社交回复建议、朋友圈文案生成等场景化的 Prompt 构建。
 */
interface LlmClient {

    /**
     * 获取当前活跃的 Provider
     */
    val activeProvider: LlmProvider

    /**
     * 切换 LLM 提供者
     */
    fun switchProvider(type: LlmProviderType)

    /**
     * 发送通用聊天请求，内部处理主 API 到备用 API 的降级。
     */
    suspend fun chat(request: LlmRequest): LlmResponse

    /**
     * 按功能点路由到用户选择的服务商和模型。
     */
    suspend fun chatFor(
        slot: AiFunctionSlot,
        request: LlmRequest
    ): LlmResponse

    /**
     * 生成社交回复建议（核心功能）
     *
     * @param context 对方发送的消息内容
     * @param relation 对方身份（领导/同事/朋友/家人）
     * @param styleHistory 用户历史风格描述（可选）
     * @return 3 种风格的回复建议
     */
    suspend fun generateReplySuggestions(
        context: String,
        relation: String,
        styleHistory: String? = null
    ): ReplySuggestions

    /**
     * 生成朋友圈文案
     *
     * @param imageDescription 图片描述（OCR 文字 + 标签）
     * @param imageBase64 压缩后的图片 Base64 数据（可选，用于大模型视觉分析）
     * @param style 文案风格（可选）
     * @param customStyleDescription 自定义风格描述（可选）
     * @param additionalInfo 用户补充信息（可选）
     * @return 3 种风格的文案
     */
    suspend fun generateSocialCopy(
        imageDescription: String,
        imageBase64: String? = null,
        style: ReplyStyle? = null,
        customStyleDescription: String? = null,
        additionalInfo: String? = null
    ): SocialCopySuggestions

    /**
     * 分析朋友圈创作图片
     *
     * @param imageDescription 本地识别出的 OCR 文字和图片标签
     * @param imageBase64 压缩后的图片 Base64 数据（可选，用于大模型视觉分析）
     * @return 结构化图片理解摘要
     */
    suspend fun analyzeMomentImage(
        imageDescription: String,
        imageBase64: String? = null
    ): MomentImageAnalysis

    /**
     * 生成朋友圈创作文案
     *
     * @param imageDescription 本地识别出的 OCR 文字和图片标签
     * @param analysis 结构化图片理解摘要
     * @param imageBase64 压缩后的图片 Base64 数据（可选，用于大模型视觉分析）
     * @param styleName 用户选择的文案风格名称
     * @param styleDescription 用户选择的文案风格说明
     * @param additionalInfo 用户补充信息（可选）
     * @return 多条可发布朋友圈文案
     */
    suspend fun generateMomentCopy(
        imageDescription: String,
        analysis: MomentImageAnalysis,
        imageBase64: String? = null,
        styleName: String,
        styleDescription: String,
        additionalInfo: String? = null,
        personalExpression: PersonalExpressionPromptContext? = null
    ): MomentCopyResult

    /**
     * 通用聊天（流式）
     */
    fun chatStream(
        systemPrompt: String,
        userMessage: String
    ): Flow<LlmResponse.StreamChunk>

    /**
     * 按功能点路由的流式聊天。
     */
    fun chatStreamFor(
        slot: AiFunctionSlot,
        systemPrompt: String,
        userMessage: String
    ): Flow<LlmResponse.StreamChunk>
}
