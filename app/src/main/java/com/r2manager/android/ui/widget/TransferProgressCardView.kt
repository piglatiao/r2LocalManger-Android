package com.r2manager.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.r2manager.android.R
import com.r2manager.android.core.util.AppDispatchers
import com.r2manager.android.core.util.ByteFormat
import com.r2manager.android.domain.model.TransferDirection
import com.r2manager.android.domain.model.TransferProgress
import com.r2manager.android.domain.model.TransferStatus
import com.r2manager.android.domain.transfer.TransferProgressBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 传输进度卡片（悬浮于浏览页底部的 `progcard`）。
 *
 * 数据流（`docs/05 §8.9-6`）：**唯一进度来源**是 P3 的 [TransferProgressBus]。
 * 本视图只**订阅**该总线（不产生、不修改进度），可选用 [attach] 绑定总线自动刷新，
 * 也可由调用方在拿到 [TransferProgress] 后手动 [bind]。
 *
 * 文案一律取 P1 通用资源（`transfer_speed_eta` 等）与 [ByteFormat]，
 * 方向/状态以图标与颜色区分，避免跨包依赖 P4-B 的页面文案。
 */
class TransferProgressCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val directionIcon: ImageView
    private val titleView: TextView
    private val tagView: TextView
    private val progressBar: ProgressBar
    private val bytesView: TextView
    private val speedView: TextView
    private val actionsContainer: LinearLayout
    private val primaryButton: MaterialButton
    private val dangerButton: MaterialButton

    private var observeJob: Job? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_transfer_progress_card, this, true)
        directionIcon = findViewById(R.id.tp_direction_icon)
        titleView = findViewById(R.id.tp_title)
        tagView = findViewById(R.id.tp_tag)
        progressBar = findViewById(R.id.tp_progress)
        bytesView = findViewById(R.id.tp_bytes)
        speedView = findViewById(R.id.tp_speed)
        actionsContainer = findViewById(R.id.tp_actions)
        primaryButton = findViewById(R.id.tp_action_primary)
        dangerButton = findViewById(R.id.tp_action_danger)
    }

    // ==================== 订阅总线 ====================

    /**
     * 订阅 [bus]，收到进度即刷新本卡片。
     *
     * @param scope 与生命周期绑定的协程作用域（如 `viewLifecycleOwner.lifecycleScope`）
     * @param bus P3 传输进度总线
     * @param filterTaskId 仅展示该任务；[NO_TASK_FILTER] 表示展示最新一条
     */
    fun attach(
        scope: CoroutineScope,
        bus: TransferProgressBus,
        filterTaskId: Long = NO_TASK_FILTER
    ) {
        detach()
        observeJob = scope.launch(AppDispatchers.main) {
            bus.flow.collect { progress ->
                if (filterTaskId == NO_TASK_FILTER || progress.taskId == filterTaskId) {
                    bind(progress)
                }
            }
        }
    }

    /** 取消订阅。 */
    fun detach() {
        observeJob?.cancel()
        observeJob = null
    }

    override fun onDetachedFromWindow() {
        detach()
        super.onDetachedFromWindow()
    }

    // ==================== 绑定数据 ====================

    /**
     * 绑定一条进度事件。
     *
     * @param progress 进度事件
     * @param fileName 展示用文件名；为空时取 [TransferProgress.key] 的末段
     */
    fun bind(progress: TransferProgress, fileName: String? = null) {
        applyDirection(progress.direction)

        val percent = if (progress.total > 0) {
            ((progress.transferred * 100) / progress.total).toInt().coerceIn(0, 100)
        } else {
            0
        }
        applyStatus(progress.status)

        titleView.text = fileName?.takeIf { it.isNotBlank() } ?: progress.key.substringAfterLast('/')
        tagView.text = percentLabel(percent)
        progressBar.progress = percent
        bytesView.text = bytesLine(percent, progress.transferred, progress.total)

        val eta = ByteFormat.eta(progress.etaSeconds)
        speedView.text = if (eta.isEmpty()) {
            ByteFormat.speed(progress.bytesPerSecond)
        } else {
            context.getString(R.string.transfer_speed_eta, ByteFormat.speed(progress.bytesPerSecond), eta)
        }
    }

    /** 手动设置标题（覆盖文件名）。 */
    fun setTitle(text: CharSequence) {
        titleView.text = text
    }

    /** 手动设置状态标签文字（覆盖百分比）。 */
    fun setStatusTag(text: CharSequence) {
        tagView.text = text
    }

    /**
     * 设置主动作（如「查看全部任务」）。
     *
     * @param textRes 文案资源；为 null 时隐藏
     * @param listener 点击回调
     */
    fun setActionPrimary(@StringRes textRes: Int?, listener: (() -> Unit)?) {
        bindAction(primaryButton, textRes, listener)
        refreshActionsVisibility()
    }

    /**
     * 设置危险 / 次动作（如「取消」）。
     *
     * @param textRes 文案资源；为 null 时隐藏
     * @param listener 点击回调
     */
    fun setActionDanger(@StringRes textRes: Int?, listener: (() -> Unit)?) {
        bindAction(dangerButton, textRes, listener)
        refreshActionsVisibility()
    }

    // ==================== 内部 ====================

    private fun applyDirection(direction: TransferDirection) {
        val (iconRes, colorRes) = when (direction) {
            TransferDirection.UPLOAD -> R.drawable.ic_up to R.color.brand
            TransferDirection.DOWNLOAD -> R.drawable.ic_down to R.color.blue
        }
        directionIcon.setImageResource(iconRes)
        directionIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
    }

    private fun applyStatus(status: TransferStatus) {
        val colorRes = when (status) {
            TransferStatus.QUEUED -> R.color.ink_tertiary
            TransferStatus.RUNNING -> R.color.primary
            TransferStatus.DONE -> R.color.success
            TransferStatus.FAILED -> R.color.error
            TransferStatus.CANCELLED -> R.color.ink_tertiary
        }
        val color = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
        progressBar.progressTintList = color
        tagView.setTextColor(color)
    }

    private fun bindAction(button: MaterialButton, @StringRes textRes: Int?, listener: (() -> Unit)?) {
        if (textRes == null || listener == null) {
            button.visibility = View.GONE
            button.setOnClickListener(null)
        } else {
            button.text = context.getString(textRes)
            button.visibility = View.VISIBLE
            button.setOnClickListener { listener() }
        }
    }

    private fun refreshActionsVisibility() {
        actionsContainer.visibility =
            if (primaryButton.visibility == View.VISIBLE || dangerButton.visibility == View.VISIBLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    /** 百分比标签（纯数字 + 符号，无需文案资源）。 */
    private fun percentLabel(percent: Int): String = "$percent%"

    /** 字节明细行：`42% · 10.3 MB / 24.6 MB`（纯数字 + 符号）。 */
    private fun bytesLine(percent: Int, transferred: Long, total: Long): String =
        "$percent% $DOT ${ByteFormat.size(transferred)} $SLASH ${ByteFormat.size(total)}"

    companion object {
        /** 不做任务过滤，展示最新一条进度。 */
        const val NO_TASK_FILTER: Long = -1L

        private const val DOT = "\u00B7"
        private const val SLASH = "/"
    }
}
