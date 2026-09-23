package com.r2manager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer
import com.r2manager.android.core.util.UrlUtils
import com.r2manager.android.data.repository.CredentialRepository
import com.r2manager.android.data.repository.SaveResult
import com.r2manager.android.data.repository.TestResult
import com.r2manager.android.domain.model.Credentials

/**
 * R2 凭证配置 ViewModel（P4-B）。
 *
 * 存取与「保存后自动列桶」全部复用 P2 [CredentialRepository]；
 * 端点由 Account ID 经 [UrlUtils.buildEndpoint] 派生（与 P2 保存逻辑一致，仅作预览）。
 */
class CredentialsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo: CredentialRepository = container.credentialRepository

    /** 读取已保存凭证。 */
    suspend fun load(): Credentials? = repo.load()

    /** 保存凭证，并刷新设置状态以同步自动选择的首桶。 */
    suspend fun save(credentials: Credentials): SaveResult {
        val result = repo.save(credentials)
        if (result.saved) {
            container.settingsRepository.refresh()
        }
        return result
    }

    /** 连接测试（10s 超时内列一次对象）。 */
    suspend fun test(credentials: Credentials): TestResult = repo.testConnection(credentials)

    /** 清除已保存凭证。 */
    suspend fun clear() = repo.clear()

    /** 由 Account ID 预览 S3 端点。 */
    fun endpointFor(accountId: String): String =
        UrlUtils.buildEndpoint(accountId, container.settingsRepository.settings().value.endpoint)

    /** 当前区域（固定 auto）。 */
    fun region(): String = container.settingsRepository.settings().value.region

    /** 应用锁是否已启用（用于未启用时的安全提示条）。 */
    fun isAppLockEnabled(): Boolean = container.appLockManager.status().enabled

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
                    return CredentialsViewModel(container) as T
                }
            }
    }
}
