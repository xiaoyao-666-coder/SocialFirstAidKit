package com.socialfirstaidkit.core.ui.overlay

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver

/**
 * 键盘高度监听器
 *
 * 通过 ViewTreeObserver 监听键盘弹出/收起，获取键盘高度。
 * 在 WindowInsetsCompat 不可用时估算键盘高度。
 */
class KeyboardHeightObserver(
    private val activity: Activity,
    private val onKeyboardHeightChanged: (height: Int, isVisible: Boolean) -> Unit
) : ViewTreeObserver.OnGlobalLayoutListener {

    private var lastKeyboardHeight = 0
    private val rootView: View = activity.window.decorView.findViewById(android.R.id.content)
    private val usableHeight = IntArray(1)

    fun start() {
        rootView.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    fun stop() {
        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onGlobalLayout() {
        val rect = Rect()
        rootView.getWindowVisibleDisplayFrame(rect)

        val screenHeight = rootView.rootView.height
        val keyboardHeight = screenHeight - rect.bottom

        if (keyboardHeight > 100) {
            // 键盘可见
            if (keyboardHeight != lastKeyboardHeight) {
                lastKeyboardHeight = keyboardHeight
                onKeyboardHeightChanged(keyboardHeight, true)
            }
        } else {
            // 键盘隐藏
            if (lastKeyboardHeight > 0) {
                lastKeyboardHeight = 0
                onKeyboardHeightChanged(0, false)
            }
        }
    }
}
