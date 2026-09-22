package com.r2manager.android.domain.transfer

import android.util.Log
import com.r2manager.android.core.error.ErrorType
import com.r2manager.android.data.local.db.TransferTaskDao
import com.r2manager.android.data.local.db.TransferTaskEntity
import com.r2manager.android.data.remote.s3.ProgressListener
import com.r2manager.android.domain.model.TransferDirection
import com.r2manager.android.domain.model.TransferProgress
import com.r2manager.android.domain.model.TransferStatus
import com.r2manager.android.domain.model.TransferTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 传输调度队列（P3）：内存队列 + DAO 持久化，**串行执行**（R-36 一期串行）。
 *
 * 设计：
 * - 单 worker 协程从 [Channel] 逐个取 taskId，**取一个、跑一个、跑完再取下一个**，天然串行。
 * - 每个任务在独立子协程里跑并 `join`，因此**取消某个任务不会杀掉 worker**（后续任务继续）。
 * - 队列排空时回调 [onAllIdle]，由引擎据此停止前台服务（R-32）。
 * - 任务终态后回调 [onTaskTerminal]，由引擎做列表缓存失效与状态刷新。
 * - 进度落库 + 发总线在本类完成；进度**单调不减**由 [TransferTaskRunner] 保证，本类只做节流。
 */
