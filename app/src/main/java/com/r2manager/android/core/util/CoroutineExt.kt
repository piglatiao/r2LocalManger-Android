package com.r2manager.android.core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 协程扩展工具。
 */
object CoroutineExt {

    /**
     * 在 IO 调度器上执行挂起代码块。
     *
     * @param block 挂起代码块
     */
    suspend fun <T> withIo(block: suspend () -> T): T = withContext(AppDispatchers.io) { block() }

    /**
     * 在 Default 调度器上执行挂起代码块。
     *
     * @param block 挂起代码块
     */
    suspend fun <T> withDefault(block: suspend () -> T): T = withContext(AppDispatchers.default) { block() }

    /**
     * 把"取消回调"绑定到当前协程：协程被取消时执行 [onCancel]。
     *
     * 用于把 OkHttp `Call.cancel()` / `CancellationSignal.cancel()` 挂到取消上（R-27）。
     * 必须在挂起函数内调用。
     *
     * @param onCancel 取消时执行的动作
     */
    suspend fun bindCancellation(onCancel: () -> Unit) {
        val job = coroutineContext[Job]
        job?.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) {
                runCatching { onCancel() }
            }
        }
    }
}

/**
 * 启动一个 IO 协程（便捷扩展）。
 *
 * @param block 挂起代码块
 */
fun CoroutineScope.launchIo(block: suspend CoroutineScope.() -> Unit): Job =
    launch(AppDispatchers.io) { block() }

/**
 * 启动一个 Default 协程（便捷扩展）。
 *
 * @param block 挂起代码块
 */
fun CoroutineScope.launchDefault(block: suspend CoroutineScope.() -> Unit): Job =
    launch(AppDispatchers.default) { block() }
