package com.socialfirstaidkit.feature.socialcopy.repository

import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.feature.socialcopy.model.MomentImageStyleOption
import com.socialfirstaidkit.feature.socialcopy.model.SocialCopyStyleOption
import com.socialfirstaidkit.feature.socialcopy.model.defaultMomentImageStyleOptions
import com.socialfirstaidkit.feature.socialcopy.model.defaultSocialCopyStyleOptions
import com.socialfirstaidkit.feature.socialcopy.model.sortedForDisplay
import com.socialfirstaidkit.feature.socialcopy.model.sortedImageStylesForDisplay
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialCopyStyleRepository @Inject constructor(
    private val secureStorage: SecureStorage
) {
    fun getStyleOptions(): List<SocialCopyStyleOption> {
        val raw = secureStorage.getString(KEY_STYLES) ?: return defaultSocialCopyStyleOptions()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SocialCopyStyleOption(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    description = item.getString("description"),
                    order = item.optInt("order", index),
                    isSystemPreset = item.optBoolean("isSystemPreset", false),
                    systemKey = item.optString("systemKey").ifBlank { null },
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            }.mergeWithPresets()
        }.getOrElse { defaultSocialCopyStyleOptions() }
    }

    fun saveStyleOptions(options: List<SocialCopyStyleOption>) {
        val array = JSONArray()
        options.sortedForDisplay().forEach { option ->
            array.put(
                JSONObject()
                    .put("id", option.id)
                    .put("name", option.name)
                    .put("description", option.description)
                    .put("order", option.order)
                    .put("isSystemPreset", option.isSystemPreset)
                    .put("systemKey", option.systemKey)
                    .put("createdAt", option.createdAt)
            )
        }
        secureStorage.saveString(KEY_STYLES, array.toString())
    }

    fun getDeletedStyleOptions(): List<SocialCopyStyleOption> {
        val raw = secureStorage.getString(KEY_DELETED_STYLES) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SocialCopyStyleOption(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    description = item.getString("description"),
                    order = item.optInt("order", index),
                    isSystemPreset = item.optBoolean("isSystemPreset", false),
                    systemKey = item.optString("systemKey").ifBlank { null },
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            }.sortedByDescending { it.createdAt }
        }.getOrElse { emptyList() }
    }

    fun saveDeletedStyleOptions(options: List<SocialCopyStyleOption>) {
        val array = JSONArray()
        options.sortedByDescending { it.createdAt }.forEach { option ->
            array.put(
                JSONObject()
                    .put("id", option.id)
                    .put("name", option.name)
                    .put("description", option.description)
                    .put("order", option.order)
                    .put("isSystemPreset", option.isSystemPreset)
                    .put("systemKey", option.systemKey)
                    .put("createdAt", option.createdAt)
            )
        }
        secureStorage.saveString(KEY_DELETED_STYLES, array.toString())
    }

    fun getImageStyleOptions(): List<MomentImageStyleOption> {
        val raw = secureStorage.getString(KEY_IMAGE_STYLES) ?: return defaultMomentImageStyleOptions()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                MomentImageStyleOption(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    description = item.getString("description"),
                    prompt = item.getString("prompt"),
                    order = item.optInt("order", index),
                    isSystemPreset = item.optBoolean("isSystemPreset", false),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            }.upgradeLegacyImagePresetPrompts().sortedImageStylesForDisplay()
        }.getOrElse { defaultMomentImageStyleOptions() }
    }

    fun saveImageStyleOptions(options: List<MomentImageStyleOption>) {
        val array = JSONArray()
        options.sortedImageStylesForDisplay().forEach { option ->
            array.put(
                JSONObject()
                    .put("id", option.id)
                    .put("name", option.name)
                    .put("description", option.description)
                    .put("prompt", option.prompt)
                    .put("order", option.order)
                    .put("isSystemPreset", option.isSystemPreset)
                    .put("createdAt", option.createdAt)
            )
        }
        secureStorage.saveString(KEY_IMAGE_STYLES, array.toString())
    }

    fun getDeletedImageStyleOptions(): List<MomentImageStyleOption> {
        val raw = secureStorage.getString(KEY_DELETED_IMAGE_STYLES) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                MomentImageStyleOption(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    description = item.getString("description"),
                    prompt = item.getString("prompt"),
                    order = item.optInt("order", index),
                    isSystemPreset = item.optBoolean("isSystemPreset", false),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            }.sortedByDescending { it.createdAt }
        }.getOrElse { emptyList() }
    }

    fun saveDeletedImageStyleOptions(options: List<MomentImageStyleOption>) {
        val array = JSONArray()
        options.sortedByDescending { it.createdAt }.forEach { option ->
            array.put(
                JSONObject()
                    .put("id", option.id)
                    .put("name", option.name)
                    .put("description", option.description)
                    .put("prompt", option.prompt)
                    .put("order", option.order)
                    .put("isSystemPreset", option.isSystemPreset)
                    .put("createdAt", option.createdAt)
            )
        }
        secureStorage.saveString(KEY_DELETED_IMAGE_STYLES, array.toString())
    }

    private fun List<SocialCopyStyleOption>.mergeWithPresets(): List<SocialCopyStyleOption> {
        val presets = defaultSocialCopyStyleOptions()
        val presetById = presets.associateBy { it.id }
        val seenIds = mutableSetOf<String>()
        val merged = mutableListOf<SocialCopyStyleOption>()

        sortedForDisplay().forEach { stored ->
            val preset = presetById[stored.id]
            val option = when {
                preset != null -> preset.copy(order = stored.order, createdAt = stored.createdAt)
                !stored.isSystemPreset -> stored
                else -> null
            }
            if (option != null && seenIds.add(option.id)) {
                merged += option
            }
        }

        presets.forEach { preset ->
            if (seenIds.add(preset.id)) {
                merged += preset.copy(order = merged.size)
            }
        }

        return merged
            .mapIndexed { index, option -> option.copy(order = index) }
            .sortedForDisplay()
    }

    private fun List<MomentImageStyleOption>.upgradeLegacyImagePresetPrompts(): List<MomentImageStyleOption> {
        val defaultsById = defaultMomentImageStyleOptions().associateBy { it.id }
        return map { option ->
            val default = defaultsById[option.id]
            if (option.isSystemPreset && default != null) {
                default.copy(order = option.order, createdAt = option.createdAt)
            } else {
                option
            }
        }
    }

    private companion object {
        const val KEY_STYLES = "social_copy_style_options"
        const val KEY_IMAGE_STYLES = "moment_image_style_options"
        const val KEY_DELETED_STYLES = "social_copy_deleted_style_options"
        const val KEY_DELETED_IMAGE_STYLES = "moment_deleted_image_style_options"

        val LEGACY_IMAGE_STYLE_PROMPTS = mapOf(
            "hand_drawn" to "保留原图主体和真实质感，添加轻量手绘线条、箭头、圈选和简短中文标注，标注内容要贴合画面，不遮挡主体，整体像生活博主的随手注释图，适合朋友圈发布。",
            "fresh_diary" to "将原图处理成清爽生活日记风分享图，保留主体，增加柔和留白、轻量日期感文字、细线分隔和低饱和装饰，版式干净自然，适合朋友圈记录日常。",
            "cute_sticker" to "保留原图内容，添加少量可爱贴纸、小表情、星星或便签元素，颜色明快但克制，不遮挡关键画面，整体轻松可爱，适合朋友圈分享。",
            "highlight_focus" to "保留原图真实性，通过轻微景深、柔和光影、局部高亮和简洁文字强调画面重点，让主体更醒目，整体高级但不过度修图，适合朋友圈分享。",
            "minimal_card" to "基于原图生成简约朋友圈分享卡，保留照片主体，加入干净边距、简洁中文标题区和轻量排版装饰，画面有呼吸感，文字不超过一行短句，整体现代、自然、可发布。"
        )
    }
}
