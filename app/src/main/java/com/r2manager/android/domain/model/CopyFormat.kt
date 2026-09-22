package com.r2manager.android.domain.model

/**
 * 复制链接格式。
 */
enum class CopyFormat {
    /** 纯 URL。 */
    URL,

    /** HTML `<a>` 片段。 */
    HTML,

    /** Markdown 链接。 */
    MARKDOWN
}
