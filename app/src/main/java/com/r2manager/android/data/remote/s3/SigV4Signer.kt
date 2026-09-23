package com.r2manager.android.data.remote.s3

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 待签名的 HTTP 请求描述（纯数据，便于单测）。
 *
 * @param method HTTP 方法（GET/PUT/POST/HEAD/DELETE）
 * @param host 请求 Host（不含协议与路径）
 * @param rawPath 未编码的请求路径，桶名已位于 Host，如 `/dir/a b.txt`
 * @param queryParams 查询参数（未编码）；同名多值请合并为逗号分隔
 * @param headers 需要参与签名的额外请求头（键不区分大小写；`host`/`x-amz-*` 由签名器自动补齐）
 * @param payloadHash 载荷 SHA-256 十六进制；流式上传用 [SigV4Signer.UNSIGNED_PAYLOAD]
 * @param sessionToken 临时凭证的会话令牌；非空时纳入签名
 */
data class S3SignRequest(
    val method: String,
    val host: String,
    val rawPath: String,
    val queryParams: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val payloadHash: String = SigV4Signer.UNSIGNED_PAYLOAD,
    val sessionToken: String? = null
)

/**
 * 手写 AWS Signature Version 4（`AWS4-HMAC-SHA256`），region 固定 `auto`（可由 [S3Config.region] 覆盖）。
 *
 * 纯函数实现（不依赖 OkHttp），因此可被 `mockwebserver` 或官方测试向量直接校验。
 * 生成的头：`Authorization`、`x-amz-date`、`x-amz-content-sha256`、`Host`，
 * 有会话令牌时附加 `x-amz-security-token`。
 */
object SigV4Signer {

    const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
    const val ALGORITHM = "AWS4-HMAC-SHA256"
    const val SERVICE = "s3"
    const val TERMINATOR = "aws4_request"

    private const val HEADER_HOST = "host"
    private const val HEADER_AMZ_DATE = "x-amz-date"
    private const val HEADER_CONTENT_SHA256 = "x-amz-content-sha256"
    private const val HEADER_SECURITY_TOKEN = "x-amz-security-token"

    private val DATE_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val DATE_STAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    private val URI_HEX = "0123456789ABCDEF".toCharArray()
    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * 计算签名并返回需要写入请求的头集合。
     *
     * @param req 待签名请求
     * @param config 凭证与区域（region 空则回退 `auto`）
     * @param now 签名时刻（UTC）
     * @return 头名 → 头值；至少含 `Authorization`/`x-amz-date`/`x-amz-content-sha256`/`Host`
     */
    fun sign(req: S3SignRequest, config: S3Config, now: Instant): Map<String, String> {
        val amzDate = DATE_TIME_FORMAT.format(now)
        val dateStamp = DATE_STAMP_FORMAT.format(now)
        val region = config.region.trim().ifEmpty { "auto" }

        val headerMap = LinkedHashMap<String, String>()
        headerMap[HEADER_HOST] = req.host
        headerMap[HEADER_CONTENT_SHA256] = req.payloadHash
        headerMap[HEADER_AMZ_DATE] = amzDate
        req.sessionToken?.let { token ->
            if (token.isNotBlank()) {
                headerMap[HEADER_SECURITY_TOKEN] = token
            }
        }
        for ((name, value) in req.headers) {
            headerMap[name.lowercase()] = value
        }

        val sortedNames = headerMap.keys.sorted()
        val canonicalHeaders = buildString {
            for (name in sortedNames) {
                append(name)
                append(':')
                append(normalizeHeaderValue(headerMap.getValue(name)))
                append('\n')
            }
        }
        val signedHeaders = sortedNames.joinToString(";")

        val canonicalUri = canonicalUri(req.rawPath)
        val canonicalQuery = canonicalQueryString(req.queryParams)

        val canonicalRequest = buildString {
            append(req.method.uppercase())
            append('\n')
            append(canonicalUri)
            append('\n')
            append(canonicalQuery)
            append('\n')
            append(canonicalHeaders)
            append('\n')
            append(signedHeaders)
            append('\n')
            append(req.payloadHash)
        }

        val credentialScope = "$dateStamp/$region/$SERVICE/$TERMINATOR"
        val stringToSign = buildString {
            append(ALGORITHM)
            append('\n')
            append(amzDate)
            append('\n')
            append(credentialScope)
            append('\n')
            append(sha256Hex(canonicalRequest))
        }

        val signingKey = deriveSigningKey(config.secretAccessKey, dateStamp, region)
        val signature = hex(hmacSha256(signingKey, stringToSign))

        val authorization = "$ALGORITHM " +
            "Credential=${config.accessKeyId}/$credentialScope, " +
            "SignedHeaders=$signedHeaders, " +
            "Signature=$signature"

        val result = LinkedHashMap<String, String>()
        result["Authorization"] = authorization
        result[HEADER_AMZ_DATE] = amzDate
        result[HEADER_CONTENT_SHA256] = req.payloadHash
        result["Host"] = req.host
        req.sessionToken?.let { token ->
            if (token.isNotBlank()) {
                result[HEADER_SECURITY_TOKEN] = token
            }
        }
        return result
    }

    // —— 供 S3RequestFactory 复用，保证实际请求 URL 与签名一致 ——

    /** 规范化 Canonical URI：逐段 RFC3986 编码，保留 `/`。 */
    fun canonicalUri(rawPath: String): String {
        val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        return uriEncode(path, encodeSlash = false)
    }

    /** 规范化 Canonical Query String：按名升序，名与值均编码。 */
    fun canonicalQueryString(params: Map<String, String>): String {
        if (params.isEmpty()) {
            return ""
        }
        return params.keys.sorted().joinToString("&") { name ->
            val value = params.getValue(name)
            "${uriEncode(name, encodeSlash = true)}=${uriEncode(value, encodeSlash = true)}"
        }
    }

    /** 计算 SHA-256 十六进制摘要。 */
    fun sha256Hex(value: String): String = hex(sha256(value.toByteArray(Charsets.UTF_8)))

    /** SHA-256 原始摘要。 */
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    /** RFC3986 百分号编码；[encodeSlash] = false 时保留 `/`。 */
    fun uriEncode(value: String, encodeSlash: Boolean): String {
        val sb = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            val isUnreserved = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') ||
                c == '-' || c == '_' || c == '.' || c == '~'
            if (isUnreserved || (c == '/' && !encodeSlash)) {
                sb.append(c)
            } else {
                sb.append('%')
                val code = byte.toInt() and 0xFF
                sb.append(URI_HEX[code ushr 4])
                sb.append(URI_HEX[code and 0x0F])
            }
        }
        return sb.toString()
    }

    /** 头值规范化：去首尾空白、内部连续空白压成单空格。 */
    private fun normalizeHeaderValue(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    /** 派生 SigV4 签名密钥（AWS4 + HMAC 链）。 */
    fun deriveSigningKey(secretAccessKey: String, dateStamp: String, region: String): ByteArray {
        val kDate = hmacSha256("AWS4$secretAccessKey".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, SERVICE)
        return hmacSha256(kService, TERMINATOR)
    }

    /** HMAC-SHA256。 */
    fun hmacSha256(key: ByteArray, data: String): ByteArray =
        hmacSha256(key, data.toByteArray(Charsets.UTF_8))

    /** HMAC-SHA256（字节输入）。 */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }
}
