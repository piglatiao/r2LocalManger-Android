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
import com.r2manager.android.databinding.ItemActionSheetBinding
import com.r2manager.android.databinding.SheetItemActionsBinding
import com.r2manager.android.domain.model.ObjectInfo

/**
 * 列表项动作面板（打开 / 下载 / 复制链接 / 分享 / 删除）。
 */
class ItemActionsSheet : BottomSheetDialogFragment() {

    /** 可用动作。 */
    enum class Action { OPEN, DOWNLOAD, COPY_URL, SHARE, DELETE }

    /** 结果回调。 */
    interface Listener {
        /** 选中动作。 */
        fun onItemAction(action: Action, info: ObjectInfo)
    }

    private var _binding: SheetItemActionsBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val listener: Listener? get() = parentFragment as? Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetItemActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val inflater = LayoutInflater.from(requireContext())
        val info = objectInfo() ?: run { dismiss(); return }
        binding.itemActionsTitle.text = info.name
        val isFile = !info.isFolder
        addRow(inflater, binding.itemActionsContainer, R.drawable.ic_eye, R.string.browser_action_open, Action.OPEN)
        addRow(inflater, binding.itemActionsContainer, R.drawable.ic_down, R.string.browser_action_download, Action.DOWNLOAD)
        if (isFile) {
            addRow(inflater, binding.itemActionsContainer, R.drawable.ic_link, R.string.browser_action_copy_url, Action.COPY_URL)
            addRow(inflater, binding.itemActionsContainer, R.drawable.ic_share, R.string.browser_action_share, Action.SHARE)
        }
        addRow(
            inflater, binding.itemActionsContainer, R.drawable.ic_trash, R.string.browser_action_delete,
            Action.DELETE, danger = true
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int = R.style.Theme_R2Manager_BottomSheet

    private fun addRow(
        inflater: LayoutInflater,
        parent: LinearLayout,
        iconRes: Int,
        labelRes: Int,
        action: Action,
        danger: Boolean = false
    ) {
        val row = ItemActionSheetBinding.inflate(inflater, parent, false)
        row.itemActionIcon.setImageResource(iconRes)
        row.itemActionLabel.setText(labelRes)
        val color = ContextCompat.getColor(
            requireContext(),
            if (danger) R.color.error else R.color.ink_primary
        )
        row.itemActionIcon.setColorFilter(color)
        row.itemActionLabel.setTextColor(color)
        row.root.setOnClickListener {
            val info = objectInfo()
            if (info != null) {
                listener?.onItemAction(action, info)
            }
            dismiss()
        }
        parent.addView(row.root)
    }

    private fun objectInfo(): ObjectInfo? {
        val args = arguments ?: return null
        val key = args.getString(ARG_KEY) ?: return null
        return ObjectInfo(
            key = key,
            name = args.getString(ARG_NAME).orEmpty(),
            isFolder = args.getBoolean(ARG_IS_FOLDER, false),
            size = args.getLong(ARG_SIZE, 0L),
            lastModifiedIso = args.getString(ARG_LAST_MODIFIED),
            contentType = args.getString(ARG_CONTENT_TYPE),
            etag = args.getString(ARG_ETAG)
        )
    }

    companion object {
        private const val ARG_KEY = "key"
        private const val ARG_NAME = "name"
        private const val ARG_IS_FOLDER = "isFolder"
        private const val ARG_SIZE = "size"
        private const val ARG_LAST_MODIFIED = "lastModified"
        private const val ARG_CONTENT_TYPE = "contentType"
        private const val ARG_ETAG = "etag"

        /** 构造实例。 */
        fun newInstance(info: ObjectInfo): ItemActionsSheet = ItemActionsSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_KEY, info.key)
                putString(ARG_NAME, info.name)
                putBoolean(ARG_IS_FOLDER, info.isFolder)
                putLong(ARG_SIZE, info.size)
                putString(ARG_LAST_MODIFIED, info.lastModifiedIso)
                putString(ARG_CONTENT_TYPE, info.contentType)
                putString(ARG_ETAG, info.etag)
            }
        }
    }
}
