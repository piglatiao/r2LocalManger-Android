package com.r2manager.android.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.r2manager.android.AppContainer

/**
 * 通用 ViewModel 工厂（零注解处理器）。
 *
 * 由于所有 ViewModel 都直接以 [AppContainer] 作为构造依赖，这里用「按类型注册的创建器」方式装配，
 * 避免为每个实现写一个工厂类。
 *
 * 用法：
 * ```
 * private val vm: BrowserViewModel by viewModels {
 *     ViewModelFactory.of(container) { BrowserViewModel(container) }
 * }
 * ```
 *
 * @param container 应用依赖容器（保留以便创建器按需取用）
 * @param creator 目标 ViewModel 的创建器
 */
class ViewModelFactory(
    private val container: AppContainer,
    private val creator: () -> ViewModel
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        container.appContext // 触发容器可用性自检（早期失败优于运行期 NPE）
        val instance = creator()
        require(modelClass.isInstance(instance)) {
            "ViewModelFactory 创建的实例与请求类型不符: 请求 ${modelClass.name}，实际 ${instance.javaClass.name}"
        }
        return instance as T
    }

    companion object {
        /**
         * 便捷构造。
         *
         * @param container 依赖容器
         * @param create ViewModel 创建器
         */
        fun of(container: AppContainer, create: () -> ViewModel): ViewModelFactory =
            ViewModelFactory(container, create)
    }
}
