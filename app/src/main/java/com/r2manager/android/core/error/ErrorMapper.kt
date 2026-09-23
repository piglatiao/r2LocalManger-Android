package com.r2manager.android.core.error

import android.content.Context
import com.r2manager.android.R
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * 错误映射器：把任意 [Throwable] 归一化为 [AppError]。
 *
 * 分类优先级（逐字对齐 `docs/02 §7` 与 `R2Client._classifyError`）：
 * `FILE_SYSTEM > NO_CONNECTION > NETWORK > AUTH > PERMISSION > BUCKET > OBJECT > SAF_PERMISSION > UNKNOWN`
 *
 * 文案唯一来源：`res/values/strings_error.xml`（取自 `i18n/zh-CN.js` 的 UI_TEXT）。
 */
object ErrorMapper {

    /** 文件系统错误码。 */
    private val FILE_SYSTEM_CODES = setOf("ENOENT", "EACCES", "ENOSPC")

    /** 认证类错误码。 */
    private val AUTH_CODES = setOf("InvalidAccessKeyId", "SignatureDoesNotMatch", "InvalidToken", "ExpiredToken")

    /** 桶类错误码。 */
    private val BUCKET_CODES = setOf("NoSuchBucket", "BucketNotFound")

    /** 对象类错误码。 */
    private val OBJECT_CODES = setOf("NoSuchKey", "NotFound")

    /** SAF 授权失效标记（P2/P3 通过该 code 或 SecurityException 表达）。 */
    private const val SAF_PERMISSION_CODE = "SAF_PERMISSION"

    /** 无网络连接标记（由 ConnectivityMonitor 侧在判定离线时抛出）。 */
    private const val NO_CONNECTION_CODE = "NO_CONNECTION"

    /** 遍历 cause 链的最大深度，避免异常自引用死循环。 */
    private const val MAX_CAUSE_DEPTH = 12

    /**
     * 从异常构建 [AppError]（契约签名，逐字对齐 §4.2）。
     *
     * @param t 原始异常
     * @param operation 操作名（用于日志定位）
     */
    fun fromThrowable(t: Throwable, operation: String): AppError = fromThrowable(t, operation, offline = false)

    /**
     * 带离线判定的重载：当调用方已通过 ConnectivityManager 判定设备离线时传 [offline] = true，
     * 分类优先落到 [ErrorType.NO_CONNECTION]。
     *
     * @param t 原始异常
     * @param operation 操作名
     * @param offline 是否已判定无网络连接
     */
    fun fromThrowable(t: Throwable, operation: String, offline: Boolean): AppError {
        val chain = causeChain(t)
        val s3Code = chain.firstNotNullOfOrNull { readString(it, "getCode") }
        val httpStatus = chain.firstNotNullOfOrNull { readInt(it, "getHttpStatus") }
            ?: chain.firstNotNullOfOrNull { readInt(it, "getStatus") }

        val fsCode = s3Code?.takeIf { it in FILE_SYSTEM_CODES }
            ?: chain.firstNotNullOfOrNull { e -> readString(e, "getCode")?.takeIf { it in FILE_SYSTEM_CODES } }

        val type: ErrorType = when {
            fsCode != null -> ErrorType.FILE_SYSTEM
            offline || s3Code == NO_CONNECTION_CODE -> ErrorType.NO_CONNECTION
            chain.any { it is UnknownHostException || it is ConnectException } -> ErrorType.NETWORK
            chain.any { it is SocketTimeoutException || it is TimeoutException } -> ErrorType.NETWORK
            // EHOSTUNREACH（NoRouteToHost）与 ECONNRESET 等（SocketException）一并归入 NETWORK
            chain.any { it is NoRouteToHostException || it is SocketException } -> ErrorType.NETWORK
            s3Code == SAF_PERMISSION_CODE -> ErrorType.SAF_PERMISSION
            chain.any { it is SecurityException } -> ErrorType.SAF_PERMISSION
            else -> classify(s3Code, httpStatus).let { classified ->
                // 仅当无其它线索且链路含一般 I/O 异常时，兜底视作网络错误
                if (classified == ErrorType.UNKNOWN && chain.any { isGenericIo(it) }) {
                    ErrorType.NETWORK
                } else {
                    classified
                }
            }
        }

        val messageRes = specificMessageRes(type, fsCode, chain)
        return AppError(
            type = type,
            messageResId = messageRes,
            recovery = recoveryFor(type),
            cause = t,
            httpStatus = httpStatus,
            s3Code = s3Code,
            operation = operation
        )
    }

