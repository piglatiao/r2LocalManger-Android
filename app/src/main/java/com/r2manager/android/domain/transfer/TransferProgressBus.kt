package com.r2manager.android.domain.transfer

import com.r2manager.android.domain.model.TransferProgress
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 传输进度总线（P3）。
 *
 * 设计要点（见架构 §8.9-6）：**进度唯一来源**。前台服务与 UI 卡片都只订阅此 Flow，
 * 前台服务不自行产生进度，避免「双重来源」导致的抖动。
 *
 * 采用 `conflate` 语义（replay=1 + DROP_OLDEST）：高频进度回调不会阻塞生产者，
 * 订阅方（UI / 通知）只需拿到最新一条即可。
 */
class TransferProgressBus {

    private val mutableFlow: MutableSharedFlow<TransferProgress> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** 只读进度流；`conflate` 合并拥堵，避免 UI 抖动。 */
    val flow: SharedFlow<TransferProgress> = mutableFlow.asSharedFlow()

    /**
     * 投递一条进度。非挂起、线程安全、永不阻塞调用方。
     */
    fun emit(p: TransferProgress) {
        mutableFlow.tryEmit(p)
    }
}
