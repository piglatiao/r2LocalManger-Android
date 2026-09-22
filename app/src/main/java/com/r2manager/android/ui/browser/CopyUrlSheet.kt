package com.r2manager.android.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.r2manager.android.R
import com.r2manager.android.databinding.SheetCopyUrlBinding
import com.r2manager.android.domain.model.CopyFormat

/**
 * 复制链接底部面板：展示公开链接、切换格式（URL / HTML / Markdown）并复制。
 *
 * 由 [BrowserFragment] 弹出；结果通过 [Listener] 回传（复制动作在宿主完成并展示提示）。
 */
class CopyUrlSheet : BottomSheetDialogFragment() {

    /** 结果回调。 */
    interface Listener {
        /** 用户确认复制，参数为已按所选格式格式化的文本。 */
        fun onCopyUrl(text: String, format: CopyFormat)

        /** 面板关闭（用于恢复工具条等）。 */
        fun onCopyUrlDismissed()
    }

    private var _binding: SheetCopyUrlBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val listener: Listener? get() = parentFragment as? Listener

    private lateinit var baseUrl: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetCopyUrlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        baseUrl = arguments?.getString(ARG_URL).orEmpty()
        binding.copyUrlName.text = arguments?.getString(ARG_NAME)
        binding.copyUrlValue.text = baseUrl

        val initial = readInitialFormat()
        binding.copyUrlFormatGroup.check(formatChipId(initial))
        binding.copyUrlValue.isLongClickable = true
        binding.copyUrlValue.setOnLongClickListener { true }

        binding.copyUrlCopy.setOnClickListener {
            listener?.onCopyUrl(format(baseUrl, currentFormat()), currentFormat())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        listener?.onCopyUrlDismissed()
    }

    override fun getTheme(): Int = R.style.Theme_R2Manager_BottomSheet

    private fun currentFormat(): CopyFormat = when (binding.copyUrlFormatGroup.checkedChipId) {
        R.id.chip_format_html -> CopyFormat.HTML
        R.id.chip_format_markdown -> CopyFormat.MARKDOWN
        else -> CopyFormat.URL
    }

    private fun format(url: String, format: CopyFormat): String = when (format) {
        CopyFormat.URL -> url
        CopyFormat.HTML -> "<a href=\"$url\">$url</a>"
        CopyFormat.MARKDOWN -> "[$url]($url)"
    }

    private fun formatChipId(format: CopyFormat): Int = when (format) {
        CopyFormat.URL -> R.id.chip_format_url
        CopyFormat.HTML -> R.id.chip_format_html
        CopyFormat.MARKDOWN -> R.id.chip_format_markdown
    }

    private fun readInitialFormat(): CopyFormat {
        val name = arguments?.getString(ARG_FORMAT) ?: return CopyFormat.URL
        return runCatching { CopyFormat.valueOf(name) }.getOrDefault(CopyFormat.URL)
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_NAME = "name"
        private const val ARG_FORMAT = "format"

        /** 构造实例。 */
        fun newInstance(name: String, url: String, initialFormat: CopyFormat): CopyUrlSheet =
            CopyUrlSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)
                    putString(ARG_URL, url)
                    putString(ARG_FORMAT, initialFormat.name)
                }
            }
    }
}
