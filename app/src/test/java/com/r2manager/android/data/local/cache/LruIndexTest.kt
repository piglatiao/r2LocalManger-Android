package com.r2manager.android.data.local.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 缓存 LRU 索引单测：仅覆盖内存淘汰逻辑（不触碰 JSON 序列化，后者需设备环境）。
 */
class LruIndexTest {

    private fun newIndex(): LruIndex =
        LruIndex(File.createTempFile("lru_index", ".json").apply { delete() })

    @Test
    fun evictsLeastRecentlyUsedWhenBytesExceedLimit() {
        val index = newIndex()
        index.put(entry("a", bytes = 40, accessedAt = 100))
        index.put(entry("b", bytes = 40, accessedAt = 200))
        index.put(entry("c", bytes = 40, accessedAt = 300))

        val evicted = index.evict(maxEntries = 100, maxBytes = 100)

        assertEquals(listOf("a"), evicted.map { it.id })
        assertEquals(2, index.count())
        assertEquals(80L, index.totalBytes())
        assertNull(index.peek("a"))
    }

    @Test
    fun evictsWhenEntryCountExceedsLimit() {
        val index = newIndex()
        index.put(entry("a", bytes = 1, accessedAt = 100))
        index.put(entry("b", bytes = 1, accessedAt = 200))
        index.put(entry("c", bytes = 1, accessedAt = 300))

        val evicted = index.evict(maxEntries = 2, maxBytes = Long.MAX_VALUE)

        assertEquals(listOf("a"), evicted.map { it.id })
        assertEquals(2, index.count())
    }

    @Test
    fun getTouchesAccessedAtSoEntrySurvivesEviction() {
        val index = newIndex()
        index.put(entry("a", bytes = 1, accessedAt = 100))
        index.put(entry("b", bytes = 1, accessedAt = 200))

        index.get("a", now = 500)

        val evicted = index.evict(maxEntries = 1, maxBytes = Long.MAX_VALUE)
        assertEquals(listOf("b"), evicted.map { it.id })
        assertTrue(index.peek("a") != null)
    }

    @Test
    fun noEvictionWhenWithinLimits() {
        val index = newIndex()
        index.put(entry("a", bytes = 10, accessedAt = 100))
        assertTrue(index.evict(maxEntries = 10, maxBytes = 100).isEmpty())
    }

    @Test
    fun removeAndClear() {
        val index = newIndex()
        index.put(entry("a", bytes = 10, accessedAt = 100))
        index.put(entry("b", bytes = 20, accessedAt = 200))

        assertEquals(20L, index.remove("b")?.bytes)
        assertNull(index.peek("b"))

        val cleared = index.clear()
        assertEquals(listOf("a"), cleared.map { it.id })
        assertEquals(0, index.count())
    }

    @Test
    fun whereFiltersByMetadata() {
        val index = newIndex()
        index.put(entry("a", bytes = 1, accessedAt = 1, metadata = mapOf("bucket" to "x")))
        index.put(entry("b", bytes = 1, accessedAt = 2, metadata = mapOf("bucket" to "y")))

        val result = index.where { it.metadata["bucket"] == "x" }
        assertEquals(listOf("a"), result.map { it.id })
    }

    private fun entry(
        id: String,
        bytes: Long,
        accessedAt: Long,
        metadata: Map<String, String> = emptyMap()
    ) = LruIndex.Entry(
        id = id,
        file = "$id.bin",
        bytes = bytes,
        cachedAt = accessedAt,
        accessedAt = accessedAt,
        metadata = LinkedHashMap(metadata)
    )
}
