package com.r2manager.android.ui.browser

import androidx.recyclerview.widget.DiffUtil
import com.r2manager.android.domain.model.ObjectInfo

/**
 * 列表项 Diff 回调。
 *
 * 以 [ObjectInfo.key] 为身份；[ObjectInfo] 为 data class，`areContentsTheSame` 直接比较全部字段。
 */
object ObjectListDiffCallback : DiffUtil.ItemCallback<ObjectInfo>() {

    override fun areItemsTheSame(oldItem: ObjectInfo, newItem: ObjectInfo): Boolean =
        oldItem.key == newItem.key

    override fun areContentsTheSame(oldItem: ObjectInfo, newItem: ObjectInfo): Boolean =
        oldItem == newItem
}
