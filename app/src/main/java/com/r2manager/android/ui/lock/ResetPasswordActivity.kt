package com.r2manager.android.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.TransferConstants
import com.r2manager.android.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.launch

/**
 * 找回密码（P4-B）。
 *
 * 入口来自 [LockActivity] 的「忘记密码？」。填写 Cloudflare Account ID + API Token 验证身份，
 * 校验通过后由 P3 [com.r2manager.android.domain.security.AppLockManager.resetPasswordWithCloudflare]
 * 重设密码（同时清空并重建本地缓存密钥）；成功后仅 `setResult(RESULT_OK)` + `finish()`，
 * 由调用方（[LockActivity]）决定后续——本页**不负责导航**。
 */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding

    private val viewModel: ResetPasswordViewModel by viewModels {
        ResetPasswordViewModel.factory(applicationContext.appContainer())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvBack.setOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        clearError()
        val accountId = binding.etAccountId.text?.toString()?.trim().orEmpty()
        val apiToken = binding.etApiToken.text?.toString()?.trim().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()
        val confirm = binding.etConfirmPassword.text?.toString().orEmpty()

        if (accountId.isEmpty() || apiToken.isEmpty()) {
            showError(getString(R.string.lock_reset_error_empty))
            return
        }
        if (newPassword.length < TransferConstants.MIN_PASSWORD_LENGTH) {
            showError(getString(R.string.lock_setup_error_short))
            return
        }
        if (newPassword != confirm) {
            showError(getString(R.string.lock_setup_error_mismatch))
            return
        }

        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            val result = viewModel.reset(accountId, apiToken, newPassword)
            binding.btnSubmit.isEnabled = true
            if (result.success) {
                Toast.makeText(this@ResetPasswordActivity, R.string.lock_reset_success, Toast.LENGTH_SHORT).show()
                // 只返回结果，不负责导航：重设后密钥已在内存，交调用方（LockActivity）决定后续
                setResult(RESULT_OK)
                finish()
            } else {
                showError(getString(R.string.lock_reset_error_verify))
            }
        }
    }

    private fun showError(message: CharSequence) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun clearError() {
        binding.tvError.text = ""
        binding.tvError.visibility = View.GONE
    }

    companion object {
        /** 启动找回密码页。 */
        fun start(context: Context) {
            context.startActivity(Intent(context, ResetPasswordActivity::class.java))
        }
    }
}
