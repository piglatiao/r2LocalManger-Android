package com.r2manager.android.ui.common

import android.content.Context
import android.view.View
import android.widget.TextView

/**
 * 通用视图扩展（可见性、文本、dp 换算）。
 *
 * 说明：`View.isVisible` 属性由 AndroidX core-ktx 提供（`androidx.core.view.isVisible`），
 * 此处不再重复定义。
 */

/** 设置文本；为空时自动 `GONE`，否则 `VISIBLE`。 */
fun TextView.setTextOrGone(text: CharSequence?) {
    if (text.isNullOrEmpty()) {
        visibility = View.GONE
    } else {
        visibility = View.VISIBLE
        this.text = text
    }
}

/** dp → px。 */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** dp → px（视图上下文）。 */
fun View.dp(value: Int): Int = context.dp(value)
