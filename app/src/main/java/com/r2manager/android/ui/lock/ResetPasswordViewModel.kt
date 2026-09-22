package com.r2manager.android.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer
import com.r2manager.android.domain.security.AppLockManager
import com.r2manager.android.domain.security.UnlockResult

/**
 * 找回密码 ViewModel（P4-B）。
 *
 * 复用 P3 [AppLockManager.resetPasswordWithCloudflare]：先用当前账号的 Cloudflare 凭据校验身份，
 * 通过后清空缓存并以新密码换钥（R-04）。身份校验实现由 `AppContainer` 装配 [AppLockManager] 时注入。
 */
class ResetPasswordViewModel(private val container: AppContainer) : ViewModel() {

    private val appLock: AppLockManager = container.appLockManager

    /**
     * 用 Cloudflare 凭据验证身份并重设密码。
     *
     * @param accountId Cloudflare Account ID
     * @param apiToken Cloudflare API Token
     * @param newPassword 新密码
     */
    suspend fun reset(
        accountId: String,
        apiToken: String,
        newPassword: String
    ): UnlockResult = appLock.resetPasswordWithCloudflare(accountId, apiToken, newPassword)

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
                    return ResetPasswordViewModel(container) as T
                }
            }
    }
}
