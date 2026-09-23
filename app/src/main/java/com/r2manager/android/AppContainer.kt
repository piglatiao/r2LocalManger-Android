package com.r2manager.android

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.r2manager.android.core.constants.NetworkConstants
import com.r2manager.android.core.crypto.SecretCodec
import com.r2manager.android.core.util.AppDispatchers
import com.r2manager.android.data.local.cache.CryptoFileStore
import com.r2manager.android.data.local.cache.CryptoFileStoreImpl
import com.r2manager.android.data.local.cache.ObjectListCache
import com.r2manager.android.data.local.cache.ObjectListCacheImpl
import com.r2manager.android.data.local.cache.ThumbnailCache
import com.r2manager.android.data.local.cache.ThumbnailCacheImpl
import com.r2manager.android.data.local.db.AppDatabaseHelper
import com.r2manager.android.data.local.db.TransferTaskDao
import com.r2manager.android.data.local.prefs.CredentialStore
import com.r2manager.android.data.local.prefs.CredentialStoreImpl
import com.r2manager.android.data.local.prefs.KeystoreSecretCodec
import com.r2manager.android.data.local.prefs.SettingsStore
import com.r2manager.android.data.local.prefs.SettingsStoreImpl
import com.r2manager.android.data.remote.ConnectivityMonitor
import com.r2manager.android.data.remote.ConnectivityMonitorImpl
import com.r2manager.android.data.remote.cf.CfAuth
import com.r2manager.android.data.remote.cf.CloudflareClient
import com.r2manager.android.data.remote.cf.CloudflareClientImpl
import com.r2manager.android.data.remote.s3.S3Client
import com.r2manager.android.data.remote.s3.S3ClientImpl
import com.r2manager.android.data.repository.BucketRepository
import com.r2manager.android.data.repository.BucketRepositoryImpl
import com.r2manager.android.data.repository.CredentialRepository
import com.r2manager.android.data.repository.CredentialRepositoryImpl
import com.r2manager.android.data.repository.SettingsRepository
import com.r2manager.android.data.repository.SettingsRepositoryImpl
import com.r2manager.android.data.repository.StorageRepository
import com.r2manager.android.data.repository.StorageRepositoryImpl
import com.r2manager.android.domain.security.AppLockManager
import com.r2manager.android.domain.security.BiometricAuthenticator
import com.r2manager.android.domain.security.CacheEraser
import com.r2manager.android.domain.security.KeyManager
import com.r2manager.android.domain.thumbnail.ThumbnailGenerator
import com.r2manager.android.domain.thumbnail.ThumbnailLoader
import com.r2manager.android.domain.thumbnail.VideoFrameExtractor
import com.r2manager.android.domain.transfer.DownloadTargetResolver
import com.r2manager.android.domain.transfer.TransferEngine
import com.r2manager.android.domain.transfer.TransferEngineImpl
import com.r2manager.android.domain.transfer.TransferProgressBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * 手写依赖容器（替代 Hilt/Dagger，避免注解处理器）。
 *
 * 生命周期与进程一致；UI 层不得自行 `new` 网络 / 存储对象，一切从本容器获取。
 *
 * ## 归属约定
 * - 本文件归 **P1**。
 * - P2 / P3 的单例在此以**懒加载**方式真实装配（P2/P3 类落地后由 P1 集成阶段填充）。
 * - 依赖环（`cryptoFileStore → keyProvider → keyManager → cacheEraser → *Cache → cryptoFileStore`）
 *   通过「密钥以 **lambda** 惰性读取」打破：构造期不读取密钥，仅在运行期读写缓存时才取值。
 *
 * ## 需要 Activity 的组件（BiometricAuthenticator）
 * [BiometricAuthenticator] 构造需要 `FragmentActivity`，而本容器是应用级。约定：前台 Activity
 * 在 `onCreate` 调用 [attachActivity]、`onDestroy` 调用 [detachActivity]，容器据此构建 [biometric]。
 * 由于「应用锁 bootstrap」是由启动页触发的，启动页须先 [attachActivity] 再访问 [appLockManager]。
 */
class AppContainer(private val application: Context) {

    /** 应用上下文（applicationContext）。 */
    val appContext: Context get() = application.applicationContext

    /** 全局调度器。 */
    val appDispatchers: AppDispatchers get() = AppDispatchers

