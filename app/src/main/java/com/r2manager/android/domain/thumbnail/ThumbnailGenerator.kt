package com.r2manager.android.domain.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.r2manager.android.core.constants.TransferConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 图片缩略图生成（P3，R-11）。
 *
 * 规则（对齐桌面版 `main.js` 的 `encodeThumbnailImage`）：
 * - 最长边 [TransferConstants.THUMBNAIL_MAX_EDGE_PX]（320px）；
 * - JPEG 质量 [TransferConstants.THUMBNAIL_JPEG_QUALITY]（80）；
 * - 源文件 > [TransferConstants.THUMBNAIL_SOURCE_MAX_BYTES]（64MB）**直接返回 null（跳过）**；
 * - 编码失败时，若原始内容 ≤ [TransferConstants.THUMBNAIL_RAW_FALLBACK_MAX_BYTES]（4MB）则原样返回，否则 null。
 */
class ThumbnailGenerator {

    /**
     * 从图片流生成 JPEG 缩略图。
     * @param source 图片字节流（本方法会关闭它）
     * @param key 对象 key（仅用于日志/扩展名判断）
     * @param size 源文件字节数（用于跳过判定）
     * @return JPEG 字节；跳过/失败返回 null
     */
    suspend fun generate(source: InputStream, key: String, size: Long): ByteArray? =
        withContext(Dispatchers.IO) {
            if (size > TransferConstants.THUMBNAIL_SOURCE_MAX_BYTES) {
                Log.i(TAG, "源文件过大，跳过缩略图: $key ($size bytes)")
                return@withContext null
            }

            val raw = try {
                source.use { it.readBytes() }
            } catch (t: Throwable) {
                Log.w(TAG, "读取源失败: $key", t)
                return@withContext null
            }
            if (raw.isEmpty()) return@withContext null

            val encoded = runCatching { encode(raw) }.getOrNull()
            if (encoded != null) return@withContext encoded

            // 编码失败：兜底（仅在体积可控时落盘原始内容）
            if (raw.size.toLong() <= TransferConstants.THUMBNAIL_RAW_FALLBACK_MAX_BYTES) raw else null
        }

    private fun encode(raw: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        // 两阶段降采样：先按 inSampleSize 快速缩小，再精确缩放
        var sample = 1
        val maxEdge = max(width, height)
        val target = TransferConstants.THUMBNAIL_MAX_EDGE_PX
        while (maxEdge / (sample * 2) >= target) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null
        val result = compressToJpeg(decoded, target, TransferConstants.THUMBNAIL_JPEG_QUALITY)
        decoded.recycle()
        return result
    }

    companion object {
        private const val TAG = "ThumbnailGenerator"

        /**
         * 缩放至最长边 [maxEdge]（不足则不放大）并压缩为 JPEG。
         * 供 [ThumbnailGenerator] 与 [VideoFrameExtractor] 复用。
         */
        internal fun compressToJpeg(bitmap: Bitmap, maxEdge: Int, quality: Int): ByteArray? {
            if (bitmap.isRecycled) return null
            val longest = max(bitmap.width, bitmap.height)
            val scaled = if (longest > maxEdge && longest > 0) {
                val ratio = maxEdge.toFloat() / longest.toFloat()
                Bitmap.createScaledBitmap(
                    bitmap,
                    max(1, (bitmap.width * ratio).roundToInt()),
                    max(1, (bitmap.height * ratio).roundToInt()),
                    true
                )
            } else {
                bitmap
            }

            return try {
                val out = ByteArrayOutputStream()
                val ok = scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (!ok) null else out.toByteArray()
            } catch (t: Throwable) {
                Log.w(TAG, "JPEG 压缩失败", t)
                null
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        }
    }
}
