package com.r2manager.android.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import com.r2manager.android.core.constants.PrefKeys
import com.r2manager.android.core.crypto.SecretCodec
import com.r2manager.android.domain.model.Credentials
import org.json.JSONObject

/**
 * R2 凭证存取（四字段一律 Keystore 加密后 Base64 落 `SharedPreferences`）。
 *
 * **任何日志/异常不得打印明文**：本类不记录任何字段内容。
 */
interface CredentialStore {
    fun load(): Credentials?
    fun save(credentials: Credentials)
    fun clear()
}

/**
 * [CredentialStore] 默认实现。
 *
 * @param context 应用上下文
 * @param codec Keystore 包裹器（见 [KeystoreSecretCodec]）
 */
class CredentialStoreImpl(
    context: Context,
    private val codec: SecretCodec
) : CredentialStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefKeys.FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): Credentials? {
        val blob = prefs.getString(PrefKeys.CREDENTIALS_BLOB, null) ?: return null
        val json = codec.decrypt(blob) ?: return null
        return runCatching { parse(json) }.getOrNull()
    }

    override fun save(credentials: Credentials) {
        val json = serialize(credentials)
        val wrapped = codec.encrypt(json)
        prefs.edit().putString(PrefKeys.CREDENTIALS_BLOB, wrapped).apply()
    }

    override fun clear() {
        prefs.edit().remove(PrefKeys.CREDENTIALS_BLOB).apply()
    }

    private fun serialize(credentials: Credentials): String = JSONObject().apply {
        put(KEY_ACCOUNT_ID, credentials.accountId)
        put(KEY_ACCESS_KEY_ID, credentials.accessKeyId)
        put(KEY_SECRET_ACCESS_KEY, credentials.secretAccessKey)
        put(KEY_API_TOKEN, credentials.apiToken)
        put(KEY_JURISDICTION, credentials.jurisdiction)
    }.toString()

    private fun parse(json: String): Credentials {
        val obj = JSONObject(json)
        return Credentials(
            accountId = obj.optString(KEY_ACCOUNT_ID),
            accessKeyId = obj.optString(KEY_ACCESS_KEY_ID),
            secretAccessKey = obj.optString(KEY_SECRET_ACCESS_KEY),
            apiToken = obj.optString(KEY_API_TOKEN),
            jurisdiction = obj.optString(KEY_JURISDICTION, DEFAULT_JURISDICTION)
                .ifBlank { DEFAULT_JURISDICTION }
        )
    }

    private companion object {
        const val KEY_ACCOUNT_ID = "accountId"
        const val KEY_ACCESS_KEY_ID = "accessKeyId"
        const val KEY_SECRET_ACCESS_KEY = "secretAccessKey"
        const val KEY_API_TOKEN = "apiToken"
        const val KEY_JURISDICTION = "jurisdiction"
        const val DEFAULT_JURISDICTION = "default"
    }
}
