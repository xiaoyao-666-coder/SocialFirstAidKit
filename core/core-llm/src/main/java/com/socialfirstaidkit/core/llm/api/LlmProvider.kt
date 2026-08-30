package com.socialfirstaidkit.core.llm.api

import com.socialfirstaidkit.core.llm.model.LlmRequest
import com.socialfirstaidkit.core.llm.model.LlmResponse
import kotlinx.coroutines.flow.Flow

/**
 * LLM 提供者统一接口
 *
 * 所有 LLM 服务商（通义千问、文心一言、蓝心大模型）均需实现此接口。
 * 采用策略模式，支持运行时切换不同的 LLM 提供者。
 */
interface LlmProvider {

    /** 提供者名称（用于 UI 展示） */
    val providerName: String

    /** 提供者类型标识 */
    val providerType: com.socialfirstaidkit.core.llm.model.LlmProviderType

    /** 默认使用的模型名称 */
    val defaultModel: String

    /**
     * 发送聊天请求（非流式）
     *
     * @param request LLM 请求参数
     * @return 完整响应或错误
     */
    suspend fun chat(request: LlmRequest): LlmResponse

    /**
     * 发送聊天请求（流式）
     *
     * @param request LLM 请求参数
     * @return 流式响应块 Flow
     */
    fun chatStream(request: LlmRequest): Flow<LlmResponse.StreamChunk>

    /**
     * 验证 API Key 是否有效
     *
     * @return true 如果 API Key 有效
     */
    suspend fun validateApiKey(): Boolean
}
