package com.r2manager.android.core.util

import java.util.Locale

/**
 * 字节 / 速度 / 剩余时间格式化（列表与传输中心共用）。
 */
object ByteFormat {

    private const val UNIT = 1024.0
    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    /**
     * 人类可读大小，如 `1.2 MB`。
     *
     * @param bytes 字节数（<=0 返回 "0 B"）
     */
    fun size(bytes: Long): String {
        if (bytes <= 0) {
            return "0 B"
        }
        var value = bytes.toDouble()
        var index = 0
        while (value >= UNIT && index < UNITS.size - 1) {
            value /= UNIT
            index++
        }
        return if (index == 0) {
            "${bytes} ${UNITS[index]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, UNITS[index])
        }
    }

    /**
     * 传输速度，如 `1.2 MB/s`。
     *
     * @param bytesPerSec 每秒字节数
     */
    fun speed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) {
            return "0 B/s"
        }
        return size(bytesPerSec) + "/s"
    }

    /**
     * 剩余时间，如 `1 分 20 秒` / `2 时 5 分`。
     *
     * @param seconds 剩余秒数（<0 返回空串）
     */
    fun eta(seconds: Long): String {
        if (seconds < 0) {
            return ""
        }
        if (seconds < 60) {
            return "$seconds 秒"
        }
        val minutes = seconds / 60
        if (minutes < 60) {
            val rem = seconds % 60
            return if (rem == 0L) "$minutes 分" else "$minutes 分 $rem 秒"
        }
        val hours = minutes / 60
        val remMinutes = minutes % 60
        return if (remMinutes == 0L) "$hours 时" else "$hours 时 $remMinutes 分"
    }
}
