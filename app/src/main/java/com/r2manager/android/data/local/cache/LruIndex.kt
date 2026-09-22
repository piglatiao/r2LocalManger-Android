package com.r2manager.android.data.local.cache

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 通用缓存索引：内存映射 + JSON 落盘 + LRU 淘汰。
 *
 * 复刻桌面版 `index.json` 思路：`{version, updatedAt, entries:[{id,file,bytes,cachedAt,accessedAt,meta}]}`。
 * 业务字段（bucket/prefix/count/contentType/…）统一放进 [Entry.metadata]。
 *
 * 淘汰策略：条目数或总字节任一超限，按 `accessedAt` 升序（最久未使用）淘汰，直到达标。
 */
class LruIndex(private val indexFile: File) {

    /** 单个索引条目。 */
    class Entry(
        val id: String,
        val file: String,
        val bytes: Long,
        val cachedAt: Long,
        var accessedAt: Long,
        val metadata: MutableMap<String, String> = LinkedHashMap()
    )

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun load() {
        entries.clear()
        if (!indexFile.isFile) {
            return
        }
        val raw = runCatching { indexFile.readText(Charsets.UTF_8) }.getOrNull() ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val array: JSONArray = root.optJSONArray("entries") ?: return
        val baseDir = indexFile.parentFile
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val file = item.optString("file")
            if (id.isEmpty() || file.isEmpty()) {
                continue
            }
            val stored = if (baseDir != null) File(baseDir, file) else File(file)
            if (!stored.isFile) {
                continue
            }
            val meta = LinkedHashMap<String, String>()
            item.optJSONObject("meta")?.let { metaObj ->
                val keys = metaObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    meta[key] = metaObj.optString(key)
                }
            }
            val cachedAt = item.optLong("cachedAt", 0L)
            entries[id] = Entry(
                id = id,
                file = file,
                bytes = stored.length().takeIf { it > 0 } ?: item.optLong("bytes", 0L),
                cachedAt = cachedAt,
                accessedAt = item.optLong("accessedAt", cachedAt),
                metadata = meta
            )
        }
    }

    @Synchronized
    fun save() {
        val array = JSONArray()
        for (entry in entries.values) {
            val item = JSONObject()
                .put("id", entry.id)
                .put("file", entry.file)
                .put("bytes", entry.bytes)
                .put("cachedAt", entry.cachedAt)
                .put("accessedAt", entry.accessedAt)
            if (entry.metadata.isNotEmpty()) {
                // 不用 JSONObject(Map) 构造器：其形参是 Java 原生类型 Map，
                // K2 会把它解析为 (Mutable)Map<Any?, Any?>，而 MutableMap<String, String>
                // 因不可变型变不匹配。逐个 put 可避开原生类型，也无需强转。
                val meta = JSONObject()
                entry.metadata.forEach { (key, value) -> meta.put(key, value) }
                item.put("meta", meta)
            }
            array.put(item)
        }
        val root = JSONObject()
            .put("version", INDEX_VERSION)
            .put("updatedAt", System.currentTimeMillis())
            .put("entries", array)
        runCatching {
            indexFile.parentFile?.mkdirs()
            indexFile.writeText(root.toString(), Charsets.UTF_8)
        }
    }

    /** 访问（更新 accessedAt）。 */
    @Synchronized
    fun get(id: String, now: Long = System.currentTimeMillis()): Entry? {
        val entry = entries[id] ?: return null
        entry.accessedAt = now
        return entry
    }

    @Synchronized
    fun peek(id: String): Entry? = entries[id]

    @Synchronized
    fun put(entry: Entry) {
        entries[entry.id] = entry
    }

    @Synchronized
    fun remove(id: String): Entry? = entries.remove(id)

    @Synchronized
    fun all(): List<Entry> = entries.values.toList()

    @Synchronized
    fun where(predicate: (Entry) -> Boolean): List<Entry> = entries.values.filter(predicate)

    @Synchronized
    fun count(): Int = entries.size

    @Synchronized
    fun totalBytes(): Long = entries.values.sumOf { it.bytes }

    /**
     * 按 LRU 淘汰到上限以内。
     * @return 被淘汰的条目（调用方负责删除对应文件）
     */
    @Synchronized
    fun evict(maxEntries: Int, maxBytes: Long): List<Entry> {
        var used = totalBytes()
        val overLimit: () -> Boolean = { entries.size > maxEntries || used > maxBytes }
        if (!overLimit()) {
            return emptyList()
        }
        val candidates = entries.values.sortedBy { it.accessedAt }
        val evicted = ArrayList<Entry>()
        for (entry in candidates) {
            if (!overLimit()) {
                break
            }
            entries.remove(entry.id)
            used -= entry.bytes
            evicted.add(entry)
        }
        return evicted
    }

    /** 清空索引，返回全部原条目。 */
    @Synchronized
    fun clear(): List<Entry> {
        val all = entries.values.toList()
        entries.clear()
        return all
    }

    private companion object {
        const val INDEX_VERSION = 1
    }
}
