package com.r2manager.android.ui.startup

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
import com.r2manager.android.ui.lock.LockActivity
import com.r2manager.android.ui.lock.LockSetupActivity
import com.r2manager.android.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * 启动页（对应架构 §5.4 冷启动分流）。
 *
 * - 先向 [com.r2manager.android.AppContainer] 注册自身（应用锁 bootstrap 需要 Activity）；
 * - 依据 [StartupViewModel.state] 路由：
 *   - [StartupUiState.Ready] → [MainActivity]（文件列表）；
 *   - [StartupUiState.RequiresLock] → [LockActivity]（结果回调；解锁成功后续跑凭证校验再分流）；
 *   - [StartupUiState.MissingCredentials] → [MainActivity] 设置页（凭证引导，不发网络请求）；
 *   - [StartupUiState.Failed] → 五态错误视图（不 finish，便于重试）。
 *
 * 硬不变式：任何进入 [MainActivity]（可能展示桶/凭证内容）的路径，都必须先经过应用锁校验。
 */
class StartupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartupBinding

    private val viewModel: StartupViewModel by viewModels {
        ViewModelFactory.of(appContainer()) { StartupViewModel(appContainer()) }
    }

    /** 防止状态切换期间重复路由。 */
    private var navigated = false

    /** 是否已向解锁页发起请求（避免状态重入时重复 launch）。 */
    private var unlockRequested = false

    /** 是否已向首次密码引导页发起请求。 */
    private var passwordSetupRequested = false

    /**
     * 解锁页结果回调：`RESULT_OK` → 跳过 bootstrap 续跑凭证校验与分流；否则用户放弃解锁 → 退出。
     *
     * 说明：`unlockRequested` 有意**不**在此复位——解锁后状态若被重复观测为 [StartupUiState.RequiresLock]，
     * 该标记可防止再次拉起解锁页；真正的离开由后续 [navigate] / [finish] 完成。
     */
    private val lockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            navigated = false
            viewModel.continueAfterUnlock()
        } else {
            finish()
        }
    }

    /** 首次密码引导结果：设置成功或跳过后都继续进入正常分流。 */
    private val passwordSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            viewModel.dismissPasswordSetup()
        }
        navigated = false
        viewModel.continueAfterPasswordSetup()
    }

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

            StartupUiState.RequiresLock -> {
                // 用结果回调启动锁页（锁页解锁成功 setResult(RESULT_OK)+finish 后回到本回调），
                // 本页不 finish，保证解锁归来能续跑凭证校验与分流。
                if (!unlockRequested) {
                    unlockRequested = true
                    lockLauncher.launch(buildLockIntent())
                }
            }

            StartupUiState.NeedsPasswordSetup -> {
                if (!passwordSetupRequested) {
                    passwordSetupRequested = true
                    passwordSetupLauncher.launch(Intent(this, LockSetupActivity::class.java))
                }
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
     * 构造进入解锁页的 Intent。
     *
     * 来访 extras 一并附上（防御性透传）；但真正带给主界面的 extras 以**本页原始 intent** 为准，
     * 不依赖锁页回传——解锁成功后由锁页 `setResult(RESULT_OK) + finish()`，本页在结果回调中续跑分流。
     */
    private fun buildLockIntent(): Intent =
        Intent(this, LockActivity::class.java).also { forwardIncoming(it) }

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
