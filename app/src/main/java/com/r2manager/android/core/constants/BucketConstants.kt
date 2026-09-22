package com.r2manager.android.core.constants

/**
 * 存储桶相关常量（位置枚举、命名规则、存储类型）。
 */
object BucketConstants {
    /** 可用位置提示枚举（空 = auto）。 */
    val LOCATION_HINTS: List<String> = listOf("auto", "apac", "eeur", "enam", "weur", "wnam", "oc")

    /** 桶名合法字符规则。 */
    const val NAME_REGEX: String = "^[a-z0-9][a-z0-9-]*[a-z0-9]$"

    /** 标准存储类型。 */
    const val STORAGE_STANDARD: String = "Standard"

    /** 低频访问存储类型。 */
    const val STORAGE_INFREQUENT: String = "InfrequentAccess"

    /** 桶名长度下限。 */
    const val NAME_MIN_LENGTH: Int = 3

    /** 桶名长度上限。 */
    const val NAME_MAX_LENGTH: Int = 64

    /**
     * 校验桶名：长度 3..64 且匹配 [NAME_REGEX]。
     *
     * @param name 待校验桶名
     * @return 合法返回 true
     */
    fun isValidName(name: String): Boolean =
        name.length in NAME_MIN_LENGTH..NAME_MAX_LENGTH && Regex(NAME_REGEX).matches(name)
}
