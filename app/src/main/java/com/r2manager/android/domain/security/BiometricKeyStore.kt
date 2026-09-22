package com.r2manager.android.domain.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

/**
 * 生物识别绑定密钥的 Keystore 封装（P3 · 缺陷修复 #BIOMETRIC-KEY）。
 *
 * ## 背景 / 缺陷
 * 会话密钥由密码经 PBKDF2 派生并仅存于 [KeyManager] 内存；冷启动（[AppLockManager.bootstrap]）
 * 会 `clearKey()` 保持 locked。而 `BiometricAuthenticator` 仅返回「是否通过」布尔值、且**不携带
 * 任何密钥材料**，故冷启动下生物识别成功后 UI 拿不到会话密钥（只能提示改用密码），
 * 违反 PRD R-03「冷启动自动唤起 BiometricPrompt，解锁成功后进入文件列表」。本类即为其修复：
 * 让生物识别成功后**能够**还原会话密钥。
 *
 * ## 方案（非对称 + 时效授权，跨 API 26+ 一致，无需 CryptoObject）
 * - 生成 **RSA 密钥对**（别名 [ALIAS]），私钥 `setUserAuthenticationRequired(true)` 且有效期
 *   [VALIDITY_SECONDS]：
 *   **加密走公钥（无需认证）**，**解密走私钥（需先通过一次生物识别）**；因此登记（设置密码）时
 *   无需打扰用户，仅冷启动解锁时需要生物识别。
 * - 会话密钥（AES-256，[com.r2manager.android.core.constants.CacheConstants.KEY_LENGTH] 字节）在
 *   设置 / 修改 / 找回密码时用公钥加密后 base64 落盘；冷启动解锁时由「已完成生物识别的私钥」解密还原。
 * - 生物识别重录（enrollment 变更）会使私钥永久失效 → [KeyPermanentlyInvalidatedException]，
 *   此时 [decrypt] 返回 null，由调用方清空绑定并回退密码通道。
 *
 * 本类只做 Keystore 加解密；密文的持久化由 [KeyManager] 负责（域层不直接持有偏好存储）。
 */
class BiometricKeyStore {

    /**
     * 用公钥加密任意小数据（**无需认证**）。仅供登记会话密钥使用。
     *
     * @param plain 明文（会话密钥字节）
     * @return base64 编码的密文；失败返回 null
     */
    fun encrypt(plain: ByteArray): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey())
        Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP)
    }.onFailure { Log.w(TAG, "生物识别密钥加密失败", it) }.getOrNull()

    /**
     * 用私钥解密（**需已通过一次生物识别**）。
     *
     * @param encoded [encrypt] 产出的 base64 密文
     * @return 明文；若未认证 / 密钥失效 / 数据损坏则返回 null
     */
    fun decrypt(encoded: String): ByteArray? = runCatching {
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        val privateKey = existingPrivateKey() ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        cipher.doFinal(encrypted)
    }.onFailure { t ->
        val invalidated = t is KeyPermanentlyInvalidatedException ||
            t.cause is KeyPermanentlyInvalidatedException
        if (invalidated) {
            Log.w(TAG, "生物识别密钥已失效（可能重录了指纹/人脸），清理密钥对", t)
            deleteKey()
        } else {
            // 未认证（UserNotAuthenticatedException）等属预期分支：返回 null 交由调用方回退密码
            Log.w(TAG, "生物识别密钥解密失败，回退密码通道", t)
        }
    }.getOrNull()

    /** 删除 Keystore 密钥对（关闭应用锁 / 密钥失效时调用）。 */
    fun deleteKey() {
        runCatching {
            val store = keyStore()
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        }.onFailure { Log.w(TAG, "删除生物识别密钥失败", it) }
    }

    // —— 内部 ——

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun publicKey(): PublicKey {
        ensureKeyPair()
        return keyStore().getCertificate(ALIAS)!!.publicKey
    }

    /** 已存在才返回私钥；不存在返回 null（**不**顺手生成新对，避免「新对解旧密文」的错配）。 */
    private fun existingPrivateKey(): PrivateKey? {
        val store = keyStore()
        if (!store.containsAlias(ALIAS)) return null
        return runCatching { store.getKey(ALIAS, null) as? PrivateKey }.getOrNull()
    }

    private fun ensureKeyPair() {
        val store = keyStore()
        if (store.containsAlias(ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setDigests(
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512
            )
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(VALIDITY_SECONDS)
                }
            }
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    private companion object {
        const val TAG = "BiometricKeyStore"
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "r2manager_biometric_v1"
        const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        const val KEY_SIZE_BITS = 2048

        /** 认证有效期：生物识别成功后 30s 内可解密（足够覆盖「成功回调 → 还原密钥」的窗口）。 */
        const val VALIDITY_SECONDS = 30
    }
}
