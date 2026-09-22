package com.r2manager.android.domain.transfer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.r2manager.android.domain.model.TransferProgress
import com.r2manager.android.domain.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 传输前台服务（P3）。`foregroundServiceType="dataSync"`。
 *
 * 职责（见架构 §5.1、§8.9-6、R-32）：
 * - 保活 + 维持**唯一进度通知**；进度来源是 [TransferEngine.progress]（[TransferProgressBus]），
 *   服务本身**不产生**进度，避免双重来源。
 * - 无进行中任务时由引擎调用 [stop]，服务 `stopForeground(STOP_FOREGROUND_REMOVE)` 并撤掉进度通知（R-32）。
 * - 通知点「取消」→ [ACTION_CANCEL_TASK] 直接落回引擎 `cancel(taskId)`。
 * - 通知点击 → 打开应用直达传输中心（见 [TransferNotifications]）。
 * - **POST_NOTIFICATIONS 未授权**：`startForeground` 照常执行（服务仍需保活），通知更新静默失败，
 *   应用内进度为主，**通知缺失不阻塞任务**（PRD §6-1 兜底）。
 */
class TransferService : Service() {

    private lateinit var notifications: TransferNotifications
    private var serviceScope: CoroutineScope? = null
    private var observeJob: Job? = null
    private var isForeground = false

    private val engine: TransferEngine?
        get() = TransferEngineLocator.engine

    override fun onCreate() {
        super.onCreate()
        notifications = TransferNotifications(this)
        notifications.ensureChannel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId > 0L) {
                    engine?.cancel(taskId)
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START 或系统重启（intent == null）
                promoteToForeground()
                startObserving()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        observeJob = null
        serviceScope?.cancel()
        serviceScope = null
        stopForegroundCompat()
        runCatching { NotificationManagerCompat.from(this).cancel(TransferNotifications.NOTIF_ID_PROGRESS) }
        isForeground = false
        super.onDestroy()
    }

    /** 提升为前台服务；缺失通知权限也不阻塞（R-32 / PRD §6-1）。 */
    private fun promoteToForeground() {
        if (isForeground) return
        val notification = notifications.buildIdle()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TransferNotifications.NOTIF_ID_PROGRESS,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(TransferNotifications.NOTIF_ID_PROGRESS, notification)
            }
            isForeground = true
        } catch (t: Throwable) {
            // 极端情况下（如后台启动被拒/权限缺失）仍需继续跑任务：任务进度以应用内为准
            Log.w(TAG, "startForeground 失败，降级为应用内进度", t)
            isForeground = false
        }
    }

    /** 订阅进度流：进度通知与终态通知。 */
    private fun startObserving() {
        if (observeJob?.isActive == true) return
        val scope = serviceScope ?: return
        val bus = engine?.progress
        if (bus == null) {
            Log.w(TAG, "引擎未就绪，暂不订阅进度")
            return
        }
        observeJob = scope.launch {
            bus.collectLatest { progress -> handleProgress(progress) }
        }
    }

    private fun handleProgress(progress: TransferProgress) {
        when (progress.status) {
            TransferStatus.RUNNING, TransferStatus.QUEUED -> {
                val task = engine?.tasks?.value?.firstOrNull { it.id == progress.taskId }
                val notification = if (task != null) {
                    notifications.buildProgress(task, progress)
                } else {
                    null
                }
                if (notification != null) {
                    runCatching {
                        NotificationManagerCompat.from(this)
                            .notify(TransferNotifications.NOTIF_ID_PROGRESS, notification)
                    }
                }
            }
            TransferStatus.DONE, TransferStatus.FAILED, TransferStatus.CANCELLED -> {
                val task = engine?.tasks?.value?.firstOrNull { it.id == progress.taskId }
                if (task != null) {
                    runCatching {
                        NotificationManagerCompat.from(this)
                            .notify(TransferNotifications.NOTIF_ID_TERMINAL, notifications.buildTerminal(task))
                    }
                }
            }
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val TAG = "TransferService"

        const val ACTION_START = "com.r2manager.android.action.START_TRANSFER"
        const val ACTION_STOP = "com.r2manager.android.action.STOP_TRANSFER"
        const val ACTION_CANCEL_TASK = "com.r2manager.android.action.CANCEL_TASK"
        const val EXTRA_TASK_ID = "task_id"

        /** 启动前台服务（dataSync）。 */
        fun start(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_START
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "启动前台服务失败", it) }
        }

        /** 停止服务：`stopForeground` + 撤通知由 [onDestroy] 完成（R-32）。 */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TransferService::class.java)) }
        }
    }
}
