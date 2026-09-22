package com.r2manager.android.data.remote.s3

import com.r2manager.android.core.constants.TransferConstants
import kotlin.math.ceil

/**
 * Multipart 分片规划：与桌面版 `R2Client._getMultipartPartSize` 逐字对齐。
 *
 * `partSize = max(64MB, ceil(size / 10000 / 5MB) * 5MB)`，保证分片数不超过 [TransferConstants.MAX_MULTIPART_PARTS]。
 */
object MultipartPlan {

    /**
     * 计算分片大小。
     *
     * @param totalBytes 对象总字节数
     * @return 单分片字节数，至少 [TransferConstants.DEFAULT_MULTIPART_PART_SIZE]（64MB）
     */
    fun partSize(totalBytes: Long): Long {
        val defaultPartSize = TransferConstants.DEFAULT_MULTIPART_PART_SIZE
        val minPartSize = TransferConstants.MIN_MULTIPART_PART_SIZE
        val maxParts = TransferConstants.MAX_MULTIPART_PARTS
        // 与 JS 的浮点除法语义一致：ceil(size / maxParts / minPart) * minPart
        val sizeForPartLimit = ceil(
            totalBytes.toDouble() / maxParts.toDouble() / minPartSize.toDouble()
        ).toLong() * minPartSize
        return maxOf(defaultPartSize, sizeForPartLimit)
    }

    /**
     * 计算分片数量（向上取整）。
     *
     * @param totalBytes 对象总字节数
     * @param partSize 单分片字节数（通常来自 [partSize]）
     * @return 分片数；`partSize <= 0` 或 `totalBytes <= 0` 时返回 0
     */
    fun partCount(totalBytes: Long, partSize: Long): Int {
        if (partSize <= 0L || totalBytes <= 0L) {
            return 0
        }
        return ceil(totalBytes.toDouble() / partSize.toDouble()).toInt()
    }

    /**
     * 判断是否应走 Multipart（严格大于 300MB）。
     *
     * @param totalBytes 对象总字节数
     * @return 超过 [TransferConstants.LARGE_UPLOAD_THRESHOLD_BYTES] 返回 true
     */
    fun shouldUseMultipart(totalBytes: Long): Boolean =
        totalBytes > TransferConstants.LARGE_UPLOAD_THRESHOLD_BYTES

    /**
     * 计算指定分片的长度（最后一片可能更短）。
     *
     * @param totalBytes 对象总字节数
     * @param partSize 单分片字节数
     * @param partNumber 分片序号（从 1 开始）
     * @return 该分片的字节数
     */
    fun partLength(totalBytes: Long, partSize: Long, partNumber: Int): Long {
        if (partNumber <= 0 || partSize <= 0L) {
            return 0L
        }
        val offset = partSize * (partNumber - 1).toLong()
        if (offset >= totalBytes) {
            return 0L
        }
        return minOf(partSize, totalBytes - offset)
    }
}
