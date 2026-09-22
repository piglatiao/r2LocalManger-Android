package com.r2manager.android.core.log

import android.content.Context
import android.util.Log
import com.r2manager.android.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 统一日志入口。
 *
 * - debug：输出到 Logcat，并保留最近 [RING_CAPACITY] 条内存日志；
 * - release：仅滚动写入应用私有目录 `logs/r2.log`（环形，最多 [RING_CAPACITY] 条），供"运行日志"页与导出（R-47）。
 *
 * 所有对外输出都必须先经 [LogRedactor] 脱敏（调用方传原文，本类内部自动脱敏）。
 */
object AppLog {

    private const val TAG = "R2Manager"

    /** 内存环形日志容量。 */
    private const val RING_CAPACITY = 200

    /** 落盘文件相对应用私有目录的路径。 */
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "r2.log"

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val ring = ArrayDeque<String>(RING_CAPACITY)
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    /** 是否 debug 构建（决定是否走 Logcat）。 */
    private val isDebug: Boolean = BuildConfig.DEBUG

    /**
     * 由 Application 在 onCreate 中调用一次。
     *
     * @param context 应用上下文（内部仅持有 applicationContext）
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun d(tag: String, message: String) = log(Log.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(Log.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Log.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Log.ERROR, tag, message, throwable)

    /** 返回内存环形日志快照（最新在末尾）。 */
    fun entries(): List<String> = synchronized(lock) { ring.toList() }

    /** 导出为可直接展示/分享的文本。 */
    fun exportText(): String = synchronized(lock) { ring.joinToString(separator = "\n") }

    /** 清空内存与落盘日志。 */
    fun clear() {
        synchronized(lock) { ring.clear() }
        runCatching { logFile()?.delete() }
    }

    /** 日志文件（若已 init），供"导出日志"分享。 */
    fun logFile(): File? {
        val ctx = appContext ?: return null
        return File(File(ctx.filesDir, LOG_DIR), LOG_FILE)
    }

    private fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
        val safeTag = LogRedactor.redact(tag)
        val safeMessage = LogRedactor.redact(message + (throwable?.let { " | " + LogRedactor.redact(it.toString()) } ?: ""))
        val line = "${timeFormat.format(Date())} ${levelLabel(level)}/$safeTag: $safeMessage"

        if (isDebug) {
            when (level) {
                Log.ERROR -> Log.e(TAG, safeMessage, throwable)
                Log.WARN -> Log.w(TAG, safeMessage, throwable)
                Log.INFO -> Log.i(TAG, safeMessage)
                else -> Log.d(TAG, safeMessage)
            }
        }

        synchronized(lock) {
            while (ring.size >= RING_CAPACITY) {
                ring.removeFirst()
            }
            ring.addLast(line)
        }

        if (!isDebug) {
            appendToFile(line)
        }
    }

    private fun appendToFile(line: String) {
        val ctx = appContext ?: return
        runCatching {
            val dir = File(ctx.filesDir, LOG_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, LOG_FILE)
            file.appendText(line + "\n")
            trimFileIfNeeded(file)
        }
    }

    /** 文件超过 2 倍容量行数时，按内存环形重写（保持最多 [RING_CAPACITY] 行）。 */
    private fun trimFileIfNeeded(file: File) {
        val lines = file.readLines()
        if (lines.size > RING_CAPACITY * 2) {
            val kept = synchronized(lock) { ring.toList() }
            file.writeText(kept.joinToString(separator = "\n") + "\n")
        }
    }

    private fun levelLabel(level: Int): String = when (level) {
        Log.ERROR -> "E"
        Log.WARN -> "W"
        Log.INFO -> "I"
        else -> "D"
    }
}
