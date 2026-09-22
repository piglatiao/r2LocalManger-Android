package com.r2manager.android.ui.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.r2manager.android.R
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.databinding.FragmentPreviewImageBinding
import com.r2manager.android.ui.common.BaseFragment
import com.r2manager.android.ui.common.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * 图片预览：可缩放/双击放大/下拉关闭（[com.r2manager.android.ui.widget.ZoomableImageView]）。
 */
class ImagePreviewFragment : BaseFragment<FragmentPreviewImageBinding>() {

    private val viewModel: PreviewViewModel by activityViewModels {
        ViewModelFactory.of(container) { PreviewViewModel(container) }
    }

    override fun inflateBinding(inflater: LayoutInflater, parent: ViewGroup?): FragmentPreviewImageBinding =
        FragmentPreviewImageBinding.inflate(inflater, parent, false)

    override fun onBind(binding: FragmentPreviewImageBinding) {
        binding.previewImage.onSwipeDownToSwipeDismiss = { requireActivity().finish() }
        val key = arguments?.getString(PreviewActivity.EXTRA_KEY).orEmpty()
        val kind = readKind()

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    PreviewUiState.Loading -> {
                        binding.previewImageProgress.isVisible = true
                        binding.previewImageMessage.isVisible = false
                    }

                    is PreviewUiState.Image -> {
                        binding.previewImageProgress.isVisible = false
                        binding.previewImageMessage.isVisible = state.bitmap == null
                        if (state.bitmap != null) {
                            binding.previewImage.setImageBitmap(state.bitmap)
                        } else {
                            binding.previewImageMessage.setText(R.string.preview_image_failed)
                        }
                    }

                    is PreviewUiState.TooLarge -> {
                        binding.previewImageProgress.isVisible = false
                        binding.previewImageMessage.isVisible = true
                        binding.previewImageMessage.setText(R.string.preview_download_to_open)
                    }

                    is PreviewUiState.Error -> {
                        binding.previewImageProgress.isVisible = false
                        binding.previewImageMessage.isVisible = true
                        binding.previewImageMessage.text = getString(state.error.messageResId)
                    }

                    else -> Unit
                }
            }
        }
        viewModel.open(key, kind)
    }

    private fun readKind(): PreviewKind {
        val name = arguments?.getString(PreviewActivity.EXTRA_KIND) ?: return PreviewKind.IMAGE
        return runCatching { PreviewKind.valueOf(name) }.getOrDefault(PreviewKind.IMAGE)
    }
}
