package com.r2manager.android.core.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 时间格式化与 ISO-8601 互转。
 *
 * - 存储侧统一使用 ISO-8601 UTC 字符串（如 `2024-01-15T14:30:00Z`）；
 * - 展示侧按本地时区格式化为 `yyyy-MM-dd HH:mm`。
 */
object TimeFormat {

    private val DISPLAY_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    /**
     * epoch millis → ISO-8601 UTC 字符串。
     *
     * @param epochMillis 毫秒时间戳
     */
    fun toIso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    /**
     * ISO-8601 字符串 → epoch millis（解析失败返回 null）。
     *
     * @param iso ISO-8601 字符串
     */
    fun parseIso(iso: String?): Long? {
        val text = iso?.trim().orEmpty()
        if (text.isEmpty()) {
            return null
        }
        return try {
            Instant.parse(text).toEpochMilli()
        } catch (t: Throwable) {
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (t2: Throwable) {
                null
            }
        }
    }

    /**
     * ISO-8601 → 本地展示文案（解析失败返回空串）。
     *
     * @param iso ISO-8601 字符串
     */
    fun display(iso: String?): String {
        val millis = parseIso(iso) ?: return ""
        return DISPLAY_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }
}
