package com.r2manager.android.data.remote.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3 XML 解析单测（纯 JVM，无 Android 依赖）。
 */
class S3XmlParserTest {

    private val listXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
          <Name>bucket</Name>
          <Prefix>docs/</Prefix>
          <KeyCount>2</KeyCount>
          <IsTruncated>false</IsTruncated>
          <Contents>
            <Key>docs/a.txt</Key>
            <LastModified>2024-01-02T03:04:05.000Z</LastModified>
            <ETag>&quot;e1&quot;</ETag>
            <Size>10</Size>
            <StorageClass>STANDARD</StorageClass>
          </Contents>
          <Contents>
            <Key>docs/sub/</Key>
            <LastModified>2024-02-02T00:00:00.000Z</LastModified>
            <Size>0</Size>
          </Contents>
          <Contents>
            <Key>docs/</Key>
            <Size>0</Size>
          </Contents>
          <CommonPrefixes><Prefix>docs/sub2/</Prefix></CommonPrefixes>
          <CommonPrefixes><Prefix>docs/</Prefix></CommonPrefixes>
        </ListBucketResult>
    """.trimIndent()

    @Test
    fun parsesContentsAndSkipsPlaceholders() {
        val page = S3XmlParser.parseListObjectsV2(listXml, "docs/")

        // docs/a.txt + docs/sub/ 两项；docs/（== prefix）被剔除
        assertEquals(2, page.contents.size)
        assertFalse(page.isTruncated)
        assertNull(page.nextContinuationToken)

        val file = page.contents.first { !it.isFolder }
        assertEquals("docs/a.txt", file.key)
        assertEquals("a.txt", file.name)
        assertEquals(10L, file.size)
        assertEquals("2024-01-02T03:04:05.000Z", file.lastModifiedIso)
        assertEquals("e1", file.etag)

        val folder = page.contents.first { it.isFolder }
        assertEquals("docs/sub/", folder.key)
        assertEquals("sub", folder.name)
        assertEquals(0L, folder.size)
    }

    @Test
    fun parsesCommonPrefixesAndSkipsEqualToPrefix() {
        val page = S3XmlParser.parseListObjectsV2(listXml, "docs/")
        assertEquals(listOf("docs/sub2/"), page.commonPrefixes)
    }

    @Test
    fun parsesTruncationAndContinuationToken() {
        val xml = """
            <ListBucketResult>
              <IsTruncated>true</IsTruncated>
              <NextContinuationToken>TOKEN_123</NextContinuationToken>
              <Contents><Key>a.bin</Key><Size>1</Size></Contents>
            </ListBucketResult>
        """.trimIndent()
        val page = S3XmlParser.parseListObjectsV2(xml, "")
        assertTrue(page.isTruncated)
        assertEquals("TOKEN_123", page.nextContinuationToken)
        assertEquals(1, page.contents.size)
    }

    @Test
    fun parsesDeleteResultWithDeletedAndErrors() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <DeleteResult>
              <Deleted><Key>a.txt</Key></Deleted>
              <Deleted><Key>b.txt</Key></Deleted>
              <Error><Key>c.txt</Key><Code>AccessDenied</Code><Message>denied</Message></Error>
            </DeleteResult>
        """.trimIndent()
        val result = S3XmlParser.parseDeleteResult(xml)
        assertEquals(listOf("a.txt", "b.txt"), result.deletedKeys)
        assertEquals(1, result.errors.size)
        assertEquals("c.txt", result.errors[0].key)
        assertEquals("AccessDenied", result.errors[0].code)
        assertEquals("denied", result.errors[0].message)
    }

    @Test
    fun parsesUploadId() {
        val xml = """
            <InitiateMultipartUploadResult>
              <Bucket>bucket</Bucket>
              <Key>big.bin</Key>
              <UploadId>UPLOAD_XYZ</UploadId>
            </InitiateMultipartUploadResult>
        """.trimIndent()
        assertEquals("UPLOAD_XYZ", S3XmlParser.parseUploadId(xml))
    }

    @Test
    fun parsesErrorBody() {
        val xml = "<Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message></Error>"
        val error = S3XmlParser.parseError(xml)
        assertEquals("NoSuchKey", error?.code)
        assertEquals("The specified key does not exist.", error?.message)
        assertNull(S3XmlParser.parseError(null))
    }

    @Test
    fun buildsDeleteObjectsBodyWithEscaping() {
        val body = S3XmlParser.buildDeleteObjectsBody(listOf("a&b.txt", "c<d>.txt"))
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Delete xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" +
                "<Object><Key>a&amp;b.txt</Key></Object>" +
                "<Object><Key>c&lt;d&gt;.txt</Key></Object>" +
                "<Quiet>false</Quiet></Delete>",
            body
        )
    }

    @Test
    fun buildsCompleteMultipartBodyWithQuotedEtags() {
        val body = S3XmlParser.buildCompleteMultipartBody(
            listOf(PartETag(2, "etag2"), PartETag(1, "etag1"))
        )
        assertTrue(body.contains("<Part><PartNumber>1</PartNumber><ETag>&quot;etag1&quot;</ETag></Part>"))
        assertTrue(body.contains("<Part><PartNumber>2</PartNumber><ETag>&quot;etag2&quot;</ETag></Part>"))
        // 分片必须按序号升序
        assertTrue(body.indexOf("<PartNumber>1</PartNumber>") < body.indexOf("<PartNumber>2</PartNumber>"))
    }

    @Test
    fun normalizeIsoAndHttpDate() {
        assertEquals("2024-01-02T03:04:05.000Z", S3XmlParser.normalizeIso("2024-01-02T03:04:05Z"))
        assertEquals("2009-10-12T17:50:00.000Z", S3XmlParser.normalizeHttpDate("Mon, 12 Oct 2009 17:50:00 GMT"))
        assertNull(S3XmlParser.normalizeIso(null))
        assertNull(S3XmlParser.normalizeHttpDate("not-a-date"))
    }
}
