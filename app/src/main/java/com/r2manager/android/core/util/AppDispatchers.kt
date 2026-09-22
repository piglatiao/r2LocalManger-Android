package com.r2manager.android.core.util

import kotlinx.coroutines.Dispatchers

/**
 * 全局协程调度器（单点定义，便于测试替换）。
 */
object AppDispatchers {
    /** 网络 / 磁盘 IO。 */
    val io = Dispatchers.IO

    /** CPU 密集（缩略图编码、PDF 渲染）。 */
    val default = Dispatchers.Default

    /** 主线程。 */
    val main = Dispatchers.Main
}
