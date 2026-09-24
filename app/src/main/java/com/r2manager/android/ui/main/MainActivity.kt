package com.r2manager.android.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.UiConstants
import com.r2manager.android.databinding.ActivityMainBinding
import com.r2manager.android.ui.common.ViewModelFactory
import com.r2manager.android.ui.common.applyNavigationBarPadding

/**
 * 主界面：文件 / 传输 / 设置 三个 Tab。
 *
 * 职责：
 * - 承载 [MainPagerAdapter] 与自绘底部导航（不依赖 BottomNavigationView 的菜单资源）；
 * - 统一解析入口直达 extra（`onCreate` / `onNewIntent` 共用 [resolveEntryTab]）：
 *   [UiConstants.EXTRA_OPEN_TRANSFER] → 传输中心（R-32 通知点击）；
 *   [UiConstants.EXTRA_OPEN_SETTINGS] → 设置页（R-01 凭证缺失引导）；二者同时存在时传输优先；
 * - 向 [com.r2manager.android.AppContainer] 注册自身（供需要 Activity 的组件使用，如生物识别）；
 * - `onResume` 触发一次「中断任务恢复」（R-33）。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MainPagerAdapter
    private lateinit var navItems: List<NavItem>
    private var lastBackPressedAt = 0L

    private val viewModel: MainViewModel by viewModels {
        ViewModelFactory.of(appContainer()) { MainViewModel(appContainer()) }
    }

    /** 底部导航单项（根容器 + 图标 + 文案 + 目标 Tab）。 */
    private data class NavItem(
        val rootId: Int,
        val iconId: Int,
        val labelId: Int,
        val tab: MainTab
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appContainer().attachActivity(this)

        pagerAdapter = MainPagerAdapter(this)
        binding.pager.adapter = pagerAdapter
        // 三页固定，预加载相邻页使滑动更顺滑。
        binding.pager.offscreenPageLimit = 2
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                applyNavSelection(position)
            }
        })

        navItems = listOf(
            NavItem(R.id.bottom_nav_files, R.id.bottom_nav_files_icon, R.id.bottom_nav_files_label, MainTab.FILES),
            NavItem(R.id.bottom_nav_transfer, R.id.bottom_nav_transfer_icon, R.id.bottom_nav_transfer_label, MainTab.TRANSFER),
            NavItem(R.id.bottom_nav_settings, R.id.bottom_nav_settings_icon, R.id.bottom_nav_settings_label, MainTab.SETTINGS)
        )
        for (item in navItems) {
            findViewById<android.view.View>(item.rootId).setOnClickListener { selectTab(item.tab) }
        }

        binding.bottomNav.applyNavigationBarPadding()

        // 根页面连续两次返回才退出应用。
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = SystemClock.elapsedRealtime()
                if (lastBackPressedAt != 0L && now - lastBackPressedAt <= BACK_PRESS_INTERVAL_MS) {
                    finish()
                } else {
                    lastBackPressedAt = now
                    Toast.makeText(
                        this@MainActivity,
                        R.string.toast_press_back_again_to_exit,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        // 冷启动直达：统一解析入口 extra（传输 > 设置），无直达请求时落默认「文件」页。
        selectTab(resolveEntryTab(intent) ?: MainTab.FILES)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 前台再次收到直达请求（如通知点击）才切页；无 extra 则维持当前 Tab 不变。
        resolveEntryTab(intent)?.let { selectTab(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreInterruptedTransfers()
    }

    override fun onDestroy() {
        appContainer().detachActivity(this)
        super.onDestroy()
    }

    // ==================== 内部 ====================

    /**
     * 解析入口 Intent 的「直达目的地」。
     *
     * 供 [onCreate] 与 [onNewIntent] **共用同一套「读取 → 切页 → 清 extra」逻辑**，
     * 避免两段重复代码导致其中一个入口漏读。
     *
     * 优先级：[UiConstants.EXTRA_OPEN_TRANSFER]（通知直达传输中心，更具体）
     * ＞ [UiConstants.EXTRA_OPEN_SETTINGS]（凭证缺失引导设置页）＞ 无直达请求。
     *
     * 读取后即 [Intent.removeExtra] 消费掉两个 extra，防止配置变更或 Intent 复用时重复切页。
     *
     * @param entry 入口 Intent（`onCreate` 传 Activity 的 [intent]，可能为 null）
     * @return 目标 [MainTab]；`null` 表示无直达请求，由调用方决定默认行为
     */
    private fun resolveEntryTab(entry: Intent?): MainTab? {
        if (entry == null) {
            return null
        }
        val openTransfer = entry.getBooleanExtra(UiConstants.EXTRA_OPEN_TRANSFER, false)
        val openSettings = entry.getBooleanExtra(UiConstants.EXTRA_OPEN_SETTINGS, false)
        if (openTransfer || openSettings) {
            entry.removeExtra(UiConstants.EXTRA_OPEN_TRANSFER)
            entry.removeExtra(UiConstants.EXTRA_OPEN_SETTINGS)
        }
        return when {
            openTransfer -> MainTab.TRANSFER
            openSettings -> MainTab.SETTINGS
            else -> null
        }
    }

    private fun selectTab(tab: MainTab) {
        val index = tab.ordinal
        if (binding.pager.currentItem != index) {
            binding.pager.setCurrentItem(index, false)
        }
        applyNavSelection(index)
    }

    /** 从文件页打开设置页，用于处理未配置凭证状态。 */
    fun openSettingsTab() {
        selectTab(MainTab.SETTINGS)
    }

    /** 依据当前页序号刷新底部导航选中态（图标与文案着色）。 */
    private fun applyNavSelection(position: Int) {
        val selectedColor = ContextCompat.getColor(this, R.color.primary)
        val normalColor = ContextCompat.getColor(this, R.color.ink_tertiary)
        for ((index, item) in navItems.withIndex()) {
            val selected = index == position
            findViewById<ImageView>(item.iconId).setColorFilter(if (selected) selectedColor else normalColor)
            findViewById<TextView>(item.labelId).setTextColor(if (selected) selectedColor else normalColor)
            findViewById<ImageView>(item.iconId).isSelected = selected
        }
    }

    companion object {
        private const val BACK_PRESS_INTERVAL_MS = 2_000L

        /**
         * 构造打开主界面的 Intent。
         *
         * 直达 extra 键统一取自 [UiConstants]（[UiConstants.EXTRA_OPEN_TRANSFER] /
         * [UiConstants.EXTRA_OPEN_SETTINGS]），由 [resolveEntryTab] 在入口消费。
         *
         * @param context 上下文
         * @param openTransfer 是否直达传输中心（通知点击）
         * @param openSettings 是否直达设置页（凭证缺失引导）
         */
        fun intent(
            context: Context,
            openTransfer: Boolean = false,
            openSettings: Boolean = false
        ): Intent = Intent(context, MainActivity::class.java).apply {
            if (openTransfer) {
                putExtra(UiConstants.EXTRA_OPEN_TRANSFER, true)
            }
            if (openSettings) {
                putExtra(UiConstants.EXTRA_OPEN_SETTINGS, true)
            }
        }
    }
}
