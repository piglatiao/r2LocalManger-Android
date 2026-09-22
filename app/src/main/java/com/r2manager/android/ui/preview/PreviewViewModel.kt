package com.r2manager.android.ui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.viewModelScope
import com.r2manager.android.AppContainer
import com.r2manager.android.core.constants.TransferConstants
import com.r2manager.android.core.error.ErrorMapper
import com.r2manager.android.core.mime.PreviewKind
import com.r2manager.android.data.repository.PreviewData
import com.r2manager.android.domain.model.ObjectMeta
import com.r2manager.android.ui.common.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * 预览页 UI 状态。
 */
sealed interface PreviewUiState {

    /** 加载中。 */
    data object Loading : PreviewUiState

    /** 图片（已解码，含可选缩略图占位）。 */
    data class Image(val bitmap: Bitmap?, val meta: ObjectMeta) : PreviewUiState

    /** 文本。 */
    data class Text(val content: String, val meta: ObjectMeta) : PreviewUiState

    /** PDF（已落地缓存文件，供 PdfRenderer 随机读取）。 */
    data class Pdf(val file: File, val meta: ObjectMeta) : PreviewUiState

    /** 视频（已落地缓存文件，供 framework VideoView 播放）。 */
    data class Video(val file: File, val meta: ObjectMeta) : PreviewUiState

    /** 信息卡（其它类型 / 不可内联预览）。 */
    data class Info(val meta: ObjectMeta, val publicUrl: String) : PreviewUiState

    /** 超出内联预览上限。 */
    data class TooLarge(val meta: ObjectMeta, val kind: PreviewKind) : PreviewUiState

    /** 失败。 */
    data class Error(val error: com.r2manager.android.core.error.AppError) : PreviewUiState
}

/**
 * 预览页 ViewModel。
 *
 * 依据 [PreviewKind] 走不同加载路径（见架构 §5.5）：
 * - IMAGE：下载并降采样解码为 [Bitmap]；
 * - TEXT：≤2MB 直读 UTF-8，超出返回 [PreviewUiState.TooLarge]；
 * - PDF：≤25MB 落地缓存文件，交给 `PdfRenderer` 逐页渲染；
 * - VIDEO：落地缓存文件，交给 framework `VideoView`（不引入 media3）；
 * - OTHER / 视频信息：展示 [PreviewUiState.Info] 信息卡。
 *
 * @param container 依赖容器
 */
class PreviewViewModel(container: AppContainer) : AppViewModel(container) {

    private val _state = MutableStateFlow<PreviewUiState>(PreviewUiState.Loading)
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    private var currentKey: String = ""

    /**
     * 打开预览。
     *
     * @param key 对象 key
     * @param kind 预览分组
     */
    fun open(key: String, kind: PreviewKind) {
        currentKey = key
        appScope.launch {
            _state.value = PreviewUiState.Loading
            runCatching { load(key, kind) }
                .onSuccess { _state.value = it }
                .onFailure { _state.value = PreviewUiState.Error(ErrorMapper.fromThrowable(it, "preview")) }
        }
    }

    /** 重新加载（错误态重试）。 */
    fun retry(kind: PreviewKind) {
        if (currentKey.isNotBlank()) {
            open(currentKey, kind)
        }
    }

    /** 对象公开链接。 */
    fun publicUrlOf(key: String): String = runCatching {
        container.storageRepository.publicUrl(key)
    }.getOrDefault("")

    private suspend fun load(key: String, kind: PreviewKind): PreviewUiState {
        val repo = container.storageRepository
        return when (kind) {
            PreviewKind.IMAGE -> {
                val (stream, meta) = repo.openObjectStream(key)
                PreviewUiState.Image(decodeImage(stream), meta)
            }

            PreviewKind.TEXT -> when (val data = repo.preview(key)) {
                is PreviewData.Text -> PreviewUiState.Text(data.content, data.meta)
                is PreviewData.TooLarge -> PreviewUiState.TooLarge(data.meta, kind)
                is PreviewData.Binary -> {
                    val text = data.stream.use { it.readBytes().toString(Charsets.UTF_8) }
                    PreviewUiState.Text(text, data.meta)
                }
            }

            PreviewKind.PDF -> {
                val (stream, meta) = repo.openObjectStream(key)
                if (meta.contentLength > TransferConstants.PDF_PREVIEW_MAX_BYTES) {
                    stream.close()
                    PreviewUiState.TooLarge(meta, kind)
                } else {
                    PreviewUiState.Pdf(materialize(stream, key, "pdf"), meta)
                }
            }

            PreviewKind.VIDEO -> {
                val (stream, meta) = repo.openObjectStream(key)
                PreviewUiState.Video(materialize(stream, key, "mp4"), meta)
            }

            PreviewKind.OTHER -> {
                val meta = repo.head(key)
                PreviewUiState.Info(meta, repo.publicUrl(key))
            }
        }
    }

    private suspend fun decodeImage(stream: InputStream): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = runCatching { stream.use { it.readBytes() } }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) {
            return@withContext null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val maxEdge = max(bounds.outWidth, bounds.outHeight)
        while (maxEdge / (sample * 2) >= DECODE_MAX_EDGE_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    }

    private suspend fun materialize(stream: InputStream, key: String, suffix: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(container.appContext.cacheDir, "preview").apply { mkdirs() }
            val file = File(dir, "p_${System.currentTimeMillis()}_${key.hashCode()}.$suffix")
            stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            file
        }

    private companion object {
        /** 图片预览解码目标最长边（避免超大图 OOM）。 */
        const val DECODE_MAX_EDGE_PX = 2048
    }
}
