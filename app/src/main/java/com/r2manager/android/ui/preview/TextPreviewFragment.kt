package com.r2manager.android.ui.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.r2manager.android.R
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.databinding.FragmentPreviewTextBinding
import com.r2manager.android.ui.common.BaseFragment
import com.r2manager.android.ui.common.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * 文本预览：等宽字体、可滚动；> 2MB 显示「下载后查看」。
 */
class TextPreviewFragment : BaseFragment<FragmentPreviewTextBinding>() {

    private val viewModel: PreviewViewModel by activityViewModels {
        ViewModelFactory.of(container) { PreviewViewModel(container) }
    }

    override fun inflateBinding(inflater: LayoutInflater, parent: ViewGroup?): FragmentPreviewTextBinding =
        FragmentPreviewTextBinding.inflate(inflater, parent, false)

    override fun onBind(binding: FragmentPreviewTextBinding) {
        val key = arguments?.getString(PreviewActivity.EXTRA_KEY).orEmpty()
        val kind = readKind()

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    PreviewUiState.Loading -> {
                        binding.previewTextProgress.isVisible = true
                        binding.previewTextScroll.isVisible = false
                        binding.previewTextMessage.isVisible = false
                    }

                    is PreviewUiState.Text -> {
                        binding.previewTextProgress.isVisible = false
                        binding.previewTextMessage.isVisible = false
                        binding.previewTextScroll.isVisible = true
                        binding.previewTextContent.text = state.content
                    }

                    is PreviewUiState.TooLarge -> {
                        binding.previewTextProgress.isVisible = false
                        binding.previewTextScroll.isVisible = false
                        binding.previewTextMessage.isVisible = true
                        binding.previewTextMessage.setText(R.string.preview_download_to_open)
                    }

                    is PreviewUiState.Error -> {
                        binding.previewTextProgress.isVisible = false
                        binding.previewTextScroll.isVisible = false
                        binding.previewTextMessage.isVisible = true
                        binding.previewTextMessage.text = getString(state.error.messageResId)
                    }

                    else -> Unit
                }
            }
        }
        viewModel.open(key, kind)
    }

    private fun readKind(): PreviewKind {
        val name = arguments?.getString(PreviewActivity.EXTRA_KIND) ?: return PreviewKind.TEXT
        return runCatching { PreviewKind.valueOf(name) }.getOrDefault(PreviewKind.TEXT)
    }
}
