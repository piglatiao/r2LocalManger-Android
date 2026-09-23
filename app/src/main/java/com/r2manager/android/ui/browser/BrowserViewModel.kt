package com.r2manager.android.ui.browser

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.r2manager.android.AppContainer
import com.r2manager.android.core.error.ErrorMapper
import com.r2manager.android.core.mime.FileTypes
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.core.util.UriUtils
import com.r2manager.android.domain.model.Bucket
import com.r2manager.android.domain.model.CopyFormat
import com.r2manager.android.domain.model.ObjectInfo
import com.r2manager.android.domain.transfer.DownloadRequest
import com.r2manager.android.ui.common.AppViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 浏览页一次性事件（导航 / 弹层 / 提示）。
 */
sealed interface BrowserEvent {

    /** 资源文案提示。 */
    data class Message(val resId: Int) : BrowserEvent

    /** 纯文本提示。 */
    data class MessageText(val text: String) : BrowserEvent

    /** 打开预览页。 */
    data class OpenPreview(val key: String, val kind: PreviewKind) : BrowserEvent

    /** 弹出「复制链接」面板。 */
    data class OpenUrlSheet(val info: ObjectInfo) : BrowserEvent

    /** 弹出桶切换面板。 */
    data class ShowBuckets(val buckets: List<Bucket>, val currentBucket: String) : BrowserEvent

    /** 请求 UI 弹出上传来源面板。 */
    data object RequestUploadSource : BrowserEvent

    /** 请求 UI 拉起 SAF 文档选择器。 */
    data object RequestSafUpload : BrowserEvent

    /** 请求 UI 拉起照片选择器（Photo Picker）。 */
    data object RequestPhotoPicker : BrowserEvent

    /** 请求 UI 拉起拍照。 */
    data object RequestCamera : BrowserEvent

    /** 请求 UI 选择下载目录（无持久化授权时）。 */
    data class RequestDownloadDir(val keys: List<String>) : BrowserEvent

    /** 请求 UI 确认删除。 */
    data class ConfirmDelete(val keys: List<String>, val names: List<String>) : BrowserEvent

    /** 请求 UI 确认覆盖同名文件后继续上传。 */
    data class ConfirmOverwrite(val conflictingNames: List<String>) : BrowserEvent
}

/**
 * 浏览页 ViewModel（文件列表主链路）。
 *
 * 覆盖：列目录（含分页 / 缓存 / 离线降级）、进入文件夹、搜索 / 筛选 / 排序、多选、
 * 删除、复制链接、上传（含同名冲突 R-28）、下载（统一 SAF 目录）、切桶、缩略图（经适配器）。
 *
 * @param container 依赖容器
 */
class BrowserViewModel(container: AppContainer) : AppViewModel(container) {

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events: Flow<BrowserEvent> = _events.receiveAsFlow()

    /** 待确认覆盖的上传 Uri（用于用户确认后继续）。 */
    private var pendingUploadUris: List<Uri> = emptyList()

    private var started = false

    init {
        appScope.launch {
            container.settingsRepository.settings().collect { settings ->
                val newBucket = settings.currentBucket
                val changed = _state.value.bucket != newBucket
                _state.update { it.copy(bucket = newBucket) }
                if (changed && started) {
                    resetAndLoad()
                }
            }
        }
    }

    /** 页面首次进入时启动加载（幂等）。 */
    fun start() {
        if (started) {
            return
        }
        started = true
        resetAndLoad()
    }

    // ==================== 列目录 ====================

    /** 进入目标目录并加载。 */
    fun navigateTo(prefix: String) {
        _state.update {
            it.copy(
                prefix = com.r2manager.android.core.util.PathUtils.normalizePrefix(prefix),
                objects = emptyList(),
                selectedKeys = emptySet()
            )
        }
        resetAndLoad()
    }

    /** 返回上一级（已在根目录则无操作）。 */
    fun navigateUp(): Boolean {
        val current = _state.value.prefix
        if (current.isEmpty()) {
            return false
        }
        val parent = current.substringBeforeLast('/', "")
        navigateTo(parent)
        return true
    }

