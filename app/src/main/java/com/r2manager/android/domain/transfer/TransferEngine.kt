package com.r2manager.android.domain.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.r2manager.android.core.util.PathUtils
import com.r2manager.android.core.util.UriUtils
import com.r2manager.android.data.local.cache.ObjectListCache
import com.r2manager.android.data.local.db.TransferTaskDao
import com.r2manager.android.data.remote.s3.S3Client
import com.r2manager.android.domain.model.TransferDirection
import com.r2manager.android.domain.model.TransferProgress
import com.r2manager.android.domain.model.TransferStatus
import com.r2manager.android.domain.model.TransferTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 传输引擎对外接口（P3）。UI/ViewModel 只依赖本接口。
 */
interface TransferEngine {
    /** 全量任务，UI 按状态分组。 */
    val tasks: StateFlow<List<TransferTask>>

    /** 进度流（conflate），供 UI 卡片与前台服务通知共用。 */
    val progress: SharedFlow<TransferProgress>

    /** 入队上传，返回任务 id 列表。 */
    suspend fun enqueueUpload(uris: List<Uri>, targetPrefix: String): List<Long>

    /** 入队下载（统一 SAF 目录树），返回任务 id 列表。 */
    suspend fun enqueueDownload(tasks: List<DownloadRequest>, treeUri: Uri): List<Long>

    /** 取消单个任务（RUNNING → 立即 abort；QUEUED → 移出队列）。 */
    fun cancel(taskId: Long)

    /** 重试单个失败/取消的任务（复用同一行，不新建同名任务，R-31）。 */
    fun retry(taskId: Long)

    /**
     * 从**内存态与持久化中同时移除**一条任务（传输中心的「移除任务」，增量契约）。
     *
     * 语义：若该任务正在运行 / 排队，先按 [cancel] 语义中止，再删除其 DB 行并同步内存态，
     * 保证 [tasks] 不会在下次发射时把已移除的任务重新捡回（避免 DAO 与内存态不一致）。
     */
    fun remove(taskId: Long)

    fun cancelAll()
    fun retryAllFailed()
    fun clearCompleted()

    /** 上传前查同名冲突（R-28）。 */
    suspend fun findConflicts(prefix: String, names: List<String>): List<String>

    /** 进程重启后恢复：RUNNING/QUEUED → FAILED（可重试），满足 R-33。 */
    suspend fun restore()
}

/** 单条下载请求。 */
data class DownloadRequest(val bucket: String, val key: String, val size: Long, val relativePath: String?)

/**
 * 引擎定位器：让前台服务在不依赖 P1 `AppContainer` 具体成员命名的前提下拿到引擎实例。
 *
 * [TransferEngineImpl] 构造时自注册；`AppContainer` 只需 `TransferEngineImpl(...)` 即可。
 */
object TransferEngineLocator {
    @Volatile
    var engine: TransferEngine? = null
        internal set
}

/**
 * 传输引擎实现（P3）。
 *
 * 职责：任务入队/持久化、启动/停止前台服务、串行调度委托给 [TransferQueue]、
 * 完成后失效列表缓存、对外暴露 [tasks]/[progress]。
 *
 * 依赖：P2 的 [S3Client]（经 `s3Provider` 惰性获取，支持换凭证后重建）、[TransferTaskDao]、
 * [ObjectListCache]；P1 的 `PathUtils`/`UriUtils` 与领域模型。
 */
