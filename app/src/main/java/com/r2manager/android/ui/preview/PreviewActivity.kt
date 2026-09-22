package com.r2manager.android.ui.preview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.databinding.ActivityPreviewBinding
import com.r2manager.android.ui.common.ViewModelFactory
import com.r2manager.android.ui.common.applyStatusBarPadding

/**
 * 预览页容器：按 [PreviewKind] 装载对应子 Fragment，并共享一个 [PreviewViewModel]。
 *
 * 子页面：图片（[ImagePreviewFragment]）/ 文本（[TextPreviewFragment]）/ PDF（[PdfPreviewFragment]）/
 * 视频（[VideoPreviewFragment]）/ 其它（[OtherPreviewFragment]）。
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.previewToolbar.applyStatusBarPadding()
        binding.previewToolbar.setNavigationIcon(R.drawable.ic_back)
        binding.previewToolbar.setNavigationOnClickListener { finish() }

        val key = intent.getStringExtra(EXTRA_KEY).orEmpty()
        val kind = readKind()
        binding.previewToolbar.title = key.substringAfterLast('/').ifBlank { getString(R.string.app_name) }

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

    companion object {
        /** 对象 key 参数。 */
        const val EXTRA_KEY = "preview_key"

        /** 预览分组参数（[PreviewKind] 名称）。 */
        const val EXTRA_KIND = "preview_kind"

        /**
         * 构造预览 Intent。
         *
         * @param context 上下文
         * @param key 对象 key
         * @param kind 预览分组
         */
        fun intent(context: Context, key: String, kind: PreviewKind): Intent =
            Intent(context, PreviewActivity::class.java).apply {
                putExtra(EXTRA_KEY, key)
                putExtra(EXTRA_KIND, kind.name)
            }
    }
}
