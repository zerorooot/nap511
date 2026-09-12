package github.zerorooot.nap511.util


import android.app.Application
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
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
import github.zerorooot.nap511.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
class App : Application(), ImageLoaderFactory {

    private var currentToast: Toast? = null

    companion object {
        lateinit var instance: App
            private set

        //缓存fileListCache文件
        lateinit var cacheFile: File
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
        cacheFile = File(this.cacheDir, "fileListCache.json")

        // 预热 SettingsRepository，在应用进程启动时即触发后台异步预读 DataStore
        SettingsRepository.getInstance()

        // 启动时在后台线程清理过期 7 天的 Coil 图片缓存
        appScope.launch {
            cleanExpiredCoilDiskCache(this@App)
        }
        appScope.launch {
            val initialCookie = SettingsRepository.getDataSuspend(ConfigKeyUtil.COOKIE, "")
            val initialUid = SettingsRepository.getDataSuspend(ConfigKeyUtil.UID, "")
            val initialLimit =
                SettingsRepository.getDataSuspend(ConfigKeyUtil.REQUEST_LIMIT_COUNT, "200")
                    .toIntOrNull()
                    ?: 200
            UserSessionManager.init(initialCookie, initialUid, initialLimit)

            isLogEnabled = SettingsRepository.getDataSuspend(ConfigKeyUtil.LOG, false)
            initLog()
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
        XLog.i("-----------------------init-----------------------------------")
    }

    private val toastScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun toast(text: String) {
        toastScope.launch {
            currentToast?.cancel()
            currentToast = Toast.makeText(instance, text, Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    fun getStringRes(id: Int): String {
        return getString(id)
    }


    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
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


}