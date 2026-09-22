package com.r2manager.android.domain.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.r2manager.android.core.error.AppError
import com.r2manager.android.core.error.ErrorType
import com.r2manager.android.core.error.RecoveryAction

/**
 * 生物识别封装（P3，PRD §5.4）。
 *
 * 平台差异（硬约束）：
 * - **API 30+**：允许 `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`（人脸/指纹 + 设备 PIN/密码）。
 * - **API 26–29**：降级为**仅指纹**（`BIOMETRIC_WEAK`）。
 * - **PIN 兜底通道始终可用**，不受平台版本或生物识别可用性影响（由 UI 保证）。
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    private val allowedAuthenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

    private val usesDeviceCredential: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 当前设备是否具备可用的生物识别（或 30+ 的设备凭证）能力。 */
    fun canAuthenticate(): Boolean = try {
        BiometricManager.from(activity).canAuthenticate(allowedAuthenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } catch (t: Throwable) {
        false
    }

    /**
     * 唤起生物识别。
     * @param onResult 成功传 `(true, null)`；失败/取消传 `(false, error?)`（用户主动取消时 error 为 null）。
     */
    fun authenticate(title: String, subtitle: String, onResult: (Boolean, AppError?) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true, null)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val userCancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                val error = if (userCancelled) {
                    null
                } else {
                    AppError(
                        type = ErrorType.PERMISSION,
                        messageResId = 0,
                        recovery = RecoveryAction.NONE,
                        operation = "biometric",
                        s3Code = errorCode.toString()
                    )
                }
                onResult(false, error)
            }

            override fun onAuthenticationFailed() {
                // 单次未识别：不结束，等待重试（最终由 ERROR_* 回调收尾）
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)

        if (!usesDeviceCredential) {
            // 未启用设备凭证时，必须提供取消按钮文案
            builder.setNegativeButtonText("取消")
        }

        prompt.authenticate(builder.build())
    }
}
