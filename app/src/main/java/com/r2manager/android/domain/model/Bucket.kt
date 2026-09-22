package com.r2manager.android.domain.model

/**
 * 存储桶（管理面返回）。
 *
 * @property name 桶名
 * @property creationDateIso ISO-8601 创建时间
 * @property locationHint 位置提示（空 = auto）
 * @property storageClass 存储类型（Standard / InfrequentAccess）
 */
data class Bucket(
    val name: String,
    val creationDateIso: String?,
    val locationHint: String,
    val storageClass: String
)
