package com.r2manager.android.core.util

/**
 * URL 处理工具（端点生成、公开链接拼接、脱敏）。
 */
object UrlUtils {

    private const val HTTPS_PREFIX = "https://"

    /** R2 S3 端点域名后缀。 */
    private const val R2_ENDPOINT_SUFFIX = ".r2.cloudflarestorage.com"

    /** Account ID 主机名首段长度范围。 */
    private const val ACCOUNT_ID_MIN = 16
    private const val ACCOUNT_ID_MAX = 64

    /**
     * 标准化 URL：未带协议补 `https://`，去掉尾部 `/`。
     *
     * @param raw 原始 URL
     * @param allowEmpty 为 true 时允许空串（返回空串），否则空串也返回空串
     */
    fun normalizeUrl(raw: String?, allowEmpty: Boolean = false): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) {
            return if (allowEmpty) "" else ""
        }
        val withScheme = if (text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
        ) {
            text
        } else {
            HTTPS_PREFIX + text
        }
        return withScheme.trimEnd('/')
    }

    /**
     * 由 Account ID 派生 S3 端点。
     *
     * @param accountId Cloudflare Account ID
     * @param fallback Account ID 为空时的回退值
     */
    fun buildEndpoint(accountId: String, fallback: String = ""): String {
        val id = accountId.trim()
        if (id.isEmpty()) {
            return fallback
        }
        return "$HTTPS_PREFIX$id$R2_ENDPOINT_SUFFIX"
    }

    /**
     * 从端点主机名反推 Account ID（首段需为 16..64 位小写字母数字）。
     *
     * @param endpoint S3 端点
     * @return Account ID；无法识别返回空串
     */
    fun inferAccountIdFromEndpoint(endpoint: String): String {
        val text = endpoint.trim()
        if (text.isEmpty()) {
            return ""
        }
        val withoutScheme = text.substringAfter("://", text)
        val host = withoutScheme.substringBefore('/').substringBefore(':')
        val first = host.substringBefore('.')
        if (first.length !in ACCOUNT_ID_MIN..ACCOUNT_ID_MAX) {
            return ""
        }
        return if (first.all { it in 'a'..'z' || it in '0'..'9' }) first else ""
    }

    /**
     * 拼接公开链接：`base` 去尾 `/` 后接 `/<key>`。
     *
     * @param base 公开域名或端点（`publicUrl || endpoint`）
     * @param key 对象 key
     */
    fun joinPublicUrl(base: String, key: String): String {
        val normalizedBase = base.trim().trimEnd('/')
        val normalizedKey = key.trim().trimStart('/')
        if (normalizedBase.isEmpty()) {
            return normalizedKey
        }
        return "$normalizedBase/$normalizedKey"
    }

    /**
     * 去除 URL 的查询串（用于签名链接脱敏）。
     *
     * @param url 原始 URL
     */
    fun stripQuery(url: String): String {
        val index = url.indexOf('?')
        return if (index >= 0) url.substring(0, index) else url
    }
}
