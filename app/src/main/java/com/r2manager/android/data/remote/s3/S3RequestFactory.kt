package com.r2manager.android.data.remote.s3

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Clock

/**
 * 构造并签名 S3 请求（path-style：`/<bucket>/<key>`）。
 *
 * 关键点：实际请求 URL 的路径与查询串**复用** [SigV4Signer.canonicalUri] / [SigV4Signer.canonicalQueryString]，
 * 保证「被签名的字符串」与「实际发出的 URL」逐字一致，避免 `SignatureDoesNotMatch`。
 *
 * @param config 连接配置
 * @param clock 签名时钟（单测可注入固定时刻）
 */
class S3RequestFactory(
    private val config: S3Config,
    private val clock: Clock = Clock.systemUTC()
) {

    private val baseUrl: HttpUrl = config.normalizedEndpoint
        .takeIf { it.isNotEmpty() }
        ?.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("S3 端点无效: '${config.endpoint}'")

    private val host: String = buildString {
        append(baseUrl.host)
        val defaultPort = if (baseUrl.isHttps) 443 else 80
        if (baseUrl.port != defaultPort) {
            append(':')
            append(baseUrl.port)
        }
    }

    private val emptySha256: String = SigV4Signer.sha256Hex("")

    // ———————————————————— 对外构建器 ————————————————————

    /** `GET /<bucket>?list-type=2&prefix=&delimiter=&...` */
    fun listObjectsV2(
        prefix: String,
        delimiter: String?,
        continuationToken: String?,
        maxKeys: Int
    ): Request {
        val query = LinkedHashMap<String, String>()
        query["list-type"] = "2"
        query["prefix"] = prefix
        if (delimiter != null) {
            query["delimiter"] = delimiter
        }
        if (continuationToken != null) {
            query["continuation-token"] = continuationToken
        }
        query["max-keys"] = maxKeys.toString()
        return build("GET", bucketPath(), query, null, emptySha256, null)
    }

    /** `HEAD /<bucket>/<key>` */
    fun headObject(key: String): Request =
        build("HEAD", objectPath(key), emptyMap(), null, emptySha256, null)

    /** `GET /<bucket>/<key>` */
    fun getObject(key: String): Request =
        build("GET", objectPath(key), emptyMap(), null, emptySha256, null)

    /** `DELETE /<bucket>/<key>` */
    fun deleteObject(key: String): Request =
        build("DELETE", objectPath(key), emptyMap(), null, emptySha256, null)

    /** `PUT /<bucket>/<key>`（流式，UNSIGNED-PAYLOAD） */
    fun putObject(key: String, body: RequestBody): Request =
        build("PUT", objectPath(key), emptyMap(), body, SigV4Signer.UNSIGNED_PAYLOAD, body.contentType()?.toString())

    /** `POST /<bucket>/<key>?uploads` */
    fun createMultipartUpload(key: String, contentType: String): Request =
        build(
            method = "POST",
            rawPath = objectPath(key),
            query = mapOf("uploads" to ""),
            body = EMPTY_BODY,
            payloadHash = emptySha256,
            contentType = contentType
        )

    /** `PUT /<bucket>/<key>?partNumber=N&uploadId=...` */
    fun uploadPart(key: String, uploadId: String, partNumber: Int, body: RequestBody): Request =
        build(
            method = "PUT",
            rawPath = objectPath(key),
            query = mapOf("partNumber" to partNumber.toString(), "uploadId" to uploadId),
            body = body,
            payloadHash = SigV4Signer.UNSIGNED_PAYLOAD,
            contentType = null
        )

    /** `POST /<bucket>/<key>?uploadId=...`（XML 体，签名真实 SHA-256） */
    fun completeMultipartUpload(key: String, uploadId: String, xml: String): Request =
        build(
            method = "POST",
            rawPath = objectPath(key),
            query = mapOf("uploadId" to uploadId),
            body = xml.toRequestBody(XML_MEDIA_TYPE),
            payloadHash = SigV4Signer.sha256Hex(xml),
            contentType = XML_CONTENT_TYPE
        )

    /** `DELETE /<bucket>/<key>?uploadId=...` */
    fun abortMultipartUpload(key: String, uploadId: String): Request =
        build(
            method = "DELETE",
            rawPath = objectPath(key),
            query = mapOf("uploadId" to uploadId),
            body = null,
            payloadHash = emptySha256,
            contentType = null
        )

    /** `POST /<bucket>?delete`（XML 体，签名真实 SHA-256） */
    fun deleteObjects(xml: String): Request =
        build(
            method = "POST",
            rawPath = bucketPath(),
            query = mapOf("delete" to ""),
            body = xml.toRequestBody(XML_MEDIA_TYPE),
            payloadHash = SigV4Signer.sha256Hex(xml),
            contentType = XML_CONTENT_TYPE
        )

    // ———————————————————— 内部 ————————————————————

    private fun bucketPath(): String = "/${config.bucket}"

    private fun objectPath(key: String): String =
        if (key.isEmpty()) bucketPath() else "/${config.bucket}/$key"

    private fun build(
        method: String,
        rawPath: String,
        query: Map<String, String>,
        body: RequestBody?,
        payloadHash: String,
        contentType: String?
    ): Request {
        val canonicalUri = SigV4Signer.canonicalUri(rawPath)
        val canonicalQuery = SigV4Signer.canonicalQueryString(query)

        val urlBuilder = baseUrl.newBuilder().encodedPath(canonicalUri)
        if (canonicalQuery.isNotEmpty()) {
            urlBuilder.encodedQuery(canonicalQuery)
        }
        val url = urlBuilder.build()

        val signHeaders = LinkedHashMap<String, String>()
        if (contentType != null) {
            signHeaders["content-type"] = contentType
        }

        val signedHeaders = SigV4Signer.sign(
            req = S3SignRequest(
                method = method,
                host = host,
                rawPath = rawPath,
                queryParams = query,
                headers = signHeaders,
                payloadHash = payloadHash
            ),
            config = config,
            now = clock.instant()
        )

        val builder = Request.Builder().url(url).method(method, body)
        for ((name, value) in signedHeaders) {
            when (name.lowercase()) {
                "host" -> Unit            // OkHttp 依据 URL 自动设置 Host
                "content-type" -> Unit    // 由 RequestBody 提供
                else -> builder.header(name, value)
            }
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType)
        }
        return builder.build()
    }

    private companion object {
        const val XML_CONTENT_TYPE = "application/xml"
        val XML_MEDIA_TYPE = XML_CONTENT_TYPE.toMediaType()
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
    }
}
