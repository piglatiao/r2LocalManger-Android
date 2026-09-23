package com.r2manager.android.data.repository

import com.r2manager.android.core.constants.NetworkConstants
import com.r2manager.android.core.constants.TransferConstants
import com.r2manager.android.core.mime.FileTypes
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.core.util.PathUtils
import com.r2manager.android.core.util.UrlUtils
import com.r2manager.android.data.local.cache.ObjectListCache
import com.r2manager.android.data.local.cache.ThumbnailCache
import com.r2manager.android.data.remote.ConnectivityMonitor
import com.r2manager.android.data.remote.s3.S3Client
import com.r2manager.android.data.remote.s3.S3ClientImpl
import com.r2manager.android.data.remote.s3.S3Config
import com.r2manager.android.domain.model.BatchDeleteResult
import com.r2manager.android.domain.model.ObjectInfo
import com.r2manager.android.domain.model.ObjectMeta
import com.r2manager.android.domain.model.ThumbnailMeta
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.InputStream

/** 列目录结果（含缓存/离线标记）。 */
data class ListResult(
    val objects: List<ObjectInfo>,
    val nextContinuationToken: String?,
    val isTruncated: Boolean,
    val fromCache: Boolean,
    val cachedAt: Long?,
    val offline: Boolean,
    /** 是否已有完整的 S3 配置；未配置不应显示为离线。 */
    val configured: Boolean = true
)

/** 预览数据（文本 / 二进制流 / 超限）。 */
sealed interface PreviewData {
    data class Text(val content: String, val meta: ObjectMeta) : PreviewData
    data class Binary(val stream: InputStream, val meta: ObjectMeta) : PreviewData
    data class TooLarge(val meta: ObjectMeta, val kind: PreviewKind) : PreviewData
}

/**
 * 存储仓库：聚合 S3 数据面 + 列表/缩略图缓存，对上层只暴露领域模型与缓存策略。
 */
interface StorageRepository {
    suspend fun listObjects(
        prefix: String,
        forceRefresh: Boolean,
        continuationToken: String? = null,
        pageSize: Int = NetworkConstants.LIST_MAX_KEYS
    ): ListResult

    suspend fun loadThumbnail(meta: ThumbnailMeta): ByteArray?
    suspend fun preview(key: String): PreviewData
    suspend fun head(key: String): ObjectMeta
    suspend fun delete(key: String)
    suspend fun deleteBatch(keys: List<String>): BatchDeleteResult
    suspend fun openObjectStream(key: String): Pair<InputStream, ObjectMeta>
    fun publicUrl(key: String): String

    /** 换桶/换凭证后重建 S3Client（替换配置来源） */
    suspend fun rebuild(configProvider: () -> S3Config?)
}

/**
 * [StorageRepository] 默认实现。
 *
 * @param configProvider 每次调用实时读取当前 S3 配置（null = 未配置）
 * @param httpClient 复用的 OkHttp 客户端
 * @param listCache 对象列表缓存
 * @param thumbnailCache 缩略图缓存
 * @param connectivity 网络状态（可空；用于离线降级）
 * @param ioDispatcher 网络/IO 调度器
 */
