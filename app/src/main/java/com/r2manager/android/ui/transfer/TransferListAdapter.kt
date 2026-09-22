package com.r2manager.android.ui.transfer

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.r2manager.android.R
import com.r2manager.android.core.util.ByteFormat
import com.r2manager.android.databinding.ItemTransferBinding
import com.r2manager.android.domain.model.TransferDirection
import com.r2manager.android.domain.model.TransferStatus
import com.r2manager.android.domain.model.TransferTask

/**
 * 传输任务卡片适配器（P4-B）。
 *
 * 一张卡片覆盖三态（进行中 / 失败 / 已完成），按 [TransferStatus] 切换动作区可见性，
 * 与原型 `tcard` 一致：进行中 → 取消；失败 → 重试 + 移除；完成 → 打开 / 分享 / 复制链接。
 *
 * 长文件名与字节明细分行展示，避免「文件名挤掉大小/时间」（R-10）。
 */
class TransferListAdapter(
    private val onCancel: (TransferTask) -> Unit,
    private val onRetry: (TransferTask) -> Unit,
    private val onRemove: (TransferTask) -> Unit,
    private val onOpen: (TransferTask) -> Unit,
    private val onShare: (TransferTask) -> Unit,
    private val onCopyLink: (TransferTask) -> Unit
) : RecyclerView.Adapter<TransferListAdapter.Holder>() {

    private val items = ArrayList<TransferTask>()

    /** 全量刷新（DiffUtil，避免整表重绘）。 */
    fun submit(list: List<TransferTask>) {
        val diff = DiffUtil.calculateDiff(TaskDiff(items, list))
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemTransferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    /** 单张卡片视图持有者。 */
    inner class Holder(private val binding: ItemTransferBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TransferTask) {
            val context = binding.root.context

            bindDirection(task)
            binding.tvName.text = task.key.substringAfterLast('/').ifBlank { task.key }
            bindStatus(task)

            val percent = task.percent
            binding.progressBar.progress = percent
            binding.tvLeft.text = bytesLine(task)
            binding.tvRight.text = context.getString(R.string.transfer_progress_percent, percent)

            bindActions(task)
        }

        private fun bindDirection(task: TransferTask) {
            val context = binding.root.context
            val isUpload = task.direction == TransferDirection.UPLOAD
            binding.tvDirection.text = context.getString(
                if (isUpload) R.string.transfer_direction_upload else R.string.transfer_direction_download
            )
            binding.tvDirection.setTextColor(
                ContextCompat.getColor(context, if (isUpload) R.color.brand else R.color.blue)
            )
        }

        private fun bindStatus(task: TransferTask) {
            val context = binding.root.context
            val (labelRes, colorRes) = when (task.status) {
                TransferStatus.QUEUED -> R.string.transfer_status_queued to R.color.ink_tertiary
                TransferStatus.RUNNING -> R.string.transfer_status_running to R.color.primary
                TransferStatus.DONE -> R.string.transfer_status_done to R.color.success
                TransferStatus.FAILED -> R.string.transfer_status_failed to R.color.error
                TransferStatus.CANCELLED -> R.string.transfer_status_cancelled to R.color.ink_tertiary
            }
            binding.tvStatus.text = context.getString(labelRes)

            val color = ContextCompat.getColor(context, colorRes)
            binding.tvStatus.setTextColor(color)
            binding.progressBar.progressTintList = ColorStateList.valueOf(color)
        }

        private fun bindActions(task: TransferTask) {
            val running = task.status == TransferStatus.QUEUED || task.status == TransferStatus.RUNNING
            val failed = task.status == TransferStatus.FAILED || task.status == TransferStatus.CANCELLED
            val done = task.status == TransferStatus.DONE

            binding.runningActions.visibility = if (running) View.VISIBLE else View.GONE
            binding.failedActions.visibility = if (failed) View.VISIBLE else View.GONE
            binding.doneActions.visibility = if (done) View.VISIBLE else View.GONE

            binding.btnCancel.setOnClickListener { onCancel(task) }
            binding.btnRetry.setOnClickListener { onRetry(task) }
            binding.btnRemove.setOnClickListener { onRemove(task) }
            binding.tvOpen.setOnClickListener { onOpen(task) }
            binding.tvShare.setOnClickListener { onShare(task) }
            binding.tvCopy.setOnClickListener { onCopyLink(task) }
        }

        /** 字节明细行：`10.3 MB / 24.6 MB`（数值 + 符号，无需文案资源）。 */
        private fun bytesLine(task: TransferTask): String =
            ByteFormat.size(task.transferred) + " / " + ByteFormat.size(task.size)
    }

    /** 任务列表差分（按 id + 关键字段）。 */
    private class TaskDiff(
        private val old: List<TransferTask>,
        private val new: List<TransferTask>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = old.size

        override fun getNewListSize(): Int = new.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition].id == new[newItemPosition].id

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition] == new[newItemPosition]
    }
}
