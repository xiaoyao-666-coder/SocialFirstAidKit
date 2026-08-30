package com.socialfirstaidkit.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.socialfirstaidkit.core.llm.routing.AiCapability
import com.socialfirstaidkit.core.llm.routing.AiFunctionModule
import com.socialfirstaidkit.core.llm.routing.AiFunctionSlot
import com.socialfirstaidkit.core.llm.routing.AiModelRegistry
import com.socialfirstaidkit.core.llm.routing.AiModelRoute
import com.socialfirstaidkit.core.llm.routing.AiModelSpec
import com.socialfirstaidkit.core.llm.routing.AiProviderId
import com.socialfirstaidkit.core.llm.routing.CustomOpenAiProfile
import com.socialfirstaidkit.core.ui.theme.GlassCard
import com.socialfirstaidkit.core.ui.theme.HeroPanel
import com.socialfirstaidkit.core.ui.theme.SectionLabel
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors

private typealias ModelSettingsUiState = ModelSettingsViewModel.ModelSettingsUiState

private fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

    val appSettings = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    val fallbackSettings = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    runCatching {
        context.startActivity(appSettings)
    }.recoverCatching {
        context.startActivity(fallbackSettings)
    }
}

private enum class RouteStatusFilter(val label: String) {
    ALL("全部状态"),
    CUSTOM("已自定义"),
    DEFAULT("默认配置"),
    MISSING_KEY("缺少 Key"),
    INVALID("配置异常")
}

private val hiddenFunctionSlots = setOf(
    AiFunctionSlot.PROFILE_AVATAR_IMAGE_GENERATION
)

private fun visibleFunctionSlots(): List<AiFunctionSlot> =
    AiFunctionSlot.entries.filterNot { it in hiddenFunctionSlots }

