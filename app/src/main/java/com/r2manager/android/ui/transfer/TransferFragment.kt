package com.r2manager.android.ui.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayoutMediator
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.mime.MimeTypes
import com.r2manager.android.core.util.UrlUtils
import com.r2manager.android.databinding.FragmentTransferBinding
import com.r2manager.android.domain.model.TransferStatus
import com.r2manager.android.domain.model.TransferTask
import kotlinx.coroutines.launch

/**
 * 传输任务中心（P4-B）。
 *
 * 结构：顶栏（标题 + 更多菜单）+ 三 Tab（进行中 / 已完成 / 失败，显示计数）+ [androidx.viewpager2.widget.ViewPager2]。
 * 数据：订阅 [TransferViewModel.tasks]（唯一来源为 P3 `TransferEngine.tasks`），按状态分三组投递给 [TransferPagerAdapter]。
 *
 * 进入方式：由主框架（P4-A）的导航承载；P3 `TransferService` 通知点击携带
 * `UiConstants.EXTRA_OPEN_TRANSFER` 指向 `MainActivity`，由 P4-A 切到本页（P4-B 只保证本页被打开时正确展示三 Tab）。
 */
class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: TransferViewModel by viewModels {
        TransferViewModel.factory(requireContext().appContainer())
    }

    private val pagerAdapter: TransferPagerAdapter by lazy {
        TransferPagerAdapter(
            onCancel = ::onCancel,
            onRetry = ::onRetry,
            onRemove = ::onRemove,
            onOpen = ::onOpen,
            onShare = ::onShare,
            onCopyLink = ::onCopyLink
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPager()
        setupMoreMenu()
        observeTasks()
    }

    private fun setupPager() {
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitle(position, 0)
        }.attach()
    }

    private fun setupMoreMenu() {
        binding.btnMore.setOnClickListener {
            val popup = PopupMenu(requireContext(), binding.btnMore)
            popup.menu.add(0, MENU_CLEAR_COMPLETED, 0, getString(R.string.transfer_clear_completed))
            popup.menu.add(0, MENU_RETRY_ALL, 1, getString(R.string.transfer_retry_all))
            popup.menu.add(0, MENU_CANCEL_ALL, 2, getString(R.string.transfer_cancel_all))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_CLEAR_COMPLETED -> viewModel.clearCompleted()
                    MENU_RETRY_ALL -> viewModel.retryAllFailed()
                    MENU_CANCEL_ALL -> viewModel.cancelAll()
                }
                true
            }
            popup.show()
        }
    }

    private fun observeTasks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tasks.collect { all -> render(all) }
            }
        }
    }

    private fun render(all: List<TransferTask>) {
        val running = all.filter {
            it.status == TransferStatus.QUEUED || it.status == TransferStatus.RUNNING
        }
        val completed = all.filter { it.status == TransferStatus.DONE }
        val failed = all.filter {
            it.status == TransferStatus.FAILED || it.status == TransferStatus.CANCELLED
        }

        pagerAdapter.submit(running, completed, failed)

        binding.tabLayout.getTabAt(TransferPagerAdapter.PAGE_RUNNING)?.text =
            tabTitle(TransferPagerAdapter.PAGE_RUNNING, running.size)
        binding.tabLayout.getTabAt(TransferPagerAdapter.PAGE_COMPLETED)?.text =
            tabTitle(TransferPagerAdapter.PAGE_COMPLETED, completed.size)
        binding.tabLayout.getTabAt(TransferPagerAdapter.PAGE_FAILED)?.text =
            tabTitle(TransferPagerAdapter.PAGE_FAILED, failed.size)
    }

    private fun tabTitle(position: Int, count: Int): String = when (position) {
        TransferPagerAdapter.PAGE_RUNNING -> getString(R.string.transfer_tab_running_count, count)
        TransferPagerAdapter.PAGE_COMPLETED -> getString(R.string.transfer_tab_completed_count, count)
        else -> getString(R.string.transfer_tab_failed_count, count)
    }

    // ==================== 卡片动作 ====================

    private fun onCancel(task: TransferTask) {
        viewModel.cancel(task.id)
        toast(R.string.transfer_cancelled_toast)
    }

    private fun onRetry(task: TransferTask) {
        viewModel.retry(task.id)
        toast(R.string.transfer_started_toast)
    }

    private fun onRemove(task: TransferTask) {
        viewModel.removeTask(task.id)
        toast(R.string.transfer_removed_toast)
    }

    private fun onOpen(task: TransferTask) {
        val uri = runCatching { Uri.parse(task.localUri) }.getOrNull()
        if (uri == null) {
            toast(R.string.error_file_not_found)
            return
        }
        val mimeType = resolvedMimeType(uri, task.key)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(requireContext().contentResolver, task.key, uri)
        }
        runCatching { startActivity(intent) }
            .onFailure { toast(R.string.error_file_not_found) }
    }

    private fun onShare(task: TransferTask) {
        val uri = runCatching { Uri.parse(task.localUri) }.getOrNull()
        if (uri == null) {
            toast(R.string.error_file_not_found)
            return
        }
        val mimeType = resolvedMimeType(uri, task.key)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(requireContext().contentResolver, task.key, uri)
        }
        runCatching { startActivity(Intent.createChooser(send, getString(R.string.action_share))) }
            .onFailure { toast(R.string.error_file_not_found) }
    }

    /** 获取文档提供方的 MIME；未提供时按对象扩展名推断。
     * @param uri 下载文件的文档 Uri
     * @param key 对象 key
     */
    private fun resolvedMimeType(uri: Uri, key: String): String {
        val provided = runCatching { requireContext().contentResolver.getType(uri) }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "*/*" }
        val inferred = MimeTypes.resolveContentType(key)
        return if (provided == null ||
            (provided == MimeTypes.DEFAULT_CONTENT_TYPE && inferred != MimeTypes.DEFAULT_CONTENT_TYPE)
        ) inferred else provided
    }

    private fun onCopyLink(task: TransferTask) {
        val settings = requireContext().appContainer().settingsRepository.settings().value
        val base = settings.publicUrl.ifBlank { settings.endpoint }
        val url = UrlUtils.joinPublicUrl(base, task.key)
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(task.key, url))
        toast(R.string.action_copy_link)
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MENU_CLEAR_COMPLETED = 1
        private const val MENU_RETRY_ALL = 2
        private const val MENU_CANCEL_ALL = 3

        /** 供主框架（P4-A）在导航到传输 Tab 时构造实例。 */
        fun newInstance(): TransferFragment = TransferFragment()
    }
}
