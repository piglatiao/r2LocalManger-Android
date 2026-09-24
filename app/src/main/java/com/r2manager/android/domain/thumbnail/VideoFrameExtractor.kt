package com.r2manager.android.domain.thumbnail

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.Log
import com.r2manager.android.core.constants.TransferConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 视频首帧抽取（P3）。
 *
 * 零依赖实现：使用 framework 的 [MediaMetadataRetriever]（**不引入 media3**）。
 * 由于 `MediaMetadataRetriever` 需要可随机读取的数据源，这里用 [ByteArrayMediaDataSource]
 * 把内存字节包装成 `MediaDataSource`（API 23+，满足 minSdk 26），**无需落临时文件**。
 *
 * 并发 ≤ [TransferConstants.VIDEO_THUMBNAIL_CONCURRENCY]（2）由 [ThumbnailLoader] 的 Semaphore 保证。
 * 抽帧后压缩规则与图片一致（320px / JPEG 80）。
 */
class VideoFrameExtractor {

    /** 视频缩略图与媒体总时长。 */
    data class Result(
        /** JPEG 缩略图字节。 */
        val jpeg: ByteArray?,
        /** 视频总时长（毫秒）。 */
        val durationMs: Long?
    )

    /**
     * 抽取视频首帧并读取媒体总时长。
     * @param source 视频字节流（本方法会关闭它）
     * @return 缩略图和时长；读取失败返回 null
     */
    suspend fun extractFirstFrame(source: InputStream): Result? = withContext(Dispatchers.IO) {
        val bytes = try {
            source.use { it.readBytes() }
        } catch (t: Throwable) {
            Log.w(TAG, "读取视频源失败", t)
            return@withContext null
        }
        if (bytes.isEmpty()) return@withContext null

        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        var durationMs: Long? = null
        try {
            retriever.setDataSource(ByteArrayMediaDataSource(bytes))
            durationMs = runCatching {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }.getOrNull()
            frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (t: Throwable) {
            Log.w(TAG, "视频抽帧失败", t)
        } finally {
            runCatching { retriever.release() }
        }

        val jpeg = frame?.let { bitmap ->
            val compressed = ThumbnailGenerator.compressToJpeg(
                bitmap,
                TransferConstants.THUMBNAIL_MAX_EDGE_PX,
                TransferConstants.THUMBNAIL_JPEG_QUALITY
            )
            bitmap.recycle()
            compressed
        }
        Result(jpeg, durationMs)
    }

    /** 把内存字节包装成可随机读取的 [MediaDataSource]。 */
    private class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
        override fun getSize(): Long = data.size.toLong()

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0L || position >= data.size) return -1
            val start = position.toInt()
            val length = minOf(size, data.size - start)
            System.arraycopy(data, start, buffer, offset, length)
            return length
        }

        override fun close() {
            // 内存数据，无需释放
        }
    }

    private companion object {
        const val TAG = "VideoFrameExtractor"
    }
}
