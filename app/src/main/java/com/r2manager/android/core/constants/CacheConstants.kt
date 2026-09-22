package com.r2manager.android.core.constants

/**
 * 本地缓存相关常量。
 *
 * - 缩略图缓存默认 512MB，可配置区间 128MB ~ 4GB；
 * - 列表缓存默认 300 条 / 64MB；
 * - 密文格式常量与桌面版 [LocalCryptoService] 保持一致（与 core.crypto.AesGcmCipher 联动）。
 */
object CacheConstants {
    /** 缩略图缓存默认上限。 */
    const val DEFAULT_THUMBNAIL_MAX_BYTES: Long = 512L * 1024 * 1024 // 512 MB

    /** 缩略图缓存下限。 */
    const val MIN_THUMBNAIL_MAX_BYTES: Long = 128L * 1024 * 1024 // 128 MB

    /** 缩略图缓存上限。 */
    const val MAX_THUMBNAIL_MAX_BYTES: Long = 4L * 1024 * 1024 * 1024 // 4 GB

    /** 设置页可选的缩略图缓存档位（MB）。 */
    val THUMBNAIL_MAX_OPTIONS_MB: List<Int> = listOf(128, 256, 512, 1024, 2048, 4096)

    /** 列表缓存默认最大条目数。 */
    const val LIST_CACHE_DEFAULT_MAX_ENTRIES: Int = 300

    /** 列表缓存默认最大字节数。 */
    const val LIST_CACHE_DEFAULT_MAX_BYTES: Long = 64L * 1024 * 1024

    /** 索引落盘的合并延迟（毫秒），避免频繁写盘。 */
    const val INDEX_FLUSH_DELAY_MS: Long = 1500L

    /** 密文魔数："R2CE"。 */
    val MAGIC: ByteArray = byteArrayOf(0x52, 0x32, 0x43, 0x45)

    /** 密文格式版本号。 */
    const val CRYPTO_FORMAT_VERSION: Int = 1

    /** AES-GCM IV 长度（字节）。 */
    const val IV_LENGTH: Int = 12

    /** AES-GCM auth tag 长度（字节）。 */
    const val TAG_LENGTH: Int = 16

    /** 密钥长度（字节）。 */
    const val KEY_LENGTH: Int = 32

    /** 盐长度（字节）。 */
    const val SALT_LENGTH: Int = 16

    /** PBKDF2 迭代次数（替代桌面版 scrypt）。 */
    const val PBKDF2_ITERATIONS: Int = 120_000
}
