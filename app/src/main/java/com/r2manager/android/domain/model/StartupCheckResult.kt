package com.r2manager.android.domain.model

/**
 * 启动检查结果。
 *
 * @property ok 是否通过
 * @property failure 失败原因（ok=true 时为 null）
 */
data class StartupCheckResult(
    val ok: Boolean,
    val failure: Failure?
) {
    /** 启动检查失败分类。 */
    enum class Failure {
        /** 未配置凭证。 */
        MISSING_CREDENTIALS,

        /** R2 配置无效。 */
        INVALID_SETTINGS,

        /** 无法连接到 R2。 */
        CONNECTION_FAILED
    }
}
