package com.r2manager.android.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.NetworkConstants
import com.r2manager.android.core.error.AppError
import com.r2manager.android.core.error.ErrorMapper
import com.r2manager.android.databinding.FragmentCredentialsBinding
import com.r2manager.android.domain.model.Credentials
import kotlinx.coroutines.launch

/**
 * R2 凭证配置（P4-B）。
 *
 * 四字段：Account ID / API Token（管理面）/ Access Key / Secret Key（数据面）+ jurisdiction 下拉；
 * Endpoint 与 Region 只读（Endpoint 由 Account ID 自动派生）。未启用应用锁时顶部显示安全提示条。
 */
class CredentialsFragment : Fragment() {

    private var _binding: FragmentCredentialsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: CredentialsViewModel by viewModels {
        CredentialsViewModel.factory(requireContext().appContainer())
    }

    private val jurisdictionOptions = listOf(
        NetworkConstants.DEFAULT_JURISDICTION, "eu", "fedramp"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCredentialsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindDropdown(binding.acJurisdiction, jurisdictionOptions)
        binding.acJurisdiction.setText(NetworkConstants.DEFAULT_JURISDICTION, false)
        binding.etRegion.setText(viewModel.region())

        binding.bannerLock.visibility =
            if (viewModel.isAppLockEnabled()) View.GONE else View.VISIBLE

        binding.etAccountId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.etEndpoint.setText(viewModel.endpointFor(s?.toString().orEmpty()))
            }
        })

        binding.btnBack.setOnClickListener { back() }
        binding.btnTest.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { save() }
        binding.tvClear.setOnClickListener { confirmClear() }

        loadCredentials()
    }

    private fun loadCredentials() {
        viewLifecycleOwner.lifecycleScope.launch {
            val credentials = viewModel.load()
            if (credentials == null) {
                binding.etEndpoint.setText(viewModel.endpointFor(""))
                return@launch
            }
            binding.etAccountId.setText(credentials.accountId)
            binding.etApiToken.setText(credentials.apiToken)
            binding.etAccessKey.setText(credentials.accessKeyId)
            binding.etSecretKey.setText(credentials.secretAccessKey)
            binding.acJurisdiction.setText(credentials.jurisdiction, false)
            binding.etEndpoint.setText(viewModel.endpointFor(credentials.accountId))
        }
    }

    private fun collect(): Credentials = Credentials(
        accountId = binding.etAccountId.text?.toString()?.trim().orEmpty(),
        accessKeyId = binding.etAccessKey.text?.toString()?.trim().orEmpty(),
        secretAccessKey = binding.etSecretKey.text?.toString()?.trim().orEmpty(),
        apiToken = binding.etApiToken.text?.toString()?.trim().orEmpty(),
        jurisdiction = binding.acJurisdiction.text?.toString()?.trim()
            .orEmpty()
            .ifBlank { NetworkConstants.DEFAULT_JURISDICTION }
    )

    private fun save() {
        val credentials = collect()
        if (!credentials.isComplete) {
            toast(R.string.credentials_required)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.save(credentials)
            if (!result.saved) {
                showConnectionFailure(result.error)
            } else if (result.managementApiOk == true) {
                toast(R.string.credentials_saved)
            } else {
                toast(R.string.credentials_saved_no_bucket)
            }
        }
    }

    private fun testConnection() {
        val credentials = collect()
        if (!credentials.isComplete) {
            toast(R.string.credentials_required)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnTest.isEnabled = false
            val result = viewModel.test(credentials)
            binding.btnTest.isEnabled = true
            if (result.ok) {
                toast(R.string.credentials_test_ok)
            } else {
                showConnectionFailure(result.error)
            }
        }
    }

    /** 展示可定位的连接错误，避免把完整异常消息或签名内容展示给用户。 */
    private fun showConnectionFailure(error: AppError?) {
        val titleRes = error?.messageResId?.takeIf { it != 0 } ?: R.string.credentials_test_fail
        val message = error?.let { ErrorMapper.detailFor(requireContext(), it) }
            ?: getString(titleRes)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.credentials_clear_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> clearCredentials() }
            .show()
    }

    private fun clearCredentials() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.clear()
            binding.etAccountId.setText("")
            binding.etApiToken.setText("")
            binding.etAccessKey.setText("")
            binding.etSecretKey.setText("")
            binding.acJurisdiction.setText(NetworkConstants.DEFAULT_JURISDICTION, false)
            binding.etEndpoint.setText(viewModel.endpointFor(""))
            toast(R.string.credentials_cleared)
        }
    }

    private fun bindDropdown(view: MaterialAutoCompleteTextView, options: List<String>) {
        view.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options))
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