    /**
     * 依据 S3 错误码与 HTTP 状态码分类（不含 FILE_SYSTEM / NO_CONNECTION 分支）。
     *
     * 判定优先级对齐 `docs/02 §7`：`AUTH > FILE_SYSTEM > PERMISSION > BUCKET > OBJECT`
     * （即 `4>5>6`：PERMISSION 先于 BUCKET / OBJECT）。
     *
     * @param s3Code S3 错误码（可能为 null）
     * @param httpStatus HTTP 状态码（可能为 null）
     */
    fun classify(s3Code: String?, httpStatus: Int?): ErrorType = when {
        s3Code == SAF_PERMISSION_CODE || s3Code == NO_CONNECTION_CODE -> ErrorType.UNKNOWN
        s3Code in AUTH_CODES -> ErrorType.AUTH
        s3Code != null && s3Code in FILE_SYSTEM_CODES -> ErrorType.FILE_SYSTEM
        s3Code == "AccessDenied" -> ErrorType.PERMISSION
        httpStatus == 403 -> ErrorType.PERMISSION
        s3Code != null && s3Code in BUCKET_CODES -> ErrorType.BUCKET
        s3Code != null && s3Code in OBJECT_CODES -> ErrorType.OBJECT
        httpStatus == 404 -> ErrorType.OBJECT
        else -> ErrorType.UNKNOWN
    }

    /**
     * 七类（+2）分类对应的默认文案资源。
     *
     * @param type 错误分类
     * @return 文案资源 id
     */
    fun messageFor(type: ErrorType): Int = when (type) {
        ErrorType.FILE_SYSTEM -> R.string.error_file_not_found
        ErrorType.NETWORK -> R.string.error_network
        ErrorType.AUTH -> R.string.error_auth
        ErrorType.PERMISSION -> R.string.error_permission
        ErrorType.BUCKET -> R.string.error_bucket
        ErrorType.OBJECT -> R.string.error_object
        ErrorType.NO_CONNECTION -> R.string.error_no_connection
        ErrorType.SAF_PERMISSION -> R.string.error_saf_permission
        ErrorType.UNKNOWN -> R.string.error_unknown
    }

    /**
     * 分类对应的恢复动作。
     *
     * @param type 错误分类
     */
    fun recoveryFor(type: ErrorType): RecoveryAction = when (type) {
        ErrorType.NETWORK, ErrorType.NO_CONNECTION -> RecoveryAction.RETRY
        ErrorType.BUCKET, ErrorType.OBJECT -> RecoveryAction.REFRESH_LIST
        ErrorType.AUTH -> RecoveryAction.GO_TO_CREDENTIALS
        ErrorType.SAF_PERMISSION -> RecoveryAction.RESELECT_DIRECTORY
        ErrorType.UNKNOWN -> RecoveryAction.OPEN_LOGS
        ErrorType.FILE_SYSTEM, ErrorType.PERMISSION -> RecoveryAction.NONE
    }

    /**
     * 认证类错误的详情文案资源（"设置 > 凭证配置 …"），供 UI 二次提示使用。
     */
    fun authDetailFor(type: ErrorType): Int? =
        if (type == ErrorType.AUTH) R.string.error_auth_detail else null

    /**
     * 生成安全的错误详情，只展示服务端错误码和 HTTP 状态，不展示异常消息或请求签名。
     *
     * @param context 用于读取本地化文案
     * @param error 统一错误模型
     */
    fun detailFor(context: Context, error: AppError): String? {
        val details = ArrayList<String>(3)
        authDetailFor(error.type)?.let { details.add(context.getString(it)) }
        error.s3Code
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(MAX_DISPLAY_CODE_LENGTH)
            ?.let { details.add(context.getString(R.string.error_s3_code, it)) }
        error.httpStatus
            ?.takeIf { it > 0 }
            ?.let { details.add(context.getString(R.string.error_http_status, it)) }
        return details.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    // —— 内部工具 ——

    /** 精细文案：FILE_SYSTEM 依据具体 code 区分"不存在/无权限/空间不足"。 */
    private fun specificMessageRes(type: ErrorType, fsCode: String?, chain: List<Throwable>): Int = when {
        fsCode == "ENOENT" -> R.string.error_file_not_found
        fsCode == "EACCES" -> R.string.error_file_access
        fsCode == "ENOSPC" -> R.string.error_disk_space
        type == ErrorType.FILE_SYSTEM && chain.any { it is FileNotFoundException } -> R.string.error_file_not_found
        else -> messageFor(type)
    }

    /** 展开 cause 链（含自身），限制深度。 */
    private fun causeChain(t: Throwable): List<Throwable> {
        val list = ArrayList<Throwable>(MAX_CAUSE_DEPTH)
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < MAX_CAUSE_DEPTH) {
            list.add(cur)
            val next = cur.cause
            cur = if (next === cur) null else next
            depth++
        }
        return list
    }

    /** 反射读取无参 String getter，失败返回 null（不引 kotlin-reflect，保持与 P2 解耦）。 */
    private fun readString(target: Throwable, getter: String): String? = runCatching {
        val value = target.javaClass.getMethod(getter).invoke(target)
        (value as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** 反射读取无参 Int getter，失败返回 null。 */
    private fun readInt(target: Throwable, getter: String): Int? = runCatching {
        val value = target.javaClass.getMethod(getter).invoke(target)
        when (value) {
            is Int -> value
            is Number -> value.toInt()
            else -> null
        }
    }.getOrNull()

    /** 兜底：把 [IOException] 视作网络类（仅在无其它线索时使用，见 [fromThrowable] 的 `else` 分支）。 */
    private fun isGenericIo(t: Throwable): Boolean = t is IOException

    private const val MAX_DISPLAY_CODE_LENGTH = 80
}
