package com.socialfirstaidkit.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object ChatAssist : Screen("chat_assist", "消息助手", Icons.Default.Chat)
    data object SocialCopy : Screen("social_copy", "朋友圈创作", Icons.Default.Create)
    data object Profile : Screen("profile", "个人中心", Icons.Default.Person)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
    data object ModelSettings : Screen("model_settings", "模型配置", Icons.Default.Settings)

    data object ExpressionCenter : Screen("expression_center", "表达档案中心", Icons.Default.Person)
    data object ExpressionQuestionnaireGuide : Screen("expression_questionnaire_guide", "问卷引导", Icons.Default.Person)
    data object ExpressionQuestionnaire : Screen("expression_questionnaire", "表达习惯问卷", Icons.Default.Person)
    data object ExpressionResult : Screen("expression_result", "档案生成结果", Icons.Default.Person)
    data object ExpressionEditor : Screen("expression_editor", "编辑表达档案", Icons.Default.Person)
    data object ExpressionUpdateProposal : Screen("expression_update_proposal", "更新建议", Icons.Default.Person)
    data object ExpressionVersionHistory : Screen("expression_version_history", "版本记录", Icons.Default.Person)

    data object ContactSessions : Screen("contact_sessions/{contactId}", "话题列表", Icons.Default.Chat) {
        fun createRoute(contactId: String) = "contact_sessions/$contactId"
    }

    data object ChatDetail : Screen("chat_detail/{sessionId}", "会话详情", Icons.Default.Chat) {
        fun createRoute(sessionId: String) = "chat_detail/$sessionId"
    }

    data object ChatSearch : Screen("chat_search", "搜索会话", Icons.Default.Chat)

    companion object {
        val items = listOf(ChatAssist, SocialCopy, Profile)
    }
}
