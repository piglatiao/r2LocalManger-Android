package com.r2manager.android.ui.browser

import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import com.r2manager.android.R
import com.r2manager.android.core.mime.FileTypes
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.data.local.cache.CacheKeyFactory
import com.r2manager.android.domain.model.ObjectInfo
import com.r2manager.android.domain.model.ThumbnailMeta
import com.r2manager.android.domain.thumbnail.ThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 列表缩略图请求管理器（R-11/R-12 的 UI 侧）。
 *
 * 职责：
 * - 同 [ImageView] 复用时取消上一次请求（避免错位）；
 * - 进程内小内存 LRU 缓存（避免滚动反复解码）；
 * - 文件夹 / 不可预览类型直接展示类型图标；文件过大由 [ThumbnailLoader] 返回 null，回退类型图标。
 *
 * @param loader 缩略图加载器（下载 → 生成 → 加密落盘）
 * @param scope 绑定页面生命周期的协程作用域
 */
class ThumbnailRequestManager(
    private val loader: ThumbnailLoader,
    private val scope: CoroutineScope
) {

    private val jobs = HashMap<ImageView, Job>()

    /** 内存缓存：按解码后位图字节估算占用，上限 12MB。 */
    private val memory = object : LruCache<String, ByteArray>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    /**
     * 绑定缩略图到目标视图。
     *
     * @param target 目标 ImageView
     * @param bucket 桶名
     * @param info 列表项
     */
    fun load(target: ImageView, bucket: String, info: ObjectInfo) {
        if (info.isFolder) {
            showTypeIcon(target, info)
            return
        }
        val key = CacheKeyFactory.thumbId(thumbnailMeta(bucket, info))
        val cached = memory.get(key)
        if (cached != null) {
            applyBytes(target, cached)
            return
        }
        cancel(target)
        target.setImageDrawable(null)
        target.setImageResource(typeIconRes(info))
        jobs[target] = scope.launch {
            val bytes = runCatching { loader.load(bucket, info) }.getOrNull()
            if (bytes != null) {
                memory.put(key, bytes)
                applyBytes(target, bytes)
            } else {
                showTypeIcon(target, info)
            }
        }
    }

    /** 取消某视图的在途请求（应在 `onViewRecycled` 调用）。 */
    fun cancel(target: ImageView) {
        jobs.remove(target)?.cancel()
    }

    /** 取消全部在途请求（页面销毁时调用）。 */
    fun clear() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    // ==================== 内部 ====================

    private fun thumbnailMeta(bucket: String, info: ObjectInfo): ThumbnailMeta = ThumbnailMeta(
        bucket = bucket,
        key = info.key,
        size = info.size,
        lastModifiedIso = info.lastModifiedIso,
        etag = info.etag
    )

    private fun applyBytes(target: ImageView, bytes: ByteArray) {
        val options = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
        if (bitmap != null) {
            target.setImageBitmap(bitmap)
        } else {
            target.setImageResource(R.drawable.ic_image)
        }
    }

    private fun showTypeIcon(target: ImageView, info: ObjectInfo) {
        target.setImageResource(typeIconRes(info))
    }

    private fun typeIconRes(info: ObjectInfo): Int {
        if (info.isFolder) {
            return R.drawable.ic_folder
        }
        return when (FileTypes.kindOf(info.key)) {
            PreviewKind.IMAGE -> R.drawable.ic_image
            PreviewKind.VIDEO -> R.drawable.ic_play
            PreviewKind.PDF, PreviewKind.TEXT -> R.drawable.ic_doc
            PreviewKind.OTHER -> R.drawable.ic_file
        }
    }

    private companion object {
        const val MAX_MEMORY_BYTES = 12 * 1024 * 1024
    }
}
