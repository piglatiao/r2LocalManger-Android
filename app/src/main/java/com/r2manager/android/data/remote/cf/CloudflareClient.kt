package com.r2manager.android.data.remote.cf

import com.r2manager.android.domain.model.Bucket
import com.r2manager.android.domain.model.CustomDomain
import com.r2manager.android.domain.model.ManagedDomain
import com.r2manager.android.domain.model.Zone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cloudflare 管理面鉴权信息。 */
data class CfAuth(
    val accountId: String,
    val apiToken: String,
    val jurisdiction: String = "default"
)

/**
 * Cloudflare 管理面客户端。
 *
 * Base = `https://api.cloudflare.com/client/v4`，账户级路径前缀 `/accounts/{accountId}`；
 * 鉴权 `Authorization: Bearer <apiToken>`；`jurisdiction != default` 的账户级请求附加 `cf-r2-jurisdiction`。
 */
interface CloudflareClient {
    suspend fun listBuckets(perPage: Int = 1000): List<Bucket>
    suspend fun getBucket(name: String): Bucket
    suspend fun createBucket(name: String, storageClass: String, locationHint: String?): Bucket
    suspend fun updateBucketStorageClass(name: String, storageClass: String): Bucket
    suspend fun deleteBucket(name: String)
    suspend fun listCustomDomains(bucket: String): List<CustomDomain>
    suspend fun upsertCustomDomain(bucket: String, input: CustomDomainInput): CustomDomain
    suspend fun deleteCustomDomain(bucket: String, domain: String)
    suspend fun getManagedDomain(bucket: String): ManagedDomain
    suspend fun setManagedDomain(bucket: String, enabled: Boolean): ManagedDomain
    suspend fun listZones(): List<Zone>          // 分页取全量，仅同账号，按 name 升序
}

/**
 * [CloudflareClient] 默认实现。
 *
 * @param authProvider 每次请求实时读取鉴权信息（换 Token/账号无需重建）
 * @param httpClient 复用的 OkHttp 客户端
 * @param ioDispatcher 网络 IO 调度器
 */
