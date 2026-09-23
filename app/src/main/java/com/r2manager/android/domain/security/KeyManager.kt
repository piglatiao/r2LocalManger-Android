package com.r2manager.android.domain.security

import android.util.Base64
import android.util.Log
import com.r2manager.android.core.constants.CacheConstants
import com.r2manager.android.core.crypto.KeyDerivation
import com.r2manager.android.core.crypto.SecretCodec
import com.r2manager.android.data.local.prefs.SettingsStore
import javax.crypto.Cipher

/**
 * 缓存密钥管理（P3）。
 *
 * 密钥来源两种，与桌面版 `AppLockService`/`LocalCryptoService` 语义对齐：
 * - **已启用应用锁**：密钥由密码经 PBKDF2 派生（Android 端替代桌面版 scrypt），解锁时才有密钥；
 * - **未启用应用锁**：使用本地随机密钥，经 [SecretCodec]（Keystore）包裹后落盘（[SettingsStore.getFallbackKeyWrapped]）。
 *
 * 关键约束：`rotateKeyFromPassword` / `rotateAfterDisable` **必须先清空全部缓存再换钥**（R-04）。
 */
class KeyManager(
    private val settings: SettingsStore,
    private val codec: SecretCodec,
    private val cacheEraser: CacheEraser
) {

    @Volatile
    private var key: ByteArray? = null

    /** 生物识别绑定密钥的 Keystore 封装（缺陷修复 #BIOMETRIC-KEY；懒建，避免无生物识别时也占用）。 */
    private val biometricKeyStore by lazy { BiometricKeyStore() }

    /** 当前会话是否持有可用密钥。 */
    fun hasKey(): Boolean = key != null

    /** 当前密钥（只读引用，调用方不得修改内容）。 */
    fun currentKey(): ByteArray? = key

    /** 设置会话密钥。 */
    fun setKey(key: ByteArray) {
        // 覆盖前先抹掉旧密钥
        this.key?.fill(0)
        this.key = key
    }

    /** 清除会话密钥（锁屏 / 退出时调用），并尽力抹除内存副本。 */
    fun clearKey() {
        key?.fill(0)
        key = null
    }

    /**
     * 未启用应用锁时的密钥准备：读取（或首次生成）随机密钥并装载。
     * 落盘形式为「Keystore 包裹的 base64(key)」，与 §8.9-8 一致。
     */
    fun provisionFallbackKey() {
        val wrapped = settings.getFallbackKeyWrapped()
        if (!wrapped.isNullOrEmpty()) {
            val decoded = codec.decrypt(wrapped)
            if (!decoded.isNullOrEmpty()) {
                val bytes = runCatching { Base64.decode(decoded, Base64.NO_WRAP) }.getOrNull()
                if (bytes != null && bytes.size == CacheConstants.KEY_LENGTH) {
                    setKey(bytes)
                    return
                }
            }
        }

        val fresh = KeyDerivation.generateRandomKey()
        val encoded = Base64.encodeToString(fresh, Base64.NO_WRAP)
        settings.setFallbackKeyWrapped(codec.encrypt(encoded))
        setKey(fresh)
    }

    /**
     * 用密码派生新密钥：**先清空全部缓存，再换钥**（R-04）。
     * @return 新密钥
     */
    suspend fun rotateKeyFromPassword(password: String, salt: ByteArray): ByteArray {
        val cleared = cacheEraser.clearAll()
        Log.i(TAG, "换钥前已清空缓存：${cleared.first} 项 / ${cleared.second} 字节")
        val derived = KeyDerivation.deriveKey(password, salt)
        setKey(derived)
        return derived
    }

    /**
     * 关闭应用锁后的换钥：**清空缓存 → 切回本地随机密钥**，保证缓存目录仍是密文。
     * @return 新的随机密钥
     */
    suspend fun rotateAfterDisable(): ByteArray {
        val cleared = cacheEraser.clearAll()
        Log.i(TAG, "关闭锁换钥前已清空缓存：${cleared.first} 项 / ${cleared.second} 字节")
        clearKey()
        provisionFallbackKey()
        return key ?: KeyDerivation.generateRandomKey().also { setKey(it) }
    }

    // —— 生物识别绑定密钥（缺陷修复 #BIOMETRIC-KEY）——

    /** 是否已登记生物识别绑定密钥（即：冷启动可通过生物识别还原会话密钥）。 */
    fun hasBiometricKey(): Boolean =
        !settings.raw().getString(BIOMETRIC_KEY_BLOB, null).isNullOrEmpty()

    /** 为生物识别 Prompt 准备需要授权的解密 Cipher。 */
    fun prepareBiometricCipher(): Cipher? = biometricKeyStore.createDecryptCipher()

    /**
     * 用生物识别绑定的 Keystore **公钥**包裹当前会话密钥并落盘。
     * 应在「设置 / 修改 / 找回密码」成功后调用（此时 [key] 已是新的密码派生密钥）。
     *
     * @return 是否登记成功（当前无会话密钥或 Keystore 不可用时返回 false）
     */
    fun rememberBiometricKey(): Boolean {
        val current = key ?: return false
        val wrapped = biometricKeyStore.encrypt(current) ?: return false
        settings.raw().edit().putString(BIOMETRIC_KEY_BLOB, wrapped).apply()
        return true
    }

    /** 清除生物识别绑定（关闭应用锁 / 密钥失效时调用）：删密文 + 删 Keystore 密钥对。 */
    fun forgetBiometricKey() {
        settings.raw().edit().remove(BIOMETRIC_KEY_BLOB).apply()
        biometricKeyStore.deleteKey()
    }

    /**
     * 冷启动生物识别解锁：在**已通过一次生物识别**（时效授权有效）的前提下，用私钥解密还原
     * 会话密钥并装载。
     *
     * 与密码解锁不同，这里还原的是**同一把**密码派生密钥，故**无需清空缓存**（缓存仍可解密）。
     *
     * @return 还原成功返回 true；密文缺失 / 未认证 / 密钥失效返回 false（调用方应回退密码通道）
     */
    fun unlockWithBiometric(cipher: Cipher): Boolean {
        val wrapped = settings.raw().getString(BIOMETRIC_KEY_BLOB, null)
        if (wrapped.isNullOrEmpty()) return false
        val decoded = biometricKeyStore.decrypt(wrapped, cipher)
        if (decoded == null || decoded.size != CacheConstants.KEY_LENGTH) {
            decoded?.fill(0)
            // 无法还原（密钥失效 / 数据损坏）→ 清除陈旧绑定，待密码解锁后重建
            forgetBiometricKey()
            return false
        }
        setKey(decoded)
        return true
    }

    private companion object {
        const val TAG = "KeyManager"

        /** 生物识别绑定密文（Keystore 公钥加密的会话密钥，base64）落盘键。 */
        const val BIOMETRIC_KEY_BLOB = "appLockBiometricKeyBlob"
    }
}
