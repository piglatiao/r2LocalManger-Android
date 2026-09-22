package com.r2manager.android.data.repository

import android.content.Context
import com.r2manager.android.core.constants.NetworkConstants
import com.r2manager.android.core.constants.PrefKeys
import com.r2manager.android.core.util.UrlUtils
import com.r2manager.android.data.local.cache.CachePaths
import com.r2manager.android.data.local.cache.ListCacheStats
import com.r2manager.android.data.local.cache.ObjectListCache
import com.r2manager.android.data.local.cache.ThumbnailCache
import com.r2manager.android.data.local.cache.ThumbnailCacheStats
import com.r2manager.android.data.local.prefs.SettingsStore
import com.r2manager.android.domain.model.AppSettings
import com.r2manager.android.domain.model.CopyFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 设置与缓存仓库：对外暴露 `StateFlow<AppSettings>`，写操作后自动刷新。
 */
interface SettingsRepository {
    fun settings(): StateFlow<AppSettings>
    suspend fun updateR2Config(accountId: String, jurisdiction: String)
    suspend fun setThumbnailCache(enabled: Boolean, maxBytes: Long)
    suspend fun setObjectListCacheEnabled(enabled: Boolean)
    suspend fun setAutoLockMinutes(minutes: Int)
    suspend fun setLastCopyFormat(format: CopyFormat)
    suspend fun cacheStats(): Pair<ThumbnailCacheStats, ListCacheStats>
    suspend fun clearCaches(): Pair<Int, Long>
    suspend fun cacheDirectory(): String
}

/**
 * [SettingsRepository] 默认实现。
 *
 * @param store 底层设置存取
 * @param thumbnailCache 缩略图缓存（配置与统计）
 * @param listCache 列表缓存（配置与统计）
 * @param context 应用上下文（用于「打开缓存目录」定位缓存根目录）
 * @param ioDispatcher IO 调度器
 */
class SettingsRepositoryImpl(
    private val store: SettingsStore,
    private val thumbnailCache: ThumbnailCache,
    private val listCache: ObjectListCache,
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    private val cacheRootDir = CachePaths.root(context.applicationContext)

    private val state = MutableStateFlow(store.load())

    override fun settings(): StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun updateR2Config(accountId: String, jurisdiction: String) =
        withContext(ioDispatcher) {
            val current = state.value
            val endpoint = if (accountId.isBlank()) {
                current.endpoint
            } else {
                UrlUtils.buildEndpoint(accountId, current.endpoint)
            }
            store.saveR2Config(endpoint, NetworkConstants.REGION, jurisdiction)
            store.raw().edit().putString(PrefKeys.ACCOUNT_ID, accountId).apply()
            refresh()
        }

    override suspend fun setThumbnailCache(enabled: Boolean, maxBytes: Long) =
        withContext(ioDispatcher) {
            store.setThumbnailCache(enabled, maxBytes)
            thumbnailCache.configure(enabled, maxBytes)
            refresh()
        }

    override suspend fun setObjectListCacheEnabled(enabled: Boolean) = withContext(ioDispatcher) {
        store.setObjectListCacheEnabled(enabled)
        listCache.configure(enabled)
        refresh()
    }

    override suspend fun setAutoLockMinutes(minutes: Int) = withContext(ioDispatcher) {
        store.setAutoLockMinutes(minutes)
        refresh()
    }

    override suspend fun setLastCopyFormat(format: CopyFormat) = withContext(ioDispatcher) {
        store.setLastCopyFormat(format)
        refresh()
    }

    override suspend fun cacheStats(): Pair<ThumbnailCacheStats, ListCacheStats> =
        thumbnailCache.stats() to listCache.stats()

    override suspend fun clearCaches(): Pair<Int, Long> {
        val (thumbCount, thumbFreed) = thumbnailCache.clear()
        val (listCount, listFreed) = listCache.clear()
        return (thumbCount + listCount) to (thumbFreed + listFreed)
    }

    override suspend fun cacheDirectory(): String = cacheRootDir.absolutePath

    private fun refresh() {
        state.value = store.load()
    }
}
