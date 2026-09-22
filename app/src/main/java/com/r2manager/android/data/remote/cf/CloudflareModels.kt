package com.r2manager.android.data.remote.cf

import com.r2manager.android.domain.model.Bucket
import com.r2manager.android.domain.model.CustomDomain
import com.r2manager.android.domain.model.ManagedDomain
import com.r2manager.android.domain.model.Zone
import org.json.JSONObject

/**
 * 绑定/更新自定义域名的入参（对应桌面版 create/update custom domain 的 payload）。
 */
data class CustomDomainInput(
    val domain: String,
    val zoneId: String,
    val enabled: Boolean = true,
    val minTls: String = "1.2",
    val ciphers: List<String> = emptyList()
)

/**
 * Cloudflare 管理面 JSON ↔ 领域模型映射（逐字对齐桌面版 `normalize*` 逻辑）。
 *
 * 全部为容错解析：字段缺失返回空串/null，不抛异常。
 */
internal object CloudflareModels {

    /** 桶：`{name, creation_date, locationHint|location, storageClass|storage_class}` */
    fun toBucket(json: JSONObject): Bucket = Bucket(
        name = json.optString("name").trim(),
        creationDateIso = firstNonEmpty(
            json.optString("creation_date"),
            json.optString("creationDate")
        ),
        locationHint = firstNonEmpty(
            json.optString("locationHint"),
            json.optString("location")
        ).orEmpty(),
        storageClass = firstNonEmpty(
            json.optString("storageClass"),
            json.optString("storage_class")
        ).orEmpty()
    )

    /** 自定义域名：`{domain|hostname, enabled, zoneId|zone_id, minTLS|min_tls, ciphers[], status}` */
    fun toCustomDomain(json: JSONObject): CustomDomain {
        val ciphers = ArrayList<String>()
        val ciphersArray = json.optJSONArray("ciphers")
        if (ciphersArray != null) {
            for (i in 0 until ciphersArray.length()) {
                val item = ciphersArray.optString(i)
                if (item.isNotEmpty()) {
                    ciphers.add(item)
                }
            }
        }
        return CustomDomain(
            domain = firstNonEmpty(json.optString("domain"), json.optString("hostname")).orEmpty(),
            enabled = json.optBoolean("enabled", false),
            zoneId = firstNonEmpty(json.optString("zoneId"), json.optString("zone_id")).orEmpty(),
            minTls = firstNonEmpty(json.optString("minTLS"), json.optString("min_tls")).orEmpty(),
            ciphers = ciphers,
            status = json.optString("status").trim()
        )
    }

    /** r2.dev 托管域名：`{enabled, domain|hostname|uri|url|publicUrl}` */
    fun toManagedDomain(json: JSONObject): ManagedDomain = ManagedDomain(
        enabled = json.optBoolean("enabled", false),
        domain = firstNonEmpty(
            json.optString("domain"),
            json.optString("hostname"),
            json.optString("uri"),
            json.optString("url"),
            json.optString("publicUrl")
        ).orEmpty()
    )

    /** Zone：`{id, name, status, account.id|account_id}` */
    fun toZone(json: JSONObject): Zone {
        val accountId = firstNonEmpty(
            json.optJSONObject("account")?.optString("id"),
            json.optString("account_id")
        ).orEmpty()
        return Zone(
            id = json.optString("id").trim(),
            name = json.optString("name").trim(),
            status = json.optString("status").trim(),
            accountId = accountId
        )
    }

    /** 建桶请求体：`{name, storageClass, locationHint?}` */
    fun bucketRequestBody(name: String, storageClass: String, locationHint: String?): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("storageClass", storageClass)
            if (!locationHint.isNullOrBlank()) {
                put("locationHint", locationHint)
            }
        }

    /** 自定义域名请求体：`{domain, zoneId, enabled, minTLS?, ciphers?}` */
    fun customDomainRequestBody(input: CustomDomainInput): JSONObject =
        JSONObject().apply {
            put("domain", input.domain)
            put("zoneId", input.zoneId)
            put("enabled", input.enabled)
            if (input.minTls.isNotBlank()) {
                put("minTLS", input.minTls)
            }
            if (input.ciphers.isNotEmpty()) {
                put("ciphers", org.json.JSONArray(input.ciphers))
            }
        }

    private fun firstNonEmpty(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
