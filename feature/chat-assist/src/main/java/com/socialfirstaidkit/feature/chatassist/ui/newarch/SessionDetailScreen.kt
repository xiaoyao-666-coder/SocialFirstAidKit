package com.socialfirstaidkit.feature.chatassist.ui.newarch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.socialfirstaidkit.core.data.local.entity.ChatMessageEntity
import com.socialfirstaidkit.core.ui.components.AiThinkingIndicator
import com.socialfirstaidkit.core.ui.theme.GlassCard
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import com.socialfirstaidkit.feature.chatassist.model.CustomMessageAssistOption
import com.socialfirstaidkit.feature.chatassist.model.DEFAULT_RELATION_ID
import com.socialfirstaidkit.feature.chatassist.model.DEFAULT_STYLE_ID
import com.socialfirstaidkit.feature.chatassist.ui.ConversationAnalysisCard
import com.socialfirstaidkit.feature.chatassist.ui.SuggestionsResultCard
import com.socialfirstaidkit.feature.chatassist.viewmodel.MessageAssistUiState
import com.socialfirstaidkit.feature.chatassist.ui.newarch.VoiceInputState
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.items
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onStartRealtimeAssist: (String?, String) -> Unit = { _, _ -> },
    viewModel: NewChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val currentSession by viewModel.currentSession.collectAsState()
    val currentContact by viewModel.currentContact.collectAsState()
    val messages by viewModel.messagesForSelectedSession.collectAsState()

    val relationOptions by viewModel.customRelationOptions.collectAsState()
    val styleOptions by viewModel.customStyleOptions.collectAsState()

    // 路由参数加载
    LaunchedEffect(sessionId) {
        viewModel.selectSession(sessionId)
    }

    var inputText by remember { mutableStateOf("") }
    var senderType by remember { mutableStateOf("USER") } // USER / CONTACT
    var additionalNotes by remember { mutableStateOf("") }
    var showSuggestionsSheet by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val voiceInputState by viewModel.voiceInputState.collectAsState()
    val isListening = voiceInputState is VoiceInputState.Listening

    // 对话框与BottomSheet状态
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPrefsSheet by remember { mutableStateOf(false) }

    // 消息多选与转发状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedMessageIds = remember { mutableStateListOf<String>() }
    var actionMenuMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var insertingAfterMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var deleteConfirmMessages by remember { mutableStateOf<List<ChatMessageEntity>>(emptyList()) }
    var forwardMessages by remember { mutableStateOf<List<ChatMessageEntity>>(emptyList()) }
    var showForwardModeSheet by remember { mutableStateOf(false) }
    var showForwardTargetSheet by remember { mutableStateOf(false) }
    var forwardAsMerged by remember { mutableStateOf(false) }
    var showMultiSelectAiReplyDialog by remember { mutableStateOf(false) }

    val allSessionsForForward by viewModel.allSessionsForForward.collectAsState()
    LaunchedEffect(showForwardTargetSheet) {
        if (showForwardTargetSheet) {
            viewModel.loadAllSessionsForForward()
        }
    }
    
    // 搜索状态
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val trimmedSearchQuery = searchQuery.trim()
    val visibleMessages = remember(messages, trimmedSearchQuery, isSearchMode) {
        if (!isSearchMode || trimmedSearchQuery.isBlank()) {
            messages
        } else {
            messages.filter { it.matchesChatSearch(trimmedSearchQuery) }
        }
    }

    LaunchedEffect(isSearchMode, trimmedSearchQuery, visibleMessages.size) {
        if (isSearchMode && trimmedSearchQuery.isNotBlank() && visibleMessages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // 短语音识别结果写入输入框
    fun appendRecognizedText(text: String) {
        val recognizedText = text.trim()
        if (recognizedText.isBlank()) return
        inputText = if (inputText.isBlank()) {
            recognizedText
        } else {
            "${inputText.trimEnd()} $recognizedText"
        }
    }

    LaunchedEffect(voiceInputState) {
        when (val state = voiceInputState) {
            is VoiceInputState.Recognized -> {
                appendRecognizedText(state.text)
                viewModel.clearVoiceInputState()
            }
            is VoiceInputState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.clearVoiceInputState()
            }
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopVoiceInput()
        }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput()
        } else {
            Toast.makeText(context, "需要麦克风权限才能语音输入，如果已被永久禁用，请前往系统设置开启", Toast.LENGTH_LONG).show()
        }
    }

    // 新的重命名话题输入
    var newTopicName by remember { mutableStateOf("") }
    LaunchedEffect(showRenameDialog) {
        if (showRenameDialog) {
            newTopicName = currentSession?.topicName.orEmpty()
        }
    }

    // 图片选取用于 OCR 导入
    val ocrImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "正在分析截图并同步对话，请稍后...", Toast.LENGTH_SHORT).show()
            viewModel.importScreenshot(
                imageUri = uri,
                onSuccess = {
                    Toast.makeText(context, "截图对话同步成功！", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    Toast.makeText(context, "导入失败: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // 专属设置缓存
    var selectedRelId by remember { mutableStateOf<String?>(null) }
    var selectedStyId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showPrefsSheet) {
        if (showPrefsSheet) {
            selectedRelId = currentSession?.preferredRelationId
            selectedStyId = currentSession?.preferredStyleId
        }
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = { Text("已选择 ${selectedMessageIds.size} 条消息", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { isMultiSelectMode = false; selectedMessageIds.clear() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = currentSession?.topicName ?: "会话详情",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val contactName = currentContact?.displayName ?: "聊天好友"
                                Text(
                                    text = "联系人: $contactName",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { onStartRealtimeAssist(currentContact?.id, sessionId) }
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "\u542f\u52a8\u5fae\u4fe1\u5b9e\u65f6\u8f85\u52a9",
                                    tint = SocialAidModernColors.Purple
                                )
                            }
                            IconButton(onClick = { isSearchMode = !isSearchMode; if (!isSearchMode) searchQuery = "" }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索聊天记录", tint = if (isSearchMode) SocialAidModernColors.Purple else LocalContentColor.current)
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("专属身份/风格") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showPrefsSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("截图 OCR 导入") },
                                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        ocrImageLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("重命名话题") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("清空记录") },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showClearConfirm = true
                                    }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("删除会话", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    )
                    if (isSearchMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("搜索聊天消息...", fontSize = 14.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { isSearchMode = false; searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "??")
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            if (trimmedSearchQuery.isNotBlank()) {
                                Text(
                                    text = "找到 ${visibleMessages.size} 条相关消息",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isMultiSelectMode) {
                MultiSelectBottomBar(
                    selectedCount = selectedMessageIds.size,
                    onAiReply = {
                        showMultiSelectAiReplyDialog = true
                    },
                    onForward = {
                        val selectedList = messages.filter { selectedMessageIds.contains(it.messageId) }
                        if (selectedList.isNotEmpty()) {
                            forwardMessages = selectedList
                            showForwardModeSheet = true
                        }
                    },
                    onDelete = {
                        deleteConfirmMessages = messages.filter { selectedMessageIds.contains(it.messageId) }
                    },
                    onCancel = {
                        isMultiSelectMode = false
                        selectedMessageIds.clear()
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                ) {
                    // 第一行：补充说明与生成回复
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = additionalNotes,
                            onValueChange = { additionalNotes = it },
                            placeholder = { Text("补充说明（如：对方语气、当前语境...）", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            textStyle = TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                viewModel.generateContextualReply(additionalNotes)
                                showSuggestionsSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SocialAidModernColors.Purple),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("生成回复", fontSize = 12.sp)
                        }
                    }

                    // 第二行：发送方切换、输入框（带麦克风）、发送按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 发送方切换按钮
                        Button(
                            onClick = {
                                senderType = if (senderType == "USER") "CONTACT" else "USER"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (senderType == "USER") Color(0xFFE8F5E9) else Color(0xFFEEEEEE)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = if (senderType == "USER") "我" else "对方",
                                color = if (senderType == "USER") Color(0xFF2E7D32) else Color(0xFF333333),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp),
                            shape = RoundedCornerShape(24.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (isListening) {
                                            viewModel.stopVoiceInput()
                                        } else if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            viewModel.startVoiceInput()
                                        } else {
                                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = if (isListening) "停止语音输入" else "语音输入",
                                        tint = if (isListening) MaterialTheme.colorScheme.error else Color.Gray
                                    )
                                }
                            },
                            placeholder = { Text("输入消息...") },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SocialAidModernColors.Purple,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val sName = if (senderType == "USER") "我" else currentContact?.displayName ?: "对方"
                                    viewModel.sendMessage(
                                        content = inputText.trim(),
                                        senderType = senderType,
                                        senderName = sName
                                    )
                                    inputText = ""
                                    // 滚动到底部
                                    scope.launch {
                                        if (visibleMessages.isNotEmpty()) {
                                            listState.animateScrollToItem(visibleMessages.size - 1)
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SocialAidModernColors.Purple),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("发送", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击右上角使用截图导入消息\n或点击下方对话框手动输入消息",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    )
                }
            } else if (isSearchMode && trimmedSearchQuery.isNotBlank() && visibleMessages.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "没有找到相关消息",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = visibleMessages,
                        key = { _, msg -> msg.messageId }
                    ) { _, message ->
                        val isSelected = selectedMessageIds.contains(message.messageId)
                        val isActionMenuVisible = actionMenuMessage?.messageId == message.messageId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent)
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            AnimatedVisibility(
                                visible = isActionMenuVisible && !isMultiSelectMode,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                            ) {
                                MessageActionMenu(
                                    isUserMessage = message.senderType == "USER",
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                        actionMenuMessage = null
                                    },
                                    onForward = {
                                        forwardMessages = listOf(message)
                                        forwardAsMerged = false
                                        showForwardTargetSheet = true
                                        actionMenuMessage = null
                                    },
                                    onInsert = {
                                        insertingAfterMessage = message
                                        actionMenuMessage = null
                                    },
                                    onEdit = {
                                        editingMessage = message
                                        actionMenuMessage = null
                                    },
                                    onDelete = {
                                        deleteConfirmMessages = listOf(message)
                                        actionMenuMessage = null
                                    },
                                    onMultiSelect = {
                                        isMultiSelectMode = true
                                        selectedMessageIds.clear()
                                        selectedMessageIds.add(message.messageId)
                                        actionMenuMessage = null
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                if (isSelected) selectedMessageIds.remove(message.messageId) else selectedMessageIds.add(message.messageId)
                                                if (selectedMessageIds.isEmpty()) isMultiSelectMode = false
                                            } else if (isActionMenuVisible) {
                                                actionMenuMessage = null
                                            }
                                        },
                                        onLongClick = {
                                            if (isMultiSelectMode) {
                                                if (isSelected) selectedMessageIds.remove(message.messageId) else selectedMessageIds.add(message.messageId)
                                                if (selectedMessageIds.isEmpty()) isMultiSelectMode = false
                                            } else {
                                                actionMenuMessage = message
                                            }
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedVisibility(
                                    visible = isMultiSelectMode,
                                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .size(22.dp)
                                            .border(
                                                width = 1.5.dp,
                                                color = if (isSelected) Color(0xFF07C160) else Color.Gray.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .background(
                                                color = if (isSelected) Color(0xFF07C160) else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    MessageBubbleRow(
                                        message = message,
                                        searchQuery = searchQuery
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. 重命名话题对话框
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名话题/会话") },
            text = {
                OutlinedTextField(
                    value = newTopicName,
                    onValueChange = { newTopicName = it },
                    label = { Text("话题名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTopicName.isNotBlank()) {
                            viewModel.updateSessionTopic(newTopicName.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 2. 清空确认对话框
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空消息记录") },
            text = { Text("确定要清空该话题下的所有聊天记录吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearSessionMessages()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 3. 删除会话确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除该话题") },
            text = { Text("确定要永久删除该话题会话及其包含的所有历史消息吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(sessionId)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 3.4 多选 AI 回复微调输入对话框
    if (showMultiSelectAiReplyDialog) {
        var multiSelectNotes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMultiSelectAiReplyDialog = false },
            title = { Text("AI 上下文回复建议", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "已选择 ${selectedMessageIds.size} 条消息作为 AI 回复的上下文背景。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = multiSelectNotes,
                        onValueChange = { multiSelectNotes = it },
                        label = { Text("补充说明（如：对方语气、回复偏好...）") },
                        placeholder = { Text("（可选）输入意图微调说明...") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.generateReplyFromSelectedMessages(
                            messageIds = selectedMessageIds.toList(),
                            additionalNotes = multiSelectNotes.trim()
                        )
                        showSuggestionsSheet = true
                        showMultiSelectAiReplyDialog = false
                        isMultiSelectMode = false
                        selectedMessageIds.clear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SocialAidModernColors.Purple)
                ) {
                    Text("生成建议")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiSelectAiReplyDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 3.5 AI 回复建议 BottomSheet
    if (showSuggestionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSuggestionsSheet = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 40.dp)
                ) {
                    when (uiState) {
                        is MessageAssistUiState.Generating -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AiThinkingIndicator()
                            }
                        }
                        is MessageAssistUiState.ConversationAnalyzed -> {
                            Column {
                                ConversationAnalysisCard(
                                    analysis = (uiState as MessageAssistUiState.ConversationAnalyzed).analysis,
                                    onAnalysisChange = { viewModel.updateContextAnalysis(it) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AiThinkingIndicator()
                                }
                            }
                        }
                        is MessageAssistUiState.SuggestionsReady -> {
                            val readyState = uiState as MessageAssistUiState.SuggestionsReady
                            Column {
                                readyState.contextAnalysis?.let { analysis ->
                                    ConversationAnalysisCard(
                                        analysis = analysis,
                                        onAnalysisChange = { viewModel.updateContextAnalysis(it) }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                SuggestionsResultCard(
                                    suggestions = readyState.suggestions,
                                    replyStyle = readyState.replyStyle,
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(it))
                                        viewModel.recordCopiedSuggestion(it)
                                    },
                                    onRegenerate = {
                                        viewModel.regenerateLastContextualReply(additionalNotes)
                                    }
                                )
                            }
                        }
                        is MessageAssistUiState.Error -> {
                            Text(
                                text = (uiState as MessageAssistUiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("无可用生成内容，请在下方点击生成")
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. 专属身份/风格配置 Sheet
    if (showPrefsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPrefsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "当前会话专属设定",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SocialAidModernColors.Purple
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "此设定仅在当前话题下生效，将覆盖联系人的全局默认设置",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 专属身份
                Text("专属角色/关系", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 全局继承项
                    val globalRelName = relationOptions.firstOrNull { it.id == currentContact?.defaultRelationId }?.name ?: "无设定"
                    PreferenceChip(
                        label = "继承默认: $globalRelName",
                        selected = selectedRelId == null,
                        onClick = { selectedRelId = null }
                    )
                    relationOptions.forEach { rel ->
                        PreferenceChip(
                            label = rel.name,
                            selected = selectedRelId == rel.id,
                            onClick = { selectedRelId = rel.id }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 专属风格
                Text("专属语气风格", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val globalStyName = styleOptions.firstOrNull { it.id == currentContact?.defaultStyleId }?.name ?: "无设定"
                    PreferenceChip(
                        label = "继承默认: $globalStyName",
                        selected = selectedStyId == null,
                        onClick = { selectedStyId = null }
                    )
                    styleOptions.forEach { sty ->
                        PreferenceChip(
                            label = sty.name,
                            selected = selectedStyId == sty.id,
                            onClick = { selectedStyId = sty.id }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        viewModel.updateSessionPreferences(selectedRelId, selectedStyId)
                        showPrefsSheet = false
                        Toast.makeText(context, "专属设定已更新！", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SocialAidModernColors.Purple)
                ) {
                    Text("保存设定")
                }
            }
        }
    }

    // 6. 编辑消息对话框
    editingMessage?.let { message ->
        var content by remember(message.messageId) { mutableStateOf(message.content) }
        var tempSenderType by remember(message.messageId) { mutableStateOf(message.senderType) }
        val contactName = currentContact?.displayName ?: "对方"

        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("编辑消息", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("发送者", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isUserSelected = tempSenderType == "USER"
                            Button(
                                onClick = { tempSenderType = "USER" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isUserSelected) Color(0xFFE8F5E9) else Color(0xFFF5F5F5),
                                    contentColor = if (isUserSelected) Color(0xFF2E7D32) else Color(0xFF666666)
                                ),
                                border = if (isUserSelected) BorderStroke(1.dp, Color(0xFF2E7D32)) else BorderStroke(1.dp, Color.Transparent),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("我 (USER)", fontSize = 13.sp, fontWeight = if (isUserSelected) FontWeight.Bold else FontWeight.Normal)
                            }

                            val isContactSelected = tempSenderType == "CONTACT"
                            Button(
                                onClick = { tempSenderType = "CONTACT" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isContactSelected) Color(0xFFE3F2FD) else Color(0xFFF5F5F5),
                                    contentColor = if (isContactSelected) Color(0xFF1565C0) else Color(0xFF666666)
                                ),
                                border = if (isContactSelected) BorderStroke(1.dp, Color(0xFF1565C0)) else BorderStroke(1.dp, Color.Transparent),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(contactName, fontSize = 13.sp, fontWeight = if (isContactSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("消息内容", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sName = if (tempSenderType == "USER") "我" else contactName
                        viewModel.updateMessage(message.messageId, content.trim(), tempSenderType, sName)
                        editingMessage = null
                    },
                    enabled = content.isNotBlank()
                ) {
                    Text("保存", color = SocialAidModernColors.Purple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }

    // 6.5 在当前消息下方插入新消息
    insertingAfterMessage?.let { message ->
        var content by remember(message.messageId) { mutableStateOf("") }
        var tempSenderType by remember(message.messageId) { mutableStateOf(message.senderType) }
        val contactName = currentContact?.displayName ?: "对方"

        AlertDialog(
            onDismissRequest = { insertingAfterMessage = null },
            title = { Text("插入新消息", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("发送者", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isUserSelected = tempSenderType == "USER"
                            Button(
                                onClick = { tempSenderType = "USER" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isUserSelected) Color(0xFFE8F5E9) else Color(0xFFF5F5F5),
                                    contentColor = if (isUserSelected) Color(0xFF2E7D32) else Color(0xFF666666)
                                ),
                                border = if (isUserSelected) BorderStroke(1.dp, Color(0xFF2E7D32)) else BorderStroke(1.dp, Color.Transparent),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("我 (USER)", fontSize = 13.sp, fontWeight = if (isUserSelected) FontWeight.Bold else FontWeight.Normal)
                            }

                            val isContactSelected = tempSenderType == "CONTACT"
                            Button(
                                onClick = { tempSenderType = "CONTACT" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isContactSelected) Color(0xFFE3F2FD) else Color(0xFFF5F5F5),
                                    contentColor = if (isContactSelected) Color(0xFF1565C0) else Color(0xFF666666)
                                ),
                                border = if (isContactSelected) BorderStroke(1.dp, Color(0xFF1565C0)) else BorderStroke(1.dp, Color.Transparent),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(contactName, fontSize = 13.sp, fontWeight = if (isContactSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("消息内容", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sName = if (tempSenderType == "USER") "我" else contactName
                        viewModel.insertMessageAfter(message.messageId, content.trim(), tempSenderType, sName)
                        insertingAfterMessage = null
                    },
                    enabled = content.isNotBlank()
                ) {
                    Text("插入", color = SocialAidModernColors.Purple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { insertingAfterMessage = null }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }

    // 7. 删除多选消息确认
    if (deleteConfirmMessages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteConfirmMessages = emptyList() },
            title = { Text("删除消息") },
            text = { Text("确定要删除选中的 ${deleteConfirmMessages.size} 条消息吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessages(deleteConfirmMessages.map { it.messageId })
                        deleteConfirmMessages = emptyList()
                        selectedMessageIds.clear()
                        isMultiSelectMode = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmMessages = emptyList() }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }

    // 8. 转发方式 Sheet
    if (showForwardModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showForwardModeSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            forwardAsMerged = false
                            showForwardModeSheet = false
                            showForwardTargetSheet = true
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Forward, contentDescription = null, tint = SocialAidModernColors.Purple)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("逐条转发", fontWeight = FontWeight.SemiBold)
                        Text("每条消息单独发送到目标会话", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            forwardAsMerged = true
                            showForwardModeSheet = false
                            showForwardTargetSheet = true
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = SocialAidModernColors.Purple)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("合并转发", fontWeight = FontWeight.SemiBold)
                        Text("整理为一条聊天记录消息发送", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // 9. 选择转发目标 Sheet
    if (showForwardTargetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showForwardTargetSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = if (forwardAsMerged) "合并转发给" else "逐条转发给",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
                Text(
                    text = "已选择 ${forwardMessages.size} 条消息",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                val availableSessions = allSessionsForForward.filter { it.sessionId != sessionId }
                if (availableSessions.isEmpty()) {
                    Text(
                        text = "暂无其他会话可转发",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(availableSessions, key = { it.sessionId }) { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.forwardMessagesToSession(session.sessionId, forwardMessages, forwardAsMerged)
                                        Toast.makeText(context, "已转发", Toast.LENGTH_SHORT).show()
                                        forwardMessages = emptyList()
                                        showForwardTargetSheet = false
                                        selectedMessageIds.clear()
                                        isMultiSelectMode = false
                                    }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SocialAidModernColors.Purple.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val initials = if (session.contactName.length > 2) session.contactName.takeLast(2) else session.contactName
                                    Text(initials, color = SocialAidModernColors.Purple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${session.contactName} (${session.topicName})", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${session.messageCount} 条消息",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSelectBottomBar(
    selectedCount: Int,
    onAiReply: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
            
            Text(
                text = "已选择 ${selectedCount} 项",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onAiReply, enabled = selectedCount > 0) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI回复",
                        tint = if (selectedCount > 0) SocialAidModernColors.Purple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = onForward, enabled = selectedCount > 0) {
                    Icon(
                        imageVector = Icons.Default.Forward,
                        contentDescription = "转发",
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionMenu(
    isUserMessage: Boolean,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onInsert: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMultiSelect: () -> Unit
) {
    val alignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentAlignment = alignment
    ) {
        Surface(
            color = Color(0xFF262A33),
            contentColor = Color.White,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MessageActionIcon(Icons.Default.ContentCopy, "复制", onCopy)
                MessageActionIcon(Icons.Default.Forward, "转发", onForward)
                MessageActionIcon(Icons.Default.Add, "插入", onInsert)
                MessageActionIcon(Icons.Default.Edit, "编辑", onEdit)
                MessageActionIcon(Icons.Default.SelectAll, "多选", onMultiSelect)
                MessageActionIcon(Icons.Default.Delete, "删除", onDelete, isDestructive = true)
            }
        }
    }
}

@Composable
private fun MessageActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isDestructive) Color(0xFFFF8A80) else Color.White
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(min = 36.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
fun PreferenceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) SocialAidModernColors.Purple.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .let {
                if (!selected) {
                    it.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                } else {
                    it
                }
            }
    ) {
        Text(
            text = label,
            color = if (selected) SocialAidModernColors.Purple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun MessageBubbleRow(
    message: ChatMessageEntity,
    searchQuery: String
) {
    val isUser = message.senderType == "USER"
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser) SocialAidModernColors.Purple else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 姓名及时间
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            Text(
                text = "${message.senderName} • $timeStr",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            
            // 气泡
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 搜索文本高亮处理
                val contentText = message.content
                if (searchQuery.isNotBlank() && contentText.contains(searchQuery, ignoreCase = true)) {
                    val annotatedString = buildAnnotatedString {
                        var startIdx = 0
                        while (startIdx < contentText.length) {
                            val matchIdx = contentText.indexOf(searchQuery, startIdx, ignoreCase = true)
                            if (matchIdx == -1) {
                                append(contentText.substring(startIdx))
                                break
                            }
                            append(contentText.substring(startIdx, matchIdx))
                            // 高亮匹配文字
                            withStyle(
                                style = SpanStyle(
                                    background = if (isUser) Color.White.copy(alpha = 0.4f) else Color.Yellow.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(contentText.substring(matchIdx, matchIdx + searchQuery.length))
                            }
                            startIdx = matchIdx + searchQuery.length
                        }
                    }
                    Text(
                        text = annotatedString,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                } else {
                    Text(
                        text = contentText,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // PENDING_SEND状态小指示器
            if (message.status == "PENDING_SEND") {
                Text(
                    text = "⏳ 预备发送(发送后自动校准)",
                    fontSize = 10.sp,
                    color = SocialAidModernColors.Purple,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun ChatMessageEntity.matchesChatSearch(query: String): Boolean {
    if (query.isBlank()) return true
    return content.contains(query, ignoreCase = true) ||
        senderName.contains(query, ignoreCase = true) ||
        senderType.contains(query, ignoreCase = true)
}
