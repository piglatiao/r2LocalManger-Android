package com.r2manager.android.domain.model

/**
 * Cloudflare Zone（可用域名）。
 *
 * @property id Zone ID
 * @property name 域名
 * @property status 状态
 * @property accountId 所属账户 ID
 */
data class Zone(
    val id: String,
    val name: String,
    val status: String,
    val accountId: String
)
