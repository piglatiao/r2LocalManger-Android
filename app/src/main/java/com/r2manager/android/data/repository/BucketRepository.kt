package com.r2manager.android.data.repository

import com.r2manager.android.data.local.cache.ObjectListCache
import com.r2manager.android.data.local.cache.ThumbnailCache
import com.r2manager.android.data.local.prefs.SettingsStore
import com.r2manager.android.data.remote.cf.CloudflareClient
import com.r2manager.android.data.remote.cf.CustomDomainInput
import com.r2manager.android.data.remote.s3.S3Client
import com.r2manager.android.data.remote.s3.S3ClientImpl
import com.r2manager.android.data.remote.s3.S3Config
import com.r2manager.android.domain.model.Bucket
import com.r2manager.android.domain.model.CustomDomain
import com.r2manager.android.domain.model.ManagedDomain
import com.r2manager.android.domain.model.PublicUrlConfig
import com.r2manager.android.domain.model.Zone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 存储桶管理仓库（Cloudflare 管理面 + 删桶前的 S3 清空）。
 */
interface BucketRepository {
    suspend fun listBuckets(): List<Bucket>
    suspend fun getBucket(name: String): Bucket
    suspend fun createBucket(name: String, storageClass: String, locationHint: String?): Bucket
    suspend fun updateStorageClass(name: String, storageClass: String): Bucket

    /** 先经 S3 清空桶内全部对象（循环分页 + 批量删除），再删除桶。 */
    suspend fun deleteBucket(name: String)

    suspend fun listCustomDomains(bucket: String): List<CustomDomain>
    suspend fun upsertCustomDomain(bucket: String, input: CustomDomainInput): CustomDomain
    suspend fun deleteCustomDomain(bucket: String, domain: String)
    suspend fun getManagedDomain(bucket: String): ManagedDomain
    suspend fun setManagedDomain(bucket: String, enabled: Boolean): ManagedDomain
    suspend fun listZones(): List<Zone>

    /** 切桶：同步公开域名 → 回写 publicUrl → 清空列表/缩略图内存态。 */
    suspend fun switchBucket(name: String): PublicUrlConfig
}

/**
 * [BucketRepository] 默认实现。
 *
 * @param cfProvider 当前 Cloudflare 管理面客户端（null = 未配置）
 * @param configProvider 当前 S3 配置（用于为任意桶构造 S3Client 清空对象）
 * @param httpClient 复用的 OkHttp 客户端
 * @param settings 设置读写（回写 currentBucket / publicUrl）
 * @param listCache 列表缓存（桶级清理）
 * @param thumbnailCache 缩略图缓存（桶级清理）
 * @param ioDispatcher 网络/IO 调度器
 */
class BucketRepositoryImpl(
    private val cfProvider: () -> CloudflareClient?,
    private val configProvider: () -> S3Config?,
    private val httpClient: OkHttpClient,
    private val settings: SettingsStore,
    private val listCache: ObjectListCache,
    private val thumbnailCache: ThumbnailCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BucketRepository {

    private fun cf(): CloudflareClient =
        cfProvider() ?: throw IllegalStateException("Cloudflare 管理面未配置")

    private fun s3For(bucket: String): S3Client? =
        configProvider()?.copy(bucket = bucket)?.let { S3ClientImpl(it, httpClient, ioDispatcher) }

    override suspend fun listBuckets(): List<Bucket> = withContext(ioDispatcher) { cf().listBuckets() }

    override suspend fun getBucket(name: String): Bucket = withContext(ioDispatcher) { cf().getBucket(name) }

    override suspend fun createBucket(
        name: String,
        storageClass: String,
        locationHint: String?
    ): Bucket = withContext(ioDispatcher) { cf().createBucket(name, storageClass, locationHint) }

    override suspend fun updateStorageClass(name: String, storageClass: String): Bucket =
        withContext(ioDispatcher) { cf().updateBucketStorageClass(name, storageClass) }

    override suspend fun deleteBucket(name: String): Unit = withContext(ioDispatcher) {
        emptyBucket(name)
        cf().deleteBucket(name)
        // 远端删桶后，清理不属于现存桶的缓存（列表接口异常返回空时不清理）
        runCatching { listBuckets().map { it.name } }
            .onSuccess { buckets -> purgeCaches(buckets) }
    }

    private suspend fun emptyBucket(name: String) {
        val s3 = s3For(name) ?: throw IllegalStateException("S3 未配置，无法清空桶")
        var token: String? = null
        do {
            val page = s3.listObjectsRaw(prefix = "", delimiter = null, continuationToken = token)
            val keys = page.contents.map { it.key }.filter { it.isNotEmpty() }
            if (keys.isNotEmpty()) {
                s3.deleteObjects(keys)
            }
            token = if (page.isTruncated) page.nextContinuationToken else null
        } while (token != null)
    }

    private suspend fun purgeCaches(validBuckets: List<String>) {
        thumbnailCache.purgeBuckets(validBuckets)
        listCache.purgeBuckets(validBuckets)
    }

    override suspend fun listCustomDomains(bucket: String): List<CustomDomain> =
        withContext(ioDispatcher) { cf().listCustomDomains(bucket) }

    override suspend fun upsertCustomDomain(bucket: String, input: CustomDomainInput): CustomDomain =
        withContext(ioDispatcher) { cf().upsertCustomDomain(bucket, input) }

    override suspend fun deleteCustomDomain(bucket: String, domain: String) =
        withContext(ioDispatcher) { cf().deleteCustomDomain(bucket, domain) }

    override suspend fun getManagedDomain(bucket: String): ManagedDomain =
        withContext(ioDispatcher) { cf().getManagedDomain(bucket) }

    override suspend fun setManagedDomain(bucket: String, enabled: Boolean): ManagedDomain =
        withContext(ioDispatcher) { cf().setManagedDomain(bucket, enabled) }

    override suspend fun listZones(): List<Zone> = withContext(ioDispatcher) { cf().listZones() }

    override suspend fun switchBucket(name: String): PublicUrlConfig = withContext(ioDispatcher) {
        val endpoint = configProvider()?.normalizedEndpoint.orEmpty()
        val customDomains = runCatching { cf().listCustomDomains(name) }.getOrDefault(emptyList())
        val managed = runCatching { cf().getManagedDomain(name) }.getOrNull()

        val config = PublicUrlResolver.resolve(customDomains, managed, endpoint)
        settings.setCurrentBucket(name)
        settings.setPublicUrl(config.baseUrl)
        // 列表/缩略图缓存以 bucket 为键，切桶后自然隔离；此处无需逐条清除。
        config
    }
}
