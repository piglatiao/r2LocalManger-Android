package com.r2manager.android.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.r2manager.android.AppContainer
import com.r2manager.android.domain.model.TransferTask
import com.r2manager.android.domain.transfer.TransferEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * 传输任务中心 ViewModel（P4-B）。
 *
 * 唯一数据来源是 P3 的 [TransferEngine]（`tasks` StateFlow）；本类**不持有**任何任务副本，
 * 仅做「按状态分组」的透传，供 [TransferFragment] 三 Tab 渲染。所有任务操作（取消 / 重试 /
 * 移除 / 清空）一律转发给引擎，保证 DAO 与内存态由引擎统一维护、不出现两份真相。
 *
 * 状态机（P3 定案，§7.4）：`QUEUED → RUNNING → (DONE | FAILED | CANCELLED)`；
 * 三 Tab 映射：进行中 = `QUEUED/RUNNING`、已完成 = `DONE`、失败 = `FAILED/CANCELLED`。
 */
class TransferViewModel(private val container: AppContainer) : ViewModel() {

    private val engine: TransferEngine = container.transferEngine

    /** 全部任务（引擎单一数据源）；[TransferFragment] 负责按状态分三组。 */
    val tasks: StateFlow<List<TransferTask>> = engine.tasks

    // ==================== 任务操作（转发引擎） ====================

    /** 取消单个任务（RUNNING → abort；QUEUED → 出队，R-27）。 */
    fun cancel(taskId: Long) = engine.cancel(taskId)

    /** 重试单个失败 / 取消的任务（复用同一行，R-31）。 */
    fun retry(taskId: Long) = engine.retry(taskId)

    /** 重试全部失败任务。 */
    fun retryAllFailed() = engine.retryAllFailed()

    /** 全部取消。 */
    fun cancelAll() = engine.cancelAll()

    /** 清空已完成（含已取消）任务。 */
    fun clearCompleted() = engine.clearCompleted()

    /**
     * 移除单条任务（传输中心「移除任务」）。
     *
     * 直接调用引擎 [TransferEngine.remove]：同时移除**内存态与持久化**；运行中 / 排队中的任务
     * 先按 cancel 语义中止再移除。引擎内部以墓碑保证该任务不会在下次发射时被重新捡回，
     * 因此 UI 无需再维护任何「已隐藏 id」副本（避免 DAO 与内存态不一致）。
     */
    fun removeTask(taskId: Long) = engine.remove(taskId)

    companion object {
        /**
         * 构造工厂：从 [AppContainer] 取依赖（UI 层不自行 `new` 引擎 / DAO）。
         *
         * @param container 全局依赖容器
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TransferViewModel(container) as T
                }
            }
    }
}
