package com.r2manager.android.data.remote.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * SigV4 签名器单测。
 *
 * 主用例采用 AWS 官方文档 "Signature Version 4 Test Suite" 的 GET Object 向量，
 * 期望签名 `f0e8bdb8...bdb41` 与官方一致，确保与 AWS 规范逐位对齐。
 */
class SigV4SignerTest {

    private val config = S3Config(
        endpoint = "https://examplebucket.s3.amazonaws.com",
        region = "us-east-1",
        bucket = "examplebucket",
        accessKeyId = "AKIAIOSFODNN7EXAMPLE",
        secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    )

    private val emptyPayloadSha256 =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Test
    fun matchesAwsOfficialGetObjectVector() {
        val req = S3SignRequest(
            method = "GET",
            host = "examplebucket.s3.amazonaws.com",
            rawPath = "/test.txt",
            headers = mapOf("range" to "bytes=0-9"),
            payloadHash = emptyPayloadSha256
        )

        val headers = SigV4Signer.sign(req, config, Instant.parse("2013-05-24T00:00:00Z"))

        assertEquals(
            "AWS4-HMAC-SHA256 " +
                "Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
            headers.getValue("Authorization")
        )
        assertEquals("20130524T000000Z", headers.getValue("x-amz-date"))
        assertEquals(emptyPayloadSha256, headers.getValue("x-amz-content-sha256"))
        assertEquals("examplebucket.s3.amazonaws.com", headers.getValue("Host"))
    }

    @Test
    fun includesSecurityTokenInSignedHeaders() {
        val req = S3SignRequest(
            method = "GET",
            host = "examplebucket.s3.amazonaws.com",
            rawPath = "/test.txt",
            sessionToken = "SESSION_TOKEN"
        )
        val headers = SigV4Signer.sign(req, config, Instant.parse("2013-05-24T00:00:00Z"))
        assertTrue(headers.getValue("Authorization").contains("x-amz-security-token"))
        assertTrue(headers.getValue("Authorization").contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date;x-amz-security-token"))
        assertEquals("SESSION_TOKEN", headers.getValue("x-amz-security-token"))
    }

    @Test
    fun canonicalUriEncodesSpaceButKeepsSlash() {
        assertEquals("/bucket/a%20b.txt", SigV4Signer.canonicalUri("/bucket/a b.txt"))
        assertEquals("/bucket/dir%2Bplus/key", SigV4Signer.canonicalUri("/bucket/dir+plus/key"))
    }

    @Test
    fun uriEncodeEncodesSlashWhenAsked() {
        assertEquals("a%2Fb", SigV4Signer.uriEncode("a/b", encodeSlash = true))
        assertEquals("a/b", SigV4Signer.uriEncode("a/b", encodeSlash = false))
        assertEquals("a~b-c_d.e", SigV4Signer.uriEncode("a~b-c_d.e", encodeSlash = false))
    }

    @Test
    fun canonicalQueryIsSortedAndEncoded() {
        val query = mapOf(
            "max-keys" to "1000",
            "prefix" to "a/b",
            "delimiter" to "/"
        )
        assertEquals(
            "delimiter=%2F&max-keys=1000&prefix=a%2Fb",
            SigV4Signer.canonicalQueryString(query)
        )
    }

    @Test
    fun emptyPayloadSha256IsCorrect() {
        assertEquals(emptyPayloadSha256, SigV4Signer.sha256Hex(""))
    }
}
