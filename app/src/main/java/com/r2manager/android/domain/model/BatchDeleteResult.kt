package com.r2manager.android.domain.model

/**
 * 批量删除结果。
 *
 * @property deletedKeys 已删除的 key 列表
 * @property errors 失败项列表
 */
data class BatchDeleteResult(
    val deletedKeys: List<String>,
    val errors: List<DeleteError>
) {
    /**
     * 单项删除失败信息。
     *
     * @property key 失败 key
     * @property code S3 错误码
     * @property message 错误描述
     */
    data class DeleteError(val key: String, val code: String?, val message: String)
}
