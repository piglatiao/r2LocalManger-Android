package com.r2manager.android.core.crypto

import com.r2manager.android.core.constants.CacheConstants
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 加解密，密文格式与桌面版 `LocalCryptoService` **字节级一致**：
 *
 * ```
 * [ MAGIC "R2CE"(4B) | version(1B) | IV(12B) | tag(16B) | ciphertext(..) ]
 * ```
 *
 * 说明：
 * - [decrypt] 遇到"非本格式"的数据按明文原样返回（兼容旧数据 / 降级写入）；
 * - 密钥不符或密文损坏（GCM 校验失败）返回 null。
 */
object AesGcmCipher {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = CacheConstants.TAG_LENGTH * 8

    /** 头部长度 = 魔数 + 版本号 + IV + tag。 */
    private const val HEADER_LENGTH = 4 + 1 + CacheConstants.IV_LENGTH + CacheConstants.TAG_LENGTH

    private val random = SecureRandom()

    /**
     * 加密。
     *
     * @param key 32 字节密钥
     * @param plain 明文（空数组原样返回）
     * @return 密文（含头部）
     */
    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        require(key.size == CacheConstants.KEY_LENGTH) {
            "AES-256-GCM 密钥长度必须为 ${CacheConstants.KEY_LENGTH} 字节，实际 ${key.size}"
        }
        if (plain.isEmpty()) {
            return plain
        }

        val iv = ByteArray(CacheConstants.IV_LENGTH)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        // SunJCE 的 GCM 输出为 ciphertext || tag
        val output = cipher.doFinal(plain)
        val tagOffset = output.size - CacheConstants.TAG_LENGTH
        val cipherText = output.copyOfRange(0, tagOffset)
        val tag = output.copyOfRange(tagOffset, output.size)

        val result = ByteArray(HEADER_LENGTH + cipherText.size)
        var pos = 0
        System.arraycopy(CacheConstants.MAGIC, 0, result, pos, CacheConstants.MAGIC.size)
        pos += CacheConstants.MAGIC.size
        result[pos] = CacheConstants.CRYPTO_FORMAT_VERSION.toByte()
        pos += 1
        System.arraycopy(iv, 0, result, pos, iv.size)
        pos += iv.size
        System.arraycopy(tag, 0, result, pos, tag.size)
        pos += tag.size
        System.arraycopy(cipherText, 0, result, pos, cipherText.size)
        return result
    }

    /**
     * 解密。
     *
     * @param key 32 字节密钥
     * @param stored 密文（含头部）
     * @return 明文；非本格式返回原数据；密钥不符 / 损坏返回 null
     */
    fun decrypt(key: ByteArray, stored: ByteArray): ByteArray? {
        if (stored.isEmpty()) {
            return null
        }
        // 非本格式：按明文原样返回
        if (!isEncrypted(stored)) {
            return stored
        }
        if (key.size != CacheConstants.KEY_LENGTH) {
            return null
        }
        return try {
            val version = stored[CacheConstants.MAGIC.size].toInt()
            if (version != CacheConstants.CRYPTO_FORMAT_VERSION) {
                return null
            }
            var pos = CacheConstants.MAGIC.size + 1
            val iv = stored.copyOfRange(pos, pos + CacheConstants.IV_LENGTH)
            pos += CacheConstants.IV_LENGTH
            val tag = stored.copyOfRange(pos, pos + CacheConstants.TAG_LENGTH)
            pos += CacheConstants.TAG_LENGTH
            val cipherText = stored.copyOfRange(pos, stored.size)

            // SunJCE 需要 ciphertext || tag
            val combined = cipherText + tag
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(combined)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 判断是否为本格式密文（魔数匹配且长度不小于头部）。
     *
     * @param data 待判断数据
     */
    fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < HEADER_LENGTH) {
            return false
        }
        for (i in CacheConstants.MAGIC.indices) {
            if (data[i] != CacheConstants.MAGIC[i]) {
                return false
            }
        }
        return true
    }
}
