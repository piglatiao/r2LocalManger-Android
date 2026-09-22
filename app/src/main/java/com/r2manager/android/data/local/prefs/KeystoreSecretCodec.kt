package com.r2manager.android.data.local.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.r2manager.android.core.constants.CacheConstants
import com.r2manager.android.core.constants.PrefKeys
import com.r2manager.android.core.crypto.SecretCodec
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 中的 AES-256-GCM 密钥包裹任意机密字符串（凭证 blob / 回退密钥）。
 *
 * 密文格式与全局一致：`R2CE`(4) + version(1) + IV(12) + tag(16) + ciphertext。
 * 外层再做 Base64（`NO_WRAP`），便于存进 `SharedPreferences`。
 *
 * 解包失败（密钥失效 / 数据损坏 / 被篡改）返回 null，绝不抛异常。
 */
class KeystoreSecretCodec(
    private val alias: String = PrefKeys.KEYSTORE_ALIAS
) : SecretCodec {

    override fun encrypt(plain: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val combined = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val tagLength = CacheConstants.TAG_LENGTH
        val cipherTextLength = combined.size - tagLength
        val cipherText = combined.copyOfRange(0, cipherTextLength)
        val tag = combined.copyOfRange(cipherTextLength, combined.size)

        val out = ByteArray(MAGIC.size + 1 + iv.size + tag.size + cipherText.size)
        var offset = 0
        System.arraycopy(MAGIC, 0, out, offset, MAGIC.size); offset += MAGIC.size
        out[offset] = CacheConstants.CRYPTO_FORMAT_VERSION.toByte(); offset += 1
        System.arraycopy(iv, 0, out, offset, iv.size); offset += iv.size
        System.arraycopy(tag, 0, out, offset, tag.size); offset += tag.size
        System.arraycopy(cipherText, 0, out, offset, cipherText.size)

        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String? {
        return try {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val headerSize = MAGIC.size + 1 + CacheConstants.IV_LENGTH + CacheConstants.TAG_LENGTH
            if (data.size < headerSize) {
                return null
            }
            if (!data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                return null
            }
            if (data[MAGIC.size].toInt() != CacheConstants.CRYPTO_FORMAT_VERSION) {
                return null
            }
            var offset = MAGIC.size + 1
            val iv = data.copyOfRange(offset, offset + CacheConstants.IV_LENGTH); offset += CacheConstants.IV_LENGTH
            val tag = data.copyOfRange(offset, offset + CacheConstants.TAG_LENGTH); offset += CacheConstants.TAG_LENGTH
            val cipherText = data.copyOfRange(offset, data.size)

            val combined = ByteArray(cipherText.size + tag.size)
            System.arraycopy(cipherText, 0, combined, 0, cipherText.size)
            System.arraycopy(tag, 0, combined, cipherText.size, tag.size)

            val key = existingKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CacheConstants.TAG_LENGTH * 8, iv))
            String(cipher.doFinal(combined), Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(CacheConstants.KEY_LENGTH * 8)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val MAGIC: ByteArray = CacheConstants.MAGIC
    }
}
