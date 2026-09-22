package com.r2manager.android.data.local.db

/**
 * `transfer_task` 扁平行（SQL 友好），与领域模型 `TransferTask` 一一映射。
 *
 * 说明：行 ↔ 领域模型的映射函数由传输层（domain.transfer）负责，
 * 本文件仅承载 DAO 使用的持久化结构，避免跨包重复定义扩展函数。
 */
data class TransferTaskEntity(
    val id: Long,
    val direction: String,
    val bucket: String,
    val key: String,
    val localUri: String,
    val size: Long,
    val transferred: Long,
    val status: String,
    val errorMessage: String?,
    val errorType: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val uploadId: String?,
    val uploadedParts: Int
)