    /** 下拉刷新。 */
    fun refresh() {
        load(forceRefresh = true, isRefresh = true)
    }

    /** 触底加载更多。 */
    fun loadMore() {
        val s = _state.value
        if (!s.isTruncated || s.nextToken == null || s.loadingMore || s.refreshing || s.loading) {
            return
        }
        _state.update { it.copy(loadingMore = true) }
        appScope.launch {
            runCatching {
                container.storageRepository.listObjects(
                    prefix = s.prefix,
                    forceRefresh = false,
                    continuationToken = s.nextToken
                )
            }.onSuccess { result ->
                _state.update { st ->
                    val merged = (st.objects + result.objects).distinctBy { it.key }
                    st.copy(
                        objects = merged,
                        nextToken = result.nextContinuationToken,
                        isTruncated = result.isTruncated,
                        loadingMore = false
                    )
                }
            }.onFailure {
                _state.update { st -> st.copy(loadingMore = false) }
            }
        }
    }

    private fun resetAndLoad() {
        _state.update { it.copy(objects = emptyList(), nextToken = null, isTruncated = false, error = null) }
        load(forceRefresh = false, isRefresh = false)
    }

    private fun load(forceRefresh: Boolean, isRefresh: Boolean) {
        val s = _state.value
        _state.update {
            it.copy(
                loading = it.objects.isEmpty(),
                refreshing = isRefresh || it.objects.isNotEmpty(),
                error = null
            )
        }
        appScope.launch {
            val offline = runCatching { container.connectivityMonitor.isOnline.value == false }
                .getOrDefault(false)
            runCatching {
                container.storageRepository.listObjects(prefix = s.prefix, forceRefresh = forceRefresh)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        objects = result.objects,
                        nextToken = result.nextContinuationToken,
                        isTruncated = result.isTruncated,
                        fromCache = result.fromCache,
                        cachedAt = result.cachedAt,
                        offline = result.offline,
                        unconfigured = !result.configured,
                        loading = false,
                        refreshing = false,
                        error = null
                    )
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        offline = offline,
                        error = ErrorMapper.fromThrowable(t, "listObjects", offline)
                    )
                }
            }
        }
    }

    // ==================== 交互 ====================

    /** 点击列表项：文件夹进入，文件预览。 */
    fun onItemClick(info: ObjectInfo) {
        if (_state.value.isSelectionMode) {
            toggleSelect(info)
            return
        }
        if (info.isFolder) {
            navigateTo(info.key)
        } else {
            _events.trySend(BrowserEvent.OpenPreview(info.key, FileTypes.kindOf(info.key)))
        }
    }

    /** 长按：进入多选。 */
    fun onItemLongClick(info: ObjectInfo): Boolean {
        toggleSelect(info)
        return true
    }

    /** 溢出按钮：弹动作面板。 */
    fun onItemMoreClick(info: ObjectInfo) {
        // 位置与动作由 UI 决定；此处仅提供 publicUrl。
    }

    /** 切换某项选中态。 */
    fun toggleSelect(info: ObjectInfo) {
        _state.update {
            val set = it.selectedKeys.toMutableSet()
            if (!set.add(info.key)) {
                set.remove(info.key)
            }
            it.copy(selectedKeys = set)
        }
    }

    /** 全选当前可见项。 */
    fun selectAll() {
        _state.update { it.copy(selectedKeys = it.visibleObjects.map { o -> o.key }.toSet()) }
    }

    /** 退出多选。 */
    fun clearSelection() {
        _state.update { it.copy(selectedKeys = emptySet()) }
    }

    /** 设置视图模式。 */
    fun setViewMode(mode: BrowserUiState.ViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    /** 列表 / 网格互切。 */
    fun toggleViewMode() {
        _state.update {
            val next = if (it.viewMode == BrowserUiState.ViewMode.LIST) {
                BrowserUiState.ViewMode.GRID
            } else {
                BrowserUiState.ViewMode.LIST
            }
            it.copy(viewMode = next)
        }
    }

    /** 设置排序。 */
    fun setSort(order: BrowserUiState.SortOrder) {
        _state.update { it.copy(sort = order) }
    }

    /** 设置筛选。 */
    fun setFilter(filter: BrowserUiState.FileFilter) {
        _state.update { it.copy(filter = filter) }
    }

    /** 设置搜索关键字。 */
    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** 是否有搜索 / 筛选生效。 */
    fun hasActiveQuery(): Boolean {
        val s = _state.value
        return s.query.isNotBlank() || s.filter != BrowserUiState.FileFilter.ALL
    }

    // ==================== 删除 ====================

    /** 请求删除（弹确认）。 */
    fun requestDelete(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        val names = _state.value.objects.filter { it.key in keys }.map { it.name }
        _events.trySend(BrowserEvent.ConfirmDelete(keys, names))
    }

    /** 执行删除。 */
    fun confirmDelete(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        appScope.launch {
            val succeeded = runCatching {
                container.storageRepository.deleteBatch(keys)
            }.getOrNull()
            if (succeeded != null) {
                val failedDeletes = succeeded.errors.map { it.key }
                _state.update { it.copy(selectedKeys = emptySet()) }
                if (failedDeletes.isEmpty()) {
                    _events.trySend(BrowserEvent.Message(com.r2manager.android.R.string.browser_delete_done))
                } else {
                    _events.trySend(BrowserEvent.MessageText("部分删除失败：${failedDeletes.size} 项"))
                }
                load(forceRefresh = true, isRefresh = false)
            } else {
                _events.trySend(BrowserEvent.Message(com.r2manager.android.R.string.error_unknown))
            }
        }
    }

    // ==================== 公开链接 / 复制 ====================

    /** 取对象的公开链接（未配置时为空串）。 */
    fun publicUrlOf(info: ObjectInfo): String = runCatching {
        container.storageRepository.publicUrl(info.key)
    }.getOrDefault("")

    /** 弹复制链接面板。 */
    fun requestCopyUrl(info: ObjectInfo) {
        _events.trySend(BrowserEvent.OpenUrlSheet(info))
    }

    /** 按格式生成链接文本。 */
    fun formatUrl(url: String, format: CopyFormat): String = when (format) {
        CopyFormat.URL -> url
        CopyFormat.HTML -> "<a href=\"$url\">$url</a>"
        CopyFormat.MARKDOWN -> "[$url]($url)"
    }

    /** 复制文本到剪贴板并提示。 */
    fun copyToClipboard(text: String) {
        runCatching {
            val clipboard = container.appContext
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("r2_url", text))
        }
        // 记住上次格式，下次默认选中
        appScope.launch { runCatching { container.settingsRepository.setLastCopyFormat(lastCopyFormat) } }
        _events.trySend(BrowserEvent.Message(com.r2manager.android.R.string.toast_copied))
    }

    /** 最近一次复制使用的格式（由 UI 在调用前设置）。 */
    var lastCopyFormat: CopyFormat = CopyFormat.URL

    // ==================== 上传 ====================

    /** 请求弹出上传来源面板。 */
    fun requestUpload() {
        _events.trySend(BrowserEvent.RequestUploadSource)
    }

    /** 用户选好来源后由 UI 调用：处理已选 Uri。 */
    fun onUploadPicked(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }
        val prefix = _state.value.prefix
        appScope.launch {
            val ctx = container.appContext
            val names = uris.map { UriUtils.queryDisplayName(ctx, it) ?: it.lastPathSegment ?: "unnamed" }
            val conflicts = runCatching { container.transferEngine.findConflicts(prefix, names) }
                .getOrDefault(emptyList())
            if (conflicts.isNotEmpty()) {
                pendingUploadUris = uris
                _events.trySend(BrowserEvent.ConfirmOverwrite(conflicts))
            } else {
                enqueueUpload(uris)
            }
        }
    }

    /** 确认覆盖后继续上传。 */
    fun confirmUploadOverwrite() {
        val uris = pendingUploadUris
        pendingUploadUris = emptyList()
        if (uris.isNotEmpty()) {
            enqueueUpload(uris)
        }
    }

    /** 取消覆盖上传。 */
    fun cancelUploadOverwrite() {
        pendingUploadUris = emptyList()
    }

    private fun enqueueUpload(uris: List<Uri>) {
        val prefix = _state.value.prefix
        appScope.launch {
            val ids = runCatching { container.transferEngine.enqueueUpload(uris, prefix) }
                .getOrDefault(emptyList())
            if (ids.isNotEmpty()) {
                _events.trySend(BrowserEvent.MessageText("已加入上传队列：${ids.size} 项"))
            } else {
                _events.trySend(BrowserEvent.Message(com.r2manager.android.R.string.error_unknown))
            }
        }
    }

    // ==================== 下载 ====================

    /** 请求下载（UI 负责目录选择）。 */
    fun requestDownload(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        _events.trySend(BrowserEvent.RequestDownloadDir(keys))
    }

    /** 用给定目录树 Uri 下载所选（含文件夹递归展开）。 */
    fun downloadKeys(keys: List<String>, treeUri: Uri) {
        if (keys.isEmpty()) {
            return
        }
        appScope.launch {
            val bucket = _state.value.bucket
            val requests = ArrayList<DownloadRequest>()
            for (key in keys) {
                val info = _state.value.objects.firstOrNull { it.key == key }
                if (info != null && info.isFolder) {
                    requests.addAll(expandFolder(info.key))
                } else if (info != null) {
                    requests.add(toDownloadRequest(key, info.size))
                }
            }
            if (requests.isEmpty()) {
                _events.trySend(BrowserEvent.MessageText("没有可下载的文件"))
                return@launch
            }
            val ids = runCatching { container.transferEngine.enqueueDownload(requests, treeUri) }
                .getOrDefault(emptyList())
            if (ids.isNotEmpty()) {
                _events.trySend(BrowserEvent.MessageText("已加入下载队列：${ids.size} 项"))
            } else {
                _events.trySend(BrowserEvent.Message(com.r2manager.android.R.string.error_unknown))
            }
        }
    }

    /** 递归展开文件夹为文件下载请求（跨页收集）。 */
    private suspend fun expandFolder(folderKey: String): List<DownloadRequest> {
        val out = ArrayList<DownloadRequest>()
        var token: String? = null
        do {
            val page = runCatching {
                container.storageRepository.listObjects(
                    prefix = folderKey,
                    forceRefresh = false,
                    continuationToken = token
                )
            }.getOrNull() ?: break
            for (obj in page.objects) {
                if (!obj.isFolder) {
                    out.add(toDownloadRequest(obj.key, obj.size))
                }
            }
            token = if (page.isTruncated) page.nextContinuationToken else null
        } while (token != null)
        return out
    }

    private fun toDownloadRequest(key: String, size: Long): DownloadRequest {
        val relativePath = key.substringBeforeLast('/', "").ifBlank { null }
        return DownloadRequest(bucket = _state.value.bucket, key = key, size = size, relativePath = relativePath)
    }

    // ==================== 切桶 ====================

    /** 加载桶列表并请求 UI 弹切换面板。 */
    fun requestBucketSwitch() {
        appScope.launch {
            val buckets = runCatching { container.bucketRepository.listBuckets() }.getOrDefault(emptyList())
            if (buckets.isEmpty()) {
                _events.trySend(BrowserEvent.Message(com.r2manager.android.R.string.browser_bucket_none))
            } else {
                _events.trySend(BrowserEvent.ShowBuckets(buckets, _state.value.bucket))
            }
        }
    }

    /** 切到指定桶并回到根目录。 */
    fun switchBucket(name: String) {
        if (name == _state.value.bucket) {
            return
        }
        appScope.launch {
            val ok = runCatching { container.bucketRepository.switchBucket(name) }.isSuccess
            if (ok) {
                _state.update { it.copy(bucket = name, prefix = "", objects = emptyList()) }
                resetAndLoad()
            } else {
                _events.trySend(BrowserEvent.Message(com.r2manager.android.R.string.error_bucket))
            }
        }
    }
}
