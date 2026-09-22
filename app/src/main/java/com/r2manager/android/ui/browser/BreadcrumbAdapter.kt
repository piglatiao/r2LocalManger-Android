package com.r2manager.android.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.r2manager.android.core.util.PathUtils
import com.r2manager.android.databinding.ItemBreadcrumbBinding

/**
 * 面包屑适配器（水平 RecyclerView）：`[bucket] / seg1 / seg2 / …`，末段为当前目录不可点。
 *
 * 分段规则复用 P1 [PathUtils.breadcrumbSegments]，与缓存键 / 传输 key 保持一致。
 *
 * @param onCrumbClick 点击回调，参数为目标目录前缀（点 bucket → `""`）
 */
class BreadcrumbAdapter(
    private val onCrumbClick: (prefix: String) -> Unit
) : RecyclerView.Adapter<BreadcrumbAdapter.CrumbHolder>() {

    /** 单个面包屑节点。 */
    data class Crumb(val label: String, val prefix: String, val isCurrent: Boolean, val isFirst: Boolean)

    private val crumbs = ArrayList<Crumb>()

    /** 重新绑定路径。 */
    fun submit(bucket: String, prefix: String) {
        crumbs.clear()
        val normalized = PathUtils.normalizePrefix(prefix)
        val segments = PathUtils.breadcrumbSegments(normalized)
        val rootLabel = bucket.trim().ifBlank { "/" }
        crumbs.add(Crumb(rootLabel, "", isCurrent = segments.isEmpty(), isFirst = true))
        for ((index, segment) in segments.withIndex()) {
            crumbs.add(
                Crumb(
                    label = segment.substringAfterLast('/'),
                    prefix = segment,
                    isCurrent = index == segments.lastIndex,
                    isFirst = false
                )
            )
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = crumbs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CrumbHolder =
        CrumbHolder(ItemBreadcrumbBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CrumbHolder, position: Int) {
        holder.bind(crumbs[position])
    }

    /** 面包屑节点视图。 */
    inner class CrumbHolder(private val binding: ItemBreadcrumbBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(crumb: Crumb) {
            binding.crumbSeparator.visibility = if (crumb.isFirst) View.GONE else View.VISIBLE
            binding.crumbLabel.text = crumb.label
            binding.crumbLabel.isSelected = crumb.isCurrent
            binding.crumbLabel.alpha = if (crumb.isCurrent) 1f else 0.7f
            if (crumb.isCurrent) {
                binding.crumbLabel.setOnClickListener(null)
                binding.crumbLabel.isClickable = false
                binding.crumbLabel.isFocusable = false
            } else {
                binding.crumbLabel.isClickable = true
                binding.crumbLabel.isFocusable = true
                binding.crumbLabel.setOnClickListener { onCrumbClick(crumb.prefix) }
            }
        }
    }
}
