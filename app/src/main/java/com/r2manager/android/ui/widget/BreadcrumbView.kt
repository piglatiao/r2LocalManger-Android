package com.r2manager.android.ui.widget

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.r2manager.android.R
import com.r2manager.android.core.util.PathUtils

/**
 * 面包屑导航视图（浏览页 `crumbar`）。
 *
 * 与 P4-A 的 `view_breadcrumb.xml` **职责切分**：该布局归 P4-A；本自定义 View 归 P4-C，
 * 因此本类**完全用代码构建**子视图，不引用也不创建 `view_breadcrumb.xml` / `item_breadcrumb.xml`。
 * （见 `docs/05 §7.5` 归属说明。）
 *
 * 结构：`[bucket] / seg1 / seg2 / …`，末段为当前目录（不可点），其余可点回跳。
 * 分段规则复用 P1 的 [PathUtils.breadcrumbSegments]，与缓存键 / 传输 key 的规范化保持一致。
 */
class BreadcrumbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val crumbBar: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private var onCrumbClickListener: ((prefix: String) -> Unit)? = null

    /** 当前绑定的目录前缀（规范化后，根目录为 `""`）。 */
    var currentPrefix: String = ""
        private set

    /** 当前桶名（可能为空）。 */
    var bucketName: String = ""
        private set

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        addView(
            crumbBar,
            LayoutParams(LayoutParams.WRAP_CONTENT, resources.getDimensionPixelSize(R.dimen.breadcrumb_height))
        )
        minimumHeight = resources.getDimensionPixelSize(R.dimen.breadcrumb_height)
    }

    /**
     * 注册面包屑点击回调。
     *
     * @param listener 回调参数为目标目录前缀（点击 bucket → `""`），由调用方决定如何导航
     */
    fun setOnCrumbClickListener(listener: ((prefix: String) -> Unit)?) {
        onCrumbClickListener = listener
    }

    /**
     * 绑定当前路径并重建面包屑。
     *
     * @param bucket 桶名（作为根节点显示；空时使用中性根符号）
     * @param prefix 当前目录前缀（可为 `""` = 桶根）
     */
    fun bind(bucket: String?, prefix: String) {
        bucketName = bucket.orEmpty()
        currentPrefix = PathUtils.normalizePrefix(prefix)
        crumbBar.removeAllViews()

        val segments = PathUtils.breadcrumbSegments(currentPrefix)
        val rootLabel = bucketName.ifBlank { ROOT_SYMBOL }
        addCrumb(rootLabel, isCurrent = segments.isEmpty(), targetPrefix = "")

        for ((index, segment) in segments.withIndex()) {
            addSeparator()
            val name = segment.substringAfterLast('/')
            addCrumb(name, isCurrent = index == segments.lastIndex, targetPrefix = segment)
        }

        // 目录较深时优先展示当前层（右对齐）。
        post { fullScroll(View.FOCUS_RIGHT) }
    }

    /** 清空面包屑。 */
    fun clear() {
        crumbBar.removeAllViews()
        bucketName = ""
        currentPrefix = ""
    }

    // ==================== 内部：视图构建 ====================

    private fun addCrumb(label: String, isCurrent: Boolean, targetPrefix: String) {
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.space_2)
        val textView = TextView(context).apply {
            text = label
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_list_subtitle))
            setTextColor(
                ContextCompat.getColor(context, if (isCurrent) R.color.ink_primary else R.color.ink_secondary)
            )
            typeface = if (isCurrent) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            gravity = Gravity.CENTER_VERTICAL
            minHeight = resources.getDimensionPixelSize(R.dimen.breadcrumb_height)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            contentDescription = label
        }
        if (isCurrent) {
            textView.isClickable = false
            textView.isFocusable = false
        } else {
            textView.background = ContextCompat.getDrawable(context, R.drawable.item_ripple)
            textView.isClickable = true
            textView.isFocusable = true
            textView.setOnClickListener { onCrumbClickListener?.invoke(targetPrefix) }
        }
        crumbBar.addView(
            textView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addSeparator() {
        val separator = TextView(context).apply {
            text = SEPARATOR
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_list_subtitle))
            setTextColor(ContextCompat.getColor(context, R.color.ink_4))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        crumbBar.addView(
            separator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private companion object {
        /** 层级分隔符。 */
        const val SEPARATOR = "/"

        /** 无桶名时的中性根符号（避免语言依赖）。 */
        const val ROOT_SYMBOL = "/"
    }
}
