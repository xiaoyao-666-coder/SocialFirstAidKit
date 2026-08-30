package com.socialfirstaidkit.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.socialfirstaidkit.core.data.personalexpression.EmojiUsage
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionProfile
import com.socialfirstaidkit.core.ui.theme.GlassCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpressionResultScreen(
    onBackClick: () -> Unit = {},
    onEnableAndDone: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.expressionProfile

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("档案生成结果", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (profile == null || !profile.questionnaireCompleted) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("暂无可展示的表达档案", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("这是你的表达画像", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(profile.readableSummary(), fontSize = 14.sp, lineHeight = 21.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        profile.summaryTags().forEach { tag ->
                            AssistChip(onClick = {}, label = { Text(tag, fontSize = 12.sp) })
                        }
                    }
                }
            }

            ResultSection("场景偏好") {
                ResultLine("安慰方式", profile.sceneStyles.comfortStyle.label)
                ResultLine("拒绝方式", profile.sceneStyles.refusalStyle.label)
                ResultLine("冲突方式", profile.sceneStyles.conflictStyle.label)
                ResultLine("语气词/emoji", "${profile.languageMarkers.emojiUsage.label}，语气词 ${profile.languageMarkers.fillerWords.toOneDecimal()}/5")
            }

            ResultSection("禁用表达") {
                if (profile.avoidedExpressions.isEmpty()) {
                    Text("暂无禁用表达", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    profile.avoidedExpressions.take(8).forEach { ResultLine("避开", it) }
                }
            }

            PreviewCompare(profile)

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    viewModel.setExpressionEnabled(true)
                    onEnableAndDone()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ToggleOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("启用档案", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("继续调整")
            }
        }
    }
}

@Composable
private fun ResultSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(82.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PreviewCompare(profile: PersonalExpressionProfile) {
    ResultSection("回复预览") {
        Text("普通回复", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PreviewBubble("这件事确实挺让人不舒服的，你先别太往心里去。")
        Spacer(modifier = Modifier.height(10.dp))
        Text("参考你的表达档案", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        PreviewBubble(profile.sampleReplies["comfort"].orEmpty().ifBlank { personalizedPreview(profile) })
    }
}

@Composable
private fun PreviewBubble(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(text, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

private fun personalizedPreview(profile: PersonalExpressionProfile): String {
    val base = "听起来确实挺不容易的，先别太怪自己。"
    return when (profile.languageMarkers.emojiUsage) {
        EmojiUsage.NONE -> base
        EmojiUsage.LOW -> "$base 🙂"
        EmojiUsage.MEDIUM, EmojiUsage.HIGH -> "$base 🙂✨"
    }
}

private fun Float.toOneDecimal(): String = String.format("%.1f", this)
