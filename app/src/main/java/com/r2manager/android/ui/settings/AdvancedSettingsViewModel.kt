package com.r2manager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer
import com.r2manager.android.data.remote.cf.CustomDomainInput
import com.r2manager.android.data.repository.BucketRepository
import com.r2manager.android.domain.model.CustomDomain
import com.r2manager.android.domain.model.ManagedDomain
import com.r2manager.android.domain.model.Zone

/**
 * 高级设置 ViewModel（P4-B）· 自定义域名与 r2.dev。
 *
 * 全部经 P2 [BucketRepository] 走 Cloudflare 管理面：列 / 绑 / 解绑自定义域名，
 * 查询 / 启停 r2.dev 托管域名，列可用 Zone。
 */
class AdvancedSettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo: BucketRepository = container.bucketRepository

    /** 当前公开访问地址。 */
    fun publicUrl(): String = container.settingsRepository.settings().value.publicUrl

    /** 当前桶名。 */
    fun currentBucket(): String = container.settingsRepository.settings().value.currentBucket

    /** 列出桶的自定义域名。 */
    suspend fun listCustomDomains(bucket: String): List<CustomDomain> =
        repo.listCustomDomains(bucket)

    /** 绑定 / 更新自定义域名。 */
    suspend fun upsertCustomDomain(bucket: String, input: CustomDomainInput): CustomDomain =
        repo.upsertCustomDomain(bucket, input)

    /** 解绑自定义域名。 */
    suspend fun deleteCustomDomain(bucket: String, domain: String) =
        repo.deleteCustomDomain(bucket, domain)

    /** 查询 r2.dev 托管域名。 */
    suspend fun getManagedDomain(bucket: String): ManagedDomain = repo.getManagedDomain(bucket)

    /** 启停 r2.dev 托管域名。 */
    suspend fun setManagedDomain(bucket: String, enabled: Boolean): ManagedDomain =
        repo.setManagedDomain(bucket, enabled)

    /** 列出账号可用 Zone。 */
    suspend fun listZones(): List<Zone> = repo.listZones()

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
                    return AdvancedSettingsViewModel(container) as T
                }
            }
    }
}
