package com.socialfirstaidkit.feature.chatassist.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssistantDirection
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.socialfirstaidkit.core.data.model.OcrResult
import com.socialfirstaidkit.core.llm.model.ChatMessage
import com.socialfirstaidkit.core.llm.model.ChatSession
import com.socialfirstaidkit.core.llm.model.ReplySuggestions
import com.socialfirstaidkit.core.llm.model.SenderType
import com.socialfirstaidkit.core.llm.api.ReplyStyle
import com.socialfirstaidkit.core.ui.components.AiThinkingIndicator
import com.socialfirstaidkit.core.ui.theme.GlassCard
import com.socialfirstaidkit.core.ui.theme.HeroPanel
import com.socialfirstaidkit.core.ui.theme.IconBubble
import com.socialfirstaidkit.core.ui.theme.SectionLabel
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import com.socialfirstaidkit.feature.chatassist.model.CustomMessageAssistOption
import com.socialfirstaidkit.feature.chatassist.model.SingleReplyHistory
import com.socialfirstaidkit.feature.chatassist.model.sortedForDisplay
import com.socialfirstaidkit.feature.chatassist.viewmodel.ConversationContextAnalysis
import com.socialfirstaidkit.feature.chatassist.viewmodel.MessageAssistUiState
import com.socialfirstaidkit.feature.chatassist.viewmodel.ReplyMode
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionProfile
import com.socialfirstaidkit.service.accessibility.ChatAccessibilityService
import com.socialfirstaidkit.service.accessibility.RealtimeOverlayPrefs
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

import com.socialfirstaidkit.feature.chatassist.ui.newarch.ContactListScreen
import com.socialfirstaidkit.feature.chatassist.ui.newarch.NewChatViewModel

private enum class CustomOptionsMode {
    RELATION,
    STYLE
}

private enum class ContextualModePage {
    SESSION_LIST,
    DETAIL
}

