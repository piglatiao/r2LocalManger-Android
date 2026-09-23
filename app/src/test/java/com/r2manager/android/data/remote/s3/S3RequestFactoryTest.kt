package com.r2manager.android.data.remote.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okio.Buffer
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

    @Test
    fun deleteObjectsMatchesDesktopXmlAndSignsChecksums() {
        val xml = S3XmlParser.buildDeleteObjectsBody(
            listOf("a&b.txt", "c<d>.txt", "\u4e2d\u6587 name.png")
        )
        val request = factory.deleteObjects(xml)
        val body = Buffer()
        request.body!!.writeTo(body)

        assertEquals("POST", request.method)
        assertEquals("delete=", request.url.encodedQuery)
        assertEquals(xml, body.readUtf8())
        assertEquals("247", request.header("Content-Length"))
        assertEquals("CRC32", request.header("x-amz-sdk-checksum-algorithm"))
        assertEquals("mSyhYw==", request.header("x-amz-checksum-crc32"))
        assertEquals(SigV4Signer.sha256Hex(xml), request.header("x-amz-content-sha256"))
        assertTrue(
            request.header("Authorization").orEmpty().contains(
                "SignedHeaders=content-length;content-type;host;x-amz-checksum-crc32;" +
                    "x-amz-content-sha256;x-amz-date;x-amz-sdk-checksum-algorithm"
            )
        )
    }

    @Test
    fun singleDeleteRemainsSeparateFromBatchChecksumHeaders() {
        val request = factory.deleteObject("dir/a.webp")

        assertEquals("DELETE", request.method)
        assertEquals("/dir/a.webp", request.url.encodedPath)
        assertEquals(null, request.header("x-amz-sdk-checksum-algorithm"))
        assertEquals(null, request.header("x-amz-checksum-crc32"))
    }
}
