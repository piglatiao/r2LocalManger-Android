package com.r2manager.android.domain.model

/**
 * r2.dev 托管域名。
 *
 * @property enabled 是否启用
 * @property domain 托管域名
 */
data class ManagedDomain(
    val enabled: Boolean,
    val domain: String
)
