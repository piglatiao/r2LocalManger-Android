package com.r2manager.android.data.repository

import com.r2manager.android.R
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
import com.r2manager.android.domain.model.Bucket
import com.r2manager.android.domain.model.Credentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

/** 凭证保存结果；保存完成前会等待一次管理面列桶，便于自动选择首桶。 */
data class SaveResult(val saved: Boolean, val managementApiOk: Boolean?, val error: AppError?)

/** 连接测试结果。 */
data class TestResult(val ok: Boolean, val error: AppError?)

/**
 * 凭证仓库：存取 R2 凭证，保存成功后列出存储桶并默认选择首桶。
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
 * @param ioDispatcher IO 调度器
 */
class CredentialRepositoryImpl(
    private val store: CredentialStore,
    private val settings: SettingsStore,
    private val httpClient: OkHttpClient,
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

        val auth = CfAuth(credentials.accountId, credentials.apiToken, credentials.jurisdiction)
        val buckets = discoverBuckets(auth)
        val managementApiOk = !buckets.isNullOrEmpty()
        if (buckets != null) {
            val selectedBucket = selectAvailableBucket(settings.load().currentBucket, buckets)
            settings.setCurrentBucket(selectedBucket)
        }
        settings.setManagementApiAvailable(managementApiOk)

        SaveResult(true, managementApiOk, null)
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
        var bucket = current.currentBucket.trim()
        val auth = CfAuth(credentials.accountId, credentials.apiToken, credentials.jurisdiction)
        val buckets = discoverBuckets(auth)
        if (buckets != null) {
            bucket = selectAvailableBucket(bucket, buckets)
            settings.setCurrentBucket(bucket)
        }
        if (bucket.isBlank()) {
            return@withContext TestResult(false, bucketUnavailableError("testConnection"))
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
        val bucket = current.currentBucket.trim()
        if (endpoint.isBlank() || bucket.isBlank()) {
            return null
        }
        return S3Config(
            endpoint = endpoint,
            region = current.region,
            bucket = bucket,
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

    /** 在限定时间内获取存储桶；超时返回空结果，外部取消继续向上传递。 */
    private suspend fun discoverBuckets(auth: CfAuth): List<Bucket>? = try {
        withTimeout(NetworkConstants.STARTUP_PROBE_TIMEOUT_MS) {
            CloudflareClientImpl({ auth }, httpClient, ioDispatcher).listBuckets()
        }
    } catch (t: Throwable) {
        if (t is CancellationException && t !is TimeoutCancellationException) {
            throw t
        }
        null
    }

    /** 管理面列桶成功时保留有效当前桶，否则选择远端返回的第一个桶。 */
    private fun selectAvailableBucket(currentBucket: String, buckets: List<Bucket>): String {
        val normalizedCurrent = currentBucket.trim()
        return buckets.firstOrNull { it.name == normalizedCurrent }?.name
            ?: buckets.firstOrNull()?.name.orEmpty()
    }

    /** 管理面未返回存储桶时阻止发起指向虚构桶名的 S3 请求。 */
    private fun bucketUnavailableError(operation: String): AppError = AppError(
        type = ErrorType.BUCKET,
        messageResId = R.string.error_bucket_not_selected,
        recovery = ErrorMapper.recoveryFor(ErrorType.BUCKET),
        operation = operation
    )
}
