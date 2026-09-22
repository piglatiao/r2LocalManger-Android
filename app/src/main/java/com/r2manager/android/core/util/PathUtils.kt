package com.r2manager.android.core.util

/**
 * 对象 key / 目录前缀工具（缓存键与列表逻辑共用）。
 *
 * 约定：根目录前缀为 `""`，缓存/传输 key 统一用 [normalizePrefix] 去尾 `/`。
 */
object PathUtils {

    /**
     * 规整目录前缀：去尾部 `/`（根目录返回 `""`）。
     *
     * @param prefix 原始前缀
     */
    fun normalizePrefix(prefix: String?): String = prefix?.trim().orEmpty().trimEnd('/')

    /**
     * 拼接完整 key。
     *
     * @param prefix 目录前缀（可为空串）
     * @param name 末段名
     */
    fun joinKey(prefix: String, name: String): String {
        val p = normalizePrefix(prefix)
        val n = name.trim().trim('/')
        if (p.isEmpty()) {
            return n
        }
        if (n.isEmpty()) {
            return p
        }
        return "$p/$n"
    }

    /**
     * 返回该目录 + 全部上级前缀（含 `""`），用于写操作后的精确缓存失效（R-35）。
     *
     * 例：`"a/b/c"` → `["a/b/c", "a/b", "a", ""]`。
     *
     * @param prefix 目录前缀
     */
    fun parentPrefixes(prefix: String): List<String> {
        val normalized = normalizePrefix(prefix)
        val result = ArrayList<String>()
        if (normalized.isEmpty()) {
            result.add("")
            return result
        }
        var current = normalized
        result.add(current)
        while (true) {
            val slash = current.lastIndexOf('/')
            if (slash < 0) {
                break
            }
            current = current.substring(0, slash)
            result.add(current)
        }
        result.add("")
        return result
    }

    /**
     * 面包屑分段：返回逐级累积的完整前缀列表，便于按段导航。
     *
     * 例：`"a/b/c"` → `["a", "a/b", "a/b/c"]`；根目录 → `[]`。
     *
     * @param prefix 目录前缀
     */
    fun breadcrumbSegments(prefix: String): List<String> {
        val normalized = normalizePrefix(prefix)
        if (normalized.isEmpty()) {
            return emptyList()
        }
        val parts = normalized.split('/').filter { it.isNotEmpty() }
        val result = ArrayList<String>(parts.size)
        val sb = StringBuilder()
        for (part in parts) {
            if (sb.isNotEmpty()) {
                sb.append('/')
            }
            sb.append(part)
            result.add(sb.toString())
        }
        return result
    }
}
