package com.r2manager.android.data.remote.s3

import com.r2manager.android.domain.model.BatchDeleteResult
import com.r2manager.android.domain.model.ObjectInfo
import com.r2manager.android.domain.model.ObjectMeta
import okhttp3.Response
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory

/**
 * S3 XML 响应解析器（零第三方依赖）。
 *
 * 使用 JDK/Android 内置的 `javax.xml.parsers`(SAX) + `org.xml.sax`：
 * 二者在 Android 运行时与本地 JVM 单测环境均有**真实实现**，因此 [S3XmlParser] 可被纯 JVM 单测覆盖。
 *
 * 覆盖 `ListObjectsV2`、`DeleteObjects`、`InitiateMultipartUploadResult` 与错误体。
 * 过滤规则（与桌面版 `R2Client.listObjects` 对齐）：
 * 剔除 `key == prefix`、空白 key 的 `<Contents>`；剔除 `== prefix` 的 `<CommonPrefixes>`；
 * `key` 以 `/` 结尾者标记为文件夹（占位对象），不进入文件列表。
 */
object S3XmlParser {

    const val FOLDER_CONTENT_TYPE = "application/x-directory"

    private val ISO_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    private val RFC1123: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    // ———————————————————— ListObjectsV2 ————————————————————

    /** 解析 `ListObjectsV2` 响应体。 */
    fun parseListObjectsV2(xml: String, prefix: String): S3RawPage {
        val handler = ListObjectsHandler(prefix)
        parse(xml) { handler }
        return handler.result()
    }

    /** 解析 `ListObjectsV2` 响应流。 */
    fun parseListObjectsV2(stream: InputStream, prefix: String): S3RawPage {
        val handler = ListObjectsHandler(prefix)
        parse(stream) { handler }
        return handler.result()
    }

    // ———————————————————— DeleteObjects ————————————————————

    /** 解析 `DeleteObjects`（`Quiet=false`）响应体。 */
    fun parseDeleteResult(xml: String): BatchDeleteResult {
        val handler = DeleteResultHandler()
        parse(xml) { handler }
        return handler.result()
    }

    /** 解析 `DeleteObjects` 响应流。 */
    fun parseDeleteResult(stream: InputStream): BatchDeleteResult {
        val handler = DeleteResultHandler()
        parse(stream) { handler }
        return handler.result()
    }

    // ———————————————————— 错误体 / Multipart ————————————————————

    /** 解析 S3 错误体（`<Error><Code/><Message/></Error>`）。 */
    fun parseError(xml: String?): S3ErrorInfo? {
        if (xml.isNullOrBlank()) {
            return null
        }
        val handler = S3ErrorHandler()
        return runCatching {
            parse(xml) { handler }
            handler.result()
        }.getOrNull()
    }

    /** 解析 `InitiateMultipartUploadResult` 的 `UploadId`。 */
    fun parseUploadId(xml: String): String? {
        val handler = TextElementHandler("UploadId")
        parse(xml) { handler }
        return handler.value?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 构造 `CompleteMultipartUpload` 请求体。 */
    fun buildCompleteMultipartBody(parts: List<PartETag>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<CompleteMultipartUpload>")
        for (part in parts.sortedBy { it.partNumber }) {
            append("<Part>")
            append("<PartNumber>").append(part.partNumber).append("</PartNumber>")
            append("<ETag>").append(escapeXml(ensureQuoted(part.etag))).append("</ETag>")
            append("</Part>")
        }
        append("</CompleteMultipartUpload>")
    }

    /** 构造 `DeleteObjects` 请求体（`Quiet=false`）。 */
    fun buildDeleteObjectsBody(keys: List<String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<Delete xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">")
        for (key in keys) {
            append("<Object><Key>").append(escapeXml(key)).append("</Key></Object>")
        }
        append("<Quiet>false</Quiet>")
        append("</Delete>")
    }

    // ———————————————————— 头部 → ObjectMeta ————————————————————

