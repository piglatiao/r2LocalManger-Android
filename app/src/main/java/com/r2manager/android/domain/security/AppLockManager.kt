package com.r2manager.android.domain.security

import android.util.Base64
import android.util.Log
import com.r2manager.android.core.constants.PrefKeys
import com.r2manager.android.core.constants.TransferConstants
import com.r2manager.android.core.crypto.KeyDerivation
import com.r2manager.android.core.error.AppError
import com.r2manager.android.core.error.ErrorType
import com.r2manager.android.core.error.RecoveryAction
import com.r2manager.android.data.local.prefs.SettingsStore
import com.r2manager.android.domain.model.AppLockStatus

/** 解锁/设置结果。 */
data class UnlockResult(
    val success: Boolean,
    val error: AppError? = null,
    val lockoutRemainingMs: Long = 0
)

/**
 * 应用锁与密钥体系（P3）。
 *
 * 与桌面版 `AppLockService` 语义对齐：
 * - 只存 `salt + verifier(HMAC)`，从不存明文密码；
 * - 未启用锁 → 随机回退密钥（[KeyManager.provisionFallbackKey]）；
 * - 启用锁 → 密码派生密钥，冷启动保持 locked 直至输入正确密码（R-06）；
 * - 连续 5 次失败锁 30 秒（R-03，[PinAttemptTracker]）；
 * - **开关应用锁 = 密钥体系变更 → 先清空全部缓存再换钥**（R-04）。
 *
 * @param verifyCloudflareIdentity 身份校验回调（找回密码用）；默认拒绝，由 `AppContainer` 注入
 *   基于管理面（如列桶）的校验实现。缺省注入时找回密码会失败而不会误放行。
 */
