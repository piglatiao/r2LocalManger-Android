package com.r2manager.android.core.error

/**
 * 用户可执行的恢复动作，UI 据此渲染主动作按钮。
 */
enum class RecoveryAction {
    /** 可重试（网络类）。 */
    RETRY,

    /** 前往凭证配置页（认证类）。 */
    GO_TO_CREDENTIALS,

    /** 刷新列表（对象 / 桶不存在）。 */
    REFRESH_LIST,

    /** 重新选择下载目录（SAF 授权失效）。 */
    RESELECT_DIRECTORY,

    /** 打开运行日志（未知错误）。 */
    OPEN_LOGS,

    /** 无明确恢复动作。 */
    NONE
}
