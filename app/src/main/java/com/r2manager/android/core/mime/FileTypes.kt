package com.r2manager.android.core.mime

/**
 * 预览类型分组。
 */
enum class PreviewKind {
    /** 图片：可缩放预览。 */
    IMAGE,

    /** 视频：VideoView 播放。 */
    VIDEO,

    /** PDF：PdfRenderer 逐页渲染。 */
    PDF,

    /** 文本 / 代码：等宽字体展示。 */
    TEXT,

    /** 其它：信息卡 + "下载后打开"。 */
    OTHER
}

/**
 * 文件类型判定唯一来源（对应 `docs/02 §6` 的预览分组）。
 */
object FileTypes {

    /** 图片扩展名。 */
    val IMAGE_EXTS: List<String> = listOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico")

    /** 视频扩展名。 */
    val VIDEO_EXTS: List<String> = listOf("mp4", "avi", "mov", "wmv", "flv", "mkv", "webm")

    /** PDF 扩展名。 */
    val PDF_EXTS: List<String> = listOf("pdf")

    /** 文本 / 代码扩展名。 */
    val TEXT_EXTS: List<String> = listOf("txt", "md", "json", "xml", "html", "css", "js", "ts", "yaml", "yml", "log")

    /**
     * 取对象 key 的扩展名（小写，不含点）。文件夹（以 `/` 结尾）或无扩展名返回空串。
     *
     * @param key 对象 key
     */
    fun extensionOf(key: String): String {
        val trimmed = key.trim().trimEnd('/')
        val slash = trimmed.lastIndexOf('/')
        val name = if (slash >= 0) trimmed.substring(slash + 1) else trimmed
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) {
            return ""
        }
        return name.substring(dot + 1).lowercase()
    }

    /**
     * 按扩展名判定预览分组。
     *
     * @param key 对象 key
     */
    fun kindOf(key: String): PreviewKind {
        val ext = extensionOf(key)
        return when (ext) {
            in IMAGE_EXTS -> PreviewKind.IMAGE
            in VIDEO_EXTS -> PreviewKind.VIDEO
            in PDF_EXTS -> PreviewKind.PDF
            in TEXT_EXTS -> PreviewKind.TEXT
            else -> PreviewKind.OTHER
        }
    }
}
