package com.r2manager.android.domain.model

/**
 * R2 API 凭证（四字段）。
 *
 * 落盘前必须经 Keystore 加密，任何日志 / 异常不得打印明文。
 *
 * @property accountId Cloudflare Account ID
 * @property accessKeyId R2 S3 Access Key ID
 * @property secretAccessKey R2 S3 Secret Access Key
 * @property apiToken Cloudflare API Token（管理面）
 * @property jurisdiction `default` | `eu` | `fedramp`
 */
data class Credentials(
    val accountId: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val apiToken: String,
    val jurisdiction: String = "default"
) {
    /** S3 数据面所需三字段是否齐备。 */
    val isComplete: Boolean
        get() = accountId.isNotBlank() && accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()
}
