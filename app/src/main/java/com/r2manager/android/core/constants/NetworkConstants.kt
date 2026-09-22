package com.r2manager.android.core.constants

/**
 * 网络层超时与列举参数（P2 的 OkHttp / S3 / Cloudflare 客户端统一引用）。
 */
object NetworkConstants {
    /** 连接超时（秒）。 */
    const val CONNECT_TIMEOUT_S: Long = 15

    /** 读超时（秒）。 */
    const val READ_TIMEOUT_S: Long = 30

    /** 写超时（秒）。 */
    const val WRITE_TIMEOUT_S: Long = 60

    /** 总调用超时（秒）；0 = 不设总时限，大文件靠主动取消。 */
    const val CALL_TIMEOUT_S: Long = 0

    /** 启动静默探测超时（毫秒）。 */
    const val STARTUP_PROBE_TIMEOUT_MS: Long = 10_000

    /** 单页列对象上限。 */
    const val LIST_MAX_KEYS: Int = 1000

    /** R2 固定 region。 */
    const val REGION: String = "auto"

    /** 默认 jurisdiction。 */
    const val DEFAULT_JURISDICTION: String = "default"

    /** Cloudflare 管理面 Base URL。 */
    const val CLOUDFLARE_API_BASE: String = "https://api.cloudflare.com/client/v4"

    /** R2 S3 端点域名后缀。 */
    const val R2_ENDPOINT_SUFFIX: String = ".r2.cloudflarestorage.com"
}
