package com.socialfirstaidkit.ui.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import java.io.File
import java.util.concurrent.TimeUnit

private val DigitalAvatarCardBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF2F5FF),
        Color(0xFFF3FFF8)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSettingsClick: () -> Unit = {},
    onExpressionCenterClick: () -> Unit = {},
    onMainScreenStateChange: (Boolean) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showUserGuide by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showUserAgreement by remember { mutableStateOf(false) }
    var showContactUs by remember { mutableStateOf(false) }

    LaunchedEffect(showUserGuide, showPrivacyPolicy, showUserAgreement, showContactUs) {
        onMainScreenStateChange(!showUserGuide && !showPrivacyPolicy && !showUserAgreement && !showContactUs)
    }

    DisposableEffect(Unit) {
        onDispose { onMainScreenStateChange(true) }
    }

    BackHandler(
        enabled = showUserGuide || showPrivacyPolicy || showUserAgreement || showContactUs
    ) {
        when {
            showUserGuide -> showUserGuide = false
            showPrivacyPolicy -> showPrivacyPolicy = false
            showUserAgreement -> showUserAgreement = false
            showContactUs -> showContactUs = false
        }
    }

    // 图片选择器 - 用于更换头像
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.updateAvatar(it)
        }
    }

    when {
        showUserGuide -> {
            UserGuideScreen(
                onBackClick = { showUserGuide = false }
            )
        }
        showPrivacyPolicy -> {
            PrivacyPolicyScreen(
                onBackClick = { showPrivacyPolicy = false }
            )
        }
        showUserAgreement -> {
            UserAgreementScreen(
                onBackClick = { showUserAgreement = false }
            )
        }
        else -> {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "个人中心",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = SocialAidModernColors.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.refreshStats() },
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = SocialAidModernColors.Purple
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = SocialAidModernColors.Ink
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = SocialAidModernColors.Ink
                        )
                    }
                }
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0.dp)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
            ) {
                // 个人信息卡片
                ProfileHeaderCard(
                    profile = uiState.userProfile,
                    onNicknameClick = { viewModel.showEditNicknameDialog() },
                    onGenderClick = { viewModel.showGenderSelector() },
                    onAgeClick = { viewModel.showAgeSelector() },
                    viewModel = viewModel
                )


                AiPreferencesCard(
                    expressionProfile = uiState.expressionProfile,
                    defaultIdentity = uiState.aiDefaultIdentity,
                    defaultTone = uiState.aiDefaultTone,
                    onEdit = onExpressionCenterClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 使用统计
                StatsCard(stats = uiState.appStats)

                Spacer(modifier = Modifier.height(16.dp))

                // 关于
                AboutCard(
                    version = uiState.appVersion,
                    onUserGuideClick = { showUserGuide = true },
                    onPrivacyPolicyClick = { showPrivacyPolicy = true },
                    onUserAgreementClick = { showUserAgreement = true },
                    onContactUsClick = { showContactUs = true }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 编辑昵称对话框
        if (uiState.showEditNicknameDialog) {
            EditNicknameDialog(
                currentNickname = uiState.userProfile?.nickname ?: "",
                onDismiss = { viewModel.hideEditNicknameDialog() },
                onConfirm = { viewModel.updateNickname(it) }
            )
        }

        // 性别选择对话框
        if (uiState.showGenderSelector) {
            GenderSelectorDialog(
                currentGender = uiState.userProfile?.gender ?: "unknown",
                onDismiss = { viewModel.hideGenderSelector() },
                onSelect = { viewModel.updateGender(it) }
            )
        }

        // 年龄选择对话框
        if (uiState.showAgeSelector) {
            AgeSelectorDialog(
                currentAge = uiState.userProfile?.age ?: 0,
                onDismiss = { viewModel.hideAgeSelector() },
                onSelect = { viewModel.updateAge(it) }
            )
        }

        // 头像放大预览对话框
        if (uiState.showAvatarPreviewDialog) {
            AvatarPreviewDialog(
                avatarUri = uiState.userProfile?.avatarUri,
                onDismiss = { viewModel.hideAvatarPreviewDialog() },
                onChangeAvatar = { imagePickerLauncher.launch("image/*") }
            )
        }

        if (showContactUs) {
            ContactUsDialog(onDismiss = { showContactUs = false })
        }
    }
}
}