private fun visibleFunctionModules(): List<AiFunctionModule> =
    visibleFunctionSlots()
        .map { it.module }
        .distinct()
        .sortedBy { it.order }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: ModelSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedSlot by remember { mutableStateOf<AiFunctionSlot?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    var vivoBaseUrl by remember { mutableStateOf(ModelSettingsViewModel.DEFAULT_VIVO_BASE_URL) }
    var vivoApiKey by remember { mutableStateOf("") }
    var vivoApiVisible by remember { mutableStateOf(false) }

    var customName by remember { mutableStateOf("") }
    var customBaseUrl by remember { mutableStateOf("") }
    var customApiKey by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }
    var customApiVisible by remember { mutableStateOf(false) }

    var wenxinKey by remember { mutableStateOf("") }
    var wenxinSecret by remember { mutableStateOf("") }
    var wenxinVisible by remember { mutableStateOf(false) }
    var lanxinAppId by remember { mutableStateOf("") }
    var lanxinAppKey by remember { mutableStateOf("") }
    var lanxinVisible by remember { mutableStateOf(false) }
    var tongyiKey by remember { mutableStateOf("") }
    var tongyiVisible by remember { mutableStateOf(false) }
    var geminiKey by remember { mutableStateOf("") }
    var geminiVisible by remember { mutableStateOf(false) }
    var mimoKey by remember { mutableStateOf("") }
    var mimoBaseUrl by remember { mutableStateOf(ModelSettingsViewModel.DEFAULT_MIMO_BASE_URL) }
    var mimoVisible by remember { mutableStateOf(false) }
    var agnesKey by remember { mutableStateOf("") }
    var agnesBaseUrl by remember { mutableStateOf(ModelSettingsViewModel.DEFAULT_AGNES_BASE_URL) }
    var agnesVisible by remember { mutableStateOf(false) }

    LaunchedEffect(
        uiState.vivoBaseUrl,
        uiState.vivoApiKey,
        uiState.customName,
        uiState.customBaseUrl,
        uiState.customApiKey,
        uiState.customModel,
        uiState.wenxinKey,
        uiState.wenxinSecret,
        uiState.lanxinAppId,
        uiState.lanxinAppKey,
        uiState.tongyiKey,
        uiState.geminiKey,
        uiState.mimoKey,
        uiState.mimoBaseUrl,
        uiState.agnesKey,
        uiState.agnesBaseUrl
    ) {
        vivoBaseUrl = uiState.vivoBaseUrl
        vivoApiKey = uiState.vivoApiKey
        customName = uiState.customName
        customBaseUrl = uiState.customBaseUrl
        customApiKey = uiState.customApiKey
        customModel = uiState.customModel
        wenxinKey = uiState.wenxinKey
        wenxinSecret = uiState.wenxinSecret
        lanxinAppId = uiState.lanxinAppId
        lanxinAppKey = uiState.lanxinAppKey
        tongyiKey = uiState.tongyiKey
        geminiKey = uiState.geminiKey
        mimoKey = uiState.mimoKey
        mimoBaseUrl = uiState.mimoBaseUrl
        agnesKey = uiState.agnesKey
        agnesBaseUrl = uiState.agnesBaseUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("大模型配置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets.statusBars,
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
                .navigationBarsPadding()
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "功能分配",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "服务商接入",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> FunctionAssignmentTab(
                    uiState = uiState,
                    onSlotClick = { selectedSlot = it },
                    onResetRoute = { slot ->
                        viewModel.resetRoute(slot)
                        Toast.makeText(context, "已恢复 ${slot.title} 的默认模型", Toast.LENGTH_SHORT).show()
                    },
                    onTestRoute = { slot ->
                        viewModel.testRoute(slot)
                    }
                )
                1 -> ServiceProviderTab(
                    vivoBaseUrl = vivoBaseUrl,
                    vivoApiKey = vivoApiKey,
                    vivoApiVisible = vivoApiVisible,
                    onVivoBaseUrlChange = { vivoBaseUrl = it },
                    onVivoApiKeyChange = { vivoApiKey = it },
                    onVivoApiVisibleChange = { vivoApiVisible = it },
                    customName = customName,
                    customBaseUrl = customBaseUrl,
                    customApiKey = customApiKey,
                    customModel = customModel,
                    customApiVisible = customApiVisible,
                    customProfiles = uiState.customProfiles,
                    onCustomNameChange = { customName = it },
                    onCustomBaseUrlChange = { customBaseUrl = it },
                    onCustomApiKeyChange = { customApiKey = it },
                    onCustomModelChange = { customModel = it },
                    onCustomApiVisibleChange = { customApiVisible = it },
                    wenxinKey = wenxinKey,
                    wenxinSecret = wenxinSecret,
                    wenxinVisible = wenxinVisible,
                    onWenxinKeyChange = { wenxinKey = it },
                    onWenxinSecretChange = { wenxinSecret = it },
                    onWenxinVisibleChange = { wenxinVisible = it },
                    lanxinAppId = lanxinAppId,
                    lanxinAppKey = lanxinAppKey,
                    lanxinVisible = lanxinVisible,
                    onLanxinAppIdChange = { lanxinAppId = it },
                    onLanxinAppKeyChange = { lanxinAppKey = it },
                    onLanxinVisibleChange = { lanxinVisible = it },
                    tongyiKey = tongyiKey,
                    tongyiVisible = tongyiVisible,
                    onTongyiKeyChange = { tongyiKey = it },
                    onTongyiVisibleChange = { tongyiVisible = it },
                    geminiKey = geminiKey,
                    geminiVisible = geminiVisible,
                    onGeminiKeyChange = { geminiKey = it },
                    onGeminiVisibleChange = { geminiVisible = it },
                    mimoKey = mimoKey,
                    mimoBaseUrl = mimoBaseUrl,
                    mimoVisible = mimoVisible,
                    onMimoKeyChange = { mimoKey = it },
                    onMimoBaseUrlChange = { mimoBaseUrl = it },
                    onMimoVisibleChange = { mimoVisible = it },
                    agnesKey = agnesKey,
                    agnesBaseUrl = agnesBaseUrl,
                    agnesVisible = agnesVisible,
                    onAgnesKeyChange = { agnesKey = it },
                    onAgnesBaseUrlChange = { agnesBaseUrl = it },
                    onAgnesVisibleChange = { agnesVisible = it },
                    onSaveVivoConfig = {
                        viewModel.saveVivoConfig(vivoBaseUrl, vivoApiKey)
                        Toast.makeText(context, "vivo 配置已保存", Toast.LENGTH_SHORT).show()
                    },
                    onAddCustomLlm = {
                        viewModel.addCustomLlm(customName, customBaseUrl, customApiKey, customModel)
                        Toast.makeText(context, "自定义 OpenAI 配置已新增", Toast.LENGTH_SHORT).show()
                    },
                    onUpdateCustomLlm = {
                        viewModel.updateCustomLlm(it, customName, customBaseUrl, customApiKey, customModel)
                        Toast.makeText(context, "自定义 OpenAI 配置已更新", Toast.LENGTH_SHORT).show()
                    },
                    onSaveStandardKeys = {
                        viewModel.saveStandardKeys(
                            wenxinKey = wenxinKey,
                            wenxinSecret = wenxinSecret,
                            lanxinAppId = lanxinAppId,
                            lanxinAppKey = lanxinAppKey,
                            tongyiKey = tongyiKey,
                            geminiKey = geminiKey
                        )
                        Toast.makeText(context, "官方服务商 Key 已保存", Toast.LENGTH_SHORT).show()
                    },
                    onSaveMimoConfig = {
                        viewModel.saveMimoConfig(mimoKey, mimoBaseUrl)
                        Toast.makeText(context, "MiMo 配置已保存", Toast.LENGTH_SHORT).show()
                    },
                    onSaveAgnesConfig = {
                        viewModel.saveAgnesConfig(agnesKey, agnesBaseUrl)
                        Toast.makeText(context, "Agnes 配置已保存", Toast.LENGTH_SHORT).show()
                    },
                    onTestVivoConfig = { viewModel.testVivoConfig(vivoBaseUrl, vivoApiKey) },
                    onTestCustomLlm = { name, baseUrl, apiKey, model -> viewModel.testCustomLlm(name, baseUrl, apiKey, model) },
                    onTestMimoConfig = { viewModel.testMimoConfig(mimoBaseUrl, mimoKey) },
                    onTestAgnesConfig = { viewModel.testAgnesConfig(agnesBaseUrl, agnesKey) },
                    onOpenVivoVisionProbe = {
                        context.startActivity(
                            android.content.Intent(context, VivoVisionProbeActivity::class.java)
                        )
                    }
                )
            }
        }
    }

    selectedSlot?.let { slot ->
        var selectedProvider by remember(slot) {
            mutableStateOf(uiState.routeFor(slot).provider)
        }
        ModelSelectionDialog(
            slot = slot,
            selectedProvider = selectedProvider,
            currentRoute = uiState.routes[slot],
            customLlmName = uiState.customName,
            customProfiles = uiState.customProfiles,
            onProviderChange = { selectedProvider = it },
            onModelSelected = { model ->
                val saved = viewModel.saveRoute(slot, model.modelId)
                Toast.makeText(
                    context,
                    if (saved) "已设置为 ${model.displayName}" else "该模型能力不匹配，未保存",
                    Toast.LENGTH_SHORT
                ).show()
                if (saved) selectedSlot = null
            },
            onDismiss = { selectedSlot = null }
        )
    }

    uiState.connectionTest?.let { result ->
        ConnectionTestDialog(
            result = result,
            onGrantAllFilesAccess = { openAllFilesAccessSettings(context) },
            onRetry = { viewModel.retryConnectionTest(result.testKey) },
            onDismiss = { viewModel.dismissConnectionTest() }
        )
    }
}

