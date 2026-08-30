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
import com.socialfirstaidkit.core.data.personalexpression.PersonalExpressionVersion
import com.socialfirstaidkit.core.ui.theme.GlassCard
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressionVersionHistoryScreen(
    onBackClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var versions by remember { mutableStateOf<List<PersonalExpressionVersion>>(emptyList()) }
    var showRestoreDialog by remember { mutableStateOf<PersonalExpressionVersion?>(null) }

    LaunchedEffect(Unit) {
        val repo = viewModel.expressionRepository
        repo.observeVersions().collect { versions = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("版本记录", fontWeight = FontWeight.Bold) },
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
        if (versions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无版本记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("共 ${versions.size} 个版本", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            versions.forEach { version ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth().clickable { showRestoreDialog = version }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("版本 ${version.version}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(version.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                        Text(dateFormat.format(Date(version.createdAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(version.profile.readableSummary(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
    }

    showRestoreDialog?.let { version ->
        AlertDialog(
            onDismissRequest = { showRestoreDialog = null },
            title = { Text("恢复到版本 ${version.version}") },
            text = { Text("确定要恢复到这个版本吗？当前版本会被保存为新版本记录。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreVersion(version.id)
                    showRestoreDialog = null
                    onBackClick()
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = null }) { Text("取消") }
            }
        )
    }
}
