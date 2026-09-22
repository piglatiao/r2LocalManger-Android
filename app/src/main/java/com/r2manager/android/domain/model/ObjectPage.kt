package com.r2manager.android.domain.model

/**
 * 一页对象列表。
 *
 * @property objects 文件夹在前、文件在后（保持桌面版顺序）
 * @property nextContinuationToken 下一页 token
 * @property isTruncated 是否还有下一页
 */
data class ObjectPage(
    val objects: List<ObjectInfo>,
    val nextContinuationToken: String?,
    val isTruncated: Boolean
)
