package com.r2manager.android.data.remote.cf

import java.io.IOException

/**
 * Cloudflare 管理面异常。
 *
 * @param status HTTP 状态码
 * @param code Cloudflare `errors[0].code`（可能为 null）
 * @param message 已聚合的可读错误信息
 */
class CfApiException(
    val status: Int,
    val code: String?,
    message: String
) : IOException(message)
