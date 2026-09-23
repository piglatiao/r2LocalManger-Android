package com.r2manager.android.ui.startup

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.core.constants.UiConstants
import com.r2manager.android.core.error.RecoveryAction
import com.r2manager.android.databinding.ActivityStartupBinding
import com.r2manager.android.ui.common.ViewModelFactory
import com.r2manager.android.ui.common.applyStatusBarPadding
import com.r2manager.android.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * 启动页（对应架构 §5.4 冷启动分流）。
 *
 * - 先向 [com.r2manager.android.AppContainer] 注册自身，完成旧版本安全状态迁移；
 * - 依据 [StartupViewModel.state] 路由：
 *   - [StartupUiState.Ready] → [MainActivity]（文件列表）；
 *   - [StartupUiState.MissingCredentials] → [MainActivity] 设置页（凭证引导，不发网络请求）；
 *   - [StartupUiState.Failed] → 五态错误视图（不 finish，便于重试）。
 *
 * 安卓端不实现应用内密码锁，应用保护由系统应用锁负责。
 */
class StartupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartupBinding

    private val viewModel: StartupViewModel by viewModels {
        ViewModelFactory.of(appContainer()) { StartupViewModel(appContainer()) }
    }

    /** 防止状态切换期间重复路由。 */
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appContainer().attachActivity(this)
        binding.startupContainer.applyStatusBarPadding()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> handleState(state) }
            }
        }
    }

    override fun onDestroy() {
        appContainer().detachActivity(this)
        super.onDestroy()
    }

    private fun handleState(state: StartupUiState) {
        when (state) {
            StartupUiState.Checking -> {
                binding.stateView.showLoading(getString(R.string.state_loading))
            }

            StartupUiState.Ready -> navigate {
                startActivity(buildMainIntent())
            }

            StartupUiState.MissingCredentials -> navigate {
                startActivity(buildMainIntent().putExtra(UiConstants.EXTRA_OPEN_SETTINGS, true))
            }

            is StartupUiState.Failed -> {
                binding.stateView.showError(
                    error = state.error,
                    primaryLabelRes = R.string.action_retry,
                    onPrimary = { action -> onRecovery(action) }
                )
            }
        }
    }

    private fun onRecovery(action: RecoveryAction) {
        when (action) {
            RecoveryAction.RETRY -> viewModel.start()
            else -> viewModel.start()
        }
    }

    /**
     * 构造进入主界面的 Intent。
     *
     * 保留 [MainActivity] 的 `EXTRA_OPEN_TRANSFER` 读取逻辑，并原样透传本页来访 extras
     * （含通知携带的 `EXTRA_OPEN_TRANSFER`）。
     */
    private fun buildMainIntent(): Intent =
        MainActivity.intent(this).also { forwardIncoming(it) }

    /**
     * 把本页来访 Intent 的 extras 与分享负载（`ACTION_SEND[_MULTIPLE]` + `EXTRA_STREAM`/`clipData`）
     * 附加到 [target]，不做任何删改，保证「原样透传」。
     */
    private fun forwardIncoming(target: Intent) {
        val incoming: Intent = intent ?: return
        incoming.extras?.let { target.putExtras(it) }
        if (incoming.action == Intent.ACTION_SEND || incoming.action == Intent.ACTION_SEND_MULTIPLE) {
            target.action = incoming.action
            target.clipData = incoming.clipData
        }
    }

    /** 只在首次进入某个可导航状态时执行路由，避免重复启动。 */
    private inline fun navigate(block: () -> Unit) {
        if (navigated) {
            return
        }
        navigated = true
        block()
        finish()
    }
}
