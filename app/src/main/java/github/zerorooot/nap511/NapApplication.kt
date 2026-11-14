package github.zerorooot.nap511

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogItem
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.interceptor.AbstractFilterInterceptor
import com.elvishew.xlog.interceptor.Interceptor
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import dagger.hilt.android.HiltAndroidApp
import github.zerorooot.nap511.data.repository.SettingsRepository
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.data.local.cache.FileCacheManager
import github.zerorooot.nap511.util.cleanExpiredCoilDiskCache
import github.zerorooot.nap511.data.network.client.NetworkClient
import github.zerorooot.nap511.data.network.client.OneOneFiveImageExpirationInterceptor
import github.zerorooot.nap511.data.network.client.UserSessionManager
import github.zerorooot.nap511.util.resolveCallerTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class AutoTagInterceptor(
    private val defaultTag: String = "XLOG"
) : Interceptor {
    override fun intercept(log: LogItem): LogItem {
        // 仅当用户未显式调用 XLog.tag("CustomTag") 时，才通过堆栈动态推导
        if (log.tag == defaultTag) {
            val callerTag = resolveCallerTag(
                defaultTag = defaultTag,
                ignoredPackages = listOf("com.elvishew.xlog.", "AutoTagInterceptor")
            )
            log.tag = if (callerTag == defaultTag) defaultTag else callerTag
        }
        return log
    }
}

@HiltAndroidApp
@OptIn(ExperimentalFoundationApi::class)
class NapApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isLogEnabled = false

    override fun onCreate() {
        // 1.12.0 起新文字上下文菜单默认开启，但在 Dialog 内的 TextField
        // 长按/选中后剪切、复制、粘贴点击无效。回退到旧实现规避该回归。
        ComposeFoundationFlags.isNewContextMenuEnabled = false
        super.onCreate()

        // 同步初始化 FileCacheManager 单例目录，确保任何下游组件启动时缓存目录已就绪
        FileCacheManager.init(File(this@NapApplication.cacheDir, "file_list_cache"))

        // 同步初始化 XLog 日志框架，确保在冷启动或独立 Activity 启动时 XLog 已就绪
        initLog()

        // 预热 SettingsRepository，在应用进程启动时即触发后台异步预读 DataStore
        //为了防护部分脱离 Hilt 容器的纯 JVM 单元测试（如直接手动 val app = NapApplication() 测试某个局部工具方法，未触发 super.onCreate()），
        // 避免测试抛出 Kotlin 的 UninitializedPropertyAccessException。
        if (::settingsRepository.isInitialized) {
            appScope.launch {
                val settings = settingsRepository.settingUiStateFlow.first { it.isLoaded }
                // 同步快速完成会话网络凭证初始化
                val limit = settings.requestLimitCount.toIntOrNull() ?: 200
                UserSessionManager.init(
                    cookie = settings.cookie,
                    uid = settings.uid,
                    requestLimitCount = limit
                )

                FileCacheManager.saveRequestCache = settings.saveRequestCache
            }

            // 持续同步 DataStore 中 saveRequestCache 设置至 FileCacheManager 单例
            appScope.launch {
                settingsRepository.settingUiStateFlow
                    .map { it.saveRequestCache }
                    .distinctUntilChanged()
                    .collect { saveCache ->
                        FileCacheManager.saveRequestCache = saveCache
                    }
            }

            // 实时监听 SettingUiState 中的 LOG 开关状态更新 isLogEnabled
            appScope.launch {
                settingsRepository.getDataFlow(ConfigKeyUtil.LOG, false)
                    .distinctUntilChanged()
                    .collect { enabled ->
                        //在本次更新发生前，日志是否曾经是关闭状态
                        val wasDisabled = !isLogEnabled
                        isLogEnabled = enabled
                        //当前变为了开启，且之前处于关闭状态
                        if (enabled && wasDisabled) {
                            XLog.i("-----------------------init-----------------------------------")
                        }
                    }
            }
        }

        // 启动时在后台线程清理过期 7 天的 Coil 图片缓存
        appScope.launch {
            cleanExpiredCoilDiskCache(this@NapApplication)
        }
    }

    fun initLog() {
        //log
        val build = LogConfiguration.Builder().tag("XLOG")
            .addInterceptor(object : AbstractFilterInterceptor() {
                override fun reject(log: LogItem?): Boolean {
                    return !isLogEnabled
                }
            })
            .addInterceptor(AutoTagInterceptor())
            .build()
        val print = FilePrinter
            .Builder(this.cacheDir.absolutePath)
            .cleanStrategy(FileLastModifiedCleanStrategy(7 * 24 * 60 * 60 * 1000))
            .flattener(ClassicFlattener())
            .build()
        XLog.init(build, AndroidPrinter(true), print);
    }


    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(NetworkClient.imageOkHttpClient) // 使用独立 Dispatcher(maxRequestsPerHost=20) 与独立连接池的专属客户端
            .components {
                // 注册 GIF 解码器
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnail_cache"))
                    .maxSizePercent(0.20) // 占用 20% 的可用磁盘空间
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // 忽略服务器的 Cache-Control 限制，强制使用本地磁盘缓存
            .respectCacheHeaders(false)
            // 你也可以在这里配置全局的淡入淡出效果、默认占位图等
            .crossfade(true)
            .build()
    }

    // 惰性按需初始化 WorkManager，避免在应用启动阶段阻塞主线程
    override val workManagerConfiguration: Configuration
        get() {
            runCatching {
                XLog.i("NapApplication: WorkManager on-demand configuration initialized")
            }.onFailure {
                Log.i("NapApplication", "WorkManager on-demand configuration initialized")
            }
            val builder = Configuration.Builder()
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            // 防护：当运行在 NapApplicationTest 等纯单测环境中未走 Hilt 注入时，避免 UninitializedPropertyAccessException
            if (::workerFactory.isInitialized) {
                builder.setWorkerFactory(workerFactory)
            }
            return builder.build()
        }
}
