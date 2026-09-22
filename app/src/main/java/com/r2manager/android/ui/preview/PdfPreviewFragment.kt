package com.r2manager.android.ui.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.r2manager.android.R
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.databinding.FragmentPreviewPdfBinding
import com.r2manager.android.ui.common.BaseFragment
import com.r2manager.android.ui.common.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * PDF 预览：`PdfRenderer` 逐页渲染（≤ 25MB）。
 */
class PdfPreviewFragment : BaseFragment<FragmentPreviewPdfBinding>() {

    private val viewModel: PreviewViewModel by activityViewModels {
        ViewModelFactory.of(container) { PreviewViewModel(container) }
    }

    private var pageAdapter: PdfPageAdapter? = null

    override fun inflateBinding(inflater: LayoutInflater, parent: ViewGroup?): FragmentPreviewPdfBinding =
        FragmentPreviewPdfBinding.inflate(inflater, parent, false)

    override fun onBind(binding: FragmentPreviewPdfBinding) {
        binding.previewPdfRecycler.layoutManager = LinearLayoutManager(requireContext())
        val key = arguments?.getString(PreviewActivity.EXTRA_KEY).orEmpty()

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    PreviewUiState.Loading -> {
                        binding.previewPdfProgress.isVisible = true
                        binding.previewPdfMessage.isVisible = false
                    }

                    is PreviewUiState.Pdf -> {
                        binding.previewPdfProgress.isVisible = false
                        binding.previewPdfMessage.isVisible = false
                        binding.previewPdfRecycler.isVisible = true
                        if (pageAdapter == null) {
                            pageAdapter = PdfPageAdapter(state.file).also {
                                binding.previewPdfRecycler.adapter = it
                            }
                        }
                    }

                    is PreviewUiState.TooLarge -> showMessage(binding, R.string.preview_download_to_open)
                    is PreviewUiState.Error -> {
                        binding.previewPdfRecycler.isVisible = false
                        binding.previewPdfProgress.isVisible = false
                        binding.previewPdfMessage.isVisible = true
                        binding.previewPdfMessage.text = getString(state.error.messageResId)
                    }

                    else -> Unit
                }
            }
        }
        viewModel.open(key, PreviewKind.PDF)
    }

    private fun showMessage(binding: FragmentPreviewPdfBinding, resId: Int) {
        binding.previewPdfRecycler.isVisible = false
        binding.previewPdfProgress.isVisible = false
        binding.previewPdfMessage.isVisible = true
        binding.previewPdfMessage.setText(resId)
    }

    override fun onDestroyView() {
        pageAdapter?.close()
        pageAdapter = null
        super.onDestroyView()
    }
}