@Composable
private fun ConnectionTestDialog(
    result: ModelSettingsViewModel.ConnectionTestUiState,
    onGrantAllFilesAccess: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val statusText = when (result.status) {
        ModelSettingsViewModel.ConnectionTestStatus.RUNNING -> "测试中"
        ModelSettingsViewModel.ConnectionTestStatus.SUCCESS -> "连接成功"
        ModelSettingsViewModel.ConnectionTestStatus.FAILED -> "连接失败"
    }
    val statusColor = when (result.status) {
        ModelSettingsViewModel.ConnectionTestStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ModelSettingsViewModel.ConnectionTestStatus.SUCCESS -> SocialAidModernColors.Mint
        ModelSettingsViewModel.ConnectionTestStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    AlertDialog(
        onDismissRequest = {
            if (!result.isRunning) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusChip(statusText, statusColor)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(result.providerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("模型：${result.modelName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("能力：${result.capabilityLabel}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (result.elapsedMs != null) {
                            Text("耗时：${result.elapsedMs} ms", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (result.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(result.summary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Text("测试提示词：${result.prompt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (!result.imageUrl.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        AsyncImage(
                            model = result.imageUrl,
                            contentDescription = "测试生成图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = buildString {
                                if (result.isRunning) {
                                    appendLine("连接 API 中...")
                                }
                                if (result.responseText.isNotBlank()) {
                                    appendLine(result.responseText)
                                }
                                if (result.usageText.isNotBlank()) {
                                    appendLine()
                                    appendLine("usage: ${result.usageText}")
                                }
                                if (result.requestDebugText.isNotBlank()) {
                                    appendLine()
                                    appendLine("request:")
                                    appendLine(result.requestDebugText)
                                }
                                if (result.traceId.isNotBlank()) {
                                    appendLine("traceId: ${result.traceId}")
                                }
                            }.ifBlank { "等待响应..." },
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            color = Color(0xFFE8FFF7),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        dismissButton = {
            Row {
                if (result.requiresAllFilesAccess) {
                    TextButton(onClick = onGrantAllFilesAccess) {
                        Text("去授权")
                    }
                }
                if (!result.isRunning && result.testKey.startsWith("slot:")) {
                    TextButton(onClick = onRetry) {
                        Text("重新测试")
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FunctionAssignmentTab(
    uiState: ModelSettingsUiState,
    onSlotClick: (AiFunctionSlot) -> Unit,
    onResetRoute: (AiFunctionSlot) -> Unit,
    onTestRoute: (AiFunctionSlot) -> Unit
) {
    var selectedModule by remember { mutableStateOf<AiFunctionModule?>(null) }
    var selectedCapabilities by remember { mutableStateOf<Set<AiCapability>>(emptySet()) }
    var selectedStatuses by remember { mutableStateOf<Set<RouteStatusFilter>>(emptySet()) }
    var selectedProviders by remember { mutableStateOf<Set<AiProviderId>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }

    var showAdvancedFilter by remember { mutableStateOf(false) }

    val allSlots = visibleFunctionSlots().sortedWith(
        compareBy<AiFunctionSlot> { it.module.order }.thenBy { it.ordinal }
    )
    val availableModules = visibleFunctionModules()
    val filteredSlots = allSlots.filter { slot ->
        val route = uiState.routeFor(slot)
        val status = routeStatusFilter(uiState, slot, route)
        
        val matchesModule = selectedModule == null || slot.module == selectedModule
        val matchesCapability = selectedCapabilities.isEmpty() || slot.requiredCapability in selectedCapabilities
        val matchesStatus = selectedStatuses.isEmpty() || status in selectedStatuses
        val matchesProvider = selectedProviders.isEmpty() || route.provider in selectedProviders
        val matchesSearch = slotMatchesSearch(
            slot = slot,
            route = route,
            customLlmName = uiState.customName,
            customProfiles = uiState.customProfiles,
            query = searchQuery
        )

        matchesModule && matchesCapability && matchesStatus && matchesProvider && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {


        FunctionRouteFilterPanel(
            selectedModule = selectedModule,
            onModuleSelected = { selectedModule = it },
            availableModules = availableModules,
            selectedCapabilities = selectedCapabilities,
            onCapabilitiesSelected = { selectedCapabilities = it },
            selectedStatuses = selectedStatuses,
            onStatusesSelected = { selectedStatuses = it },
            selectedProviders = selectedProviders,
            onProvidersSelected = { selectedProviders = it },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onShowAdvancedFilter = { showAdvancedFilter = true },
            resultCount = filteredSlots.size,
            customLlmName = uiState.customName
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredSlots.isEmpty()) {
            EmptyFilterResultCard()
        } else {
            filteredSlots.groupBy { it.module }.toSortedMap(compareBy<AiFunctionModule> { it.order }).forEach { (module, slots) ->
                SectionHeader(module.title)
                slots.forEach { slot ->
                    val route = uiState.routeFor(slot)
                    FunctionModelCard(
                        slot = slot,
                        route = route,
                        customLlmName = uiState.customName,
            customProfiles = uiState.customProfiles,
                        isDefaultRoute = uiState.isEffectivelyDefaultRoute(slot),
                        isProviderConfigured = uiState.isProviderConfigured(route.provider),
                        onSelect = { onSlotClick(slot) },
                        onReset = { onResetRoute(slot) },
                        onTest = { onTestRoute(slot) }
                    )
                }
            }
        }
    }

    if (showAdvancedFilter) {
        AdvancedFilterBottomSheet(
            selectedModule = selectedModule,
            onModuleSelected = { selectedModule = it },
            availableModules = availableModules,
            selectedCapabilities = selectedCapabilities,
            onCapabilitiesSelected = { selectedCapabilities = it },
            selectedStatuses = selectedStatuses,
            onStatusesSelected = { selectedStatuses = it },
            selectedProviders = selectedProviders,
            onProvidersSelected = { selectedProviders = it },
            customLlmName = uiState.customName,
            onDismiss = { showAdvancedFilter = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FunctionRouteFilterPanel(
    selectedModule: AiFunctionModule?,
    onModuleSelected: (AiFunctionModule?) -> Unit,
    availableModules: List<AiFunctionModule>,
    selectedCapabilities: Set<AiCapability>,
    onCapabilitiesSelected: (Set<AiCapability>) -> Unit,
    selectedStatuses: Set<RouteStatusFilter>,
    onStatusesSelected: (Set<RouteStatusFilter>) -> Unit,
    selectedProviders: Set<AiProviderId>,
    onProvidersSelected: (Set<AiProviderId>) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onShowAdvancedFilter: () -> Unit,
    resultCount: Int,
    customLlmName: String
) {
    var moduleExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Search Bar & Item count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("搜索功能、模型或服务商", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            modifier = Modifier.size(16.dp),
                            tint = SocialAidModernColors.Muted
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(16.dp),
                                    tint = SocialAidModernColors.Muted
                                )
                            }
                        }
                    },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.5f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                        focusedBorderColor = SocialAidModernColors.Purple,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.82f)
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
                )
                Text(
                    "共 $resultCount 项",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Dropdowns and Filter button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dropdown 1: Module (replacing Capability)
                val moduleActive = selectedModule != null
                val moduleLabel = selectedModule?.title ?: "全部模块"
                Box(modifier = Modifier.weight(1f)) {
                    DropdownFilterButton(
                        text = moduleLabel,
                        active = moduleActive,
                        onClick = { moduleExpanded = true }
                    )
                    DropdownMenu(
                        expanded = moduleExpanded,
                        onDismissRequest = { moduleExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部模块", fontSize = 12.sp) },
                            onClick = {
                                onModuleSelected(null)
                                moduleExpanded = false
                            }
                        )
                        availableModules.forEach { module ->
                            DropdownMenuItem(
                                text = { Text(module.title, fontSize = 12.sp) },
                                onClick = {
                                    onModuleSelected(module)
                                    moduleExpanded = false
                                }
                            )
                        }
                    }
                }

                // Dropdown 2: Status
                val statusActive = selectedStatuses.isNotEmpty() && RouteStatusFilter.ALL !in selectedStatuses
                val statusLabel = when {
                    selectedStatuses.isEmpty() || RouteStatusFilter.ALL in selectedStatuses -> "全部状态"
                    selectedStatuses.size == 1 -> selectedStatuses.first().label
                    else -> "状态(${selectedStatuses.size})"
                }
                Box(modifier = Modifier.weight(1f)) {
                    DropdownFilterButton(
                        text = statusLabel,
                        active = statusActive,
                        onClick = { statusExpanded = true }
                    )
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部状态", fontSize = 12.sp) },
                            onClick = {
                                onStatusesSelected(emptySet())
                                statusExpanded = false
                            }
                        )
                        RouteStatusFilter.entries.filterNot { it == RouteStatusFilter.ALL }.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.label, fontSize = 12.sp) },
                                onClick = {
                                    onStatusesSelected(setOf(status))
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }

                // Dropdown 3: Provider
                val providerActive = selectedProviders.isNotEmpty()
                val providerLabel = when {
                    selectedProviders.isEmpty() -> "全部服务商"
                    selectedProviders.size == 1 -> providerDisplayName(selectedProviders.first(), customLlmName)
                    else -> "服务商(${selectedProviders.size})"
                }
                Box(modifier = Modifier.weight(1f)) {
                    DropdownFilterButton(
                        text = providerLabel,
                        active = providerActive,
                        onClick = { providerExpanded = true }
                    )
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部服务商", fontSize = 12.sp) },
                            onClick = {
                                onProvidersSelected(emptySet())
                                providerExpanded = false
                            }
                        )
                        AiProviderId.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(providerDisplayName(provider, customLlmName), fontSize = 12.sp) },
                                onClick = {
                                    onProvidersSelected(setOf(provider))
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                // Button 4: Advanced Filter
                val activeFiltersCount = (if (selectedModule != null) 1 else 0) +
                    selectedCapabilities.size +
                    selectedStatuses.size +
                    selectedProviders.size
                AdvancedFilterButton(
                    activeCount = activeFiltersCount,
                    onClick = onShowAdvancedFilter
                )
            }
        }
    }
}

@Composable
private fun DropdownFilterButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (active) SocialAidModernColors.Purple.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            if (active) SocialAidModernColors.Purple else Color.White.copy(alpha = 0.82f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) SocialAidModernColors.Purple else SocialAidModernColors.Muted,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (active) SocialAidModernColors.Purple else SocialAidModernColors.Muted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AdvancedFilterButton(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = activeCount > 0
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (active) SocialAidModernColors.Purple.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            if (active) SocialAidModernColors.Purple else Color.White.copy(alpha = 0.82f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = if (active) SocialAidModernColors.Purple else SocialAidModernColors.Muted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = if (active) "筛选($activeCount) ▽" else "筛选 ▽",
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) SocialAidModernColors.Purple else SocialAidModernColors.Muted,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFilterBottomSheet(
    selectedModule: AiFunctionModule?,
    onModuleSelected: (AiFunctionModule?) -> Unit,
    availableModules: List<AiFunctionModule>,
    selectedCapabilities: Set<AiCapability>,
    onCapabilitiesSelected: (Set<AiCapability>) -> Unit,
    selectedStatuses: Set<RouteStatusFilter>,
    onStatusesSelected: (Set<RouteStatusFilter>) -> Unit,
    selectedProviders: Set<AiProviderId>,
    onProvidersSelected: (Set<AiProviderId>) -> Unit,
    customLlmName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var tempModule by remember { mutableStateOf(selectedModule) }
    var tempCapabilities by remember { mutableStateOf(selectedCapabilities) }
    var tempStatuses by remember { mutableStateOf(selectedStatuses) }
    var tempProviders by remember { mutableStateOf(selectedProviders) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "高级筛选",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = {
                    tempModule = null
                    tempCapabilities = emptySet()
                    tempStatuses = emptySet()
                    tempProviders = emptySet()
                }) {
                    Text("重置", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: 所属模块
            Text("所属模块", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsFilterChip(
                    text = "全部模块",
                    selected = tempModule == null,
                    onClick = { tempModule = null }
                )
                availableModules.forEach { module ->
                    SettingsFilterChip(
                        text = module.title,
                        selected = tempModule == module,
                        onClick = { tempModule = module }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: 所需能力
            Text("所需能力", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                visibleFunctionSlots()
                    .map { it.requiredCapability }
                    .distinct()
                    .forEach { capability ->
                        val isSelected = capability in tempCapabilities
                        SettingsFilterChip(
                            text = capability.label,
                            selected = isSelected,
                            onClick = {
                                tempCapabilities = if (isSelected) {
                                    tempCapabilities - capability
                                } else {
                                    tempCapabilities + capability
                                }
                            }
                        )
                    }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: 配置状态
            Text("配置状态", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RouteStatusFilter.entries.filterNot { it == RouteStatusFilter.ALL }.forEach { status ->
                    val isSelected = status in tempStatuses
                    SettingsFilterChip(
                        text = status.label,
                        selected = isSelected,
                        onClick = {
                            tempStatuses = if (isSelected) {
                                tempStatuses - status
                            } else {
                                tempStatuses + status
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: 服务商
            Text("服务商", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AiProviderId.entries.forEach { provider ->
                    val isSelected = provider in tempProviders
                    val displayName = providerDisplayName(provider, customLlmName)
                    SettingsFilterChip(
                        text = displayName,
                        selected = isSelected,
                        onClick = {
                            tempProviders = if (isSelected) {
                                tempProviders - provider
                            } else {
                                tempProviders + provider
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Confirm Button
            Button(
                onClick = {
                    onModuleSelected(tempModule)
                    onCapabilitiesSelected(tempCapabilities)
                    onStatusesSelected(tempStatuses)
                    onProvidersSelected(tempProviders)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text("确定", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 12.sp, maxLines = 1) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.72f),
            labelColor = SocialAidModernColors.Muted,
            selectedContainerColor = SocialAidModernColors.Purple,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun EmptyFilterResultCard() {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 8.dp
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("没有匹配的功能配置", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "调整模块、能力、状态或搜索关键词后再试。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FunctionModelCard(
    slot: AiFunctionSlot,
    route: AiModelRoute,
    customLlmName: String,
    customProfiles: List<CustomOpenAiProfile>,
    isDefaultRoute: Boolean,
    isProviderConfigured: Boolean,
    onSelect: () -> Unit,
    onReset: () -> Unit,
    onTest: () -> Unit
) {
    val customSpec = customProfiles
        .firstOrNull { AiModelRegistry.customOpenAiSpec(it).modelId == route.modelId }
        ?.let { AiModelRegistry.customOpenAiSpec(it) }
    val spec = AiModelRegistry.findModel(route.modelId) ?: customSpec
    val compatible = AiModelRegistry.isRouteCompatible(slot, route)
    val status = when {
        !compatible -> "能力不匹配"
        !isProviderConfigured -> "Key 未配置"
        isDefaultRoute -> "默认"
        else -> "已自定义"
    }
    val statusColor = when (status) {
        "能力不匹配" -> MaterialTheme.colorScheme.error
        "Key 未配置" -> MaterialTheme.colorScheme.tertiary
        "已自定义" -> MaterialTheme.colorScheme.primary
        else -> SocialAidModernColors.Mint
    }
    val providerName = providerDisplayName(route.provider, customLlmName)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SocialAidModernColors.Glass),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(slot.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        slot.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(text = status, color = statusColor)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("服务商：$providerName", fontSize = 12.sp)
            Text("模型/引擎：${route.modelName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CapabilityChip(slot.requiredCapability.label)
                spec?.capabilities.orEmpty()
                    .filterNot { it == slot.requiredCapability }
                    .take(3)
                    .forEach { CapabilityChip(it.label) }
            }

            if (!compatible) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "当前模型不支持 ${slot.requiredCapability.label}，保存时会自动回退到 vivo 默认能力。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onTest) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("测试")
                }
                TextButton(onClick = onReset, enabled = !isDefaultRoute) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("恢复默认")
                }
                Button(onClick = onSelect) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("选择模型")
                }
            }
        }
    }
}

@Composable
private fun ServiceProviderTab(
    vivoBaseUrl: String,
    vivoApiKey: String,
    vivoApiVisible: Boolean,
    onVivoBaseUrlChange: (String) -> Unit,
    onVivoApiKeyChange: (String) -> Unit,
    onVivoApiVisibleChange: (Boolean) -> Unit,
    customName: String,
    customBaseUrl: String,
    customApiKey: String,
    customModel: String,
    customApiVisible: Boolean,
    customProfiles: List<CustomOpenAiProfile>,
    onCustomNameChange: (String) -> Unit,
    onCustomBaseUrlChange: (String) -> Unit,
    onCustomApiKeyChange: (String) -> Unit,
    onCustomModelChange: (String) -> Unit,
    onCustomApiVisibleChange: (Boolean) -> Unit,
    wenxinKey: String,
    wenxinSecret: String,
    wenxinVisible: Boolean,
    onWenxinKeyChange: (String) -> Unit,
    onWenxinSecretChange: (String) -> Unit,
    onWenxinVisibleChange: (Boolean) -> Unit,
    lanxinAppId: String,
    lanxinAppKey: String,
    lanxinVisible: Boolean,
    onLanxinAppIdChange: (String) -> Unit,
    onLanxinAppKeyChange: (String) -> Unit,
    onLanxinVisibleChange: (Boolean) -> Unit,
    tongyiKey: String,
    tongyiVisible: Boolean,
    onTongyiKeyChange: (String) -> Unit,
    onTongyiVisibleChange: (Boolean) -> Unit,
    geminiKey: String,
    geminiVisible: Boolean,
    onGeminiKeyChange: (String) -> Unit,
    onGeminiVisibleChange: (Boolean) -> Unit,
    mimoKey: String,
    mimoBaseUrl: String,
    mimoVisible: Boolean,
    onMimoKeyChange: (String) -> Unit,
    onMimoBaseUrlChange: (String) -> Unit,
    onMimoVisibleChange: (Boolean) -> Unit,
    agnesKey: String,
    agnesBaseUrl: String,
    agnesVisible: Boolean,
    onAgnesKeyChange: (String) -> Unit,
    onAgnesBaseUrlChange: (String) -> Unit,
    onAgnesVisibleChange: (Boolean) -> Unit,
    onSaveVivoConfig: () -> Unit,
    onAddCustomLlm: () -> Unit,
    onUpdateCustomLlm: (String) -> Unit,
    onSaveStandardKeys: () -> Unit,
    onSaveMimoConfig: () -> Unit,
    onSaveAgnesConfig: () -> Unit,
    onTestVivoConfig: () -> Unit,
    onTestCustomLlm: (String, String, String, String) -> Unit,
    onTestMimoConfig: () -> Unit,
    onTestAgnesConfig: () -> Unit,
    onOpenVivoVisionProbe: () -> Unit
) {
    var selectedCustomProfileId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        HeroPanel(
            title = "服务商接入",
            subtitle = "管理自定义 OpenAI 兼容服务，可接入自建网关、DeepSeek、Kimi 或其它兼容接口。",
            icon = Icons.Default.Key,
            statusText = "统一入口",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader("自定义 OpenAI 兼容服务")
        GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 8.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "适合接入自建网关、DeepSeek、Kimi 或其它 OpenAI-compatible 服务。每个配置会以独立卡片保存，并在功能分配中作为单独模型选择。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("已保存配置", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Key 默认隐藏，点击卡片编辑时可更新", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = {
                            selectedCustomProfileId = null
                            onCustomNameChange("")
                            onCustomBaseUrlChange("")
                            onCustomApiKeyChange("")
                            onCustomModelChange("")
                            onCustomApiVisibleChange(false)
                        }
                    ) {
                        Text("新增配置")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (customProfiles.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    ) {
                        Text(
                            "还没有自定义 OpenAI 兼容服务，先在下方填写后保存。",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customProfiles.forEach { profile ->
                            CustomOpenAiProfileCard(
                                profile = profile,
                                selected = selectedCustomProfileId == profile.id,
                                onEdit = {
                                    selectedCustomProfileId = profile.id
                                    onCustomNameChange(profile.name)
                                    onCustomBaseUrlChange(profile.baseUrl)
                                    onCustomApiKeyChange(profile.apiKey)
                                    onCustomModelChange(profile.model)
                                    onCustomApiVisibleChange(false)
                                },
                                onTest = {
                                    selectedCustomProfileId = profile.id
                                    onCustomNameChange(profile.name)
                                    onCustomBaseUrlChange(profile.baseUrl)
                                    onCustomApiKeyChange(profile.apiKey)
                                    onCustomModelChange(profile.model)
                                    onCustomApiVisibleChange(false)
                                    onTestCustomLlm(profile.name, profile.baseUrl, profile.apiKey, profile.model)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    if (selectedCustomProfileId == null) "新增自定义服务" else "编辑自定义服务",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customName,
                    onValueChange = onCustomNameChange,
                    label = { Text("服务商名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customBaseUrl,
                    onValueChange = onCustomBaseUrlChange,
                    label = { Text("Base URL") },
                    placeholder = { Text("例如：https://api.deepseek.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                ProviderStatusLine(
                    configured = customBaseUrl.isNotBlank(),
                    text = "API 类型：" + customOpenAiApiTypeLabel(customBaseUrl, customModel)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SecretTextField(
                    value = customApiKey,
                    onValueChange = onCustomApiKeyChange,
                    label = "API Key",
                    visible = customApiVisible,
                    onVisibleChange = onCustomApiVisibleChange
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customModel,
                    onValueChange = onCustomModelChange,
                    label = { Text("默认模型名称") },
                    placeholder = { Text("例如：deepseek-chat") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { onTestCustomLlm(customName, customBaseUrl, customApiKey, customModel) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("连接测试")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val saved = customBaseUrl.isNotBlank() && customModel.isNotBlank()
                        val editingProfileId = selectedCustomProfileId
                        if (editingProfileId == null) {
                            onAddCustomLlm()
                        } else {
                            onUpdateCustomLlm(editingProfileId)
                        }
                        if (saved) {
                            selectedCustomProfileId = null
                            onCustomNameChange("")
                            onCustomBaseUrlChange("")
                            onCustomApiKeyChange("")
                            onCustomModelChange("")
                            onCustomApiVisibleChange(false)
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedCustomProfileId == null) "保存为新配置" else "保存修改")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomOpenAiProfileCard(
    profile: CustomOpenAiProfile,
    selected: Boolean,
    onEdit: () -> Unit,
    onTest: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.66f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.86f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(profile.model, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                StatusChip("API：" + customOpenAiApiTypeLabel(profile.baseUrl, profile.model), SocialAidModernColors.Purple)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Base URL：" + profile.baseUrl,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Text(
                if (profile.apiKey.isBlank()) "API Key：未填写" else "API Key：已保存（隐藏）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTest) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("测试")
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
            }
        }
    }
}

private fun customOpenAiApiTypeLabel(baseUrl: String, modelName: String = ""): String {
    if (modelName.isNotBlank()) {
        when (AiModelRegistry.primaryCustomOpenAiCapability(modelName)) {
            AiCapability.IMAGE_GENERATION -> return "OpenAI 图片生成"
            AiCapability.VIDEO_GENERATION -> return "OpenAI 视频生成"
            else -> Unit
        }
    }
    return baseOpenAiApiTypeLabel(baseUrl)
}

private fun baseOpenAiApiTypeLabel(baseUrl: String): String {
    val normalized = baseUrl.trim().lowercase()
    return when {
        normalized.contains("/responses") -> "OpenAI Responses"
        normalized.contains("/images/generations") -> "OpenAI Images"
        normalized.contains("/chat/completions") -> "OpenAI Chat Completions"
        normalized.contains("openai") -> "OpenAI Chat Completions"
        normalized.contains("deepseek") -> "DeepSeek OpenAI-Compatible"
        normalized.contains("moonshot") || normalized.contains("kimi") -> "Kimi OpenAI-Compatible"
        normalized.contains("siliconflow") -> "SiliconFlow OpenAI-Compatible"
        normalized.contains("openrouter") -> "OpenRouter OpenAI-Compatible"
        normalized.isNotBlank() -> "OpenAI-Compatible"
        else -> "待识别"
    }
}
@Composable
private fun ModelSelectionDialog(
    slot: AiFunctionSlot,
    selectedProvider: AiProviderId,
    customLlmName: String,
    customProfiles: List<CustomOpenAiProfile>,
    currentRoute: AiModelRoute?,
    onProviderChange: (AiProviderId) -> Unit,
    onModelSelected: (AiModelSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val customOpenAiModels = remember(customProfiles) {
        customProfiles.map { AiModelRegistry.customOpenAiSpec(it) }
    }
    val activeProviders = remember(slot, customOpenAiModels) {
        AiProviderId.entries.filter { provider ->
            val providerModels = selectableModelsForProvider(provider) +
                if (provider == AiProviderId.CUSTOM_OPENAI) customOpenAiModels else emptyList()
            if (provider == AiProviderId.CUSTOM_OPENAI) {
                customOpenAiModels.isNotEmpty()
            } else {
                providerModels.any { AiModelRegistry.isSlotAndModelCompatible(slot, it) }
            }
        }
    }

    LaunchedEffect(slot, activeProviders, selectedProvider) {
        if (selectedProvider !in activeProviders && activeProviders.isNotEmpty()) {
            onProviderChange(activeProviders.first())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 ${slot.title} 模型") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "所需能力：${slot.requiredCapability.label}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("服务商", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                activeProviders.chunked(2).forEach { rowProviders ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowProviders.forEach { provider ->
                            ProviderChoiceChip(
                                label = providerDisplayName(provider, customLlmName),
                                selected = provider == selectedProvider,
                                onClick = { onProviderChange(provider) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowProviders.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Text("模型/能力引擎", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val models = (selectableModelsForProvider(selectedProvider) +
                    if (selectedProvider == AiProviderId.CUSTOM_OPENAI) customOpenAiModels else emptyList())
                    .sortedWith(compareByDescending<AiModelSpec> { it.recommended }.thenBy { it.displayName })
                models.forEach { model ->
                    val compatible = AiModelRegistry.isSlotAndModelCompatible(slot, model)
                    ModelChoiceItem(
                        model = model,
                        isSelected = currentRoute?.modelId == model.modelId,
                        compatible = compatible,
                        requiredCapability = slot.requiredCapability,
                        onClick = { if (compatible) onModelSelected(model) }
                    )
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

private fun selectableModelsForProvider(provider: AiProviderId): List<AiModelSpec> {
    return AiModelRegistry.modelsForProvider(provider)
        .filterNot { it.modelId == AiModelRegistry.CUSTOM_OPENAI_TEXT_ID }
}

@Composable
private fun ModelChoiceItem(
    model: AiModelSpec,
    isSelected: Boolean,
    compatible: Boolean,
    requiredCapability: AiCapability,
    onClick: () -> Unit
) {
    val contentColor = if (compatible) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compatible) Modifier.clickable(onClick = onClick) else Modifier),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            when {
                isSelected -> MaterialTheme.colorScheme.primary
                compatible -> MaterialTheme.colorScheme.outlineVariant
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(model.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = contentColor)
                        if (model.recommended) {
                            StatusChip("推荐", SocialAidModernColors.Mint)
                        }
                    }
                    Text(model.modelName, fontSize = 11.sp, color = contentColor.copy(alpha = 0.75f))
                }
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.capabilities.take(4).forEach { CapabilityChip(it.label) }
            }
            if (model.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(model.note, fontSize = 11.sp, color = contentColor.copy(alpha = 0.75f))
            }
            if (!compatible) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "不支持 ${requiredCapability.label}，不可用于当前功能。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ProviderChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { onVisibleChange(!visible) }) {
                Icon(
                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (visible) "隐藏" else "显示"
                )
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    SectionLabel(
        title = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ProviderTitle(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ProviderSpacer() {
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun ProviderStatusLine(configured: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (configured) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (configured) SocialAidModernColors.Mint else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CapabilityChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun providerDisplayName(provider: AiProviderId, customLlmName: String): String {
    return if (provider == AiProviderId.CUSTOM_OPENAI && customLlmName.isNotBlank()) {
        customLlmName
    } else {
        provider.displayName
    }
}

private fun ModelSettingsUiState.routeFor(slot: AiFunctionSlot): AiModelRoute {
    return routes[slot] ?: defaultRoutes[slot] ?: AiModelRegistry.defaultRoute(slot)
}

private fun ModelSettingsUiState.isEffectivelyDefaultRoute(slot: AiFunctionSlot): Boolean {
    return routes[slot] == null || routes[slot] == defaultRoutes[slot]
}

private fun routeStatusFilter(
    uiState: ModelSettingsUiState,
    slot: AiFunctionSlot,
    route: AiModelRoute
): RouteStatusFilter {
    val compatible = AiModelRegistry.isRouteCompatible(slot, route)
    val keyReady = uiState.isProviderConfigured(route.provider)
    return when {
        !compatible -> RouteStatusFilter.INVALID
        !keyReady -> RouteStatusFilter.MISSING_KEY
        uiState.isEffectivelyDefaultRoute(slot) -> RouteStatusFilter.DEFAULT
        else -> RouteStatusFilter.CUSTOM
    }
}

private fun slotMatchesSearch(
    slot: AiFunctionSlot,
    route: AiModelRoute,
    customLlmName: String,
    customProfiles: List<CustomOpenAiProfile>,
    query: String
): Boolean {
    val keyword = query.trim()
    if (keyword.isBlank()) return true
    val customSpec = customProfiles
        .firstOrNull { AiModelRegistry.customOpenAiSpec(it).modelId == route.modelId }
        ?.let { AiModelRegistry.customOpenAiSpec(it) }
    val spec = AiModelRegistry.findModel(route.modelId) ?: customSpec
    val searchableText = listOfNotNull(
        slot.title,
        slot.description,
        slot.module.title,
        slot.requiredCapability.label,
        route.modelName,
        providerDisplayName(route.provider, customLlmName),
        spec?.displayName,
        spec?.note
    ).joinToString(" ")
    return searchableText.contains(keyword, ignoreCase = true)
}
