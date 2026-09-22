package com.r2manager.android.domain.model

/**
 * 缩略图缓存元信息（缓存键参与字段，见 §8.9 强约定）。
 *
 * @property bucket 桶名
 * @property key 对象 key
 * @property size 源对象字节数
 * @property lastModifiedIso ISO-8601 修改时间
 * @property etag ETag
 */
data class ThumbnailMeta(
    val bucket: String,
    val key: String,
    val size: Long,
    val lastModifiedIso: String?,
    val etag: String?
)
