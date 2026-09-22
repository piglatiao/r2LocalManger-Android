package com.r2manager.android.ui.common

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView 通用装饰：列表分割线 / 网格间距。
 */
class RecyclerItemDecoration private constructor(
    private val dividerHeightPx: Int,
    private val spanCount: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        if (spanCount <= 1) {
            // 线性列表：底部留出分割线高度（最后一项不留）
            val isLast = position == state.itemCount - 1
            outRect.bottom = if (isLast) 0 else dividerHeightPx
        } else {
            // 网格：均匀间距
            val column = position % spanCount
            if (includeEdge) {
                outRect.left = dividerHeightPx - column * dividerHeightPx / spanCount
                outRect.right = (column + 1) * dividerHeightPx / spanCount
                if (position < spanCount) {
                    outRect.top = dividerHeightPx
                }
                outRect.bottom = dividerHeightPx
            } else {
                outRect.left = column * dividerHeightPx / spanCount
                outRect.right = dividerHeightPx - (column + 1) * dividerHeightPx / spanCount
                if (position >= spanCount) {
                    outRect.top = dividerHeightPx
                }
            }
        }
    }

    companion object {
        /** 线性列表分割线。 */
        fun forLinearList(dividerHeightPx: Int): RecyclerItemDecoration =
            RecyclerItemDecoration(dividerHeightPx, spanCount = 1, includeEdge = false)

        /** 网格间距。 */
        fun forGrid(spacingPx: Int, spanCount: Int): RecyclerItemDecoration =
            RecyclerItemDecoration(spacingPx, spanCount.coerceAtLeast(1), includeEdge = true)
    }
}
