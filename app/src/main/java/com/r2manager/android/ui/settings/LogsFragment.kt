package com.r2manager.android.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.r2manager.android.R
import com.r2manager.android.core.log.AppLog
import com.r2manager.android.databinding.FragmentLogsBinding

/**
 * 运行日志（P4-B）。
 *
 * 数据来源为 P1 [AppLog] 的内存环形日志（最近 200 条，已脱敏）；支持导出（分享文本）与清空。
 */
class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { back() }
        binding.btnExport.setOnClickListener { export() }
        binding.btnClear.setOnClickListener { clear() }

        render()
    }

    private fun render() {
        val text = AppLog.exportText()
        binding.tvLogs.text = text.ifBlank { getString(R.string.logs_empty) }
    }

    private fun export() {
        val text = AppLog.exportText()
        if (text.isBlank()) {
            toast(R.string.logs_empty)
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { startActivity(Intent.createChooser(send, getString(R.string.logs_export))) }
    }

    private fun clear() {
        AppLog.clear()
        render()
        toast(R.string.logs_cleared)
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
