package com.r2manager.android.ui.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.r2manager.android.databinding.ItemPdfPageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * PDF 逐页适配器：用 framework [PdfRenderer] 渲染每页为位图（不引入第三方库）。
 *
 * - 按需渲染（进入视口时渲染，渲染结果缓存）；
 * - [PdfRenderer] 非线程安全，渲染在单线程协程 + 同步块内完成；
 * - 页面回收时释放已缓存位图。
 *
 * @param file PDF 缓存文件
 */
class PdfPageAdapter(private val file: File) : RecyclerView.Adapter<PdfPageAdapter.PageHolder>() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = SparseArray<Bitmap>()

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var pageCount = 0
    private val renderLock = Any()

    init {
        open()
    }

    private fun open() {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor = pfd
            val r = PdfRenderer(pfd)
            renderer = r
            pageCount = r.pageCount
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder =
        PageHolder(ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.bind(position)
    }

    override fun onViewRecycled(holder: PageHolder) {
        holder.release()
        super.onViewRecycled(holder)
    }

    /** 释放渲染器与位图（Fragment 销毁时调用）。 */
    fun close() {
        scope.cancel()
        for (i in 0 until cache.size()) {
            cache.valueAt(i).recycle()
        }
        cache.clear()
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }

    private fun render(pageIndex: Int) {
        if (cache.get(pageIndex) != null) {
            return
        }
        scope.launch {
            val bitmap = synchronized(renderLock) {
                runCatching {
                    val r = renderer ?: return@runCatching null
                    r.openPage(pageIndex).use { page ->
                        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }.getOrNull()
            }
            if (bitmap != null) {
                cache.put(pageIndex, bitmap)
                mainHandler.post { notifyItemChanged(pageIndex) }
            }
        }
    }

    /** 页视图。 */
    inner class PageHolder(private val binding: ItemPdfPageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pageIndex: Int) {
            val cached = cache.get(pageIndex)
            if (cached != null) {
                binding.pdfPageProgress.isVisible = false
                binding.pdfPageImage.isVisible = true
                binding.pdfPageImage.setImageBitmap(cached)
            } else {
                binding.pdfPageProgress.isVisible = true
                binding.pdfPageImage.isVisible = false
                render(pageIndex)
            }
            binding.pdfPageImage.setOnClickListener { /* 预留：点击放大 */ }
        }

        fun release() {
            binding.pdfPageImage.setImageDrawable(null)
        }
    }
}
