package com.socialfirstaidkit.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.socialfirstaidkit.core.data.personalexpression.LearningThreshold
import com.socialfirstaidkit.core.ui.theme.GlassCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpressionCenterScreen(
    onBackClick: () -> Unit = {},
    onStartQuestionnaire: () -> Unit = {},
    onViewProfile: () -> Unit = {},
    onViewUpdateProposal: () -> Unit = {},
    onViewVersionHistory: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile = uiState.expressionProfile
    val pendingProposal = uiState.expressionPendingProposal
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.expressionActionMessage) {
        val message = uiState.expressionActionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearExpressionActionMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人表达档案中心", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // 状态卡片
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("表达档案状态", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // 主开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("用于消息回复", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                if (profile?.enabled == true) "已启用，回复将参考表达档案"
                                else "关闭中，回复不使用表达档案",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile?.enabled == true,
                            onCheckedChange = {
                                if (profile == null || !profile.questionnaireCompleted) {
                                    onStartQuestionnaire()
                                } else {
                                    viewModel.toggleExpressionEnabled()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 自动学习开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("根据复制过的回复优化", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                if (profile?.autoLearningEnabled == true) "已开启，复制回复会记录为表达样本"
                                else "已关闭",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile?.autoLearningEnabled == true,
                            onCheckedChange = { viewModel.setAutoLearningEnabled(it) },
                            enabled = profile?.enabled == true
                        )
                    }

                    // 学习频率
                    if (profile?.enabled == true && profile.autoLearningEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("学习频率", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "积累多少条复制样本后触发 AI 分析",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LearningThreshold.entries.forEach { threshold ->
                                val isSelected = profile.learningThreshold == threshold
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setLearningThreshold(threshold) },
                                    label = {
                                        Column {
                                            Text(threshold.label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            Text("${threshold.sampleCount}条", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile?.learningThreshold?.description ?: "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 摘要标签
                    if (profile != null && profile.questionnaireCompleted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val tags = profile.summaryTags()
                        if (tags.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                tags.take(4).forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = tag,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 更新建议卡片
            if (pendingProposal != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onViewUpdateProposal() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Update, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("有新的更新建议", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
                            Text("基于你的复制习惯，AI 生成了档案微调建议", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 核心操作区
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("操作", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    ActionItem(
                        icon = Icons.Default.Quiz,
                        title = if (profile?.questionnaireCompleted == true) "重新问卷" else "开始问卷",
                        subtitle = "通过 13 道题了解你的表达习惯",
                        onClick = onStartQuestionnaire
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ActionItem(
                        icon = Icons.Default.Visibility,
                        title = "查看完整档案",
                        subtitle = if (profile != null) profile.readableSummary() else "完成问卷后可查看",
                        onClick = onViewProfile,
                        enabled = profile?.questionnaireCompleted == true
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ActionItem(
                        icon = Icons.Default.AutoFixHigh,
                        title = "根据近期使用更新",
                        subtitle = if (pendingProposal != null) "已有待确认更新建议" else "用复制过的回复生成待确认建议",
                        onClick = {
                            viewModel.generateExpressionUpdateFromRecentUse()
                        },
                        enabled = profile?.questionnaireCompleted == true && !uiState.isGeneratingExpressionUpdate
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ActionItem(
                        icon = Icons.Default.History,
                        title = "版本记录",
                        subtitle = "查看和恢复历史档案版本",
                        onClick = onViewVersionHistory
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 删除
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("删除表达档案", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除表达档案") },
            text = { Text("确定要删除表达档案吗？此操作不可恢复，所有问卷结果、学习样本和版本记录都会被清除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpressionProfile()
                    showDeleteDialog = false
                    onBackClick()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.outline)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (enabled) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
