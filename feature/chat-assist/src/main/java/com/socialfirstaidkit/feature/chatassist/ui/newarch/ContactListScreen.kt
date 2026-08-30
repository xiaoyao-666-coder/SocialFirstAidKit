package com.socialfirstaidkit.feature.chatassist.ui.newarch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.socialfirstaidkit.core.ui.theme.GlassCard
import com.socialfirstaidkit.core.ui.theme.HeroPanel
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onNavigateBack: () -> Unit = {},
    onContactClick: (String) -> Unit,
    onAddContact: () -> Unit = {},
    onStartRealtimeAssist: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    batchEditEnabled: Boolean = false,
    onBatchEditFinished: () -> Unit = {},
    viewModel: NewChatViewModel = hiltViewModel()
) {
    val contacts by viewModel.contactsWithLastMessage.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newContactName by remember { mutableStateOf("") }
    var selectedContactIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(batchEditEnabled) {
        if (!batchEditEnabled) selectedContactIds = emptySet()
    }
    LaunchedEffect(contacts) {
        val existingIds = contacts.map { it.contact.id }.toSet()
        selectedContactIds = selectedContactIds.intersect(existingIds)
    }

    val content = @Composable { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            HeroPanel(
                title = "联系人列表",
                subtitle = "选择一个聊天联系人，为你与 TA 的对话分流不同的话题场景。",
                icon = Icons.Default.People,
                statusText = "已同步 ${contacts.size} 位好友"
            )

            if (onStartRealtimeAssist != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartRealtimeAssist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SocialAidModernColors.Purple)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "启动该联系人的实时辅助",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            if (batchEditEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                BatchEditBar(
                    selectedCount = selectedContactIds.size,
                    totalCount = contacts.size,
                    onSelectAll = {
                        selectedContactIds = if (selectedContactIds.size == contacts.size) {
                            emptySet()
                        } else {
                            contacts.map { it.contact.id }.toSet()
                        }
                    },
                    onDelete = { showDeleteConfirm = true },
                    onDone = onBatchEditFinished
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无联系人，请点击右上角按钮添加", color = SocialAidModernColors.Muted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(contacts, key = { it.contact.id }) { item ->
                        val contactId = item.contact.id
                        ContactItemRow(
                            name = item.contact.displayName,
                            avatarPath = item.contact.avatarUri,
                            lastMessage = item.lastMessageText,
                            lastTime = dateFormat.format(Date(item.lastMessageTime)),
                            batchEditEnabled = batchEditEnabled,
                            selected = contactId in selectedContactIds,
                            onSelectedChange = {
                                selectedContactIds = if (contactId in selectedContactIds) {
                                    selectedContactIds - contactId
                                } else {
                                    selectedContactIds + contactId
                                }
                            },
                            onClick = {
                                viewModel.selectContact(contactId)
                                onContactClick(contactId)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("对话上下文", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加联系人")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            content(padding)
        }
    } else {
        content(PaddingValues(0.dp))
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
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
                            showAddDialog = false
                            newContactName = ""
                        }
                    },
                    enabled = newContactName.isNotBlank()
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    newContactName = ""
                }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除联系人") },
            text = { Text("将删除选中的 ${selectedContactIds.size} 位联系人及其所有会话消息。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteContacts(selectedContactIds.toList())
                        selectedContactIds = emptySet()
                        showDeleteConfirm = false
                        onBatchEditFinished()
                    },
                    enabled = selectedContactIds.isNotEmpty()
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
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
private fun BatchEditBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("已选 $selectedCount", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onSelectAll, enabled = totalCount > 0) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (selectedCount == totalCount && totalCount > 0) "取消全选" else "全选")
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onDone) {
                Icon(Icons.Default.Clear, contentDescription = "完成")
            }
        }
    }
}

@Composable
private fun ContactItemRow(
    name: String,
    avatarPath: String?,
    lastMessage: String,
    lastTime: String,
    batchEditEnabled: Boolean,
    selected: Boolean,
    onSelectedChange: () -> Unit,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (batchEditEnabled) onSelectedChange else onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (batchEditEnabled) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectedChange() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SocialAidModernColors.MintSoft),
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
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.SemiBold,
                        color = SocialAidModernColors.Ink,
                        fontSize = 15.sp
                    )
                    Text(
                        text = lastTime,
                        color = SocialAidModernColors.Muted,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastMessage,
                    color = SocialAidModernColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}
