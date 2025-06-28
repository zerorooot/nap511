package github.zerorooot.nap511.util

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogItem
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.interceptor.AbstractFilterInterceptor
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.AvatarBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.lang.Thread.UncaughtExceptionHandler
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class App : Application() {
    companion object {
        lateinit var instance: App
        var cookie by mutableStateOf("")
        var uid by mutableStateOf("0")

        //页面导航
        var selectedItem by mutableStateOf(ConfigKeyUtil.MY_FILE)

        //页面手势
        var gesturesEnabled by mutableStateOf(true)

        //每次请求文件数
        var requestLimitCount: Int = 100
        lateinit var drawerState: DrawerState
        lateinit var scope: CoroutineScope
        private fun isScopeInitialized() = ::scope.isInitialized

        //缓存fileListCache文件
        lateinit var cacheFile: File
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        cookie = DataStoreUtil.getData(ConfigKeyUtil.COOKIE, "")
        uid = DataStoreUtil.getData(ConfigKeyUtil.UID, "0")
        requestLimitCount = DataStoreUtil.getData(ConfigKeyUtil.REQUEST_LIMIT_COUNT, "100").toInt()
        cacheFile = File(this.cacheDir, "fileListCache.json")

        initLog()
    }

    fun initLog() {
        //log
        val build = LogConfiguration.Builder().tag("XLOG")
            .addInterceptor(object : AbstractFilterInterceptor() {
                override fun reject(log: LogItem?): Boolean {
                    return !DataStoreUtil.getData(ConfigKeyUtil.LOG_SCREEN, true)
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
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            XLog.enableStackTrace(50).e("程序崩溃退出", e)
            handler?.uncaughtException(thread, e)
        }

        val uncaughtExceptionHandler = Thread.currentThread().uncaughtExceptionHandler
        Thread.currentThread().uncaughtExceptionHandler = object : UncaughtExceptionHandler {
            override fun uncaughtException(t: Thread, e: Throwable) {
                XLog.enableStackTrace(50).e("程序崩溃退出", e)
                uncaughtExceptionHandler?.uncaughtException(t, e)
            }
        }
    }

    fun toast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun openDrawerState() {
        if (isScopeInitialized()) {
            scope.launch {
                drawerState.open()
            }
        }

    }

    fun getStringRes(id: Int): String {
        return getString(id)
    }

    fun checkLogin(cookie: String): Pair<Boolean, String> {
        val gson = Gson()
        val okHttpClient = OkHttpClient().newBuilder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Cookie", cookie)
                        .addHeader("Content-Type", "application/json; Charset=UTF-8")
                        .addHeader(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
                        )
                        .build()
                );
            }).build()
        val avatarUrl = "https://my.115.com/?ct=ajax&ac=nav&_${System.currentTimeMillis() / 1000}"
        val avatarUrlRequest: Request = Request.Builder().url(avatarUrl).get().build()
        val avatarUrlRespBody = okHttpClient.newCall(avatarUrlRequest).execute().body.string()
        Log.d("nap511 checkLogin avatarBean", avatarUrlRespBody)

        //{"state":true,"data":{"expire":1,"user_name":"Test","face":"face","user_id":11}}
        val avatarBean = run {
            try {
                gson.fromJson(
                    gson.fromJson(avatarUrlRespBody, JsonObject::class.java).get("data"),
                    AvatarBean::class.java
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (avatarBean == null) {
            return Pair(false, "验证失败，请重试")
        }

        avatarBean.expireString = Instant.ofEpochSecond(avatarBean.expire)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        DataStoreUtil.putData(ConfigKeyUtil.COOKIE, cookie)
        DataStoreUtil.putData(ConfigKeyUtil.UID, avatarBean.userId)
        DataStoreUtil.putData(ConfigKeyUtil.AVATAR_BEAN, gson.toJson(avatarBean))
        return Pair(true, "登陆成功,重启中～")
    }

    /**
     * 判断允许通知，是否已经授权
     * 返回值为true时，通知栏打开，false未打开。
     * @param context 上下文
     */
    fun isNotificationEnabled(context: Context): Boolean {
        val CHECK_OP_NO_THROW = "checkOpNoThrow"
        val OP_POST_NOTIFICATION = "OP_POST_NOTIFICATION"
        val mAppOps = context.getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val appInfo = context.applicationInfo
        val pkg = context.applicationContext.packageName
        val uid = appInfo.uid
        val appOpsClass: Class<*>?
        /* Context.APP_OPS_MANAGER */try {
            appOpsClass = Class.forName(AppOpsManager::class.java.name)
            val checkOpNoThrowMethod: Method = appOpsClass.getMethod(
                CHECK_OP_NO_THROW, Integer.TYPE, Integer.TYPE,
                String::class.java
            )
            val opPostNotificationValue: Field = appOpsClass.getDeclaredField(OP_POST_NOTIFICATION)
            val value = opPostNotificationValue.get(Int::class.java) as Int
            return checkOpNoThrowMethod.invoke(
                mAppOps,
                value,
                uid,
                pkg
            ) as Int == AppOpsManager.MODE_ALLOWED
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * 跳转到app的设置界面--开启通知
     * @param context
     */
    fun goToNotificationSetting(context: Context) {
        val intent = Intent()
        // android 8.0引导
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS")
        intent.putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun closeDrawerState() {
        if (isScopeInitialized()) {
            scope.launch {
                drawerState.close()
            }
        }
    }
}