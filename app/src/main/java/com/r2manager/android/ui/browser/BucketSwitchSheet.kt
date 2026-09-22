package com.r2manager.android.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.r2manager.android.R
import com.r2manager.android.databinding.SheetBucketSwitchBinding

/**
 * 桶切换底部面板：列出当前账号下的桶，点选后切桶。
 *
 * 由 [BrowserFragment] 弹出；结果通过 [Listener] 回传。
 */
class BucketSwitchSheet : BottomSheetDialogFragment() {

    /** 结果回调。 */
    interface Listener {
        /** 选择桶。 */
        fun onBucketSelected(name: String)
    }

    private var _binding: SheetBucketSwitchBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val listener: Listener? get() = parentFragment as? Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetBucketSwitchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val buckets = arguments?.getStringArrayList(ARG_BUCKETS).orEmpty()
        val current = arguments?.getString(ARG_CURRENT).orEmpty()
        for (name in buckets) {
            binding.bucketSwitchContainer.addView(buildRow(name, name == current))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int = R.style.Theme_R2Manager_BottomSheet

    private fun buildRow(name: String, selected: Boolean): View {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_4),
                resources.getDimensionPixelSize(R.dimen.space_3),
                resources.getDimensionPixelSize(R.dimen.space_4),
                resources.getDimensionPixelSize(R.dimen.space_3)
            )
            setBackgroundResource(R.drawable.bg_list_item_ripple)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val label = TextView(context).apply {
            text = name
            setTextAppearance(R.style.TextAppearance_R2Manager_ListTitle)
            setTextColor(ContextCompat.getColor(context, R.color.ink_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)
        if (selected) {
            val check = ImageView(context).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(ContextCompat.getColor(context, R.color.primary))
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.icon_size),
                    resources.getDimensionPixelSize(R.dimen.icon_size)
                )
            }
            row.addView(check)
        }
        row.setOnClickListener {
            listener?.onBucketSelected(name)
            dismiss()
        }
        return row
    }

    companion object {
        private const val ARG_BUCKETS = "buckets"
        private const val ARG_CURRENT = "current"

        /** 构造实例。 */
        fun newInstance(buckets: List<String>, current: String): BucketSwitchSheet =
            BucketSwitchSheet().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_BUCKETS, ArrayList(buckets))
                    putString(ARG_CURRENT, current)
                }
            }
    }
}
