package com.r2manager.android.core.mime

/**
 * MIME 类型唯一来源。
 *
 * 扩展名映射逐字照搬桌面版 `R2Client.js` 的 `MIME_TYPE_BY_EXTENSION`（42 条），
 * 上传写入 `ContentType` 的规则：显式 MIME → 扩展名映射 → `application/octet-stream`。
 */
object MimeTypes {

    /** 内联预览的 MIME 前缀。 */
    val INLINE_PREFIXES: List<String> = listOf("image/", "video/", "text/")

    /** 内联预览的精确 MIME。 */
    val INLINE_EXACT: Set<String> = setOf(
        "application/pdf",
        "application/json",
        "application/xml",
        "application/javascript",
        "application/typescript",
        "application/x-yaml"
    )

    /** 兜底 MIME。 */
    const val DEFAULT_CONTENT_TYPE: String = "application/octet-stream"

    /** 扩展名 → MIME（42 条，逐字对齐桌面版）。 */
    val MIME_BY_EXTENSION: Map<String, String> = mapOf(
        // 图片
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "bmp" to "image/bmp",
        "ico" to "image/x-icon",
        // 视频
        "mp4" to "video/mp4",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        // 音频
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
        // 文档
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        // 文本与代码
        "txt" to "text/plain",
        "md" to "text/markdown",
        "json" to "application/json",
        "xml" to "application/xml",
        "html" to "text/html",
        "css" to "text/css",
        "js" to "application/javascript",
        "ts" to "application/typescript",
        "yaml" to "application/x-yaml",
        "yml" to "application/x-yaml",
        // 压缩
        "zip" to "application/zip",
        "rar" to "application/x-rar-compressed",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip"
    )

    /**
     * 解析对象 MIME 类型：显式优先 → 扩展名映射 → 兜底。
     *
     * @param key 对象 key
     * @param provided 外部已知（显式）的 MIME 类型
     */
    fun resolveContentType(key: String, provided: String? = null): String {
        val normalized = provided?.trim().orEmpty()
        if (normalized.isNotEmpty()) {
            return normalized
        }
        return MIME_BY_EXTENSION[FileTypes.extensionOf(key)] ?: DEFAULT_CONTENT_TYPE
    }

    /**
     * 判断某 MIME 是否适合写入 `ContentDisposition: inline`（便于浏览器直接预览）。
     *
     * @param contentType MIME 类型
     */
    fun shouldUseInlineDisposition(contentType: String): Boolean {
        val normalized = contentType.trim().lowercase()
        if (normalized.isEmpty()) {
            return false
        }
        if (INLINE_EXACT.contains(normalized)) {
            return true
        }
        return INLINE_PREFIXES.any { normalized.startsWith(it) }
    }
}
