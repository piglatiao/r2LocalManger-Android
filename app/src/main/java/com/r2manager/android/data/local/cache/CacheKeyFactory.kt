package com.r2manager.android.data.local.cache

import com.r2manager.android.domain.model.ThumbnailMeta
import java.security.MessageDigest

/**
 * 缓存键工厂（与桌面版一致，**不得改**）。
 *
 * - 缩略图：`sha1(bucket \0 key \0 size \0 lastModifiedIso \0 etag)`
 * - 列表：`sha1(bucket \0 prefix)`
 *
 * 用 `\0` 作为分隔符，避免不同字段拼接产生歧义。
 */
object CacheKeyFactory {

    private const val SEP = '\u0000'

    /** 缩略图缓存键。 */
    fun thumbId(meta: ThumbnailMeta): String {
        val raw = buildString {
            append(meta.bucket)
            append(SEP)
            append(meta.key)
            append(SEP)
            append(meta.size)
            append(SEP)
            append(meta.lastModifiedIso ?: "")
            append(SEP)
            append(meta.etag ?: "")
        }
        return sha1Hex(raw)
    }

    /** 对象列表缓存键。 */
    fun listId(bucket: String, prefix: String): String {
        val raw = buildString {
            append(bucket)
            append(SEP)
            append(prefix)
        }
        return sha1Hex(raw)
    }

    /** SHA-1 十六进制小写摘要。 */
    fun sha1Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