class StorageRepositoryImpl(
    configProvider: () -> S3Config?,
    private val httpClient: OkHttpClient,
    private val listCache: ObjectListCache,
    private val thumbnailCache: ThumbnailCache,
    private val connectivity: ConnectivityMonitor? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : StorageRepository {

    @Volatile
    private var provider: () -> S3Config? = configProvider

    private fun currentConfig(): S3Config? = provider()

    private fun clientOf(config: S3Config): S3Client = S3ClientImpl(config, httpClient, ioDispatcher)

    override suspend fun rebuild(configProvider: () -> S3Config?) {
        provider = configProvider
    }

    override suspend fun listObjects(
        prefix: String,
        forceRefresh: Boolean,
        continuationToken: String?,
        pageSize: Int
    ): ListResult = withContext(ioDispatcher) {
        val config = currentConfig()
            ?: return@withContext ListResult(
                emptyList(), null, false, false, null, offline = false, configured = false
            )
        val s3 = clientOf(config)
        val bucket = config.bucket
        val normalized = PathUtils.normalizePrefix(prefix)
        val isFirstPage = continuationToken == null

        if (isFirstPage && !forceRefresh) {
            val cached = listCache.get(bucket, normalized)
            if (cached != null) {
                return@withContext ListResult(
                    objects = cached.objects,
                    nextContinuationToken = null,
                    isTruncated = false,
                    fromCache = true,
                    cachedAt = cached.cachedAt,
                    offline = connectivity?.isOnline?.value == false
                )
            }
        }

        val page = try {
            s3.listObjectsV2(normalized, continuationToken, DELIMITER, pageSize)
        } catch (t: Throwable) {
            // 网络失败时若首屏有缓存，降级返回缓存并标记离线
            if (isFirstPage) {
                val cached = listCache.get(bucket, normalized)
                if (cached != null) {
                    return@withContext ListResult(
                        cached.objects, null, false, true, cached.cachedAt, offline = true
                    )
                }
            }
            throw t
        }

        if (isFirstPage) {
            if (!page.isTruncated) {
                // 整目录一次取回：写入缓存，并按远端真实列表裁剪缓存与缩略图
                listCache.put(bucket, normalized, page.objects)
                pruneCaches(bucket, normalized, page.objects)
            }
        }

        ListResult(
            objects = page.objects,
            nextContinuationToken = page.nextContinuationToken,
            isTruncated = page.isTruncated,
            fromCache = false,
            cachedAt = null,
            offline = false
        )
    }

    /** 强制刷新后，按远端真实列表清理已删除的文件/文件夹缓存（R-35 配套）。 */
    private suspend fun pruneCaches(bucket: String, prefix: String, objects: List<ObjectInfo>) {
        val liveFolders = objects.filter { it.isFolder }.map { it.key }
        val liveKeys = objects.filter { !it.isFolder }.map { it.key }
        listCache.pruneMissingFolders(bucket, prefix, liveFolders)
        thumbnailCache.pruneMissingObjects(bucket, prefix, liveKeys, liveFolders)
    }

    override suspend fun loadThumbnail(meta: ThumbnailMeta): ByteArray? =
        thumbnailCache.get(meta)

    override suspend fun preview(key: String): PreviewData = withContext(ioDispatcher) {
        val config = currentConfig()
            ?: throw IllegalStateException("S3 未配置")
        val s3 = clientOf(config)
        val kind = FileTypes.kindOf(key)
        val (stream, meta) = s3.openStream(key)

        when (kind) {
            PreviewKind.TEXT -> {
                if (meta.contentLength > TransferConstants.TEXT_PREVIEW_MAX_BYTES) {
                    stream.close()
                    PreviewData.TooLarge(meta, kind)
                } else {
                    val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
                    PreviewData.Text(text, meta)
                }
            }
            PreviewKind.PDF -> {
                if (meta.contentLength > TransferConstants.PDF_PREVIEW_MAX_BYTES) {
                    stream.close()
                    PreviewData.TooLarge(meta, kind)
                } else {
                    PreviewData.Binary(stream, meta)
                }
            }
            else -> PreviewData.Binary(stream, meta)
        }
    }

    override suspend fun head(key: String): ObjectMeta = withContext(ioDispatcher) {
        val config = currentConfig() ?: throw IllegalStateException("S3 未配置")
        clientOf(config).headObject(key)
    }

    override suspend fun delete(key: String) = withContext(ioDispatcher) {
        val config = currentConfig() ?: throw IllegalStateException("S3 未配置")
        clientOf(config).deleteObject(key)
        invalidateForKey(config.bucket, key)
    }

    override suspend fun deleteBatch(keys: List<String>): BatchDeleteResult =
        withContext(ioDispatcher) {
            val config = currentConfig() ?: throw IllegalStateException("S3 未配置")
            val result = clientOf(config).deleteObjects(keys)
            for (key in keys) {
                invalidateForKey(config.bucket, key)
            }
            result
        }

    override suspend fun openObjectStream(key: String): Pair<InputStream, ObjectMeta> =
        withContext(ioDispatcher) {
            val config = currentConfig() ?: throw IllegalStateException("S3 未配置")
            clientOf(config).openStream(key)
        }

    override fun publicUrl(key: String): String {
        val config = currentConfig() ?: return ""
        val base = config.normalizedPublicUrl.ifEmpty { config.normalizedEndpoint }
        return UrlUtils.joinPublicUrl(base, key)
    }

    /** 写操作后失效「该文件所在目录 + 全部上级目录」。 */
    private suspend fun invalidateForKey(bucket: String, key: String) {
        val dir = key.substringBeforeLast('/', "")
        val prefixes = PathUtils.parentPrefixes(PathUtils.normalizePrefix(dir)).toSet()
        listCache.invalidate(bucket, prefixes)
    }

    private companion object {
        const val DELIMITER = "/"
    }
}
