package com.r2manager.android.core.crypto

import com.r2manager.android.core.constants.CacheConstants
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 密钥派生与校验工具。
 *
 * 与桌面版差异（有意为之，见 `docs/02 §11`）：桌面版用 **scrypt**，Android 侧改用
 * **PBKDF2WithHmacSHA256**。两者派生算法不同，**同一密码在两个平台上得到的密钥不可互换**；
 * 因此 **不支持读取桌面版遗留的加密缓存文件**（桌面版缓存需在桌面端重新生成，或在本端重新拉取）。
 *
 * 输出 32 字节密钥；校验值用 HmacSHA256，不落盘密码本身。
 */
object KeyDerivation {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val HMAC_ALGORITHM = "HmacSHA256"

    /** 应用锁校验值固定消息（与桌面版完全一致）。 */
    private const val VERIFIER_MESSAGE = "r2-storage-manager-app-lock-v1"

    private val random = SecureRandom()

    /**
     * 由密码 + 盐派生 32 字节密钥。
     *
     * @param password 明文密码
     * @param salt 盐（16 字节）
     * @param iterations PBKDF2 迭代次数
     * @return 32 字节密钥
     */
    fun deriveKey(
        password: String,
        salt: ByteArray,
        iterations: Int = CacheConstants.PBKDF2_ITERATIONS
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, CacheConstants.KEY_LENGTH * 8)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val key = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return key
    }

    /** 生成 16 字节随机盐。 */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(CacheConstants.SALT_LENGTH)
        random.nextBytes(salt)
        return salt
    }

    /** 生成 32 字节随机密钥（未启用应用锁时使用）。 */
    fun generateRandomKey(): ByteArray {
        val key = ByteArray(CacheConstants.KEY_LENGTH)
        random.nextBytes(key)
        return key
    }

    /**
     * 生成密码校验值：`hex(HmacSHA256(key, "r2-storage-manager-app-lock-v1"))`。
     *
     * @param key 派生密钥
     */
    fun buildVerifier(key: ByteArray): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(VERIFIER_MESSAGE.toByteArray(Charsets.UTF_8)).toHex()
    }

    /**
     * 常量时间比较校验值。
     *
     * @param a 校验值 A
     * @param b 校验值 B
     */
    fun verifyVerifier(a: String, b: String): Boolean {
        val left = a.toByteArray(Charsets.UTF_8)
        val right = b.toByteArray(Charsets.UTF_8)
        if (left.size != right.size) {
            return false
        }
        return MessageDigest.isEqual(left, right)
    }

    /** 字节数组 → 小写 hex。 */
    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /** 小写 hex → 字节数组（校验失败抛异常，调用方自行捕获）。 */
    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex 长度必须为偶数" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "非法 hex 字符" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
