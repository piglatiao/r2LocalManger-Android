package com.r2manager.android.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import com.r2manager.android.AppContainer
import com.r2manager.android.appContainer

/**
 * Fragment 基类：统一 ViewBinding 生命周期、依赖容器获取与轻提示。
 *
 * 子类只需实现 [inflateBinding] 与 [onBind]；[binding] 在 [onViewCreated] ~ [onDestroyView] 间非空。
 *
 * @param VB 具体 ViewBinding 类型
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    /** 非空绑定（仅在视图存活期访问）。 */
    protected val binding: VB get() = checkNotNull(_binding) { "binding 仅在 onViewCreated~onDestroyView 间可用" }

    /** 应用依赖容器（经宿主 Activity）。 */
    protected val container: AppContainer get() = requireContext().appContainer()

    /** 宿主 Activity（泛型便捷）。 */
    protected val hostActivity: FragmentActivity get() = requireActivity()

    /** 创建绑定对象。 */
    protected abstract fun inflateBinding(inflater: LayoutInflater, parent: ViewGroup?): VB

    /** 视图创建完成后的初始化（订阅、监听等）。 */
    protected abstract fun onBind(binding: VB)

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onBind(binding)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 简短 Toast。 */
    protected fun toast(message: CharSequence) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /** 资源文案 Toast。 */
    protected fun toast(@StringRes messageRes: Int) {
        toast(getString(messageRes))
    }

    /** 在根视图上展示 Snackbar。 */
    protected fun snackbar(message: CharSequence) {
        _binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_SHORT).show() }
    }

    /** 资源文案 Snackbar。 */
    protected fun snackbar(@StringRes messageRes: Int) {
        snackbar(getString(messageRes))
    }
}
