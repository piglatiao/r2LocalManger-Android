package com.r2manager.android.domain.model

/**
 * 对象元信息（HeadObject / GetObject 响应归一化）。
 *
 * @property key 对象 key
 * @property contentType MIME 类型
 * @property contentLength 字节数
 * @property lastModifiedIso ISO-8601 时间
 * @property etag ETag
 * @property userMetadata 自定义 metadata（键统一小写）
 */
data class ObjectMeta(
    val key: String,
    val contentType: String?,
    val contentLength: Long,
    val lastModifiedIso: String?,
    val etag: String?,
    val userMetadata: Map<String, String>
)
