package com.r2manager.android.ui.transfer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.r2manager.android.R
import com.r2manager.android.domain.model.TransferTask
import com.r2manager.android.ui.widget.StateView

/**
 * 传输中心三 Tab 的 ViewPager2 适配器（P4-B）。
 *
 * 每页是一个「列表 + 空态」的整页视图（[RecyclerView] + [StateView]），页内含独立的
 * [TransferListAdapter]，避免多页共用同一适配器导致的挂载冲突。三页分别对应
 * 进行中 / 已完成 / 失败（`docs/05 §7.4`，与原型 `tabs` 一致）。
 *
 * 通过 [submit] 一次性投递三组数据，内部按页刷新。
 */
class TransferPagerAdapter(
    private val onCancel: (TransferTask) -> Unit,
    private val onRetry: (TransferTask) -> Unit,
    private val onRemove: (TransferTask) -> Unit,
    private val onOpen: (TransferTask) -> Unit,
    private val onShare: (TransferTask) -> Unit,
    private val onCopyLink: (TransferTask) -> Unit
) : RecyclerView.Adapter<TransferPagerAdapter.PageHolder>() {

    /** 各页数据（仅本适配器持有，供 bind 时按 position 取用）。 */
    private var running: List<TransferTask> = emptyList()
    private var completed: List<TransferTask> = emptyList()
    private var failed: List<TransferTask> = emptyList()

    /** 全量刷新三页。 */
    fun submit(running: List<TransferTask>, completed: List<TransferTask>, failed: List<TransferTask>) {
        this.running = running
        this.completed = completed
        this.failed = failed
        notifyItemRangeChanged(0, PAGE_COUNT)
    }

    override fun getItemCount(): Int = PAGE_COUNT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val context = parent.context
        val container = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.canvas))
        }
        val recycler = RecyclerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(context)
            val padding = context.resources.getDimensionPixelSize(R.dimen.page_margin)
            setPadding(padding, padding, padding, padding)
            clipToPadding = false
        }
        val stateView = StateView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(recycler)
        container.addView(stateView)

        val adapter = TransferListAdapter(
            onCancel = onCancel,
            onRetry = onRetry,
            onRemove = onRemove,
            onOpen = onOpen,
            onShare = onShare,
            onCopyLink = onCopyLink
        )
        recycler.adapter = adapter
        return PageHolder(container, recycler, stateView, adapter)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.bind(pageItems(position), position)
    }

    private fun pageItems(position: Int): List<TransferTask> = when (position) {
        PAGE_RUNNING -> running
        PAGE_COMPLETED -> completed
        else -> failed
    }

    /** 单页视图持有者（自持适配器，页间互不干扰）。 */
    class PageHolder(
        root: View,
        private val recycler: RecyclerView,
        private val stateView: StateView,
        private val adapter: TransferListAdapter
    ) : RecyclerView.ViewHolder(root) {

        fun bind(items: List<TransferTask>, position: Int) {
            adapter.submit(items)
            if (items.isEmpty()) {
                recycler.visibility = View.GONE
                stateView.visibility = View.VISIBLE
                stateView.showEmpty(
                    titleRes = emptyTitle(position),
                    descRes = if (position == TransferPagerAdapter.PAGE_RUNNING) {
                        R.string.transfer_empty_all
                    } else {
                        null
                    }
                )
            } else {
                stateView.hide()
                recycler.visibility = View.VISIBLE
            }
        }

        private fun emptyTitle(position: Int): Int = when (position) {
            TransferPagerAdapter.PAGE_RUNNING -> R.string.transfer_empty_running
            TransferPagerAdapter.PAGE_COMPLETED -> R.string.transfer_empty_completed
            else -> R.string.transfer_empty_failed
        }
    }

    companion object {
        /** 页数：进行中 / 已完成 / 失败。 */
        const val PAGE_COUNT = 3

        const val PAGE_RUNNING = 0
        const val PAGE_COMPLETED = 1
        const val PAGE_FAILED = 2
    }
}
