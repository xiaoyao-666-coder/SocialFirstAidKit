package com.socialfirstaidkit.service.accessibility.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class NewFloatingBall(
    private val context: Context,
    private val onPrimaryActionClick: (String?) -> Unit,
    private val onManualScreenshotClick: () -> Unit,
    private val onCloseClick: () -> Unit,
    private val onTuneConfirm: (String) -> Unit,
    private val onSuggestionCopied: (String) -> Unit
) {
    private companion object {
        private const val TAG = "NewFloatingBall"
        private const val DEBUG_VERSION = "edge-radial-v1"
        private const val BALL_DP = 56
        private const val ACTION_DP = 44
        private const val MENU_WIDTH_DP = 168
        private const val MENU_HEIGHT_DP = 220
        private const val RADIAL_RADIUS_DP = 90
        private const val EDGE_VISUAL_OFFSET_DP = 24
        private const val PANEL_MARGIN_DP = 12
        private const val STATUS_WIDTH_DP = 168
        private const val STATUS_HEIGHT_DP = 104
        private const val SUGGESTION_WIDTH_DP = 276
        private const val SUGGESTION_HEIGHT_DP = 190
        private const val RENAME_PROMPT_WIDTH_DP = 276
        private const val RENAME_PROMPT_HEIGHT_DP = 146
        private const val TUNE_PANEL_WIDTH_DP = 236
        private const val TUNE_PANEL_HEIGHT_DP = 214
        private const val IDLE_COLLAPSE_MS = 10_000L
    }


    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun hasApplicationOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            android.provider.Settings.canDrawOverlays(context.applicationContext)
    }

    private fun overlayWindowType(): Int {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasApplicationOverlayPermission() -> {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            context is AccessibilityService && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            else -> {
                WindowManager.LayoutParams.TYPE_PHONE
            }
        }
    }
    private var rootLayout: FrameLayout? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = context.resources.displayMetrics.widthPixels - (56 * context.resources.displayMetrics.density).roundToInt()
        y = (context.resources.displayMetrics.heightPixels * 0.68f).roundToInt()
    }

    private var isFocused = false
    private val isGeneratingState = mutableStateOf(false)
    private val suggestionsState = mutableStateListOf<String>()
    private val currentSuggestionIdx = mutableStateOf(0)
    private val isShowingCardState = mutableStateOf(false)
    private val statusMessageState = mutableStateOf("")
    private val statusMessageShownAt = mutableStateOf(0L)
    private val renamePromptState = mutableStateOf<RenamePrompt?>(null)
    private val notificationHintState = mutableStateOf(false)
    private val edgeCollapsedState = mutableStateOf(true)
    private val collapsedOnRightState = mutableStateOf(true)
    private val menuExpandedState = mutableStateOf(false)
    private val showTuneInputState = mutableStateOf(false)
    private val tuneTextState = mutableStateOf("")
    private val lastActiveTimeState = mutableStateOf(System.currentTimeMillis())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var trackingDispatchTouch = false
    private var dispatchDragging = false
    private var dragInProgress = false
    private var dispatchStartRawX = 0f
    private var dispatchStartRawY = 0f
    private var dispatchStartWindowX = 0
    private var dispatchStartWindowY = 0
    private var lastPrimaryActionAt = 0L
    private var pendingMoveX = params.x
    private var pendingMoveY = params.y
    private var moveFramePosted = false

    fun isVisible(): Boolean {
        return rootLayout != null
    }

    fun show() {
        val existingRoot = rootLayout
        if (existingRoot != null) {
            Log.d(TAG, "show ignored version=$DEBUG_VERSION attached=${existingRoot.isAttachedToWindow} w=${existingRoot.width}, h=${existingRoot.height}, x=${params.x}, y=${params.y}, type=${params.type}, appOverlayPermission=${hasApplicationOverlayPermission()}, root=${existingRoot::class.java.simpleName}")
            existingRoot.post { Log.d(TAG, "show ignored post version=$DEBUG_VERSION attached=${existingRoot.isAttachedToWindow} w=${existingRoot.width}, h=${existingRoot.height}, x=${params.x}, y=${params.y}, type=${params.type}") }
            return
        }
        Log.d(TAG, "show start version=$DEBUG_VERSION context=${context::class.java.simpleName} type=${params.type} appOverlayPermission=${hasApplicationOverlayPermission()}")

        lifecycleOwner = OverlayLifecycleOwner().apply {
            onCreate()
            onStart()
            onResume()
        }

        rootLayout = TouchInterceptFrameLayout(context) { view, event ->
            handleDispatchTouch(view, event)
        }.also { root ->
            lifecycleOwner?.attachToView(root)
        }
        composeView = ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    FloatingBallContent()
                }
            }
        }

        lifecycleOwner?.attachToView(composeView!!)
        rootLayout?.addView(composeView)
        runCatching {
            windowManager.addView(rootLayout, params)
        }.onSuccess {
            rootLayout?.post { Log.d(TAG, "floating ball measured version=$DEBUG_VERSION w=${rootLayout?.width}, h=${rootLayout?.height}, x=${params.x}, y=${params.y}") }
            Log.d(TAG, "floating ball added version=$DEBUG_VERSION type=${params.type}, x=${params.x}, y=${params.y}, root=${rootLayout?.javaClass?.simpleName}")
        }.onFailure { error ->
            Log.e(TAG, "failed to add floating ball type=${params.type}", error)
            rootLayout = null
            composeView = null
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
        }
    }

    fun triggerPrimaryActionFromAccessibility() {
        Log.d(TAG, "accessibility fallback trigger version=$DEBUG_VERSION")
        handleBallTap("accessibility")
    }
    fun hide() {
        rootLayout?.let {
            runCatching { windowManager.removeView(it) }
                .onFailure { error -> Log.w(TAG, "failed to remove floating ball", error) }
        }
        rootLayout = null
        composeView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    fun showStatus(message: String) {
        lastActiveTimeState.value = System.currentTimeMillis()
        val ballX = currentBallScreenX()
        statusMessageShownAt.value = System.currentTimeMillis()
        statusMessageState.value = message
        renamePromptState.value = null
        isShowingCardState.value = false
        isGeneratingState.value = false
        positionWideContentKeepingBallFixed(ballX, STATUS_WIDTH_DP)
    }

    fun showSuggestions(suggestions: List<String>) {
        lastActiveTimeState.value = System.currentTimeMillis()
        val ballX = currentBallScreenX()
        statusMessageShownAt.value = System.currentTimeMillis()
        suggestionsState.clear()
        suggestionsState.addAll(suggestions)
        currentSuggestionIdx.value = 0
        renamePromptState.value = null
        isShowingCardState.value = suggestions.isNotEmpty()
        isGeneratingState.value = false
        notificationHintState.value = false
        if (suggestions.isEmpty()) {
            statusMessageState.value = "\u8fd8\u6ca1\u6709\u751f\u6210\u53ef\u7528\u5efa\u8bae"
            positionWideContentKeepingBallFixed(ballX, STATUS_WIDTH_DP)
        } else {
            statusMessageState.value = ""
            positionWideContentKeepingBallFixed(ballX, SUGGESTION_WIDTH_DP)
        }
    }

    fun showRenamePrompt(
        currentName: String,
        suggestedName: String,
        completionMessage: String,
        onConfirm: () -> Unit
    ) {
        lastActiveTimeState.value = System.currentTimeMillis()
        val ballX = currentBallScreenX()
        renamePromptState.value = RenamePrompt(currentName, suggestedName, completionMessage, onConfirm)
        statusMessageState.value = ""
        isShowingCardState.value = false
        isGeneratingState.value = false
        notificationHintState.value = false
        showTuneInputState.value = false
        menuExpandedState.value = false
        edgeCollapsedState.value = false
        positionWideContentKeepingBallFixed(ballX, RENAME_PROMPT_WIDTH_DP)
        updateFocus(false)
    }

    fun showLoading(message: String = "\u6b63\u5728\u751f\u6210\u5efa\u8bae...") {
        lastActiveTimeState.value = System.currentTimeMillis()
        val ballX = currentBallScreenX()
        statusMessageShownAt.value = System.currentTimeMillis()
        statusMessageState.value = message
        isGeneratingState.value = true
        isShowingCardState.value = false
        renamePromptState.value = null
        notificationHintState.value = false
        positionWideContentKeepingBallFixed(ballX, STATUS_WIDTH_DP)
    }

    fun pulseForNotification(message: String = "\u6709\u65b0\u804a\u5929\u6d88\u606f") {
        lastActiveTimeState.value = System.currentTimeMillis()
        statusMessageShownAt.value = System.currentTimeMillis()
        statusMessageState.value = message
        renamePromptState.value = null
        notificationHintState.value = true
        if (!isGeneratingState.value) {
            isShowingCardState.value = false
        }
    }

    fun onChatVisiblePing() {
        clearNotificationPulse()
        val statusAge = System.currentTimeMillis() - statusMessageShownAt.value
        if (!isGeneratingState.value && !isShowingCardState.value && renamePromptState.value == null && !showTuneInputState.value && (statusMessageState.value.isBlank() || statusAge > 2500L)) {
            statusMessageState.value = ""
        }
    }

    fun clearNotificationPulse() {
        notificationHintState.value = false
    }

    fun hasOverlayPermission(): Boolean {
        return when {
            params.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY -> hasApplicationOverlayPermission()
            context is AccessibilityService && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> true
            else -> hasApplicationOverlayPermission()
        }
    }

    private fun updateFocus(focused: Boolean) {
        if (isFocused == focused) return
        isFocused = focused
        val layout = rootLayout ?: return
        params.flags = if (focused) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(layout, params)
    }

    private fun moveTo(x: Int, y: Int) {
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val maxX = (displayMetrics.widthPixels - ballPx).coerceAtLeast(0)
        val maxY = (displayMetrics.heightPixels - ballPx).coerceAtLeast(0)
        val nextX = x.coerceIn(0, maxX)
        val nextY = y.coerceIn(0, maxY)
        if (pendingMoveX == nextX && pendingMoveY == nextY && moveFramePosted) return
        if (params.x == nextX && params.y == nextY && !moveFramePosted) return

        pendingMoveX = nextX
        pendingMoveY = nextY
        if (moveFramePosted) return
        moveFramePosted = true
        Choreographer.getInstance().postFrameCallback {
            moveFramePosted = false
            val layout = rootLayout ?: return@postFrameCallback
            params.x = pendingMoveX
            params.y = pendingMoveY
            runCatching { windowManager.updateViewLayout(layout, params) }
                .onFailure { error -> Log.w(TAG, "failed to move floating ball", error) }
        }
    }


    fun keepTouchableOnScreen() {
        if (dragInProgress) return
        if (!edgeCollapsedState.value) {
            if (isShowingCardState.value || showTuneInputState.value) return
            if (!isGeneratingState.value && System.currentTimeMillis() - lastActiveTimeState.value >= IDLE_COLLAPSE_MS) {
                updateFocus(false)
                settleToEdge()
            }
            return
        }
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val maxX = (displayMetrics.widthPixels - ballPx).coerceAtLeast(0)
        val maxY = (displayMetrics.heightPixels - ballPx).coerceAtLeast(0)
        val nextX = params.x.coerceIn(0, maxX)
        val nextY = params.y.coerceIn(0, maxY)
        if (params.x == nextX && params.y == nextY) return
        params.x = nextX
        params.y = nextY
        pendingMoveX = nextX
        pendingMoveY = nextY
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to keep floating ball on screen", error) }
    }
    private fun settleToEdge() {
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val collapseRight = currentBallScreenX() + ballPx / 2 >= displayMetrics.widthPixels / 2
        val targetX = if (collapseRight) (displayMetrics.widthPixels - ballPx).coerceAtLeast(0) else 0
        val maxY = (displayMetrics.heightPixels - ballPx).coerceAtLeast(0)
        params.x = targetX
        params.y = currentBallScreenY().coerceIn(0, maxY)
        pendingMoveX = params.x
        pendingMoveY = params.y
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to settle floating ball", error) }
        collapsedOnRightState.value = collapseRight
        edgeCollapsedState.value = true
        menuExpandedState.value = false
        showTuneInputState.value = false
        isShowingCardState.value = false
        suggestionsState.clear()
        renamePromptState.value = null
        statusMessageState.value = ""
        notificationHintState.value = false
    }

    private fun revealFromEdge() {
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val maxX = (displayMetrics.widthPixels - ballPx).coerceAtLeast(0)
        params.x = if (!collapsedOnRightState.value) {
            8
        } else {
            (maxX - 8).coerceAtLeast(0)
        }
        rootLayout?.let { layout ->
            runCatching { windowManager.updateViewLayout(layout, params) }
                .onFailure { error -> Log.w(TAG, "failed to reveal floating ball", error) }
        }
        edgeCollapsedState.value = false
    }

    private fun expandMenuKeepingBallFixed() {
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val ballX = targetRevealedBallX()
        val ballY = currentBallScreenY()
        val menuWidthPx = dpToPx(MENU_WIDTH_DP)
        val menuHeightPx = dpToPx(MENU_HEIGHT_DP)
        val maxX = (displayMetrics.widthPixels - menuWidthPx).coerceAtLeast(0)
        val maxY = (displayMetrics.heightPixels - menuHeightPx).coerceAtLeast(0)
        params.x = (ballX - expandedBallLocalX()).coerceIn(0, maxX)
        params.y = (ballY - expandedBallLocalY()).coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to expand menu", error) }
        edgeCollapsedState.value = false
        menuExpandedState.value = true
    }

    private fun targetRevealedBallX(): Int {
        if (!edgeCollapsedState.value) return currentBallScreenX()
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val marginPx = dpToPx(8)
        val maxX = (displayMetrics.widthPixels - ballPx).coerceAtLeast(0)
        return if (collapsedOnRightState.value) {
            (maxX - marginPx).coerceAtLeast(0)
        } else {
            marginPx
        }
    }

    private fun collapseMenuKeepingBallFixed() {
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val ballX = currentBallScreenX()
        val ballY = currentBallScreenY()
        params.x = ballX.coerceIn(0, (displayMetrics.widthPixels - ballPx).coerceAtLeast(0))
        params.y = ballY.coerceIn(0, (displayMetrics.heightPixels - ballPx).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to collapse menu", error) }
        menuExpandedState.value = false
        edgeCollapsedState.value = false
    }

    private fun positionTunePanel() {
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val panelWidthPx = dpToPx(TUNE_PANEL_WIDTH_DP)
        val panelHeightPx = dpToPx(TUNE_PANEL_HEIGHT_DP)
        val marginPx = dpToPx(PANEL_MARGIN_DP)
        val ballX = currentBallScreenX()
        val ballY = currentBallScreenY()
        params.x = (ballX - dpToPx(TUNE_PANEL_WIDTH_DP - BALL_DP)).coerceIn(
            marginPx,
            (displayMetrics.widthPixels - panelWidthPx - marginPx).coerceAtLeast(marginPx)
        )
        params.y = ballY.coerceIn(
            marginPx,
            (displayMetrics.heightPixels - panelHeightPx - marginPx).coerceAtLeast(marginPx)
        )
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to position tune panel", error) }
    }

    private fun hideTunePanelKeepingBallFixed() {
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val ballX = currentBallScreenX()
        val ballY = currentBallScreenY()
        params.x = ballX.coerceIn(0, (displayMetrics.widthPixels - ballPx).coerceAtLeast(0))
        params.y = ballY.coerceIn(0, (displayMetrics.heightPixels - ballPx).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to hide tune panel", error) }
        showTuneInputState.value = false
        menuExpandedState.value = false
        edgeCollapsedState.value = false
    }

    private fun hideSuggestionCardKeepingBallFixed() {
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val ballX = currentBallScreenX()
        val ballY = currentBallScreenY()
        isShowingCardState.value = false
        suggestionsState.clear()
        currentSuggestionIdx.value = 0
        params.x = ballX.coerceIn(0, (displayMetrics.widthPixels - ballPx).coerceAtLeast(0))
        params.y = ballY.coerceIn(0, (displayMetrics.heightPixels - ballPx).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to hide suggestion card", error) }
        edgeCollapsedState.value = false
        lastActiveTimeState.value = System.currentTimeMillis()
    }

    private fun dpToPx(value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    }

    private fun expandedBallLocalX(): Int {
        return if (collapsedOnRightState.value) dpToPx(MENU_WIDTH_DP - BALL_DP) else 0
    }

    private fun expandedBallLocalY(): Int = dpToPx((MENU_HEIGHT_DP - BALL_DP) / 2)

    private fun currentBallScreenX(): Int = params.x + currentBallLocalX()

    private fun currentBallScreenY(): Int {
        return params.y + if (menuExpandedState.value) expandedBallLocalY() else 0
    }

    private fun currentBallLocalX(): Int {
        return when {
            menuExpandedState.value -> expandedBallLocalX()
            showTuneInputState.value -> dpToPx(TUNE_PANEL_WIDTH_DP - BALL_DP)
            renamePromptState.value != null -> dpToPx(RENAME_PROMPT_WIDTH_DP - BALL_DP)
            isShowingCardState.value -> dpToPx(SUGGESTION_WIDTH_DP - BALL_DP)
            statusMessageState.value.isNotBlank() && !edgeCollapsedState.value -> dpToPx(STATUS_WIDTH_DP - BALL_DP)
            else -> 0
        }
    }


    private fun positionWideContentKeepingBallFixed(ballX: Int, contentWidthDp: Int) {
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val localBallX = dpToPx(contentWidthDp - BALL_DP)
        val contentWidthPx = dpToPx(contentWidthDp)
        params.x = (ballX - localBallX).coerceIn(
            0,
            (displayMetrics.widthPixels - contentWidthPx).coerceAtLeast(0)
        )
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to position wide floating content", error) }
    }

    private fun clearStatusKeepingBallFixed() {
        val layout = rootLayout ?: return
        val displayMetrics = context.resources.displayMetrics
        val ballPx = dpToPx(BALL_DP)
        val ballX = currentBallScreenX()
        val ballY = currentBallScreenY()
        statusMessageState.value = ""
        notificationHintState.value = false
        params.x = ballX.coerceIn(0, (displayMetrics.widthPixels - ballPx).coerceAtLeast(0))
        params.y = ballY.coerceIn(0, (displayMetrics.heightPixels - ballPx).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(layout, params) }
            .onFailure { error -> Log.w(TAG, "failed to clear status", error) }
        lastActiveTimeState.value = System.currentTimeMillis()
    }
    private fun handleDispatchTouch(view: View, event: MotionEvent): Boolean {
        if (isShowingCardState.value || showTuneInputState.value || renamePromptState.value != null) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingDispatchTouch = isInsideBall(view, event)
                if (!trackingDispatchTouch) {
                    Log.d(TAG, "dispatch touch outside x=${event.x}, y=${event.y}, w=${view.width}, h=${view.height}")
                    return false
                }

                dispatchStartRawX = event.rawX
                dispatchStartRawY = event.rawY
                dispatchStartWindowX = params.x
                dispatchStartWindowY = params.y
                dispatchDragging = false
                dragInProgress = false
                lastActiveTimeState.value = System.currentTimeMillis()
                Log.d(TAG, "dispatch touch down edgeCollapsed=${edgeCollapsedState.value}, menuExpanded=${menuExpandedState.value}, viewW=${view.width}, viewH=${view.height}, x=${event.x}, y=${event.y}")
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!trackingDispatchTouch) return false
                var dx = event.rawX - dispatchStartRawX
                var dy = event.rawY - dispatchStartRawY
                if (!dispatchDragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                    dispatchDragging = true
                    dragInProgress = true
                    if (edgeCollapsedState.value) {
                        revealFromEdge()
                        dispatchStartRawX = event.rawX
                        dispatchStartRawY = event.rawY
                        dispatchStartWindowX = params.x
                        dispatchStartWindowY = params.y
                        dx = 0f
                        dy = 0f
                    } else {
                        edgeCollapsedState.value = false
                    }
                    menuExpandedState.value = false
                    Log.d(TAG, "dispatch drag start")
                }
                if (dispatchDragging) {
                    moveTo(dispatchStartWindowX + dx.roundToInt(), dispatchStartWindowY + dy.roundToInt())
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!trackingDispatchTouch) return false
                trackingDispatchTouch = false
                if (dispatchDragging) {
                    Log.d(TAG, "dispatch drag end x=${params.x}, y=${params.y}")
                    dispatchDragging = false
                    dragInProgress = false
                    lastActiveTimeState.value = System.currentTimeMillis()
                    settleToEdge()
                } else {
                    view.performClick()
                    handleBallTap("dispatch")
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                trackingDispatchTouch = false
                dispatchDragging = false
                dragInProgress = false
                return true
            }

            else -> return trackingDispatchTouch
        }
    }
    private fun installNativeTouchHandler(touchView: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var startRawX = 0f
        var startRawY = 0f
        var startWindowX = 0
        var startWindowY = 0
        var dragging = false
        var trackingBall = false

        touchView.isClickable = true
        touchView.setOnTouchListener { view, event ->
            if (isShowingCardState.value || showTuneInputState.value || renamePromptState.value != null) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    trackingBall = isInsideBall(view, event)
                    if (!trackingBall) return@setOnTouchListener false

                    startRawX = event.rawX
                    startRawY = event.rawY
                    startWindowX = params.x
                    startWindowY = params.y
                    dragging = false
                    dragInProgress = false
                    lastActiveTimeState.value = System.currentTimeMillis()
                    Log.d(TAG, "native touch down edgeCollapsed=${edgeCollapsedState.value}, menuExpanded=${menuExpandedState.value}, x=${params.x}, y=${params.y}")
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!trackingBall) return@setOnTouchListener false
                    var dx = event.rawX - startRawX
                    var dy = event.rawY - startRawY
                    if (!dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                        dragging = true
                        dragInProgress = true
                        if (edgeCollapsedState.value) {
                            revealFromEdge()
                            startRawX = event.rawX
                            startRawY = event.rawY
                            startWindowX = params.x
                            startWindowY = params.y
                            dx = 0f
                            dy = 0f
                        } else {
                            edgeCollapsedState.value = false
                        }
                        menuExpandedState.value = false
                        Log.d(TAG, "native drag start")
                    }
                    if (dragging) {
                        moveTo(startWindowX + dx.roundToInt(), startWindowY + dy.roundToInt())
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!trackingBall) return@setOnTouchListener false
                    trackingBall = false
                    if (dragging) {
                        Log.d(TAG, "native drag end x=${params.x}, y=${params.y}")
                        dragging = false
                        dragInProgress = false
                        lastActiveTimeState.value = System.currentTimeMillis()
                        settleToEdge()
                    } else {
                        view.performClick()
                        handleBallTap("native")
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    trackingBall = false
                    dragging = false
                    dragInProgress = false
                    true
                }

                else -> trackingBall
            }
        }
    }

    private fun isInsideBall(root: View, event: MotionEvent): Boolean {
        val ballPx = dpToPx(BALL_DP)
        val maxLeft = (root.width - ballPx).coerceAtLeast(0)
        val left = currentBallLocalX().coerceIn(0, maxLeft).toFloat()
        val right = (left + ballPx).coerceAtMost(root.width.toFloat().coerceAtLeast(ballPx.toFloat()))
        val top = if (menuExpandedState.value) {
            ((root.height - ballPx) / 2).coerceAtLeast(0).toFloat()
        } else {
            0f
        }
        val bottom = (top + ballPx).coerceAtMost(root.height.toFloat().coerceAtLeast(ballPx.toFloat()))
        return event.x in left..right && event.y in top..bottom
    }

    private fun handleBallTap(source: String) {
        val now = System.currentTimeMillis()
        if (now - lastPrimaryActionAt < 180L) {
            Log.d(TAG, "ignore duplicate $source tap version=$DEBUG_VERSION deltaMs=${now - lastPrimaryActionAt}")
            return
        }
        lastPrimaryActionAt = now
        Log.d(TAG, "$source tap version=$DEBUG_VERSION edgeCollapsed=${edgeCollapsedState.value}, menuExpanded=${menuExpandedState.value}")
        clearNotificationPulse()
        if (edgeCollapsedState.value) {
            expandMenuKeepingBallFixed()
            statusMessageState.value = ""
        } else if (menuExpandedState.value) {
            collapseMenuKeepingBallFixed()
        } else {
            expandMenuKeepingBallFixed()
            statusMessageState.value = ""
        }
        showTuneInputState.value = false
        updateFocus(false)
        lastActiveTimeState.value = now
    }

    private fun handleMenuAction(action: () -> Unit) {
        clearNotificationPulse()
        collapseMenuKeepingBallFixed()
        edgeCollapsedState.value = false
        showTuneInputState.value = false
        statusMessageState.value = ""
        updateFocus(false)
        lastActiveTimeState.value = System.currentTimeMillis()
        action()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun FloatingBallContent() {
        var edgeCollapsed by edgeCollapsedState
        var menuExpanded by menuExpandedState
        var showTuneInput by showTuneInputState
        var tuneText by tuneTextState
        var lastActiveTime by lastActiveTimeState

        LaunchedEffect(lastActiveTime, showTuneInput, menuExpanded, isShowingCardState.value, isGeneratingState.value, notificationHintState.value, statusMessageState.value, renamePromptState.value, edgeCollapsed) {
            if (!isGeneratingState.value && !edgeCollapsed && !isShowingCardState.value && !showTuneInput) {
                kotlinx.coroutines.delay(IDLE_COLLAPSE_MS)
                if (!isGeneratingState.value && !isShowingCardState.value && !showTuneInputState.value && !dragInProgress && System.currentTimeMillis() - lastActiveTimeState.value >= IDLE_COLLAPSE_MS) {
                    updateFocus(false)
                    settleToEdge()
                }
            }
        }

        LaunchedEffect(statusMessageState.value, isGeneratingState.value, isShowingCardState.value, showTuneInput, menuExpanded, notificationHintState.value) {
            if (statusMessageState.value.isNotBlank() &&
                !isGeneratingState.value &&
                !isShowingCardState.value &&
                !showTuneInput &&
                !menuExpanded &&
                !notificationHintState.value
            ) {
                kotlinx.coroutines.delay(2500L)
                if (statusMessageState.value.isNotBlank() &&
                    !isGeneratingState.value &&
                    !isShowingCardState.value &&
                    !showTuneInputState.value &&
                    !menuExpandedState.value &&
                    !notificationHintState.value
                ) {
                    clearStatusKeepingBallFixed()
                }
            }
        }
        val baseAlpha = if (edgeCollapsed) 0.72f else 0.96f
        val notificationActive = notificationHintState.value
        val pulseScale: Float
        val pulseAlpha: Float
        if (notificationActive) {
            val transition = rememberInfiniteTransition(label = "notifPulse")
            val animatedScale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            val animatedAlpha by transition.animateFloat(
                initialValue = baseAlpha,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            pulseScale = animatedScale
            pulseAlpha = animatedAlpha
        } else {
            pulseScale = 1f
            pulseAlpha = baseAlpha
        }

        val showRenamePrompt = renamePromptState.value
        val showStatusChip = statusMessageState.value.isNotBlank() && !edgeCollapsed && !isShowingCardState.value && showRenamePrompt == null && !showTuneInput && !menuExpanded
        val showSuggestionCard = isShowingCardState.value && suggestionsState.isNotEmpty()
        val contentWidthDp = when {
            menuExpanded -> MENU_WIDTH_DP
            showTuneInput -> TUNE_PANEL_WIDTH_DP
            showRenamePrompt != null -> RENAME_PROMPT_WIDTH_DP
            showSuggestionCard -> SUGGESTION_WIDTH_DP
            showStatusChip -> STATUS_WIDTH_DP
            else -> BALL_DP
        }
        val contentHeightDp = when {
            menuExpanded -> MENU_HEIGHT_DP
            showTuneInput -> TUNE_PANEL_HEIGHT_DP
            showRenamePrompt != null -> RENAME_PROMPT_HEIGHT_DP
            showSuggestionCard -> SUGGESTION_HEIGHT_DP
            showStatusChip -> STATUS_HEIGHT_DP
            else -> BALL_DP
        }

        Box(
            modifier = Modifier.size(
                width = contentWidthDp.dp,
                height = contentHeightDp.dp
            )
        ) {
            if (menuExpanded) {
                fun Modifier.radialActionOffset(angleDegrees: Float): Modifier {
                    val opensToLeft = collapsedOnRightState.value
                    val resolvedAngle = if (opensToLeft) angleDegrees else 180f - angleDegrees
                    val angle = Math.toRadians(resolvedAngle.toDouble())
                    val mainCenterX = if (opensToLeft) {
                        MENU_WIDTH_DP - BALL_DP / 2f
                    } else {
                        BALL_DP / 2f
                    }
                    val mainCenterY = MENU_HEIGHT_DP / 2f
                    val actionHalf = ACTION_DP / 2f
                    val x = mainCenterX + RADIAL_RADIUS_DP * cos(angle).toFloat() - actionHalf
                    val y = mainCenterY + RADIAL_RADIUS_DP * sin(angle).toFloat() - actionHalf
                    return this
                        .align(Alignment.TopStart)
                        .offset(x = x.dp, y = y.dp)
                }

                RadialActionButton(
                    icon = Icons.Default.Search,
                    label = "\u622a\u5c4f",
                    modifier = Modifier.radialActionOffset(240f),
                    onClick = { handleMenuAction(onManualScreenshotClick) }
                )
                RadialActionButton(
                    icon = Icons.Default.Star,
                    label = "\u751f\u6210",
                    modifier = Modifier.radialActionOffset(200f),
                    onClick = { handleMenuAction { onPrimaryActionClick(tuneText.takeIf { it.isNotBlank() }) } }
                )
                RadialActionButton(
                    icon = Icons.Default.Settings,
                    label = "\u5fae\u8c03",
                    modifier = Modifier.radialActionOffset(160f),
                    onClick = {
                        collapseMenuKeepingBallFixed()
                        positionTunePanel()
                        showTuneInput = true
                        edgeCollapsed = false
                        updateFocus(true)
                        lastActiveTime = System.currentTimeMillis()
                    }
                )
                RadialActionButton(
                    icon = Icons.Default.Close,
                    label = "\u5173\u95ed",
                    modifier = Modifier.radialActionOffset(120f),
                    onClick = { handleMenuAction(onCloseClick) }
                )
            }

            if (showStatusChip) {
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    StatusChip(
                        text = statusMessageState.value,
                        notificationMode = notificationHintState.value
                    )
                }
            }

            if (showTuneInput) {
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    TuneInputCard(
                        initialText = tuneText,
                        onTextChange = { tuneText = it },
                        onConfirm = {
                            onTuneConfirm(tuneText)
                            hideTunePanelKeepingBallFixed()
                            updateFocus(false)
                            lastActiveTime = System.currentTimeMillis()
                        },
                        onDismiss = {
                            hideTunePanelKeepingBallFixed()
                            updateFocus(false)
                        }
                    )
                }
            }

            if (showSuggestionCard) {
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    SuggestionCard(
                        text = suggestionsState.getOrNull(currentSuggestionIdx.value).orEmpty(),
                        index = currentSuggestionIdx.value,
                        total = suggestionsState.size,
                        onPrev = { if (currentSuggestionIdx.value > 0) currentSuggestionIdx.value -= 1 },
                        onNext = { if (currentSuggestionIdx.value < suggestionsState.size - 1) currentSuggestionIdx.value += 1 },
                        onDismiss = { hideSuggestionCardKeepingBallFixed() },
                        onAccept = {
                            suggestionsState.getOrNull(currentSuggestionIdx.value)?.let(onSuggestionCopied)
                            hideSuggestionCardKeepingBallFixed()
                        }
                    )
                }
            }

            showRenamePrompt?.let { prompt ->
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    RenamePromptCard(
                        currentName = prompt.currentName,
                        suggestedName = prompt.suggestedName,
                        onKeep = {
                            showStatus(prompt.completionMessage)
                        },
                        onRename = {
                            prompt.onConfirm()
                            showStatus(prompt.completionMessage)
                        }
                    )
                }
            }

            MainBall(
                alpha = pulseAlpha,
                scale = pulseScale,
                modifier = Modifier
                    .align(
                        when {
                            menuExpanded && collapsedOnRightState.value -> Alignment.CenterEnd
                            menuExpanded -> Alignment.CenterStart
                            else -> Alignment.TopEnd
                        }
                    )
                    .offset(
                        x = when {
                            !edgeCollapsed -> 0.dp
                            collapsedOnRightState.value -> EDGE_VISUAL_OFFSET_DP.dp
                            else -> (-EDGE_VISUAL_OFFSET_DP).dp
                        }
                    )
            )
        }
    }

    @Composable
    private fun MainBall(
        alpha: Float,
        scale: Float,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .size(BALL_DP.dp)
                .alpha(alpha)
                .scale(scale)
                .clip(CircleShape)
                .background(Color(0x2622252D))
                .border(
                    width = 4.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF36E6FF),
                            Color(0xFF7C5CFF),
                            Color(0xFFFF6FD8),
                            Color(0xFFFFD166),
                            Color(0xFF36E6FF)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isGeneratingState.value -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                notificationHintState.value -> {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFFEFF7FF),
                        modifier = Modifier.size(23.dp)
                    )
                }
                menuExpandedState.value -> {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFFEFF7FF),
                        modifier = Modifier.size(26.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = Color(0xFFEFF7FF),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun RadialActionButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Box(
            modifier = modifier
                .size(ACTION_DP.dp)
                .clip(CircleShape)
                .background(Color(0x8A22252D))
                .border(1.dp, Color(0x44FFFFFF), CircleShape)
                .combinedClickable(onClick = onClick, onLongClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xEFFFFFFF),
                modifier = Modifier.size(21.dp)
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SuggestionCard(
        text: String,
        index: Int,
        total: Int,
        onPrev: () -> Unit,
        onNext: () -> Unit,
        onDismiss: () -> Unit,
        onAccept: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .width(SUGGESTION_WIDTH_DP.dp)
                .combinedClickable(onClick = {}, onLongClick = onAccept),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 12.dp,
            color = Color(0xF21B1D24)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onPrev, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = Color.White)
                    }
                    Text(
                        text = "${index + 1}/$total  \u957f\u6309\u91c7\u7eb3",
                        fontSize = 12.sp,
                        color = Color(0xFFC9CED8),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onNext, enabled = index < total - 1, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = onAccept, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Check, contentDescription = "\u91c7\u7eb3\u5efa\u8bae", tint = Color(0xFF8EA0FF))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "\u5173\u95ed\u5efa\u8bae", tint = Color(0xFFD7DBE3))
                    }
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .combinedClickable(onClick = {}, onLongClick = onAccept)
                )
            }
        }
    }

    @Composable
    private fun StatusChip(text: String, notificationMode: Boolean) {
        Surface(
            modifier = Modifier.width(STATUS_WIDTH_DP.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (notificationMode) Color(0xE61D3B2A) else Color(0xE61B1D24),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (notificationMode) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun RenamePromptCard(
        currentName: String,
        suggestedName: String,
        onKeep: () -> Unit,
        onRename: () -> Unit
    ) {
        Surface(
            modifier = Modifier.width(RENAME_PROMPT_WIDTH_DP.dp),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 10.dp,
            color = Color(0xF21B1D24)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "\u8bc6\u522b\u5230\u5fae\u4fe1\u6635\u79f0",
                    fontSize = 12.sp,
                    color = Color(0xFF8EA0FF),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "\u5f53\u524d\u662f\u300c$currentName\u300d\uff0c\u662f\u5426\u6539\u4e3a\u300c$suggestedName\u300d\uff1f",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u4fdd\u7559",
                        fontSize = 12.sp,
                        color = Color(0xFFB7BECA),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(onClick = onKeep, onLongClick = onKeep)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "\u6539\u540d",
                        fontSize = 12.sp,
                        color = Color(0xFF8EA0FF),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(onClick = onRename, onLongClick = onRename)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun TuneInputCard(
        initialText: String,
        onTextChange: (String) -> Unit,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .width(TUNE_PANEL_WIDTH_DP.dp),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 10.dp,
            color = Color(0xF21B1D24)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF8EA0FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "\u5fae\u8c03\u8bed\u6c14",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8EA0FF)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = initialText,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            "\u4f8b\u5982\uff1a\u66f4\u6e29\u548c\u3001\u77ed\u4e00\u70b9",
                            fontSize = 12.sp,
                            color = Color(0xFF8C94A3)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFAAB6FF),
                        focusedContainerColor = Color(0xFF232631),
                        unfocusedContainerColor = Color(0xFF232631),
                        focusedPlaceholderColor = Color(0xFF8C94A3),
                        unfocusedPlaceholderColor = Color(0xFF8C94A3),
                        focusedBorderColor = SocialAidModernColors.Purple,
                        unfocusedBorderColor = Color(0xFF545B69)
                    )
                )
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "\u53d6\u6d88",
                        fontSize = 12.sp,
                        color = Color(0xFFB7BECA),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(onClick = onDismiss, onLongClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "\u786e\u5b9a",
                        fontSize = 12.sp,
                        color = Color(0xFF8EA0FF),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(onClick = onConfirm, onLongClick = onConfirm)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    private data class RenamePrompt(
        val currentName: String,
        val suggestedName: String,
        val completionMessage: String,
        val onConfirm: () -> Unit
    )

    private class TouchInterceptFrameLayout(
        context: Context,
        private val onRawTouch: (View, MotionEvent) -> Boolean
    ) : FrameLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            return if (onRawTouch(this, event)) {
                true
            } else {
                super.dispatchTouchEvent(event)
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
