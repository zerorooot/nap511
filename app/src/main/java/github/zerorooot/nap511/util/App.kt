package github.zerorooot.nap511.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogItem
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.interceptor.AbstractFilterInterceptor
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.Base115Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.properties.Delegates


import com.elvishew.xlog.interceptor.Interceptor

class AutoTagInterceptor(
    private val defaultTag: String = "XLOG",
    private val maxTagLength: Int = 23
) : Interceptor {
    override fun intercept(log: LogItem): LogItem {
        // 仅当用户未显式调用 XLog.tag("CustomTag") 时，才通过堆栈动态推导
        if (log.tag == defaultTag) {
            log.tag = resolveCallerClassName()
        }
        return log
    }

    private fun resolveCallerClassName(): String {
        val stackTrace = Throwable().stackTrace
        val caller = stackTrace.firstOrNull { element ->
            val className = element.className
            !className.startsWith("com.elvishew.xlog.") &&
                    !className.startsWith("java.lang.") &&
                    !className.startsWith("dalvik.system.") &&
                    !className.contains(AutoTagInterceptor::class.java.simpleName)
        } ?: return defaultTag

        // 提取简短类名，去除包名前缀及内部类、匿名类的 '$' 符号
        val simpleName = caller.className.substringAfterLast('.').substringBefore('$')
        val string = if (simpleName.length > maxTagLength) {
            simpleName.substring(0, maxTagLength)
        } else {
            simpleName
        }
        return "$string-$defaultTag"
    }
}

@OptIn(ExperimentalFoundationApi::class)
class App : Application(), ImageLoaderFactory {
    private val okHttpClient by lazy { OkHttpClient() }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private var currentToast: Toast? = null

    companion object {
        lateinit var instance: App
            private set
        var cookie = ""
        var uid = "0"

        //每次请求文件数
        var requestLimitCount by Delegates.notNull<Int>()

        //缓存fileListCache文件
        lateinit var cacheFile: File
    }

    override fun onCreate() {
        // 1.12.0 起新文字上下文菜单默认开启，但在 Dialog 内的 TextField
        // 长按/选中后剪切、复制、粘贴点击无效。回退到旧实现规避该回归。
        ComposeFoundationFlags.isNewContextMenuEnabled = false
        super.onCreate()
        instance = this
        cookie = DataStoreUtil.getData(ConfigKeyUtil.COOKIE, "")
        uid = DataStoreUtil.getData(ConfigKeyUtil.UID, "0")
        requestLimitCount = DataStoreUtil.getData(ConfigKeyUtil.REQUEST_LIMIT_COUNT, "200").toInt()
        cacheFile = File(this.cacheDir, "fileListCache.json")

        initLog()
    }

    fun initLog() {
        //log
        val build = LogConfiguration.Builder().tag("XLOG")
            .addInterceptor(object : AbstractFilterInterceptor() {
                override fun reject(log: LogItem?): Boolean {
                    return !DataStoreUtil.getData(ConfigKeyUtil.LOG, true)
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
        XLog.d("-----------------------init-----------------------------------")
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

    suspend fun checkLogin(cookie: String) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis() / 1000
        val avatarUrl = "https://my.115.com/?ct=ajax&ac=nav&_$timestamp"
        val ua = ConfigKeyUtil.USER_AGENT
        val gson = Gson()

        val request = Request.Builder()
            .url(avatarUrl)
            .addHeader("Cookie", cookie)
            .addHeader("User-Agent", ua)
            .get()
            .build()

        // 使用 runCatching 捕获网络/解析异常，避免 try-catch 嵌套
        val pair = try {
            // 使用 .use 自动关闭 Response 资源
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Pair(false, "网络请求失败: HTTP ${response.code}")
                }

                val bodyStr = response.body.string()
                XLog.d("checkLogin avatarResp: $bodyStr")

                // 4. 一次性反序列化，避免 Gson 嵌套双重解析
                val type = object : TypeToken<Base115Response<AvatarBean>>() {}.type
                val result = gson.fromJson<Base115Response<AvatarBean>>(bodyStr, type)

                val avatarBean = result?.data ?: return@use Pair(false, "验证失败，请重试")

                // 5. 格式化过期时间
                avatarBean.expireString = Instant.ofEpochSecond(avatarBean.expire)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

                // 6. 持久化数据
                DataStoreUtil.putDataSuspend(ConfigKeyUtil.COOKIE, cookie)
                DataStoreUtil.putDataSuspend(ConfigKeyUtil.UID, avatarBean.userId)
                DataStoreUtil.putDataSuspend(ConfigKeyUtil.AVATAR_BEAN, gson.toJson(avatarBean))

                Pair(true, "登陆成功,重启中～")
            }
        } catch (e: Exception) {
            XLog.e("checkLogin Check login failed", e)
            Pair(false, "验证失败: ${e.localizedMessage ?: "未知错误"}")
        }

        if (pair.first) {
            ProcessPhoenix.triggerRebirth(applicationContext);
        }
        toast(pair.second)
    }



    /**
     * 判断允许通知，是否已经授权
     * 返回值为true时，通知栏打开，false未打开。
     * @param context 上下文
     */
    fun isNotificationEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 跳转到app的设置界面--开启通知
     * @param context
     */
    fun goToNotificationSetting(context: Context) {
        val intent = Intent()
        // android 8.0引导
        intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
        intent.putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
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
            // 忽略服务器的 Cache-Control 限制，强制使用本地磁盘缓存
            .respectCacheHeaders(false)
            // 你也可以在这里配置全局的淡入淡出效果、默认占位图等
            .crossfade(true)
            .build()
    }

    /**
     * {"jsonrpc":"2.0","id":"nap511","method":"aria2.getVersion","params":["token:11"]}
     */
    fun checkAria2(aria2Url: String, aria2Token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val requestJson = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", "nap511")
                addProperty("method", "aria2.getVersion")
                add("params", JsonArray().apply {
                    if (aria2Token.isNotEmpty()) add("token:$aria2Token")
                })
            }

            val request = Request.Builder()
                .url(aria2Url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val message = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use "aria2配置失败, HTTP ${response.code}"

                    val bodyStr = response.body.string()
                    val bodyJson = JsonParser.parseString(bodyStr).asJsonObject

                    if (bodyJson.has("error")) {
                        val errorMsg = bodyJson.getAsJsonObject("error")?.get("message")?.asString
                        "aria2配置失败, $errorMsg"
                    } else {
                        DataStoreUtil.putDataSuspend(ConfigKeyUtil.ARIA2_URL, aria2Url)
                        DataStoreUtil.putDataSuspend(ConfigKeyUtil.ARIA2_TOKEN, aria2Token)
                        "aria2配置成功，请重新下载文件"
                    }
                }
            }.getOrElse { e ->
                "aria2配置失败, ${e.localizedMessage ?: "未知错误"}"
            }

            toast(message)
        }

    }
}