package com.socialfirstaidkit.feature.chatassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialfirstaidkit.feature.chatassist.viewmodel.ReplyMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageAssistTopBar(
    currentMode: ReplyMode,
    onModeChange: (ReplyMode) -> Unit,
    onSearchClick: () -> Unit,
    onAddChatClick: () -> Unit,
    onBatchEditClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    var addMenuExpanded by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modes = listOf(
                    ReplyMode.SINGLE_TEXT to "单条回复",
                    ReplyMode.CONTEXTUAL to "对话上下文"
                )
                modes.forEach { (mode, label) ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(17.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surface
                                else Color.Transparent
                            )
                            .clickable { onModeChange(mode) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        actions = {
            if (currentMode == ReplyMode.CONTEXTUAL) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "搜索会话")
                }
                Box {
                    IconButton(onClick = { addMenuExpanded = true }) {
                        Icon(Icons.Default.Add, contentDescription = "更多功能")
                    }
                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("新增联系人") },
                            onClick = {
                                onAddChatClick()
                                addMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("批量编辑") },
                            onClick = {
                                onBatchEditClick()
                                addMenuExpanded = false
                            }
                        )
                    }
                }
            } else if (currentMode == ReplyMode.SINGLE_TEXT) {
                IconButton(onClick = onHistoryClick) {
                    Icon(Icons.Default.History, contentDescription = "历史记录")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
