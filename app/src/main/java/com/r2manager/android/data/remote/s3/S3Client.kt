package com.r2manager.android.data.remote.s3

import android.content.ContentResolver
import android.net.Uri
import com.r2manager.android.core.constants.NetworkConstants
import com.r2manager.android.core.util.UrlUtils
import com.r2manager.android.domain.model.BatchDeleteResult
import com.r2manager.android.domain.model.ObjectInfo
import com.r2manager.android.domain.model.ObjectMeta
import com.r2manager.android.domain.model.ObjectPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 进度回调：已传输字节 / 总字节。
 *
 * 实现内部**不得**阻塞；如需中止请求，请抛 [kotlinx.coroutines.CancellationException]。
 */
fun interface ProgressListener {
    fun onProgress(transferred: Long, total: Long)
}

/** Multipart 分片完成后的 `(PartNumber, ETag)` 记录。 */
data class PartETag(val partNumber: Int, val etag: String)

/**
 * `ListObjectsV2` 的原始解析结果（未做文件夹/文件排序）。
 *
 * @param contents `<Contents>` 条目（已剔除 key==prefix、空白 key；`key` 以 `/` 结尾者为占位文件夹）
 * @param commonPrefixes `<CommonPrefixes>` 的 Prefix 列表（已剔除 == prefix）
 * @param nextContinuationToken 分页游标
 * @param isTruncated 是否还有后续页
 */
data class S3RawPage(
    val contents: List<ObjectInfo>,
    val commonPrefixes: List<String>,
    val nextContinuationToken: String?,
    val isTruncated: Boolean
)

/**
 * 统一上传源：把 Uri / File / ByteArray 都包成「可重开、可按偏移读取」的流，
 * 供 PutObject 与 Multipart 顺序读取使用（避免 Multipart 需要随机访问）。
 */
interface UploadSourceStream {
    /** 源总字节数。 */
    val length: Long

    /**
     * 打开一个新流。
     * @param offset 起始偏移（必须支持，用于分片）
     */
    fun open(offset: Long = 0): InputStream

    /** 外部已知的 MIME 提示（可为 null）。 */
    fun contentTypeHint(): String?
}

/** 内存字节数组上传源。 */
class ByteArrayUploadSource(
    private val bytes: ByteArray,
    private val hint: String? = null
) : UploadSourceStream {
    override val length: Long get() = bytes.size.toLong()

    override fun open(offset: Long): InputStream {
        val start = offset.coerceIn(0L, bytes.size.toLong()).toInt()
        return ByteArrayInputStream(bytes, start, bytes.size - start)
    }

    override fun contentTypeHint(): String? = hint
}

/** 本地文件上传源。 */
class FileUploadSource(
    private val file: File,
    private val hint: String? = null
) : UploadSourceStream {
    override val length: Long get() = file.length()

    override fun open(offset: Long): InputStream {
        val stream = FileInputStream(file)
        skipFully(stream, offset)
        return stream
    }

    override fun contentTypeHint(): String? = hint
}

/**
 * Content Uri 上传源（SAF / MediaStore / Photo Picker）。
 *
 * 通过 [ContentResolver] 每次重新 `openInputStream`，然后 `skip` 到偏移处。
 */
class UriUploadSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val length: Long,
    private val hint: String? = null
) : UploadSourceStream {

    override fun open(offset: Long): InputStream {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("无法打开输入流: $uri")
        skipFully(stream, offset)
        return stream
    }

    override fun contentTypeHint(): String? = hint
}

/** 顺序跳过 [offset] 字节（`skip` 可能返回更小值，需循环）。 */
private fun skipFully(stream: InputStream, offset: Long) {
    var remaining = offset
    while (remaining > 0) {
        val skipped = stream.skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
        } else {
            if (stream.read() < 0) {
                break
            }
            remaining -= 1
        }
    }
}

/**
 * 自实现 S3 客户端（OkHttp + 手写 SigV4 + 自实现 Multipart，不使用 AWS SDK）。
 *
 * 所有方法 `suspend`；协程取消时会 `Call.cancel()`（上传即 abort）。
 */
