package com.r2manager.android.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [UrlUtils] 单元测试（P1）。
 */
class UrlUtilsTest {

    @Test
    fun `normalizeUrl 补协议并去尾斜杠`() {
        assertEquals("https://example.com", UrlUtils.normalizeUrl("example.com/"))
        assertEquals("http://example.com", UrlUtils.normalizeUrl("http://example.com/"))
        assertEquals("https://a.example.com/x", UrlUtils.normalizeUrl(" https://a.example.com/x/ "))
    }

    @Test
    fun `normalizeUrl 空串处理`() {
        assertEquals("", UrlUtils.normalizeUrl(null))
        assertEquals("", UrlUtils.normalizeUrl("   ", allowEmpty = true))
    }

    @Test
    fun `buildEndpoint 由 accountId 派生`() {
        assertEquals(
            "https://abc.r2.cloudflarestorage.com",
            UrlUtils.buildEndpoint("abc")
        )
        assertEquals("fallback", UrlUtils.buildEndpoint("", "fallback"))
    }

    @Test
    fun `inferAccountIdFromEndpoint 识别合法首段`() {
        val id = "0123456789abcdef"
        assertEquals(id, UrlUtils.inferAccountIdFromEndpoint("https://$id.r2.cloudflarestorage.com"))
        // 首段过短 → 无法识别
        assertEquals("", UrlUtils.inferAccountIdFromEndpoint("https://short.r2.cloudflarestorage.com"))
        // 含大写 → 无法识别
        assertEquals("", UrlUtils.inferAccountIdFromEndpoint("https://0123456789ABCDEF.r2.cloudflarestorage.com"))
        assertEquals("", UrlUtils.inferAccountIdFromEndpoint(""))
    }

    @Test
    fun `joinPublicUrl 去尾斜杠接 key`() {
        assertEquals(
            "https://cdn.example.com/a/b.png",
            UrlUtils.joinPublicUrl("https://cdn.example.com/", "a/b.png")
        )
        assertEquals(
            "https://cdn.example.com/a/b.png",
            UrlUtils.joinPublicUrl("https://cdn.example.com", "/a/b.png")
        )
    }

    @Test
    fun `stripQuery 剥离查询串`() {
        assertEquals(
            "https://cdn.example.com/a.png",
            UrlUtils.stripQuery("https://cdn.example.com/a.png?X-Amz-Signature=abc&X-Amz-Credential=def")
        )
        assertEquals("https://cdn.example.com/a.png", UrlUtils.stripQuery("https://cdn.example.com/a.png"))
    }
}
