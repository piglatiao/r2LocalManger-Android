package com.r2manager.android.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer
import com.r2manager.android.domain.model.AppLockStatus
import com.r2manager.android.domain.security.AppLockManager
import com.r2manager.android.domain.security.KeyManager
import com.r2manager.android.domain.security.UnlockResult
import com.r2manager.android.data.local.prefs.SettingsStore

/**
 * 应用锁解锁 ViewModel（P4-B）。
 *
 * 复用 P3 的 [AppLockManager]：解锁、连续失败锁定（内部由 P3 [com.r2manager.android.domain.security.PinAttemptTracker]
 * 保证「5 次失败锁 30 秒」）、冷启动密钥准备等安全语义全部下沉到 P3，UI 只做展示与调用。
 *
 * 生物识别偏好为本页私有开关（`SharedPreferences` 私有键），不新增全局资源；
 * 是否具备生物识别能力由 [com.r2manager.android.domain.security.BiometricAuthenticator] 在 Activity 侧判定。
 */
class LockViewModel(private val container: AppContainer) : ViewModel() {

    private val appLock: AppLockManager = container.appLockManager
    private val keyManager: KeyManager = container.keyManager
    private val settings: SettingsStore = container.settingsStore

    /** 当前应用锁状态。 */
    fun status(): AppLockStatus = appLock.status()

    /** 会话是否已持有缓存密钥（软上锁场景：密钥仍在内存，可被生物识别直接放行）。 */
    fun hasSessionKey(): Boolean = keyManager.hasKey()

    /** 是否已登记生物识别绑定密钥（冷启动下生物识别成功后可由 Keystore 私钥还原会话密钥）。 */
    fun hasBiometricKey(): Boolean = keyManager.hasBiometricKey()

    /** 是否启用「优先生物识别」。 */
    fun isBiometricEnabled(): Boolean = settings.raw().getBoolean(KEY_BIOMETRIC_ENABLED, true)

    /** 记录「优先生物识别」开关。 */
    fun setBiometricEnabled(enabled: Boolean) {
        settings.raw().edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * 用密码解锁（连续失败计数与锁定时长由 P3 [AppLockManager] 内部 [com.r2manager.android.domain.security.PinAttemptTracker] 管理）。
     *
     * @param password 用户输入的密码
     * @return 解锁结果；[UnlockResult.lockoutRemainingMs] 为剩余锁定毫秒数（未锁定为 0）
     */
    suspend fun unlockWithPassword(password: String): UnlockResult = appLock.unlockWithPassword(password)

    /**
     * 生物识别解锁：调用前提是 UI 已完成一次成功的生物识别。
     * 由 P3 [AppLockManager] 用生物识别绑定的 Keystore 私钥还原会话密钥（PRD R-03 冷启动解锁）。
     *
     * @return 成功则 [UnlockResult.success]=true；失败应回退 PIN 兜底通道
     */
    suspend fun unlockByBiometric(): UnlockResult = appLock.unlockByBiometric()

    /** 「暂不设置密码」，之后不再提示初始化。 */
    fun dismissSetup() = appLock.dismissSetup()

    /** 首次设置应用密码（同时启用锁）；调用方需先保证长度合法。 */
    suspend fun setPassword(password: String) = appLock.setPassword(password)

    companion object {
        /** 生物识别偏好键（本页私有，后续若上收为平台键再并入 P1 PrefKeys）。 */
        const val KEY_BIOMETRIC_ENABLED = "securityBiometricEnabled"

        /**
         * 构造工厂。
         *
         * @param container 全局依赖容器
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LockViewModel(container) as T
                }
            }
    }
}
