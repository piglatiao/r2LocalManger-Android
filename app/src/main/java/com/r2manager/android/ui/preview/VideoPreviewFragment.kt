package com.r2manager.android.ui.preview

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.MediaController
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.r2manager.android.R
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.databinding.FragmentPreviewVideoBinding
import com.r2manager.android.ui.common.BaseFragment
import com.r2manager.android.ui.common.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * 视频预览：framework [android.widget.VideoView] 播放（主理人裁决：一期不引入 media3）。
 *
 * 视频先落地缓存文件再播放；点击封面播放，播放中显示 `MediaController`。
 */
class VideoPreviewFragment : BaseFragment<FragmentPreviewVideoBinding>() {

    private val viewModel: PreviewViewModel by activityViewModels {
        ViewModelFactory.of(container) { PreviewViewModel(container) }
    }

    override fun inflateBinding(inflater: LayoutInflater, parent: ViewGroup?): FragmentPreviewVideoBinding =
        FragmentPreviewVideoBinding.inflate(inflater, parent, false)

    override fun onBind(binding: FragmentPreviewVideoBinding) {
        val key = arguments?.getString(PreviewActivity.EXTRA_KEY).orEmpty()

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    PreviewUiState.Loading -> {
                        binding.previewVideoProgress.isVisible = true
                        binding.previewVideoPlay.isVisible = false
                        binding.previewVideoMessage.isVisible = false
                    }

                    is PreviewUiState.Video -> {
                        binding.previewVideoProgress.isVisible = false
                        binding.previewVideoMessage.isVisible = false
                        binding.previewVideoPlay.isVisible = true
                        prepare(binding, Uri.fromFile(state.file))
                    }

                    is PreviewUiState.Error -> {
                        binding.previewVideoProgress.isVisible = false
                        binding.previewVideoPlay.isVisible = false
                        binding.previewVideoMessage.isVisible = true
                        binding.previewVideoMessage.text = getString(state.error.messageResId)
                    }

                    else -> Unit
                }
            }
        }
        viewModel.open(key, PreviewKind.VIDEO)
    }

    private fun prepare(binding: FragmentPreviewVideoBinding, uri: Uri) {
        val controller = MediaController(requireContext())
        controller.setAnchorView(binding.previewVideo)
        binding.previewVideo.setMediaController(controller)
        binding.previewVideo.setVideoURI(uri)
        binding.previewVideo.setOnPreparedListener { mp ->
            mp.isLooping = false
            binding.previewVideoPlay.isVisible = true
            binding.previewVideoPlay.setOnClickListener {
                binding.previewVideoPlay.isVisible = false
                binding.previewVideo.start()
            }
        }
        binding.previewVideo.setOnErrorListener { _, _, _ ->
            binding.previewVideoPlay.isVisible = false
            binding.previewVideoMessage.isVisible = true
            binding.previewVideoMessage.setText(R.string.preview_video_failed)
            true
        }
    }

    override fun onStop() {
        super.onStop()
        bindingOrNull()?.previewVideo?.pause()
    }

    private fun bindingOrNull(): FragmentPreviewVideoBinding? = runCatching { binding }.getOrNull()
}
