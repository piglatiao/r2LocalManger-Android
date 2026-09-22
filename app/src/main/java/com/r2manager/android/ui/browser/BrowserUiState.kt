package com.r2manager.android.ui.browser

import com.r2manager.android.core.error.AppError
import com.r2manager.android.core.mime.FileTypes
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.core.util.TimeFormat
import com.r2manager.android.domain.model.ObjectInfo

/**
 * 浏览页 UI 状态（不可变快照）。
 *
 * 过滤与排序在 [visibleObjects] 中即时计算，保证列表渲染与「搜索/筛选/排序」一致。
 */
data class BrowserUiState(
    /** 当前桶（空表示未配置）。 */
    val bucket: String = "",
    /** 当前目录前缀（`""` = 桶根）。 */
    val prefix: String = "",
    /** 原始对象列表（文件夹在前，来自仓库）。 */
    val objects: List<ObjectInfo> = emptyList(),
    /** 视图模式：列表 / 网格。 */
    val viewMode: ViewMode = ViewMode.LIST,
    /** 排序方式。 */
    val sort: SortOrder = SortOrder.NAME_ASC,
    /** 类型筛选。 */
    val filter: FileFilter = FileFilter.ALL,
    /** 搜索关键字（对名称做不区分大小写包含匹配）。 */
    val query: String = "",
    /** 已选中的 key 集合（非空 = 多选模式）。 */
    val selectedKeys: Set<String> = emptySet(),
    /** 首屏加载中。 */
    val loading: Boolean = false,
    /** 下拉刷新中。 */
    val refreshing: Boolean = false,
    /** 分页加载中。 */
    val loadingMore: Boolean = false,
    /** 离线（无网络且展示缓存/无缓存）。 */
    val offline: Boolean = false,
    /** 当前数据来自缓存。 */
    val fromCache: Boolean = false,
    /** 缓存时间（来自缓存时有效）。 */
    val cachedAt: Long? = null,
    /** 首屏错误。 */
    val error: AppError? = null,
    /** 下一页游标（null = 无更多）。 */
    val nextToken: String? = null,
    /** 是否还有更多页。 */
    val isTruncated: Boolean = false
) {

    /** 视图模式。 */
    enum class ViewMode { LIST, GRID }

    /** 排序方式。 */
    enum class SortOrder {
        NAME_ASC, NAME_DESC, TIME_DESC, TIME_ASC, SIZE_DESC, SIZE_ASC
    }

    /** 类型筛选。 */
    enum class FileFilter { ALL, FOLDER, IMAGE, VIDEO, DOC }

    /** 是否处于多选模式。 */
    val isSelectionMode: Boolean get() = selectedKeys.isNotEmpty()

    /** 是否正在加载（任一态）。 */
    val isLoading: Boolean get() = loading || refreshing || loadingMore

    /** 过滤 + 排序后的可见列表。 */
    val visibleObjects: List<ObjectInfo>
        get() {
            val q = query.trim().lowercase()
            val filtered = objects.filter { info ->
                val matchesQuery = q.isEmpty() || info.name.lowercase().contains(q)
                matchesQuery && matchesFilter(info)
            }
            return sortObjects(filtered)
        }

    /** 当前可见列表中的文件夹数。 */
    val folderCount: Int get() = visibleObjects.count { it.isFolder }

    /** 当前可见列表中的文件数。 */
    val fileCount: Int get() = visibleObjects.count { !it.isFolder }

    /** 是否「空目录」（无任何对象，且非搜索 / 筛选态）。 */
    val isEmptyDirectory: Boolean
        get() = !isLoading && error == null && objects.isEmpty()

    /** 是否「无匹配结果」（有对象但被搜索 / 筛选清空）。 */
    val isNoResult: Boolean
        get() = !isLoading && error == null && objects.isNotEmpty() && visibleObjects.isEmpty()

    /** 是否处于「离线且无缓存」。 */
    val isOfflineEmpty: Boolean
        get() = !isLoading && error == null && offline && objects.isEmpty()

    private fun matchesFilter(info: ObjectInfo): Boolean = when (filter) {
        FileFilter.ALL -> true
        FileFilter.FOLDER -> info.isFolder
        FileFilter.IMAGE -> !info.isFolder && FileTypes.kindOf(info.key) == PreviewKind.IMAGE
        FileFilter.VIDEO -> !info.isFolder && FileTypes.kindOf(info.key) == PreviewKind.VIDEO
        FileFilter.DOC -> !info.isFolder && FileTypes.kindOf(info.key).let {
            it == PreviewKind.PDF || it == PreviewKind.TEXT
        }
    }

    private fun sortObjects(list: List<ObjectInfo>): List<ObjectInfo> {
        val comparator: Comparator<ObjectInfo> = when (sort) {
            SortOrder.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOrder.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOrder.TIME_DESC -> compareByDescending { TimeFormat.parseIso(it.lastModifiedIso) ?: Long.MIN_VALUE }
            SortOrder.TIME_ASC -> compareBy { TimeFormat.parseIso(it.lastModifiedIso) ?: Long.MAX_VALUE }
            SortOrder.SIZE_DESC -> compareByDescending { it.size }
            SortOrder.SIZE_ASC -> compareBy { it.size }
        }
        // 文件夹恒置顶（R-10：文件夹先于文件），组内再按所选规则排序。
        val folders = list.filter { it.isFolder }.sortedWith(comparator)
        val files = list.filter { !it.isFolder }.sortedWith(comparator)
        return folders + files
    }
}
