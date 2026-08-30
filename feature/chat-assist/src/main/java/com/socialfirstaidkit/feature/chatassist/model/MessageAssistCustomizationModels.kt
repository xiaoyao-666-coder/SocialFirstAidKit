package com.socialfirstaidkit.feature.chatassist.model

import com.socialfirstaidkit.core.llm.api.ReplyStyle
import kotlinx.serialization.Serializable

const val DEFAULT_RELATION_ID = "friend"
const val DEFAULT_STYLE_ID = "default"

@Serializable
data class CustomMessageAssistOption(
    val id: String,
    val name: String,
    val description: String,
    val order: Int = 0,
    val isSystemPreset: Boolean = false,
    val systemKey: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class PresetRelationOption(
    val id: String,
    val label: String,
    val promptContext: String
)

data class PresetStyleOption(
    val id: String,
    val label: String,
    val replyStyle: ReplyStyle?,
    val promptDescription: String
)

data class SelectedStyleConfig(
    val replyStyle: ReplyStyle?,
    val customStyleDescription: String
)

val presetRelationOptions = listOf(
    PresetRelationOption(
        id = "leader",
        label = "领导",
        promptContext = "领导（需要礼貌、尊重、专业，注意分寸和效率）"
    ),
    PresetRelationOption(
        id = "colleague",
        label = "同事",
        promptContext = "同事（职场沟通，注意协作、边界和效率）"
    ),
    PresetRelationOption(
        id = "friend",
        label = "朋友",
        promptContext = "朋友（熟悉、自然，可以更轻松但保持体面）"
    ),
    PresetRelationOption(
        id = "family",
        label = "家人",
        promptContext = "家人（亲近、真诚、关心感更强）"
    )
)

val presetStyleOptions = listOf(
    PresetStyleOption(
        id = DEFAULT_STYLE_ID,
        label = "默认",
        replyStyle = null,
        promptDescription = "由系统根据场景自动平衡语气、礼貌和边界感。"
    ),
    PresetStyleOption(
        id = "formal",
        label = "正式",
        replyStyle = ReplyStyle.FORMAL,
        promptDescription = "措辞礼貌、稳妥，适合更正式的沟通场景。"
    ),
    PresetStyleOption(
        id = "casual",
        label = "随意",
        replyStyle = ReplyStyle.CASUAL,
        promptDescription = "更轻松自然，适合熟悉关系下的日常交流。"
    ),
    PresetStyleOption(
        id = "humorous",
        label = "幽默",
        replyStyle = ReplyStyle.HUMOROUS,
        promptDescription = "带一点轻巧幽默，帮助缓和气氛。"
    ),
    PresetStyleOption(
        id = "empathetic",
        label = "共情",
        replyStyle = ReplyStyle.EMPATHETIC,
        promptDescription = "先理解对方感受，再给出温和回应。"
    ),
    PresetStyleOption(
        id = "assertive",
        label = "坚定",
        replyStyle = ReplyStyle.ASSERTIVE,
        promptDescription = "立场清晰，表达有边界但不过度生硬。"
    ),
    PresetStyleOption(
        id = "daizhou_li",
        label = "大周礼",
        replyStyle = ReplyStyle.DAI_ZHOU_LI,
        promptDescription = "古文感更强的雅正语体，用词克制、端庄、守礼，核心精神遵循“发乎情止乎礼”，但不固定起手式。"
    )
)

fun defaultRelationManagedOptions(): List<CustomMessageAssistOption> {
    return presetRelationOptions.mapIndexed { index, option ->
        CustomMessageAssistOption(
            id = option.id,
            name = option.label,
            description = option.promptContext,
            order = index,
            isSystemPreset = true,
            systemKey = option.id
        )
    }
}

fun defaultStyleManagedOptions(): List<CustomMessageAssistOption> {
    return presetStyleOptions.mapIndexed { index, option ->
        CustomMessageAssistOption(
            id = option.id,
            name = option.label,
            description = option.promptDescription,
            order = index,
            isSystemPreset = true,
            systemKey = option.id
        )
    }
}

fun List<CustomMessageAssistOption>.sortedForDisplay(): List<CustomMessageAssistOption> {
    return sortedWith(compareBy<CustomMessageAssistOption> { it.order }.thenBy { it.createdAt })
}

fun buildRelationPromptContext(
    selectedId: String,
    options: List<CustomMessageAssistOption>
): String {
    val selectedOption = options.firstOrNull { it.id == selectedId }
        ?: options.sortedForDisplay().firstOrNull()
        ?: defaultRelationManagedOptions().first()
    return if (selectedOption.isSystemPreset) {
        selectedOption.description
    } else {
        "${selectedOption.name}（${selectedOption.description}）"
    }
}

fun buildStyleConfig(
    selectedId: String,
    options: List<CustomMessageAssistOption>
): SelectedStyleConfig {
    val selectedOption = options.firstOrNull { it.id == selectedId }
        ?: options.sortedForDisplay().firstOrNull()
        ?: defaultStyleManagedOptions().first()

    if (!selectedOption.isSystemPreset) {
        return SelectedStyleConfig(
            replyStyle = ReplyStyle.OTHER,
            customStyleDescription = "请使用“${selectedOption.name}”这种回复风格。风格说明：${selectedOption.description}"
        )
    }

    val presetStyle = presetStyleOptions.firstOrNull { it.id == selectedOption.systemKey }
        ?: presetStyleOptions.first { it.id == DEFAULT_STYLE_ID }

    return SelectedStyleConfig(
        replyStyle = presetStyle.replyStyle,
        customStyleDescription = if (presetStyle.replyStyle == null) "" else presetStyle.promptDescription
    )
}
