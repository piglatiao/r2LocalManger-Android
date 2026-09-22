package com.r2manager.android.core.mime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MimeTypes] / [FileTypes] 单元测试（P1）。
 */
class MimeTypesTest {

    @Test
    fun `扩展名映射表共 42 条`() {
        assertEquals(42, MimeTypes.MIME_BY_EXTENSION.size)
    }

    @Test
    fun `关键扩展名映射正确`() {
        assertEquals("image/jpeg", MimeTypes.MIME_BY_EXTENSION["jpg"])
        assertEquals("application/pdf", MimeTypes.MIME_BY_EXTENSION["pdf"])
        assertEquals("application/x-yaml", MimeTypes.MIME_BY_EXTENSION["yml"])
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            MimeTypes.MIME_BY_EXTENSION["xlsx"]
        )
        assertEquals("application/x-7z-compressed", MimeTypes.MIME_BY_EXTENSION["7z"])
    }

    @Test
    fun `resolveContentType 显式优先`() {
        assertEquals("image/webp", MimeTypes.resolveContentType("photo.bin", "image/webp"))
    }

    @Test
    fun `resolveContentType 扩展名映射`() {
        assertEquals("image/png", MimeTypes.resolveContentType("dir/photo.PNG"))
        assertEquals("application/json", MimeTypes.resolveContentType("a/b/data.json"))
    }

    @Test
    fun `resolveContentType 未知扩展名兜底`() {
        assertEquals(MimeTypes.DEFAULT_CONTENT_TYPE, MimeTypes.resolveContentType("weird.unknown"))
        assertEquals(MimeTypes.DEFAULT_CONTENT_TYPE, MimeTypes.resolveContentType("noext"))
    }

    @Test
    fun `shouldUseInlineDisposition 前缀与精确类型`() {
        assertTrue(MimeTypes.shouldUseInlineDisposition("image/png"))
        assertTrue(MimeTypes.shouldUseInlineDisposition("video/mp4"))
        assertTrue(MimeTypes.shouldUseInlineDisposition("text/plain"))
        assertTrue(MimeTypes.shouldUseInlineDisposition("application/pdf"))
        assertTrue(MimeTypes.shouldUseInlineDisposition("application/x-yaml"))
        assertFalse(MimeTypes.shouldUseInlineDisposition("application/zip"))
        assertFalse(MimeTypes.shouldUseInlineDisposition("application/octet-stream"))
        assertFalse(MimeTypes.shouldUseInlineDisposition(""))
    }

    @Test
    fun `extensionOf 处理大小写与路径`() {
        assertEquals("png", FileTypes.extensionOf("a/b/Pic.PNG"))
        assertEquals("", FileTypes.extensionOf("folder/"))
        assertEquals("", FileTypes.extensionOf("noext"))
        assertEquals("", FileTypes.extensionOf(".gitignore"))
    }

    @Test
    fun `kindOf 分组正确`() {
        assertEquals(PreviewKind.IMAGE, FileTypes.kindOf("a/img.jpeg"))
        assertEquals(PreviewKind.VIDEO, FileTypes.kindOf("a/clip.webm"))
        assertEquals(PreviewKind.PDF, FileTypes.kindOf("a/doc.pdf"))
        assertEquals(PreviewKind.TEXT, FileTypes.kindOf("a/log-20240115.log"))
        assertEquals(PreviewKind.OTHER, FileTypes.kindOf("a/setup.exe"))
    }
}
