package com.r2manager.android.core.constants

/**
 * SharedPreferences 键名（唯一来源）。
 *
 * 注意：凭证与回退密钥一律经 Keystore 包裹后 base64 写入，键名见 [CREDENTIALS_BLOB] /
 * [FALLBACK_CACHE_KEY] / [KEYSTORE_ALIAS]。
 */
object PrefKeys {
    /** 偏好文件名。 */
    const val FILE_NAME = "r2_manager_prefs"

    // —— 连接配置 ——
    const val ACCOUNT_ID = "accountId"
    const val ENDPOINT = "r2Endpoint"
    const val REGION = "r2Region"
    const val CURRENT_BUCKET = "currentBucket"
    const val PUBLIC_URL = "r2PublicUrl"
    const val JURISDICTION = "jurisdiction"
    const val MANAGEMENT_OK = "managementApiAvailable"

    // —— 缓存开关 ——
    const val THUMB_ENABLED = "thumbnailCacheEnabled"
    const val THUMB_MAX_MB = "thumbnailCacheMaxSizeMB"
    const val LIST_CACHE_ENABLED = "objectListCacheEnabled"

    // —— 应用锁 ——
    const val APP_LOCK_ENABLED = "appLockEnabled"
    const val APP_LOCK_SALT = "appLockSalt"
    const val APP_LOCK_VERIFIER = "appLockVerifier"
    const val APP_LOCK_DISMISSED = "appLockSetupDismissed"

    /** 后台自动上锁分钟数：-1=从不，0=立即，1/5/15。 */
    const val AUTO_LOCK_MINUTES = "autoLockMinutes"

    // —— 其它 ——
    const val LAST_COPY_FORMAT = "lastCopyFormat"
    const val SAF_DOWNLOAD_TREE_URI = "safDownloadTreeUri"

    /** 未启用应用锁时的随机密钥（Keystore 包裹后存储）。 */
    const val FALLBACK_CACHE_KEY = "localCacheKey"

    /** 凭证 blob（Keystore 包裹后存储）。 */
    const val CREDENTIALS_BLOB = "credentialsBlob"

    /** Keystore 别名。 */
    const val KEYSTORE_ALIAS = "r2manager_secret_v1"

    /** 一次性明文缓存清理标记。 */
    const val UPGRADE_CLEANED = "plaintextCacheCleaned"
}
