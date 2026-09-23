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
import com.r2manager.android.databinding.FragmentSecuritySettingsBinding
import kotlinx.coroutines.launch

/**
 * 系统安全页（P4-B）。
 *
 * 安卓端不提供应用内密码、应用内生物识别或自动上锁，应用保护由系统应用锁负责。
 */
class SecuritySettingsFragment : Fragment() {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: SecuritySettingsViewModel by viewModels {
        SecuritySettingsViewModel.factory(requireContext().appContainer())
    }

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
        binding.btnClearCred.setOnClickListener { confirmClearCredentials() }
    }

    /** 确认清除本地保存的 R2 凭证。 */
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

    /** 返回设置首页。 */
    private fun back() {
        (parentFragment as? SettingsFragment)?.closeSub()
    }

    /** 显示操作结果。 */
    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
