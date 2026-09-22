package com.r2manager.android.domain.model

/**
 * 应用设置快照。
 *
 * @property endpoint S3 兼容端点
 * @property region 固定 `auto`
 * @property currentBucket 当前桶
 * @property publicUrl 当前桶公开地址
 * @property jurisdiction `default` | `eu` | `fedramp`
 * @property thumbnailCacheEnabled 缩略图缓存开关
 * @property thumbnailCacheMaxBytes 缩略图缓存上限（字节）
 * @property objectListCacheEnabled 对象列表缓存开关
 * @property appLockEnabled 应用锁开关
 * @property autoLockMinutes 后台自动上锁分钟数（-1=从不，0=立即）
 * @property managementApiAvailable 管理面是否可用（凭证保存后探测结果）
 * @property lastCopyFormat 上次使用的复制格式
 */
data class AppSettings(
    val endpoint: String,
    val region: String,
    val currentBucket: String,
    val publicUrl: String,
    val jurisdiction: String,
    val thumbnailCacheEnabled: Boolean,
    val thumbnailCacheMaxBytes: Long,
    val objectListCacheEnabled: Boolean,
    val appLockEnabled: Boolean,
    val autoLockMinutes: Int,
    val managementApiAvailable: Boolean,
    val lastCopyFormat: CopyFormat
)
