package com.socialfirstaidkit.feature.socialcopy.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.socialfirstaidkit.core.llm.model.MomentCopySuggestion
import com.socialfirstaidkit.core.llm.model.MomentImageAnalysis
import com.socialfirstaidkit.core.ui.components.AiThinkingIndicator
import com.socialfirstaidkit.core.ui.theme.GlassCard
import com.socialfirstaidkit.core.ui.theme.HeroPanel
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import com.socialfirstaidkit.feature.socialcopy.model.MomentImageStyleOption
import com.socialfirstaidkit.feature.socialcopy.model.SocialCopyHistory
import com.socialfirstaidkit.feature.socialcopy.model.SocialCopyStyleOption
import com.socialfirstaidkit.feature.socialcopy.model.sortedForDisplay
import com.socialfirstaidkit.feature.socialcopy.model.sortedImageStylesForDisplay
import com.socialfirstaidkit.feature.socialcopy.viewmodel.SocialCopyViewModel
import com.socialfirstaidkit.feature.socialcopy.viewmodel.SocialCopyViewModel.SocialCopyUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class SocialCopyHistoryPage {
    MAIN,
    HISTORY_LIST,
    HISTORY_DETAIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialCopyScreen(
    viewModel: SocialCopyViewModel = hiltViewModel(),
    onMainScreenStateChange: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val styleOptions by viewModel.styleOptions.collectAsState()
    val deletedStyleOptions by viewModel.deletedStyleOptions.collectAsState()
    val selectedStyleId by viewModel.selectedStyleId.collectAsState()
    val imageStyleOptions by viewModel.imageStyleOptions.collectAsState()
    val deletedImageStyleOptions by viewModel.deletedImageStyleOptions.collectAsState()
    val selectedImageStyleId by viewModel.selectedImageStyleId.collectAsState()
    val additionalInfo by viewModel.additionalInfo.collectAsState()
    val history by viewModel.history.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showStyleManagement by remember { mutableStateOf(false) }
    var showImageStyleManagement by remember { mutableStateOf(false) }
    var historyPage by remember { mutableStateOf(SocialCopyHistoryPage.MAIN) }
    var selectedHistory by remember { mutableStateOf<SocialCopyHistory?>(null) }
    var currentStep by remember { mutableIntStateOf(1) }
    var isTextOnlyMode by remember { mutableStateOf(false) }

    BackHandler(
        enabled = showStyleManagement ||
            showImageStyleManagement ||
            historyPage != SocialCopyHistoryPage.MAIN ||
            currentStep > 1
    ) {
        when {
            showStyleManagement -> showStyleManagement = false
            showImageStyleManagement -> showImageStyleManagement = false
            historyPage == SocialCopyHistoryPage.HISTORY_DETAIL -> {
                historyPage = SocialCopyHistoryPage.HISTORY_LIST
            }
            historyPage == SocialCopyHistoryPage.HISTORY_LIST -> {
                historyPage = SocialCopyHistoryPage.MAIN
            }
            currentStep > 1 -> {
                currentStep--
            }
        }
    }

    LaunchedEffect(showStyleManagement, showImageStyleManagement, historyPage) {
        onMainScreenStateChange(
            !showStyleManagement &&
                !showImageStyleManagement &&
                historyPage == SocialCopyHistoryPage.MAIN
        )
    }

    DisposableEffect(Unit) {
        onDispose { onMainScreenStateChange(true) }
    }

    if (showStyleManagement) {
        SocialCopyStyleManagementScreen(
            styleOptions = styleOptions,
            deletedStyleOptions = deletedStyleOptions,
            onBack = { showStyleManagement = false },
            onAddStyle = { name, description -> viewModel.addCustomStyle(name, description) },
            onUpdateStyle = { id, name, description -> viewModel.updateCustomStyle(id, name, description) },
            onDeleteStyle = { id -> viewModel.deleteCustomStyle(id) },
            onRestoreStyle = { id -> viewModel.restoreDeletedStyle(id) },
            onPermanentDeleteStyle = { id -> viewModel.permanentlyDeleteStyle(id) },
            onMoveStyle = { fromIndex, toIndex -> viewModel.moveStyleOption(fromIndex, toIndex) }
        )
        return
    }

    if (showImageStyleManagement) {
        MomentImageStyleManagementScreen(
            imageStyleOptions = imageStyleOptions,
            deletedImageStyleOptions = deletedImageStyleOptions,
            onBack = { showImageStyleManagement = false },
            onAddStyle = { name, description, prompt -> viewModel.addCustomImageStyle(name, description, prompt) },
            onUpdateStyle = { id, name, description, prompt -> viewModel.updateCustomImageStyle(id, name, description, prompt) },
            onDeleteStyle = { id -> viewModel.deleteCustomImageStyle(id) },
            onRestoreStyle = { id -> viewModel.restoreDeletedImageStyle(id) },
            onPermanentDeleteStyle = { id -> viewModel.permanentlyDeleteImageStyle(id) },
            onMoveStyle = { fromIndex, toIndex -> viewModel.moveImageStyleOption(fromIndex, toIndex) }
        )
        return
    }

    fun copyText(text: String) {
        if (text.isBlank()) return
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    when (historyPage) {
        SocialCopyHistoryPage.HISTORY_LIST -> {
            SocialCopyHistoryListScreen(
                histories = history,
                onBack = { historyPage = SocialCopyHistoryPage.MAIN },
                onSelect = {
                    selectedHistory = it
                    historyPage = SocialCopyHistoryPage.HISTORY_DETAIL
                },
                onClearAll = { viewModel.clearHistory() },
                onDeleteHistories = { list -> viewModel.deleteHistories(list) }
            )
            return
        }
        SocialCopyHistoryPage.HISTORY_DETAIL -> {
            SocialCopyHistoryDetailScreen(
                history = selectedHistory,
                onBack = { historyPage = SocialCopyHistoryPage.HISTORY_LIST },
                onCopy = ::copyText,
                onDelete = { viewModel.deleteHistory(it) },
                onDeleted = {
                    selectedHistory = null
                    historyPage = SocialCopyHistoryPage.HISTORY_LIST
                }
            )
            return
        }
        SocialCopyHistoryPage.MAIN -> Unit
    }

    fun shareGeneratedImage(imageUrl: String, copyText: String) {
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    when {
                        imageUrl.startsWith("content://", ignoreCase = true) -> Uri.parse(imageUrl)
                        imageUrl.startsWith("file:", ignoreCase = true) -> {
                            val filePath = Uri.parse(imageUrl).path ?: error("Image file path is empty")
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                File(filePath)
                            )
                        }
                        imageUrl.startsWith("/") -> {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                File(imageUrl)
                            )
                        }
                        else -> {
                            val connection = URL(imageUrl).openConnection().apply {
                                connectTimeout = 30_000
                                readTimeout = 90_000
                            }
                            val bytes = connection.getInputStream().use { it.readBytes() }
                            val tempFile = File(context.cacheDir, "moment_share_${System.currentTimeMillis()}.jpg")
                            tempFile.writeBytes(bytes)
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                tempFile
                            )
                        }
                    }
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, copyText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(context.contentResolver, "Moment share image", uri)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share to"))
            } catch (e: Exception) {
                Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun resetAll() {
        viewModel.reset()
        currentStep = 1
        isTextOnlyMode = false
    }

    fun copyAndComplete(text: String) {
        if (text.isBlank()) return
        viewModel.recordCopiedSuggestion(text)
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制到剪贴板，创作完成", Toast.LENGTH_SHORT).show()
        resetAll()
    }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            isTextOnlyMode = false
            viewModel.processImage(it)
        }
    }

    LaunchedEffect(uiState.suggestions) {
        if (uiState.suggestions.isNotEmpty() && currentStep == 1) {
            currentStep = 2
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "朋友圈创作",
                color = SocialAidModernColors.Ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentStep > 1) {
                    TextButton(
                        onClick = { resetAll() },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重新开始", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(
                    onClick = { historyPage = SocialCopyHistoryPage.HISTORY_LIST },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("历史", fontSize = 13.sp)
                }
            }
        }

        StepIndicator(
            currentStep = currentStep,
            isTextOnly = isTextOnlyMode,
            onStepClick = { step ->
                currentStep = step
            },
            canNavigateToStep = { step ->
                when (step) {
                    1 -> true
                    2 -> uiState.suggestions.isNotEmpty()
                    3 -> !isTextOnlyMode && uiState.suggestions.isNotEmpty()
                    else -> false
                }
            }
        )

        uiState.errorMessage?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = viewModel::clearError
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (currentStep) {
                1 -> StepOneContent(
                    uiState = uiState,
                    isTextOnlyMode = isTextOnlyMode,
                    additionalInfo = additionalInfo,
                    selectedStyleId = selectedStyleId,
                    styleOptions = styleOptions,
                    onModeChange = { textOnly ->
                        isTextOnlyMode = textOnly
                        if (textOnly) {
                            viewModel.setupTextOnlyMode(preserveInput = true)
                        } else {
                            viewModel.setupImageMode(preserveInput = true)
                        }
                    },
                    onPickMedia = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAdditionalInfoChange = viewModel::setAdditionalInfo,
                    onStyleSelected = viewModel::setSelectedStyleId,
                    onGenerateClick = viewModel::generateMomentCopy,
                    onManageStyles = { showStyleManagement = true },
                    onAnalysisChange = viewModel::updateImageAnalysis
                )
                2 -> StepTwoContent(
                    suggestions = uiState.suggestions,
                    selectedIndex = uiState.selectedSuggestionIndex,
                    isGeneratingCopy = uiState.isGeneratingCopy,
                    isTextOnly = isTextOnlyMode,
                    onSelect = viewModel::selectSuggestion,
                    onRegenerate = viewModel::generateMomentCopy,
                    onCopyClick = { text ->
                        viewModel.recordCopiedSuggestion(text)
                        copyText(text)
                    },
                    onComplete = { text -> copyAndComplete(text) },
                    onGoToStep3 = { currentStep = 3 },
                    onBack = { currentStep = 1 },
                    onUpdateSuggestion = { index, newContent ->
                        viewModel.updateSuggestionContent(index, newContent)
                    }
                )
                3 -> StepThreeContent(
                    uiState = uiState,
                    imageStyleOptions = imageStyleOptions,
                    selectedImageStyleId = selectedImageStyleId,
                    onStyleSelected = viewModel::setSelectedImageStyleId,
                    onGenerateShareImage = viewModel::generateStyledImage,
                    onManageImageStyles = { showImageStyleManagement = true },
                    onBack = { currentStep = 2 },
                    onShare = { imageUrl, copyText -> shareGeneratedImage(imageUrl, copyText) }
                )
            }
        }
    }
}