    /** 由 HTTP 响应头构造 [ObjectMeta]（HeadObject / GetObject 通用）。 */
    fun metaFromHeaders(response: Response, key: String): ObjectMeta {
        val contentType = response.header("Content-Type")
        val contentLength = response.header("Content-Length")?.toLongOrNull()
            ?: response.body?.contentLength()
            ?: -1L
        val lastModifiedIso = normalizeHttpDate(response.header("Last-Modified"))
        val etag = response.header("ETag")?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }
        val userMetadata = LinkedHashMap<String, String>()
        for (name in response.headers.names()) {
            if (name.startsWith("x-amz-meta-", ignoreCase = true)) {
                val metaKey = name.substring("x-amz-meta-".length)
                userMetadata[metaKey] = response.header(name).orEmpty()
            }
        }
        return ObjectMeta(
            key = key,
            contentType = contentType,
            contentLength = contentLength,
            lastModifiedIso = lastModifiedIso,
            etag = etag,
            userMetadata = userMetadata
        )
    }

    // ———————————————————— 时间工具 ————————————————————

    /** ISO-8601 → 规范化 ISO 串（毫秒精度，UTC）；解析失败返回 null。 */
    fun normalizeIso(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) {
            return null
        }
        return runCatching { ISO_FORMAT.format(Instant.parse(value)) }.getOrNull()
    }

    /** HTTP 日期（RFC1123，如 `Wed, 12 Oct 2009 17:50:00 GMT`）→ 规范化 ISO 串。 */
    fun normalizeHttpDate(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) {
            return null
        }
        return runCatching {
            ISO_FORMAT.format(ZonedDateTime.parse(value, RFC1123).toInstant())
        }.getOrNull()
    }

    /** 规范化 ISO 串 → epoch millis；解析失败返回 null。 */
    fun isoToEpochMillis(iso: String?): Long? {
        val value = iso?.trim().orEmpty()
        if (value.isEmpty()) {
            return null
        }
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    // ———————————————————— 内部实现 ————————————————————

    private fun parse(xml: String, handlerFactory: () -> DefaultHandler) {
        val factory = newFactory()
        val parser = factory.newSAXParser()
        parser.parse(InputSource(StringReader(xml)), handlerFactory())
    }

    private fun parse(stream: InputStream, handlerFactory: () -> DefaultHandler) {
        val factory = newFactory()
        val parser = factory.newSAXParser()
        parser.parse(InputSource(stream), handlerFactory())
    }

    private fun newFactory(): SAXParserFactory {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        // 关闭外部实体，防御 XXE（不同实现支持度不同，失败忽略）。
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory
    }

    private fun ensureQuoted(etag: String): String {
        val trimmed = etag.trim()
        return if (trimmed.startsWith("\"")) trimmed else "\"$trimmed\""
    }

    private fun escapeXml(value: String): String {
        val sb = StringBuilder(value.length)
        for (c in value) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 取单个文本元素值的通用 handler（如 UploadId）。 */
    private class TextElementHandler(private val target: String) : DefaultHandler() {
        var value: String? = null
        private val buffer = StringBuilder()
        private var capturing = false

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName
            buffer.setLength(0)
            capturing = name == target
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capturing) {
                buffer.append(ch, start, length)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName
            if (capturing && name == target) {
                value = buffer.toString()
                capturing = false
            }
        }
    }

    /** S3 错误体。 */
    private class S3ErrorHandler : DefaultHandler() {
        private val buffer = StringBuilder()
        private var current: String? = null
        private var code: String? = null
        private var message: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
            current = localName?.takeIf { it.isNotEmpty() } ?: qName
            buffer.setLength(0)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            buffer.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (current) {
                "Code" -> code = buffer.toString().trim()
                "Message" -> message = buffer.toString().trim()
            }
            current = null
        }

        fun result(): S3ErrorInfo? {
            if (code.isNullOrEmpty() && message.isNullOrEmpty()) {
                return null
            }
            return S3ErrorInfo(code, message)
        }
    }

    /** `ListObjectsV2` handler。 */
    private class ListObjectsHandler(private val prefix: String) : DefaultHandler() {
        private val contents = ArrayList<ObjectInfo>()
        private val commonPrefixes = ArrayList<String>()
        private var nextToken: String? = null
        private var truncated = false

        private val buffer = StringBuilder()
        private var current: String? = null

        private var inContents = false
        private var inCommonPrefixes = false

        private var key: String? = null
        private var lastModified: String? = null
        private var size: String? = null
        private var etag: String? = null
        private var contentType: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName
            current = name
            buffer.setLength(0)
            when (name) {
                "Contents" -> {
                    inContents = true
                    key = null; lastModified = null; size = null; etag = null; contentType = null
                }
                "CommonPrefixes" -> inCommonPrefixes = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            buffer.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName
            // 逐字对齐桌面版：key / 前缀 / MIME 原样取值（对象 key 首尾空格是合法值，不得 trim）；
            // 仅数值/日期/标志/游标做 trim，避免解析器空白干扰。
            val raw = buffer.toString()
            val text = raw.trim()
            when (name) {
                "Key" -> if (inContents) key = raw
                "ContentType" -> if (inContents) contentType = raw
                "LastModified" -> if (inContents) lastModified = text
                "Size" -> if (inContents) size = text
                "ETag" -> if (inContents) etag = text
                "Prefix" -> if (inCommonPrefixes) {
                    if (raw.isNotEmpty() && raw != prefix) {
                        commonPrefixes.add(raw)
                    }
                }
                "IsTruncated" -> if (!inContents) truncated = text.equals("true", ignoreCase = true)
                "NextContinuationToken" -> if (!inContents) {
                    nextToken = text.takeIf { it.isNotEmpty() }
                }
                "CommonPrefixes" -> inCommonPrefixes = false
                "Contents" -> {
                    inContents = false
                    finalizeContent()
                }
            }
            current = null
        }

        private fun finalizeContent() {
            // 不 trim：对象 key 首尾空格是合法值（与桌面版一致）。
            val itemKey = key ?: ""
            if (itemKey.isEmpty() || itemKey == prefix) {
                return
            }
            val isFolder = itemKey.endsWith("/")
            val name = itemKey.removePrefix(prefix).removeSuffix("/")
            contents.add(
                ObjectInfo(
                    key = itemKey,
                    name = name,
                    isFolder = isFolder,
                    size = if (isFolder) 0L else (size?.toLongOrNull() ?: 0L),
                    lastModifiedIso = normalizeIso(lastModified),
                    contentType = if (isFolder) FOLDER_CONTENT_TYPE else contentType?.takeIf { it.isNotEmpty() },
                    etag = etag?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }
                )
            )
        }

        fun result(): S3RawPage = S3RawPage(
            contents = contents,
            commonPrefixes = commonPrefixes,
            nextContinuationToken = nextToken,
            isTruncated = truncated
        )
    }

    /** `DeleteObjects` handler。 */
    private class DeleteResultHandler : DefaultHandler() {
        private val deleted = ArrayList<String>()
        private val errors = ArrayList<BatchDeleteResult.DeleteError>()

        private val buffer = StringBuilder()
        private var current: String? = null
        private var inDeleted = false
        private var inError = false

        private var key: String? = null
        private var code: String? = null
        private var message: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName
            current = name
            buffer.setLength(0)
            when (name) {
                "Deleted" -> {
                    inDeleted = true; key = null
                }
                "Error" -> {
                    inError = true; key = null; code = null; message = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            buffer.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName
            val raw = buffer.toString()
            val text = raw.trim()
            when (name) {
                // Key 不 trim（对象 key 首尾空格合法，与列表解析一致）；Code/Message 允许 trim。
                "Key" -> if (inDeleted || inError) key = raw
                "Code" -> if (inError) code = text
                "Message" -> if (inError) message = text
                "Deleted" -> {
                    inDeleted = false
                    key?.takeIf { it.isNotEmpty() }?.let { deleted.add(it) }
                }
                "Error" -> {
                    inError = false
                    val errKey = key.orEmpty()
                    errors.add(
                        BatchDeleteResult.DeleteError(
                            key = errKey,
                            code = code?.takeIf { it.isNotEmpty() },
                            message = message.orEmpty()
                        )
                    )
                }
            }
            current = null
        }

        fun result(): BatchDeleteResult = BatchDeleteResult(deleted, errors)
    }
}

/** S3 错误体解析结果。 */
data class S3ErrorInfo(val code: String?, val message: String?)
