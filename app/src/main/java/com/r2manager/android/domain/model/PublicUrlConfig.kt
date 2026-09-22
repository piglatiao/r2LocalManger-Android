package com.r2manager.android.domain.model

/**
 * 公开链接解析结果。
 *
 * @property baseUrl 公开访问基址
 * @property source 来源
 * @property publiclyReachable 是否可公网直连（自定义域名 / 已启用 r2.dev 为 true；S3 端点因需签名通常 false）
 */
data class PublicUrlConfig(
    val baseUrl: String,
    val source: Source,
    val publiclyReachable: Boolean
) {
    /** 公开链接来源，优先级：自定义域名 → r2.dev → S3 端点。 */
    enum class Source {
        /** 已启用的自定义域名。 */
        CUSTOM_DOMAIN,

        /** r2.dev 托管域名（enabled）。 */
        MANAGED_R2_DEV,

        /** S3 端点。 */
        S3_ENDPOINT
    }
}
