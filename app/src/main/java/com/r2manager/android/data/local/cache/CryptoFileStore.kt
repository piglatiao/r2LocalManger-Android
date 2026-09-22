package com.r2manager.android.data.local.cache

import android.content.Context
import com.r2manager.android.core.crypto.AesGcmCipher
import java.io.File

/**
 * 缓存文件薄封装：路径解析 + AES-256-GCM 加解密。
 *
 * 语义（对应桌面版 `_canUseCache`）：
 * - **无密钥时不写入**（[write] 返回 false），避免明文落盘；
 * - [read] 对 R2CE 密文用当前密钥解密（密钥不符/损坏 → null）；非 R2CE 格式原样返回。
 */
interface CryptoFileStore {
    /** 读取并解密；文件不存在或无法解密返回 null。 */
    fun read(relPath: String): ByteArray?

    /** 加密写入明文；无密钥或写失败返回 false。 */
    fun write(relPath: String, plain: ByteArray): Boolean

    fun delete(relPath: String)
    fun size(relPath: String): Long
    fun exists(relPath: String): Boolean
}

/**
 * [CryptoFileStore] 默认实现。
 *
 * @param context 应用上下文（缓存根目录 = `files/cache`）
 * @param keyProvider 当前加密密钥（未持有 → null，此时不写缓存，避免明文落盘）。
 *        通常由 AppContainer 传入 `{ keyManager.currentKey() }`。
 */
class CryptoFileStoreImpl(
    context: Context,
    private val keyProvider: () -> ByteArray?
) : CryptoFileStore {

    private val rootDir: File = CachePaths.root(context.applicationContext)

    override fun read(relPath: String): ByteArray? {
        val file = fileOf(relPath)
        if (!file.isFile) {
            return null
        }
        val stored = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (!AesGcmCipher.isEncrypted(stored)) {
            return stored
        }
        val key = keyProvider() ?: return null
        return AesGcmCipher.decrypt(key, stored)
    }

    override fun write(relPath: String, plain: ByteArray): Boolean {
        val key = keyProvider() ?: return false
        val encrypted = AesGcmCipher.encrypt(key, plain)
        val file = fileOf(relPath)
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(encrypted)
            true
        }.getOrDefault(false)
    }

    override fun delete(relPath: String) {
        runCatching { fileOf(relPath).delete() }
    }

    override fun size(relPath: String): Long {
        val file = fileOf(relPath)
        return if (file.isFile) file.length() else 0L
    }

    override fun exists(relPath: String): Boolean = fileOf(relPath).isFile

    private fun fileOf(relPath: String): File = File(rootDir, relPath)
}
