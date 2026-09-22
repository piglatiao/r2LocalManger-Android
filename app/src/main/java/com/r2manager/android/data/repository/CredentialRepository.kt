package com.r2manager.android.data.repository

import com.r2manager.android.core.constants.NetworkConstants
import com.r2manager.android.core.constants.PrefKeys
import com.r2manager.android.core.error.AppError
import com.r2manager.android.core.error.ErrorMapper
import com.r2manager.android.core.error.ErrorType
import com.r2manager.android.core.util.UrlUtils
import com.r2manager.android.data.local.prefs.CredentialStore
import com.r2manager.android.data.local.prefs.SettingsStore
import com.r2manager.android.data.remote.cf.CfAuth
import com.r2manager.android.data.remote.cf.CloudflareClientImpl
import com.r2manager.android.data.remote.s3.S3ClientImpl
import com.r2manager.android.data.remote.s3.S3Config
import com.r2manager.android.domain.model.Credentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

/** 凭证保存结果；[managementApiOk] 为 null 表示管理面探测仍在后台进行。 */
data class SaveResult(val saved: Boolean, val managementApiOk: Boolean?, val error: AppError?)

/** 连接测试结果。 */
data class TestResult(val ok: Boolean, val error: AppError?)

/**
 * 凭证仓库：存取 R2 凭证，保存成功后异步探测管理面（不阻塞主链路）。
 */
interface CredentialRepository {
    suspend fun load(): Credentials?
    suspend fun save(credentials: Credentials): SaveResult
    suspend fun clear()

    /** 10s 超时内列一次对象，验证数据面可达。 */
    suspend fun testConnection(credentials: Credentials): TestResult

    /** 当前可用的 S3 配置（未配置完整时返回 null）。 */
    fun currentS3Config(): S3Config?
}

/**
 * [CredentialRepository] 默认实现。
 *
 * @param store 加密凭证存取
 * @param settings 设置存取（端点/桶/公开域名）
 * @param httpClient 复用的 OkHttp 客户端
 * @param scope 用于「管理面探测」的异步作用域（应用级）
 * @param ioDispatcher IO 调度器
 */
class CredentialRepositoryImpl(
    private val store: CredentialStore,
    private val settings: SettingsStore,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CredentialRepository {

    override suspend fun load(): Credentials? = withContext(ioDispatcher) { store.load() }

    override suspend fun save(credentials: Credentials): SaveResult = withContext(ioDispatcher) {
        if (!credentials.isComplete) {
            return@withContext SaveResult(false, null, incompleteError("saveCredentials"))
        }

        store.save(credentials)

        val current = settings.load()
        val endpoint = UrlUtils.buildEndpoint(credentials.accountId, current.endpoint)
        settings.saveR2Config(endpoint, NetworkConstants.REGION, credentials.jurisdiction)
        settings.raw().edit().putString(PrefKeys.ACCOUNT_ID, credentials.accountId).apply()

        // 异步探测管理面：一次列桶，成功与否只影响高级设置页提示，不阻塞保存
        val auth = CfAuth(credentials.accountId, credentials.apiToken, credentials.jurisdiction)
        scope.launch {
            val ok = runCatching {
                CloudflareClientImpl({ auth }, httpClient, ioDispatcher).listBuckets()
            }.isSuccess
            settings.setManagementApiAvailable(ok)
        }

        SaveResult(true, null, null)
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        store.clear()
        settings.setManagementApiAvailable(false)
    }

    override suspend fun testConnection(credentials: Credentials): TestResult = withContext(ioDispatcher) {
        if (!credentials.isComplete) {
            return@withContext TestResult(false, incompleteError("testConnection"))
        }
        val current = settings.load()
        val bucket = current.currentBucket
        if (bucket.isBlank()) {
            return@withContext TestResult(false, incompleteError("testConnection"))
        }
        val endpoint = UrlUtils.buildEndpoint(credentials.accountId, current.endpoint)
        val config = S3Config(
            endpoint = endpoint,
            region = NetworkConstants.REGION,
            bucket = bucket,
            accessKeyId = credentials.accessKeyId,
            secretAccessKey = credentials.secretAccessKey,
            jurisdiction = credentials.jurisdiction,
            publicUrl = current.publicUrl
        )
        try {
            withTimeout(NetworkConstants.STARTUP_PROBE_TIMEOUT_MS) {
                S3ClientImpl(config, httpClient, ioDispatcher)
                    .listObjectsV2(prefix = "", continuationToken = null, delimiter = "/", maxKeys = 1)
            }
            TestResult(true, null)
        } catch (t: Throwable) {
            if (t is CancellationException && t !is TimeoutCancellationException) {
                throw t
            }
            TestResult(false, ErrorMapper.fromThrowable(t, "testConnection"))
        }
    }

    override fun currentS3Config(): S3Config? {
        val credentials = store.load() ?: return null
        if (!credentials.isComplete) {
            return null
        }
        val current = settings.load()
        val endpoint = current.endpoint.ifBlank { UrlUtils.buildEndpoint(credentials.accountId) }
        if (endpoint.isBlank() || current.currentBucket.isBlank()) {
            return null
        }
        return S3Config(
            endpoint = endpoint,
            region = current.region,
            bucket = current.currentBucket,
            accessKeyId = credentials.accessKeyId,
            secretAccessKey = credentials.secretAccessKey,
            jurisdiction = credentials.jurisdiction,
            publicUrl = current.publicUrl
        )
    }

    private fun incompleteError(operation: String): AppError = AppError(
        type = ErrorType.AUTH,
        messageResId = ErrorMapper.messageFor(ErrorType.AUTH),
        recovery = ErrorMapper.recoveryFor(ErrorType.AUTH),
        operation = operation
    )
}
