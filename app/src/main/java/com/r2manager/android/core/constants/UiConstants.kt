package com.r2manager.android.core.constants

/**
 * UI 层通用常量（动画时长、分页、节流等），避免散落魔法数字。
 */
object UiConstants {
    /** 短动画时长（毫秒）。 */
    const val ANIM_DURATION_SHORT_MS: Long = 150L

    /** 常规动画时长（毫秒）。 */
    const val ANIM_DURATION_MS: Long = 220L

    /** 预览页工具条自动淡出时长（毫秒）。 */
    const val PREVIEW_TOOLBAR_AUTO_HIDE_MS: Long = 3_000L

    /** 触底加载提前量（条目数）。 */
    const val LOAD_MORE_THRESHOLD: Int = 5

    /** 列表网格列数（竖屏）。 */
    const val GRID_SPAN_PORTRAIT: Int = 3

    /** 列表网格列数（横屏）。 */
    const val GRID_SPAN_LANDSCAPE: Int = 5

    /** 进度回调合并节流（毫秒），避免 UI 抖动。 */
    const val PROGRESS_THROTTLE_MS: Long = 250L

    /** 多选全选的稳定标识（用于 Diff）。 */
    const val SNACKBAR_DURATION_MS: Long = 4_000L

    /**
     * 传输通知点击后「直达传输中心」的 Intent extra 键。
     *
     * 值必须与 P3 [com.r2manager.android.domain.transfer.TransferNotifications.EXTRA_OPEN_TRANSFER] 完全一致
     * （`com.r2manager.android.extra.OPEN_TRANSFER`）；P3 以字符串常量脱耦 P4，P4 侧统一引用本常量，
     * 避免 P4 反向依赖 P3。
     */
    const val EXTRA_OPEN_TRANSFER: String = "com.r2manager.android.extra.OPEN_TRANSFER"

    /**
     * 凭证缺失 / 不完整 / 未选桶时「直达设置页引导」的 Intent extra 键（PRD R-01 冷启动分流）。
     *
     * 与 [EXTRA_OPEN_TRANSFER] 同级、集中于此避免重复定义；由
     * [com.r2manager.android.ui.main.MainActivity] 在 `onCreate` 与 `onNewIntent` 两个入口
     * 统一解析。二者同时存在时，**「传输」优先于「设置」**。
     */
    const val EXTRA_OPEN_SETTINGS: String = "com.r2manager.android.extra.OPEN_SETTINGS"
}
