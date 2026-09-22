package com.r2manager.android.ui.main

import androidx.lifecycle.viewModelScope
import com.r2manager.android.AppContainer
import com.r2manager.android.domain.model.AppSettings
import com.r2manager.android.ui.common.AppViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 主界面 ViewModel：仅负责「当前桶 / 设置快照」的对外暴露，供各 Tab 共享。
 *
 * 具体页面逻辑分别在各自 ViewModel（如 [com.r2manager.android.ui.browser.BrowserViewModel]）。
 *
 * @param container 依赖容器
 */
class MainViewModel(container: AppContainer) : AppViewModel(container) {

    /** 设置快照（含当前桶、公开域名等）。 */
    val settings: StateFlow<AppSettings> = container.settingsRepository.settings()

    /** 当前桶名（空表示未配置）。 */
    val currentBucket: String get() = settings.value.currentBucket

    /**
     * 传输队列排空后若发现存在未完成任务，刷新一次（进程重启恢复）。
     * 由主界面在 [onResume] 触发。
     */
    fun restoreInterruptedTransfers() {
        appScope.launch {
            runCatching { container.transferEngine.restore() }
        }
    }
}
