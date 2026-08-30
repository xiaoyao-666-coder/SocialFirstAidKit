package com.socialfirstaidkit.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.socialfirstaidkit.core.ui.theme.GlassCard
import com.socialfirstaidkit.core.ui.theme.HeroPanel
import com.socialfirstaidkit.core.ui.theme.SectionLabel
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import androidx.activity.compose.BackHandler
import com.socialfirstaidkit.feature.chatassist.ui.newarch.NewChatViewModel
import com.socialfirstaidkit.feature.chatassist.ui.CustomOptionsManagementScreen
import com.socialfirstaidkit.feature.socialcopy.viewmodel.SocialCopyViewModel
import com.socialfirstaidkit.feature.socialcopy.ui.SocialCopyStyleManagementScreen

private enum class SettingsSubScreen {
    MAIN,
    RELATION_MANAGEMENT,
    STYLE_MANAGEMENT,
    SOCIAL_COPY_STYLE_MANAGEMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onModelSettingsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    messageAssistViewModel: NewChatViewModel = hiltViewModel(),
    socialCopyViewModel: SocialCopyViewModel = hiltViewModel()
) {
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }

    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MAIN) {
        currentSubScreen = SettingsSubScreen.MAIN
    }

    when (currentSubScreen) {
        SettingsSubScreen.RELATION_MANAGEMENT -> {
            val customRelationOptions by messageAssistViewModel.customRelationOptions.collectAsState()
            val deletedRelationOptions by messageAssistViewModel.deletedRelationOptions.collectAsState()
            CustomOptionsManagementScreen(
                title = "管理对方身份",
                description = "这里会展示系统默认身份和你新增的身份。你可以修改、删除，并通过拖动来调整显示顺序。",
                nameLabel = "身份名称",
                descriptionLabel = "身份描述",
                namePlaceholder = "例如：客户、导师、甲方同学",
                descriptionPlaceholder = "例如：第一次合作，沟通要专业克制，同时保持友好",
                options = customRelationOptions,
                deletedOptions = deletedRelationOptions,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onAddOption = { name, description ->
                    messageAssistViewModel.addCustomRelation(name, description)
                },
                onUpdateOption = { id, name, description ->
                    messageAssistViewModel.updateCustomRelation(id, name, description)
                },
                onDeleteOption = { id ->
                    messageAssistViewModel.deleteCustomRelation(id)
                },
                onRestoreOption = { id ->
                    messageAssistViewModel.restoreDeletedRelation(id)
                },
                onPermanentDeleteOption = { id ->
                    messageAssistViewModel.permanentlyDeleteRelation(id)
                },
                onMoveOption = { fromIndex, toIndex ->
                    messageAssistViewModel.moveRelationOption(fromIndex, toIndex)
                },
                onGenerateDescription = { name ->
                    messageAssistViewModel.generateDescription(name, isRelationMode = true)
                }
            )
            return
        }
        SettingsSubScreen.STYLE_MANAGEMENT -> {
            val customStyleOptions by messageAssistViewModel.customStyleOptions.collectAsState()
            val deletedStyleOptions by messageAssistViewModel.deletedStyleOptions.collectAsState()
            CustomOptionsManagementScreen(
                title = "管理回复风格",
                description = "这里会展示系统默认风格和你新增的风格。你可以修改、删除，并通过拖动来调整显示顺序。",
                nameLabel = "风格名称",
                descriptionLabel = "风格描述",
                namePlaceholder = "例如：温柔坚定、轻松有边界",
                descriptionPlaceholder = "例如：语气自然，拒绝时委婉但明确，不拖泥带水",
                options = customStyleOptions,
                deletedOptions = deletedStyleOptions,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onAddOption = { name, description ->
                    messageAssistViewModel.addCustomStyle(name, description)
                },
                onUpdateOption = { id, name, description ->
                    messageAssistViewModel.updateCustomStyle(id, name, description)
                },
                onDeleteOption = { id ->
                    messageAssistViewModel.deleteCustomStyle(id)
                },
                onRestoreOption = { id ->
                    messageAssistViewModel.restoreDeletedStyle(id)
                },
                onPermanentDeleteOption = { id ->
                    messageAssistViewModel.permanentlyDeleteStyle(id)
                },
                onMoveOption = { fromIndex, toIndex ->
                    messageAssistViewModel.moveStyleOption(fromIndex, toIndex)
                },
                onGenerateDescription = { name ->
                    messageAssistViewModel.generateDescription(name, isRelationMode = false)
                }
            )
            return
        }
        SettingsSubScreen.SOCIAL_COPY_STYLE_MANAGEMENT -> {
            val styleOptions by socialCopyViewModel.styleOptions.collectAsState()
            val deletedStyleOptions by socialCopyViewModel.deletedStyleOptions.collectAsState()
            SocialCopyStyleManagementScreen(
                styleOptions = styleOptions,
                deletedStyleOptions = deletedStyleOptions,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onAddStyle = { name, description -> socialCopyViewModel.addCustomStyle(name, description) },
                onUpdateStyle = { id, name, description -> socialCopyViewModel.updateCustomStyle(id, name, description) },
                onDeleteStyle = { id -> socialCopyViewModel.deleteCustomStyle(id) },
                onRestoreStyle = { id -> socialCopyViewModel.restoreDeletedStyle(id) },
                onPermanentDeleteStyle = { id -> socialCopyViewModel.permanentlyDeleteStyle(id) },
                onMoveStyle = { fromIndex, toIndex -> socialCopyViewModel.moveStyleOption(fromIndex, toIndex) }
            )
            return
        }
        else -> {
            // Proceed normally
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearAllDataDialog by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            HeroPanel(
                title = "权限与数据设置",
                subtitle = "管理系统权限与本地数据，让社交辅助更稳定也更可控。",
                icon = Icons.Default.SettingsSuggest,
                statusText = "安全配置",
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 大模型配置
            SectionHeader("大模型与 API 配置")
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = onModelSettingsClick,
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SocialAidModernColors.Glass),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("大模型接入与功能分配", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("配置各功能的模型路由与自定义 API 接入", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "进入配置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 个性化与自定义
            SectionHeader("个性化与自定义")

            PersonalExpressionAccessCard(
                masterEnabled = uiState.personalExpressionMasterEnabled,
                messageAssistEnabled = uiState.personalExpressionMessageAssistEnabled,
                socialCopyEnabled = uiState.personalExpressionSocialCopyEnabled,
                onMasterChange = viewModel::setPersonalExpressionMasterEnabled,
                onMessageAssistChange = viewModel::setPersonalExpressionMessageAssistEnabled,
                onSocialCopyChange = viewModel::setPersonalExpressionSocialCopyEnabled
            )

            // 对方身份
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = { currentSubScreen = SettingsSubScreen.RELATION_MANAGEMENT },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SocialAidModernColors.Glass),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("管理对方身份", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("自定义聊天对象角色，让回复更贴合对方身份", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "进入配置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 回复风格
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = { currentSubScreen = SettingsSubScreen.STYLE_MANAGEMENT },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SocialAidModernColors.Glass),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("管理回复风格", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("自定义聊天语气和态度，塑造专属沟通表达", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "进入配置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 朋友圈创作风格
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = { currentSubScreen = SettingsSubScreen.SOCIAL_COPY_STYLE_MANAGEMENT },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SocialAidModernColors.Glass),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("朋友圈创作风格", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("自定义朋友圈文案的基调，生成更具个性的动态", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "进入配置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 权限管理
            SectionHeader("权限管理")

            PermissionItem(
                title = "无障碍服务",
                description = "读取聊天窗口完整上下文（可选增强）",
                isGranted = uiState.accessibilityPermissionGranted,
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )


            // 隐私
            SectionHeader("隐私与安全")

            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("数据安全说明", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 消息内容仅用于识别聊天和生成建议\n• 配置、历史和缓存主要保存在设备本地\n• API Key 等敏感配置加密存储\n• 调用 AI 时仅向你选择的服务商发送当前功能所需的最小必要内容",
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showClearCacheDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("清除缓存（当前 ${uiState.cacheSize}）")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showClearAllDataDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("清除所有本地数据")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清除缓存") },
            text = {
                Text("确定要清除缓存吗？这将删除临时文件和缓存内容，但会保留头像和用户资料。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        showClearCacheDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearAllDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDataDialog = false },
            title = { Text("清除所有本地数据") },
            text = {
                Text("此操作将清除本地消息缓存等数据，且无法撤销。确定继续吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        Toast.makeText(context, "本地数据已清除", Toast.LENGTH_SHORT).show()
                        showClearAllDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDataDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    SectionLabel(
        title = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PersonalExpressionAccessCard(
    masterEnabled: Boolean,
    messageAssistEnabled: Boolean,
    socialCopyEnabled: Boolean,
    onMasterChange: (Boolean) -> Unit,
    onMessageAssistChange: (Boolean) -> Unit,
    onSocialCopyChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SocialAidModernColors.Glass),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSwitchRow(
                title = "个人表达档案接入",
                description = "总开关，关闭后所有生成场景都不会参考个人表达档案",
                checked = masterEnabled,
                enabled = true,
                onCheckedChange = onMasterChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            SettingsSwitchRow(
                title = "消息助手",
                description = "生成聊天回复时参考你的语气、长度和避雷表达",
                checked = messageAssistEnabled,
                enabled = masterEnabled,
                onCheckedChange = onMessageAssistChange
            )
            SettingsSwitchRow(
                title = "朋友圈创作",
                description = "生成朋友圈文案时参考你的日常表达习惯",
                checked = socialCopyEnabled,
                enabled = masterEnabled,
                onCheckedChange = onSocialCopyChange
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                description,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SocialAidModernColors.Glass),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已授权",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "去授权",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

