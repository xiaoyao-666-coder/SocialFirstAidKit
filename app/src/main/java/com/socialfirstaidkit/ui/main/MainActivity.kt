package com.socialfirstaidkit.ui.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.socialfirstaidkit.core.data.local.SecureStorage
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import com.socialfirstaidkit.core.ui.theme.SocialAidPosterBackground
import com.socialfirstaidkit.feature.chatassist.ui.MessageAssistScreen
import com.socialfirstaidkit.feature.chatassist.ui.ChatSearchScreen
import com.socialfirstaidkit.feature.chatassist.ui.newarch.ContactSessionsScreen
import com.socialfirstaidkit.feature.chatassist.ui.newarch.SessionDetailScreen
import com.socialfirstaidkit.feature.chatassist.ui.newarch.NewChatViewModel
import android.widget.Toast
import com.socialfirstaidkit.service.accessibility.ChatAccessibilityService
import com.socialfirstaidkit.service.accessibility.RealtimeOverlayPrefs
import com.socialfirstaidkit.feature.socialcopy.ui.SocialCopyScreen
import com.socialfirstaidkit.feature.socialcopy.viewmodel.SocialCopyViewModel
import com.socialfirstaidkit.ui.components.UserAgreementDialog
import com.socialfirstaidkit.ui.navigation.Screen
import com.socialfirstaidkit.ui.profile.ProfileScreen
import com.socialfirstaidkit.ui.profile.ProfileViewModel
import com.socialfirstaidkit.ui.profile.ExpressionCenterScreen
import com.socialfirstaidkit.ui.profile.ExpressionQuestionnaireGuideScreen
import com.socialfirstaidkit.ui.profile.ExpressionQuestionnaireScreen
import com.socialfirstaidkit.ui.profile.ExpressionResultScreen
import com.socialfirstaidkit.ui.profile.ExpressionEditorScreen
import com.socialfirstaidkit.ui.profile.ExpressionUpdateProposalScreen
import com.socialfirstaidkit.ui.profile.ExpressionVersionHistoryScreen
import com.socialfirstaidkit.ui.settings.SettingsScreen
import com.socialfirstaidkit.ui.settings.ModelSettingsScreen
import com.socialfirstaidkit.ui.splash.SplashScreen
import com.socialfirstaidkit.ui.splash.SplashViewModel
import com.socialfirstaidkit.ui.theme.SocialFirstAidTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels()
    private val socialCopyViewModel: SocialCopyViewModel by viewModels()

    @Inject
    lateinit var secureStorage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureSystemBars()
        setContent {
            SocialFirstAidTheme {
                var showSplash by remember { mutableStateOf(true) }
                var showUserAgreement by remember { 
                    mutableStateOf(!secureStorage.getBoolean(KEY_USER_AGREEMENT_ACCEPTED, false))
                }

                when {
                    showSplash -> {
                        SplashScreen(
                            onSplashComplete = {
                                showSplash = false
                            },
                            viewModel = splashViewModel
                        )
                    }
                    showUserAgreement -> {
                        UserAgreementDialog(
                            onAgree = {
                                secureStorage.saveBoolean(KEY_USER_AGREEMENT_ACCEPTED, true)
                                showUserAgreement = false
                            },
                            onDisagree = {
                                finish()
                            }
                        )
                    }
                    else -> {
                        MainScreen(
                            socialCopyViewModel = socialCopyViewModel
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tryShowRealtimeAssistOnResume(this)
    }

    companion object {
        private const val KEY_USER_AGREEMENT_ACCEPTED = "user_agreement_accepted"
    }

    private fun configureSystemBars() {
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    socialCopyViewModel: SocialCopyViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var isModuleMainScreen by remember(currentRoute) { mutableStateOf(true) }
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val newChatViewModel: NewChatViewModel = hiltViewModel()

    val showBottomBar = currentRoute in Screen.items.map { it.route } && isModuleMainScreen

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                NavigationBar(
                    modifier = Modifier.height(64.dp + bottomPadding),
                    containerColor = SocialAidModernColors.Glass,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    Screen.items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.ChatAssist.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        SocialAidPosterBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.ChatAssist.route
                ) {
                    composable(Screen.ChatAssist.route) {
                        MessageAssistScreen(
                            viewModel = newChatViewModel,
                            onMainScreenStateChange = { isModuleMainScreen = it },
                            onExpressionCenterClick = {
                                navController.navigate(Screen.ExpressionCenter.route)
                            },
                            onNavigateToChatDetail = { sessionId ->
                                newChatViewModel.selectSession(sessionId)
                                navController.navigate(Screen.ChatDetail.createRoute(sessionId))
                            },
                            onNavigateToChatSearch = {
                                navController.navigate(Screen.ChatSearch.route)
                            },
                            onNavigateToContactSessions = { contactId ->
                                newChatViewModel.selectContact(contactId)
                                navController.navigate(Screen.ContactSessions.createRoute(contactId))
                            },
                            onStartRealtimeAssist = {}
                        )
                    }
                    composable(
                        route = Screen.ContactSessions.route,
                        arguments = listOf(navArgument("contactId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val contactId = backStackEntry.arguments?.getString("contactId") ?: ""
                        ContactSessionsScreen(
                            contactId = contactId,
                            onNavigateBack = { navController.popBackStack() },
                            onSessionClick = { sessionId ->
                                newChatViewModel.selectSession(sessionId)
                                navController.navigate(Screen.ChatDetail.createRoute(sessionId))
                            },
                            onStartRealtimeAssist = { selectedContactId ->
                                startRealtimeAssist(context, contactId = selectedContactId)
                            },
                            viewModel = newChatViewModel
                        )
                    }
                    composable(
                        route = Screen.ChatDetail.route,
                        arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                        SessionDetailScreen(
                            sessionId = sessionId,
                            onNavigateBack = { navController.popBackStack() },
                            onStartRealtimeAssist = { selectedContactId, selectedSessionId ->
                                startRealtimeAssist(
                                    context,
                                    contactId = selectedContactId,
                                    sessionId = selectedSessionId
                                )
                            },
                            viewModel = newChatViewModel
                        )
                    }
                    composable(Screen.ChatSearch.route) {
                        ChatSearchScreen(
                            viewModel = newChatViewModel,
                            onBackClick = { navController.popBackStack() },
                            onOpenSession = { sessionId ->
                                newChatViewModel.selectSession(sessionId)
                                navController.navigate(Screen.ChatDetail.createRoute(sessionId))
                            },
                            onOpenContact = { contactId ->
                                newChatViewModel.selectContact(contactId)
                                navController.navigate(Screen.ContactSessions.createRoute(contactId))
                            }
                        )
                    }
                    composable(Screen.SocialCopy.route) {
                        SocialCopyScreen(
                            viewModel = socialCopyViewModel,
                            onMainScreenStateChange = { isModuleMainScreen = it }
                        )
                    }
                    composable(Screen.Profile.route) {
                        ProfileScreen(
                            onMainScreenStateChange = { isModuleMainScreen = it },
                            onSettingsClick = {
                                navController.navigate(Screen.Settings.route) {
                                    launchSingleTop = true
                                }
                            },
                            onExpressionCenterClick = {
                                navController.navigate(Screen.ExpressionCenter.route)
                            },
                            viewModel = profileViewModel
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onBackClick = { navController.popBackStack() },
                            onModelSettingsClick = {
                                navController.navigate(Screen.ModelSettings.route) {
                                    launchSingleTop = true
                                }
                            },
                            messageAssistViewModel = newChatViewModel,
                            socialCopyViewModel = socialCopyViewModel
                        )
                    }
                    composable(Screen.ModelSettings.route) {
                        ModelSettingsScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.ExpressionCenter.route) {
                        ExpressionCenterScreen(
                            onBackClick = { navController.popBackStack() },
                            onStartQuestionnaire = {
                                navController.navigate(Screen.ExpressionQuestionnaireGuide.route)
                            },
                            onViewProfile = {
                                navController.navigate(Screen.ExpressionEditor.route)
                            },
                            onViewUpdateProposal = {
                                navController.navigate(Screen.ExpressionUpdateProposal.route)
                            },
                            onViewVersionHistory = {
                                navController.navigate(Screen.ExpressionVersionHistory.route)
                            },
                            viewModel = profileViewModel
                        )
                    }
                    composable(Screen.ExpressionQuestionnaireGuide.route) {
                        ExpressionQuestionnaireGuideScreen(
                            onBackClick = { navController.popBackStack() },
                            onStartQuestionnaire = {
                                navController.navigate(Screen.ExpressionQuestionnaire.route)
                            },
                            onUseDefault = {
                                profileViewModel.createDefaultProfile(enabled = true)
                                navController.navigate(Screen.ExpressionResult.route) {
                                    popUpTo(Screen.ExpressionQuestionnaireGuide.route) { inclusive = true }
                                }
                            },
                            viewModel = profileViewModel
                        )
                    }
                    composable(Screen.ExpressionQuestionnaire.route) {
                        ExpressionQuestionnaireScreen(
                            onBackClick = { navController.popBackStack() },
                            onComplete = { answers ->
                                profileViewModel.completeQuestionnaire(answers) {
                                    navController.navigate(Screen.ExpressionResult.route) {
                                        popUpTo(Screen.ExpressionQuestionnaireGuide.route) { inclusive = true }
                                    }
                                }
                            },
                            viewModel = profileViewModel
                        )
                    }
                    composable(Screen.ExpressionResult.route) {
                        ExpressionResultScreen(
                            onBackClick = { navController.popBackStack() },
                            onEnableAndDone = {
                                profileViewModel.setExpressionEnabled(true)
                                navController.popBackStack(Screen.ExpressionCenter.route, false)
                            },
                            onEditProfile = {
                                navController.navigate(Screen.ExpressionEditor.route) {
                                    popUpTo(Screen.ExpressionResult.route) { inclusive = true }
                                }
                            },
                            viewModel = profileViewModel
                        )
                    }
                    composable(Screen.ExpressionEditor.route) {
                        ExpressionEditorScreen(
                            onBackClick = { navController.popBackStack() },
                            viewModel = profileViewModel
                        )
                    }
                    composable(Screen.ExpressionUpdateProposal.route) {
                        ExpressionUpdateProposalScreen(
                            onBackClick = { navController.popBackStack() },
                            viewModel = profileViewModel
                        )
                    }
                    composable(Screen.ExpressionVersionHistory.route) {
                        ExpressionVersionHistoryScreen(
                            onBackClick = { navController.popBackStack() },
                            viewModel = profileViewModel
                        )
                    }
                }
            }
        }
    }
}

private fun startRealtimeAssist(
    context: android.content.Context,
    contactId: String? = null,
    sessionId: String? = null
) {
    RealtimeOverlayPrefs.setEnabled(context, true)
    RealtimeOverlayPrefs.setBoundContext(context, contactId, sessionId)

    when {
        !hasChatAccessibilityPermission(context) -> {
            Toast.makeText(
                context,
                "\u8bf7\u5148\u5f00\u542f\u65e0\u969c\u788d\u670d\u52a1\uff0c\u60ac\u6d6e\u7403\u9700\u8981\u5b83\u8bc6\u522b\u804a\u5929\u754c\u9762",
                Toast.LENGTH_LONG
            ).show()
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        else -> {
            val serviceConnected = ChatAccessibilityService.isServiceConnected()
            val shown = if (serviceConnected) {
                ChatAccessibilityService.showFloatingBallNow(contactId, sessionId)
            } else {
                false
            }
            if (!shown) {
                Toast.makeText(
                    context,
                    if (serviceConnected) {
                        "已请求显示悬浮球，请切回聊天界面查看"
                    } else {
                        "无障碍服务尚未连接，请关闭后重新开启本应用无障碍服务"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

private fun tryShowRealtimeAssistOnResume(context: android.content.Context) {
    if (!RealtimeOverlayPrefs.isEnabled(context) ||
        !RealtimeOverlayPrefs.isManualStartRequested(context) ||
        !hasChatAccessibilityPermission(context)
    ) {
        return
    }
    ChatAccessibilityService.showFloatingBallNow(
        contactId = RealtimeOverlayPrefs.getBoundContactId(context),
        sessionId = RealtimeOverlayPrefs.getBoundSessionId(context)
    )
}

private fun hasChatAccessibilityPermission(context: android.content.Context): Boolean {
    val accessibilityManager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
        as? AccessibilityManager
        ?: return false

    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    return enabledServices.any { service ->
        service.resolveInfo.serviceInfo.packageName == context.packageName &&
            service.resolveInfo.serviceInfo.name.endsWith(".ChatAccessibilityService")
    }
}


