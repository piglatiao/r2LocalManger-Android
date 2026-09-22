package com.r2manager.android.domain.model

/**
 * 自定义域名（Cloudflare 管理面）。
 *
 * @property domain 域名
 * @property enabled 是否启用
 * @property zoneId 所属 Zone ID
 * @property minTls 最低 TLS 版本
 * @property ciphers 允许的加密套件
 * @property status 域名状态
 */
data class CustomDomain(
    val domain: String,
    val enabled: Boolean,
    val zoneId: String,
    val minTls: String,
    val ciphers: List<String>,
    val status: String
)
