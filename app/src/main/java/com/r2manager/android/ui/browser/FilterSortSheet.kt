package com.r2manager.android.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.r2manager.android.R
import com.r2manager.android.databinding.SheetFilterSortBinding

/**
 * 排序 / 筛选底部面板。
 *
 * 结果通过 `parentFragment` 实现 [Listener] 回传（由 [BrowserFragment] 以 `childFragmentManager` 弹出）。
 */
class FilterSortSheet : BottomSheetDialogFragment() {

    /** 结果回调。 */
    interface Listener {
        /** 应用排序与筛选。 */
        fun onFilterSortApplied(sort: BrowserUiState.SortOrder, filter: BrowserUiState.FileFilter)
    }

    private var _binding: SheetFilterSortBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val listener: Listener? get() = parentFragment as? Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetFilterSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sort = readSort()
        val filter = readFilter()
        binding.filterSortSortGroup.check(sortChipId(sort))
        binding.filterSortFilterGroup.check(filterChipId(filter))
        binding.filterSortApply.setOnClickListener {
            listener?.onFilterSortApplied(selectedSort(), selectedFilter())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int = R.style.Theme_R2Manager_BottomSheet

    private fun selectedSort(): BrowserUiState.SortOrder = when (binding.filterSortSortGroup.checkedChipId) {
        R.id.chip_sort_name_asc -> BrowserUiState.SortOrder.NAME_ASC
        R.id.chip_sort_name_desc -> BrowserUiState.SortOrder.NAME_DESC
        R.id.chip_sort_time_asc -> BrowserUiState.SortOrder.TIME_ASC
        R.id.chip_sort_size_desc -> BrowserUiState.SortOrder.SIZE_DESC
        R.id.chip_sort_size_asc -> BrowserUiState.SortOrder.SIZE_ASC
        else -> BrowserUiState.SortOrder.TIME_DESC
    }

    private fun selectedFilter(): BrowserUiState.FileFilter = when (binding.filterSortFilterGroup.checkedChipId) {
        R.id.chip_filter_folder -> BrowserUiState.FileFilter.FOLDER
        R.id.chip_filter_image -> BrowserUiState.FileFilter.IMAGE
        R.id.chip_filter_video -> BrowserUiState.FileFilter.VIDEO
        R.id.chip_filter_doc -> BrowserUiState.FileFilter.DOC
        else -> BrowserUiState.FileFilter.ALL
    }

    private fun sortChipId(sort: BrowserUiState.SortOrder): Int = when (sort) {
        BrowserUiState.SortOrder.NAME_ASC -> R.id.chip_sort_name_asc
        BrowserUiState.SortOrder.NAME_DESC -> R.id.chip_sort_name_desc
        BrowserUiState.SortOrder.TIME_ASC -> R.id.chip_sort_time_asc
        BrowserUiState.SortOrder.TIME_DESC -> R.id.chip_sort_time_desc
        BrowserUiState.SortOrder.SIZE_DESC -> R.id.chip_sort_size_desc
        BrowserUiState.SortOrder.SIZE_ASC -> R.id.chip_sort_size_asc
    }

    private fun filterChipId(filter: BrowserUiState.FileFilter): Int = when (filter) {
        BrowserUiState.FileFilter.ALL -> R.id.chip_filter_all
        BrowserUiState.FileFilter.FOLDER -> R.id.chip_filter_folder
        BrowserUiState.FileFilter.IMAGE -> R.id.chip_filter_image
        BrowserUiState.FileFilter.VIDEO -> R.id.chip_filter_video
        BrowserUiState.FileFilter.DOC -> R.id.chip_filter_doc
    }

    private fun readSort(): BrowserUiState.SortOrder {
        val name = arguments?.getString(ARG_SORT) ?: return BrowserUiState.SortOrder.NAME_ASC
        return runCatching { BrowserUiState.SortOrder.valueOf(name) }.getOrDefault(BrowserUiState.SortOrder.NAME_ASC)
    }

    private fun readFilter(): BrowserUiState.FileFilter {
        val name = arguments?.getString(ARG_FILTER) ?: return BrowserUiState.FileFilter.ALL
        return runCatching { BrowserUiState.FileFilter.valueOf(name) }.getOrDefault(BrowserUiState.FileFilter.ALL)
    }

    companion object {
        private const val ARG_SORT = "sort"
        private const val ARG_FILTER = "filter"

        /** 构造实例。 */
        fun newInstance(sort: BrowserUiState.SortOrder, filter: BrowserUiState.FileFilter): FilterSortSheet =
            FilterSortSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SORT, sort.name)
                    putString(ARG_FILTER, filter.name)
                }
            }
    }
}