class AppLockManager(
    private val settings: SettingsStore,
    private val keyManager: KeyManager,
    private val cacheEraser: CacheEraser,
    private val biometric: BiometricAuthenticator,
    private val verifyCloudflareIdentity: suspend (accountId: String, apiToken: String) -> Boolean = { _, _ -> false }
) {

    private val attempts = PinAttemptTracker()

    /** 当前应用锁状态。 */
    fun status(): AppLockStatus {
        val raw = settings.raw()
        val enabled = raw.getBoolean(PrefKeys.APP_LOCK_ENABLED, false)
        val salt = raw.getString(PrefKeys.APP_LOCK_SALT, null)
        val verifier = raw.getString(PrefKeys.APP_LOCK_VERIFIER, null)
        val dismissed = raw.getBoolean(PrefKeys.APP_LOCK_DISMISSED, false)
        val hasPassword = !salt.isNullOrEmpty() && !verifier.isNullOrEmpty()

        return AppLockStatus(
            enabled = enabled,
            hasPassword = hasPassword,
            locked = enabled && hasPassword && !keyManager.hasKey(),
            needsSetup = !enabled && !hasPassword && !dismissed
        )
    }

    /**
     * 冷启动准备密钥（§5.4）：
     * - 已启用锁 → 清除会话密钥，保持 `locked=true`，等用户解锁；
     * - 未启用锁 → 准备随机回退密钥，`locked=false`。
     */
    suspend fun bootstrap(): AppLockStatus {
        val current = status()
        return if (current.enabled && current.hasPassword) {
            keyManager.clearKey()
            status()
        } else {
            keyManager.provisionFallbackKey()
            status()
        }
    }

    /** 用密码解锁应用。连续失败计数由 [attempts] 管理。 */
    suspend fun unlockWithPassword(password: String): UnlockResult {
        val lockedFor = attempts.isLockedOut()
        if (lockedFor > 0L) {
            return UnlockResult(false, authError(), lockedFor)
        }
        if (!matches(password)) {
            val lockout = attempts.recordFailure()
            Log.w(TAG, "解锁失败，剩余尝试=${attempts.remainingAttempts()}，锁定剩余=${lockout}ms")
            return UnlockResult(false, authError(), lockout)
        }
        attempts.reset()
        // 重新派生并装载密钥
        val salt = readSalt() ?: return UnlockResult(false, authError())
        val key = KeyDerivation.deriveKey(password, salt)
        keyManager.setKey(key)
        // 密码解锁成功即刷新生物识别绑定（覆盖「重录生物识别致绑定失效」后的自愈）
        keyManager.rememberBiometricKey()
        return UnlockResult(true)
    }

    /**
     * 生物识别解锁（§4 时序图 `LA->>AL: unlockByBiometric → KM.setKey`）。
     *
     * 调用前提：UI 已通过 [BiometricAuthenticator] 完成一次**成功的**生物识别（时效授权仍有效）。
     * 本方法用生物识别绑定的 Keystore 私钥还原会话密钥；失败（未登记 / 未认证 / 失效）时返回失败，
     * 由 UI 回退 PIN 兜底通道，符合「PIN 始终可用」的硬约束。
     *
     * @return 成功（已还原并装载会话密钥）返回 [UnlockResult.success]=true
     */
    suspend fun unlockByBiometric(): UnlockResult {
        return if (keyManager.unlockWithBiometric()) {
            attempts.reset()
            UnlockResult(true)
        } else {
            UnlockResult(false, authError())
        }
    }

    /** 首次设置密码（同时启用锁）。 */
    suspend fun setPassword(password: String) {
        requireValidLength(password)
        val salt = KeyDerivation.generateSalt()
        // 换钥：先清空旧密钥加密的缓存（R-04）
        val key = keyManager.rotateKeyFromPassword(password, salt)
        persistPassword(password, salt, key)
        attempts.reset()
    }

    /** 修改密码（需原密码）。 */
    suspend fun changePassword(current: String, newPassword: String): UnlockResult {
        if (attempts.isLockedOut() > 0L) {
            return UnlockResult(false, authError(), attempts.isLockedOut())
        }
        if (!matches(current)) {
            val lockout = attempts.recordFailure()
            return UnlockResult(false, authError(), lockout)
        }
        if (newPassword.length < TransferConstants.MIN_PASSWORD_LENGTH) {
            return UnlockResult(false, policyError())
        }
        val salt = KeyDerivation.generateSalt()
        val key = keyManager.rotateKeyFromPassword(newPassword, salt)
        persistPassword(newPassword, salt, key)
        attempts.reset()
        return UnlockResult(true)
    }

    /**
     * 开关应用锁（= 密钥体系变更）。
     * - `enable=true`：需要密码。已有密码则先校验；否则把 `currentPassword` 作为新密码。
     * - `enable=false`：需原密码（若已启用），随后清空缓存并切回随机密钥。
     */
    suspend fun setEnabled(currentPassword: String?, enable: Boolean): UnlockResult {
        return if (enable) {
            if (currentPassword.isNullOrEmpty()) return UnlockResult(false, authError())
            if (hasStoredPassword() && !matches(currentPassword)) {
                return UnlockResult(false, authError())
            }
            if (currentPassword.length < TransferConstants.MIN_PASSWORD_LENGTH) {
                return UnlockResult(false, policyError())
            }
            val salt = KeyDerivation.generateSalt()
            val key = keyManager.rotateKeyFromPassword(currentPassword, salt)
            persistPassword(currentPassword, salt, key)
            attempts.reset()
            UnlockResult(true)
        } else {
            if (hasStoredPassword()) {
                if (currentPassword.isNullOrEmpty() || !matches(currentPassword)) {
                    return UnlockResult(false, authError())
                }
            }
            keyManager.rotateAfterDisable()          // 清缓存 + 随机钥
            keyManager.forgetBiometricKey()          // 关闭锁：删除生物识别绑定（密钥体系已变更）
            settings.setAppLockMeta(enabled = false, saltB64 = null, verifier = null, dismissed = true)
            // setAppLockMeta 只会「写入非空值」，关闭锁时必须显式移除盐/校验值（对齐桌面版 disable()）
            settings.raw().edit()
                .remove(PrefKeys.APP_LOCK_SALT)
                .remove(PrefKeys.APP_LOCK_VERIFIER)
                .apply()
            attempts.reset()
            UnlockResult(true)
        }
    }

    /**
     * 用 Cloudflare Account ID + API Token 找回密码：校验身份通过后清空缓存并以新密码换钥。
     */
    suspend fun resetPasswordWithCloudflare(
        accountId: String,
        apiToken: String,
        newPassword: String
    ): UnlockResult {
        if (accountId.isBlank() || apiToken.isBlank()) {
            return UnlockResult(false, authError())
        }
        if (newPassword.length < TransferConstants.MIN_PASSWORD_LENGTH) {
            return UnlockResult(false, policyError())
        }
        val verified = runCatching { verifyCloudflareIdentity(accountId, apiToken) }
            .onFailure { Log.w(TAG, "Cloudflare 身份校验异常", it) }
            .getOrDefault(false)
        if (!verified) {
            return UnlockResult(false, authError())
        }
        val salt = KeyDerivation.generateSalt()
        val key = keyManager.rotateKeyFromPassword(newPassword, salt)
        persistPassword(newPassword, salt, key)
        attempts.reset()
        return UnlockResult(true)
    }

    /** 标记「暂不设置密码」，之后不再提示初始化。 */
    fun dismissSetup() {
        val raw = settings.raw()
        settings.setAppLockMeta(
            enabled = raw.getBoolean(PrefKeys.APP_LOCK_ENABLED, false),
            saltB64 = raw.getString(PrefKeys.APP_LOCK_SALT, null),
            verifier = raw.getString(PrefKeys.APP_LOCK_VERIFIER, null),
            dismissed = true
        )
    }

    // —— 内部 ——

    private suspend fun persistPassword(password: String, salt: ByteArray, key: ByteArray) {
        settings.setAppLockMeta(
            enabled = true,
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            verifier = KeyDerivation.buildVerifier(key),
            dismissed = true
        )
        keyManager.setKey(key)
        // 设置 / 修改 / 找回密码后登记生物识别绑定（冷启动可生物识别解锁，PRD R-03）
        keyManager.rememberBiometricKey()
    }

    private fun hasStoredPassword(): Boolean {
        val raw = settings.raw()
        return !raw.getString(PrefKeys.APP_LOCK_SALT, null).isNullOrEmpty() &&
            !raw.getString(PrefKeys.APP_LOCK_VERIFIER, null).isNullOrEmpty()
    }

    private fun readSalt(): ByteArray? {
        val b64 = settings.raw().getString(PrefKeys.APP_LOCK_SALT, null) ?: return null
        return runCatching { Base64.decode(b64, Base64.NO_WRAP) }.getOrNull()
    }

    private fun matches(password: String): Boolean {
        val salt = readSalt() ?: return false
        val verifier = settings.raw().getString(PrefKeys.APP_LOCK_VERIFIER, null) ?: return false
        val key = KeyDerivation.deriveKey(password, salt)
        val matched = KeyDerivation.verifyVerifier(KeyDerivation.buildVerifier(key), verifier)
        if (!matched) key.fill(0)
        return matched
    }

    private fun requireValidLength(password: String) {
        require(password.length >= TransferConstants.MIN_PASSWORD_LENGTH) {
            "密码至少需要 ${TransferConstants.MIN_PASSWORD_LENGTH} 个字符"
        }
    }

    private fun authError(): AppError = AppError(
        type = ErrorType.AUTH,
        messageResId = 0,
        recovery = RecoveryAction.NONE,
        operation = "app_lock"
    )

    private fun policyError(): AppError = AppError(
        type = ErrorType.UNKNOWN,
        messageResId = 0,
        recovery = RecoveryAction.NONE,
        operation = "password_policy"
    )

    private companion object {
        const val TAG = "AppLockManager"
    }
}
