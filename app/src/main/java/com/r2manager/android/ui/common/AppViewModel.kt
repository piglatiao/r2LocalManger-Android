package com.r2manager.android.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r2manager.android.AppContainer
import kotlinx.coroutines.CoroutineScope

/**
 * 所有页面 ViewModel 的基类。
 *
 * 统一持有应用级依赖容器 [container]（禁止在 ViewModel 内 `new` 网络 / 存储对象），
 * 并暴露 [appScope] = [viewModelScope]，保证协程随 ViewModel 生命周期自动取消。
 *
 * @param container 应用依赖容器（由 [ViewModelFactory] 注入）
 */
abstract class AppViewModel(protected val container: AppContainer) : ViewModel() {

    /** 页面级协程作用域（随 ViewModel 销毁取消）。 */
    protected val appScope: CoroutineScope get() = viewModelScope
}
