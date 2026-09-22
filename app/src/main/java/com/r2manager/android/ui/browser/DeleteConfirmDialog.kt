package com.r2manager.android.ui.browser

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.r2manager.android.R
import com.r2manager.android.databinding.DialogDeleteConfirmBinding
import com.r2manager.android.databinding.DialogOverwriteConfirmBinding

/**
 * 删除 / 覆盖确认对话框工厂（material 圆角对话框，样式见 `Theme.R2Manager.Dialog`）。
 *
 * 不继承 `DialogFragment`：调用方在需要时即时构建并 `show()`，避免跨配置变更的回调泄漏。
 */
object DeleteConfirmDialog {

    /**
     * 确认删除（支持单项 / 多项）。
     *
     * @param context 上下文
     * @param names 待删除对象名（用于文案）
     * @param onConfirm 确认回调
     */
    fun showDelete(context: Context, names: List<String>, onConfirm: () -> Unit) {
        val binding = DialogDeleteConfirmBinding.inflate(android.view.LayoutInflater.from(context))
        binding.dialogDeleteTitle.setText(R.string.browser_delete_title)
        binding.dialogDeleteMessage.text = context.getString(R.string.browser_delete_message, names.size)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> onConfirm() }
            .create()
        dialog.show()
        styleButtons(dialog)
    }

    /**
     * 确认覆盖同名文件后继续上传（R-28）。
     *
     * @param context 上下文
     * @param conflictingNames 冲突文件名列表
     * @param onConfirm 继续上传回调
     */
    fun showOverwrite(context: Context, conflictingNames: List<String>, onConfirm: () -> Unit) {
        val binding = DialogOverwriteConfirmBinding.inflate(android.view.LayoutInflater.from(context))
        binding.dialogOverwriteTitle.setText(R.string.browser_overwrite_title)
        val preview = conflictingNames.take(MAX_PREVIEW_NAMES).joinToString("\n") { "· $it" }
        val extra = if (conflictingNames.size > MAX_PREVIEW_NAMES) {
            "\n" + context.getString(R.string.browser_overwrite_more, conflictingNames.size - MAX_PREVIEW_NAMES)
        } else {
            ""
        }
        binding.dialogOverwriteMessage.text =
            context.getString(R.string.browser_overwrite_message, conflictingNames.size) + "\n" + preview + extra
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ -> onConfirm() }
            .create()
        dialog.show()
        styleButtons(dialog)
    }

    private fun styleButtons(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            androidx.core.content.ContextCompat.getColor(dialog.context, R.color.primary)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            androidx.core.content.ContextCompat.getColor(dialog.context, R.color.ink_secondary)
        )
    }

    private const val MAX_PREVIEW_NAMES = 5
}
