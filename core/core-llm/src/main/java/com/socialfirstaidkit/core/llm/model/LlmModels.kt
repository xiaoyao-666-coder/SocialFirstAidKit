package com.socialfirstaidkit.core.llm.model

import kotlinx.serialization.Serializable

/**
 * LLM 消息角色
 */
@Serializable
enum class LlmRole {
    SYSTEM, USER, ASSISTANT
}

/**
 * LLM 消息
 */
@Serializable
data class LlmMessage(
    val role: LlmRole,
    val content: String,
    val imageUrl: String? = null  // 多模态支持
)

/**
 * LLM 请求
 */
@Serializable
data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val stream: Boolean = false
)

/**
 * Token 使用量
 */
@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

/**
 * LLM 响应（密封类）
 */
sealed class LlmResponse {
    /**
     * 完整响应
     */
    data class Complete(
        val content: String,
        val usage: TokenUsage = TokenUsage()
    ) : LlmResponse()

    /**
     * 流式响应块
     */
    data class StreamChunk(
        val delta: String,
        val finished: Boolean = false
    ) : LlmResponse()

    /**
     * 错误响应
     */
    data class Error(
        val code: String = "UNKNOWN",
        val message: String
    ) : LlmResponse()
}

/**
 * 社交回复建议结果（JSON 解析后的结构化数据）
 */
@Serializable
data class ReplySuggestions(
    val formal: String,      // 礼貌正式版
    val casual: String,      // 轻松随意版
    val humorous: String     // 幽默化解版
)

/**
 * 朋友圈文案建议结果
 */
@Serializable
data class SocialCopySuggestions(
    val literary: String,    // 文艺版
    val funny: String,       // 搞笑版
    val professional: String // 专业版
)

/**
 * 朋友圈创作图片理解结果
 */
@Serializable
data class MomentImageAnalysis(
    val subject: String,
    val scene: String,
    val mood: String,
    val themes: List<String> = emptyList(),
    val insight: String
)

/**
 * 朋友圈创作文案建议
 */
@Serializable
data class MomentCopySuggestion(
    val styleName: String,
    val content: String
)

/**
 * 朋友圈创作文案生成结果
 */
@Serializable
data class MomentCopyResult(
    val suggestions: List<MomentCopySuggestion> = emptyList()
)

/**
 * LLM 提供者类型
 */
enum class LlmProviderType {
    TONGYI,   // 通义千问
    WENXIN,   // 文心一言
    LANXIN,   // 蓝心大模型
    GEMINI,   // Google Gemini
    VIVO,     // Vivo AIGC (formerly BACKUP_OPENAI)
    VIVO_ON_DEVICE, // Vivo BlueLM on-device 3B
    CUSTOM, // User-defined custom API
    MIMO, // 小米 MiMo 大模型
    AGNES // Agnes AI
}
