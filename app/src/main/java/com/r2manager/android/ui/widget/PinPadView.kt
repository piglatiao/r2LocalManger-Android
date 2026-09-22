package com.r2manager.android.ui.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.r2manager.android.R

/**
 * PIN 键盘视图（应用锁解锁 / 首次设置密码 / 找回密码）。
 *
 * 结构（`view_pin_pad.xml`，归 P4-C）：
 * `[● ● ● ○ ○ ○]` 圆点行 + `1 2 3 / 4 5 6 / 7 8 9 / 生物识别 0 ⌫` 宫格。
 *
 * 设计要点：
 * - 圆点与键位**全部用代码构建**（键位为符号，避免硬编码中文文案）；
 * - 生物识别键默认使用锁图标，可由调用方 [setBiometricIconRes] 覆盖；
 * - 输入缓冲与回调内置，[onPinComplete] 在长度达到 [setPinLength] 时触发。
 */
class PinPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dotsContainer: LinearLayout
    private val keysContainer: LinearLayout
    private val dots = ArrayList<View>()

    private val buffer = StringBuilder()

    private var biometricCell: View? = null
    private var biometricIcon: ImageView? = null

    /** 密码位数（默认 6）。 */
    var pinLength: Int = DEFAULT_PIN_LENGTH
        private set

    /** 数字键回调（每次输入一个数字）。 */
    var onDigit: ((Char) -> Unit)? = null

    /** 输入变化回调（含退格），参数为当前缓冲内容。 */
    var onPinChanged: ((String) -> Unit)? = null

    /** 输入长度达到 [pinLength] 时回调。 */
    var onPinComplete: ((String) -> Unit)? = null

    /** 生物识别键回调。 */
    var onBiometric: (() -> Unit)? = null

    /** 退格键回调。 */
    var onBackspace: (() -> Unit)? = null

    /** 当前已输入的位数。 */
    val enteredLength: Int
        get() = buffer.length

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.view_pin_pad, this, true)
        dotsContainer = findViewById(R.id.pin_dots)
        keysContainer = findViewById(R.id.pin_keys)
        rebuildDots()
        buildKeys()
        updateDots()
    }

    // ==================== 对外 API ====================

    /** 设置密码位数并重建圆点（会清空当前输入）。 */
    fun setPinLength(length: Int) {
        require(length > 0) { "pinLength must be > 0" }
        pinLength = length
        clear()
        rebuildDots()
    }

    /** 覆盖生物识别键图标。 */
    fun setBiometricIconRes(@DrawableRes drawableRes: Int) {
        biometricIcon?.setImageResource(drawableRes)
    }

    /**
     * 是否显示生物识别键。
     *
     * @param enabled true 显示且可点；false 隐藏占位（保持宫格对齐）
     */
    fun setBiometricEnabled(enabled: Boolean) {
        biometricCell?.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    /** 清空输入与圆点。 */
    fun clear() {
        buffer.setLength(0)
        updateDots()
    }

    // ==================== 内部：输入逻辑 ====================

    private fun appendDigit(digit: Char) {
        if (buffer.length >= pinLength) {
            return
        }
        buffer.append(digit)
        onDigit?.invoke(digit)
        updateDots()
        onPinChanged?.invoke(buffer.toString())
        if (buffer.length == pinLength) {
            onPinComplete?.invoke(buffer.toString())
        }
    }

    private fun removeDigit() {
        if (buffer.isNotEmpty()) {
            buffer.setLength(buffer.length - 1)
            updateDots()
            onPinChanged?.invoke(buffer.toString())
        }
        onBackspace?.invoke()
    }

    // ==================== 内部：视图构建 ====================

    private fun rebuildDots() {
        dotsContainer.removeAllViews()
        dots.clear()
        val dotSize = resources.getDimensionPixelSize(R.dimen.space_3)
        val gap = resources.getDimensionPixelSize(R.dimen.space_2)
        for (index in 0 until pinLength) {
            val dot = View(context).apply {
                layoutParams = LayoutParams(dotSize, dotSize).apply {
                    marginStart = gap
                    marginEnd = gap
                }
            }
            dots.add(dot)
            dotsContainer.addView(dot)
        }
    }

    private fun updateDots() {
        val filled = buffer.length
        for ((index, dot) in dots.withIndex()) {
            dot.background = dotDrawable(filled = index < filled)
        }
    }

    private fun buildKeys() {
        keysContainer.removeAllViews()
        val rowHeight = resources.getDimensionPixelSize(R.dimen.appbar_height)
        for (row in 0 until KEY_ROWS) {
            val rowView = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, rowHeight)
            }
            for (column in 0 until KEY_COLUMNS) {
                val cell = createCell(row, column, rowHeight)
                rowView.addView(
                    cell,
                    LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                )
            }
            keysContainer.addView(rowView)
        }
    }

    private fun createCell(row: Int, column: Int, cellHeight: Int): View {
        return when {
            row < DIGIT_ROWS -> createDigitCell((row * KEY_COLUMNS + column + 1).toString(), cellHeight)
            column == COLUMN_BIOMETRIC -> createBiometricCell(cellHeight)
            column == COLUMN_BACKSPACE -> createBackspaceCell(cellHeight)
            else -> createDigitCell("0", cellHeight)
        }
    }

    private fun createDigitCell(text: String, cellHeight: Int): View {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_page_title))
            setTextColor(ContextCompat.getColor(context, R.color.ink_primary))
            background = ContextCompat.getDrawable(context, R.drawable.item_ripple)
            isClickable = true
            isFocusable = true
            minHeight = cellHeight
            contentDescription = text
            setOnClickListener { appendDigit(text[0]) }
        }
    }

    private fun createBackspaceCell(cellHeight: Int): View {
        return TextView(context).apply {
            text = BACKSPACE_SYMBOL
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_page_title))
            setTextColor(ContextCompat.getColor(context, R.color.ink_primary))
            background = ContextCompat.getDrawable(context, R.drawable.item_ripple)
            isClickable = true
            isFocusable = true
            minHeight = cellHeight
            setOnClickListener { removeDigit() }
        }
    }

    private fun createBiometricCell(cellHeight: Int): View {
        val image = ImageView(context).apply {
            setImageResource(R.drawable.ic_lock)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.primary)
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = ContextCompat.getDrawable(context, R.drawable.item_ripple)
            isClickable = true
            isFocusable = true
            setOnClickListener { onBiometric?.invoke() }
        }
        biometricIcon = image
        biometricCell = image
        val params = LayoutParams(LayoutParams.MATCH_PARENT, cellHeight)
        image.layoutParams = params
        return image
    }

    private fun dotDrawable(filled: Boolean): GradientDrawable {
        val size = resources.getDimensionPixelSize(R.dimen.space_3)
        val stroke = resources.getDimensionPixelSize(R.dimen.stroke_thin)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(size, size)
            if (filled) {
                setColor(ContextCompat.getColor(context, R.color.brand))
            } else {
                setColor(ContextCompat.getColor(context, R.color.transparent))
                setStroke(stroke, ContextCompat.getColor(context, R.color.ink_4))
            }
        }
    }

    private companion object {
        /** 默认密码位数（与原型 PIN 圆点数一致）。 */
        const val DEFAULT_PIN_LENGTH = 6

        const val KEY_ROWS = 4
        const val KEY_COLUMNS = 3
        const val DIGIT_ROWS = 3
        const val COLUMN_BIOMETRIC = 0
        const val COLUMN_BACKSPACE = 2

        /** 退格符号（语言无关）。 */
        const val BACKSPACE_SYMBOL = "\u232B"
    }
}
