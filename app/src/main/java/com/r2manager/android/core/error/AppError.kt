package com.r2manager.android.core.error

/**
 * 统一错误模型。所有跨层错误都归一化为 [AppError]，UI 通过 [messageResId] 取文案、
 * 通过 [recovery] 决定主动作，禁止在 UI 硬编码错误文案。
 *
 * @property type 错误分类
 * @property messageResId 指向 `res/values/strings_error.xml` 的中文文案
 * @property recovery 用户可执行的恢复动作
 * @property cause 原始异常（可能为 null）
 * @property httpStatus 关联的 HTTP 状态码（若有）
 * @property s3Code S3 错误码（若有）
 * @property operation 出错的操作名，便于日志定位
 */
data class AppError(
    val type: ErrorType,
    val messageResId: Int,
    val recovery: RecoveryAction,
    val cause: Throwable? = null,
    val httpStatus: Int? = null,
    val s3Code: String? = null,
    val operation: String? = null
)