@Composable
private fun ProfileHeaderCard(
    profile: com.socialfirstaidkit.core.data.local.entity.UserProfileEntity?,
    onNicknameClick: () -> Unit,
    onGenderClick: () -> Unit,
    onAgeClick: () -> Unit,
    viewModel: ProfileViewModel
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = SocialAidModernColors.Purple.copy(alpha = 0.18f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：昵称、性别、年龄
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 昵称
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onNicknameClick() }
                ) {
                    Text(
                        text = if (profile?.nickname.isNullOrBlank()) "点击设置昵称" else profile!!.nickname,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (profile?.nickname.isNullOrBlank())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            SocialAidModernColors.Ink
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 性别和年龄
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 性别
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable { onGenderClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val genderIcon = when (profile?.gender) {
                            "male" -> "♂"
                            "female" -> "♀"
                            else -> "?"
                        }
                        val genderText = when (profile?.gender) {
                            "male" -> "男"
                            "female" -> "女"
                            else -> "保密"
                        }
                        Text(
                            text = "$genderIcon $genderText",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // 年龄
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable { onAgeClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val ageText = if (profile?.age == 0) "年龄" else "${profile?.age}岁"
                        Text(
                            text = "🎂 $ageText",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 右侧：头像
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 真实头像 - 点击放大预览
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { viewModel.showAvatarPreviewDialog() },
                    contentAlignment = Alignment.Center
                ) {
                    if (profile?.avatarUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(profile.avatarUri))
                                .crossfade(true)
                                .build(),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "头像",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                }
            }
        }
    }
}

@Composable
private fun DigitalAvatarCard(
    comicAvatarUrl: String?,
    isGenerating: Boolean,
    onPreview: () -> Unit,
    onGenerate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.7f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DigitalAvatarCardBrush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.82f))
                        .clickable(enabled = comicAvatarUrl != null, onClick = onPreview),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isGenerating -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        comicAvatarUrl != null -> AsyncImage(
                            model = comicAvatarUrl,
                            contentDescription = "数字形象",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        else -> Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "数字形象",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SocialAidModernColors.Ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (comicAvatarUrl == null) {
                            "创建专属形象，后续配图场景可更自然地使用你的风格。"
                        } else {
                            "已创建专属数字形象，可查看或重新生成。"
                        },
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = SocialAidModernColors.Muted
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (comicAvatarUrl != null) {
                        OutlinedButton(
                            onClick = onPreview,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("查看", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onGenerate,
                        enabled = !isGenerating,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (comicAvatarUrl == null) "创建" else "重做", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiPreferencesCard(
    expressionProfile: com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionProfile? = null,
    defaultIdentity: String,
    defaultTone: String,
    onEdit: () -> Unit
) {
    val statusText = when {
        expressionProfile == null -> "未建立"
        expressionProfile.enabled && expressionProfile.questionnaireCompleted -> "已启用"
        expressionProfile.questionnaireCompleted -> "已暂停"
        else -> "未建立"
    }
    val summaryTags = expressionProfile?.summaryTags().orEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            SocialAidModernColors.Purple.copy(alpha = 0.14f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("个人表达档案", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        statusText,
                        fontSize = 12.sp,
                        color = when (statusText) {
                            "已启用" -> MaterialTheme.colorScheme.primary
                            "已暂停" -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (summaryTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    summaryTags.take(4).forEach { tag ->
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
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                PreferenceSummaryRow("常用身份", defaultIdentity)
                PreferenceSummaryRow("默认语气", defaultTone)
            }
        }
    }
}

@Composable
private fun PreferenceSummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = SocialAidModernColors.Ink,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatsCard(stats: com.socialfirstaidkit.core.data.local.entity.AppStatsEntity?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 使用统计",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = "💬",
                    value = stats?.totalMessagesAnalyzed ?: 0,
                    label = "消息助手"
                )
                StatItem(
                    icon = "✍️",
                    value = stats?.totalCopiesGenerated ?: 0,
                    label = "朋友圈创作"
                )
                StatItem(
                    icon = "📱",
                    value = stats?.appOpenCount ?: 0,
                    label = "打开次数"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 使用天数
            val daysSinceFirstUse = stats?.firstOpenDate?.let {
                val diff = System.currentTimeMillis() - it
                TimeUnit.MILLISECONDS.toDays(diff).toInt() + 1
            } ?: 1

            Text(
                text = "🎉 已陪伴你 $daysSinceFirstUse 天",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun StatItem(icon: String, value: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutCard(
    version: String,
    onUserGuideClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onUserAgreementClick: () -> Unit,
    onContactUsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "关于",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 版本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("版本号")
                Text(
                    text = version,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // 使用指南
            Text(
                text = "使用指南",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUserGuideClick() }
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            // 隐私政策
            Text(
                text = "隐私政策",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPrivacyPolicyClick() }
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            // 用户协议
            Text(
                text = "用户协议",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUserAgreementClick() }
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            // 联系我们
            Text(
                text = "联系我们",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onContactUsClick() }
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ContactUsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("联系我们") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "如果你在使用社交急救包时遇到问题，或希望反馈识别效果、模型接入、悬浮球体验、隐私与协议相关问题，可以通过邮箱联系开发者。",
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
                Text(
                    text = "邮箱：zhnxnlng@gmail.com",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "反馈时建议附上：手机型号、系统版本、聊天软件名称、问题截图或复现步骤。请不要发送无关隐私内容。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}



@Composable
private fun EditNicknameDialog(
    currentNickname: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nickname by remember { mutableStateOf(currentNickname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改昵称") },
        text = {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nickname) },
                enabled = nickname.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 头像放大预览对话框
 */
@Composable
private fun AvatarPreviewDialog(
    avatarUri: String?,
    onDismiss: () -> Unit,
    onChangeAvatar: () -> Unit
) {
    val hasAvatar = avatarUri != null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (hasAvatar) "头像预览" else "设置头像",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 放大的头像
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasAvatar) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(avatarUri!!))
                                .crossfade(true)
                                .build(),
                            contentDescription = "头像预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "默认头像",
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }

                    Button(onClick = {
                        onDismiss()
                        onChangeAvatar()
                    }) {
                        Text(if (hasAvatar) "更换头像" else "设置头像")
                    }
                }
            }
        }
    }
}

/**
 * 数字形象生成方式选择对话框
 */
@Composable
private fun ComicAvatarTypeSelectorDialog(
    onDismiss: () -> Unit,
    onSelectPureAnime: () -> Unit,
    onSelectPrototype: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择生成方式") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 完全二次元选项
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPureAnime() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "完全二次元",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "AI自由设计，与你形象无关",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 基于原型选项
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPrototype() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "基于人物原型",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "上传照片生成数字形象",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 完全二次元配置对话框
 */
@Composable
private fun PureAnimeConfigDialog(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
    isGenerating: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = { Text("完全二次元形象") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "AI将根据你的描述生成完全二次元的数字形象",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 提示词输入框
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    label = { Text("描述你想要的形象") },
                    placeholder = { Text("例如：蓝发少女，紫色眼睛，可爱风格...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isGenerating
                )

                if (isGenerating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "正在生成中...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGenerate,
                enabled = !isGenerating && prompt.isNotBlank()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("生成")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isGenerating
            ) {
                Text("取消")
            }
        }
    )
}

/**
 * 基于原型配置对话框
 */
@Composable
private fun PrototypeConfigDialog(
    imageUri: String?,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onImageSelect: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
    isGenerating: Boolean,
    isDetectingFace: Boolean,
    faceError: String?
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImageSelect(it) }
    }

    AlertDialog(
        onDismissRequest = { if (!isGenerating && !isDetectingFace) onDismiss() },
        title = { Text("基于人物原型") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "上传一张照片，AI将根据照片中的人物生成数字形象",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 图片上传区域
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clickable(enabled = !isGenerating && !isDetectingFace) {
                            imagePicker.launch("image/*")
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (imageUri != null)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isDetectingFace -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("正在检测人脸...")
                                }
                            }
                            imageUri != null -> {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(File(imageUri))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "选中的图片",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("点击上传照片")
                                }
                            }
                        }
                    }
                }

                // 人脸检测错误提示
                if (faceError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = faceError,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // 提示词输入框
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    label = { Text("生成要求（可选）") },
                    placeholder = { Text("描述你想要的数字形象风格...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isGenerating && !isDetectingFace && imageUri != null
                )

                if (isGenerating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "正在生成中...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGenerate,
                enabled = !isGenerating && !isDetectingFace && imageUri != null
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("生成")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isGenerating && !isDetectingFace
            ) {
                Text("取消")
            }
        }
    )
}

/**
 * 数字形象对话框
 */
@Composable
private fun ComicAvatarDialog(
    comicAvatarUrl: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的专属数字形象") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (errorMessage != null) {
                    // 显示错误信息
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 优化错误提示
                    val friendlyMessage = when {
                        errorMessage.contains("Rate limit", ignoreCase = true) ||
                        errorMessage.contains("code=1003", ignoreCase = true) ->
                            "生成太频繁了，请稍等片刻再试~"
                        errorMessage.contains("timeout", ignoreCase = true) ->
                            "网络超时，请检查网络后重试"
                        errorMessage.contains("Network error", ignoreCase = true) ->
                            "网络连接失败，请检查网络设置"
                        else -> errorMessage
                    }

                    Text(
                        text = friendlyMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )

                    // 如果是限流错误，显示额外提示
                    if (errorMessage.contains("Rate limit", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "（API调用频率限制，建议等待30秒后再试）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (comicAvatarUrl != null) {
                    // 显示数字形象
                    AsyncImage(
                        model = comicAvatarUrl,
                        contentDescription = "数字形象",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "你的专属数字形象",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "可作为个人中心里的专属数字形象展示",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            if (errorMessage != null) {
                TextButton(onClick = onRegenerate) {
                    Text("重试")
                }
            } else {
                TextButton(onClick = onRegenerate) {
                    Text("重新生成")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun GenderSelectorDialog(
    currentGender: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        "male" to "♂ 男",
        "female" to "♀ 女",
        "unknown" to "? 保密"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择性别") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentGender == value,
                            onClick = { onSelect(value) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AgeSelectorDialog(
    currentAge: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var selectedAge by remember { mutableIntStateOf(if (currentAge == 0) 25 else currentAge) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年龄") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${selectedAge}岁",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = selectedAge.toFloat(),
                    onValueChange = { selectedAge = it.toInt() },
                    valueRange = 1f..100f,
                    steps = 99,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selectedAge) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
