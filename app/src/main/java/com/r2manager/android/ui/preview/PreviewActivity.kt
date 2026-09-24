package com.r2manager.android.ui.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.r2manager.android.R
import com.r2manager.android.core.util.UriUtils
import com.r2manager.android.domain.model.ObjectMeta
import com.r2manager.android.domain.transfer.DownloadRequest
import com.r2manager.android.domain.transfer.DownloadTargetResolver
import com.r2manager.android.appContainer
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.databinding.ActivityPreviewBinding
import com.r2manager.android.ui.common.ViewModelFactory
import com.r2manager.android.ui.common.applyStatusBarPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 预览页容器：按 [PreviewKind] 装载对应子 Fragment，并共享一个 [PreviewViewModel]。
 *
 * 子页面：图片（[ImagePreviewFragment]）/ 文本（[TextPreviewFragment]）/ PDF（[PdfPreviewFragment]）/
 * 视频（[VideoPreviewFragment]）/ 其它（[OtherPreviewFragment]）。
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private val container by lazy { applicationContext.appContainer() }
    private val viewModel: PreviewViewModel by viewModels {
        ViewModelFactory.of(container) { PreviewViewModel(container) }
    }
    private lateinit var downloadTargetResolver: DownloadTargetResolver
    private var pendingDownload: DownloadRequest? = null

    private val downloadTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                UriUtils.persistTreePermission(this, uri, flags)
                pendingDownload?.let { enqueueDownload(it, uri) }
            }
            pendingDownload = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.previewToolbar.applyStatusBarPadding()
        binding.previewToolbar.setNavigationIcon(R.drawable.ic_back)
        binding.previewToolbar.setNavigationOnClickListener { finish() }
        downloadTargetResolver = DownloadTargetResolver(this)

        val key = intent.getStringExtra(EXTRA_KEY).orEmpty()
        val bucket = intent.getStringExtra(EXTRA_BUCKET).orEmpty()
        val kind = readKind()
        binding.previewToolbar.title = key.substringAfterLast('/').ifBlank { getString(R.string.app_name) }
        binding.previewToolbar.menu.add(0, MENU_DOWNLOAD, 0, R.string.action_download).apply {
            setIcon(R.drawable.ic_down)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            contentDescription = getString(R.string.action_download)
        }
        binding.previewToolbar.menu.add(0, MENU_SHARE, 1, R.string.action_share).apply {
            setIcon(R.drawable.ic_share)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            contentDescription = getString(R.string.action_share)
        }
        binding.previewToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_DOWNLOAD -> {
                    requestDownload(key, bucket)
                    true
                }
                MENU_SHARE -> {
                    shareObject(key)
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            val fragment: Fragment = when (kind) {
                PreviewKind.IMAGE -> ImagePreviewFragment()
                PreviewKind.TEXT -> TextPreviewFragment()
                PreviewKind.PDF -> PdfPreviewFragment()
                PreviewKind.VIDEO -> VideoPreviewFragment()
                PreviewKind.OTHER -> OtherPreviewFragment()
            }
            fragment.arguments = Bundle().apply {
                putString(EXTRA_KEY, key)
                putString(EXTRA_KIND, kind.name)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.preview_container, fragment)
                .commit()
        }
    }

    private fun readKind(): PreviewKind {
        val name = intent.getStringExtra(EXTRA_KIND) ?: return PreviewKind.OTHER
        return runCatching { PreviewKind.valueOf(name) }.getOrDefault(PreviewKind.OTHER)
    }

    /** 使用当前对象元数据创建下载任务，并在需要时请求 SAF 目录授权。
     * @param key 文件对象 key
     * @param bucket 存储桶名称
     */
    private fun requestDownload(key: String, bucket: String) {
        val meta = currentMeta() ?: run {
            Toast.makeText(this, R.string.state_loading, Toast.LENGTH_SHORT).show()
            return
        }
        val resolvedBucket = bucket.ifBlank { container.settingsRepository.settings().value.currentBucket }
        val request = DownloadRequest(
            bucket = resolvedBucket,
            key = key,
            size = meta.contentLength,
            relativePath = key.substringBeforeLast('/', "").ifBlank { null }
        )
        val treeUri = downloadTargetResolver.resolveTreeUri()
        if (treeUri != null) {
            enqueueDownload(request, treeUri)
        } else {
            pendingDownload = request
            downloadTreeLauncher.launch(null)
        }
    }

    /** 将单文件下载任务加入现有传输队列。
     * @param request 单文件下载请求
     * @param treeUri 用户授权的目标目录
     */
    private fun enqueueDownload(request: DownloadRequest, treeUri: Uri) {
        lifecycleScope.launch {
            val queued = try {
                container.transferEngine.enqueueDownload(listOf(request), treeUri).isNotEmpty()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                false
            }
            Toast.makeText(
                this@PreviewActivity,
                if (queued) R.string.preview_download_queued else R.string.error_unknown,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** 通过系统分享面板分享对象公开链接。
     * @param key 文件对象 key
     */
    private fun shareObject(key: String) {
        val url = viewModel.publicUrlOf(key)
        if (url.isBlank()) {
            Toast.makeText(this, R.string.preview_no_public_url, Toast.LENGTH_SHORT).show()
            return
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.action_share)))
    }

    /** 获取当前预览对象的元数据。 */
    private fun currentMeta(): ObjectMeta? = when (val state = viewModel.state.value) {
        is PreviewUiState.Image -> state.meta
        is PreviewUiState.Text -> state.meta
        is PreviewUiState.Pdf -> state.meta
        is PreviewUiState.Video -> state.meta
        is PreviewUiState.Info -> state.meta
        is PreviewUiState.TooLarge -> state.meta
        else -> null
    }

    companion object {
        private const val MENU_DOWNLOAD = 1
        private const val MENU_SHARE = 2

        /** 对象 key 参数。 */
        const val EXTRA_KEY = "preview_key"

        /** 预览分组参数（[PreviewKind] 名称）。 */
        const val EXTRA_KIND = "preview_kind"

        /** 当前存储桶参数。 */
        const val EXTRA_BUCKET = "preview_bucket"

        /**
         * 构造预览 Intent。
         *
         * @param context 上下文
         * @param key 对象 key
         * @param kind 预览分组
         * @param bucket 当前存储桶
         */
        fun intent(context: Context, key: String, kind: PreviewKind, bucket: String = ""): Intent =
            Intent(context, PreviewActivity::class.java).apply {
                putExtra(EXTRA_KEY, key)
                putExtra(EXTRA_KIND, kind.name)
                putExtra(EXTRA_BUCKET, bucket)
            }
    }
}
