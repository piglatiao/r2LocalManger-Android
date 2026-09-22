package com.r2manager.android.data.repository

import com.r2manager.android.core.util.UrlUtils
import com.r2manager.android.domain.model.CustomDomain
import com.r2manager.android.domain.model.ManagedDomain
import com.r2manager.android.domain.model.PublicUrlConfig

/**
 * 公开链接域名解析（与桌面版 `StorageService` 的 publicUrl 优先级一致）。
 *
 * 优先级：**已启用的自定义域名 → r2.dev（enabled = true）→ S3 端点**。
 * `publiclyReachable` 表示该域名对本机/公网是否可用（S3 端点默认不可公开访问）。
 */
object PublicUrlResolver {

    /**
     * 解析公开链接配置。
     *
     * @param customDomains 当前桶的自定义域名列表
     * @param managed r2.dev 托管域名（可能为 null）
     * @param endpoint S3 兼容端点（兜底）
     * @return 解析结果，`baseUrl` 已标准化（`https://` 前缀、去尾 `/`）
     */
    fun resolve(
        customDomains: List<CustomDomain>,
        managed: ManagedDomain?,
        endpoint: String
    ): PublicUrlConfig {
        // 1. 已启用的自定义域名（取第一个 enabled 且域名非空）
        val custom = customDomains.firstOrNull { it.enabled && it.domain.isNotBlank() }
        if (custom != null) {
            return PublicUrlConfig(
                baseUrl = normalize(custom.domain),
                source = PublicUrlConfig.Source.CUSTOM_DOMAIN,
                publiclyReachable = true
            )
        }

        // 2. r2.dev 托管域名
        if (managed != null && managed.enabled && managed.domain.isNotBlank()) {
            return PublicUrlConfig(
                baseUrl = normalize(managed.domain),
                source = PublicUrlConfig.Source.MANAGED_R2_DEV,
                publiclyReachable = true
            )
        }

        // 3. S3 端点兜底
        return PublicUrlConfig(
            baseUrl = normalize(endpoint),
            source = PublicUrlConfig.Source.S3_ENDPOINT,
            publiclyReachable = false
        )
    }

    /** 补 `https://`、去尾 `/`。 */
    private fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        return UrlUtils.normalizeUrl(trimmed, allowEmpty = true)
    }

    /** 供测试/调试：由 [PublicUrlConfig] 与 key 拼接最终 URL。 */
    fun buildUrl(config: PublicUrlConfig, key: String): String =
        UrlUtils.joinPublicUrl(config.baseUrl, key)

    /** 判定该域名是否为 S3 端点（用于 UI 提示"该域名不可公开访问"）。 */
    fun isS3Endpoint(config: PublicUrlConfig): Boolean =
        config.source == PublicUrlConfig.Source.S3_ENDPOINT
}
