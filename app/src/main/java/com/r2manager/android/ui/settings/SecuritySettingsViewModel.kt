package com.r2manager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer

/**
 * 系统安全页 ViewModel（P4-B）。
 *
 * 安卓端应用锁由系统管理，本页只保留凭证清除操作。
 */
class SecuritySettingsViewModel(private val container: AppContainer) : ViewModel() {

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
