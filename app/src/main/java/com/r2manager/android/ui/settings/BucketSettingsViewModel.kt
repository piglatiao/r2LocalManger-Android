package com.r2manager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer
import com.r2manager.android.data.repository.BucketRepository
import com.r2manager.android.domain.model.Bucket
import com.r2manager.android.domain.model.PublicUrlConfig

/**
 * 存储桶设置 ViewModel（P4-B）。
 *
 * 全部操作复用 P2 [BucketRepository]：列桶 / 建桶 / 改存储类型 / 删桶（先 S3 清空再删除）/ 切桶。
 */
class BucketSettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo: BucketRepository = container.bucketRepository

    /** 最近一次列出的桶（供删除 / 编辑定位）。 */
    var buckets: List<Bucket> = emptyList()
        private set

    /** 当前桶名。 */
    fun currentBucket(): String = container.settingsRepository.settings().value.currentBucket

    /** 列出账号下全部桶；当前桶为空时默认选中返回列表的第一个。 */
    suspend fun listBuckets(): List<Bucket> {
        buckets = repo.listBuckets()
        if (buckets.isNotEmpty() && currentBucket().isBlank()) {
            repo.switchBucket(buckets.first().name)
            container.settingsRepository.refresh()
        }
        return buckets
    }

    /** 切换当前桶（同步公开域名 → 回写 publicUrl → 桶级缓存隔离）。 */
    suspend fun switchBucket(name: String): PublicUrlConfig = repo.switchBucket(name)

    /** 新建桶。 */
    suspend fun createBucket(name: String, storageClass: String, locationHint: String?): Bucket =
        repo.createBucket(name, storageClass, locationHint)

    /** 更新指定桶的存储类型。 */
    suspend fun updateStorageClass(name: String, storageClass: String): Bucket =
        repo.updateStorageClass(name, storageClass)

    /** 删除桶（先清空全部对象再删除）。 */
    suspend fun deleteBucket(name: String) = repo.deleteBucket(name)

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
                    return BucketSettingsViewModel(container) as T
                }
            }
    }
}
