package com.r2manager.android.core.util

/**
 * 一次性事件包装，避免 `StateFlow` 在配置变更后重复投递（导航 / Snackbar）。
 *
 * 用法：ViewModel 暴露 `Event<T>`，UI 侧调用 [consume] 取一次值。
 */
class Event<out T>(private val content: T) {

    private var handled: Boolean = false

    /**
     * 返回并标记为已消费；再次调用返回 null。
     */
    fun consume(): T? {
        if (handled) {
            return null
        }
        handled = true
        return content
    }

    /**
     * 只查看内容，不改变消费状态。
     */
    fun peek(): T? = content
}
