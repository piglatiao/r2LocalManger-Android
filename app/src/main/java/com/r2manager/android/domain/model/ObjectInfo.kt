package com.r2manager.android.domain.model

/**
 * 列表项（对应桌面版 `listObjects` 返回）。
 *
 * @property key 完整 key（文件夹以 `/` 结尾）
 * @property name 末段名（文件夹去掉结尾 `/`）
 * @property isFolder 是否虚拟文件夹
 * @property size 字节数（文件夹为 0）
 * @property lastModifiedIso ISO-8601 时间；CommonPrefixes 补齐失败为 null
 * @property contentType MIME 类型（文件夹为 `application/x-directory`）
 * @property etag 对象 ETag（文件夹为 null）
 */
data class ObjectInfo(
    val key: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
    val lastModifiedIso: String?,
    val contentType: String?,
    val etag: String? = null
)
