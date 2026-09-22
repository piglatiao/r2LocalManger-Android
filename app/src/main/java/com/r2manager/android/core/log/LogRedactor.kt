package com.r2manager.android.core.log

import com.r2manager.android.core.util.UrlUtils

/**
 * 日志脱敏工具（R-06 日志验收要求）。
 *
 * 必须屏蔽：`secretAccessKey`、`apiKey` / `apiToken`、`accessKeyId`，
 * 以及任何含 `X-Amz-Signature` / `X-Amz-Credential` / `Authorization` 的完整 URL。
 * 签名 URL 一律 [UrlUtils.stripQuery] 后再打日志。
 */
object LogRedactor {

    private const val MASK = "***"

    /** 需要脱敏的键名。 */
    private val SENSITIVE_KEYS = listOf(
        "secretAccessKey", "secret_access_key", "secretAccesskey",
        "apiKey", "api_key", "apiToken", "api_token",
        "accessKeyId", "access_key_id", "accessKey"
    )

    /** 键值对正则：名称 分隔符 值（值取到分隔符或空白为止）。 */
    private val SENSITIVE_PAIR = Regex(
        "(?i)(" + SENSITIVE_KEYS.joinToString("|") + ")(\\s*[:=]\\s*)([\"']?)([^\"'\\s,;&}]+)",
    )

    /** 需要整体脱敏的签名类查询参数。 */
    private val SIGNATURE_PARAMS = listOf(
        "X-Amz-Signature", "X-Amz-Credential", "X-Amz-Security-Token", "X-Amz-Algorithm", "Authorization"
    )

    /** URL 匹配。 */
    private val URL_PATTERN = Regex("https?://[^\\s\"'<>\\\\]+")

    /**
     * 对任意文本脱敏：先屏蔽敏感键值，再把签名 URL 的查询串剥离（保留路径）。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    fun redact(text: String): String {
        if (text.isEmpty()) {
            return text
        }
        val masked = SENSITIVE_PAIR.replace(text) { match ->
            val name = match.groupValues[1]
            val sep = match.groupValues[2]
            val quote = match.groupValues[3]
            "$name$sep$quote$MASK"
        }
        return URL_PATTERN.replace(masked) { match ->
            val url = match.value
            if (containsSignature(url)) UrlUtils.stripQuery(url) else url
        }
    }

    /**
     * 判断 URL 是否含签名类参数。
     *
     * @param url 待检查 URL
     */
    fun containsSignature(url: String): Boolean = SIGNATURE_PARAMS.any { url.contains(it, ignoreCase = true) }

    /**
     * 对敏感值本身脱敏：空串返回空串，否则返回掩码（不泄露长度）。
     *
     * @param value 敏感值
     */
    fun maskValue(value: String?): String = if (value.isNullOrEmpty()) "" else MASK
}
