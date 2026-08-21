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
            }).build()
        //todo  日志输出代码位置
        /**
         *     val stackTrace = Throwable().stackTrace
         *                     val caller = stackTrace[1] // 获取调用者信息
         *                     val logTag = "${caller.fileName}:${caller.lineNumber}" // 显示文件名和行号
         */
        val print = FilePrinter
            .Builder(this.cacheDir.absolutePath)
            .cleanStrategy(FileLastModifiedCleanStrategy(7 * 24 * 60 * 60 * 1000))
            .flattener(ClassicFlattener())
            .build()
        XLog.init(build, AndroidPrinter(true), print);
        XLog.d("-----------------------init-----------------------------------")
//        val handler = Thread.getDefaultUncaughtExceptionHandler()
//        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
//            XLog.enableStackTrace(50).e("程序崩溃退出", e)
//            handler?.uncaughtException(thread, e)
//        }
//
//        val uncaughtExceptionHandler = Thread.currentThread().uncaughtExceptionHandler
//        Thread.currentThread().uncaughtExceptionHandler = UncaughtExceptionHandler { t, e ->
//            XLog.enableStackTrace(50).e("程序崩溃退出", e)
//            uncaughtExceptionHandler?.uncaughtException(t, e)
//        }
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
     * 通过账号密码登录115网盘
     * @param username 账号（手机号或用户名）
     * @param password 密码
     * @return Pair<成功标志, 消息>
     */
    suspend fun accountLogin(username: String, password: String) {
        val pair = try {
            // RSA 加密密码
            val rsaUtil = MyRsaUtil()
            val encryptedPassword = rsaUtil.encrypt(password)

            val jsonBody = """{"login_name":"$username","login_pass":"$encryptedPassword"}"""
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val client = OkHttpClient().newBuilder()
                .followRedirects(false)
                .build()

            val request = Request.Builder()
                .url("https://passportapi.115.com/app/1.0/web/1.0/login")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader(
                    "User-Agent",
                    ConfigKeyUtil.USER_AGENT
                )
                .addHeader("Referer", "https://passport.115.com/")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body.string()


            XLog.d("nap511 accountLogin response: $responseBody")
            XLog.d("nap511 accountLogin code: ${response.code}")

            // 从 Set-Cookie 头中提取所有 cookie
            val cookieHeaders = response.headers("Set-Cookie")
            val cookieBuilder = StringBuilder()
            for (cookieHeader in cookieHeaders) {
                val cookiePart = cookieHeader.split(";")[0].trim()
                if (cookiePart.isNotEmpty()) {
                    if (cookieBuilder.isNotEmpty()) cookieBuilder.append("; ")
                    cookieBuilder.append(cookiePart)
                }
            }
            val cookieString = cookieBuilder.toString()

            XLog.d("nap511 accountLogin cookies: $cookieString")

            if (cookieString.isEmpty()) {
                // 尝试从响应 JSON 中获取 cookie
                try {
                    val jsonObject = Gson().fromJson(responseBody, JsonObject::class.java)
                    val state = jsonObject.get("state")?.asBoolean ?: false
                    if (!state) {
                        val msg =
                            jsonObject.get("message")?.asString ?: jsonObject.get("msg")?.asString
                            ?: "登录失败"
                        Pair(false, msg)
                    } else {
                        // 尝试从 data 中获取 cookie
                        val data = jsonObject.getAsJsonObject("data")
                        val cookieObj = data?.getAsJsonObject("cookie")
                        if (cookieObj != null) {
                            val cookieStr = cookieObj.entrySet()
                                .joinToString("; ") { "${it.key}=${it.value.asString}" }
                            checkLogin(cookieStr)
                            Pair(true, "登录中...")
                        } else {
                            Pair(false, "登录失败：无法获取Cookie，请尝试通过网页登录")
                        }
                    }
                } catch (e: Exception) {
                    Pair(false, "登录失败：${e.message}")
                }

            } else {
                Pair(false, "登录失败，$cookieString")
            }
        } catch (e: Exception) {
            XLog.e("nap511 accountLogin error", e)
            Pair(false, "登录失败：${e.message}")
        }

        XLog.d("nap511 accountLogin pair $pair")
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