    /** 应用级协程作用域（凭证管理面异步探测等使用）。 */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + appDispatchers.io)

    /** 凭证 / 回退密钥的 Keystore 包裹器（全局单例）。 */
    private val secretCodec: SecretCodec by lazy { KeystoreSecretCodec() }

    /**
     * 复用的 OkHttp 客户端：超时统一取自 [NetworkConstants]（连接 15s / 读 30s / 写 60s；
     * 总调用超时 0 = 不限，大文件靠主动取消）。S3 / Cloudflare / 仓库共用同一实例。
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(NetworkConstants.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(NetworkConstants.CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ============ 前台 Activity 注册（供需要 Activity 的组件使用） ============

    @Volatile
    private var activityRef: WeakReference<FragmentActivity>? = null

    /**
     * 注册当前前台 Activity（弱引用，避免泄漏）。前台 Activity 应在 `onCreate` 调用。
     *
     * @param activity 当前前台 `FragmentActivity`
     */
    fun attachActivity(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    /**
     * 注销前台 Activity（仅当与已注册的是同一实例时清除）。前台 Activity 应在 `onDestroy` 调用。
     *
     * @param activity 正在销毁的 `FragmentActivity`
     */
    fun detachActivity(activity: FragmentActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    // ============ P2 · 数据面 / 本地持久化 / 仓库 ============

    /** 偏好存储（非敏感设置）。 */
    val settingsStore: SettingsStore by lazy { SettingsStoreImpl(appContext) }

    /** 凭证存储（Keystore 加密落盘）。 */
    val credentialStore: CredentialStore by lazy { CredentialStoreImpl(appContext, secretCodec) }

    /** 缓存文件加解密薄封装（密钥经 [KeyManager] 惰性读取）。 */
    val cryptoFileStore: CryptoFileStore by lazy {
        CryptoFileStoreImpl(appContext) { keyManager.currentKey() }
    }

    /** 缩略图缓存（加密，LRU）。 */
    val thumbnailCache: ThumbnailCache by lazy {
        ThumbnailCacheImpl(appContext, cryptoFileStore) { keyManager.currentKey() }
    }

    /** 对象列表缓存（加密，按 bucket+prefix）。 */
    val objectListCache: ObjectListCache by lazy {
        ObjectListCacheImpl(appContext, cryptoFileStore) { keyManager.currentKey() }
    }

    /** 传输任务数据库。 */
    val databaseHelper: AppDatabaseHelper by lazy { AppDatabaseHelper(appContext) }

    /** 传输任务 DAO。 */
    val transferTaskDao: TransferTaskDao by lazy { TransferTaskDao(databaseHelper) }

    /**
     * S3 数据面客户端（按当前配置即时构建；S3ClientImpl 轻量，复用 [httpClient]）。
     *
     * 未配置完整凭证 / 桶时访问会抛 [IllegalStateException]；需要「未配置返回 null」的调用方
     * 请使用 [s3ClientOrNull]（如传输引擎的 `s3Provider`）。
     */
    val s3Client: S3Client
        get() = s3ClientOrNull()
            ?: error("S3 未配置：请先在凭证页填写 accountId / accessKeyId / secretAccessKey 并选择当前桶")

    /** 当前配置对应的 S3 客户端；未配置完整时返回 null。 */
    private fun s3ClientOrNull(): S3Client? =
        credentialRepository.currentS3Config()?.let { S3ClientImpl(it, httpClient, appDispatchers.io) }

    /** Cloudflare 管理面客户端（鉴权每次实时读取，换 Token/账号无需重建）。 */
    val cloudflareClient: CloudflareClient by lazy {
        CloudflareClientImpl(
            authProvider = { cfAuth() },
            httpClient = httpClient,
            ioDispatcher = appDispatchers.io
        )
    }

    /** 网络连通性监听。 */
    val connectivityMonitor: ConnectivityMonitor by lazy { ConnectivityMonitorImpl(appContext) }

    /** 存储仓库（聚合 S3 数据面 + 缓存）。 */
    val storageRepository: StorageRepository by lazy {
        StorageRepositoryImpl(
            configProvider = { credentialRepository.currentS3Config() },
            httpClient = httpClient,
            listCache = objectListCache,
            thumbnailCache = thumbnailCache,
            connectivity = connectivityMonitor,
            ioDispatcher = appDispatchers.io
        )
    }

    /** 桶管理仓库（Cloudflare 管理面 + 删桶前 S3 清空）。 */
    val bucketRepository: BucketRepository by lazy {
        BucketRepositoryImpl(
            cfProvider = { cloudflareClient },
            configProvider = { credentialRepository.currentS3Config() },
            httpClient = httpClient,
            settings = settingsStore,
            listCache = objectListCache,
            thumbnailCache = thumbnailCache,
            ioDispatcher = appDispatchers.io
        )
    }

    /** 设置与缓存仓库。 */
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(
            store = settingsStore,
            thumbnailCache = thumbnailCache,
            listCache = objectListCache,
            context = appContext,
            ioDispatcher = appDispatchers.io
        )
    }

    /** 凭证仓库（存取 R2 凭证 + 异步探测管理面）。 */
    val credentialRepository: CredentialRepository by lazy {
        CredentialRepositoryImpl(
            store = credentialStore,
            settings = settingsStore,
            httpClient = httpClient,
            ioDispatcher = appDispatchers.io
        )
    }

    // ============ P3 · 传输引擎 / 安全 / 缩略图 ============

    /** 传输引擎实现（进程内单例；构造时自注册到 `TransferEngineLocator`）。 */
    private val transferEngineImpl: TransferEngineImpl by lazy {
        TransferEngineImpl(
            context = appContext,
            dao = transferTaskDao,
            s3Provider = { s3ClientOrNull() },
            objectListCache = objectListCache,
            downloadTargetResolver = DownloadTargetResolver(appContext),
            currentBucketProvider = { settingsRepository.settings().value.currentBucket }
        )
    }

    /** 传输引擎（对外接口）。 */
    val transferEngine: TransferEngine get() = transferEngineImpl

    /**
     * 传输进度总线：**复用引擎内部那条**，避免出现两条总线导致 UI 与前台服务读到不同进度。
     */
    val transferProgressBus: TransferProgressBus get() = transferEngineImpl.progressBus

    /** 缓存密钥管理器（缓存清理器 + 设置存储 + Keystore 包裹器）。 */
    val keyManager: KeyManager by lazy {
        KeyManager(settingsStore, secretCodec, cacheEraser)
    }

    /** 缓存擦除器（换钥前清空缩略图 / 列表缓存）。 */
    val cacheEraser: CacheEraser by lazy {
        CacheEraser(thumbnailCache, objectListCache)
    }

    /** 应用锁管理器（第 5 个可选参注入「基于 CloudflareClient 的身份校验」回调，供找回密码使用）。 */
    val appLockManager: AppLockManager by lazy {
        AppLockManager(
            settings = settingsStore,
            keyManager = keyManager,
            cacheEraser = cacheEraser,
            biometric = biometric,
            verifyCloudflareIdentity = ::verifyCloudflareIdentity
        )
    }

    /** 缩略图生成器（纯图片编码，无依赖）。 */
    val thumbnailGenerator: ThumbnailGenerator by lazy { ThumbnailGenerator() }

    /** 视频首帧抽取（framework `MediaMetadataRetriever`，不引入 media3）。 */
    val videoFrameExtractor: VideoFrameExtractor by lazy { VideoFrameExtractor() }

    /** 缩略图加载器（缓存命中直出 → 下载 → 生成 → 写缓存）。 */
    val thumbnailLoader: ThumbnailLoader by lazy {
        ThumbnailLoader(
            repo = storageRepository,
            cache = thumbnailCache,
            generator = thumbnailGenerator,
            video = videoFrameExtractor
        )
    }

    // ============ 内部工具 ============

    /** 生物识别封装：需要前台 Activity；未注册时给出可定位的异常。 */
    private val biometric: BiometricAuthenticator
        get() {
            val activity = activityRef?.get()
                ?: error("BiometricAuthenticator 需要前台 Activity：请先调用 AppContainer.attachActivity(activity)")
            return BiometricAuthenticator(activity)
        }

    /** 当前 Cloudflare 鉴权信息（缺凭证时为空白，由客户端在请求前抛出明确异常）。 */
    private fun cfAuth(): CfAuth {
        val credentials = credentialStore.load()
        return if (credentials == null) {
            CfAuth("", "", NetworkConstants.DEFAULT_JURISDICTION)
        } else {
            CfAuth(credentials.accountId, credentials.apiToken, credentials.jurisdiction)
        }
    }

    /**
     * 找回密码用的身份校验：以「accountId + apiToken」临时列一次桶；10s 超时。
     *
     * 只做连通性 / 鉴权有效性判定，不返回桶内容，避免泄漏。
     *
     * @param accountId Cloudflare Account ID
     * @param apiToken Cloudflare API Token
     * @return 校验通过返回 true
     */
    private suspend fun verifyCloudflareIdentity(accountId: String, apiToken: String): Boolean {
        if (accountId.isBlank() || apiToken.isBlank()) {
            return false
        }
        return runCatching {
            withTimeout(NetworkConstants.STARTUP_PROBE_TIMEOUT_MS) {
                CloudflareClientImpl(
                    authProvider = {
                        CfAuth(accountId, apiToken, NetworkConstants.DEFAULT_JURISDICTION)
                    },
                    httpClient = httpClient,
                    ioDispatcher = appDispatchers.io
                ).listBuckets(perPage = 1)
            }
        }.isSuccess
    }
}
