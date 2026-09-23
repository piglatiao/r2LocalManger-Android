package com.r2manager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer
import com.r2manager.android.domain.security.AppLockManager
import com.r2manager.android.domain.security.UnlockResult
import com.r2manager.android.ui.lock.LockViewModel

/**
 * 安全与密码 ViewModel（P4-B）。
 *
 * 应用锁开关 / 改密 / 开关＝密钥体系变更（P3 [AppLockManager] 会先清空缓存再换钥）、
 * 后台自动上锁、优先生物识别偏好与清除凭证。
 */
class SecuritySettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val appLock: AppLockManager = container.appLockManager

    /** 应用锁是否已启用。 */
    fun isLockEnabled(): Boolean = appLock.status().enabled

    /** 是否已存在可校验的应用密码。 */
    fun hasPassword(): Boolean = appLock.status().hasPassword

    /** 是否启用「优先生物识别」。 */
    fun isBiometricEnabled(): Boolean =
        container.settingsStore.raw().getBoolean(LockViewModel.KEY_BIOMETRIC_ENABLED, true)

    /** 记录「优先生物识别」开关。 */
    fun setBiometricEnabled(enabled: Boolean) {
        container.settingsStore.raw()
            .edit()
            .putBoolean(LockViewModel.KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    /** 后台自动上锁分钟数（-1=从不，0=立即）。 */
    fun autoLockMinutes(): Int = container.settingsRepository.settings().value.autoLockMinutes

    /** 设置后台自动上锁分钟数。 */
    suspend fun setAutoLockMinutes(minutes: Int) =
        container.settingsRepository.setAutoLockMinutes(minutes)

    /** 开关应用锁（= 密钥体系变更→清缓存换钥）。 */
    suspend fun setEnabled(currentPassword: String?, enable: Boolean): UnlockResult =
        appLock.setEnabled(currentPassword, enable)

    /** 修改密码。 */
    suspend fun changePassword(current: String?, newPassword: String): UnlockResult =
        appLock.changePassword(current, newPassword)

    /** 清除已保存凭证。 */
    suspend fun clearCredentials() = container.credentialRepository.clear()

    companion object {
        /**
         * 构造工厂。
         *
         * @param container 全局依赖容器
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SecuritySettingsViewModel(container) as T
                }
            }
    }
}
