package com.r2manager.android.data.local.cache

import android.content.Context
import com.r2manager.android.core.constants.CacheConstants
import com.r2manager.android.domain.model.ObjectInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 列表缓存命中结果。 */
data class CachedList(val objects: List<ObjectInfo>, val cachedAt: Long)

/** 列表缓存统计。 */
data class ListCacheStats(
    val enabled: Boolean,
    val count: Int,
    val usedBytes: Long,
    val directory: String
)

/**
 * 对象列表缓存（bucket + prefix 维度，加密落盘）。
 *
 * - 缓存键 = `sha1(bucket \0 prefix)`；缓存实体含对象数组与 `cachedAt`
 * - 非强制刷新时优先返回缓存（上层置 `fromCache = true`）
 * - 写操作后按「该目录 + 全部上级」精确失效（[invalidate]），**不整桶失效**
 * - 条目数（默认 300）与总字节（默认 64MB）双上限，按 LRU 淘汰
 */
interface ObjectListCache {
    suspend fun get(bucket: String, prefix: String): CachedList?
    suspend fun put(bucket: String, prefix: String, objects: List<ObjectInfo>): Boolean

    /** 失效指定目录集合（调用方传入 `PathUtils.parentPrefixes(prefix)`，含 `""`）。 */
    suspend fun invalidate(bucket: String, prefixes: Set<String>): Int

    suspend fun pruneMissingFolders(bucket: String, prefix: String, liveFolders: List<String>): Int
    suspend fun purgeBuckets(validBuckets: List<String>): Int
    suspend fun clear(): Pair<Int, Long>
    suspend fun stats(): ListCacheStats
    fun configure(enabled: Boolean)
}

/**
 * [ObjectListCache] 默认实现。
 *
 * @param context 应用上下文（缓存根目录 = `files/cache`）
 * @param fileStore 加密文件存取
 * @param keyProvider 当前密钥（null 表示未解锁，禁用缓存读写）；通常传 `{ keyManager.currentKey() }`
 */