@Composable
private fun CompactMomentHeader(
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "朋友圈创作",
            color = SocialAidModernColors.Ink,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            onClick = onHistoryClick,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("历史", fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SocialCopyHistoryListScreen(
    histories: List<SocialCopyHistory>,
    onBack: () -> Unit,
    onSelect: (SocialCopyHistory) -> Unit,
    onClearAll: () -> Unit,
    onDeleteHistories: (List<SocialCopyHistory>) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 有图模式, 1: 纯文字模式
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    val filteredHistories = remember(histories, selectedTab) {
        histories.filter { it.isTextOnly == (selectedTab == 1) }
    }

    LaunchedEffect(selectedTab) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (isSelectionMode) "已选择 ${selectedIds.size} 项" else "朋友圈创作历史", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
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
                },
                actions = {
                    if (isSelectionMode) {
                        if (selectedIds.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val itemsToDelete = histories.filter { selectedIds.contains(it.id) }
                                    onDeleteHistories(itemsToDelete)
                                    isSelectionMode = false
                                    selectedIds = emptySet()
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        if (histories.isNotEmpty()) {
                            IconButton(onClick = onClearAll) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "清空历史")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf("📷 有图历史", "✍️ 纯文字历史")
                modes.forEachIndexed { index, title ->
                    val selected = index == selectedTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredHistories.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "暂无历史记录",
                                    fontWeight = FontWeight.Bold,
                                    color = SocialAidModernColors.Ink
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (selectedTab == 0) "生成有图朋友圈文案后，会自动保存在这里。" else "生成纯文字朋友圈文案后，会自动保存在这里。",
                                    color = SocialAidModernColors.Muted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    items(filteredHistories, key = { it.id }) { history ->
                        val isSelected = selectedIds.contains(history.id)
                        SocialCopyHistoryRow(
                            history = history,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
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
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SocialCopyHistoryRow(
    history: SocialCopyHistory,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = history.styleName,
                        fontWeight = FontWeight.SemiBold,
                        color = SocialAidModernColors.Ink,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (history.isTextOnly) "纯文字" else "图片",
                        color = SocialAidModernColors.Muted,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = history.suggestions.firstOrNull()?.content.orEmpty().ifBlank { history.analysis.insight },
                    color = SocialAidModernColors.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(history.createdAt)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
            if (!isSelectionMode) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocialCopyHistoryDetailScreen(
    history: SocialCopyHistory?,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (SocialCopyHistory) -> Unit,
    onDeleted: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    history?.let { item ->
                        IconButton(
                            onClick = {
                                onDelete(item)
                                onDeleted()
                            }
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "删除记录",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (history == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("记录不存在", color = SocialAidModernColors.Muted)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = history.styleName,
                            fontWeight = FontWeight.Bold,
                            color = SocialAidModernColors.Ink
                        )
                        Text(
                            text = dateFormat.format(Date(history.createdAt)),
                            color = SocialAidModernColors.Muted,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "主题：${history.analysis.subject} / ${history.analysis.scene}",
                            color = SocialAidModernColors.Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        history.additionalInfo?.let {
                            Text(
                                text = "补充：$it",
                                color = SocialAidModernColors.Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (!history.isTextOnly) {
                item {
                    var showOriginalDetail by remember { mutableStateOf(false) }
                    var showGeneratedDetail by remember { mutableStateOf(false) }
                    
                    val originalUri = remember(history.imageUriString) {
                        history.imageUriString?.let { Uri.parse(it) }
                    }
                    
                    if (showOriginalDetail && originalUri != null) {
                        ZoomableImageDialog(
                            imageModel = originalUri,
                            contentDescription = "上传的原图",
                            onDismiss = { showOriginalDetail = false }
                        )
                    }
                    if (showGeneratedDetail && history.generatedImageUrl != null) {
                        ZoomableImageDialog(
                            imageModel = history.generatedImageUrl,
                            contentDescription = "生成的分享图",
                            onDismiss = { showGeneratedDetail = false }
                        )
                    }

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(title = "素材与图片加工")
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (originalUri != null) {
                                    PreviewImageColumn(
                                        label = "当时上传的图片",
                                        model = originalUri,
                                        modifier = Modifier.weight(1f),
                                        onClick = { showOriginalDetail = true }
                                    )
                                }
                                if (history.generatedImageUrl != null) {
                                    PreviewImageColumn(
                                        label = "生成的分享图",
                                        model = history.generatedImageUrl,
                                        modifier = Modifier.weight(1f),
                                        onClick = { showGeneratedDetail = true }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                                            .border(1.dp, SocialAidModernColors.Hairline, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "未生成分享图",
                                            fontSize = 12.sp,
                                            color = SocialAidModernColors.Muted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(history.suggestions) { suggestion ->
                CopySuggestionItem(
                    suggestion = suggestion,
                    index = history.suggestions.indexOf(suggestion),
                    selected = false,
                    onSelect = {},
                    onCopy = { onCopy(suggestion.content) }
                )
            }
        }
    }
}

@Composable
private fun EmptyCreationState() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "从一张图片开始",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SocialAidModernColors.Ink
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "选择食物、旅行、学习办公或日常生活照片后，AI 会先识别画面，再生成朋友圈文案和分享图。",
                style = MaterialTheme.typography.bodyMedium,
                color = SocialAidModernColors.Muted,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun LoadingCard(text: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiThinkingIndicator()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = SocialAidModernColors.Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }
    }
}

@Composable
private fun ImagePreviewCard(
    imageUri: Uri,
    isAnalyzing: Boolean
) {
    var showImageDetail by remember { mutableStateOf(false) }
    if (showImageDetail) {
        ZoomableImageDialog(
            imageModel = imageUri,
            contentDescription = "原图详情",
            onDismiss = { showImageDetail = false }
        )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "图片预览")
            Spacer(modifier = Modifier.height(10.dp))
            Box {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "选中的图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showImageDetail = true },
                    contentScale = ContentScale.Fit
                )
                if (isAnalyzing) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "识别中",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisSummaryCard(
    analysis: MomentImageAnalysis,
    onAnalysisChange: ((MomentImageAnalysis) -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedSubject by remember(analysis.subject) { mutableStateOf(analysis.subject) }
    var editedScene by remember(analysis.scene) { mutableStateOf(analysis.scene) }
    var editedMood by remember(analysis.mood) { mutableStateOf(analysis.mood) }
    var editedThemes by remember(analysis.themes) { 
        mutableStateOf(analysis.themes.joinToString("、")) 
    }
    var editedInsight by remember(analysis.insight) { mutableStateOf(analysis.insight) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "AI 识别结果")
                if (onAnalysisChange != null) {
                    if (isEditing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    val themeList = editedThemes.split("、", ",", "，", " ").map { it.trim() }.filter { it.isNotEmpty() }
                                    onAnalysisChange(
                                        MomentImageAnalysis(
                                            subject = editedSubject,
                                            scene = editedScene,
                                            mood = editedMood,
                                            themes = themeList,
                                            insight = editedInsight
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
                                    editedSubject = analysis.subject
                                    editedScene = analysis.scene
                                    editedMood = analysis.mood
                                    editedThemes = analysis.themes.joinToString("、")
                                    editedInsight = analysis.insight
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
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isEditing) {
                OutlinedTextField(
                    value = editedSubject,
                    onValueChange = { editedSubject = it },
                    label = { Text("主体", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = editedScene,
                    onValueChange = { editedScene = it },
                    label = { Text("场景", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = editedMood,
                    onValueChange = { editedMood = it },
                    label = { Text("氛围", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = editedThemes,
                    onValueChange = { editedThemes = it },
                    label = { Text("主题 (用顿号“、”分隔)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = editedInsight,
                    onValueChange = { editedInsight = it },
                    label = { Text("洞察理解", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    minLines = 2
                )
            } else {
                AnalysisRow(label = "主体", value = analysis.subject)
                AnalysisRow(label = "场景", value = analysis.scene)
                AnalysisRow(label = "氛围", value = analysis.mood)
                AnalysisRow(
                    label = "主题",
                    value = analysis.themes.joinToString("、").ifBlank { "日常记录" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = analysis.insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SocialAidModernColors.Ink,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun AnalysisRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(44.dp),
            color = SocialAidModernColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value.ifBlank { "未识别" },
            modifier = Modifier.weight(1f),
            color = SocialAidModernColors.Ink,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun AdditionalInfoField(
    value: String,
    onValueChange: (String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "补充信息")
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("想表达什么") },
                placeholder = { Text("例如：周末放松、和朋友吃饭、不要太夸张") },
                singleLine = true
            )
        }
    }
}

@Composable
private fun SocialCopyStyleSelector(
    styleOptions: List<SocialCopyStyleOption>,
    selectedStyleId: String,
    onStyleSelected: (String) -> Unit,
    onManageStyles: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeaderWithAction(
                title = "文案风格",
                actionLabel = "自定义",
                onActionClick = onManageStyles
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                styleOptions.sortedForDisplay().forEach { option ->
                    FilterChip(
                        selected = selectedStyleId == option.id,
                        onClick = { onStyleSelected(option.id) },
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
    }
}

@Composable
private fun CopySuggestionsSection(
    suggestions: List<MomentCopySuggestion>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onCopy: (String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(title = "朋友圈文案")
            suggestions.forEachIndexed { index, suggestion ->
                CopySuggestionItem(
                    suggestion = suggestion,
                    index = index,
                    selected = index == selectedIndex,
                    onSelect = { onSelect(index) },
                    onCopy = { onCopy(suggestion.content) }
                )
            }
        }
    }
}

@Composable
private fun CopySuggestionItem(
    suggestion: MomentCopySuggestion,
    index: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        color = if (selected) SocialAidModernColors.Purple.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) SocialAidModernColors.Purple else SocialAidModernColors.Hairline
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "方案 ${index + 1} · ${suggestion.styleName}",
                    modifier = Modifier.weight(1f),
                    color = SocialAidModernColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = SocialAidModernColors.Purple,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制文案",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Text(
                text = suggestion.content,
                color = SocialAidModernColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun MomentImageStyleSelector(
    imageStyleOptions: List<MomentImageStyleOption>,
    selectedStyleId: String,
    onStyleSelected: (String) -> Unit,
    onManageStyles: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeaderWithAction(
                title = "AI 图片加工",
                actionLabel = "自定义",
                onActionClick = onManageStyles
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imageStyleOptions.sortedImageStylesForDisplay().forEach { option ->
                    FilterChip(
                        selected = selectedStyleId == option.id,
                        onClick = { onStyleSelected(option.id) },
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = imageStyleOptions.firstOrNull { it.id == selectedStyleId }?.description.orEmpty(),
                color = SocialAidModernColors.Muted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun GeneratedShareImageSection(
    originalImageUri: Uri,
    generatedImageUrl: String,
    selectedCopy: String,
    onShare: () -> Unit
) {
    var showGeneratedDetail by remember { mutableStateOf(false) }
    if (showGeneratedDetail) {
        ZoomableImageDialog(
            imageModel = generatedImageUrl,
            contentDescription = "分享图详情",
            onDismiss = { showGeneratedDetail = false }
        )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "分享图预览")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PreviewImageColumn(
                    label = "原图",
                    model = originalImageUri,
                    modifier = Modifier.weight(1f)
                )
                PreviewImageColumn(
                    label = "成图",
                    model = generatedImageUrl,
                    modifier = Modifier.weight(1f),
                    onClick = { showGeneratedDetail = true }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onShare,
                enabled = selectedCopy.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("分享发布")
            }
        }
    }
}

@Composable
private fun PreviewImageColumn(
    label: String,
    model: Any,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = SocialAidModernColors.Muted
        )
        Spacer(modifier = Modifier.height(5.dp))
        AsyncImage(
            model = model,
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = SocialAidModernColors.Ink,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
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
        SectionHeader(title = title)
        Surface(
            modifier = Modifier.clickable(onClick = onActionClick),
            color = Color.White.copy(alpha = 0.78f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, SocialAidModernColors.Hairline)
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
fun SocialCopyStyleManagementScreen(
    styleOptions: List<SocialCopyStyleOption>,
    deletedStyleOptions: List<SocialCopyStyleOption>,
    onBack: () -> Unit,
    onAddStyle: (String, String) -> Unit,
    onUpdateStyle: (String, String, String) -> Unit,
    onDeleteStyle: (String) -> Unit,
    onRestoreStyle: (String) -> Unit,
    onPermanentDeleteStyle: (String) -> Unit,
    onMoveStyle: (Int, Int) -> Unit
) {
    var editingOption by remember { mutableStateOf<SocialCopyStyleOption?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    if (showEditor) {
        SocialCopyStyleEditorDialog(
            option = editingOption,
            onDismiss = {
                showEditor = false
                editingOption = null
            },
            onSave = { name, description ->
                val option = editingOption
                if (option == null) {
                    onAddStyle(name, description)
                } else {
                    onUpdateStyle(option.id, name, description)
                }
                showEditor = false
                editingOption = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("朋友圈创作风格", fontWeight = FontWeight.Bold) },
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
                            editingOption = null
                            showEditor = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新增风格")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "这里会展示系统默认风格和你新增的朋友圈创作风格。你可以新增、编辑、删除，并调整展示顺序。",
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

            Text(
                text = "当前风格",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SocialAidModernColors.Ink
            )

            styleOptions.sortedForDisplay().forEachIndexed { index, option ->
                SocialCopyStyleOptionRow(
                    option = option,
                    index = index,
                    itemCount = styleOptions.size,
                    onEdit = {
                        editingOption = option
                        showEditor = true
                    },
                    onDelete = { onDeleteStyle(option.id) },
                    onMove = onMoveStyle
                )
            }
        }
    }

    if (showHistory) {
        DeletedSocialCopyStylesDialog(
            title = "朋友圈创作风格历史记录",
            styles = deletedStyleOptions,
            onDismiss = { showHistory = false },
            onRestore = onRestoreStyle,
            onPermanentDelete = onPermanentDeleteStyle
        )
    }
}

@Composable
private fun SocialCopyStyleOptionRow(
    option: SocialCopyStyleOption,
    index: Int,
    itemCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    var dragOffsetY by remember { mutableStateOf(0f) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "拖动排序",
                tint = SocialAidModernColors.Muted,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .pointerInput(index, itemCount) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { dragOffsetY = 0f },
                            onDragCancel = { dragOffsetY = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                            if (dragOffsetY > thresholdPx && index < itemCount - 1) {
                                onMove(index, index + 1)
                                dragOffsetY -= thresholdPx
                            } else if (dragOffsetY < -thresholdPx && index > 0) {
                                onMove(index, index - 1)
                                dragOffsetY += thresholdPx
                            }
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
                            border = BorderStroke(1.dp, SocialAidModernColors.Hairline)
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

@Composable
private fun SocialCopyStyleEditorDialog(
    option: SocialCopyStyleOption?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(option?.id) { mutableStateOf(option?.name.orEmpty()) }
    var description by remember(option?.id) { mutableStateOf(option?.description.orEmpty()) }
    val canSave = name.isNotBlank() && description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (option == null) "新增创作风格" else "编辑创作风格") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("风格名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("风格说明") },
                    minLines = 3,
                    placeholder = { Text("例如：松弛、有画面感，像朋友随手记录生活") }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name.trim(), description.trim()) }
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
private fun DeletedSocialCopyStylesDialog(
    title: String,
    styles: List<SocialCopyStyleOption>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onPermanentDelete: (String) -> Unit
) {
    DeletedStyleHistoryDialog(
        title = title,
        items = styles.map { DeletedStyleHistoryItem(it.id, it.name, it.description) },
        onDismiss = onDismiss,
        onRestore = onRestore,
        onPermanentDelete = onPermanentDelete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MomentImageStyleManagementScreen(
    imageStyleOptions: List<MomentImageStyleOption>,
    deletedImageStyleOptions: List<MomentImageStyleOption>,
    onBack: () -> Unit,
    onAddStyle: (String, String, String) -> Unit,
    onUpdateStyle: (String, String, String, String) -> Unit,
    onDeleteStyle: (String) -> Unit,
    onRestoreStyle: (String) -> Unit,
    onPermanentDeleteStyle: (String) -> Unit,
    onMoveStyle: (Int, Int) -> Unit
) {
    var editingOption by remember { mutableStateOf<MomentImageStyleOption?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    if (showEditor) {
        MomentImageStyleEditorDialog(
            option = editingOption,
            onDismiss = {
                showEditor = false
                editingOption = null
            },
            onSave = { name, description, prompt ->
                val option = editingOption
                if (option == null) {
                    onAddStyle(name, description, prompt)
                } else {
                    onUpdateStyle(option.id, name, description, prompt)
                }
                showEditor = false
                editingOption = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图片加工风格", fontWeight = FontWeight.Bold) },
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
                            editingOption = null
                            showEditor = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新增风格")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "这里会展示系统默认图片加工风格和你新增的自定义风格。自定义风格会作为 AI 图片加工提示词使用。",
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

            Text(
                text = "当前图片加工风格",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SocialAidModernColors.Ink
            )

            imageStyleOptions.sortedImageStylesForDisplay().forEachIndexed { index, option ->
                MomentImageStyleOptionRow(
                    option = option,
                    index = index,
                    itemCount = imageStyleOptions.size,
                    onEdit = {
                        editingOption = option
                        showEditor = true
                    },
                    onDelete = { onDeleteStyle(option.id) },
                    onMove = onMoveStyle
                )
            }
        }
    }

    if (showHistory) {
        DeletedMomentImageStylesDialog(
            title = "图片加工风格历史记录",
            styles = deletedImageStyleOptions,
            onDismiss = { showHistory = false },
            onRestore = onRestoreStyle,
            onPermanentDelete = onPermanentDeleteStyle
        )
    }
}

@Composable
private fun MomentImageStyleOptionRow(
    option: MomentImageStyleOption,
    index: Int,
    itemCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    var dragOffsetY by remember { mutableStateOf(0f) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "拖动排序",
                tint = SocialAidModernColors.Muted,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .pointerInput(index, itemCount) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { dragOffsetY = 0f },
                            onDragCancel = { dragOffsetY = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                            if (dragOffsetY > thresholdPx && index < itemCount - 1) {
                                onMove(index, index + 1)
                                dragOffsetY -= thresholdPx
                            } else if (dragOffsetY < -thresholdPx && index > 0) {
                                onMove(index, index - 1)
                                dragOffsetY += thresholdPx
                            }
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
                            border = BorderStroke(1.dp, SocialAidModernColors.Hairline)
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

@Composable
private fun MomentImageStyleEditorDialog(
    option: MomentImageStyleOption?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(option?.id) { mutableStateOf(option?.name.orEmpty()) }
    var description by remember(option?.id) { mutableStateOf(option?.description.orEmpty()) }
    var prompt by remember(option?.id) { mutableStateOf(option?.prompt.orEmpty()) }
    val canSave = name.isNotBlank() && description.isNotBlank() && prompt.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (option == null) "新增图片加工风格" else "编辑图片加工风格") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("风格名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("风格说明") },
                    minLines = 2,
                    placeholder = { Text("例如：像手账拼贴，轻量贴纸和留白") }
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("AI 加工提示词") },
                    minLines = 4,
                    placeholder = {
                        Text(
                            "可参考相似预设风格来写：先说明目标风格，再约束保留原图主体、场景和数量；只学习预设的线条、贴纸、留白、卡片或聚焦方式，不复制参考图内容；文字要少而准，不遮挡主体，不编造地点、品牌、身份或事件。"
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name.trim(), description.trim(), prompt.trim()) }
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
private fun DeletedMomentImageStylesDialog(
    title: String,
    styles: List<MomentImageStyleOption>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onPermanentDelete: (String) -> Unit
) {
    DeletedStyleHistoryDialog(
        title = title,
        items = styles.map { DeletedStyleHistoryItem(it.id, it.name, it.description) },
        onDismiss = onDismiss,
        onRestore = onRestore,
        onPermanentDelete = onPermanentDelete
    )
}

private data class DeletedStyleHistoryItem(
    val id: String,
    val name: String,
    val description: String
)

@Composable
private fun DeletedStyleHistoryDialog(
    title: String,
    items: List<DeletedStyleHistoryItem>,
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
                if (items.isEmpty()) {
                    Text(
                        text = "暂无删除记录",
                        color = SocialAidModernColors.Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    items.forEach { item ->
                        Surface(
                            color = Color.White.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SocialAidModernColors.Hairline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = item.description,
                                        color = SocialAidModernColors.Muted,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 18.sp
                                    )
                                }
                                IconButton(onClick = { onRestore(item.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "恢复")
                                }
                                IconButton(onClick = { onPermanentDelete(item.id) }) {
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
private fun ZoomableImageDialog(
    imageModel: Any,
    contentDescription: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = nextScale
                            if (nextScale == 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    isTextOnly: Boolean,
    onStepClick: (Int) -> Unit = {},
    canNavigateToStep: (Int) -> Boolean = { true }
) {
    val steps = if (isTextOnly) listOf("灵感配置", "文案精选") else listOf("灵感配置", "文案精选", "卡片设计")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, name ->
            val stepNum = index + 1
            val isActive = currentStep == stepNum
            val isCompleted = currentStep > stepNum
            val isClickable = canNavigateToStep(stepNum)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = isClickable) { onStepClick(stepNum) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive -> MaterialTheme.colorScheme.primary
                                isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Text(
                            text = stepNum.toString(),
                            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(6.dp))
                
                Text(
                    text = name,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }
            
            if (index < steps.size - 1) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun ModeSelector(isTextOnly: Boolean, onModeChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val modes = listOf("📷 有图模式", "✍️ 纯文字模式")
        modes.forEachIndexed { index, title ->
            val selected = (index == 1) == isTextOnly
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onModeChange(index == 1) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StepOneContent(
    uiState: SocialCopyUiState,
    isTextOnlyMode: Boolean,
    additionalInfo: String,
    selectedStyleId: String,
    styleOptions: List<SocialCopyStyleOption>,
    onModeChange: (Boolean) -> Unit,
    onPickMedia: () -> Unit,
    onAdditionalInfoChange: (String) -> Unit,
    onStyleSelected: (String) -> Unit,
    onGenerateClick: () -> Unit,
    onManageStyles: () -> Unit,
    onAnalysisChange: (MomentImageAnalysis) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModeSelector(isTextOnly = isTextOnlyMode, onModeChange = onModeChange)

        if (!isTextOnlyMode) {
            if (uiState.imageUri == null) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clickable(onClick = onPickMedia)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("点击上传照片开始创作", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("AI将自动智能识别照片场景和氛围", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                ImagePreviewCard(
                    imageUri = uiState.imageUri,
                    isAnalyzing = uiState.isAnalyzingImage
                )

                uiState.analysis?.let { analysis ->
                    AnalysisSummaryCard(
                        analysis = analysis,
                        onAnalysisChange = onAnalysisChange
                    )
                }
            }
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "✍️ 纯文字朋友圈灵感生成",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "无需配图。请在下方写下您想表达的心情或背景，选择一种风格，AI 就能为您量身定制文案建议。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        AdditionalInfoField(
            value = additionalInfo,
            onValueChange = onAdditionalInfoChange
        )

        SocialCopyStyleSelector(
            styleOptions = styleOptions,
            selectedStyleId = selectedStyleId,
            onStyleSelected = onStyleSelected,
            onManageStyles = onManageStyles
        )

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onGenerateClick,
            enabled = (isTextOnlyMode || uiState.analysis != null) && !uiState.isAnalyzingImage && !uiState.isGeneratingCopy,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (uiState.isGeneratingCopy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("文案灵感生成中...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (uiState.suggestions.isNotEmpty()) "重新生成文案" else "生成朋友圈文案", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompactAnalysisRow(analysis: com.socialfirstaidkit.core.llm.model.MomentImageAnalysis) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            "主体" to analysis.subject,
            "场景" to analysis.scene,
            "氛围" to analysis.mood
        ).forEach { (label, value) ->
            if (value.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = "$label: $value",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StepTwoContent(
    suggestions: List<com.socialfirstaidkit.core.llm.model.MomentCopySuggestion>,
    selectedIndex: Int,
    isGeneratingCopy: Boolean,
    isTextOnly: Boolean,
    onSelect: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onCopyClick: (String) -> Unit,
    onComplete: (String) -> Unit,
    onGoToStep3: () -> Unit,
    onBack: () -> Unit,
    onUpdateSuggestion: (Int, String) -> Unit
) {
    if (suggestions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无文案，请返回重新生成")
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = selectedIndex, pageCount = { suggestions.size })
    
    LaunchedEffect(pagerState.currentPage) {
        onSelect(pagerState.currentPage)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("修改输入")
            }

            Text(
                text = "文案精选 (${pagerState.currentPage + 1}/${suggestions.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            IconButton(
                onClick = onRegenerate,
                enabled = !isGeneratingCopy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "重新生成", modifier = Modifier.size(18.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp
        ) { page ->
            val suggestion = suggestions[page]
            var isEditing by remember { mutableStateOf(false) }
            var editText by remember { mutableStateOf(suggestion.content) }
            
            LaunchedEffect(suggestion.content) {
                editText = suggestion.content
            }

            GlassCard(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val scale = 1f - (kotlin.math.abs(pageOffset) * 0.08f).coerceIn(0f, 0.08f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (kotlin.math.abs(pageOffset) * 0.3f).coerceIn(0f, 0.3f)
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "方案 ${page + 1} · ${suggestion.styleName}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isEditing) {
                                        onUpdateSuggestion(page, editText)
                                    }
                                    isEditing = !isEditing
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isEditing) "保存" else "编辑",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onCopyClick(suggestion.content) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "复制",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (isEditing) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier.fillMaxSize(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                        } else {
                            val cardScrollState = rememberScrollState()
                            Text(
                                text = suggestion.content,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(cardScrollState),
                                color = SocialAidModernColors.Ink,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "共 ${suggestion.content.length} 字",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onComplete(suggestions[pagerState.currentPage].content) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("复制文案并完成", fontWeight = FontWeight.Bold)
            }

            if (!isTextOnly) {
                OutlinedButton(
                    onClick = onGoToStep3,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("制作艺术分享图 ->", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StepThreeContent(
    uiState: SocialCopyUiState,
    imageStyleOptions: List<MomentImageStyleOption>,
    selectedImageStyleId: String,
    onStyleSelected: (String) -> Unit,
    onGenerateShareImage: () -> Unit,
    onManageImageStyles: () -> Unit,
    onBack: () -> Unit,
    onShare: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("返回选文案")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("第三步：卡片设计", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        MomentImageStyleSelector(
            imageStyleOptions = imageStyleOptions,
            selectedStyleId = selectedImageStyleId,
            onStyleSelected = onStyleSelected,
            onManageStyles = onManageImageStyles
        )

        Button(
            onClick = onGenerateShareImage,
            enabled = uiState.analysis != null && !uiState.isGeneratingImage && !uiState.isAnalyzingImage,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            if (uiState.isGeneratingImage) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 风格化处理中...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (uiState.generatedImageUrl != null) "重新生成分享图" else "开始生成分享图", fontWeight = FontWeight.Bold)
            }
        }

        if (uiState.generatedImageUrl != null) {
            GeneratedShareImageSection(
                originalImageUri = uiState.imageUri ?: Uri.EMPTY,
                generatedImageUrl = uiState.generatedImageUrl,
                selectedCopy = uiState.selectedSuggestion?.content.orEmpty(),
                onShare = { onShare(uiState.generatedImageUrl, uiState.selectedSuggestion?.content.orEmpty()) }
            )
        } else {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "选好风格后，点击上面按钮生成精美的海报卡片",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
