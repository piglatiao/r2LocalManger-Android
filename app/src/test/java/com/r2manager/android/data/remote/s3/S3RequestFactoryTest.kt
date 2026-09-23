package com.r2manager.android.data.remote.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 校验 S3 请求的虚拟主机、路径和查询串与签名输入保持一致。 */
class S3RequestFactoryTest {

    private val config = S3Config(
        endpoint = "https://account.r2.cloudflarestorage.com",
        region = "auto",
        bucket = "bucket",
        accessKeyId = "access-key",
        secretAccessKey = "secret-key"
    )

    private val factory = S3RequestFactory(
        config,
        Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC)
    )

    @Test
    fun listObjectsUsesBucketHostAndRootPath() {
        val request = factory.listObjectsV2("", "/", null, 1)

        assertEquals("bucket.account.r2.cloudflarestorage.com", request.url.host)
        assertEquals("/", request.url.encodedPath)
        assertEquals(
            "delimiter=%2F&list-type=2&max-keys=1&prefix=",
            request.url.encodedQuery
        )
        assertTrue(request.header("Authorization").orEmpty().contains("SignedHeaders=host;"))
    }

    @Test
    fun objectPathDoesNotRepeatBucketInUrl() {
        val request = factory.getObject("dir/a b.txt")

        assertEquals("bucket.account.r2.cloudflarestorage.com", request.url.host)
        assertEquals("/dir/a%20b.txt", request.url.encodedPath)
    }
}
