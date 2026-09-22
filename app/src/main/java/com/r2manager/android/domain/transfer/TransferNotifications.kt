package com.r2manager.android.domain.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.r2manager.android.core.util.ByteFormat
import com.r2manager.android.domain.model.TransferDirection
import com.r2manager.android.domain.model.TransferProgress
import com.r2manager.android.domain.model.TransferStatus
import com.r2manager.android.domain.model.TransferTask

/**
 * 传输通知构建器（P3）。
 *
 * 设计要点（见架构 §8.9-6）：
 * - 通知是前台服务的「唯一进度出口」，进度数据源是 [TransferProgressBus]，本类只做渲染。
 * - 进度通知使用 `setProgress` + `setOnlyAlertOnce(true)` + 固定通知 id（[NOTIF_ID_PROGRESS]），
 *   反复更新不响铃、不叠加；终态通知使用另一个 id（[NOTIF_ID_TERMINAL]）且 `autoCancel`，
 *   二者互不覆盖（避免「通知打架」）。
 * - 通知点击 → 打开应用并直达传输中心：用 `setClassName` 以字符串引用**启动页** `StartupActivity`，
 *   避免 P3 对 P4 的编译期依赖。`LAUNCHER` 已移至 `StartupActivity`（`MainActivity` 为 `exported=false`），
 *   故通知**必须先进启动页做应用锁分流**，否则会绕过应用锁；启动页原样透传 [EXTRA_OPEN_TRANSFER]，
 *   再由 `MainActivity` 跳转传输 Tab。
 * - 小图标使用 framework 资源（`android.R.drawable.stat_sys_*`），不依赖 P1 的 drawable。
 */
class TransferNotifications(private val context: Context) {

    private val manager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    /** 创建通知渠道（幂等，O+ 必须）。 */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
        }
        manager?.createNotificationChannel(channel)
    }

    /** 进度通知：点击直达传输中心，含「取消」动作。 */
    fun buildProgress(task: TransferTask, p: TransferProgress): Notification {
        val percent = computePercent(p.transferred, p.total)
        val title = if (task.direction == TransferDirection.UPLOAD) "正在上传" else "正在下载"
        val detail = buildDetail(p, percent)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(task.direction))
            .setContentTitle("$title · ${fileName(task.key)}")
            .setContentText(detail)
            .setProgress(100, percent, p.total <= 0L)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(task.id))
            .addAction(0, "取消", cancelIntent(task.id))
            .build()
    }

    /** 终态通知（完成/失败/取消），可点击打开传输中心，非持续通知。 */
    fun buildTerminal(task: TransferTask): Notification {
        val (title, text) = terminalTitle(task)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(task.direction))
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(task.id))
            .build()
    }

    /** 前台服务启动时的占位通知（进度未知）。 */
    fun buildIdle(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("文件传输")
            .setContentText("准备中…")
            .setProgress(0, 0, true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    // —— 内部工具 ——

    private fun smallIcon(direction: TransferDirection): Int =
        if (direction == TransferDirection.UPLOAD) {
            android.R.drawable.stat_sys_upload
        } else {
            android.R.drawable.stat_sys_download
        }

    private fun computePercent(transferred: Long, total: Long): Int =
        if (total <= 0L) 0 else ((transferred * 100) / total).toInt().coerceIn(0, 100)

    private fun buildDetail(p: TransferProgress, percent: Int): String {
        val speed = if (p.bytesPerSecond > 0) ByteFormat.speed(p.bytesPerSecond) else "—"
        val eta = if (p.etaSeconds > 0) "剩余 ${ByteFormat.eta(p.etaSeconds)}" else "计算中…"
        return "$percent% · $speed · $eta"
    }

    private fun terminalTitle(task: TransferTask): Pair<String, String> {
        val name = fileName(task.key)
        val isUpload = task.direction == TransferDirection.UPLOAD
        return when (task.status) {
            TransferStatus.DONE -> (if (isUpload) "上传完成" else "下载完成") to name
            TransferStatus.FAILED ->
                (if (isUpload) "上传失败" else "下载失败") to
                    (task.errorMessage ?: name)
            TransferStatus.CANCELLED ->
                (if (isUpload) "已取消上传" else "已取消下载") to name
            else -> (if (isUpload) "上传" else "下载") to name
        }
    }

    private fun fileName(key: String): String =
        key.substringAfterLast('/').ifBlank { key }

    private fun openAppIntent(taskId: Long): PendingIntent {
        val intent = Intent().apply {
            // 先经启动页（LAUNCHER）做应用锁分流，避免绕过锁；CLEAR_TOP 复用已有启动页实例。
            setClassName(context.packageName, STARTUP_ACTIVITY_CLASS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_TRANSFER, true)
            putExtra(TransferService.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PENDING_FLAGS
        )
    }

    private fun cancelIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, TransferService::class.java).apply {
            action = TransferService.ACTION_CANCEL_TASK
            putExtra(TransferService.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getService(
            context,
            (taskId and 0xFFFFFF).toInt() + REQUEST_CANCEL_OFFSET,
            intent,
            PENDING_FLAGS
        )
    }

    companion object {
        const val CHANNEL_ID = "r2_transfer_progress"
        const val CHANNEL_NAME = "文件传输"
        const val CHANNEL_DESC = "上传与下载进度"

        /** 单一进度通知 id（反复更新同一通知）。 */
        const val NOTIF_ID_PROGRESS = 1001

        /** 终态通知 id（与进度通知分离，互不覆盖）。 */
        const val NOTIF_ID_TERMINAL = 1002

        /** 通知点击后进入的界面：启动页（`LAUNCHER`，负责应用锁分流；字符串式引用解耦 P4）。 */
        const val STARTUP_ACTIVITY_CLASS = "com.r2manager.android.ui.startup.StartupActivity"

        /** 通知点击打开传输中心的附加标记。 */
        const val EXTRA_OPEN_TRANSFER = "com.r2manager.android.extra.OPEN_TRANSFER"

        const val REQUEST_OPEN_APP = 2001
        const val REQUEST_CANCEL_OFFSET = 100_000

        private const val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
