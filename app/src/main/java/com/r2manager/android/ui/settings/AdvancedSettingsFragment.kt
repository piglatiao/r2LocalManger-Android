package com.r2manager.android.ui.settings

import android.os.Bundle
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
import com.r2manager.android.data.remote.cf.CustomDomainInput
import com.r2manager.android.databinding.FragmentAdvancedSettingsBinding
import com.r2manager.android.databinding.ItemSettingBinding
import com.r2manager.android.domain.model.CustomDomain
import com.r2manager.android.domain.model.Zone
import kotlinx.coroutines.launch

/**
 * 高级设置（P4-B）· 自定义域名与 r2.dev。
 *
 * 展示当前公开地址与优先级；列出桶的自定义域名（可解绑）；从账号可用 Zone 中绑定新域名
 * （可设最低 TLS）；启停 r2.dev 公开访问。
 */
class AdvancedSettingsFragment : Fragment() {

    private var _binding: FragmentAdvancedSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AdvancedSettingsViewModel by viewModels {
        AdvancedSettingsViewModel.factory(requireContext().appContainer())
    }

    private val minTlsOptions = listOf("1.0", "1.1", "1.2", "1.3")
    private var zones: List<Zone> = emptyList()
    private var bindingFlags = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdvancedSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvPublic.text = viewModel.publicUrl().ifBlank { getString(R.string.settings_advanced_none) }
        bindDropdown(binding.acMinTls, minTlsOptions, "1.2")

        binding.btnBack.setOnClickListener { back() }
        binding.btnBind.setOnClickListener { bindDomain() }
        binding.switchR2Dev.setOnCheckedChangeListener { _, checked ->
            if (bindingFlags) return@setOnCheckedChangeListener
            setManagedDomain(checked)
        }

        loadZones()
        loadDomains()
        loadManagedDomain()
    }

    // ==================== 加载 ====================

    private fun loadZones() {
        viewLifecycleOwner.lifecycleScope.launch {
            zones = runCatching { viewModel.listZones() }.getOrDefault(emptyList())
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                zones.map { it.name }
            )
            binding.acZone.setAdapter(adapter)
            if (zones.isNotEmpty()) {
                binding.acZone.setText(zones.first().name, false)
            }
        }
    }

    private fun loadDomains() {
        val bucket = viewModel.currentBucket()
        if (bucket.isBlank()) {
            renderDomains(emptyList())
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val domains = runCatching { viewModel.listCustomDomains(bucket) }.getOrDefault(emptyList())
            renderDomains(domains)
        }
    }

    private fun renderDomains(domains: List<CustomDomain>) {
        binding.tvDomainsTitle.text = getString(R.string.advanced_domains, domains.size)
        val container = binding.containerDomains
        container.removeAllViews()
        if (domains.isEmpty()) {
            val item = ItemSettingBinding.inflate(layoutInflater, container, false)
            item.ivIcon.setImageResource(R.drawable.ic_globe)
            item.tvTitle.setText(R.string.advanced_no_domain)
            item.ivTrail.visibility = View.GONE
            item.root.isClickable = false
            container.addView(item.root)
            return
        }
        for (domain in domains) {
            val item = ItemSettingBinding.inflate(layoutInflater, container, false)
            item.ivIcon.setImageResource(R.drawable.ic_globe)
            item.tvTitle.text = domain.domain
            item.tvMeta.text = if (domain.enabled) {
                getString(R.string.advanced_enabled)
            } else {
                getString(R.string.advanced_disabled)
            }
            item.root.setOnClickListener { confirmUnbind(domain) }
            container.addView(item.root)
        }
    }

    private fun loadManagedDomain() {
        val bucket = viewModel.currentBucket()
        if (bucket.isBlank()) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val managed = runCatching { viewModel.getManagedDomain(bucket) }.getOrNull() ?: return@launch
            bindingFlags = true
            binding.switchR2Dev.isChecked = managed.enabled
            bindingFlags = false
            binding.tvR2devHost.text = managed.domain
        }
    }

    // ==================== 操作 ====================

    private fun bindDomain() {
        val bucket = viewModel.currentBucket()
        if (bucket.isBlank()) {
            toast(R.string.settings_bucket_none)
            return
        }
        val domain = binding.etDomain.text?.toString()?.trim().orEmpty()
        val zoneName = binding.acZone.text?.toString()?.trim().orEmpty()
        val zone = zones.firstOrNull { it.name == zoneName }
        if (domain.isEmpty() || zone == null) {
            toast(R.string.advanced_need_domain)
            return
        }
        val minTls = binding.acMinTls.text?.toString()?.trim().orEmpty().ifBlank { "1.2" }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnBind.isEnabled = false
            runCatching {
                viewModel.upsertCustomDomain(
                    bucket,
                    CustomDomainInput(domain = domain, zoneId = zone.id, enabled = true, minTls = minTls)
                )
            }
                .onSuccess {
                    toast(R.string.advanced_bound)
                    binding.etDomain.setText("")
                    loadDomains()
                }
                .onFailure { toast(R.string.error_network) }
            binding.btnBind.isEnabled = true
        }
    }

    private fun confirmUnbind(domain: CustomDomain) {
        val bucket = viewModel.currentBucket()
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.advanced_zone_prefix, domain.zoneId, domain.minTls))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.advanced_unbind) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { viewModel.deleteCustomDomain(bucket, domain.domain) }
                        .onSuccess {
                            toast(R.string.advanced_unbound)
                            loadDomains()
                        }
                        .onFailure { toast(R.string.error_network) }
                }
            }
            .show()
    }

    private fun setManagedDomain(enabled: Boolean) {
        val bucket = viewModel.currentBucket()
        if (bucket.isBlank()) {
            bindingFlags = true
            binding.switchR2Dev.isChecked = !enabled
            bindingFlags = false
            toast(R.string.settings_bucket_none)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { viewModel.setManagedDomain(bucket, enabled) }
                .onSuccess { managed ->
                    bindingFlags = true
                    binding.switchR2Dev.isChecked = managed.enabled
                    bindingFlags = false
                    binding.tvR2devHost.text = managed.domain
                }
                .onFailure {
                    bindingFlags = true
                    binding.switchR2Dev.isChecked = !enabled
                    bindingFlags = false
                    toast(R.string.error_network)
                }
        }
    }

    private fun bindDropdown(view: MaterialAutoCompleteTextView, options: List<String>, selected: String) {
        view.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options))
        view.setText(selected, false)
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
