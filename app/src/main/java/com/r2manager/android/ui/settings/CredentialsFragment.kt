package com.r2manager.android.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.NetworkConstants
import com.r2manager.android.databinding.FragmentCredentialsBinding
import com.r2manager.android.domain.model.Credentials
import com.r2manager.android.ui.lock.LockSetupActivity
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

    /**
     * 首次设置密码引导结果（原型第 5 屏 `lock-setup`）。
     * `RESULT_OK` → 提示已启用并收起未启用横幅；`RESULT_CANCELED`（暂不设置 / 返回）→ 静默，不重复打扰。
     */
    private val lockSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            _binding?.bannerLock?.visibility = View.GONE
            toast(R.string.security_lock_enabled)
        }
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
            // 「保存前是否已有凭证」作为首次判据：凭证一旦存在即永存，故该条件天然只在首次命中，
            // 无需新增持久化标记（避免跨包改 AppSettings / 新增设置项）。
            val hadCredentialsBefore = viewModel.load() != null
            val result = viewModel.save(credentials)
            toast(if (result.saved) R.string.credentials_saved else R.string.credentials_test_fail)
            if (result.saved && !hadCredentialsBefore && !viewModel.isAppLockEnabled()) {
                promptEnableLock()
            }
        }
    }

    /**
     * 首次配置完成后引导设置应用锁（原型第 5 屏 `lock-setup`）。
     *
     * 文案明示「启用会清空并重建本地缓存」（与安全设置页内联入口同一 [R.string.security_lock_cache_wipe] 口径）；
     * 「暂不设置」静默返回、之后不再重复打扰（凭证已存在，判据不再命中）。
     */
    private fun promptEnableLock() {
        if (!isAdded || _binding == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_setup_title)
            .setMessage(
                getString(R.string.security_lock_cache_wipe) + "\n\n" +
                    getString(R.string.lock_setup_intro)
            )
            .setNegativeButton(R.string.lock_setup_skip, null)
            .setPositiveButton(R.string.lock_setup_submit) { _, _ ->
                lockSetupLauncher.launch(Intent(requireContext(), LockSetupActivity::class.java))
            }
            .show()
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
            toast(if (result.ok) R.string.credentials_test_ok else R.string.credentials_test_fail)
        }
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
