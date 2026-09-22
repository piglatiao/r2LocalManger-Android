package com.r2manager.android.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.r2manager.android.R
import com.r2manager.android.appContainer
import com.r2manager.android.databinding.ActivityLockBinding
import com.r2manager.android.domain.security.BiometricAuthenticator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用锁解锁页（P4-B）。
 *
 * 降级链路（PRD 硬要求）：
 * 1. 生物识别可用且已开启 → 作为**优先项**（软上锁场景密钥仍在内存，验证成功直接放行）；
 * 2. 生物识别不可用 / 失败 / 取消 / 冷启动无会话密钥 → **回退到 PIN 兜底通道**；
 * 3. PIN 键盘（[GridLayout]）**恒定可用**，不依赖 API 版本（API 26–29 亦可用）；
 * 4. 连续 5 次密码错误 → 锁定 30 秒并倒计时（由 P3 [com.r2manager.android.domain.security.AppLockManager]
 *    内部的 [com.r2manager.android.domain.security.PinAttemptTracker] 判定，返回 `lockoutRemainingMs`）。
 *
 * 页面**不渲染任何列表内容**（R-03）。解锁成功 → `setResult(RESULT_OK)` + `finish()`，由启动页
 * 以结果契约续跑后续分流（本页**不**自行跳转 `MainActivity`、不透传 extras）。
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var biometric: BiometricAuthenticator

    private val viewModel: LockViewModel by viewModels {
        LockViewModel.factory(applicationContext.appContainer())
    }

    /** 找回密码结果：重设成功后密钥已装载（会话即解锁），直接放行；取消则留在本页。 */
    private val resetPasswordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            onUnlocked()
        }
    }

    private val entered = StringBuilder()
    private val keyCells = mutableListOf<View>()

    private var inputLocked = false
    private var submitting = false
    private var countdownJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        biometric = BiometricAuthenticator(this)

        val status = viewModel.status()
        if (!status.enabled || !status.hasPassword) {
            // 未启用锁或未设置密码：无需解锁，直接以 RESULT_OK 收尾（由启动页续跑分流）。
            onUnlocked()
            return
        }

        buildKeypad()
        binding.btnUnlock.setOnClickListener { submit() }
        binding.tvForgot.setOnClickListener {
            resetPasswordLauncher.launch(Intent(this, ResetPasswordActivity::class.java))
        }
        updatePinDisplay()
        maybeAutoPromptBiometric()
    }

    // ==================== 键盘构建 ====================

    private fun buildKeypad() {
        val grid = binding.keypad
        grid.removeAllViews()
        grid.columnCount = COLUMNS
        keyCells.clear()

        val cellHeight = resources.getDimensionPixelSize(R.dimen.appbar_height)
        for (digit in 1..9) {
            addCell(grid, digitCell(digit.toString()), row = (digit - 1) / COLUMNS, col = (digit - 1) % COLUMNS, cellHeight)
        }
        // 第三行：生物识别 / 0 / 退格
        addCell(grid, biometricCell(), row = 3, col = 0, cellHeight)
        addCell(grid, digitCell("0"), row = 3, col = 1, cellHeight)
        addCell(grid, backspaceCell(), row = 3, col = 2, cellHeight)
    }

    private fun addCell(grid: GridLayout, cell: View, row: Int, col: Int, cellHeight: Int) {
        val params = GridLayout.LayoutParams(
            GridLayout.spec(row),
            GridLayout.spec(col, 1f)
        ).apply {
            width = 0
            height = cellHeight
        }
        cell.layoutParams = params
        keyCells.add(cell)
        grid.addView(cell)
    }

    private fun digitCell(text: String): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setSingleLine(true)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_page_title))
        setTextColor(ContextCompat.getColor(this@LockActivity, R.color.ink_primary))
        background = ContextCompat.getDrawable(this@LockActivity, R.drawable.item_ripple)
        isClickable = true
        isFocusable = true
        contentDescription = text
        setOnClickListener { onDigit(text) }
    }

    private fun backspaceCell(): TextView = TextView(this).apply {
        text = BACKSPACE_SYMBOL
        gravity = Gravity.CENTER
        setSingleLine(true)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_page_title))
        setTextColor(ContextCompat.getColor(this@LockActivity, R.color.ink_primary))
        background = ContextCompat.getDrawable(this@LockActivity, R.drawable.item_ripple)
        isClickable = true
        isFocusable = true
        contentDescription = getString(R.string.lock_delete)
        setOnClickListener { onBackspace() }
    }

    private fun biometricCell(): View {
        val usable = viewModel.isBiometricEnabled() && biometric.canAuthenticate()
        val image = ImageView(this).apply {
            setImageResource(R.drawable.ic_lock)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@LockActivity, R.color.primary)
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = ContextCompat.getDrawable(this@LockActivity, R.drawable.item_ripple)
            isClickable = usable
            isFocusable = usable
            contentDescription = getString(R.string.lock_biometric)
            setOnClickListener { startBiometric() }
        }
        // 不可用时保留占位（INVISIBLE），维持 0 / 退格的宫格对齐。
        if (!usable) {
            image.visibility = View.INVISIBLE
        }
        return image
    }

    // ==================== 输入 ====================

    private fun onDigit(digit: String) {
        if (inputLocked || submitting) return
        if (entered.length >= MAX_PIN_LENGTH) return
        entered.append(digit)
        clearError()
        updatePinDisplay()
    }

    private fun onBackspace() {
        if (inputLocked || submitting) return
        if (entered.isNotEmpty()) {
            entered.setLength(entered.length - 1)
            clearError()
            updatePinDisplay()
        }
    }

    private fun updatePinDisplay() {
        binding.tvPinDisplay.text = DOT.repeat(entered.length)
        binding.btnUnlock.isEnabled = !inputLocked && !submitting && entered.isNotEmpty()
    }

    // ==================== 解锁 ====================

    private fun submit() {
        if (submitting || inputLocked) return
        val password = entered.toString()
        if (password.isEmpty()) return

        submitting = true
        binding.btnUnlock.isEnabled = false
        lifecycleScope.launch {
            val result = viewModel.unlockWithPassword(password)
            submitting = false
            if (result.success) {
                onUnlocked()
                return@launch
            }
            entered.setLength(0)
            updatePinDisplay()
            val lockout = result.lockoutRemainingMs
            if (lockout > 0L) {
                startLockout(lockout)
            } else {
                showError(getString(R.string.lock_error_wrong))
                binding.btnUnlock.isEnabled = entered.isNotEmpty()
            }
        }
    }

    private fun startLockout(remainingMs: Long) {
        countdownJob?.cancel()
        inputLocked = true
        setKeypadEnabled(false)
        binding.btnUnlock.isEnabled = false

        countdownJob = lifecycleScope.launch {
            var remaining = remainingMs
            while (remaining > 0L) {
                val seconds = ((remaining + 999L) / 1000L).toInt()
                showError(getString(R.string.lock_error_locked, seconds))
                delay(TICK_MS)
                remaining -= TICK_MS
            }
            inputLocked = false
            setKeypadEnabled(true)
            clearError()
            updatePinDisplay()
        }
    }

    private fun setKeypadEnabled(enabled: Boolean) {
        for (cell in keyCells) {
            cell.isEnabled = enabled
            cell.alpha = if (enabled) 1f else DISABLED_ALPHA
        }
    }

    // ==================== 生物识别 ====================

    private fun maybeAutoPromptBiometric() {
        // 冷启动 / 软上锁：只要已登记生物识别绑定、或仍持有会话密钥，就自动唤起 BiometricPrompt（PRD R-03）。
        val canUnlock = viewModel.hasBiometricKey() || viewModel.hasSessionKey()
        if (viewModel.isBiometricEnabled() && biometric.canAuthenticate() && canUnlock) {
            startBiometric()
        }
    }

    private fun startBiometric() {
        if (!viewModel.isBiometricEnabled() || !biometric.canAuthenticate()) {
            showError(getString(R.string.lock_biometric_unavailable))
            return
        }
        biometric.authenticate(
            getString(R.string.lock_biometric_prompt_title),
            getString(R.string.lock_biometric_prompt_sub)
        ) { ok, error ->
            if (isFinishing || isDestroyed) return@authenticate
            when {
                ok -> onBiometricSuccess()
                error != null -> showError(getString(R.string.lock_biometric_unavailable))
                // 用户主动取消：静默，保留 PIN 通道
            }
        }
    }

    /**
     * 生物识别成功后的放行逻辑。
     *
     * - 软上锁（会话密钥仍在内存）→ 直接放行；
     * - 冷启动（密钥已被 [com.r2manager.android.domain.security.AppLockManager.bootstrap] 清理）→
     *   用生物识别绑定的 Keystore 私钥还原会话密钥（PRD R-03）；还原失败（未登记 / 绑定失效）则
     *   留在本页提示改用密码，PIN 兜底通道始终可用。
     */
    private fun onBiometricSuccess() {
        if (viewModel.hasSessionKey()) {
            onUnlocked()
            return
        }
        lifecycleScope.launch {
            val result = viewModel.unlockByBiometric()
            if (result.success) {
                onUnlocked()
            } else {
                showError(getString(R.string.lock_biometric_need_password))
            }
        }
    }

    // ==================== 展示 ====================

    private fun showError(message: CharSequence) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun clearError() {
        binding.tvError.text = ""
        binding.tvError.visibility = View.GONE
    }

    /**
     * 解锁完成：以 `RESULT_OK` 收尾并结束本页，**不再自行跳转 MainActivity、也不透传 extras**。
     *
     * - 冷启动（由启动页以结果契约拉起）：回调 `RESULT_OK`，由启动页续跑「解锁后凭证校验 / 连接探测 /
     *   分流」后再进主界面；extras 由启动页持有的原始 intent 权威提供（此处不回传，避免双次导航）。
     * - 重新上锁（`AutoLockController` 在运行中的 `MainActivity` 之上拉起，无调用方等结果）：
     *   `setResult` 无副作用，`finish()` 后自然露出下层 `MainActivity`。
     *
     * 两条路径共用本方法，均以 `finish()` 为准，不写「RESULT_OK 专属」分支。
     */
    private fun onUnlocked() {
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        countdownJob = null
        super.onDestroy()
    }

    companion object {
        private const val COLUMNS = 3
        private const val MAX_PIN_LENGTH = 32
        private const val TICK_MS = 1000L
        private const val DISABLED_ALPHA = 0.4f

        /** 圆点字符（展示已输入位数，不暴露明文）。 */
        private const val DOT = "\u2022"

        /** 退格符号（语言无关）。 */
        private const val BACKSPACE_SYMBOL = "\u232B"

        /**
         * 启动解锁页。
         *
         * @param context 上下文
         */
        fun start(context: Context) {
            context.startActivity(
                Intent(context, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