class TransferQueue(
    private val dao: TransferTaskDao,
    private val runner: TransferTaskRunner,
    private val progressBus: TransferProgressBus,
    private val onAllIdle: () -> Unit,
    private val onTaskTerminal: (TransferTask) -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<Long>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)

    /**
     * 已被移除（[remove]）的任务 id 集合：worker 取件后 [execute] 据此**直接早退**，
     * 保证「引擎已删 DAO 行」的排队任务不会再被执行。任务 id 不复用，故可长期保留。
     */
    private val removed = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var stopped = false

    @Volatile
    private var idle = true

    private var workerJob: Job? = null
    private var runningJob: Job? = null

    @Volatile
    private var runningTaskId: Long = -1L

    /** 启动 worker（幂等）。 */
    @Suppress("RedundantSuspendModifier")
    suspend fun start() {
        stopped = false
        ensureWorker()
    }

    /** 停止：取消 worker 与正在执行的任务（进程/服务收尾用）。 */
    fun stop() {
        stopped = true
        channel.close()
        workerJob?.cancel()
        workerJob = null
        runningJob?.cancel(CancellationException("queue stopped"))
        runningJob = null
    }

    /** 是否空闲：无排队、无正在执行。 */
    fun isIdle(): Boolean = idle && pending.get() <= 0 && runningTaskId < 0L

    /** 是否有任务正在执行。 */
    fun hasRunning(): Boolean = runningTaskId >= 0L

    /** 入队一个任务（taskId 必须已在 DAO 中且状态为 QUEUED）。 */
    fun offer(taskId: Long) {
        if (taskId <= 0L) return
        pending.incrementAndGet()
        idle = false
        if (channel.trySend(taskId).isFailure) {
            // 通道若已关闭（服务被停止），回滚计数
            pending.decrementAndGet()
        }
        ensureWorker()
    }

    /**
     * 取消一个任务。
     * - 正在执行 → 取消其子协程（Runner 内部保证 abort，R-27）。
     * - 排队中 → 直接置 CANCELLED 并移出队列。
     *
     * @return 是否已受理
     */
    fun cancel(task: TransferTask): Boolean {
        if (task.id == runningTaskId) {
            runningJob?.cancel(CancellationException("cancelled by user"))
            return true
        }
        val entity = dao.findById(task.id) ?: return false
        if (entity.status == TransferStatus.QUEUED.name) {
            // 从排队计数中移除（防止与 worker 取件竞争导致计数为负）
            pending.updateAndGet { if (it > 0) it - 1 else it }
            val cancelled = entity.copy(status = TransferStatus.CANCELLED.name, updatedAt = System.currentTimeMillis())
            dao.update(cancelled)
            onTaskTerminal(cancelled.toTask())
            maybeNotifyIdle()
            return true
        }
        return false
    }

    /**
     * 移除一个任务的**内存态**（配合 `TransferEngine.remove` 的「同时移除内存态与持久化」语义）。
     *
     * - 正在执行 → 取消其子协程（按 cancel 语义中止，Runner 内部保证 abort，R-27）。
     * - 排队中 / 尚未取件 → 仅登记墓碑；**不主动改计数**，由 worker 取件时正常递减后 [execute] 早退，
     *   避免与 worker 取件竞争导致 `pending` 计数错乱。
     */
    fun remove(taskId: Long) {
        removed.add(taskId)
        if (taskId == runningTaskId) {
            runningJob?.cancel(CancellationException("removed by user"))
        }
    }

    // —— 内部 ——

    private fun ensureWorker() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            for (id in channel) {
                if (stopped) break
                pending.decrementAndGet()
                idle = false
                val job = scope.launch { execute(id) }
                runningJob = job
                job.join()
                runningJob = null
                maybeNotifyIdle()
            }
        }
        idle = false
    }

    private fun maybeNotifyIdle() {
        if (pending.get() <= 0 && runningTaskId < 0L) {
            idle = true
            onAllIdle()
        }
    }

    private suspend fun execute(id: Long) {
        // 已被移除：直接早退（墓碑消费掉即可，避免无界增长）
        if (removed.remove(id)) return
        val entity = dao.findById(id) ?: return
        val task = entity.toTask()
        if (task.status == TransferStatus.CANCELLED || task.status == TransferStatus.DONE) return

        runningTaskId = id
        val running = task.copy(status = TransferStatus.RUNNING, updatedAt = System.currentTimeMillis())
        dao.update(running.toEntity())
        progressBus.emit(
            TransferProgress(
                taskId = id,
                direction = running.direction,
                key = running.key,
                transferred = running.transferred,
                total = running.size,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
                status = TransferStatus.RUNNING
            )
        )

        var lastTime = System.currentTimeMillis()
        var lastBytes = running.transferred
        var bytesPerSecond = 0L
        var etaSeconds = 0L
        val sink = ProgressListener { transferred, total ->
            val now = System.currentTimeMillis()
            val dt = now - lastTime
            if (dt >= PROGRESS_THROTTLE_MS) {
                val deltaBytes = (transferred - lastBytes).coerceAtLeast(0L)
                bytesPerSecond = if (dt > 0) deltaBytes * 1000L / dt else 0L
                val remaining = (total - transferred).coerceAtLeast(0L)
                etaSeconds = if (bytesPerSecond > 0L) remaining / bytesPerSecond else 0L
                lastTime = now
                lastBytes = transferred
            }
            dao.updateProgress(id, transferred, TransferStatus.RUNNING.name, now)
            progressBus.emit(
                TransferProgress(
                    taskId = id,
                    direction = running.direction,
                    key = running.key,
                    transferred = transferred,
                    total = total,
                    bytesPerSecond = bytesPerSecond,
                    etaSeconds = etaSeconds,
                    status = TransferStatus.RUNNING
                )
            )
        }

        val terminal = try {
            runner.run(running, sink)
        } catch (t: Throwable) {
            Log.e(TAG, "任务执行异常 taskId=$id", t)
            running.copy(
                status = TransferStatus.FAILED,
                errorMessage = t.message,
                updatedAt = System.currentTimeMillis()
            )
        }

        runningTaskId = -1L
        val persisted = terminal.copy(updatedAt = System.currentTimeMillis())
        dao.update(persisted.toEntity())
        // 先回传终态给引擎（同步更新内存态），再广播终态进度，
        // 保证前台服务收到终态事件时 tasks.value 已是终态（能构建正确的完成通知）。
        onTaskTerminal(persisted)
        progressBus.emit(
            TransferProgress(
                taskId = persisted.id,
                direction = persisted.direction,
                key = persisted.key,
                transferred = persisted.transferred,
                total = persisted.size,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
                status = persisted.status
            )
        )
    }

    private companion object {
        const val TAG = "TransferQueue"
        const val PROGRESS_THROTTLE_MS = 300L
    }
}

// —— 实体 / 领域模型互转（P3 内部共享）——

internal fun TransferTaskEntity.toTask(): TransferTask = TransferTask(
    id = id,
    direction = parseEnum(direction, TransferDirection.UPLOAD),
    bucket = bucket,
    key = key,
    localUri = localUri,
    size = size,
    transferred = transferred,
    status = parseEnum(status, TransferStatus.QUEUED),
    errorMessage = errorMessage,
    errorType = errorType?.let { parseEnum(it, ErrorType.UNKNOWN) },
    createdAt = createdAt,
    updatedAt = updatedAt,
    uploadId = uploadId,
    uploadedParts = uploadedParts
)

internal fun TransferTask.toEntity(): TransferTaskEntity = TransferTaskEntity(
    id = id,
    direction = direction.name,
    bucket = bucket,
    key = key,
    localUri = localUri,
    size = size,
    transferred = transferred,
    status = status.name,
    errorMessage = errorMessage,
    errorType = errorType?.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    uploadId = uploadId,
    uploadedParts = uploadedParts
)

private inline fun <reified T : Enum<T>> parseEnum(value: String?, fallback: T): T =
    if (value == null) {
        fallback
    } else {
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
    }