interface S3Client {
    suspend fun listObjectsV2(
        prefix: String = "",
        continuationToken: String? = null,
        delimiter: String? = "/",
        maxKeys: Int = NetworkConstants.LIST_MAX_KEYS
    ): ObjectPage

    /** 列某文件夹下最新 LastModified（CommonPrefixes 补齐用，循环分页，无 Delimiter）。 */
    suspend fun latestLastModified(prefix: String): String?

    suspend fun listObjectsRaw(
        prefix: String,
        delimiter: String?,
        continuationToken: String?
    ): S3RawPage

    suspend fun headObject(key: String): ObjectMeta

    /** 预览取流；调用方负责关闭返回的流。 */
    suspend fun openStream(key: String): Pair<InputStream, ObjectMeta>

    /** 小文件上传，返回 ETag。 */
    suspend fun putObject(
        key: String,
        source: UploadSourceStream,
        contentType: String,
        contentLength: Long,
        onProgress: ProgressListener?
    ): String

    /** 初始化 Multipart，返回 uploadId。 */
    suspend fun createMultipartUpload(key: String, contentType: String): String

    /** 上传单个分片，返回 ETag。 */
    suspend fun uploadPart(
        key: String,
        uploadId: String,
        partNumber: Int,
        source: UploadSourceStream,
        partLength: Long,
        onProgress: ProgressListener?
    ): String

    suspend fun completeMultipartUpload(key: String, uploadId: String, parts: List<PartETag>)

    suspend fun abortMultipartUpload(key: String, uploadId: String)

    suspend fun downloadObject(key: String, sink: OutputStream, onProgress: ProgressListener?)

    suspend fun deleteObject(key: String)

    suspend fun deleteObjects(keys: List<String>): BatchDeleteResult

    fun publicUrl(key: String): String
}

/**
 * [S3Client] 的默认实现。
 *
 * @param config 连接配置（凭证 / 端点 / 桶）
 * @param httpClient 复用的 OkHttp 客户端（超时由 AppContainer 统一配置）
 * @param ioDispatcher 网络 IO 调度器
 */
