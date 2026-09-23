package com.r2manager.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.PrefKeys
import com.r2manager.android.core.util.ByteFormat
import com.r2manager.android.databinding.FragmentSettingsBinding
import com.r2manager.android.databinding.ItemSettingBinding

/**
 * 设置首页（P4-B）。
 *
 * 结构：`fragment_settings` 同时承载「首页列表」与「子页容器」——
 * 首页行用 [ItemSettingBinding] 动态构建进 `listContainer`，子页用 `childFragmentManager`
 * 替换进 `subContainer`（同一页面内二级导航，避免额外 Activity）。
 *
 * 子页：凭证配置 / 存储桶 / 缓存 / 系统应用锁 / 高级（自定义域名与 r2.dev）/ 运行日志。
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val rowViews = HashMap<Int, ItemSettingBinding>()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildHomeRows()
        childFragmentManager.addOnBackStackChangedListener { updateContainerVisibility() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        updateContainerVisibility()
    }

    override fun onResume() {
        super.onResume()
        refreshMetas()
    }

    // ==================== 首页列表 ====================

    private fun buildHomeRows() {
        val list = binding.listContainer
        list.removeAllViews()
        rowViews.clear()

        for (spec in ROW_SPECS) {
            val item = ItemSettingBinding.inflate(layoutInflater, list, false)
            item.ivIcon.setImageResource(spec.iconRes)
            item.tvTitle.setText(spec.titleRes)
            item.root.setOnClickListener { openSub(spec.tag) }
            list.addView(item.root)
            rowViews[spec.tag] = item
        }
    }

    private fun refreshMetas() {
        val container = requireContext().appContainer()
        val settings = container.settingsRepository.settings().value
        val accountId = container.settingsStore.raw().getString(PrefKeys.ACCOUNT_ID, "").orEmpty()

        rowViews[ROW_CREDENTIALS]?.tvMeta?.text =
            accountId.ifBlank { getString(R.string.settings_account_none) }
        rowViews[ROW_BUCKET]?.tvMeta?.text =
            settings.currentBucket.ifBlank { getString(R.string.settings_bucket_none) }
        rowViews[ROW_CACHE]?.tvMeta?.text = if (settings.thumbnailCacheEnabled) {
            getString(R.string.settings_cache_on, ByteFormat.size(settings.thumbnailCacheMaxBytes))
        } else {
            getString(R.string.settings_cache_off)
        }
        rowViews[ROW_SECURITY]?.tvMeta?.setText(R.string.settings_security_system)
        rowViews[ROW_ADVANCED]?.tvMeta?.text =
            settings.publicUrl.ifBlank { getString(R.string.settings_advanced_none) }
        rowViews[ROW_LOGS]?.let { it.tvMeta.setText(R.string.settings_logs_meta) }
    }

    // ==================== 二级导航 ====================

    private fun openSub(tag: Int) {
        val currentTag = childFragmentManager.findFragmentById(R.id.subContainer)?.tag
        if (currentTag == tag.toString()) {
            return
        }
        childFragmentManager.commit {
            replace(R.id.subContainer, createSub(tag), tag.toString())
            addToBackStack(tag.toString())
        }
    }

    private fun createSub(tag: Int): Fragment = when (tag) {
        ROW_CREDENTIALS -> CredentialsFragment()
        ROW_BUCKET -> BucketSettingsFragment()
        ROW_CACHE -> CacheSettingsFragment()
        ROW_SECURITY -> SecuritySettingsFragment()
        ROW_ADVANCED -> AdvancedSettingsFragment()
        else -> LogsFragment()
    }

    /** 子页请求返回（由各子页的返回按钮调用）。 */
    fun closeSub() {
        childFragmentManager.popBackStack()
    }

    private fun updateContainerVisibility() {
        val hasSub = childFragmentManager.backStackEntryCount > 0
        backCallback.isEnabled = hasSub
        binding.homeContainer.visibility = if (hasSub) View.GONE else View.VISIBLE
        binding.subContainer.visibility = if (hasSub) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 首页行定义。 */
    private data class RowSpec(val tag: Int, val iconRes: Int, val titleRes: Int)

    companion object {
        private const val ROW_CREDENTIALS = 1
        private const val ROW_BUCKET = 2
        private const val ROW_CACHE = 3
        private const val ROW_SECURITY = 4
        private const val ROW_ADVANCED = 5
        private const val ROW_LOGS = 6

        private val ROW_SPECS = listOf(
            RowSpec(ROW_CREDENTIALS, R.drawable.ic_key, R.string.settings_credentials),
            RowSpec(ROW_BUCKET, R.drawable.ic_db, R.string.settings_bucket),
            RowSpec(ROW_CACHE, R.drawable.ic_cloud, R.string.settings_cache),
            RowSpec(ROW_SECURITY, R.drawable.ic_shield, R.string.settings_security),
            RowSpec(ROW_ADVANCED, R.drawable.ic_globe, R.string.settings_advanced),
            RowSpec(ROW_LOGS, R.drawable.ic_doc, R.string.settings_logs)
        )

        /** 供主框架（P4-A）构造实例。 */
        fun newInstance(): SettingsFragment = SettingsFragment()
    }
}
