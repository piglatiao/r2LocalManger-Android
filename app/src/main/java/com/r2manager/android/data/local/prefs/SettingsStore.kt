package com.r2manager.android.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import com.r2manager.android.core.constants.CacheConstants
import com.r2manager.android.core.constants.PrefKeys
import com.r2manager.android.domain.model.AppSettings
import com.r2manager.android.domain.model.CopyFormat

/**
 * 本地设置（非敏感）读写。
 *
 * 全部落 `SharedPreferences`（[PrefKeys.FILE_NAME]）；凭证不进此类，见 [CredentialStore]。
 * 应用锁的盐/校验值等敏感字段不经接口暴露，由上层通过 [raw] 直接读取 `PrefKeys.*` 键。
 */
interface SettingsStore {
    fun load(): AppSettings

    fun saveR2Config(endpoint: String, region: String, jurisdiction: String)
    fun setCurrentBucket(bucket: String)
    fun setPublicUrl(url: String)
    fun setThumbnailCache(enabled: Boolean, maxBytes: Long)
    fun setObjectListCacheEnabled(enabled: Boolean)
    fun setManagementApiAvailable(ok: Boolean)
    fun setLastCopyFormat(format: CopyFormat)
    fun setAutoLockMinutes(minutes: Int)
    fun setAppLockMeta(enabled: Boolean, saltB64: String?, verifier: String?, dismissed: Boolean)
    fun getSafDownloadTreeUri(): String?
    fun setSafDownloadTreeUri(uri: String?)
    fun getFallbackKeyWrapped(): String?
    fun setFallbackKeyWrapped(v: String?)
    fun isPlaintextCacheCleaned(): Boolean
    fun markPlaintextCacheCleaned()
    fun raw(): SharedPreferences
}

/**
 * [SettingsStore] 默认实现。
 *
 * @param context 应用上下文
 */
class SettingsStoreImpl(context: Context) : SettingsStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefKeys.FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): AppSettings {
        val maxMb = prefs.getInt(PrefKeys.THUMB_MAX_MB, DEFAULT_THUMBNAIL_MAX_MB)
        val maxBytes = maxMb.coerceAtLeast(0).toLong() * BYTES_PER_MB
        return AppSettings(
            endpoint = prefs.getString(PrefKeys.ENDPOINT, "").orEmpty(),
            region = prefs.getString(PrefKeys.REGION, DEFAULT_REGION).orEmpty().ifEmpty { DEFAULT_REGION },
            currentBucket = prefs.getString(PrefKeys.CURRENT_BUCKET, "").orEmpty(),
            publicUrl = prefs.getString(PrefKeys.PUBLIC_URL, "").orEmpty(),
            jurisdiction = prefs.getString(PrefKeys.JURISDICTION, DEFAULT_JURISDICTION).orEmpty()
                .ifEmpty { DEFAULT_JURISDICTION },
            thumbnailCacheEnabled = prefs.getBoolean(PrefKeys.THUMB_ENABLED, true),
            thumbnailCacheMaxBytes = maxBytes,
            objectListCacheEnabled = prefs.getBoolean(PrefKeys.LIST_CACHE_ENABLED, true),
            appLockEnabled = prefs.getBoolean(PrefKeys.APP_LOCK_ENABLED, false),
            autoLockMinutes = prefs.getInt(PrefKeys.AUTO_LOCK_MINUTES, DEFAULT_AUTO_LOCK_MINUTES),
            managementApiAvailable = prefs.getBoolean(PrefKeys.MANAGEMENT_OK, false),
            lastCopyFormat = readCopyFormat()
        )
    }

    override fun saveR2Config(endpoint: String, region: String, jurisdiction: String) {
        prefs.edit()
            .putString(PrefKeys.ENDPOINT, endpoint)
            .putString(PrefKeys.REGION, region.ifBlank { DEFAULT_REGION })
            .putString(PrefKeys.JURISDICTION, jurisdiction.ifBlank { DEFAULT_JURISDICTION })
            .apply()
    }

    override fun setCurrentBucket(bucket: String) {
        prefs.edit().putString(PrefKeys.CURRENT_BUCKET, bucket).apply()
    }

    override fun setPublicUrl(url: String) {
        prefs.edit().putString(PrefKeys.PUBLIC_URL, url).apply()
    }

    override fun setThumbnailCache(enabled: Boolean, maxBytes: Long) {
        val mb = (maxBytes / BYTES_PER_MB).toInt().coerceAtLeast(0)
        prefs.edit()
            .putBoolean(PrefKeys.THUMB_ENABLED, enabled)
            .putInt(PrefKeys.THUMB_MAX_MB, mb)
            .apply()
    }

    override fun setObjectListCacheEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PrefKeys.LIST_CACHE_ENABLED, enabled).apply()
    }

    override fun setManagementApiAvailable(ok: Boolean) {
        prefs.edit().putBoolean(PrefKeys.MANAGEMENT_OK, ok).apply()
    }

    override fun setLastCopyFormat(format: CopyFormat) {
        prefs.edit().putString(PrefKeys.LAST_COPY_FORMAT, format.name).apply()
    }

    override fun setAutoLockMinutes(minutes: Int) {
        prefs.edit().putInt(PrefKeys.AUTO_LOCK_MINUTES, minutes).apply()
    }

    override fun setAppLockMeta(enabled: Boolean, saltB64: String?, verifier: String?, dismissed: Boolean) {
        val editor = prefs.edit()
            .putBoolean(PrefKeys.APP_LOCK_ENABLED, enabled)
            .putBoolean(PrefKeys.APP_LOCK_DISMISSED, dismissed)
        if (saltB64 != null) {
            editor.putString(PrefKeys.APP_LOCK_SALT, saltB64)
        }
        if (verifier != null) {
            editor.putString(PrefKeys.APP_LOCK_VERIFIER, verifier)
        }
        editor.apply()
    }

    override fun getSafDownloadTreeUri(): String? =
        prefs.getString(PrefKeys.SAF_DOWNLOAD_TREE_URI, null)

    override fun setSafDownloadTreeUri(uri: String?) {
        prefs.edit().putString(PrefKeys.SAF_DOWNLOAD_TREE_URI, uri).apply()
    }

    override fun getFallbackKeyWrapped(): String? =
        prefs.getString(PrefKeys.FALLBACK_CACHE_KEY, null)

    override fun setFallbackKeyWrapped(v: String?) {
        prefs.edit().putString(PrefKeys.FALLBACK_CACHE_KEY, v).apply()
    }

    override fun isPlaintextCacheCleaned(): Boolean =
        prefs.getBoolean(PrefKeys.UPGRADE_CLEANED, false)

    override fun markPlaintextCacheCleaned() {
        prefs.edit().putBoolean(PrefKeys.UPGRADE_CLEANED, true).apply()
    }

    override fun raw(): SharedPreferences = prefs

    private fun readCopyFormat(): CopyFormat {
        val raw = prefs.getString(PrefKeys.LAST_COPY_FORMAT, null) ?: return CopyFormat.URL
        return runCatching { CopyFormat.valueOf(raw) }.getOrDefault(CopyFormat.URL)
    }

    companion object {
        const val DEFAULT_REGION = "auto"
        const val DEFAULT_JURISDICTION = "default"
        const val DEFAULT_AUTO_LOCK_MINUTES = 5
        val DEFAULT_THUMBNAIL_MAX_MB: Int =
            (CacheConstants.DEFAULT_THUMBNAIL_MAX_BYTES / (1024L * 1024L)).toInt()
        private const val BYTES_PER_MB = 1024L * 1024L
    }
}
