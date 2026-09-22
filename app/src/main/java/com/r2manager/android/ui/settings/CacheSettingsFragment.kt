package com.r2manager.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.CacheConstants
import com.r2manager.android.core.util.ByteFormat
import com.r2manager.android.databinding.FragmentCacheSettingsBinding
import kotlinx.coroutines.launch

/**
 * 缓存设置（P4-B）。
 *
 * 缩略图 / 列表缓存开关与缩略图大小上限（[CacheConstants.THUMBNAIL_MAX_OPTIONS_MB]）；
 * 实时展示占用、条目数、已缓存目录与缓存目录，支持清理缓存与展示 / 复制缓存目录路径。
 */
class CacheSettingsFragment : Fragment() {

    private var _binding: FragmentCacheSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: CacheSettingsViewModel by viewModels {
        CacheSettingsViewModel.factory(requireContext().appContainer())
    }

    private val limitOptionsMb = CacheConstants.THUMBNAIL_MAX_OPTIONS_MB
    private var bindingFlags = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCacheSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindLimitDropdown()
        bindSwitches()

        binding.btnBack.setOnClickListener { back() }
        binding.btnClear.setOnClickListener { confirmClear() }
        binding.btnRefresh.setOnClickListener { refreshStats() }
        // 「打开缓存目录」按 PRD §5.4 改为「复制路径」：绝对路径已在下方行内展示（tvDir），此处只提供复制。
        binding.btnOpenDir.setText(R.string.cache_copy_path)
        binding.btnOpenDir.setOnClickListener { copyCacheDir() }

        applySettings()
        refreshStats()
    }

    private fun bindLimitDropdown() {
        val labels = limitOptionsMb.map { ByteFormat.size(it.toLong() * MB) }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            labels
        )
        binding.acLimit.setAdapter(adapter)
        binding.acLimit.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                if (bindingFlags) return@OnItemClickListener
                val enabled = viewModel.settings().thumbnailCacheEnabled
                val maxBytes = limitOptionsMb[position].toLong() * MB
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.setThumbnailCache(enabled, maxBytes)
                    refreshStats()
                }
            }
    }

    private fun bindSwitches() {
        binding.switchThumb.setOnCheckedChangeListener { _, checked ->
            if (bindingFlags) return@setOnCheckedChangeListener
            val maxBytes = viewModel.settings().thumbnailCacheMaxBytes
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.setThumbnailCache(checked, maxBytes)
                refreshStats()
            }
        }
        binding.switchList.setOnCheckedChangeListener { _, checked ->
            if (bindingFlags) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.setObjectListCacheEnabled(checked)
                refreshStats()
            }
        }
    }

    private fun applySettings() {
        val settings = viewModel.settings()
        bindingFlags = true
        binding.switchThumb.isChecked = settings.thumbnailCacheEnabled
        binding.switchList.isChecked = settings.objectListCacheEnabled
        binding.acLimit.setText(nearestLimitLabel(settings.thumbnailCacheMaxBytes), false)
        bindingFlags = false
    }

    private fun nearestLimitLabel(maxBytes: Long): String {
        val maxMb = (maxBytes / MB).toInt()
        val nearest = limitOptionsMb.minByOrNull { kotlin.math.abs(it - maxMb) }
            ?: limitOptionsMb.first()
        return ByteFormat.size(nearest.toLong() * MB)
    }

    private fun refreshStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (thumb, list) = runCatching { viewModel.cacheStats() }.getOrNull() ?: return@launch
            val used = thumb.usedBytes + list.usedBytes
            val max = thumb.maxBytes
            binding.tvUsage.text = getString(
                R.string.cache_usage_value,
                ByteFormat.size(used),
                ByteFormat.size(max)
            )
            binding.progress.progress =
                if (max > 0) ((used * 100) / max).toInt().coerceIn(0, 100) else 0
            binding.tvEntries.text = thumb.count.toString()
            binding.tvFolders.text = list.count.toString()
            binding.tvDir.text = thumb.directory
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.cache_clear_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.cache_clear) { _, _ -> clearCaches() }
            .show()
    }

    private fun clearCaches() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { viewModel.clearCaches() }
                .onSuccess { (count, freed) ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.cache_cleared, count, ByteFormat.size(freed)),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshStats()
                }
        }
    }

    /**
     * 复制缓存目录绝对路径（PRD §5.4 定案：**展示 + 复制路径**，不跳转系统文件管理器）。
     *
     * 绝对路径已由 [refreshStats] 写入行内 [android.widget.TextView]（`tvDir`）展示；本动作只把该路径
     * 复制到剪贴板并提示「已复制」。原先的 `FileProvider.getUriForFile` + `file://` 兜底已整体删除——
     * `file_paths.xml` 未声明 `filesDir/cache` 该 root，那是一条注定失败、只会误导后来者的路径。
     */
    private fun copyCacheDir() {
        viewLifecycleOwner.lifecycleScope.launch {
            val path = runCatching { viewModel.cacheDirectory() }.getOrDefault("")
            if (path.isBlank()) {
                return@launch
            }
            binding.tvDir.text = path   // 兜底展示（正常情况下 refreshStats 已填）
            copyToClipboard(path)
            toast(R.string.toast_copied)
        }
    }

    /** 复制文本到剪贴板（复用全局「已复制」提示口径）。 */
    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("r2_cache_path", text))
    }

    private fun back() {
        (parentFragment as? SettingsFragment)?.closeSub()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 1 MB 字节数。 */
        private const val MB = 1024L * 1024L
    }
}
