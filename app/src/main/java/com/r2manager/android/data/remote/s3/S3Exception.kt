package com.r2manager.android.data.remote.s3

import java.io.IOException

/**
 * 统一的 S3 层异常。
 *
 * [code] 为 S3 返回体中的 `<Code>`（如 `NoSuchBucket`/`AccessDenied`），非 S3 错误时为 null；
 * [httpStatus] 为 HTTP 状态码（网络异常时为 0）。上层经 `ErrorMapper` 归入七类错误。
 */
class S3Exception(
    val code: String?,
    val httpStatus: Int,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
