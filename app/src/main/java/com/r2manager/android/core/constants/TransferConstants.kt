package com.r2manager.android.core.constants

/**
 * 传输相关阈值与常量（唯一来源，禁止在业务代码散落魔法数字）。
 *
 * 阈值与桌面版 `src/main/infrastructure/R2Client.js` 完全对齐：
 * 大文件阈值 300MB、默认分片 64MB、最小分片 5MB、最大分片数 10000。
 */
object TransferConstants {
    /** 大文件阈值：超过则走 Multipart 上传。 */
    const val LARGE_UPLOAD_THRESHOLD_BYTES: Long = 300L * 1024 * 1024 // 300 MB

    /** 默认分片大小。 */
    const val DEFAULT_MULTIPART_PART_SIZE: Long = 64L * 1024 * 1024 // 64 MB

    /** 最小分片大小 / 分片对齐粒度。 */
    const val MIN_MULTIPART_PART_SIZE: Long = 5L * 1024 * 1024 // 5 MB

    /** 单次 Multipart 上传允许的最大分片数（S3 限制）。 */
    const val MAX_MULTIPART_PARTS: Int = 10_000

    /** 文本预览上限，超过则提示"文件过大，请下载后查看"。 */
    const val TEXT_PREVIEW_MAX_BYTES: Long = 2L * 1024 * 1024 // 2 MB

    /** PDF 预览上限。 */
    const val PDF_PREVIEW_MAX_BYTES: Long = 25L * 1024 * 1024 // 25 MB

    /** 缩略图源文件跳过阈值：> 64MB 直接跳过缩略图。 */
    const val THUMBNAIL_SOURCE_MAX_BYTES: Long = 64L * 1024 * 1024 // 64 MB

    /** 缩略图编码失败时，原始内容落盘的上限。 */
    const val THUMBNAIL_RAW_FALLBACK_MAX_BYTES: Long = 4L * 1024 * 1024 // 4 MB

    /** 缩略图最长边像素。 */
    const val THUMBNAIL_MAX_EDGE_PX: Int = 320

    /** 缩略图 JPEG 质量。 */
    const val THUMBNAIL_JPEG_QUALITY: Int = 80

    /** 视频抽帧并发上限（由 ThumbnailLoader 的 Semaphore 保证）。 */
    const val VIDEO_THUMBNAIL_CONCURRENCY: Int = 2

    /** 应用锁密码最小长度。 */
    const val MIN_PASSWORD_LENGTH: Int = 4

    /** 连续失败次数上限，超过则进入锁定期。 */
    const val PIN_MAX_ATTEMPTS: Int = 5

    /** PIN 锁定时长（毫秒）。 */
    const val PIN_LOCKOUT_MS: Long = 30_000L
}
