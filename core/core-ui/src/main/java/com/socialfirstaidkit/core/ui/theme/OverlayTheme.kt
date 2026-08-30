package com.socialfirstaidkit.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 悬浮窗专用颜色定义
 */
object OverlayColors {
    // 关系标签颜色
    val LeaderColor = Color(0xFF6F55E8)    // 紫色 - 领导
    val ColleagueColor = Color(0xFF4F7BEF)  // 蓝紫 - 同事
    val FriendColor = Color(0xFF43C86F)     // 绿色 - 朋友
    val FamilyColor = Color(0xFFFF9F5A)     // 暖橙 - 家人
    val DefaultColor = Color(0xFF6750D8)    // 紫色 - 默认

    // 回复卡片背景色
    val FormalCardBg = Color(0xFFF2EDFF)    // 浅紫 - 正式版
    val CasualCardBg = Color(0xFFEAF9EF)    // 浅绿 - 随意版
    val HumorousCardBg = Color(0xFFFFF4EA)  // 浅橙 - 幽默版

    // 通用
    val OverlayBackground = Color(0xF2FFFFFF)
    val TextPrimary = Color(0xFF191428)
    val TextSecondary = Color(0xFF6E6685)
    val LoadingIndicator = Color(0xFF6750D8)
}

/**
 * 根据关系类型获取颜色
 */
fun getRelationColor(relation: String): Color {
    return when (relation.lowercase()) {
        "leader", "领导" -> OverlayColors.LeaderColor
        "colleague", "同事" -> OverlayColors.ColleagueColor
        "friend", "朋友" -> OverlayColors.FriendColor
        "family", "家人" -> OverlayColors.FamilyColor
        else -> OverlayColors.DefaultColor
    }
}