class S3ClientImpl(
    private val config: S3Config,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : S3Client {

    private val factory = S3RequestFactory(config)

    /**
     * 发送请求并返回未关闭的 [Response]（调用方负责关闭）。
     * 取消时立即 `Call.cancel()`。
     */
    private suspend fun call(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation {
                runCatching { call.cancel() }
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) {
                        cont.resume(response)
                    } else {
                        runCatching { response.close() }
                    }
                }
            })
        }

    /** 断言 2xx；失败时解析 S3 错误体并抛出 [S3Exception]。 */
    private fun ensureSuccess(response: Response, operation: String): Response {
        if (response.isSuccessful) {
            return response
        }
        val status = response.code
        val bodyText = runCatching { response.body?.string() }.getOrNull()
        val error = S3XmlParser.parseError(bodyText)
        val code = error?.code
        val message = error?.message?.takeIf { it.isNotBlank() }
            ?: "S3 请求失败($operation) HTTP $status"
        response.close()
        throw S3Exception(code, status, message)
    }

    /** 读取整个响应体字符串（自动关闭）。 */
    private fun readBody(response: Response): String =
        response.use { it.body?.string().orEmpty() }

    // ———————————————————— 列目录 ————————————————————

    override suspend fun listObjectsRaw(
        prefix: String,
        delimiter: String?,
        continuationToken: String?
    ): S3RawPage = withContext(ioDispatcher) {
        val requestPrefix = directoryPrefix(prefix)
        val request = factory.listObjectsV2(
            prefix = requestPrefix,
            delimiter = delimiter,
            continuationToken = continuationToken,
            maxKeys = NetworkConstants.LIST_MAX_KEYS
        )
        val response = ensureSuccess(call(request), "listObjectsRaw")
        readBody(response).let { S3XmlParser.parseListObjectsV2(it, requestPrefix) }
    }

    override suspend fun listObjectsV2(
        prefix: String,
        continuationToken: String?,
        delimiter: String?,
        maxKeys: Int
    ): ObjectPage = withContext(ioDispatcher) {
        val requestPrefix = directoryPrefix(prefix)
        val request = factory.listObjectsV2(requestPrefix, delimiter, continuationToken, maxKeys)
        val response = ensureSuccess(call(request), "listObjectsV2")
        val page = readBody(response).let { S3XmlParser.parseListObjectsV2(it, requestPrefix) }

        // 文件夹（CommonPrefixes，保留出现顺序）在前，文件在后（与桌面版一致）。
        val folders = LinkedHashMap<String, ObjectInfo>()
        for (commonPrefix in page.commonPrefixes) {
            folders[commonPrefix] = folderInfo(commonPrefix, requestPrefix, null)
        }
        for (item in page.contents) {
            if (item.isFolder) {
                folders[item.key] = folderInfo(item.key, requestPrefix, item.lastModifiedIso)
            }
        }

        // CommonPrefixes 不带 LastModified：对每个缺时间的文件夹补一次最新修改时间。
        val foldersResolved = folders.values.map { folder ->
            if (folder.lastModifiedIso != null) {
                folder
            } else {
                val latest = runCatching { latestLastModified(folder.key) }.getOrNull()
                folder.copy(lastModifiedIso = latest)
            }
        }

        val files = page.contents.filter { !it.isFolder }
        ObjectPage(
            objects = foldersResolved + files,
            nextContinuationToken = page.nextContinuationToken,
            isTruncated = page.isTruncated
        )
    }

    override suspend fun latestLastModified(prefix: String): String? = withContext(ioDispatcher) {
        val requestPrefix = directoryPrefix(prefix)
        var token: String? = null
        var latestMillis: Long = Long.MIN_VALUE
        var latestIso: String? = null
        do {
            val request = factory.listObjectsV2(
                prefix = requestPrefix,
                delimiter = null,
                continuationToken = token,
                maxKeys = NetworkConstants.LIST_MAX_KEYS
            )
            val response = ensureSuccess(call(request), "latestLastModified")
            val raw = readBody(response).let { S3XmlParser.parseListObjectsV2(it, requestPrefix) }
            for (item in raw.contents) {
                val millis = S3XmlParser.isoToEpochMillis(item.lastModifiedIso) ?: continue
                if (millis > latestMillis) {
                    latestMillis = millis
                    latestIso = item.lastModifiedIso
                }
            }
            token = if (raw.isTruncated) raw.nextContinuationToken else null
        } while (token != null)
        latestIso
    }

    private fun folderInfo(key: String, prefix: String, lastModifiedIso: String?): ObjectInfo {
        val name = key.removePrefix(prefix).removeSuffix("/")
        return ObjectInfo(
            key = key,
            name = name,
            isFolder = true,
            size = 0L,
            lastModifiedIso = lastModifiedIso,
            contentType = FOLDER_CONTENT_TYPE,
            etag = null
        )
    }

    /** S3 目录查询必须使用带末尾 `/` 的前缀，根目录保持空串。 */
    private fun directoryPrefix(prefix: String): String =
        if (prefix.isEmpty() || prefix.endsWith('/')) prefix else "$prefix/"

    // ———————————————————— 元信息 / 预览 ————————————————————

    override suspend fun headObject(key: String): ObjectMeta = withContext(ioDispatcher) {
        val response = ensureSuccess(call(factory.headObject(key)), "headObject")
        response.use { S3XmlParser.metaFromHeaders(it, key) }
    }

    override suspend fun openStream(key: String): Pair<InputStream, ObjectMeta> =
        withContext(ioDispatcher) {
            val response = ensureSuccess(call(factory.getObject(key)), "openStream")
            val body = response.body ?: run {
                response.close()
                throw S3Exception(null, response.code, "预览响应为空体: $key")
            }
            // 注意：响应不可关闭，调用方消费完 body 后由 OkHttp 自行释放。
            body.byteStream() to S3XmlParser.metaFromHeaders(response, key)
        }

    // ———————————————————— 上传 ————————————————————

    override suspend fun putObject(
        key: String,
        source: UploadSourceStream,
        contentType: String,
        contentLength: Long,
        onProgress: ProgressListener?
    ): String = withContext(ioDispatcher) {
        val body = StreamProgressRequestBody(
            stream = { source.open(0) },
            contentLength = contentLength,
            contentType = contentType,
            totalBytes = contentLength,
            baseOffset = 0L,
            listener = onProgress
        )
        val response = ensureSuccess(call(factory.putObject(key, body)), "putObject")
        response.use { extractEtag(it) }
    }

    override suspend fun createMultipartUpload(key: String, contentType: String): String =
        withContext(ioDispatcher) {
            val response = ensureSuccess(
                call(factory.createMultipartUpload(key, contentType)),
                "createMultipartUpload"
            )
            val xml = readBody(response)
            S3XmlParser.parseUploadId(xml)
                ?: throw S3Exception(null, response.code, "Multipart 初始化未返回 UploadId: $key")
        }

    override suspend fun uploadPart(
        key: String,
        uploadId: String,
        partNumber: Int,
        source: UploadSourceStream,
        partLength: Long,
        onProgress: ProgressListener?
    ): String = withContext(ioDispatcher) {
        val offset = MultipartPlan.partSize(source.length) * (partNumber - 1).toLong()
        val body = StreamProgressRequestBody(
            stream = { source.open(offset) },
            contentLength = partLength,
            contentType = null,
            totalBytes = source.length,
            baseOffset = offset,
            listener = onProgress
        )
        val response = ensureSuccess(
            call(factory.uploadPart(key, uploadId, partNumber, body)),
            "uploadPart"
        )
        response.use { extractEtag(it) }
    }

    override suspend fun completeMultipartUpload(
        key: String,
        uploadId: String,
        parts: List<PartETag>
    ) = withContext(ioDispatcher) {
        val xml = S3XmlParser.buildCompleteMultipartBody(parts)
        val response = ensureSuccess(
            call(factory.completeMultipartUpload(key, uploadId, xml)),
            "completeMultipartUpload"
        )
        response.use { /* 消费并释放 */ }
        Unit
    }

    override suspend fun abortMultipartUpload(key: String, uploadId: String) =
        withContext(ioDispatcher) {
            val response = ensureSuccess(
                call(factory.abortMultipartUpload(key, uploadId)),
                "abortMultipartUpload"
            )
            response.use { }
            Unit
        }

    // ———————————————————— 下载 / 删除 ————————————————————

    override suspend fun downloadObject(
        key: String,
        sink: OutputStream,
        onProgress: ProgressListener?
    ) = withContext(ioDispatcher) {
        val response = ensureSuccess(call(factory.getObject(key)), "downloadObject")
        response.use { res ->
            val body = res.body ?: throw S3Exception(null, res.code, "下载响应为空体: $key")
            val total = body.contentLength().let { if (it >= 0) it else 0L }
            onProgress?.onProgress(0L, total)
            val buffer = ByteArray(BUFFER_SIZE)
            var transferred = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    transferred += read
                    onProgress?.onProgress(transferred, total)
                }
            }
            sink.flush()
        }
        Unit
    }

    override suspend fun deleteObject(key: String) = withContext(ioDispatcher) {
        val response = ensureSuccess(call(factory.deleteObject(key)), "deleteObject")
        response.use { }
        Unit
    }

    override suspend fun deleteObjects(keys: List<String>): BatchDeleteResult =
        withContext(ioDispatcher) {
            if (keys.isEmpty()) {
                return@withContext BatchDeleteResult(emptyList(), emptyList())
            }
            val xml = S3XmlParser.buildDeleteObjectsBody(keys)
            val response = ensureSuccess(call(factory.deleteObjects(xml)), "deleteObjects")
            readBody(response).let { S3XmlParser.parseDeleteResult(it) }
        }

    override fun publicUrl(key: String): String {
        val base = config.normalizedPublicUrl.ifEmpty { config.normalizedEndpoint }
        return UrlUtils.joinPublicUrl(base, key)
    }

    private fun extractEtag(response: Response): String =
        response.header("ETag")?.trim()?.trim('"')
            ?: throw S3Exception(null, response.code, "响应缺少 ETag")

    companion object {
        const val FOLDER_CONTENT_TYPE = "application/x-directory"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
