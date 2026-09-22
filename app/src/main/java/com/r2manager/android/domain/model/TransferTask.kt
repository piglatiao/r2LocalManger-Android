package com.r2manager.android.domain.model

import com.r2manager.android.core.error.ErrorType

/**
 * 传输方向。
 */
enum class TransferDirection {
    /** 上传。 */
    UPLOAD,

    /** 下载。 */
    DOWNLOAD
}

/**
 * 传输任务状态（状态机：QUEUED → RUNNING → DONE | FAILED | CANCELLED）。
 */
enum class TransferStatus {
    /** 排队中。 */
    QUEUED,

    /** 进行中。 */
    RUNNING,

    /** 已完成。 */
    DONE,

    /** 失败（可重试）。 */
    FAILED,

    /** 已取消。 */
    CANCELLED
}

/**
 * 传输任务。
 *
 * @property id 0 = 未入库；入库后为自增主键
 * @property direction 上传 / 下载
 * @property bucket 桶名
 * @property key 目标 / 来源对象 key
 * @property localUri 上传 = 源 Uri；下载 = 目标 SAF 文档 Uri
 * @property size 总字节数
 * @property transferred 已传输字节数
 * @property status 状态
 * @property errorMessage 失败原因
 * @property errorType 失败分类
 * @property createdAt 创建时间（epoch millis）
 * @property updatedAt 最后更新时间（epoch millis）
 * @property uploadId 大文件 uploadId（一期不做分片续传，仅展示）
 * @property uploadedParts 已完成分片数
 */
data class TransferTask(
    val id: Long,
    val direction: TransferDirection,
    val bucket: String,
    val key: String,
    val localUri: String,
    val size: Long,
    val transferred: Long,
    val status: TransferStatus,
    val errorMessage: String?,
    val errorType: ErrorType?,
    val createdAt: Long,
    val updatedAt: Long,
    val uploadId: String? = null,
    val uploadedParts: Int = 0
) {
    /** 进度百分比（0..100，size<=0 时为 0）。 */
    val percent: Int
        get() = if (size <= 0) 0 else ((transferred * 100) / size).toInt().coerceIn(0, 100)
}

/**
 * 传输进度事件（UI 卡片与通知的唯一来源）。
 *
 * @property taskId 任务 id
 * @property direction 方向
 * @property key 对象 key
 * @property transferred 已传输字节
 * @property total 总字节
 * @property bytesPerSecond 瞬时速度（由 UI/引擎估算）
 * @property etaSeconds 预估剩余秒数
 * @property status 当前状态
 */
data class TransferProgress(
    val taskId: Long,
    val direction: TransferDirection,
    val key: String,
    val transferred: Long,
    val total: Long,
    val bytesPerSecond: Long,
    val etaSeconds: Long,
    val status: TransferStatus
)