class TransferEngineImpl(
    private val context: Context,
    private val dao: TransferTaskDao,
    private val s3Provider: () -> S3Client?,
    private val objectListCache: ObjectListCache,
    private val downloadTargetResolver: DownloadTargetResolver,
    private val currentBucketProvider: () -> String
) : TransferEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 进度总线（`AppContainer` 可复用同一实例，避免出现两条总线）。 */
    internal val progressBus = TransferProgressBus()

    private val mutableTasks = MutableStateFlow<List<TransferTask>>(emptyList())

    override val tasks: StateFlow<List<TransferTask>> = mutableTasks.asStateFlow()

    override val progress: SharedFlow<TransferProgress> = progressBus.flow

    /**
     * 已移除任务的「墓碑」集合（增量契约 [remove]）。
     *
     * 一旦某 id 加入本集合：[handleTerminal] 不再把它回灌内存态、[refreshAsync] 也过滤掉它，
     * 用于防止「删除 DAO 行后，正在取消的任务稍后回写终态」把已移除任务重新捡回（DAO 与内存态不一致）。
     * 任务 id 自增且不复用，故墓碑可长期保留（规模有界）。
     */
    private val removedIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    private val queue = TransferQueue(
        dao = dao,
        runner = TransferTaskRunner(context, s3Provider),
        progressBus = progressBus,
        onAllIdle = { handleAllIdle() },
        onTaskTerminal = { task -> handleTerminal(task) }
    )

    init {
        TransferEngineLocator.engine = this
        refreshAsync()
    }

    // —— 入队 ——

    override suspend fun enqueueUpload(uris: List<Uri>, targetPrefix: String): List<Long> {
        if (uris.isEmpty()) return emptyList()
        val ids = withContext(Dispatchers.IO) {
            val bucket = currentBucketProvider()
            val now = System.currentTimeMillis()
            val result = ArrayList<Long>(uris.size)
            for (uri in uris) {
                val name = UriUtils.queryDisplayName(context, uri)
                    ?: uri.lastPathSegment
                    ?: "unnamed"
                val size = UriUtils.querySize(context, uri)
                val key = PathUtils.joinKey(PathUtils.normalizePrefix(targetPrefix), name)
                val task = TransferTask(
                    id = 0L,
                    direction = TransferDirection.UPLOAD,
                    bucket = bucket,
                    key = key,
                    localUri = uri.toString(),
                    size = size,
                    transferred = 0L,
                    status = TransferStatus.QUEUED,
                    errorMessage = null,
                    errorType = null,
                    createdAt = now,
                    updatedAt = now
                )
                result.add(dao.insert(task.toEntity()))
            }
            result
        }
        if (ids.isNotEmpty()) {
            refreshAsync()
            TransferService.start(context)
            ids.forEach { queue.offer(it) }
        }
        return ids
    }

    override suspend fun enqueueDownload(tasks: List<DownloadRequest>, treeUri: Uri): List<Long> {
        if (tasks.isEmpty()) return emptyList()
        val ids = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val result = ArrayList<Long>(tasks.size)
            for (req in tasks) {
                val fileName = req.key.substringAfterLast('/').ifBlank { req.key }
                // SAF 目标解析（授权失效会抛 SecurityException，由 UI 映射为 SAF_PERMISSION）
                val targetUri = downloadTargetResolver.createTarget(treeUri, req.relativePath, fileName)
                val task = TransferTask(
                    id = 0L,
                    direction = TransferDirection.DOWNLOAD,
                    bucket = req.bucket,
                    key = req.key,
                    localUri = targetUri.toString(),
                    size = req.size,
                    transferred = 0L,
                    status = TransferStatus.QUEUED,
                    errorMessage = null,
                    errorType = null,
                    createdAt = now,
                    updatedAt = now
                )
                result.add(dao.insert(task.toEntity()))
            }
            result
        }
        if (ids.isNotEmpty()) {
            refreshAsync()
            TransferService.start(context)
            ids.forEach { queue.offer(it) }
        }
        return ids
    }

    // —— 取消 / 重试 ——

    override fun cancel(taskId: Long) {
        engineScope.launch {
            val task = dao.findById(taskId)?.toTask() ?: return@launch
            queue.cancel(task)
            refreshAsync()
        }
    }

    override fun retry(taskId: Long) {
        engineScope.launch {
            val entity = dao.findById(taskId) ?: return@launch
            if (entity.status != TransferStatus.FAILED.name && entity.status != TransferStatus.CANCELLED.name) {
                return@launch
            }
            // 复用同一行 → 不创建重复同名任务（R-31）
            val requeued = entity.copy(
                status = TransferStatus.QUEUED.name,
                transferred = 0L,
                errorMessage = null,
                errorType = null,
                uploadId = null,
                uploadedParts = 0,
                updatedAt = System.currentTimeMillis()
            )
            dao.update(requeued)
            mutableTasks.value = mergeTask(mutableTasks.value, requeued.toTask())
            TransferService.start(context)
            queue.offer(requeued.id)
        }
    }

    override fun remove(taskId: Long) {
        engineScope.launch {
            // 先登记墓碑：即便队列/执行器随后回写终态，也不会再回灌内存态（避免被重新捡回）
            removedIds.add(taskId)
            val task = dao.findById(taskId)?.toTask()
            if (task != null &&
                (task.status == TransferStatus.RUNNING || task.status == TransferStatus.QUEUED)
            ) {
                // 运行中 → 按 cancel 语义中止；排队中 → 登记墓碑并让 worker 取件后早退
                queue.remove(taskId)
            }
            dao.delete(taskId)
            // 立即从内存态剔除，避免 UI 闪回
            mutableTasks.value = mutableTasks.value.filterNot { it.id == taskId }
        }
    }

    override fun cancelAll() {
        engineScope.launch {
            val entities = dao.listAll()
            // 先取消正在执行的
            entities.firstOrNull { it.status == TransferStatus.RUNNING.name }
                ?.let { queue.cancel(it.toTask()) }
            // 再取消排队中的
            entities.filter { it.status == TransferStatus.QUEUED.name }
                .forEach { queue.cancel(it.toTask()) }
            refreshAsync()
        }
    }

    override fun retryAllFailed() {
        engineScope.launch {
            dao.listByStatus(TransferStatus.FAILED.name).forEach { retry(it.id) }
        }
    }

    override fun clearCompleted() {
        engineScope.launch {
            dao.deleteCompleted()
            refreshAsync()
        }
    }

    // —— 冲突检查 / 恢复 ——

    override suspend fun findConflicts(prefix: String, names: List<String>): List<String> {
        if (names.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            val s3 = s3Provider() ?: return@withContext emptyList()
            val page = s3.listObjectsV2(prefix = PathUtils.normalizePrefix(prefix), delimiter = "/")
            val existing = page.objects.filter { !it.isFolder }.map { it.name }.toSet()
            names.filter { it in existing }
        }
    }

    override suspend fun restore() {
        withContext(Dispatchers.IO) {
            dao.markInterruptedAsFailed(System.currentTimeMillis())
        }
        refreshAsync()
    }

    // —— 内部 ——

    private fun handleTerminal(task: TransferTask) {
        // 已移除的任务不再回灌（避免取消中的任务回写终态后被重新捡回）
        if (task.id in removedIds) return
        // 先乐观更新内存态（前台服务可能立即读到）
        mutableTasks.value = mergeTask(mutableTasks.value, task)
        if (task.status == TransferStatus.DONE && task.direction == TransferDirection.UPLOAD) {
            invalidateListCache(task)
        }
        refreshAsync()
    }

    /**
     * 队列排空（R-32）：延迟一小段时间再停服务，给前台服务留出投放「终态通知」的时间窗，
     * 期间若有新任务入队（isIdle 变 false）则放弃停止。
     */
    private fun handleAllIdle() {
        engineScope.launch {
            delay(IDLE_STOP_DELAY_MS)
            if (queue.isIdle()) {
                TransferService.stop(context)
            }
        }
    }

    private fun invalidateListCache(task: TransferTask) {
        engineScope.launch {
            runCatching {
                val dirPrefix = if (task.key.contains('/')) task.key.substringBeforeLast('/') else ""
                val prefixes = PathUtils.parentPrefixes(dirPrefix).toSet()
                objectListCache.invalidate(task.bucket, prefixes)
            }.onFailure { Log.w(TAG, "列表缓存失效失败", it) }
        }
    }

    private fun refreshAsync() {
        engineScope.launch {
            mutableTasks.value = dao.listAll().map { it.toTask() }.filterNot { it.id in removedIds }
        }
    }

    private fun mergeTask(list: List<TransferTask>, task: TransferTask): List<TransferTask> {
        val exists = list.any { it.id == task.id }
        return if (exists) list.map { if (it.id == task.id) task else it } else list + task
    }

    private companion object {
        const val TAG = "TransferEngine"
        const val IDLE_STOP_DELAY_MS = 600L
    }
}
