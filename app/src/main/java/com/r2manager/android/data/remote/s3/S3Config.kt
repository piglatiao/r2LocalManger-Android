package com.r2manager.android.data.remote.s3

/**
 * S3 / R2 数据面连接配置。
 *
 * 与桌面版 `R2Client` 的 config 字段一一对应；`publicUrl` 为空时公开链接回退到 `endpoint`。
 */
data class S3Config(
    val endpoint: String,
    val region: String = "auto",
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val jurisdiction: String = "default",
    val publicUrl: String = ""
) {
    /** 端点去掉尾部 `/`，避免拼接出 `//`。 */
    val normalizedEndpoint: String
        get() = endpoint.trim().trimEnd('/')

    /** 公开域名去掉尾部 `/`（为空则返回空串）。 */
    val normalizedPublicUrl: String
        get() = publicUrl.trim().trimEnd('/')
}