class CloudflareClientImpl(
    private val authProvider: () -> CfAuth,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CloudflareClient {

    private val baseUrl: HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegments("client/v4")
        .build()

    private suspend fun call(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) {
                        cont.resume(response)
                    } else {
                        runCatching { response.close() }
                    }
                }
            })
        }

    /**
     * 发起管理面调用并返回**完整响应体**（根 JSONObject）。
     *
     * @param method HTTP 方法
     * @param path API 路径（含 query，如 `/r2/buckets?per_page=1000`）
     * @param includeAccountPath 是否拼接 `/accounts/{accountId}` 前缀
     * @param body JSON 请求体（null 表示无体）
     * @param extraHeaders 额外请求头（如 `cf-r2-storage-class`）
     */
    private suspend fun call(
        method: String,
        path: String,
        includeAccountPath: Boolean = true,
        body: JSONObject? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject = withContext(ioDispatcher) {
        val auth = authProvider()
        if (auth.apiToken.isBlank()) {
            throw CfApiException(0, "missing_api_token", "缺少 Cloudflare API Token")
        }

        val url = buildUrl(path, auth.accountId, includeAccountPath)
        val builder = Request.Builder().url(url)
        builder.header("Authorization", "Bearer ${auth.apiToken}")
        if (includeAccountPath && auth.jurisdiction.isNotBlank() &&
            auth.jurisdiction != DEFAULT_JURISDICTION
        ) {
            builder.header("cf-r2-jurisdiction", auth.jurisdiction)
        }
        for ((name, value) in extraHeaders) {
            builder.header(name, value)
        }

        val requestBody = body?.let { it.toString().toRequestBody(JSON_MEDIA_TYPE) }
        if (body != null) {
            builder.header("Content-Type", JSON_CONTENT_TYPE)
        }
        builder.method(method, requestBody)

        val response = call(builder.build())
        response.use { res ->
            val text = res.body?.string().orEmpty()
            val parsed = if (text.isBlank()) {
                JSONObject()
            } else {
                runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            }
            val successFalse = parsed.has("success") && !parsed.optBoolean("success", false)
            if (!res.isSuccessful || successFalse) {
                throw toException(res.code, parsed)
            }
            parsed
        }
    }

    private fun toException(status: Int, root: JSONObject): CfApiException {
        val errors = root.optJSONArray("errors")
        val first = if (errors != null && errors.length() > 0) errors.optJSONObject(0) else null
        val code = first?.opt("code")?.toString()
            ?: root.optJSONArray("messages")?.takeIf { it.length() > 0 }?.optJSONObject(0)?.opt("code")?.toString()
        val detail = first?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "HTTP $status"
        return CfApiException(status, code, "Cloudflare API 调用失败: $detail")
    }

    private fun buildUrl(path: String, accountId: String, includeAccountPath: Boolean): HttpUrl {
        val queryStart = path.indexOf('?')
        val pathPart = if (queryStart >= 0) path.substring(0, queryStart) else path
        val queryPart = if (queryStart >= 0) path.substring(queryStart + 1) else ""

        val builder = baseUrl.newBuilder()
        if (includeAccountPath && accountId.isNotBlank()) {
            builder.addPathSegment("accounts").addPathSegment(accountId)
        }
        val trimmed = pathPart.trimStart('/')
        if (trimmed.isNotEmpty()) {
            builder.addPathSegments(trimmed)
        }
        if (queryPart.isNotEmpty()) {
            for (pair in queryPart.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                val key = if (eq >= 0) pair.substring(0, eq) else pair
                val value = if (eq >= 0) pair.substring(eq + 1) else ""
                builder.addQueryParameter(key, value)
            }
        }
        return builder.build()
    }

    private fun resultObject(root: JSONObject): JSONObject =
        root.optJSONObject("result") ?: JSONObject()

    private fun resultArray(root: JSONObject, nestedKey: String? = null): List<JSONObject> {
        val result = root.opt("result")
        val array: JSONArray? = when (result) {
            is JSONArray -> result
            is JSONObject -> if (nestedKey != null) result.optJSONArray(nestedKey) else null
            else -> null
        }
        if (array == null) {
            return emptyList()
        }
        val out = ArrayList<JSONObject>(array.length())
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { out.add(it) }
        }
        return out
    }

    // ———————————————————— 桶 ————————————————————

    override suspend fun listBuckets(perPage: Int): List<Bucket> {
        val root = call("GET", "/r2/buckets?per_page=$perPage")
        return resultArray(root, nestedKey = "buckets")
            .map { CloudflareModels.toBucket(it) }
            .filter { it.name.isNotEmpty() }
    }

    override suspend fun getBucket(name: String): Bucket {
        val root = call("GET", "/r2/buckets/${encode(name)}")
        return CloudflareModels.toBucket(resultObject(root))
    }

    override suspend fun createBucket(
        name: String,
        storageClass: String,
        locationHint: String?
    ): Bucket {
        val root = call(
            method = "POST",
            path = "/r2/buckets",
            body = CloudflareModels.bucketRequestBody(name, storageClass, locationHint)
        )
        val parsed = CloudflareModels.toBucket(resultObject(root))
        return if (parsed.name.isNotEmpty()) {
            parsed
        } else {
            Bucket(name, null, locationHint.orEmpty(), storageClass)
        }
    }

    override suspend fun updateBucketStorageClass(name: String, storageClass: String): Bucket {
        val root = call(
            method = "PATCH",
            path = "/r2/buckets/${encode(name)}",
            extraHeaders = mapOf("cf-r2-storage-class" to storageClass)
        )
        val parsed = CloudflareModels.toBucket(resultObject(root))
        return if (parsed.name.isNotEmpty()) parsed else Bucket(name, null, "", storageClass)
    }

    override suspend fun deleteBucket(name: String) {
        call("DELETE", "/r2/buckets/${encode(name)}")
    }

    // ———————————————————— 自定义域名 ————————————————————

    override suspend fun listCustomDomains(bucket: String): List<CustomDomain> {
        val root = call("GET", "/r2/buckets/${encode(bucket)}/domains/custom")
        return resultArray(root, nestedKey = "domains")
            .map { CloudflareModels.toCustomDomain(it) }
            .filter { it.domain.isNotEmpty() }
    }

    override suspend fun upsertCustomDomain(bucket: String, input: CustomDomainInput): CustomDomain {
        val existing = runCatching { listCustomDomains(bucket) }.getOrDefault(emptyList())
        val body = CloudflareModels.customDomainRequestBody(input)
        val exists = existing.any { it.domain == input.domain }
        val root = if (exists) {
            call("PUT", "/r2/buckets/${encode(bucket)}/domains/custom/${encode(input.domain)}", body = body)
        } else {
            call("POST", "/r2/buckets/${encode(bucket)}/domains/custom", body = body)
        }
        val parsed = CloudflareModels.toCustomDomain(resultObject(root))
        return if (parsed.domain.isNotEmpty()) {
            parsed
        } else {
            CustomDomain(
                domain = input.domain,
                enabled = input.enabled,
                zoneId = input.zoneId,
                minTls = input.minTls,
                ciphers = input.ciphers,
                status = ""
            )
        }
    }

    override suspend fun deleteCustomDomain(bucket: String, domain: String) {
        call("DELETE", "/r2/buckets/${encode(bucket)}/domains/custom/${encode(domain)}")
    }

    // ———————————————————— r2.dev 托管域名 ————————————————————

    override suspend fun getManagedDomain(bucket: String): ManagedDomain {
        val root = call("GET", "/r2/buckets/${encode(bucket)}/domains/managed")
        return CloudflareModels.toManagedDomain(resultObject(root))
    }

    override suspend fun setManagedDomain(bucket: String, enabled: Boolean): ManagedDomain {
        val body = JSONObject().apply { put("enabled", enabled) }
        call("PUT", "/r2/buckets/${encode(bucket)}/domains/managed", body = body)
        return getManagedDomain(bucket)
    }

    // ———————————————————— Zone ————————————————————

    override suspend fun listZones(): List<Zone> = withContext(ioDispatcher) {
        val auth = authProvider()
        val perPage = 50
        val zones = ArrayList<Zone>()
        var page = 1
        while (true) {
            val query = buildString {
                append("page=").append(page)
                append("&per_page=").append(perPage)
                append("&order=name")
                append("&direction=asc")
                if (auth.accountId.isNotBlank()) {
                    append("&account.id=").append(encode(auth.accountId))
                }
            }
            // 注意：/zones 为账号外路径，不拼接 /accounts/{id}
            val root = call("GET", "/zones?$query", includeAccountPath = false)
            val raw = resultArray(root)
            raw.map { CloudflareModels.toZone(it) }
                .filter { it.id.isNotEmpty() && it.name.isNotEmpty() }
                .forEach { zones.add(it) }

            val totalPages = root.optJSONObject("result_info")?.optInt("total_pages", 0) ?: 0
            if ((totalPages > 0 && page >= totalPages) || raw.size < perPage) {
                break
            }
            page += 1
        }

        val accountId = auth.accountId.trim()
        zones.asSequence()
            .filter { accountId.isEmpty() || it.accountId.isEmpty() || it.accountId == accountId }
            .sortedBy { it.name }
            .toList()
    }

    private fun encode(value: String): String = HttpUrl.Builder()
        .scheme("https").host(HOST).addPathSegment(value).build().encodedPath.trimStart('/')

    private companion object {
        const val HOST = "api.cloudflare.com"
        const val DEFAULT_JURISDICTION = "default"
        const val JSON_CONTENT_TYPE = "application/json"
        val JSON_MEDIA_TYPE = JSON_CONTENT_TYPE.toMediaType()
    }
}
