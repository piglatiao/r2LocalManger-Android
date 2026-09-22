package com.r2manager.android.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.r2manager.android.ui.browser.BrowserFragment
import com.r2manager.android.ui.settings.SettingsFragment
import com.r2manager.android.ui.transfer.TransferFragment

/**
 * 主界面底部导航的三态定义（顺序即 ViewPager2 页序）。
 */
enum class MainTab {
    /** 文件浏览（P4-A）。 */
    FILES,

    /** 传输中心（P4-B）。 */
    TRANSFER,

    /** 设置（P4-B）。 */
    SETTINGS
}

/**
 * 主界面分页适配器：文件 / 传输 / 设置 三个顶级页面。
 *
 * 与底部导航一一对应，使用 [FragmentStateAdapter] 惰性创建。
 *
 * @param activity 宿主 Activity
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = MainTab.entries.size

    override fun createFragment(position: Int): Fragment = when (MainTab.entries[position]) {
        MainTab.FILES -> BrowserFragment()
        MainTab.TRANSFER -> TransferFragment()
        MainTab.SETTINGS -> SettingsFragment()
    }
}