class ObjectListCacheImpl(
    context: Context,
    private val fileStore: CryptoFileStore,
    private val keyProvider: () -> ByteArray?
) : ObjectListCache {

    private val rootDir: File = CachePaths.root(context.applicationContext)
    private val index = LruIndex(File(rootDir, CachePaths.LIST_INDEX))
    private val mutex = Mutex()

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var ready: Boolean = false

    private fun canUseCache(): Boolean = enabled && keyProvider() != null

    private fun ensureReady() {
        if (ready) {
            return
        }
        CachePaths.ensureDirs(rootDir)
        index.load()
        deleteFiles(index.evict(MAX_ENTRIES, CacheConstants.LIST_CACHE_DEFAULT_MAX_BYTES))
        index.save()
        ready = true
    }

    override suspend fun get(bucket: String, prefix: String): CachedList? = mutex.withLock {
        if (!canUseCache()) {
            return@withLock null
        }
        ensureReady()
        val normalizedPrefix = normalizePrefix(prefix)
        val id = CacheKeyFactory.listId(bucket, normalizedPrefix)
        val entry = index.get(id) ?: return@withLock null
        val bytes = fileStore.read(entry.file)
        if (bytes == null) {
            index.remove(id)
            index.save()
            return@withLock null
        }
        val parsed = runCatching { parsePayload(bytes) }.getOrNull()
        if (parsed == null) {
            index.remove(id)
            index.save()
            return@withLock null
        }
        parsed
    }

    override suspend fun put(bucket: String, prefix: String, objects: List<ObjectInfo>): Boolean =
        mutex.withLock {
            if (!canUseCache()) {
                return@withLock false
            }
            ensureReady()
            val normalizedPrefix = normalizePrefix(prefix)
            val id = CacheKeyFactory.listId(bucket, normalizedPrefix)
            val relPath = CachePaths.listRelPath(id)
            val cachedAt = System.currentTimeMillis()
            val payload = buildPayload(bucket, normalizedPrefix, objects, cachedAt)
            if (!fileStore.write(relPath, payload.toByteArray(Charsets.UTF_8))) {
                return@withLock false
            }
            index.put(
                LruIndex.Entry(
                    id = id,
                    file = relPath,
                    bytes = fileStore.size(relPath),
                    cachedAt = cachedAt,
                    accessedAt = cachedAt,
                    metadata = linkedMapOf(
                        META_BUCKET to bucket,
                        META_PREFIX to normalizedPrefix,
                        META_COUNT to objects.size.toString()
                    )
                )
            )
            deleteFiles(index.evict(MAX_ENTRIES, CacheConstants.LIST_CACHE_DEFAULT_MAX_BYTES))
            index.save()
            true
        }

    override suspend fun invalidate(bucket: String, prefixes: Set<String>): Int = mutex.withLock {
        ensureReady()
        val normalized = prefixes.map { normalizePrefix(it) }.toHashSet()
        val toRemove = index.where { entry ->
            entry.metadata[META_BUCKET] == bucket &&
                normalizePrefix(entry.metadata[META_PREFIX].orEmpty()) in normalized
        }
        removeEntries(toRemove)
    }

    override suspend fun pruneMissingFolders(
        bucket: String,
        prefix: String,
        liveFolders: List<String>
    ): Int = mutex.withLock {
        ensureReady()
        val base = normalizePrefix(prefix)
        val baseWithSlash = if (base.isEmpty()) "" else "$base/"
        val live = liveFolders.map { withSlash(it) }.toHashSet()

        val toRemove = index.where { entry ->
            if (entry.metadata[META_BUCKET] != bucket) {
                return@where false
            }
            val entryPrefix = normalizePrefix(entry.metadata[META_PREFIX].orEmpty())
            val entryWithSlash = if (entryPrefix.isEmpty()) "" else "$entryPrefix/"
            if (entryWithSlash == baseWithSlash || !entryWithSlash.startsWith(baseWithSlash)) {
                return@where false
            }
            val firstSegment = entryWithSlash.substring(baseWithSlash.length).split('/')[0]
            if (firstSegment.isEmpty()) {
                return@where false
            }
            val topFolder = "$baseWithSlash$firstSegment/"
            topFolder !in live
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

    override suspend fun stats(): ListCacheStats = mutex.withLock {
        ensureReady()
        ListCacheStats(
            enabled = enabled,
            count = index.count(),
            usedBytes = index.totalBytes(),
            directory = rootDir.absolutePath
        )
    }

    override fun configure(enabled: Boolean) {
        this.enabled = enabled
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

    private fun buildPayload(
        bucket: String,
        prefix: String,
        objects: List<ObjectInfo>,
        cachedAt: Long
    ): String {
        val array = JSONArray()
        for (info in objects) {
            val item = JSONObject()
                .put("key", info.key)
                .put("name", info.name)
                .put("isFolder", info.isFolder)
                .put("size", info.size)
            info.lastModifiedIso?.let { item.put("lastModifiedIso", it) }
            info.contentType?.let { item.put("contentType", it) }
            info.etag?.let { item.put("etag", it) }
            array.put(item)
        }
        return JSONObject()
            .put("bucket", bucket)
            .put("prefix", prefix)
            .put("cachedAt", cachedAt)
            .put("objects", array)
            .toString()
    }

    private fun parsePayload(bytes: ByteArray): CachedList? {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val array = root.optJSONArray("objects") ?: return null
        val objects = ArrayList<ObjectInfo>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            objects.add(
                ObjectInfo(
                    key = item.optString("key"),
                    name = item.optString("name"),
                    isFolder = item.optBoolean("isFolder", false),
                    size = item.optLong("size", 0L),
                    lastModifiedIso = item.optString("lastModifiedIso").takeIf { it.isNotEmpty() },
                    contentType = item.optString("contentType").takeIf { it.isNotEmpty() },
                    etag = item.optString("etag").takeIf { it.isNotEmpty() }
                )
            )
        }
        val cachedAt = root.optLong("cachedAt", System.currentTimeMillis())
        return CachedList(objects, cachedAt)
    }

    private fun normalizePrefix(prefix: String?): String = prefix.orEmpty().trimEnd('/')

    private fun withSlash(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.isEmpty() || trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private companion object {
        const val MAX_ENTRIES = CacheConstants.LIST_CACHE_DEFAULT_MAX_ENTRIES
        const val META_BUCKET = "bucket"
        const val META_PREFIX = "prefix"
        const val META_COUNT = "count"
    }
}
