package com.r2manager.android.ui.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.r2manager.android.R
import com.r2manager.android.domain.model.CopyFormat
import com.r2manager.android.domain.model.ObjectInfo
import com.r2manager.android.domain.model.UploadSource
import com.r2manager.android.domain.transfer.DownloadTargetResolver
import com.r2manager.android.ui.common.BaseFragment
import com.r2manager.android.ui.common.RecyclerItemDecoration
import com.r2manager.android.ui.common.ViewModelFactory
import com.r2manager.android.ui.common.applyStatusBarPadding
import com.r2manager.android.ui.preview.PreviewActivity
import com.r2manager.android.core.util.UriUtils
import com.r2manager.android.databinding.FragmentBrowserBinding
import kotlinx.coroutines.launch

/**
 * 文件浏览页（P4-A 主链路）。
 *
 * 结构：AppBar（桶名 + 切换 + 溢出菜单）+ 搜索行 + 面包屑 + 五态内容区 + 底部多选条 + 上传 FAB。
 * 交互：进入文件夹、预览、多选、上传（SAF / Photo Picker / 拍照）、下载（统一 SAF 目录）、复制链接、删除。
 */
class BrowserFragment : BaseFragment<FragmentBrowserBinding>(),
    ItemActionsSheet.Listener,
    FilterSortSheet.Listener,
    UploadSourceSheet.Listener,
    BucketSwitchSheet.Listener,
    CopyUrlSheet.Listener {

    private val viewModel: BrowserViewModel by viewModels {
        ViewModelFactory.of(container) { BrowserViewModel(container) }
    }

    private lateinit var listAdapter: ObjectListAdapter
    private lateinit var breadcrumbAdapter: BreadcrumbAdapter
    private lateinit var thumbnailManager: ThumbnailRequestManager
    private lateinit var downloadTargetResolver: DownloadTargetResolver

    private var pendingDownloadKeys: List<String> = emptyList()
    private var cameraOutputUri: Uri? = null

    /**
     * 面包屑 RecyclerView（来自 `view_breadcrumb.xml`）。
     * `<include>` 未带 id，ViewBinding 不会为它生成字段，故从根视图取。
     */
    private val breadcrumbView: RecyclerView get() = binding.root.findViewById(R.id.browser_breadcrumb)

    /** 活动筛选指示 Chip（来自 `view_filter_chip.xml`），同 [breadcrumbView] 的取法。 */
    private val filterChip: Chip get() = binding.root.findViewById(R.id.filter_chip)

    // ==================== 结果回调 ====================

    private val safUploadLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.onUploadPicked(uris)
        }
    }

    private val photoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.onUploadPicked(uris)
            }
        }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraOutputUri
        cameraOutputUri = null
        if (success && uri != null) {
            viewModel.onUploadPicked(listOf(uri))
        }
    }

    private val downloadTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            UriUtils.persistTreePermission(requireContext(), uri, flags)
            viewModel.downloadKeys(pendingDownloadKeys, uri)
        }
        pendingDownloadKeys = emptyList()
    }

    override fun inflateBinding(inflater: LayoutInflater, parent: ViewGroup?): FragmentBrowserBinding =
        FragmentBrowserBinding.inflate(inflater, parent, false)

    override fun onBind(binding: FragmentBrowserBinding) {
        thumbnailManager = ThumbnailRequestManager(container.thumbnailLoader, viewLifecycleOwner.lifecycleScope)
        downloadTargetResolver = DownloadTargetResolver(requireContext())

        setupToolbar()
        setupLists()
        setupMultiselectBar()
        setupFilterChip()
        setupBackPress()

        binding.browserFabUpload.setOnClickListener { viewModel.requestUpload() }
        binding.browserSwipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.browserStateView.showLoading(getString(R.string.state_loading))

        observeState()
        observeEvents()
        viewModel.start()
    }

    override fun onResume() {
        super.onResume()
        // 处理分享入口（ACTION_SEND / ACTION_SEND_MULTIPLE，R-29）
        handleShareIntent(requireActivity().intent)
    }

    override fun onDestroyView() {
        thumbnailManager.clear()
        super.onDestroyView()
    }

    // ==================== 初始化 ====================

    private fun setupToolbar() {
        binding.browserToolbar.applyStatusBarPadding()
        binding.browserToolbar.setNavigationIcon(R.drawable.ic_swap)
        binding.browserToolbar.setNavigationOnClickListener { viewModel.requestBucketSwitch() }
        binding.browserToolbar.inflateMenu(R.menu.menu_browser)
        binding.browserToolbar.menu.add(0, MENU_SEARCH, 0, R.string.action_search).apply {
            setIcon(R.drawable.ic_search)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS or android.view.MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        }
        binding.browserToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SEARCH -> {
                    toggleSearch()
                    true
                }

                R.id.menu_refresh -> {
                    viewModel.refresh()
                    true
                }

                R.id.menu_toggle_view -> {
                    viewModel.toggleViewMode()
                    true
                }

                R.id.menu_sort -> {
                    showFilterSort()
                    true
                }

                R.id.menu_select_all -> {
                    viewModel.selectAll()
                    true
                }

                else -> false
            }
        }
        binding.browserSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                viewModel.setQuery(s?.toString().orEmpty())

            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.browserSearchClose.setOnClickListener {
            binding.browserSearchInput.setText("")
            toggleSearch(show = false)
        }
    }

    private fun setupLists() {
        breadcrumbAdapter = BreadcrumbAdapter { prefix -> viewModel.navigateTo(prefix) }
        breadcrumbView.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        breadcrumbView.adapter = breadcrumbAdapter

        listAdapter = ObjectListAdapter(thumbnailManager, object : ObjectListAdapter.Listener {
            override fun onClick(info: ObjectInfo) = viewModel.onItemClick(info)
            override fun onLongClick(info: ObjectInfo): Boolean = viewModel.onItemLongClick(info)
            override fun onMoreClick(info: ObjectInfo, anchor: View) {
                ItemActionsSheet.newInstance(info).show(childFragmentManager, TAG_ITEM_ACTIONS)
            }
        })
        applyViewMode(BrowserUiState.ViewMode.LIST)
        binding.browserRecycler.adapter = listAdapter
        binding.browserRecycler.setHasFixedSize(true)
        binding.browserRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    // GridLayoutManager 继承 LinearLayoutManager，两种视图模式都能拿到末位可见项
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return
                    val last = lm.childCount
                    val total = lm.itemCount
                    if (total > 0 && lm.findLastVisibleItemPosition() >= total - LOAD_MORE_THRESHOLD) {
                        viewModel.loadMore()
                    }
                }
            }
        })
    }

    private fun setupMultiselectBar() {
        binding.browserMultiselectDownload.setOnClickListener {
            viewModel.requestDownload(viewModel.state.value.selectedKeys.toList())
        }
        binding.browserMultiselectShare.setOnClickListener {
            shareSelected()
        }
        binding.browserMultiselectDelete.setOnClickListener {
            viewModel.requestDelete(viewModel.state.value.selectedKeys.toList())
        }
        binding.browserMultiselectSelectAll.setOnClickListener { viewModel.selectAll() }
        binding.browserMultiselectClose.setOnClickListener { viewModel.clearSelection() }
    }

    private fun setupFilterChip() {
        filterChip.setOnClickListener { showFilterSort() }
        filterChip.setOnCloseIconClickListener {
            binding.browserSearchInput.setText("")
            viewModel.setQuery("")
            viewModel.setFilter(BrowserUiState.FileFilter.ALL)
        }
    }

    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.state.value.isSelectionMode) {
                    viewModel.clearSelection()
                } else if (!binding.browserSearchBar.isVisible) {
                    // 搜索行未展开：交给系统返回
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                } else if (binding.browserSearchInput.text?.isNotEmpty() == true) {
                    binding.browserSearchInput.setText("")
                } else {
                    toggleSearch(show = false)
                }
            }
        })
    }

    // ==================== 状态渲染 ====================

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state -> render(state) }
        }
    }

    private fun render(state: BrowserUiState) {
        // 标题 = 桶名；面包屑
        binding.browserToolbar.title = state.bucket.ifBlank { getString(R.string.app_name) }
        breadcrumbAdapter.submit(state.bucket, state.prefix)

        // 活动筛选 / 搜索指示 Chip
        val hasActiveFilter = state.query.isNotBlank() || state.filter != BrowserUiState.FileFilter.ALL
        filterChip.isVisible = hasActiveFilter
        filterChip.text = if (state.filter != BrowserUiState.FileFilter.ALL) {
            filterLabel(state.filter)
        } else {
            getString(R.string.browser_filter_active)
        }

        listAdapter.bucket = state.bucket
        listAdapter.selectionMode = state.isSelectionMode
        listAdapter.selectedKeys = state.selectedKeys
        applyViewMode(state.viewMode)
        listAdapter.submitList(state.visibleObjects) {
            binding.browserRecycler.post { listAdapter.notifyDataSetChanged() }
        }

        binding.browserSwipeRefresh.isRefreshing = state.refreshing
        binding.browserMultiselectBar.isVisible = state.isSelectionMode
        binding.browserFabUpload.isVisible = !state.isSelectionMode
        binding.browserMultiselectCount.text =
            getString(R.string.browser_selected_count, state.selectedKeys.size)
        binding.browserMultiselectSelectAll.isVisible = state.selectedKeys.size < state.visibleObjects.size

        renderContentState(state)
    }

    private fun renderContentState(state: BrowserUiState) {
        val stateView = binding.browserStateView
        when {
            state.isLoading && state.objects.isEmpty() -> {
                stateView.showLoading(getString(R.string.state_loading))
                binding.browserRecycler.isVisible = false
            }

            state.error != null -> {
                stateView.showError(
                    error = state.error,
                    primaryLabelRes = R.string.action_retry,
                    onPrimary = { viewModel.refresh() }
                )
                binding.browserRecycler.isVisible = false
            }

            state.isOfflineEmpty -> {
                stateView.showOffline(actionRes = R.string.action_retry) { viewModel.refresh() }
                binding.browserRecycler.isVisible = false
            }

            state.isEmptyDirectory -> {
                stateView.showEmpty(
                    descRes = R.string.browser_empty_desc,
                    actionRes = R.string.action_upload
                ) { viewModel.requestUpload() }
                binding.browserRecycler.isVisible = false
            }

            state.isNoResult -> {
                stateView.showNoResult(descRes = R.string.browser_no_result_desc, actionRes = R.string.browser_clear_filter) {
                    viewModel.setQuery("")
                    viewModel.setFilter(BrowserUiState.FileFilter.ALL)
                }
                binding.browserRecycler.isVisible = false
            }

            else -> {
                stateView.hide()
                binding.browserRecycler.isVisible = true
            }
        }
    }

    private fun applyViewMode(mode: BrowserUiState.ViewMode) {
        val desiredSpan = when (mode) {
            BrowserUiState.ViewMode.LIST -> 1
            BrowserUiState.ViewMode.GRID -> resources.getInteger(R.integer.grid_span_portrait)
        }
        val current = binding.browserRecycler.layoutManager
        val currentSpan = (current as? GridLayoutManager)?.spanCount
        if (mode == BrowserUiState.ViewMode.LIST && current !is LinearLayoutManager) {
            binding.browserRecycler.layoutManager = LinearLayoutManager(requireContext())
        } else if (mode == BrowserUiState.ViewMode.GRID && currentSpan != desiredSpan) {
            binding.browserRecycler.layoutManager = GridLayoutManager(requireContext(), desiredSpan)
        }
        if (binding.browserRecycler.itemDecorationCount > 0) {
            repeat(binding.browserRecycler.itemDecorationCount) { binding.browserRecycler.removeItemDecorationAt(0) }
        }
        val spacing = resources.getDimensionPixelSize(R.dimen.space_2)
        when (mode) {
            BrowserUiState.ViewMode.LIST -> binding.browserRecycler.addItemDecoration(
                RecyclerItemDecoration.forLinearList(resources.getDimensionPixelSize(R.dimen.divider_height))
            )

            BrowserUiState.ViewMode.GRID -> binding.browserRecycler.addItemDecoration(
                RecyclerItemDecoration.forGrid(spacing, resources.getInteger(R.integer.grid_span_portrait))
            )
        }
    }

    // ==================== 事件处理 ====================

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { event -> handleEvent(event) }
        }
    }

    private fun handleEvent(event: BrowserEvent) {
        when (event) {
            is BrowserEvent.Message -> snackbar(event.resId)
            is BrowserEvent.MessageText -> snackbar(event.text)
            is BrowserEvent.OpenPreview -> {
                startActivity(PreviewActivity.intent(requireContext(), event.key, event.kind))
            }

            is BrowserEvent.OpenUrlSheet -> {
                viewModel.lastCopyFormat = container.settingsRepository.settings().value.lastCopyFormat
                CopyUrlSheet.newInstance(
                    name = viewModel.state.value.objects.firstOrNull { it.key == event.info.key }?.name
                        ?: event.info.name,
                    url = viewModel.publicUrlOf(event.info),
                    initialFormat = viewModel.lastCopyFormat
                ).show(childFragmentManager, TAG_COPY_URL)
            }

            is BrowserEvent.ShowBuckets -> {
                BucketSwitchSheet.newInstance(event.buckets.map { it.name }, event.currentBucket)
                    .show(childFragmentManager, TAG_BUCKET_SWITCH)
            }

            BrowserEvent.RequestUploadSource -> {
                UploadSourceSheet().show(childFragmentManager, TAG_UPLOAD_SOURCE)
            }

            BrowserEvent.RequestSafUpload -> safUploadLauncher.launch(arrayOf("*/*"))

            BrowserEvent.RequestPhotoPicker -> photoPickerLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                )
            )

            BrowserEvent.RequestCamera -> launchCamera()

            is BrowserEvent.RequestDownloadDir -> {
                pendingDownloadKeys = event.keys
                val existing = downloadTargetResolver.resolveTreeUri()
                if (existing != null) {
                    viewModel.downloadKeys(event.keys, existing)
                    pendingDownloadKeys = emptyList()
                } else {
                    downloadTreeLauncher.launch(null)
                }
            }

            is BrowserEvent.ConfirmDelete -> {
                DeleteConfirmDialog.showDelete(requireContext(), event.names) {
                    viewModel.confirmDelete(event.keys)
                }
            }

            is BrowserEvent.ConfirmOverwrite -> {
                DeleteConfirmDialog.showOverwrite(requireContext(), event.conflictingNames) {
                    viewModel.confirmUploadOverwrite()
                }
            }
        }
    }

    // ==================== 各面板回调 ====================

    override fun onItemAction(action: ItemActionsSheet.Action, info: ObjectInfo) {
        when (action) {
            ItemActionsSheet.Action.OPEN -> viewModel.onItemClick(info)
            ItemActionsSheet.Action.DOWNLOAD -> viewModel.requestDownload(listOf(info.key))
            ItemActionsSheet.Action.COPY_URL -> viewModel.requestCopyUrl(info)
            ItemActionsSheet.Action.SHARE -> shareInfo(info)
            ItemActionsSheet.Action.DELETE -> viewModel.requestDelete(listOf(info.key))
        }
    }

    override fun onFilterSortApplied(sort: BrowserUiState.SortOrder, filter: BrowserUiState.FileFilter) {
        viewModel.setSort(sort)
        viewModel.setFilter(filter)
    }

    override fun onUploadSourceSelected(source: UploadSource) {
        when (source) {
            UploadSource.PHOTO_PICKER -> photoPickerLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                )
            )

            UploadSource.DOCUMENT_SAF -> safUploadLauncher.launch(arrayOf("*/*"))
            UploadSource.CAMERA -> launchCamera()
        }
    }

    override fun onBucketSelected(name: String) {
        viewModel.switchBucket(name)
    }

    override fun onCopyUrl(text: String, format: CopyFormat) {
        viewModel.lastCopyFormat = format
        viewModel.copyToClipboard(text)
    }

    override fun onCopyUrlDismissed() = Unit

    // ==================== 工具 ====================

    private fun toggleSearch(show: Boolean = !binding.browserSearchBar.isVisible) {
        binding.browserSearchBar.isVisible = show
        if (show) {
            binding.browserSearchInput.requestFocus()
        } else {
            viewModel.setQuery("")
        }
    }

    private fun showFilterSort() {
        val state = viewModel.state.value
        FilterSortSheet.newInstance(state.sort, state.filter).show(childFragmentManager, TAG_FILTER_SORT)
    }

    private fun filterLabel(filter: BrowserUiState.FileFilter): String = when (filter) {
        BrowserUiState.FileFilter.ALL -> getString(R.string.browser_filter_all)
        BrowserUiState.FileFilter.FOLDER -> getString(R.string.browser_filter_folder)
        BrowserUiState.FileFilter.IMAGE -> getString(R.string.browser_filter_image)
        BrowserUiState.FileFilter.VIDEO -> getString(R.string.browser_filter_video)
        BrowserUiState.FileFilter.DOC -> getString(R.string.browser_filter_doc)
    }

    private fun launchCamera() {
        val uri = createCameraOutputUri()
        if (uri == null) {
            toast(R.string.browser_camera_unavailable)
            return
        }
        cameraOutputUri = uri
        cameraLauncher.launch(uri)
    }

    private fun createCameraOutputUri(): Uri? = runCatching {
        val dir = java.io.File(requireContext().cacheDir, "camera").apply { mkdirs() }
        val file = java.io.File(dir, "capture_${System.currentTimeMillis()}.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
    }.getOrNull()

    private fun shareInfo(info: ObjectInfo) {
        val url = viewModel.publicUrlOf(info)
        shareText(if (url.isBlank()) info.name else url)
    }

    private fun shareSelected() {
        val selected = viewModel.state.value.visibleObjects.filter { it.key in viewModel.state.value.selectedKeys }
        val urls = selected.map { viewModel.publicUrlOf(it) }.filter { it.isNotBlank() }
        if (urls.isEmpty()) {
            snackbar(R.string.browser_share_empty)
            return
        }
        shareText(urls.joinToString("\n"))
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.action_share))) }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()

            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            intent.action = null
            viewModel.onUploadPicked(uris)
        }
    }

    private companion object {
        const val MENU_SEARCH = 0x7f0f0001
        const val LOAD_MORE_THRESHOLD = 5

        const val TAG_ITEM_ACTIONS = "item_actions"
        const val TAG_COPY_URL = "copy_url"
        const val TAG_BUCKET_SWITCH = "bucket_switch"
        const val TAG_UPLOAD_SOURCE = "upload_source"
        const val TAG_FILTER_SORT = "filter_sort"
    }
}
