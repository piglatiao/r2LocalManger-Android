package com.r2manager.android.domain.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.r2manager.android.core.constants.TransferConstants
import com.r2manager.android.core.error.ErrorMapper
import com.r2manager.android.core.mime.MimeTypes
import com.r2manager.android.data.remote.s3.MultipartPlan
import com.r2manager.android.data.remote.s3.PartETag
import com.r2manager.android.data.remote.s3.ProgressListener
import com.r2manager.android.data.remote.s3.S3Client
import com.r2manager.android.data.remote.s3.UriUploadSource
import com.r2manager.android.domain.model.TransferDirection
import com.r2manager.android.domain.model.TransferStatus
import com.r2manager.android.domain.model.TransferTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 单任务执行器（P3，无状态）。
 *
 * 由 [TransferQueue] 串行调度，每次只处理一个任务；本类不持有队列状态、不写 DAO，
 * 只负责「真正跑完一个上传/下载并返回终态任务」。终态由返回值表达（DONE / FAILED / CANCELLED）。
 *
 * 关键语义：
 * - 分片决策：`size > LARGE_UPLOAD_THRESHOLD_BYTES(300MB)` → Multipart（分片 [MultipartPlan]，默认 64MB），否则单次 PutObject。
 * - 偏移定位：`S3Client.uploadPart` 内部按 `MultipartPlan.partSize(source.length) * (partNumber-1)` 自行定位分片偏移，
 *   因此这里传入**完整**上传源（`length = 整个文件大小`），不可切片。
 * - 进度：S3 端回报的是**全局**进度（`baseOffset + 已消费字节`, `总字节`），本类仅做**单调不减**收敛（R-26）。
 * - 取消：捕获 [CancellationException] → 在 [NonCancellable] 中 `abortMultipartUpload`（R-27），返回 CANCELLED。
 * - 失败：经 [ErrorMapper] 归一化，写入 errorType/errorMessage。
 */
class TransferTaskRunner(
    private val context: Context,
    private val s3Provider: () -> S3Client?
) {

    /**
     * 执行一个任务并返回其终态副本（不修改入参）。
     *
     * @param task 待执行任务（status 通常为 RUNNING）
     * @param onProgress 进度回调；由调用方（队列）负责落库 + 发总线
     */
    suspend fun run(task: TransferTask, onProgress: ProgressListener): TransferTask {
        val s3: S3Client = s3Provider() ?: run {
            Log.w(TAG, "S3 客户端未就绪，任务直接判失败 taskId=${task.id}")
            return task.copy(
                status = TransferStatus.FAILED,
                errorMessage = "凭证未配置，无法传输",
                errorType = com.r2manager.android.core.error.ErrorType.AUTH,
                updatedAt = System.currentTimeMillis()
            )
        }

        val operation = if (task.direction == TransferDirection.UPLOAD) "upload" else "download"
        var lastGlobal = 0L
        // 进度包装：保证单调不减（R-26）；S3 端已回报全局进度，此处只收敛
        val report = ProgressListener { transferred, total ->
            val monotonic = if (transferred < lastGlobal) lastGlobal else transferred
            lastGlobal = monotonic
            onProgress.onProgress(monotonic, total)
        }

        var uploadId: String? = null
        return try {
            when (task.direction) {
                TransferDirection.UPLOAD -> {
                    val uri = Uri.parse(task.localUri)
                    val hint = context.contentResolver.getType(uri)
                    val contentType = MimeTypes.resolveContentType(task.key, hint)
                    val source = UriUploadSource(context.contentResolver, uri, task.size, hint)

                    if (task.size > TransferConstants.LARGE_UPLOAD_THRESHOLD_BYTES) {
                        // —— 大文件：Multipart 顺序上传 ——
                        val newUploadId = s3.createMultipartUpload(task.key, contentType)
                        uploadId = newUploadId
                        val partSize = MultipartPlan.partSize(task.size)
                        val parts = ArrayList<PartETag>()
                        var offset = 0L
                        var partNumber = 1
                        while (offset < task.size) {
                            val current = minOf(partSize, task.size - offset)
                            // 传完整 source：S3 端自行按 partNumber 定位偏移
                            val etag = s3.uploadPart(task.key, newUploadId, partNumber, source, current, report)
                            parts.add(PartETag(partNumber, etag))
                            offset += current
                            partNumber++
                        }
                        s3.completeMultipartUpload(task.key, newUploadId, parts)
                        task.copy(
                            status = TransferStatus.DONE,
                            transferred = task.size,
                            uploadId = newUploadId,
                            uploadedParts = parts.size,
                            errorMessage = null,
                            errorType = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        // —— 小文件：单次 PutObject ——
                        s3.putObject(task.key, source, contentType, task.size, report)
                        task.copy(
                            status = TransferStatus.DONE,
                            transferred = task.size,
                            errorMessage = null,
                            errorType = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }

                TransferDirection.DOWNLOAD -> {
                    val targetUri = Uri.parse(task.localUri)
                    val sink = context.contentResolver.openOutputStream(targetUri, "wt")
                        ?: throw IllegalStateException("无法打开目标文件写入流")
                    sink.use { out -> s3.downloadObject(task.key, out, report) }
                    task.copy(
                        status = TransferStatus.DONE,
                        transferred = if (task.size > 0) task.size else lastGlobal,
                        errorMessage = null,
                        errorType = null,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        } catch (ce: CancellationException) {
            // 取消：立即中止服务端未完成分片；用 NonCancellable 保证 abort 请求真的发出去（R-27）
            withContext(NonCancellable) {
                val id = uploadId
                if (id != null) {
                    runCatching { s3.abortMultipartUpload(task.key, id) }
                        .onFailure { Log.w(TAG, "abortMultipartUpload 失败 taskId=${task.id}", it) }
                }
            }
            task.copy(
                status = TransferStatus.CANCELLED,
                errorMessage = null,
                errorType = null,
                uploadId = uploadId,
                updatedAt = System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            val appError = ErrorMapper.fromThrowable(t, operation)
            task.copy(
                status = TransferStatus.FAILED,
                errorMessage = t.message ?: appError.cause?.message,
                errorType = appError.type,
                uploadId = uploadId,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private companion object {
        const val TAG = "TransferTaskRunner"
    }
}
