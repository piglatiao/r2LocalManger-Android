package com.r2manager.android.ui.startup

import androidx.lifecycle.viewModelScope
import com.r2manager.android.AppContainer
import com.r2manager.android.core.error.AppError
import com.r2manager.android.core.error.ErrorMapper
import com.r2manager.android.ui.common.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 启动分流结果。
 */
sealed interface StartupUiState {

    /** 检查中（展示骨架 / 转圈）。 */
    data object Checking : StartupUiState

    /** 需要解锁（已启用应用锁且未持钥）→ 启动 `LockActivity`。 */
    data object RequiresLock : StartupUiState

    /** 凭证缺失 / 不完整 / 未选桶 → 引导至凭证配置（不发起网络请求，R-01）。 */
    data object MissingCredentials : StartupUiState

    /** 就绪 → 进入 `MainActivity` 文件列表。 */
    data object Ready : StartupUiState

    /** 启动检查失败（展示五态错误视图）。 */
    data class Failed(val error: AppError) : StartupUiState
}

/**
 * 启动页 ViewModel（对应架构 §5.4 冷启动分流）。
 *
 * 流程：
 * 1. [AppContainer.appLockManager].bootstrap() —— 已启用锁则保持 locked，未启用则准备回退密钥；
 * 2. 读取凭证：缺失 / 不完整 / 未选桶 → [StartupUiState.MissingCredentials]（不发网络请求）；
 * 3. 否则 [StartupUiState.Ready]（后台静默探测由凭证保存链路负责，不阻塞启动）。
 *
 * @param container 依赖容器
 */
class StartupViewModel(container: AppContainer) : AppViewModel(container) {

    private val _state = MutableStateFlow<StartupUiState>(StartupUiState.Checking)

    /** 当前启动状态。 */
    val state: StateFlow<StartupUiState> = _state.asStateFlow()

    init {
        start()
    }

    /** 执行一次冷启动分流（含应用锁 bootstrap）。 */
    fun start() {
        appScope.launch {
            _state.value = StartupUiState.Checking

            val bootstrap = runCatching { container.appLockManager.bootstrap() }
            if (bootstrap.isFailure) {
                _state.value = StartupUiState.Failed(
                    ErrorMapper.fromThrowable(
                        bootstrap.exceptionOrNull() ?: IllegalStateException("启动初始化失败"),
                        "startup_bootstrap"
                    )
                )
                return@launch
            }
            if (bootstrap.getOrThrow().locked) {
                _state.value = StartupUiState.RequiresLock
                return@launch
            }
            routeByCredentials()
        }
    }

    /**
     * 解锁成功后继续分流：**跳过 bootstrap**。
     *
     * 注意：[com.r2manager.android.domain.security.AppLockManager.bootstrap] 对「已启用锁」会
     * 再次 `clearKey()`，重复调用会把刚解锁的会话重新上锁；因此解锁归来只能续跑凭证校验。
     */
    fun continueAfterUnlock() {
        appScope.launch {
            _state.value = StartupUiState.Checking
            routeByCredentials()
        }
    }

    /**
     * 凭证校验 → 分流（不发网络请求，R-01）：
     * 凭证缺失 / 不完整 / 未选桶 → [StartupUiState.MissingCredentials]；否则 [StartupUiState.Ready]。
     */
    private suspend fun routeByCredentials() {
        runCatching {
            val credentials = container.credentialRepository.load()
            val bucket = container.settingsRepository.settings().value.currentBucket
            if (credentials == null || !credentials.isComplete || bucket.isBlank()) {
                StartupUiState.MissingCredentials
            } else {
                StartupUiState.Ready
            }
        }.onSuccess { _state.value = it }
            .onFailure {
                _state.value = StartupUiState.Failed(ErrorMapper.fromThrowable(it, "startup_credentials"))
            }
    }
}
