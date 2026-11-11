package github.zerorooot.nap511.util

import android.app.Application
import android.os.Build
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
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
import github.zerorooot.nap511.BuildConfig
import github.zerorooot.nap511.di.AppContainer
import github.zerorooot.nap511.di.DefaultAppContainer
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.network.NetworkClient
import github.zerorooot.nap511.util.network.OneOneFiveImageExpirationInterceptor
import github.zerorooot.nap511.util.network.UserSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

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

@OptIn(ExperimentalFoundationApi::class)
class App : Application(), ImageLoaderFactory, Configuration.Provider {

    /** 应用级依赖注入容器，支持测试环境动态替换为 FakeAppContainer */
    var container: AppContainer = DefaultAppContainer(this)

    companion object {
        lateinit var instance: App
            internal set
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isLogEnabled = false

    override fun onCreate() {
        // 1.12.0 起新文字上下文菜单默认开启，但在 Dialog 内的 TextField
        // 长按/选中后剪切、复制、粘贴点击无效。回退到旧实现规避该回归。
        ComposeFoundationFlags.isNewContextMenuEnabled = false
        super.onCreate()
        instance = this
        // 预热 SettingsRepository，在应用进程启动时即触发后台异步预读 DataStore
        val settingsRepository = SettingsRepository.getInstance()
        appScope.launch {
            // 初始化 FileCacheManager 单例目录
            FileCacheManager.init(File(this@App.cacheDir, "file_list_cache"))

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


        // 同步初始化 XLog 日志框架，确保在冷启动或独立 Activity 启动时 XLog 已就绪
        initLog()

        // 启动时在后台线程清理过期 7 天的 Coil 图片缓存
        appScope.launch {
            cleanExpiredCoilDiskCache(this@App)
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
            SettingsRepository.getDataFlow(ConfigKeyUtil.LOG, false)
                .distinctUntilChanged()
                .collect { enabled ->
                    val wasDisabled = !isLogEnabled
                    isLogEnabled = enabled
                    if (enabled && wasDisabled) {
                        XLog.i("-----------------------init-----------------------------------")
                    }
                }
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

    /**
     * 全局兼容轻提示方法
     * 统一委托给 AppContainer 中的 ToastProvider 单例，与全量 ViewModel 弹窗共享互斥与主线程生命周期。
     */
    fun toast(text: String) {
        container.toastProvider.showToast(text)
    }

    fun getStringRes(id: Int): String {
        return getString(id)
    }


    override fun newImageLoader(): ImageLoader {
        // 为 Coil 配置带有过期拦截器的 OkHttpClient
        val okHttpClient = NetworkClient.sharedOkHttpClient.newBuilder()
            .addInterceptor(OneOneFiveImageExpirationInterceptor())
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient) // 传入配置后的 OkHttpClient
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
                XLog.i("App: WorkManager on-demand configuration initialized")
            }.onFailure {
                android.util.Log.i("App", "WorkManager on-demand configuration initialized")
            }
            return Configuration.Builder()
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
                .build()
        }
}
