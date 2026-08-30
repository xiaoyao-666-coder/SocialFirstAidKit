package com.socialfirstaidkit.feature.chatassist.ui.newarch

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.socialfirstaidkit.core.ui.theme.GlassCard
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import com.socialfirstaidkit.feature.chatassist.model.CustomMessageAssistOption
import com.socialfirstaidkit.feature.chatassist.model.DEFAULT_RELATION_ID
import com.socialfirstaidkit.feature.chatassist.model.DEFAULT_STYLE_ID
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSessionsScreen(
    contactId: String,
    onNavigateBack: () -> Unit,
    onSessionClick: (String) -> Unit,
    onStartRealtimeAssist: (String) -> Unit = {},
    viewModel: NewChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val contact by viewModel.currentContact.collectAsState()
    val sessions by viewModel.sessionsForSelectedContact.collectAsState()

    val relationOptions by viewModel.customRelationOptions.collectAsState()
    val styleOptions by viewModel.customStyleOptions.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showAddTopicDialog by remember { mutableStateOf(false) }
    var showEditContactDialog by remember { mutableStateOf(false) }
    var showRelationPicker by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var topicNameInput by remember { mutableStateOf("") }
    var contactNameInput by remember { mutableStateOf("") }

    LaunchedEffect(contactId) {
        viewModel.selectContact(contactId)
    }

    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val currentContact = contact
        if (uri != null && currentContact != null) {
            viewModel.updateContactProfile(currentContact.displayName, uri)
            Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.displayName ?: "联系人会话", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("修改个人信息") },
                            onClick = {
                                showMenu = false
                                contactNameInput = contact?.displayName ?: ""
                                showEditContactDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("设置默认关系") },
                            onClick = {
                                showMenu = false
                                showRelationPicker = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("设置默认风格") },
                            onClick = {
                                showMenu = false
                                showStylePicker = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除联系人", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTopicDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建话题")
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 联系人头部卡片
            contact?.let { c ->
                ContactCardHeader(
                    name = c.displayName,
                    avatarPath = c.avatarUri,
                    relation = relationOptions.firstOrNull { it.id == (c.defaultRelationId ?: DEFAULT_RELATION_ID) }?.name ?: "默认",
                    style = styleOptions.firstOrNull { it.id == (c.defaultStyleId ?: DEFAULT_STYLE_ID) }?.name ?: "默认",
                    onAvatarClick = { avatarLauncher.launch("image/*") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onStartRealtimeAssist(c.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SocialAidModernColors.Purple)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "启动该联系人的实时辅助",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "会话话题列表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SocialAidModernColors.Ink
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无会话话题，点击右下角按钮新建", color = SocialAidModernColors.Muted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(sessions, key = { it.session.sessionId }) { item ->
                        SessionItemRow(
                            topic = item.session.topicName,
                            messageCount = item.messageCount,
                            lastMessage = item.lastMessageText,
                            onClick = { onSessionClick(item.session.sessionId) }
                        )
                    }
                }
            }
        }
    }

    // 弹窗与 PickerSheets
    if (showAddTopicDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddTopicDialog = false
                topicNameInput = ""
            },
            title = { Text("新建会话话题") },
            text = {
                OutlinedTextField(
                    value = topicNameInput,
                    onValueChange = { topicNameInput = it },
                    label = { Text("话题名称") },
                    placeholder = { Text("例如：工作对接、日常闲聊") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (topicNameInput.isNotBlank()) {
                            viewModel.createSessionTopic(topicNameInput.trim())
                            showAddTopicDialog = false
                            topicNameInput = ""
                        }
                    },
                    enabled = topicNameInput.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddTopicDialog = false
                    topicNameInput = ""
                }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditContactDialog) {
        AlertDialog(
            onDismissRequest = { showEditContactDialog = false },
            title = { Text("修改信息") },
            text = {
                OutlinedTextField(
                    value = contactNameInput,
                    onValueChange = { contactNameInput = it },
                    label = { Text("联系人姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (contactNameInput.isNotBlank()) {
                            viewModel.updateContactProfile(contactNameInput.trim(), null)
                            showEditContactDialog = false
                        }
                    },
                    enabled = contactNameInput.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditContactDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRelationPicker && contact != null) {
        OptionPickerSheet(
            title = "选择全局默认关系身份",
            options = relationOptions,
            selectedId = contact?.defaultRelationId ?: DEFAULT_RELATION_ID,
            onSelect = { rid ->
                viewModel.updateContactPreferences(rid, contact?.defaultStyleId ?: DEFAULT_STYLE_ID)
                showRelationPicker = false
            },
            onDismiss = { showRelationPicker = false }
        )
    }

    if (showStylePicker && contact != null) {
        OptionPickerSheet(
            title = "选择全局默认回复风格",
            options = styleOptions,
            selectedId = contact?.defaultStyleId ?: DEFAULT_STYLE_ID,
            onSelect = { sid ->
                viewModel.updateContactPreferences(contact?.defaultRelationId ?: DEFAULT_RELATION_ID, sid)
                showStylePicker = false
            },
            onDismiss = { showStylePicker = false }
        )
    }

    if (showDeleteConfirm && contact != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除联系人") },
            text = { Text("确定要删除联系人 ${contact?.displayName} 吗？这将清除其下的所有话题会话和聊天记录且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        contact?.let { viewModel.deleteContact(it) }
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ContactCardHeader(
    name: String,
    avatarPath: String?,
    relation: String,
    style: String,
    onAvatarClick: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SocialAidModernColors.MintSoft)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarPath.isNullOrBlank()) {
                    AsyncImage(
                        model = File(avatarPath),
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = name.takeLast(2).ifBlank { "TA" },
                        color = SocialAidModernColors.PurpleDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SocialAidModernColors.Ink)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("关系: $relation", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SocialAidModernColors.PurpleSoft.copy(alpha = 0.4f))
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("风格: $style", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SocialAidModernColors.MintSoft.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionItemRow(
    topic: String,
    messageCount: Int,
    lastMessage: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SocialAidModernColors.PurpleSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = SocialAidModernColors.Purple, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(topic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = SocialAidModernColors.Ink)
                    Text("$messageCount 条消息", color = SocialAidModernColors.Muted, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(lastMessage, color = SocialAidModernColors.Muted, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPickerSheet(
    title: String,
    options: List<CustomMessageAssistOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SocialAidModernColors.GlassStrong
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = title,
                color = SocialAidModernColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(options, key = { it.id }) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.id) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.name, color = SocialAidModernColors.Ink, fontSize = 16.sp)
                            if (option.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = option.description,
                                    color = SocialAidModernColors.Muted,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        if (option.id == selectedId) {
                            Icon(Icons.Default.Check, contentDescription = "已选择", tint = SocialAidModernColors.Mint)
                        }
                    }
                }
            }
        }
    }
}
