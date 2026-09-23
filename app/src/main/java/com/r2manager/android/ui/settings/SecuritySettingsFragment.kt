package com.r2manager.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.TransferConstants
import com.r2manager.android.databinding.FragmentSecuritySettingsBinding
import com.r2manager.android.domain.security.BiometricAuthenticator
import kotlinx.coroutines.launch

/**
 * 安全与密码（P4-B）。
 *
 * 启用 / 关闭应用锁（开关＝密钥体系变更，P3 会先清空缓存再换钥）、优先生物识别、
 * 后台自动上锁（立即 / 1 / 5 / 15 分钟 / 从不）、修改密码、清除凭证。
 */
class SecuritySettingsFragment : Fragment() {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: SecuritySettingsViewModel by viewModels {
        SecuritySettingsViewModel.factory(requireContext().appContainer())
    }

    /** 程序化改写控件时抑制监听回调。 */
    private var bindingFlags = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecuritySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { back() }
        setupLockSwitch()
        setupBiometricSwitch()
        setupAutoLockRow()
        binding.btnChange.setOnClickListener { changePassword() }
        binding.btnClearCred.setOnClickListener { confirmClearCredentials() }

        applyState()
    }

    // ==================== 状态回填 ====================

    private fun applyState() {
        bindingFlags = true
        binding.switchLock.isChecked = viewModel.isLockEnabled()
        binding.switchBiometric.isChecked = viewModel.isBiometricEnabled()

        val biometricAvailable = BiometricAuthenticator(requireActivity()).canAuthenticate(withCryptoObject = true)
        binding.switchBiometric.isEnabled = viewModel.isLockEnabled() && biometricAvailable
        bindingFlags = false

        updatePasswordFields()
        updateAutoLockSub()
    }

    /** 无旧密码时隐藏当前密码输入，并将按钮切换为直接设置密码。 */
    private fun updatePasswordFields() {
        val hasPassword = viewModel.hasPassword()
        binding.layoutCurrent.visibility = if (hasPassword) View.VISIBLE else View.GONE
        binding.btnChange.setText(
            if (hasPassword) R.string.security_change else R.string.security_set_password
        )
    }

    private fun updateAutoLockSub() {
        val minutes = viewModel.autoLockMinutes()
        binding.tvAutolockSub.text = autoLockLabel(minutes)
    }

    private fun autoLockLabel(minutes: Int): String = when {
        minutes < 0 -> getString(R.string.settings_autolock_sub_never)
        minutes == 0 -> getString(R.string.settings_autolock_sub_immediate)
        else -> getString(R.string.settings_autolock_sub_minutes, minutes)
    }

    // ==================== 应用锁开关 ====================

    private fun setupLockSwitch() {
        binding.switchLock.setOnCheckedChangeListener { _, checked ->
            if (bindingFlags) return@setOnCheckedChangeListener
            if (checked) enableLock() else disableLock()
        }
    }

    private fun enableLock() {
        val password = binding.etNew.text?.toString().orEmpty()
        val confirm = binding.etConfirm.text?.toString().orEmpty()
        if (password.length < TransferConstants.MIN_PASSWORD_LENGTH) {
            rejectLockSwitch(getString(R.string.security_error_short))
            return
        }
        if (password != confirm) {
            rejectLockSwitch(getString(R.string.security_error_mismatch))
            return
        }
        // 启用锁＝密钥体系变更 → 会清空并重建本地缓存，先明示后果再执行（与凭证页首次引导口径一致）
        confirmCacheWipe {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.setEnabled(password, true)
                if (result.success) {
                    clearPasswordFields()
                    syncLockUi()
                    toast(R.string.security_lock_enabled)
                } else {
                    rejectLockSwitch(getString(R.string.security_error_short))
                }
            }
        }
    }

    private fun disableLock() {
        val current = binding.etCurrent.text?.toString().orEmpty()
        if (current.isEmpty()) {
            rejectLockSwitch(getString(R.string.security_error_need_current))
            return
        }
        // 关闭锁同为密钥体系变更（清空缓存 + 回退随机钥），先明示后果再执行
        confirmCacheWipe {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.setEnabled(current, false)
                if (result.success) {
                    clearPasswordFields()
                    syncLockUi()
                    toast(R.string.security_lock_disabled)
                } else {
                    rejectLockSwitch(getString(R.string.security_error_current))
                }
            }
        }
    }

    /** 校验失败：回滚开关并提示。 */
    private fun rejectLockSwitch(message: String) {
        rollbackLockSwitch()
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /** 启用 / 关闭应用锁前明示「会清空并重建本地缓存」；点「取消」回滚开关。 */
    private fun confirmCacheWipe(onConfirmed: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.security_lock_cache_wipe)
            .setNegativeButton(R.string.action_cancel) { _, _ -> rollbackLockSwitch() }
            .setPositiveButton(R.string.action_confirm) { _, _ -> onConfirmed() }
            .setCancelable(false)
            .show()
    }

    /** 仅回滚开关到当前真实状态（无提示）。 */
    private fun rollbackLockSwitch() {
        bindingFlags = true
        binding.switchLock.isChecked = viewModel.isLockEnabled()
        bindingFlags = false
    }

    private fun syncLockUi() {
        bindingFlags = true
        binding.switchLock.isChecked = viewModel.isLockEnabled()
        val biometricAvailable = BiometricAuthenticator(requireActivity()).canAuthenticate(withCryptoObject = true)
        binding.switchBiometric.isEnabled = viewModel.isLockEnabled() && biometricAvailable
        bindingFlags = false
        updatePasswordFields()
    }

    // ==================== 生物识别 ====================

    private fun setupBiometricSwitch() {
        binding.switchBiometric.setOnCheckedChangeListener { _, checked ->
            if (bindingFlags) return@setOnCheckedChangeListener
            viewModel.setBiometricEnabled(checked)
        }
    }

    // ==================== 自动上锁 ====================

    private fun setupAutoLockRow() {
        binding.rowAutolock.setOnClickListener { showAutoLockDialog() }
    }

    private fun showAutoLockDialog() {
        val values = intArrayOf(0, 1, 5, 15, -1)
        val labels = arrayOf(
            getString(R.string.autolock_option_immediate),
            getString(R.string.autolock_option_1),
            getString(R.string.autolock_option_5),
            getString(R.string.autolock_option_15),
            getString(R.string.autolock_option_never)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.security_autolock_dialog)
            .setItems(labels) { _, which ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.setAutoLockMinutes(values[which])
                    updateAutoLockSub()
                }
            }
            .show()
    }

    // ==================== 修改密码 ====================

    private fun changePassword() {
        val current = binding.etCurrent.text?.toString().orEmpty()
        val newPassword = binding.etNew.text?.toString().orEmpty()
        val confirm = binding.etConfirm.text?.toString().orEmpty()

        if (viewModel.hasPassword() && current.isEmpty()) {
            toast(R.string.security_error_need_current)
            return
        }
        if (newPassword.length < TransferConstants.MIN_PASSWORD_LENGTH) {
            toast(R.string.security_error_short)
            return
        }
        if (newPassword != confirm) {
            toast(R.string.security_error_mismatch)
            return
        }

        val submitChange: () -> Unit = {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.changePassword(current.takeIf { viewModel.hasPassword() }, newPassword)
                if (result.success) {
                    clearPasswordFields()
                    syncLockUi()
                    toast(R.string.security_password_changed)
                } else {
                    toast(R.string.security_error_current)
                }
            }
        }
        if (viewModel.hasPassword()) {
            submitChange()
        } else {
            confirmCacheWipe(submitChange)
        }
    }

    private fun clearPasswordFields() {
        binding.etCurrent.setText("")
        binding.etNew.setText("")
        binding.etConfirm.setText("")
    }

    // ==================== 清除凭证 ====================

    private fun confirmClearCredentials() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.security_danger_desc)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.security_clear_credentials) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.clearCredentials()
                    toast(R.string.credentials_cleared)
                }
            }
            .show()
    }

    private fun back() {
        (parentFragment as? SettingsFragment)?.closeSub()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
