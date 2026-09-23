package com.r2manager.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.r2manager.android.R
import com.r2manager.android.core.error.AppError
import com.r2manager.android.core.error.ErrorMapper
import com.r2manager.android.core.error.RecoveryAction

/**
 * 五态视图容器（骨架加载 / 空 / 无结果 / 离线 / 错误）。
 *
 * 设计要点（`docs/05 §7.5` 验收点「五态视图切换正确」）：
 * - 五种状态是**独立视图**，不复用同一个空容器，各自给下一步动作；
 * - 错误态文案**一律经 [ErrorMapper] 取 `@string/error_*`**，页面不得硬编码；
 * - 主动作按 [RecoveryAction] 映射 P1 通用按钮文案（`action_*`），
 *   认证类（`GO_TO_CREDENTIALS`）等无通用文案的动作由调用方传入 [showError] 的 `primaryLabelRes`。
 *
 * 使用方式（Fragment/Activity 中）：
 * ```
 * stateView.showLoading()
 * stateView.showEmpty(descRes = R.string.x, actionRes = R.string.action_upload) { doUpload() }
 * stateView.showError(appError) { recovery -> handle(recovery) }
 * ```
 */
class StateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 当前展示的状态。 */
    enum class State {
        /** 内容态（StateView 隐藏，让位给真实内容）。 */
        CONTENT,

        /** 骨架 / 加载态。 */
        LOADING,

        /** 空态（目录无文件）。 */
        EMPTY,

        /** 无结果态（搜索 / 筛选命中为空）。 */
        NO_RESULT,

        /** 离线态（无网络且无本地缓存）。 */
        OFFLINE,

        /** 错误态。 */
        ERROR
    }

    private val progressBar: ProgressBar
    private val iconView: ImageView
    private val titleView: TextView
    private val descView: TextView
    private val actionsContainer: LinearLayout
    private val primaryButton: MaterialButton
    private val secondaryButton: MaterialButton

    /** 供外部读取的当前状态（只读）。 */
    var state: State = State.CONTENT
        private set

    /** 是否正展示某个「非内容」状态且可见。 */
    val isShowingState: Boolean
        get() = visibility == View.VISIBLE && state != State.CONTENT

    init {
        LayoutInflater.from(context).inflate(R.layout.view_state, this, true)
        progressBar = findViewById(R.id.state_progress)
        iconView = findViewById(R.id.state_icon)
        titleView = findViewById(R.id.state_title)
        descView = findViewById(R.id.state_desc)
        actionsContainer = findViewById(R.id.state_actions)
        primaryButton = findViewById(R.id.state_action_primary)
        secondaryButton = findViewById(R.id.state_action_secondary)
        // 默认隐藏，等调用方显式 show*。
        visibility = View.GONE
    }

    // ==================== 对外 API ====================

    /** 骨架 / 加载态。[message] 为空时仅显示转圈。 */
    fun showLoading(message: CharSequence? = null) {
        state = State.LOADING
        visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        iconView.visibility = View.GONE
        if (message.isNullOrEmpty()) {
            titleView.visibility = View.GONE
        } else {
            titleView.visibility = View.VISIBLE
            titleView.text = message
        }
        descView.visibility = View.GONE
        bindAction(primaryButton, null, null)
        bindAction(secondaryButton, null, null)
        actionsContainer.visibility = View.GONE
    }

    /** 空态。默认文案取 P1 通用 `state_empty`。 */
    fun showEmpty(
        @StringRes titleRes: Int = R.string.state_empty,
        @StringRes descRes: Int? = null,
        @StringRes actionRes: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        state = State.EMPTY
        renderState(
            iconRes = R.drawable.ic_folder,
            iconTint = R.color.brand,
            iconBg = R.color.brand_soft,
            title = context.getString(titleRes),
            desc = descRes?.let { context.getString(it) },
            primaryLabel = actionRes?.let { context.getString(it) },
            onPrimary = onAction,
            secondaryLabel = null,
            onSecondary = null
        )
    }

    /** 无结果态（搜索 / 筛选命中为空）。默认文案取 P1 通用 `state_no_result`。 */
    fun showNoResult(
        @StringRes titleRes: Int = R.string.state_no_result,
        @StringRes descRes: Int? = null,
        @StringRes actionRes: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        state = State.NO_RESULT
        renderState(
            iconRes = R.drawable.ic_search,
            iconTint = R.color.ink_secondary,
            iconBg = R.color.surface_variant,
            title = context.getString(titleRes),
            desc = descRes?.let { context.getString(it) },
            primaryLabel = actionRes?.let { context.getString(it) },
            onPrimary = onAction,
            secondaryLabel = null,
            onSecondary = null
        )
    }

    /** 离线态（无网络且无本地缓存）。默认文案取 P1 通用 `state_offline`。 */
    fun showOffline(
        @StringRes titleRes: Int = R.string.state_offline,
        @StringRes descRes: Int? = null,
        @StringRes actionRes: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        state = State.OFFLINE
        renderState(
            iconRes = R.drawable.ic_wifi,
            iconTint = R.color.ink_secondary,
            iconBg = R.color.surface_variant,
            title = context.getString(titleRes),
            desc = descRes?.let { context.getString(it) },
            primaryLabel = actionRes?.let { context.getString(it) },
            onPrimary = onAction,
            secondaryLabel = null,
            onSecondary = null
        )
    }

    /**
     * 错误态。主文案取 [AppError.messageResId]；详情由 [ErrorMapper] 统一生成；
     * 主动作文案优先取调用方 [primaryLabelRes]，否则按恢复动作映射 P1 通用文案。
     *
     * @param error 统一错误模型
     * @param primaryLabelRes 主动作文案（如「去配置」），认证类等无通用文案时由调用方给出
     * @param onPrimary 主动作回调，参数为恢复动作
     * @param secondaryLabelRes 次动作文案（如「重试」的辅助动作），一般用于补充
     * @param onSecondary 次动作回调
     */
    fun showError(
        error: AppError,
        @StringRes primaryLabelRes: Int? = null,
        onPrimary: ((RecoveryAction) -> Unit)? = null,
        @StringRes secondaryLabelRes: Int? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        state = State.ERROR
        val title = context.getString(error.messageResId)
        val desc = ErrorMapper.detailFor(context, error)
        val labelRes = primaryLabelRes ?: defaultRecoveryLabel(error.recovery)
        renderState(
            iconRes = R.drawable.ic_info,
            iconTint = R.color.error,
            iconBg = R.color.red_soft,
            title = title,
            desc = desc,
            primaryLabel = labelRes?.let { context.getString(it) },
            onPrimary = onPrimary?.let { callback -> { callback(error.recovery) } },
            secondaryLabel = secondaryLabelRes?.let { context.getString(it) },
            onSecondary = onSecondary
        )
    }

    /** 隐藏状态视图，让位给真实内容。 */
    fun hide() {
        state = State.CONTENT
        visibility = View.GONE
    }

    // ==================== 内部实现 ====================

    private fun renderState(
        @DrawableRes iconRes: Int,
        @ColorRes iconTint: Int,
        @ColorRes iconBg: Int,
        title: CharSequence,
        desc: CharSequence?,
        primaryLabel: CharSequence?,
        onPrimary: (() -> Unit)?,
        secondaryLabel: CharSequence?,
        onSecondary: (() -> Unit)?
    ) {
        visibility = View.VISIBLE
        progressBar.visibility = View.GONE

        iconView.visibility = View.VISIBLE
        iconView.setImageResource(iconRes)
        iconView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, iconTint))
        iconView.background = roundedBackground(iconBg)

        titleView.visibility = View.VISIBLE
        titleView.text = title

        if (desc.isNullOrEmpty()) {
            descView.visibility = View.GONE
        } else {
            descView.visibility = View.VISIBLE
            descView.text = desc
        }

        bindAction(primaryButton, primaryLabel, onPrimary)
        bindAction(secondaryButton, secondaryLabel, onSecondary)
        actionsContainer.visibility =
            if (primaryButton.visibility == View.VISIBLE || secondaryButton.visibility == View.VISIBLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun bindAction(button: MaterialButton, label: CharSequence?, action: (() -> Unit)?) {
        if (label.isNullOrEmpty() || action == null) {
            button.visibility = View.GONE
            button.setOnClickListener(null)
        } else {
            button.text = label
            button.visibility = View.VISIBLE
            button.setOnClickListener { action() }
        }
    }

    private fun roundedBackground(@ColorRes colorRes: Int): GradientDrawable {
        val radius = resources.getDimension(R.dimen.radius_panel)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(ContextCompat.getColor(context, colorRes))
        }
    }

    /** 恢复动作 → P1 通用按钮文案（无对应通用文案时返回 null，交由调用方指定）。 */
    private fun defaultRecoveryLabel(action: RecoveryAction): Int? = when (action) {
        RecoveryAction.RETRY -> R.string.action_retry
        RecoveryAction.REFRESH_LIST -> R.string.action_refresh
        RecoveryAction.OPEN_LOGS -> R.string.action_view_logs
        RecoveryAction.GO_TO_CREDENTIALS -> null
        RecoveryAction.RESELECT_DIRECTORY -> null
        RecoveryAction.NONE -> null
    }
}
