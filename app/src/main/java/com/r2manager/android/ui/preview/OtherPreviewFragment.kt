package com.r2manager.android.ui.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.r2manager.android.R
import com.r2manager.android.core.mime.FileTypes
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.core.util.ByteFormat
import com.r2manager.android.core.util.TimeFormat
import com.r2manager.android.core.util.UriUtils
import com.r2manager.android.databinding.FragmentPreviewOtherBinding
import com.r2manager.android.domain.model.ObjectMeta
import com.r2manager.android.domain.transfer.DownloadTargetResolver
import com.r2manager.android.ui.common.BaseFragment
import com.r2manager.android.ui.common.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * 其它类型预览：信息卡（文件名 / 大小 / 类型 / 时间 / 公开链接）+「下载后打开」。
 */
class OtherPreviewFragment : BaseFragment<FragmentPreviewOtherBinding>() {

    private val viewModel: PreviewViewModel by activityViewModels {
        ViewModelFactory.of(container) { PreviewViewModel(container) }
    }

    private lateinit var downloadTargetResolver: DownloadTargetResolver
    private var publicUrl: String = ""
    private var objectKey: String = ""
    private var objectSize: Long = 0L

    private val downloadTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                UriUtils.persistTreePermission(requireContext(), uri, flags)
                enqueueDownload(uri)
            }
        }

    override fun inflateBinding(inflater: LayoutInflater, parent: ViewGroup?): FragmentPreviewOtherBinding =
        FragmentPreviewOtherBinding.inflate(inflater, parent, false)

    override fun onBind(binding: FragmentPreviewOtherBinding) {
        downloadTargetResolver = DownloadTargetResolver(requireContext())
        objectKey = arguments?.getString(PreviewActivity.EXTRA_KEY).orEmpty()

        binding.previewOtherCopy.setOnClickListener { copyUrl() }
        binding.previewOtherDownload.setOnClickListener { startDownload() }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is PreviewUiState.Info -> render(binding, state.meta, state.publicUrl)
                    is PreviewUiState.Error -> {
                        binding.previewOtherMessage.isVisible = true
                        binding.previewOtherMessage.text = getString(state.error.messageResId)
                    }

                    else -> Unit
                }
            }
        }
        viewModel.open(objectKey, PreviewKind.OTHER)
    }

    private fun render(binding: FragmentPreviewOtherBinding, meta: ObjectMeta, url: String) {
        publicUrl = url
        objectSize = meta.contentLength
        binding.previewOtherName.text = meta.key.substringAfterLast('/').ifBlank { meta.key }
        binding.previewOtherSize.text = ByteFormat.size(meta.contentLength)
        binding.previewOtherType.text = meta.contentType
            ?: FileTypes.extensionOf(meta.key).ifBlank { getString(R.string.preview_unknown_type) }
        binding.previewOtherTime.text = TimeFormat.display(meta.lastModifiedIso)
        binding.previewOtherUrl.text = url.ifBlank { getString(R.string.preview_no_public_url) }
        binding.previewOtherMessage.isVisible = false
    }

    private fun copyUrl() {
        if (publicUrl.isBlank()) {
            toast(R.string.preview_no_public_url)
            return
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("r2_url", publicUrl))
        toast(R.string.toast_copied)
    }

    private fun startDownload() {
        if (objectKey.isBlank()) {
            return
        }
        val existing = downloadTargetResolver.resolveTreeUri()
        if (existing != null) {
            enqueueDownload(existing)
        } else {
            downloadTreeLauncher.launch(null)
        }
    }

    private fun enqueueDownload(treeUri: Uri) {
        val bucket = container.settingsRepository.settings().value.currentBucket
        val relativePath = objectKey.substringBeforeLast('/', "").ifBlank { null }
        val request = com.r2manager.android.domain.transfer.DownloadRequest(
            bucket = bucket,
            key = objectKey,
            size = objectSize,
            relativePath = relativePath
        )
        lifecycleScope.launch {
            val ids = runCatching {
                container.transferEngine.enqueueDownload(listOf(request), treeUri)
            }.getOrDefault(emptyList())
            if (ids.isNotEmpty()) {
                toast(R.string.preview_download_queued)
            } else {
                toast(R.string.error_unknown)
            }
        }
    }
}
