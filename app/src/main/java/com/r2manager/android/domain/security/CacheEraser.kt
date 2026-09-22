package com.r2manager.android.domain.security

import android.util.Log
import com.r2manager.android.data.local.cache.ObjectListCache
import com.r2manager.android.data.local.cache.ThumbnailCache

/**
 * 缓存擦除器（P3）。
 *
 * 用于「密钥体系变更」场景：换密码、开关应用锁、找回密码。这些操作会更换缓存加密密钥，
 * 旧密钥加密的缓存已不可读，必须**先清空全部缓存再换钥**（R-04；主理人裁决：开锁开关 = 密钥体系变更）。
 */
class CacheEraser(
    private val thumbnailCache: ThumbnailCache,
    private val listCache: ObjectListCache
) {

    /**
     * 清空缩略图缓存与列表缓存。
     * @return (清除条目总数, 释放字节总数)
     */
    suspend fun clearAll(): Pair<Int, Long> {
        val thumb = runCatching { thumbnailCache.clear() }
            .onFailure { Log.w(TAG, "清空缩略图缓存失败", it) }
            .getOrDefault(0 to 0L)
        val list = runCatching { listCache.clear() }
            .onFailure { Log.w(TAG, "清空列表缓存失败", it) }
            .getOrDefault(0 to 0L)
        return (thumb.first + list.first) to (thumb.second + list.second)
    }

    private companion object {
        const val TAG = "CacheEraser"
    }
}
