package com.r2manager.android.data.local.cache

import android.content.Context
import com.r2manager.android.core.constants.CacheConstants
import com.r2manager.android.domain.model.ThumbnailMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** 缩略图缓存统计。 */
data class ThumbnailCacheStats(
    val enabled: Boolean,
    val maxBytes: Long,
    val usedBytes: Long,
    val count: Int,
    val directory: String
)

/**
 * 缩略图落盘缓存（加密）。
 *
 * - 缓存键 = bucket + key + size + lastModified + etag（见 [CacheKeyFactory.thumbId]）
 * - 字节上限（默认 512MB）内按 LRU 淘汰
 * - 无密钥时读返回 null、写返回 false（不落明文）
 */
interface ThumbnailCache {
    suspend fun get(meta: ThumbnailMeta): ByteArray?
    suspend fun put(meta: ThumbnailMeta, bytes: ByteArray, contentType: String = "image/jpeg"): Boolean
    suspend fun pruneMissingObjects(
        bucket: String,
        prefix: String,
        liveKeys: List<String>,
        liveFolders: List<String>
    ): Int

    suspend fun purgeBuckets(validBuckets: List<String>): Int
    suspend fun clear(): Pair<Int, Long>
    suspend fun stats(): ThumbnailCacheStats
    fun configure(enabled: Boolean, maxBytes: Long)
}

/**
 * [ThumbnailCache] 默认实现。
 *
 * @param context 应用上下文（缓存根目录 = `files/cache`）
 * @param fileStore 加密文件存取
 * @param keyProvider 当前密钥（null 表示未解锁，禁用缓存读写）；通常传 `{ keyManager.currentKey() }`
 */
class ThumbnailCacheImpl(
    context: Context,
    private val fileStore: CryptoFileStore,
    private val keyProvider: () -> ByteArray?
) : ThumbnailCache {

    private val rootDir: File = CachePaths.root(context.applicationContext)
    private val index = LruIndex(File(rootDir, CachePaths.THUMB_INDEX))
    private val mutex = Mutex()

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var maxBytes: Long = CacheConstants.DEFAULT_THUMBNAIL_MAX_BYTES

    @Volatile
    private var ready: Boolean = false

    private fun canUseCache(): Boolean = enabled && keyProvider() != null

    private fun ensureReady() {
        if (ready) {
            return
        }
        CachePaths.ensureDirs(rootDir)
        index.load()
        deleteFiles(index.evict(MAX_ENTRIES, maxBytes))
        index.save()
        ready = true
    }

    override suspend fun get(meta: ThumbnailMeta): ByteArray? = mutex.withLock {
        if (!canUseCache()) {
            return@withLock null
        }
        ensureReady()
        val id = CacheKeyFactory.thumbId(meta)
        val entry = index.get(id) ?: return@withLock null
        val bytes = fileStore.read(entry.file)
        if (bytes == null) {
            // 密钥不符或文件损坏：丢弃该条目
            index.remove(id)
            index.save()
            return@withLock null
        }
        bytes
    }

    override suspend fun put(
        meta: ThumbnailMeta,
        bytes: ByteArray,
        contentType: String
    ): Boolean = mutex.withLock {
        if (!canUseCache()) {
            return@withLock false
        }
        ensureReady()
        val id = CacheKeyFactory.thumbId(meta)
        val relPath = CachePaths.thumbRelPath(id)
        if (!fileStore.write(relPath, bytes)) {
            return@withLock false
        }
        val storedBytes = fileStore.size(relPath)
        val now = System.currentTimeMillis()
        index.put(
            LruIndex.Entry(
                id = id,
                file = relPath,
                bytes = storedBytes,
                cachedAt = now,
                accessedAt = now,
                metadata = linkedMapOf(
                    META_BUCKET to meta.bucket,
                    META_KEY to meta.key,
                    META_CONTENT_TYPE to contentType
                )
            )
        )
        deleteFiles(index.evict(MAX_ENTRIES, maxBytes))
        index.save()
        true
    }

    override suspend fun pruneMissingObjects(
        bucket: String,
        prefix: String,
        liveKeys: List<String>,
        liveFolders: List<String>
    ): Int = mutex.withLock {
        ensureReady()
        val base = normalizePrefix(prefix)
        val baseWithSlash = if (base.isEmpty()) "" else "$base/"
        val liveKeySet = liveKeys.toHashSet()
        val liveFolderSet = liveFolders.map { normalizeFolder(it) }.toHashSet()

        val toRemove = index.where { entry ->
            if (entry.metadata[META_BUCKET] != bucket) {
                return@where false
            }
            val key = entry.metadata[META_KEY] ?: return@where false
            if (!key.startsWith(baseWithSlash) || key == baseWithSlash) {
                return@where false
            }
            val rel = key.substring(baseWithSlash.length)
            val slash = rel.indexOf('/')
            if (slash < 0) {
                key !in liveKeySet
            } else {
                val topFolder = baseWithSlash + rel.substring(0, slash) + "/"
                topFolder !in liveFolderSet
            }
        }
        removeEntries(toRemove)
    }

    override suspend fun purgeBuckets(validBuckets: List<String>): Int = mutex.withLock {
        ensureReady()
        val valid = validBuckets.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
        if (valid.isEmpty()) {
            return@withLock 0
        }
        val toRemove = index.where { entry ->
            val bucket = entry.metadata[META_BUCKET].orEmpty()
            bucket.isNotEmpty() && bucket !in valid
        }
        removeEntries(toRemove)
    }

    override suspend fun clear(): Pair<Int, Long> = mutex.withLock {
        ensureReady()
        val entries = index.clear()
        var freed = 0L
        for (entry in entries) {
            freed += entry.bytes
            fileStore.delete(entry.file)
        }
        index.save()
        entries.size to freed
    }

    override suspend fun stats(): ThumbnailCacheStats = mutex.withLock {
        ensureReady()
        ThumbnailCacheStats(
            enabled = enabled,
            maxBytes = maxBytes,
            usedBytes = index.totalBytes(),
            count = index.count(),
            directory = rootDir.absolutePath
        )
    }

    override fun configure(enabled: Boolean, maxBytes: Long) {
        this.enabled = enabled
        if (maxBytes > 0) {
            this.maxBytes = maxBytes
        }
    }

    private fun removeEntries(entries: List<LruIndex.Entry>): Int {
        if (entries.isEmpty()) {
            return 0
        }
        for (entry in entries) {
            index.remove(entry.id)
            fileStore.delete(entry.file)
        }
        index.save()
        return entries.size
    }

    private fun deleteFiles(entries: List<LruIndex.Entry>) {
        for (entry in entries) {
            fileStore.delete(entry.file)
        }
    }

    private fun normalizePrefix(prefix: String?): String = prefix.orEmpty().trimEnd('/')

    private fun normalizeFolder(folder: String): String {
        val trimmed = folder.trim()
        return if (trimmed.isEmpty() || trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private companion object {
        const val MAX_ENTRIES = Int.MAX_VALUE
        const val META_BUCKET = "bucket"
        const val META_KEY = "key"
        const val META_CONTENT_TYPE = "contentType"
    }
}
