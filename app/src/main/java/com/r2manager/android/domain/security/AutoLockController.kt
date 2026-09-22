package com.r2manager.android.domain.security

import com.r2manager.android.data.local.prefs.SettingsStore

/**
 * 后台自动上锁控制器（P3）。
 *
 * 决策定案（见 §8.1）：后台自动上锁可选 **立即 / 1 / 5 / 15 分钟 / 从不**，
 * 对应 `autoLockMinutes`：`-1`=从不、`0`=立即、`1/5/15`=分钟数。
 *
 * 使用方式（由 UI 生命周期调用）：
 * `onStop/onPause` → [onBackground]；回到前台先 [shouldLock] 判断是否需跳解锁页，再 [onForeground]。
 * 该状态仅存在于内存：进程被杀时冷启动本就会重新 [AppLockManager.bootstrap]。
 */
class AutoLockController(private val prefs: SettingsStore) {

    @Volatile
    private var backgroundAt: Long = 0L

    /** 记录进入后台的时间点。 */
    fun onBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    /** 依据配置判断当前是否应上锁。 */
    fun shouldLock(): Boolean {
        val minutes = prefs.load().autoLockMinutes
        if (minutes < 0) return false          // 从不
        if (minutes == 0) return true          // 立即
        if (backgroundAt <= 0L) return false
        return System.currentTimeMillis() - backgroundAt >= minutes * 60_000L
    }

    /** 回到前台（已处理完是否上锁）后清空后台计时。 */
    fun onForeground() {
        backgroundAt = 0L
    }
}
