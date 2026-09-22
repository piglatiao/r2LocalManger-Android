package com.r2manager.android.ui.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.min

/**
 * 可缩放图片视图（预览页图片用，`docs/05 §6.3` 明确不引入 Coil/PhotoView）。
 *
 * 交互：
 * - **双指缩放**：[ScaleGestureDetector]，相对基准（fit-center）缩放，范围 [minScale, maxScale]；
 * - **双击**：在基准与 [doubleTapScale] 之间切换；
 * - **拖动**：放大后单指平移，边界自动回夹（[checkMatrixBounds]）；
 * - **下拉关闭**：基准缩放下单指下滑超过阈值触发 [onSwipeDownToSwipeDismiss]（供预览页 finish）。
 *
 * 通过 [Matrix] 组合实现：最终矩阵 = `baseMatrix(fit-center) × suppMatrix(用户变换)`。
 * 使用前无需特殊设置；首次布局与图片设置时自动计算基准矩阵。
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val baseMatrix = Matrix()
    private val suppMatrix = Matrix()
    private val drawMatrix = Matrix()

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private val touchSlop: Int

    private var mode = MODE_NONE
    private var lastX = 0f
    private var lastY = 0f
    private var dragStartY = 0f
    private var swiping = false

    /** 最小缩放（相对基准）。 */
    var minScale: Float = MIN_SCALE

    /** 最大缩放（相对基准）。 */
    var maxScale: Float = MAX_SCALE

    /** 双击放大的目标倍数。 */
    var doubleTapScale: Float = DOUBLE_TAP_SCALE

    /** 下拉关闭回调（基准缩放下单指下滑超过阈值时触发）。 */
    var onSwipeDownToSwipeDismiss: (() -> Unit)? = null

    /** 单击回调（如切换预览工具条显隐）。 */
    var onSingleTap: (() -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    return this@ZoomableImageView.onScale(detector)
                }

                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
            }
        )
        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    handleDoubleTap(e.x, e.y)
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    performClick()
                    onSingleTap?.invoke()
                    return true
                }
            }
        )
    }

    // ==================== 对外 API ====================

    /** 重置为基准缩放（fit-center）。 */
    fun resetZoom() {
        suppMatrix.reset()
        applyMatrix()
    }

    /** 当前缩放倍数（相对基准，基准 = 1f）。 */
    fun currentScale(): Float {
        val values = FloatArray(9)
        suppMatrix.getValues(values)
        return if (values[Matrix.MSCALE_X] <= 0f) MIN_SCALE else values[Matrix.MSCALE_X]
    }

    // ==================== 触摸交互 ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragStartY = event.y
                swiping = false
                mode = if (currentScale() > MIN_SCALE + EPSILON) MODE_PAN else MODE_DRAG
                if (mode == MODE_PAN) {
                    disallowParentIntercept(true)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                mode = MODE_NONE
                swiping = false
                disallowParentIntercept(true)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 多指抬起一指后重设基准点，避免剩余手指的 MOVE 产生跳变。
                lastX = event.x
                lastY = event.y
                mode = if (currentScale() > MIN_SCALE + EPSILON) MODE_PAN else MODE_DRAG
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && mode != MODE_NONE) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    when (mode) {
                        MODE_PAN -> {
                            suppMatrix.postTranslate(dx, dy)
                            checkMatrixBounds()
                            applyMatrix()
                        }

                        MODE_DRAG -> {
                            if (!swiping && dy > touchSlop && dy > abs(dx)) {
                                swiping = true
                            }
                            if (swiping) {
                                suppMatrix.postTranslate(0f, dy)
                                applyMatrix()
                                val threshold = swipeDismissThreshold()
                                val progress = ((event.y - dragStartY) / threshold).coerceIn(0f, 1f)
                                alpha = 1f - SWIPE_ALPHA_REDUCTION * progress
                            }
                        }
                    }
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                if (swiping) {
                    if (event.y - dragStartY > swipeDismissThreshold()) {
                        onSwipeDownToSwipeDismiss?.invoke()
                    } else {
                        resetZoom()
                    }
                }
                finishGesture()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (swiping) {
                    resetZoom()
                }
                finishGesture()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    // ==================== 矩阵计算 ====================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        suppMatrix.reset()
        configureBaseMatrix()
        applyMatrix()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        suppMatrix.reset()
        configureBaseMatrix()
        applyMatrix()
    }

    private fun onScale(detector: ScaleGestureDetector): Boolean {
        val current = currentScale()
        val target = (current * detector.scaleFactor).coerceIn(minScale, maxScale)
        val factor = target / current
        suppMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
        checkMatrixBounds()
        applyMatrix()
        disallowParentIntercept(true)
        return true
    }

    private fun handleDoubleTap(x: Float, y: Float) {
        val current = currentScale()
        if (current > MIN_SCALE + EPSILON) {
            resetZoom()
        } else {
            val target = doubleTapScale.coerceIn(minScale, maxScale)
            val factor = target / current
            suppMatrix.postScale(factor, factor, x, y)
            checkMatrixBounds()
            applyMatrix()
        }
    }

    private fun configureBaseMatrix() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f || width == 0 || height == 0) {
            return
        }
        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = min(vw / dw, vh / dh)
        baseMatrix.reset()
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
    }

    private fun applyMatrix() {
        drawMatrix.setConcat(baseMatrix, suppMatrix)
        imageMatrix = drawMatrix
    }

    /** 边界回夹：图片小于视图时居中，大于视图时不允许露出空白。 */
    private fun checkMatrixBounds() {
        val rect = displayRect() ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val rectWidth = rect.width()
        val rectHeight = rect.height()

        var deltaX = 0f
        var deltaY = 0f

        if (rectHeight <= viewHeight) {
            deltaY = (viewHeight - rectHeight) / 2f - rect.top
        } else if (rect.top > 0f) {
            deltaY = -rect.top
        } else if (rect.bottom < viewHeight) {
            deltaY = viewHeight - rect.bottom
        }

        if (rectWidth <= viewWidth) {
            deltaX = (viewWidth - rectWidth) / 2f - rect.left
        } else if (rect.left > 0f) {
            deltaX = -rect.left
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right
        }

        suppMatrix.postTranslate(deltaX, deltaY)
    }

    private fun displayRect(): RectF? {
        val d = drawable ?: return null
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(rect)
        return rect
    }

    private fun swipeDismissThreshold(): Float = height * SWIPE_DISMISS_RATIO

    private fun finishGesture() {
        mode = MODE_NONE
        swiping = false
        alpha = 1f
        disallowParentIntercept(false)
    }

    private fun disallowParentIntercept(disallow: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(disallow)
    }

    private companion object {
        const val MODE_NONE = 0
        const val MODE_PAN = 1
        const val MODE_DRAG = 2

        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
        const val EPSILON = 0.01f

        /** 下拉关闭触发阈值（占视图高度比例）。 */
        const val SWIPE_DISMISS_RATIO = 0.18f

        /** 下拉过程中的最大透明度衰减。 */
        const val SWIPE_ALPHA_REDUCTION = 0.6f
    }
}