private enum class SingleReplyHistoryPage {
    MAIN,
    HISTORY_LIST,
    HISTORY_DETAIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageAssistScreen(
    viewModel: NewChatViewModel = hiltViewModel(),
    onMainScreenStateChange: (Boolean) -> Unit = {},
    onExpressionCenterClick: () -> Unit = {},
    onNavigateToChatDetail: (String) -> Unit = {},
    onNavigateToChatSearch: () -> Unit = {},
    onNavigateToContactSessions: (String) -> Unit = {},
    onStartRealtimeAssist: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val ocrHistory by viewModel.ocrHistory.collectAsState()
    val singleReplyHistory by viewModel.singleReplyHistory.collectAsState()
    val singleReplyVersions by viewModel.singleReplyVersions.collectAsState()
    val singleReplyVersionIndex by viewModel.singleReplyVersionIndex.collectAsState()
    val replyMode by viewModel.replyMode.collectAsState()
    val selectedRelationId by viewModel.selectedRelationId.collectAsState()
    val customRelationOptions by viewModel.customRelationOptions.collectAsState()
    val deletedRelationOptions by viewModel.deletedRelationOptions.collectAsState()
    val selectedStyleId by viewModel.selectedStyleId.collectAsState()
    val customStyleOptions by viewModel.customStyleOptions.collectAsState()
    val deletedStyleOptions by viewModel.deletedStyleOptions.collectAsState()
    val additionalNotes by viewModel.additionalNotes.collectAsState()
    val singleTextMessage by viewModel.singleTextMessage.collectAsState()
    val hasGeneratedReply by viewModel.hasGeneratedReply.collectAsState()
    val expressionProfile by viewModel.expressionProfile.collectAsState()

    var showAddMessageDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var showOcrHistoryDialog by remember { mutableStateOf(false) }
    var showFloatingWindowSettings by remember { mutableStateOf(false) }
    var singleReplyHistoryPage by remember { mutableStateOf(SingleReplyHistoryPage.MAIN) }
    var selectedSingleReplyHistory by remember { mutableStateOf<SingleReplyHistory?>(null) }
    var customizationMode by remember { mutableStateOf<CustomOptionsMode?>(null) }
    var contextualModePage by remember { mutableStateOf(ContextualModePage.SESSION_LIST) }

    LaunchedEffect(customizationMode, singleReplyHistoryPage, contextualModePage, replyMode) {
        val isMainScreen = customizationMode == null &&
                singleReplyHistoryPage == SingleReplyHistoryPage.MAIN &&
                (replyMode == ReplyMode.SINGLE_TEXT || contextualModePage == ContextualModePage.SESSION_LIST)
        onMainScreenStateChange(isMainScreen)
    }

    DisposableEffect(Unit) {
        onDispose { onMainScreenStateChange(true) }
    }

    BackHandler(
        enabled = showAddMessageDialog ||
                showOcrHistoryDialog ||
                customizationMode != null ||
                singleReplyHistoryPage != SingleReplyHistoryPage.MAIN
    ) {
        when {
            showAddMessageDialog -> showAddMessageDialog = false
            showOcrHistoryDialog -> showOcrHistoryDialog = false
            customizationMode != null -> customizationMode = null
            singleReplyHistoryPage == SingleReplyHistoryPage.HISTORY_DETAIL -> {
                singleReplyHistoryPage = SingleReplyHistoryPage.HISTORY_LIST
            }
            singleReplyHistoryPage == SingleReplyHistoryPage.HISTORY_LIST -> {
                singleReplyHistoryPage = SingleReplyHistoryPage.MAIN
            }
        }
    }

    if (customizationMode != null) {
        val isRelationMode = customizationMode == CustomOptionsMode.RELATION
        CustomOptionsManagementScreen(
            title = if (isRelationMode) "管理对方身份" else "管理回复风格",
            description = if (isRelationMode) {
                "这里会展示系统默认身份和你新增的身份。你可以修改、删除，并通过拖动来调整显示顺序。"
            } else {
                "这里会展示系统默认风格和你新增的风格。你可以修改、删除，并通过拖动来调整显示顺序。"
            },
            nameLabel = if (isRelationMode) "身份名称" else "风格名称",
            descriptionLabel = if (isRelationMode) "身份描述" else "风格描述",
            namePlaceholder = if (isRelationMode) "例如：客户、导师、甲方同学" else "例如：温柔坚定、轻松有边界",
            descriptionPlaceholder = if (isRelationMode) {
                "例如：第一次合作，沟通要专业克制，同时保持友好"
            } else {
                "例如：语气自然，拒绝时委婉但明确，不拖泥带水"
            },
            options = if (isRelationMode) customRelationOptions else customStyleOptions,
            deletedOptions = if (isRelationMode) deletedRelationOptions else deletedStyleOptions,
            onBack = { customizationMode = null },
            onAddOption = { name, description ->
                if (isRelationMode) {
                    viewModel.addCustomRelation(name, description)
                } else {
                    viewModel.addCustomStyle(name, description)
                }
            },
            onUpdateOption = { id, name, description ->
                if (isRelationMode) {
                    viewModel.updateCustomRelation(id, name, description)
                } else {
                    viewModel.updateCustomStyle(id, name, description)
                }
            },
            onDeleteOption = { id ->
                if (isRelationMode) {
                    viewModel.deleteCustomRelation(id)
                } else {
                    viewModel.deleteCustomStyle(id)
                }
            },
            onRestoreOption = { id ->
                if (isRelationMode) {
                    viewModel.restoreDeletedRelation(id)
                } else {
                    viewModel.restoreDeletedStyle(id)
                }
            },
            onPermanentDeleteOption = { id ->
                if (isRelationMode) {
                    viewModel.permanentlyDeleteRelation(id)
                } else {
                    viewModel.permanentlyDeleteStyle(id)
                }
            },
            onMoveOption = { fromIndex, toIndex ->
                if (isRelationMode) {
                    viewModel.moveRelationOption(fromIndex, toIndex)
                } else {
                    viewModel.moveStyleOption(fromIndex, toIndex)
                }
            },
            onGenerateDescription = { name ->
                viewModel.generateDescription(name, isRelationMode)
            }
        )
        return
    }

    when {
        singleReplyHistoryPage == SingleReplyHistoryPage.HISTORY_LIST -> {
            SingleReplyHistoryListScreen(
                histories = singleReplyHistory,
                onBack = { singleReplyHistoryPage = SingleReplyHistoryPage.MAIN },
                onSelect = {
                    selectedSingleReplyHistory = it
                    singleReplyHistoryPage = SingleReplyHistoryPage.HISTORY_DETAIL
                },
                onClearAll = { viewModel.clearSingleReplyHistory() },
                onDeleteHistories = { list -> viewModel.deleteSingleReplyHistories(list) }
            )
        }
        singleReplyHistoryPage == SingleReplyHistoryPage.HISTORY_DETAIL -> {
            SingleReplyHistoryDetailScreen(
                history = selectedSingleReplyHistory,
                onBack = { singleReplyHistoryPage = SingleReplyHistoryPage.HISTORY_LIST },
                onDelete = { viewModel.deleteSingleReplyHistory(it) },
                onDeleted = {
                    selectedSingleReplyHistory = null
                    singleReplyHistoryPage = SingleReplyHistoryPage.HISTORY_LIST
                }
            )
        }
        else -> {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0.dp)
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    var isBatchEditMode by remember { mutableStateOf(false) }
                    var showCreateConversationDialog by remember { mutableStateOf(false) }
                    val showReplyModeSwitcher = true
                    if (showReplyModeSwitcher) {
                        MessageAssistTopBar(
                            currentMode = replyMode,
                            onModeChange = { viewModel.setReplyMode(it) },
                            onSearchClick = onNavigateToChatSearch,
                            onAddChatClick = { showAddContactDialog = true },
                            onBatchEditClick = { isBatchEditMode = !isBatchEditMode },
                            onHistoryClick = { singleReplyHistoryPage = SingleReplyHistoryPage.HISTORY_LIST }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (replyMode) {
                            ReplyMode.SINGLE_TEXT -> {
                                SingleTextModeContent(
                                    uiState = uiState,
                                    selectedRelationId = selectedRelationId,
                                    customRelationOptions = customRelationOptions,
                                    selectedStyleId = selectedStyleId,
                                    customStyleOptions = customStyleOptions,
                                    additionalNotes = additionalNotes,
                                    singleTextMessage = singleTextMessage,
                                    hasGeneratedReply = hasGeneratedReply,
                                    singleReplyHistory = singleReplyHistory,
                                    singleReplyVersions = singleReplyVersions,
                                    singleReplyVersionIndex = singleReplyVersionIndex,
                                    expressionProfile = expressionProfile,
                                    onExpressionCenterClick = onExpressionCenterClick,
                                    onRelationChange = { viewModel.setRelation(it) },
                                    onStyleChange = { viewModel.setReplyStyle(it) },
                                    onAdditionalNotesChange = { viewModel.setAdditionalNotes(it) },
                                    onSingleTextMessageChange = { viewModel.setSingleTextMessage(it) },
                                    onClearTextInputs = { viewModel.clearAllTextInputs() },
                                    onShowRelationCustomization = { customizationMode = CustomOptionsMode.RELATION },
                                    onShowStyleCustomization = { customizationMode = CustomOptionsMode.STYLE },
                                    onGenerateReply = { message ->
                                        viewModel.generateReply(message)
                                    },
                                    onCancelGeneration = { viewModel.cancelGeneration() },
                                    onReset = { viewModel.reset() },
                                    onResetGeneratedFlag = { viewModel.resetGeneratedFlag() },
                                    onPreviousVersion = { viewModel.showPreviousSingleReplyVersion() },
                                    onNextVersion = { viewModel.showNextSingleReplyVersion() },
                                    onCopySuggestion = { viewModel.recordCopiedSuggestion(it) },
                                    onContextAnalysisChange = { viewModel.updateContextAnalysis(it) }
                                )
                            }
                            ReplyMode.CONTEXTUAL -> {
                                ContactListScreen(
                                    onNavigateBack = { },
                                    onContactClick = { contactId ->
                                        viewModel.selectContact(contactId)
                                        onNavigateToContactSessions(contactId)
                                    },
                                    onStartRealtimeAssist = null,
                                    showTopBar = false,
                                    batchEditEnabled = isBatchEditMode,
                                    onBatchEditFinished = { isBatchEditMode = false },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFloatingWindowSettings) {
        RealtimeOverlaySettingsSheet(
            onDismiss = { showFloatingWindowSettings = false }
        )
    }

    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddContactDialog = false
                newContactName = ""
            },
            title = { Text("添加聊天联系人") },
            text = {
                OutlinedTextField(
                    value = newContactName,
                    onValueChange = { newContactName = it },
                    label = { Text("联系人昵称") },
                    placeholder = { Text("最好与对方昵称一致") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newContactName.isNotBlank()) {
                            viewModel.addContact(newContactName.trim())
                            showAddContactDialog = false
                            newContactName = ""
                        }
                    },
                    enabled = newContactName.isNotBlank()
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddContactDialog = false
                        newContactName = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EditSessionNameDialog(
    contactName: String,
    contactAvatarUri: String?,
    onConfirm: (String, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(contactName) { mutableStateOf(contactName) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedAvatarUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改聊天对象") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("聊天对象名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), selectedAvatarUri)
                    }
                },
                enabled = name.isNotBlank() && (name.trim() != contactName || selectedAvatarUri != null)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun EditSessionProfileDialog(
    contactName: String,
    contactAvatarUri: String?,
    selectedRelationId: String,
    customRelationOptions: List<CustomMessageAssistOption>,
    onCustomizeClick: () -> Unit,
    onConfirm: (String, String, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(contactName) { mutableStateOf(contactName) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var tempRelationId by remember(selectedRelationId) { mutableStateOf(selectedRelationId) }
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedAvatarUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑聊天对象") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable { avatarPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarModel: Any? = selectedAvatarUri ?: contactAvatarUri?.let { File(it) }
                    if (avatarModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = "聊天对象头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                TextButton(onClick = { avatarPicker.launch("image/*") }) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (contactAvatarUri == null && selectedAvatarUri == null) "上传头像" else "更换头像")
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("聊天对象名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))
                RelationSelector(
                    selectedRelationId = tempRelationId,
                    customOptions = customRelationOptions,
                    onRelationSelected = { tempRelationId = it },
                    onCustomizeClick = onCustomizeClick
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), tempRelationId, selectedAvatarUri)
                    }
                },
                enabled = name.isNotBlank() && (name.trim() != contactName || tempRelationId != selectedRelationId || selectedAvatarUri != null)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ContactAvatar(
    avatarUri: String?,
    contactName: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    val context = LocalContext.current
    val fallbackText = contactName
        .trim()
        .filterNot { it.isWhitespace() }
        .take(2)
        .ifBlank { "?" }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(avatarUri))
                    .crossfade(true)
                    .build(),
                contentDescription = "聊天对象头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = fallbackText,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34f).sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ScreenshotUploadPanel(
    onUploadScreenshot: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "快速上传截图",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = SocialAidModernColors.Ink
                )
                Text(
                    text = "识别后会追加到当前对话",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onUploadScreenshot) {
                Text("上传截图")
            }
        }
    }
}

@Composable
private fun ReplyModeSegmentedControl(
    currentMode: ReplyMode,
    onModeChange: (ReplyMode) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = SocialAidModernColors.Hairline.copy(alpha = 0.72f)
            ),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ReplyModeSegment(
                    text = "单条回复",
                    icon = Icons.AutoMirrored.Filled.ShortText,
                    selected = currentMode == ReplyMode.SINGLE_TEXT,
                    onClick = { onModeChange(ReplyMode.SINGLE_TEXT) },
                    modifier = Modifier.weight(1f)
                )
                ReplyModeSegment(
                    text = "对话上下文",
                    icon = Icons.Default.ChatBubble,
                    selected = currentMode == ReplyMode.CONTEXTUAL,
                    onClick = { onModeChange(ReplyMode.CONTEXTUAL) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReplyModeSegment(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    SocialAidModernColors.Purple.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) SocialAidModernColors.Purple else SocialAidModernColors.Muted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = if (selected) SocialAidModernColors.Purple else SocialAidModernColors.Ink,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * 单文本模式内容
 */
@Composable
private fun SingleTextModeContent(
    uiState: MessageAssistUiState,
    selectedRelationId: String,
    customRelationOptions: List<CustomMessageAssistOption>,
    selectedStyleId: String,
    customStyleOptions: List<CustomMessageAssistOption>,
    additionalNotes: String,
    singleTextMessage: String,
    hasGeneratedReply: Boolean,
    singleReplyHistory: List<SingleReplyHistory>,
    singleReplyVersions: List<ReplySuggestions>,
    singleReplyVersionIndex: Int,
    expressionProfile: PersonalExpressionProfile?,
    onExpressionCenterClick: () -> Unit,
    onRelationChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onAdditionalNotesChange: (String) -> Unit,
    onSingleTextMessageChange: (String) -> Unit,
    onClearTextInputs: () -> Unit,
    onShowRelationCustomization: () -> Unit,
    onShowStyleCustomization: () -> Unit,
    onGenerateReply: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onReset: () -> Unit,
    onResetGeneratedFlag: () -> Unit,
    onPreviousVersion: () -> Unit,
    onNextVersion: () -> Unit,
    onCopySuggestion: (String) -> Unit,
    onContextAnalysisChange: (ConversationContextAnalysis) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        // 关系选择
        RelationSelector(
            selectedRelationId = selectedRelationId,
            customOptions = customRelationOptions,
            onRelationSelected = onRelationChange,
            onCustomizeClick = onShowRelationCustomization
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 风格选择
        StyleSelector(
            selectedStyleId = selectedStyleId,
            customOptions = customStyleOptions,
            onStyleSelected = onStyleChange,
            onCustomizeClick = onShowStyleCustomization
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExpressionProfileStatusChip(
            profile = expressionProfile,
            onClick = onExpressionCenterClick
        )

        if (expressionProfile?.enabled == true && expressionProfile.questionnaireCompleted) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 补充说明输入框
        AdditionalNotesInput(
            notes = additionalNotes,
            onNotesChange = onAdditionalNotesChange,
            onClearTextInputs = onClearTextInputs
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 消息输入框
        SectionLabel(title = "对方消息")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = singleTextMessage,
            onValueChange = { onSingleTextMessageChange(it) },
            label = { Text("粘贴消息内容") },
            placeholder = { Text("例如：周末有空吗？帮我加个班") },
            trailingIcon = {
                IconButton(onClick = onClearTextInputs) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "清空输入内容",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(12.dp))

        val isGeneratingReply = uiState is MessageAssistUiState.Generating ||
                uiState is MessageAssistUiState.ConversationAnalyzed
        if (!hasGeneratedReply || isGeneratingReply) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onGenerateReply(singleTextMessage) },
                    modifier = Modifier.weight(1f),
                    enabled = singleTextMessage.isNotBlank() && !isGeneratingReply
                ) {
                    if (isGeneratingReply) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("生成回复建议")
                    }
                }

                if (isGeneratingReply) {
                    IconButton(
                        onClick = onCancelGeneration,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "停止生成",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 结果展示
        when (uiState) {
            is MessageAssistUiState.Generating -> {
                AiThinkingIndicator()
            }
            is MessageAssistUiState.ConversationAnalyzed -> {
                ConversationAnalysisCard(
                    analysis = uiState.analysis,
                    onAnalysisChange = onContextAnalysisChange
                )
                Spacer(modifier = Modifier.height(12.dp))
                AiThinkingIndicator()
            }
            is MessageAssistUiState.SuggestionsReady -> {
                uiState.contextAnalysis?.let { analysis ->
                    ConversationAnalysisCard(
                        analysis = analysis,
                        onAnalysisChange = onContextAnalysisChange
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                val displayedSuggestions = singleReplyVersions
                    .getOrNull(singleReplyVersionIndex)
                    ?: uiState.suggestions
                SuggestionsResultCard(
                    suggestions = displayedSuggestions,
                    replyStyle = uiState.replyStyle,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(it))
                        onCopySuggestion(it)
                    },
                    onRegenerate = {
                        onGenerateReply(singleTextMessage)
                    },
                    versionIndex = singleReplyVersionIndex,
                    versionCount = singleReplyVersions.size,
                    onPreviousVersion = onPreviousVersion,
                    onNextVersion = onNextVersion
                )
            }
            is MessageAssistUiState.Error -> {
                ErrorCard(message = uiState.message, onDismiss = onReset)
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RealtimeOverlaySettingsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "实时悬浮球设置",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = SocialAidModernColors.Ink
            )
            Spacer(modifier = Modifier.height(12.dp))
            RealtimeOverlaySetupCard()
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun RealtimeOverlaySetupCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var menuExpanded by remember { mutableStateOf(false) }
    var realtimeOverlayEnabled by remember { mutableStateOf(RealtimeOverlayPrefs.isEnabled(context)) }
    var floatingBallVisible by remember { mutableStateOf(ChatAccessibilityService.isFloatingBallVisible()) }
    var accessibilityEnabled by remember { mutableStateOf(checkChatAccessibilityPermission(context)) }

    fun refreshPermissions() {
        realtimeOverlayEnabled = RealtimeOverlayPrefs.isEnabled(context)
        floatingBallVisible = ChatAccessibilityService.isFloatingBallVisible()
        accessibilityEnabled = checkChatAccessibilityPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBubble(Icons.AutoMirrored.Filled.AssistantDirection)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "实时悬浮球助手",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = SocialAidModernColors.Ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "开启后可在聊天时弹出回复建议",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = SocialAidModernColors.Muted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    PermissionStatusChip("悬浮球", floatingBallVisible)
                    PermissionStatusChip("无障碍", accessibilityEnabled)
                }
            }

            Switch(
                checked = realtimeOverlayEnabled,
                onCheckedChange = { enabled ->
                    realtimeOverlayEnabled = enabled
                    RealtimeOverlayPrefs.setEnabled(context, enabled)
                    if (enabled && !accessibilityEnabled) {
                        menuExpanded = true
                    }
                }
            )

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "打开实时悬浮球设置",
                        tint = SocialAidModernColors.Purple
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("无障碍服务") },
                        leadingIcon = { Icon(Icons.Default.Accessibility, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun PermissionStatusChip(label: String, enabled: Boolean) {
    val backgroundColor = if (enabled) {
        Color(0xFFE9F7EF)
    } else {
        Color(0xFFFFF1E8)
    }
    val textColor = if (enabled) {
        Color(0xFF1B7A3A)
    } else {
        Color(0xFFC05621)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = backgroundColor
    ) {
        Text(
            text = "$label ${if (enabled) "已开启" else "未开启"}",
            fontSize = 11.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
private fun checkChatAccessibilityPermission(context: android.content.Context): Boolean {
    val accessibilityManager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
        as? android.view.accessibility.AccessibilityManager
        ?: return false
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    return enabledServices.any { service ->
        service.resolveInfo.serviceInfo.packageName == context.packageName &&
            service.resolveInfo.serviceInfo.name.endsWith(".ChatAccessibilityService")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleReplyHistoryListScreen(
    histories: List<SingleReplyHistory>,
    onBack: () -> Unit,
    onSelect: (SingleReplyHistory) -> Unit,
    onClearAll: () -> Unit,
    onDeleteHistories: (List<SingleReplyHistory>) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedIds = emptySet()
                    } else {
                        onBack()
                    }
                }
            ) {
                Icon(
                    imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                    contentDescription = if (isSelectionMode) "取消" else "返回"
                )
            }
            Text(
                text = if (isSelectionMode) "已选择 ${selectedIds.size} 项" else "单条回复历史",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            if (isSelectionMode) {
                if (selectedIds.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val itemsToDelete = histories.filter { selectedIds.contains(it.id) }
                            onDeleteHistories(itemsToDelete)
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                if (histories.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (histories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无历史记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(histories, key = { it.id }) { history ->
                    val isSelected = selectedIds.contains(history.id)
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) {
                                            selectedIds - history.id
                                        } else {
                                            selectedIds + history.id
                                        }
                                        if (selectedIds.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        onSelect(history)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(history.id)
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = history.sourceMessage,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dateFormat.format(Date(history.createdAt)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            if (!isSelectionMode) {
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
private fun SingleReplyHistoryDetailScreen(
    history: SingleReplyHistory?,
    onBack: () -> Unit,
    onDelete: (SingleReplyHistory) -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "记录详情",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history == null) {
            Text(
                text = "记录不存在",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = history.sourceMessage,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))

            val detailText = history.toDisplayText()
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = detailText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(detailText))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制")
                }
                OutlinedButton(
                    onClick = {
                        onDelete(history)
                        onDeleted()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun SingleReplyHistory.toDisplayText(): String = buildString {
    appendLine("原消息")
    appendLine(sourceMessage)
    if (formal.isNotBlank()) {
        appendLine()
        appendLine("礼貌正式")
        appendLine(formal)
    }
    if (casual.isNotBlank()) {
        appendLine()
        appendLine("轻松随意")
        appendLine(casual)
    }
    if (humorous.isNotBlank()) {
        appendLine()
        appendLine("幽默化解")
        appendLine(humorous)
    }
}

/**
 * 上下文记忆模式内容
 */
@Composable
private fun ContextualModeContent(
    uiState: MessageAssistUiState,
    currentSession: ChatSession?,
    sessions: List<ChatSession>,
    ocrHistory: List<com.socialfirstaidkit.feature.chatassist.model.OcrHistory>,
    selectedRelationId: String,
    customRelationOptions: List<CustomMessageAssistOption>,
    selectedStyleId: String,
    customStyleOptions: List<CustomMessageAssistOption>,
    expressionProfile: PersonalExpressionProfile?,
    onExpressionCenterClick: () -> Unit,
    additionalNotes: String,
    hasGeneratedReply: Boolean,
    onRelationChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onAdditionalNotesChange: (String) -> Unit,
    onClearTextInputs: () -> Unit,
    onShowRelationCustomization: () -> Unit,
    onShowStyleCustomization: () -> Unit,
    onSelectSession: (String) -> Unit,
    onCreateSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onProcessScreenshot: (Uri, (com.socialfirstaidkit.feature.chatassist.model.OcrHistory, OcrResult, Float) -> Unit) -> Unit,
    onAddMessage: (String, SenderType, String) -> Unit,
    onAppendMessages: (List<ChatMessage>) -> Unit,
    onUpdateMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onUpdateSessionProfile: (String, Uri?) -> Unit,
    onGenerateReply: () -> Unit,
    onCancelGeneration: () -> Unit,
    onReset: () -> Unit,
    page: ContextualModePage,
    onOpenSession: (String) -> Unit,
    onCreateSessionFromList: (String) -> Unit,
    onBackToSessions: () -> Unit,
    onDeleteCurrentSession: (String) -> Unit,
    onDeleteSessionsFromList: (List<String>) -> Unit,
    onShowAddMessageDialog: () -> Unit,
    onResetGeneratedFlag: () -> Unit,
    onShowOcrHistory: () -> Unit = {},
    onClearOcrHistory: () -> Unit = {},
    isBatchEditMode: Boolean = false,
    onToggleBatchEdit: () -> Unit = {},
    showCreateConversationDialog: Boolean = false,
    onDismissCreateConversationDialog: () -> Unit = {},
    onContextAnalysisChange: (ConversationContextAnalysis) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 重复检测弹窗状态。
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateHistory by remember { mutableStateOf<com.socialfirstaidkit.feature.chatassist.model.OcrHistory?>(null) }
    var pendingOcrResult by remember { mutableStateOf<OcrResult?>(null) }
    var duplicateSimilarity by remember { mutableStateOf(0f) }
    
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            onProcessScreenshot(it) { history, ocrResult, similarity ->
                // 检测到重复时的回调
                duplicateHistory = history
                pendingOcrResult = ocrResult
                duplicateSimilarity = similarity
                showDuplicateDialog = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (page) {
            ContextualModePage.SESSION_LIST -> {
                ConversationSessionListPage(
                    sessions = sessions,
                    onOpenSession = onOpenSession,
                    onDeleteSessions = onDeleteSessionsFromList,
                    isBatchEditMode = isBatchEditMode,
                    onToggleBatchEdit = onToggleBatchEdit
                )
            }
            ContextualModePage.DETAIL -> {
                ConversationSessionDetailPage(
                    uiState = uiState,
                    currentSession = currentSession,
                    selectedRelationId = selectedRelationId,
                    customRelationOptions = customRelationOptions,
                    selectedStyleId = selectedStyleId,
                    customStyleOptions = customStyleOptions,
                    expressionProfile = expressionProfile,
                    onExpressionCenterClick = onExpressionCenterClick,
                    additionalNotes = additionalNotes,
                    hasGeneratedReply = hasGeneratedReply,
                    onBack = onBackToSessions,
                    onRelationChange = onRelationChange,
                    onStyleChange = onStyleChange,
                    onAdditionalNotesChange = onAdditionalNotesChange,
                    onClearTextInputs = onClearTextInputs,
                    onShowRelationCustomization = onShowRelationCustomization,
                    onShowStyleCustomization = onShowStyleCustomization,
                    onUploadScreenshot = { imagePicker.launch("image/*") },
                    onAddMessage = onShowAddMessageDialog,
                    onDeleteSession = { currentSession?.let { onDeleteCurrentSession(it.sessionId) } },
                    onShowOcrHistory = onShowOcrHistory,
                    onUpdateMessage = onUpdateMessage,
                    onDeleteMessage = onDeleteMessage,
                    onUpdateSessionProfile = onUpdateSessionProfile,
                    onGenerateReply = onGenerateReply,
                    onCancelGeneration = onCancelGeneration,
                    onReset = onReset,
                    onResetGeneratedFlag = onResetGeneratedFlag,
                    onCopy = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    onContextAnalysisChange = onContextAnalysisChange
                )
            }
        }
    }

        // 重复检测弹窗。
        if (showDuplicateDialog && duplicateHistory != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingOcrResult = null
                    showDuplicateDialog = false
                },
                title = { Text("检测到相似识别") },
                text = {
                    Column {
                        Text("本次识别结果与历史记录相似度较高（${(duplicateSimilarity * 100).roundToInt()}%）")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "历史记录：${duplicateHistory!!.contactName} (${duplicateHistory!!.messageCount}条消息)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "是否将新识别的消息追加到当前会话末尾？",
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newMessages = pendingOcrResult
                                ?.messages
                                ?.map { message ->
                                    val senderType = when (message.senderType) {
                                        com.socialfirstaidkit.core.data.model.SenderType.USER -> SenderType.USER
                                        com.socialfirstaidkit.core.data.model.SenderType.CONTACT -> SenderType.CONTACT
                                        com.socialfirstaidkit.core.data.model.SenderType.UNKNOWN -> SenderType.CONTACT
                                    }
                                    ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        senderType = senderType,
                                        senderName = if (senderType == SenderType.USER) {
                                            "我"
                                        } else {
                                            currentSession?.contactName ?: duplicateHistory?.contactName ?: "对方"
                                        },
                                        content = message.content
                                    )
                                }
                                .orEmpty()
                            if (newMessages.isNotEmpty()) {
                                onAppendMessages(newMessages)
                            }
                            pendingOcrResult = null
                            showDuplicateDialog = false
                        }
                    ) {
                        Text("追加")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingOcrResult = null
                            showDuplicateDialog = false
                        }
                    ) {
                        Text("取消")
                    }
                }
            )
        }

    if (showCreateConversationDialog) {
        CreateConversationDialog(
            onCreateSession = { contactName ->
                onCreateSessionFromList(contactName)
                onDismissCreateConversationDialog()
            },
            onDismiss = onDismissCreateConversationDialog
        )
    }
}

@Composable
private fun ConversationSessionListPage(
    sessions: List<ChatSession>,
    onOpenSession: (String) -> Unit,
    onDeleteSessions: (List<String>) -> Unit,
    isBatchEditMode: Boolean = false,
    onToggleBatchEdit: () -> Unit = {}
) {
    var selectedSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val allSelected = sessions.isNotEmpty() && selectedSessionIds.size == sessions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isBatchEditMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "已选择 ${selectedSessionIds.size} 个会话",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            selectedSessionIds = if (allSelected) {
                                emptySet()
                            } else {
                                sessions.map { it.sessionId }.toSet()
                            }
                        },
                        enabled = sessions.isNotEmpty()
                    ) {
                        Text(if (allSelected) "取消全选" else "全选")
                    }
                }
                TextButton(
                    onClick = {
                        onDeleteSessions(selectedSessionIds.toList())
                        selectedSessionIds = emptySet()
                        onToggleBatchEdit()
                    },
                    enabled = selectedSessionIds.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (sessions.isEmpty()) {
            EmptyConversationListState(
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(sessions, key = { it.sessionId }) { session ->
                    val isSelected = selectedSessionIds.contains(session.sessionId)
                    ConversationSessionCard(
                        session = session,
                        selected = isSelected,
                        editMode = isBatchEditMode,
                        onCheckedChange = { checked ->
                            selectedSessionIds = if (checked) {
                                selectedSessionIds + session.sessionId
                            } else {
                                selectedSessionIds - session.sessionId
                            }
                        },
                        onClick = {
                            if (isBatchEditMode) {
                                selectedSessionIds = if (selectedSessionIds.contains(session.sessionId)) {
                                    selectedSessionIds - session.sessionId
                                } else {
                                    selectedSessionIds + session.sessionId
                                }
                            } else {
                                onOpenSession(session.sessionId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationSessionDetailPage(
    uiState: MessageAssistUiState,
    currentSession: ChatSession?,
    selectedRelationId: String,
    customRelationOptions: List<CustomMessageAssistOption>,
    selectedStyleId: String,
    customStyleOptions: List<CustomMessageAssistOption>,
    expressionProfile: PersonalExpressionProfile?,
    onExpressionCenterClick: () -> Unit,
    additionalNotes: String,
    hasGeneratedReply: Boolean,
    onBack: () -> Unit,
    onRelationChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    onAdditionalNotesChange: (String) -> Unit,
    onClearTextInputs: () -> Unit,
    onShowRelationCustomization: () -> Unit,
    onShowStyleCustomization: () -> Unit,
    onUploadScreenshot: () -> Unit,
    onAddMessage: () -> Unit,
    onDeleteSession: () -> Unit,
    onShowOcrHistory: () -> Unit,
    onUpdateMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onUpdateSessionProfile: (String, Uri?) -> Unit,
    onGenerateReply: () -> Unit,
    onCancelGeneration: () -> Unit,
    onReset: () -> Unit,
    onResetGeneratedFlag: () -> Unit,
    onCopy: (String) -> Unit,
    onContextAnalysisChange: (ConversationContextAnalysis) -> Unit
) {
    val isImageProcessing = uiState is MessageAssistUiState.ProcessingImage ||
            uiState is MessageAssistUiState.AnalyzingImage
    val imageProcessingMessage = when (uiState) {
        is MessageAssistUiState.ProcessingImage -> "正在识别截图..."
        is MessageAssistUiState.AnalyzingImage -> "正在分析对话内容..."
        else -> ""
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isImageProcessing) {
                Spacer(modifier = Modifier.height(68.dp))
            }

        ConversationDetailToolbar(
            contactName = currentSession?.contactName ?: "新对话",
            contactAvatarUri = currentSession?.contactAvatarUri,
            selectedRelationId = selectedRelationId,
            customRelationOptions = customRelationOptions,
            onRelationChange = onRelationChange,
            onShowRelationCustomization = onShowRelationCustomization,
            onBack = onBack,
            onUploadScreenshot = onUploadScreenshot,
            onAddMessage = onAddMessage,
            onUpdateSessionProfile = onUpdateSessionProfile,
            onShowOcrHistory = onShowOcrHistory,
            onDeleteSession = onDeleteSession
        )

        Spacer(modifier = Modifier.height(12.dp))

        StyleSelector(
            selectedStyleId = selectedStyleId,
            customOptions = customStyleOptions,
            onStyleSelected = onStyleChange,
            onCustomizeClick = onShowStyleCustomization
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExpressionProfileStatusChip(
            profile = expressionProfile,
            onClick = onExpressionCenterClick
        )

        if (expressionProfile?.enabled == true && expressionProfile.questionnaireCompleted) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        AdditionalNotesInput(
            notes = additionalNotes,
            onNotesChange = onAdditionalNotesChange,
            onClearTextInputs = onClearTextInputs
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (currentSession != null && currentSession.messages.isNotEmpty()) {
            EditableConversationDisplay(
                messages = currentSession.messages,
                contactName = currentSession.contactName,
                onMessageClick = { _, _ -> },
                onUpdateMessage = onUpdateMessage,
                onDeleteMessage = onDeleteMessage,
                onAddMessage = onAddMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            ScreenshotUploadPanel(onUploadScreenshot = onUploadScreenshot)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateReply,
                    modifier = Modifier.weight(1f),
                    enabled = uiState !is MessageAssistUiState.Generating
                            && uiState !is MessageAssistUiState.ConversationAnalyzed
                ) {
                    if (uiState is MessageAssistUiState.Generating || uiState is MessageAssistUiState.ConversationAnalyzed) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Psychology, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasGeneratedReply) "重新生成回复" else "基于当前对话生成回复")
                    }
                }

                if (uiState is MessageAssistUiState.Generating || uiState is MessageAssistUiState.ConversationAnalyzed) {
                    IconButton(
                        onClick = onCancelGeneration,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "停止生成",
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            EmptyConversationDetailState(
                onUploadScreenshot = onUploadScreenshot,
                onAddMessage = onAddMessage,
                modifier = Modifier.heightIn(min = 280.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            ScreenshotUploadPanel(onUploadScreenshot = onUploadScreenshot)
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (uiState) {
            is MessageAssistUiState.Generating -> {
                AiThinkingIndicator()
            }
            is MessageAssistUiState.ConversationAnalyzed -> {
                ConversationAnalysisCard(
                    analysis = uiState.analysis,
                    onAnalysisChange = onContextAnalysisChange
                )
                Spacer(modifier = Modifier.height(12.dp))
                AiThinkingIndicator()
            }
            is MessageAssistUiState.SuggestionsReady -> {
                uiState.contextAnalysis?.let { analysis ->
                    ConversationAnalysisCard(
                        analysis = analysis,
                        onAnalysisChange = onContextAnalysisChange
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                SuggestionsResultCard(
                    suggestions = uiState.suggestions,
                    replyStyle = uiState.replyStyle,
                    onCopy = onCopy,
                    onRegenerate = {
                        onResetGeneratedFlag()
                        onGenerateReply()
                    }
                )
            }
            is MessageAssistUiState.Error -> {
                ErrorCard(message = uiState.message, onDismiss = onReset)
            }
            else -> {}
        }
        }

        if (isImageProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter)
            ) {
                LoadingCard(message = imageProcessingMessage)
            }
        }
    }
}

@Composable
private fun ConversationDetailToolbar(
    contactName: String,
    contactAvatarUri: String?,
    selectedRelationId: String,
    customRelationOptions: List<CustomMessageAssistOption>,
    onRelationChange: (String) -> Unit,
    onShowRelationCustomization: () -> Unit,
    onBack: () -> Unit,
    onUploadScreenshot: () -> Unit,
    onAddMessage: () -> Unit,
    onUpdateSessionProfile: (String, Uri?) -> Unit,
    onShowOcrHistory: () -> Unit,
    onDeleteSession: () -> Unit
) {
    var showEditNameDialog by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }

            ContactAvatar(
                avatarUri = contactAvatarUri,
                contactName = contactName,
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contactName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = SocialAidModernColors.Ink
                )
                Text(
                    text = "继续编辑对话、上传后续截图并生成回复",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { showEditNameDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "修改聊天对象名称",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (showEditNameDialog && false) {
            IconButton(onClick = onUploadScreenshot) {
                Icon(Icons.Default.Image, contentDescription = "上传截图")
            }
            IconButton(onClick = onAddMessage) {
                Icon(Icons.Default.Add, contentDescription = "添加消息")
            }
            }
            if (false) {
            IconButton(onClick = onShowOcrHistory) {
                Icon(Icons.Default.History, contentDescription = "识别历史")
            }
            IconButton(onClick = onDeleteSession) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除对话",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    }

    if (showEditNameDialog) {
        EditSessionProfileDialog(
            contactName = contactName,
            contactAvatarUri = contactAvatarUri,
            selectedRelationId = selectedRelationId,
            customRelationOptions = customRelationOptions,
            onCustomizeClick = onShowRelationCustomization,
            onConfirm = { newName, newRelationId, avatarUri ->
                onUpdateSessionProfile(newName, avatarUri)
                if (newRelationId != selectedRelationId) {
                    onRelationChange(newRelationId)
                }
                showEditNameDialog = false
            },
            onDismiss = { showEditNameDialog = false }
        )
    }
}

@Composable
private fun ConversationSessionCard(
    session: ChatSession,
    editMode: Boolean = false,
    selected: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val lastMessage = session.messages.lastOrNull()
    val preview = lastMessage?.content?.take(42) ?: "还没有消息，点击进入后上传截图或手动添加"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onCheckedChange
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            ContactAvatar(
                avatarUri = session.contactAvatarUri,
                contactName = session.contactName
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.contactName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = SocialAidModernColors.Ink
                    )
                    Text(
                        text = dateFormat.format(Date(session.updatedAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = preview,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "${session.messages.size} 条消息",
                    fontSize = 12.sp,
                    color = SocialAidModernColors.PurpleDeep
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationListState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Forum,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "还没有对话记录",
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyConversationDetailState(
    onUploadScreenshot: () -> Unit,
    onAddMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(58.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "这个对话还没有消息",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onUploadScreenshot) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("上传截图")
            }
            Button(onClick = onAddMessage) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("手动添加")
            }
        }
    }
}

@Composable
private fun CreateConversationDialog(
    onCreateSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var contactName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增联系人") },
        text = {
            OutlinedTextField(
                value = contactName,
                onValueChange = { contactName = it },
                label = { Text("联系人名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (contactName.isNotBlank()) {
                        onCreateSession(contactName.trim())
                    }
                },
                enabled = contactName.isNotBlank()
            ) {
                Text("创建")
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
 * 可编辑的对话展示组件。
 * - 对方消息：左侧蓝色气泡
 * - 我的消息：右侧绿色气泡
 * - 点击可编辑
 */
@Composable
private fun EditableConversationDisplay(
    messages: List<ChatMessage>,
    contactName: String,
    onMessageClick: (String, String) -> Unit,
    onUpdateMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onAddMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingContent by remember { mutableStateOf("") }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF7F2FF)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = SocialAidModernColors.Purple.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "📌 识别结果（点击文字可修改）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 显示最近的消息
            messages.takeLast(20).forEach { message ->
                EditableMessageBubble(
                    message = message,
                    contactName = contactName,
                    isEditing = editingMessageId == message.id,
                    editingContent = editingContent,
                    onContentChange = { editingContent = it },
                    onStartEdit = {
                        editingMessageId = message.id
                        editingContent = message.content
                    },
                    onConfirmEdit = {
                        onUpdateMessage(message.id, editingContent)
                        editingMessageId = null
                    },
                    onDelete = {
                        onDeleteMessage(message.id)
                        editingMessageId = null
                    },
                    onCancelEdit = {
                        editingMessageId = null
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = onAddMessage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加消息")
            }
        }
    }

    // 编辑对话框
    if (editingMessageId != null) {
        AlertDialog(
            onDismissRequest = { editingMessageId = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editingContent,
                    onValueChange = { editingContent = it },
                    label = { Text("消息内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateMessage(editingMessageId!!, editingContent)
                        editingMessageId = null
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessageId = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 可编辑的消息气泡。
 * - 对方（CONTACT）：左侧，蓝色气泡
 * - 我（USER）：右侧，绿色气泡
 */
@Composable
private fun EditableMessageBubble(
    message: ChatMessage,
    contactName: String,
    isEditing: Boolean,
    editingContent: String,
    onContentChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onConfirmEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancelEdit: () -> Unit
) {
    val isUser = message.senderType == SenderType.USER

    // 对方消息使用蓝色，己方消息使用绿色。
    val backgroundColor = if (isUser) {
        Color(0xFFDCF8C6) // 微信绿色
    } else {
        Color(0xFFE3F2FD) // 浅蓝色
    }

    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // 发送者名称
        Text(
            text = if (isUser) "我" else contactName,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // 消息气泡
        if (isEditing) {
            // 编辑模式
            Card(
                modifier = Modifier
                    .widthIn(max = 280.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    BasicTextField(
                        value = editingContent,
                        onValueChange = onContentChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onConfirmEdit,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("确认", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = onCancelEdit,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("取消", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // 显示模式
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isUser) 12.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 12.dp
                        )
                    )
                    .clickable(onClick = onStartEdit)
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = Color.Black  // ✨ 文字颜色改为黑色
                )
            }
        }
    }
}

// ... 其余组件保持不变 (InfoCard, RelationSelector, StyleSelector, AdditionalNotesInput, SessionManagementArea, ConversationDisplay, MessageBubble, EmptySessionState, LoadingCard, SuggestionsResultCard, ReplySuggestionCardWithCopy, ErrorCard, SessionSelectionDialog, SessionListItem, AddMessageDialog)...

/**
 * 信息卡片
 */
@Composable
private fun InfoCard(title: String, description: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBubble(Icons.Default.AutoAwesome)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SocialAidModernColors.Ink)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = SocialAidModernColors.Muted
                )
            }
        }
    }
}

@Composable
private fun RelationSelector(
    selectedRelationId: String,
    customOptions: List<CustomMessageAssistOption>,
    onRelationSelected: (String) -> Unit,
    onCustomizeClick: () -> Unit
) {
    SectionHeaderWithAction(
        title = "对方身份",
        actionLabel = "自定义",
        onActionClick = onCustomizeClick
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        customOptions.sortedForDisplay().forEach { option ->
            FilterChip(
                selected = selectedRelationId == option.id,
                onClick = { onRelationSelected(option.id) },
                label = { Text(option.name, fontSize = 12.sp, maxLines = 1) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.72f),
                    labelColor = SocialAidModernColors.Muted,
                    selectedContainerColor = SocialAidModernColors.Purple,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun AdditionalNotesInput(
    notes: String,
    onNotesChange: (String) -> Unit,
    onClearTextInputs: (() -> Unit)? = null
) {
    Column {
        SectionLabel(title = "补充说明")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("添加额外要求或背景信息") },
            placeholder = { Text("例如：对方最近帮过我，我想委婉拒绝但不想伤感情；或者希望回复简短一点") },
            trailingIcon = if (onClearTextInputs != null) {
                {
                    IconButton(onClick = onClearTextInputs) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "清空输入内容",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
    }
}

@Composable
private fun StyleSelector(
    selectedStyleId: String,
    customOptions: List<CustomMessageAssistOption>,
    onStyleSelected: (String) -> Unit,
    onCustomizeClick: () -> Unit
) {
    SectionHeaderWithAction(
        title = "回复风格",
        actionLabel = "自定义",
        onActionClick = onCustomizeClick
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        customOptions.sortedForDisplay().forEach { option ->
            FilterChip(
                selected = selectedStyleId == option.id,
                onClick = { onStyleSelected(option.id) },
                label = { Text(option.name, fontSize = 11.sp, maxLines = 1) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.72f),
                    labelColor = SocialAidModernColors.Muted,
                    selectedContainerColor = SocialAidModernColors.Purple,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun SectionHeaderWithAction(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = SocialAidModernColors.Ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier.clickable(onClick = onActionClick),
            color = Color.White.copy(alpha = 0.78f),
            shape = RoundedCornerShape(50),
            border = androidx.compose.foundation.BorderStroke(1.dp, SocialAidModernColors.Hairline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = SocialAidModernColors.Purple,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = actionLabel,
                    color = SocialAidModernColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomOptionsManagementScreen(
    title: String,
    description: String,
    nameLabel: String,
    descriptionLabel: String,
    namePlaceholder: String,
    descriptionPlaceholder: String,
    options: List<CustomMessageAssistOption>,
    deletedOptions: List<CustomMessageAssistOption>,
    onBack: () -> Unit,
    onAddOption: (String, String) -> Unit,
    onUpdateOption: (String, String, String) -> Unit,
    onDeleteOption: (String) -> Unit,
    onRestoreOption: (String) -> Unit,
    onPermanentDeleteOption: (String) -> Unit,
    onMoveOption: (Int, Int) -> Unit,
    onGenerateDescription: (suspend (String) -> String)? = null
) {
    var editingOptionId by remember { mutableStateOf<String?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var descriptionInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var showOptionDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun resetForm() {
        editingOptionId = null
        nameInput = ""
        descriptionInput = ""
        isGenerating = false
        showOptionDialog = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showHistory = true }) {
                        Text("历史")
                    }
                    IconButton(
                        onClick = {
                            editingOptionId = null
                            nameInput = ""
                            descriptionInput = ""
                            showOptionDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新增选项")
                    }
                },
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
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SocialAidModernColors.Muted,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "长按左侧拖动手柄可以调整展示顺序。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SocialAidModernColors.Muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "当前选项",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SocialAidModernColors.Ink
            )
            Spacer(modifier = Modifier.height(10.dp))

            options.forEachIndexed { index, option ->
                ReorderableOptionRow(
                    option = option,
                    index = index,
                    itemCount = options.size,
                    onEdit = {
                        editingOptionId = option.id
                        nameInput = option.name
                        descriptionInput = option.description
                        showOptionDialog = true
                    },
                    onDelete = { onDeleteOption(option.id) },
                    onMove = onMoveOption
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (showOptionDialog) {
            CustomOptionEditorDialog(
                isEditing = editingOptionId != null,
                nameLabel = nameLabel,
                descriptionLabel = descriptionLabel,
                namePlaceholder = namePlaceholder,
                descriptionPlaceholder = descriptionPlaceholder,
                nameInput = nameInput,
                descriptionInput = descriptionInput,
                isGenerating = isGenerating,
                canGenerateDescription = onGenerateDescription != null,
                onNameChange = { nameInput = it },
                onDescriptionChange = { descriptionInput = it },
                onGenerateDescription = {
                    val generator = onGenerateDescription ?: return@CustomOptionEditorDialog
                    coroutineScope.launch {
                        isGenerating = true
                        try {
                            descriptionInput = generator(nameInput.trim())
                        } catch (_: Exception) {
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                onDismiss = { resetForm() },
                onConfirm = {
                    val trimmedName = nameInput.trim()
                    val trimmedDescription = descriptionInput.trim()
                    if (editingOptionId == null) {
                        onAddOption(trimmedName, trimmedDescription)
                    } else {
                        onUpdateOption(editingOptionId!!, trimmedName, trimmedDescription)
                    }
                    resetForm()
                }
            )
        }

        if (showHistory) {
            DeletedCustomOptionsDialog(
                title = "$title 历史记录",
                options = deletedOptions,
                onDismiss = { showHistory = false },
                onRestore = onRestoreOption,
                onPermanentDelete = onPermanentDeleteOption
            )
        }
    }
}

@Composable
private fun CustomOptionEditorDialog(
    isEditing: Boolean,
    nameLabel: String,
    descriptionLabel: String,
    namePlaceholder: String,
    descriptionPlaceholder: String,
    nameInput: String,
    descriptionInput: String,
    isGenerating: Boolean,
    canGenerateDescription: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onGenerateDescription: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑选项" else "新增选项") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = onNameChange,
                        label = { Text(nameLabel) },
                        placeholder = { Text(namePlaceholder) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    if (canGenerateDescription) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onGenerateDescription,
                            enabled = nameInput.isNotBlank() && !isGenerating,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isGenerating) "生成中" else "生成描述",
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = onDescriptionChange,
                    label = { Text(descriptionLabel) },
                    placeholder = { Text(descriptionPlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = nameInput.isNotBlank() && descriptionInput.isNotBlank()
            ) {
                Text(if (isEditing) "保存修改" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DeletedCustomOptionsDialog(
    title: String,
    options: List<CustomMessageAssistOption>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onPermanentDelete: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (options.isEmpty()) {
                    Text(
                        text = "暂无删除记录",
                        color = SocialAidModernColors.Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    options.forEach { option ->
                        Surface(
                            color = Color.White.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SocialAidModernColors.Hairline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(option.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        option.description,
                                        color = SocialAidModernColors.Muted,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 18.sp
                                    )
                                }
                                IconButton(onClick = { onRestore(option.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "恢复")
                                }
                                IconButton(onClick = { onPermanentDelete(option.id) }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "彻底删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ReorderableOptionRow(
    option: CustomMessageAssistOption,
    index: Int,
    itemCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    val currentIndex by rememberUpdatedState(index)
    val currentItemCount by rememberUpdatedState(itemCount)

    fun finishDrag() {
        val stepHeight = itemHeightPx.takeIf { it > 0f } ?: 88f
        val offsetSteps = (dragOffsetY / stepHeight).roundToInt()
        val targetIndex = (currentIndex + offsetSteps).coerceIn(0, (currentItemCount - 1).coerceAtLeast(0))
        if (targetIndex != currentIndex) {
            onMove(currentIndex, targetIndex)
        }
        dragOffsetY = 0f
    }

    GlassCard(
        modifier = Modifier
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .onGloballyPositioned { itemHeightPx = it.size.height.toFloat() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "拖动排序",
                tint = SocialAidModernColors.Muted,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .pointerInput(option.id) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { finishDrag() },
                            onDragCancel = { dragOffsetY = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                        }
                    }
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SocialAidModernColors.Ink
                    )
                    if (option.isSystemPreset) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SocialAidModernColors.Hairline)
                        ) {
                            Text(
                                text = "系统默认",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = SocialAidModernColors.Muted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SocialAidModernColors.Muted,
                    lineHeight = 18.sp
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 会话管理区域
 */
@Composable
private fun SessionManagementArea(
    currentSession: ChatSession?,
    onSelectSession: () -> Unit,
    onUploadScreenshot: () -> Unit,
    onDeleteSession: () -> Unit,
    onShowOcrHistory: () -> Unit = {},
    onClearOcrHistory: () -> Unit = {}
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentSession?.let { "👤 ${it.contactName}" } ?: "未选择会话",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SocialAidModernColors.Ink
                )

                Row {
                    // 创建或选择会话。
                    IconButton(
                        onClick = onSelectSession,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "创建或选择会话",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 上传聊天截图。
                    IconButton(onClick = onUploadScreenshot) {
                        Icon(Icons.Default.Image, contentDescription = "上传截图")
                    }

                    // 打开历史记录。
                    IconButton(onClick = onShowOcrHistory) {
                        Icon(Icons.Default.History, contentDescription = "识别历史")
                    }

                    if (currentSession != null) {
                        IconButton(onClick = onDeleteSession) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除会话",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 对话展示
 */
@Composable
private fun ConversationDisplay(
    messages: List<ChatMessage>,
    contactName: String,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            messages.takeLast(15).forEach { message ->
                MessageBubble(
                    message = message,
                    contactName = contactName
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 消息气泡
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    contactName: String
) {
    val isUser = message.senderType == SenderType.USER
    val backgroundColor = if (isUser) {
        SocialAidModernColors.Purple
    } else {
        Color.White.copy(alpha = 0.78f)
    }

    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = if (isUser) "我" else contactName,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 12.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                fontSize = 14.sp,
                color = if (isUser) Color.White else SocialAidModernColors.Ink
            )
        }
    }
}

/**
 * 空会话状态
 */
@Composable
private fun EmptySessionState(
    onUploadScreenshot: () -> Unit,
    onCreateManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "还没有对话记录",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "上传聊天截图或手动创建会话",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onUploadScreenshot) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("上传截图")
            }

            Button(onClick = onCreateManual) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("手动创建")
            }
        }
    }
}

/**
 * 加载卡片
 */
@Composable
private fun LoadingCard(message: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, fontSize = 14.sp)
        }
    }
}

@Composable
fun ConversationAnalysisCard(
    analysis: ConversationContextAnalysis,
    onAnalysisChange: ((ConversationContextAnalysis) -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedScene by remember(analysis.scene) { mutableStateOf(analysis.scene) }
    var editedTone by remember(analysis.tone) { mutableStateOf(analysis.tone) }
    var editedEmotion by remember(analysis.emotion) { mutableStateOf(analysis.emotion) }
    var editedIntent by remember(analysis.intent) { mutableStateOf(analysis.intent) }
    var editedStrategy by remember(analysis.strategy) { mutableStateOf(analysis.strategy) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "对话分析",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SocialAidModernColors.PurpleDeep
                )
                if (onAnalysisChange != null) {
                    if (isEditing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    onAnalysisChange(
                                        ConversationContextAnalysis(
                                            scene = editedScene,
                                            tone = editedTone,
                                            emotion = editedEmotion,
                                            intent = editedIntent,
                                            strategy = editedStrategy
                                        )
                                    )
                                    isEditing = false
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "保存",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    editedScene = analysis.scene
                                    editedTone = analysis.tone
                                    editedEmotion = analysis.emotion
                                    editedIntent = analysis.intent
                                    editedStrategy = analysis.strategy
                                    isEditing = false
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "取消",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = SocialAidModernColors.PurpleDeep
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (isEditing) {
                OutlinedTextField(
                    value = editedScene,
                    onValueChange = { editedScene = it },
                    label = { Text("场景", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = editedTone,
                    onValueChange = { editedTone = it },
                    label = { Text("语气", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = editedEmotion,
                    onValueChange = { editedEmotion = it },
                    label = { Text("情绪", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = editedIntent,
                    onValueChange = { editedIntent = it },
                    label = { Text("意图", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = editedStrategy,
                    onValueChange = { editedStrategy = it },
                    label = { Text("回复策略", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            } else {
                AnalysisLine(label = "场景", value = analysis.scene)
                AnalysisLine(label = "语气", value = analysis.tone)
                AnalysisLine(label = "情绪", value = analysis.emotion)
                AnalysisLine(label = "意图", value = analysis.intent)
                AnalysisLine(label = "回复策略", value = analysis.strategy)
            }
        }
    }
}

@Composable
private fun AnalysisLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label：",
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = SocialAidModernColors.Ink,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 建议结果卡片
 */
@Composable
fun SuggestionsResultCard(
    suggestions: ReplySuggestions,
    replyStyle: ReplyStyle?,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    versionIndex: Int = 0,
    versionCount: Int = 0,
    onPreviousVersion: () -> Unit = {},
    onNextVersion: () -> Unit = {}
) {
    val context = LocalContext.current

    fun copySuggestion(text: String) {
        onCopy(text)
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI 推荐回复",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SocialAidModernColors.PurpleDeep
                )

                TextButton(onClick = onRegenerate) {
                    Text("重新生成")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (replyStyle == null) {
                if (suggestions.formal.isNotBlank()) {
                    ReplySuggestionCardWithCopy(
                        label = "👔 礼貌正式",
                        text = suggestions.formal,
                        backgroundColor = Color(0xFFE3F2FD),
                        onCopy = { copySuggestion(suggestions.formal) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (suggestions.casual.isNotBlank()) {
                    ReplySuggestionCardWithCopy(
                        label = "🍃 轻松随意",
                        text = suggestions.casual,
                        backgroundColor = Color(0xFFF1F8E9),
                        onCopy = { copySuggestion(suggestions.casual) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (suggestions.humorous.isNotBlank()) {
                    ReplySuggestionCardWithCopy(
                        label = "🤡 幽默化解",
                        text = suggestions.humorous,
                        backgroundColor = Color(0xFFFFF3E0),
                        onCopy = { copySuggestion(suggestions.humorous) }
                    )
                }
            } else {
                val styleLabel = when (replyStyle) {
                    ReplyStyle.FORMAL -> "👔 正式"
                    ReplyStyle.CASUAL -> "😊 随意"
                    ReplyStyle.HUMOROUS -> "🤡 幽默"
                    ReplyStyle.EMPATHETIC -> "💓 共情"
                    ReplyStyle.ASSERTIVE -> "💪 坚定"
                    ReplyStyle.DAI_ZHOU_LI -> "📜 大周礼"
                    ReplyStyle.OTHER -> "🎨 自定义"
                }

                if (suggestions.formal.isNotBlank()) {
                    ReplySuggestionCardWithCopy(
                        label = "$styleLabel · 建议一",
                        text = suggestions.formal,
                        backgroundColor = Color(0xFFE3F2FD),
                        onCopy = { copySuggestion(suggestions.formal) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (suggestions.casual.isNotBlank()) {
                    ReplySuggestionCardWithCopy(
                        label = "$styleLabel · 建议二",
                        text = suggestions.casual,
                        backgroundColor = Color(0xFFF1F8E9),
                        onCopy = { copySuggestion(suggestions.casual) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (suggestions.humorous.isNotBlank()) {
                    ReplySuggestionCardWithCopy(
                        label = "$styleLabel · 建议三",
                        text = suggestions.humorous,
                        backgroundColor = Color(0xFFFFF3E0),
                        onCopy = { copySuggestion(suggestions.humorous) }
                    )
                }
            }

            if (suggestions.formal.isBlank() && suggestions.casual.isBlank() && suggestions.humorous.isBlank()) {
                Text(
                    text = "未能生成回复建议，请重试",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            if (versionCount > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousVersion,
                        enabled = versionIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "上一版",
                            tint = if (versionIndex > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                    Text(
                        text = "${versionIndex + 1}/$versionCount",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SocialAidModernColors.PurpleDeep
                    )
                    IconButton(
                        onClick = onNextVersion,
                        enabled = versionIndex < versionCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "下一版",
                            tint = if (versionIndex < versionCount - 1)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 带有复制按钮的回复建议卡片
 */
@Composable
private fun ReplySuggestionCardWithCopy(
    label: String,
    text: String,
    backgroundColor: Color,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = 0.92f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.86f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SocialAidModernColors.PurpleDeep
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = SocialAidModernColors.Ink
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 错误卡片
 */
@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * 会话选择对话框
 */
@Composable
private fun SessionSelectionDialog(
    sessions: List<ChatSession>,
    onSelectSession: (String) -> Unit,
    onCreateSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newSessionName by remember { mutableStateOf("") }
    var showCreateInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择或创建会话") },
        text = {
            Column {
                if (sessions.isNotEmpty()) {
                    Text("已有会话", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(sessions, key = { it.sessionId }) { session ->
                            SessionListItem(
                                session = session,
                                onClick = { onSelectSession(session.sessionId) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (showCreateInput) {
                    OutlinedTextField(
                        value = newSessionName,
                        onValueChange = { newSessionName = it },
                        label = { Text("联系人名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(
                        onClick = { showCreateInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("创建新会话")
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateInput) {
                TextButton(
                    onClick = {
                        if (newSessionName.isNotBlank()) {
                            onCreateSession(newSessionName)
                        }
                    },
                    enabled = newSessionName.isNotBlank()
                ) {
                    Text("创建")
                }
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
 * 会话列表项
 */
@Composable
private fun SessionListItem(
    session: ChatSession,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(
                avatarUri = session.contactAvatarUri,
                contactName = session.contactName,
                size = 38.dp
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.contactName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "${session.messages.size} 条消息",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = dateFormat.format(Date(session.updatedAt)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 添加消息对话框
 */
@Composable
private fun AddMessageDialog(
    onAddMessage: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var messageContent by remember { mutableStateOf("") }
    var isUser by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加消息") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isUser,
                        onClick = { isUser = false },
                        label = { Text("对方") }
                    )
                    FilterChip(
                        selected = isUser,
                        onClick = { isUser = true },
                        label = { Text("我") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = messageContent,
                    onValueChange = { messageContent = it },
                    label = { Text("消息内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (messageContent.isNotBlank()) {
                        onAddMessage(messageContent, isUser)
                    }
                },
                enabled = messageContent.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ExpressionProfileStatusChip(
    profile: PersonalExpressionProfile?,
    onClick: () -> Unit
) {
    val statusText = when {
        profile == null -> "未建立"
        profile.enabled && profile.questionnaireCompleted -> "已启用"
        profile.questionnaireCompleted -> "已暂停"
        else -> "未建立"
    }

    val statusColor = when (statusText) {
        "已启用" -> MaterialTheme.colorScheme.primary
        "已暂停" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val chipBg = when (statusText) {
        "已启用" -> SocialAidModernColors.Purple.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = chipBg,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = when (statusText) {
                "已启用" -> SocialAidModernColors.Purple.copy(alpha = 0.24f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = if (statusText == "已启用") SocialAidModernColors.Purple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "个人表达档案",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SocialAidModernColors.Ink
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}




