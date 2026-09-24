package com.r2manager.android.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.r2manager.android.R
import com.r2manager.android.core.util.ByteFormat
import com.r2manager.android.core.util.TimeFormat
import com.r2manager.android.core.mime.FileTypes
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.databinding.ItemObjectBinding
import com.r2manager.android.databinding.ItemObjectGridBinding
import com.r2manager.android.domain.model.ObjectInfo

/**
 * 对象列表适配器（列表 / 网格双视图）。
 *
 * - 列表项：缩略图 + 文件名 + 大小/时间 + 溢出按钮；
 * - 网格项：缩略图 + 文件名；
 * - 多选模式下显示勾选态、长按进入多选（由 [Listener] 决策）。
 *
 * @param thumbnails 缩略图请求管理器
 * @param listener 交互回调
 */
class ObjectListAdapter(
    private val thumbnails: ThumbnailRequestManager,
    private val listener: Listener
) : ListAdapter<ObjectInfo, ObjectListAdapter.RowHolder>(ObjectListDiffCallback) {

    /** 交互回调。 */
    interface Listener {
        /** 单击。 */
        fun onClick(info: ObjectInfo)

        /** 长按（返回 true 表示已消费）。 */
        fun onLongClick(info: ObjectInfo): Boolean

        /** 点击溢出按钮。 */
        fun onMoreClick(info: ObjectInfo, anchor: View)
    }

    /** 当前桶（缩略图缓存键需要）。 */
    var bucket: String = ""

    /** 视图模式。 */
    var viewMode: BrowserUiState.ViewMode = BrowserUiState.ViewMode.LIST

    /** 是否多选模式。 */
    var selectionMode: Boolean = false

    /** 已选 key 集合。 */
    var selectedKeys: Set<String> = emptySet()

    override fun getItemViewType(position: Int): Int =
        if (viewMode == BrowserUiState.ViewMode.GRID) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            GridHolder(ItemObjectGridBinding.inflate(inflater, parent, false))
        } else {
            ListHolder(ItemObjectBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        val info = getItem(position)
        val selected = info != null && info.key in selectedKeys
        holder.bind(getItem(position), selected)
    }

    override fun onViewRecycled(holder: RowHolder) {
        holder.releaseThumbnail()
        super.onViewRecycled(holder)
    }

    /** 行视图基类。 */
    abstract inner class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        protected abstract val thumb: ImageView

        abstract fun bind(info: ObjectInfo, selected: Boolean)

        /** 释放缩略图请求（视图回收时）。 */
        fun releaseThumbnail() {
            thumbnails.cancel(thumb)
        }

        protected fun bindCommon(root: View, info: ObjectInfo, selected: Boolean) {
            root.isSelected = selectionMode && selected
            root.isActivated = selectionMode && selected
            root.setOnClickListener { listener.onClick(info) }
            root.setOnLongClickListener { listener.onLongClick(info) }
            thumbnails.load(thumb, bucket, info) { onVideoDurationChanged(info, it) }
        }

        protected open fun onVideoDurationChanged(info: ObjectInfo, durationMs: Long?) = Unit
    }

    /** 列表视图。 */
    private inner class ListHolder(private val binding: ItemObjectBinding) : RowHolder(binding.root) {
        override val thumb: ImageView get() = binding.itemThumb

        override fun bind(info: ObjectInfo, selected: Boolean) {
            bindCommon(binding.root, info, selected)
            binding.itemName.text = info.name
            binding.itemMeta.text = subtitleOf(info, thumbnails.durationMs(bucket, info))
            binding.itemSelected.visibility =
                if (selectionMode && selected) View.VISIBLE else View.GONE
            binding.itemMore.setOnClickListener { listener.onMoreClick(info, binding.itemMore) }
        }

        override fun onVideoDurationChanged(info: ObjectInfo, durationMs: Long?) {
            binding.itemMeta.text = subtitleOf(info, durationMs)
        }
    }

    /** 网格视图。 */
    private inner class GridHolder(private val binding: ItemObjectGridBinding) : RowHolder(binding.root) {
        override val thumb: ImageView get() = binding.gridThumb

        override fun bind(info: ObjectInfo, selected: Boolean) {
            bindCommon(binding.root, info, selected)
            binding.gridName.text = info.name
            val isVideo = FileTypes.kindOf(info.key) == PreviewKind.VIDEO
            binding.gridDuration.isVisible = isVideo
            binding.gridDuration.text = formatDuration(thumbnails.durationMs(bucket, info))
            binding.gridSelected.visibility =
                if (selectionMode && selected) View.VISIBLE else View.GONE
        }

        override fun onVideoDurationChanged(info: ObjectInfo, durationMs: Long?) {
            binding.gridDuration.text = formatDuration(durationMs)
        }
    }

    private companion object {
        const val TYPE_LIST = 0
        const val TYPE_GRID = 1

        /** 列表副标题：大小 · 时间（文件夹显示「文件夹」）。 */
        fun subtitleOf(info: ObjectInfo, durationMs: Long?): String {
            if (info.isFolder) {
                return "文件夹"
            }
            val size = ByteFormat.size(info.size)
            val time = TimeFormat.display(info.lastModifiedIso)
            val duration = if (FileTypes.kindOf(info.key) == PreviewKind.VIDEO) formatDuration(durationMs) else ""
            val details = listOf(duration, size, time).filter { it.isNotEmpty() }
            return details.joinToString(" · ")
        }

        fun formatDuration(durationMs: Long?): String {
            if (durationMs == null || durationMs < 0L) return "--:--"
            val totalSeconds = durationMs / 1000L
            val seconds = totalSeconds % 60L
            val minutes = (totalSeconds / 60L) % 60L
            val hours = totalSeconds / 3600L
            return if (hours > 0L) {
                String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.ROOT, "%02d:%02d", totalSeconds / 60L, seconds)
            }
        }
    }
}
