package com.r2manager.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.BucketConstants
import com.r2manager.android.databinding.FragmentBucketSettingsBinding
import com.r2manager.android.databinding.ItemBucketBinding
import com.r2manager.android.domain.model.Bucket
import kotlinx.coroutines.launch

/**
 * 存储桶设置（P4-B）。
 *
 * 列出账号下全部桶（点击切换当前桶）；支持新建桶、更新当前桶存储类型、删除当前桶
 * （删除前需输入桶名二次确认，仓储会先经 S3 清空桶内全部对象再删除）。
 */
class BucketSettingsFragment : Fragment() {

    private var _binding: FragmentBucketSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: BucketSettingsViewModel by viewModels {
        BucketSettingsViewModel.factory(requireContext().appContainer())
    }

    private val locationOptions = BucketConstants.LOCATION_HINTS
    private val storageClassOptions = listOf(
        BucketConstants.STORAGE_STANDARD, BucketConstants.STORAGE_INFREQUENT
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBucketSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindDropdown(binding.acLocation, locationOptions, locationOptions.first())
        bindDropdown(binding.acStorageClass, storageClassOptions, storageClassOptions.first())
        bindDropdown(binding.acStorageClassEdit, storageClassOptions, storageClassOptions.first())

        binding.btnBack.setOnClickListener { back() }
        binding.btnCreate.setOnClickListener { createBucket() }
        binding.btnUpdateClass.setOnClickListener { updateStorageClass() }
        binding.btnDeleteBucket.setOnClickListener { confirmDelete() }

        refreshCurrent()
        loadBuckets()
    }

    private fun refreshCurrent() {
        val current = viewModel.currentBucket()
        binding.tvCurrentBucket.text =
            current.ifBlank { getString(R.string.settings_bucket_none) }
    }

    private fun loadBuckets() {
        viewLifecycleOwner.lifecycleScope.launch {
            val buckets = runCatching { viewModel.listBuckets() }.getOrDefault(emptyList())
            renderBuckets(buckets)
        }
    }

    private fun renderBuckets(buckets: List<Bucket>) {
        val current = viewModel.currentBucket()
        binding.tvBucketsTitle.text = getString(R.string.bucket_all, buckets.size)

        val container = binding.containerBuckets
        container.removeAllViews()
        for (bucket in buckets) {
            val item = ItemBucketBinding.inflate(layoutInflater, container, false)
            item.tvName.text = bucket.name
            item.tvMeta.text = metaLine(bucket)
            val isCurrent = bucket.name == current
            item.tvCurrent.visibility = if (isCurrent) View.VISIBLE else View.GONE
            item.ivCheck.visibility = if (isCurrent) View.VISIBLE else View.GONE
            item.root.setOnClickListener { switchTo(bucket.name) }
            container.addView(item.root)
        }
    }

    private fun metaLine(bucket: Bucket): String {
        val location = bucket.locationHint.ifBlank { BucketConstants.LOCATION_HINTS.first() }
        val storage = bucket.storageClass.ifBlank { BucketConstants.STORAGE_STANDARD }
        return "$location · $storage"
    }

    private fun switchTo(name: String) {
        if (name == viewModel.currentBucket()) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { viewModel.switchBucket(name) }
                .onSuccess {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.bucket_switch_hint, name),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshCurrent()
                    renderBuckets(viewModel.buckets)
                }
                .onFailure { toast(R.string.error_bucket) }
        }
    }

    private fun createBucket() {
        val name = binding.etBucketName.text?.toString()?.trim().orEmpty()
        if (!BucketConstants.isValidName(name)) {
            toast(R.string.bucket_name_invalid)
            return
        }
        val location = binding.acLocation.text?.toString()?.trim().orEmpty()
        val storageClass = binding.acStorageClass.text?.toString()?.trim().orEmpty()
            .ifBlank { BucketConstants.STORAGE_STANDARD }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnCreate.isEnabled = false
            runCatching {
                viewModel.createBucket(
                    name = name,
                    storageClass = storageClass,
                    locationHint = location.ifBlank { null }
                )
            }
                .onSuccess {
                    toast(R.string.bucket_created)
                    binding.etBucketName.setText("")
                    loadBuckets()
                }
                .onFailure { toast(R.string.error_bucket) }
            binding.btnCreate.isEnabled = true
        }
    }

    private fun updateStorageClass() {
        val current = viewModel.currentBucket()
        if (current.isBlank()) {
            toast(R.string.settings_bucket_none)
            return
        }
        val storageClass = binding.acStorageClassEdit.text?.toString()?.trim().orEmpty()
            .ifBlank { BucketConstants.STORAGE_STANDARD }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { viewModel.updateStorageClass(current, storageClass) }
                .onSuccess {
                    toast(R.string.bucket_class_updated)
                    loadBuckets()
                }
                .onFailure { toast(R.string.error_bucket) }
        }
    }

    private fun confirmDelete() {
        val current = viewModel.currentBucket()
        if (current.isBlank()) {
            toast(R.string.settings_bucket_none)
            return
        }
        val input = EditText(requireContext()).apply {
            hint = current
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bucket_delete_confirm_title)
            .setMessage(getString(R.string.bucket_delete_confirm_message, current))
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.bucket_delete) { _, _ ->
                if (input.text?.toString()?.trim() == current) {
                    deleteBucket(current)
                } else {
                    toast(R.string.bucket_name_invalid)
                }
            }
            .show()
    }

    private fun deleteBucket(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { viewModel.deleteBucket(name) }
                .onSuccess {
                    toast(R.string.bucket_deleted)
                    refreshCurrent()
                    loadBuckets()
                }
                .onFailure { toast(R.string.error_bucket) }
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
