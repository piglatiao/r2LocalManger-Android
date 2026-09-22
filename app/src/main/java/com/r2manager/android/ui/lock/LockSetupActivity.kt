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
import com.r2manager.android.databinding.ActivityLockSetupBinding
import com.r2manager.android.domain.security.BiometricAuthenticator
import kotlinx.coroutines.launch

/**
 * 首次设置应用密码（P4-B）。
 *
 * 冷启动引导（凭证已配置、尚未设置密码）时由启动页（P4-A）拉起；设置页「启用应用锁」亦可拉起。
 * 设置成功后应用锁即启用（密钥体系切换，P3 [com.r2manager.android.domain.security.AppLockManager.setPassword]
 * 会先清空缓存再换钥）。
 *
 * 导航约定（与 [LockActivity] 统一）：本页**不负责导航**，只返回结果——
 * 设置成功 `RESULT_OK`、取消/跳过 `RESULT_CANCELED`、完成即 `finish()`；由调用方决定后续
 * （启动页续跑分流 / 设置页刷新开关与缓存统计）。**禁止** `CLEAR_TASK` 直跳 `MainActivity`。
 */
class LockSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockSetupBinding

    private val viewModel: LockViewModel by viewModels {
        LockViewModel.factory(applicationContext.appContainer())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设备不支持生物识别时隐藏开关
        val biometricAvailable = BiometricAuthenticator(this).canAuthenticate()
        if (!biometricAvailable) {
            binding.switchBiometric.isChecked = false
            binding.switchBiometric.visibility = View.GONE
        }

        binding.btnSubmit.setOnClickListener { submit() }
        binding.tvSkip.setOnClickListener { skip() }
    }

    private fun submit() {
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirm = binding.etConfirm.text?.toString().orEmpty()

        if (password.length < TransferConstants.MIN_PASSWORD_LENGTH) {
            binding.etPassword.error = getString(R.string.lock_setup_error_short)
            return
        }
        if (password != confirm) {
            binding.etConfirm.error = getString(R.string.lock_setup_error_mismatch)
            return
        }

        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            viewModel.setPassword(password)
            viewModel.setBiometricEnabled(binding.switchBiometric.isChecked)
            toast(R.string.lock_setup_done)
            // 只返回结果，不负责导航：由调用方（启动页 / 设置页）决定后续
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun skip() {
        viewModel.dismissSetup()
        toast(R.string.lock_setup_skipped)
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** 启动首次设置页。 */
        fun start(context: Context) {
            context.startActivity(
                Intent(context, LockSetupActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
