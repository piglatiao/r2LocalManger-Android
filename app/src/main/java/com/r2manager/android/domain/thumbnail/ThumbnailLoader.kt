package com.r2manager.android.domain.thumbnail

import android.util.Log
import com.r2manager.android.core.constants.TransferConstants
import com.r2manager.android.core.mime.FileTypes
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.data.local.cache.CacheKeyFactory
import com.r2manager.android.data.local.cache.ThumbnailCache
import com.r2manager.android.data.repository.StorageRepository
import com.r2manager.android.domain.model.ObjectInfo
import com.r2manager.android.domain.model.ThumbnailMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * 缩略图加载器（P3，R-11/R-12）。
 *
 * 流程：命中缓存直出 → 未命中则下载 → 生成 → 写缓存；文件夹/超大文件/缓存关闭时返回 null
 * （由 UI 改用类型图标 + 文案「文件过大，已跳过缩略图」）。
 *
 * 关键点：
 * - 缓存键参与 `size + lastModified(+etag)`（由 [CacheKeyFactory.thumbId] 保证，R-11 强校验）；
 * - 缩略图**加密落盘**由 [ThumbnailCache] 内部完成，本类只传明文；
 * - 同 key 并发请求去重（同 key 只下载一次），视频并发 ≤ [TransferConstants.VIDEO_THUMBNAIL_CONCURRENCY]。
 */
class ThumbnailLoader(
    private val repo: StorageRepository,
    private val cache: ThumbnailCache,
    private val generator: ThumbnailGenerator,
    private val video: VideoFrameExtractor
) {

    /** 缩略图与可用的视频总时长。 */
    data class Result(
        /** JPEG 缩略图字节。 */
        val bytes: ByteArray?,
        /** 视频总时长（毫秒）。 */
        val durationMs: Long?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val videoSemaphore = Semaphore(TransferConstants.VIDEO_THUMBNAIL_CONCURRENCY)
    private val inflightMutex = Mutex()
    private val inflight = HashMap<String, Deferred<Result>>()
    private val videoDurations = ConcurrentHashMap<String, Long>()

    @Volatile
    private var cacheEnabled: Boolean = true

    @Volatile
    private var cacheEnabledCheckedAt: Long = 0L

    /**
     * 加载缩略图和视频时长。
     * @param bucket 存储桶名称
     * @param info 文件信息
     * @return 缩略图字节与可用时长；不可用字段为空
     */
    suspend fun load(bucket: String, info: ObjectInfo): Result {
        if (info.isFolder) return Result(null, null)

        val kind = FileTypes.kindOf(info.key)
        if (kind != PreviewKind.IMAGE && kind != PreviewKind.VIDEO) return Result(null, null)
        if (kind == PreviewKind.IMAGE && info.size > TransferConstants.THUMBNAIL_SOURCE_MAX_BYTES) {
            return Result(null, null)
        }

        val meta = ThumbnailMeta(
            bucket = bucket,
            key = info.key,
            size = info.size,
            lastModifiedIso = info.lastModifiedIso,
            etag = info.etag
        )

        val cacheId = CacheKeyFactory.thumbId(meta)
        val cached = cache.get(meta)
        val duration = videoDurations[cacheId] ?: cache.getVideoDuration(meta)?.also {
            videoDurations[cacheId] = it
        }
        if (cached != null && (kind != PreviewKind.VIDEO || duration != null)) {
            return Result(cached, duration)
        }
        if (info.size > TransferConstants.THUMBNAIL_SOURCE_MAX_BYTES) return Result(cached, duration)
        if (!isCacheEnabled()) return Result(cached, duration)

        val existing = inflightMutex.withLock { inflight[cacheId] }
        if (existing != null) return existing.await()

        val deferred = scope.async { produce(meta, kind, cached) }
        inflightMutex.withLock { inflight[cacheId] = deferred }
        return try {
            deferred.await()
        } finally {
            inflightMutex.withLock { inflight.remove(cacheId) }
        }
    }

    private suspend fun produce(meta: ThumbnailMeta, kind: PreviewKind, cached: ByteArray?): Result = runCatching {
        val (stream, _) = repo.openObjectStream(meta.key)
        val result = when (kind) {
            PreviewKind.IMAGE -> Result(generator.generate(stream, meta.key, meta.size), null)
            PreviewKind.VIDEO -> videoSemaphore.withPermit {
                val videoResult = video.extractFirstFrame(stream)
                Result(cached ?: videoResult?.jpeg, videoResult?.durationMs)
            }
            else -> Result(null, null)
        }
        if (kind == PreviewKind.IMAGE && result.bytes != null) {
            cache.put(meta, result.bytes, "image/jpeg")
        } else if (kind == PreviewKind.VIDEO && cached == null && result.bytes != null) {
            cache.put(meta, result.bytes, "image/jpeg")
        }
        if (kind == PreviewKind.VIDEO && result.durationMs != null) {
            videoDurations[CacheKeyFactory.thumbId(meta)] = result.durationMs
            cache.putVideoDuration(meta, result.durationMs)
        }
        result
    }.onFailure { Log.w(TAG, "生成缩略图失败: ${meta.key}", it) }.getOrDefault(Result(cached, null))

    private suspend fun isCacheEnabled(): Boolean {
        val now = System.currentTimeMillis()
        if (now - cacheEnabledCheckedAt > CACHE_FLAG_TTL_MS) {
            cacheEnabled = runCatching { cache.stats().enabled }.getOrDefault(true)
            cacheEnabledCheckedAt = now
        }
        return cacheEnabled
    }

    private companion object {
        const val TAG = "ThumbnailLoader"
        const val CACHE_FLAG_TTL_MS = 1000L
    }
}
