package com.r2manager.android.core.crypto

/**
 * 机密字符串包裹编解码器。
 *
 * 由 `data.local.prefs.KeystoreSecretCodec`（P2）用 Android Keystore 实现：
 * 对凭证 blob / 回退密钥等机密字符串做 AES-256-GCM 包裹后 base64 落盘。
 * 定义在 core 以保证 domain / 上层只依赖抽象。
 */
interface SecretCodec {
    /**
     * 加密明文机密的字符串。
     *
     * @param plain 明文
     * @return 可落盘的编码串（如 base64）
     */
    fun encrypt(plain: String): String

    /**
     * 解密编码串。
     *
     * @param encoded 已编码密文
     * @return 明文；无法解密返回 null
     */
    fun decrypt(encoded: String): String?
}
