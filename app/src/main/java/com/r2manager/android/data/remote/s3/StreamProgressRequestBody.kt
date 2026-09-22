package com.r2manager.android.data.remote.s3

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream

/**
 * 流式上传请求体：写入 OkHttp 时按**实际消费字节**回报进度。
 *
 * @param stream 每次调用返回一个新流（可重开），从 0 开始读；分片读取由调用方用 offset 起点的流
 * @param contentLength 本次请求体长度（分片长度）
 * @param contentType 请求体 MIME；null 表示不设置
 * @param totalBytes 整个对象的字节数（进度分母）
 * @param baseOffset 本次请求体在整个对象中的起始偏移（进度分子基准）
 * @param listener 进度回调；可为 null
 */
class StreamProgressRequestBody(
    private val stream: () -> InputStream,
    private val contentLength: Long,
    private val contentType: String?,
    private val totalBytes: Long,
    private val baseOffset: Long,
    private val listener: ProgressListener?
) : RequestBody() {

    override fun contentType(): MediaType? = contentType?.toMediaTypeOrNull()

    override fun contentLength(): Long = contentLength

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(BUFFER_SIZE)
        var transferred = 0L
        stream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                sink.write(buffer, 0, read)
                transferred += read.toLong()
                listener?.onProgress(baseOffset + transferred, totalBytes)
            }
        }
    }

    /** 可重开，允许 OkHttp 在重定向/重试时重新读取。 */
    override fun isOneShot(): Boolean = false

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
