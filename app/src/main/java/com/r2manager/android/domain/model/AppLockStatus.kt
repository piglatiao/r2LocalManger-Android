package com.r2manager.android.domain.model

/**
 * 应用锁状态。
 *
 * @property enabled 是否启用应用锁
 * @property hasPassword 是否已设置密码
 * @property locked 当前是否处于锁定态
 * @property needsSetup 是否需要引导设置密码
 */
data class AppLockStatus(
    val enabled: Boolean,
    val hasPassword: Boolean,
    val locked: Boolean,
    val needsSetup: Boolean
)
