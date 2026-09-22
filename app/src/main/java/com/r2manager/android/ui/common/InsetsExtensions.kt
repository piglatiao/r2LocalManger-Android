package com.r2manager.android.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 边到边（edge-to-edge）与窗口内边距工具。
 *
 * 一期所有页面均开启 edge-to-edge；顶部/底部安全区通过给具体视图追加 padding 处理，
 * 不改变其尺寸，避免与 `fitsSystemWindows` 冲突。
 */

/**
 * 为视图顶部追加「状态栏」内边距（保留原有 padding）。
 */
fun View.applyStatusBarPadding() {
    val base = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = base + top)
        insets
    }
    if (ViewCompat.isAttachedToWindow(this)) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                ViewCompat.requestApplyInsets(v)
                v.removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

/**
 * 为视图底部追加「导航栏」内边距（保留原有 padding）。
 */
fun View.applyNavigationBarPadding() {
    val base = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.updatePadding(bottom = base + bottom)
        insets
    }
    if (ViewCompat.isAttachedToWindow(this)) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                ViewCompat.requestApplyInsets(v)
                v.removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

/**
 * 为视图底部追加「系统栏 + IME」中较大者的内边距（输入页使用）。
 */
fun View.applyImeBottomPadding() {
    val base = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        view.updatePadding(bottom = base + maxOf(bars, ime))
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * 一次性应用系统栏内边距（顶部 + 底部），用于简单页面。
 */
fun View.applySystemBarsPadding() {
    val baseTop = paddingTop
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
