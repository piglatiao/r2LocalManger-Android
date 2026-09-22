package com.r2manager.android.core.error

/**
 * 错误分类枚举（优先级见 [ErrorMapper]）。
 *
 * 前 7 类与桌面版 `R2Client._classifyError` 对齐；后 2 类为 Android 平台特有分支。
 */
enum class ErrorType {
    /** 本地文件系统：ENOENT / EACCES / ENOSPC 等。 */
    FILE_SYSTEM,

    /** 网络类错误（非"设备离线"判定）。 */
    NETWORK,

    /** 认证失败：凭证错误 / 签名不匹配 / Token 失效。 */
    AUTH,

    /** 权限不足：AccessDenied，或 403 且非 InvalidAccessKeyId。 */
    PERMISSION,

    /** 桶不存在或不可访问。 */
    BUCKET,

    /** 对象不存在。 */
    OBJECT,

    /** 设备无网络连接（ConnectivityManager 判定，Android 特有）。 */
    NO_CONNECTION,

    /** SAF 授权失效（目录被删 / 权限被回收，Android 特有）。 */
    SAF_PERMISSION,

    /** 兜底未知错误。 */
    UNKNOWN
}
