package com.r2manager.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer
import com.r2manager.android.data.local.cache.ListCacheStats
import com.r2manager.android.data.local.cache.ThumbnailCacheStats
import com.r2manager.android.data.repository.SettingsRepository
import com.r2manager.android.domain.model.AppSettings

/**
 * 缓存设置 ViewModel（P4-B）。
 *
 * 复用 P2 [SettingsRepository]：读写缓存配置、统计占用、清理缓存、取缓存目录。
 */
class CacheSettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo: SettingsRepository = container.settingsRepository

    /** 当前设置快照。 */
    fun settings(): AppSettings = repo.settings().value

    /** 设置缩略图缓存开关与上限。 */
    suspend fun setThumbnailCache(enabled: Boolean, maxBytes: Long) =
        repo.setThumbnailCache(enabled, maxBytes)

    /** 设置列表缓存开关。 */
    suspend fun setObjectListCacheEnabled(enabled: Boolean) = repo.setObjectListCacheEnabled(enabled)

    /** 读取缓存统计（缩略图 + 列表）。 */
    suspend fun cacheStats(): Pair<ThumbnailCacheStats, ListCacheStats> = repo.cacheStats()

    /** 清理全部缓存，返回 (条目数, 释放字节)。 */
    suspend fun clearCaches(): Pair<Int, Long> = repo.clearCaches()

    /** 缓存根目录绝对路径。 */
    suspend fun cacheDirectory(): String = repo.cacheDirectory()

    companion object {
        /**
         * 构造工厂。
         *
         * @param container 全局依赖容器
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CacheSettingsViewModel(container) as T
                }
            }
    }
}
