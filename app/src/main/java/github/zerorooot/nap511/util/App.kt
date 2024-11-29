package github.zerorooot.nap511.util

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import github.zerorooot.nap511.bean.LoginBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class App : Application() {
    companion object {
        lateinit var instance: App
        var cookie by mutableStateOf("")
        var uid by mutableStateOf("0")

        //页面导航
        var selectedItem by mutableStateOf(ConfigUtil.MY_FILE)

        //页面手势
        var gesturesEnabled by mutableStateOf(true)

        //每次请求文件数
        var requestLimitCount: Int = 100
        lateinit var drawerState: DrawerState
        lateinit var scope: CoroutineScope
        private fun isScopeInitialized() = ::scope.isInitialized

    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        cookie = DataStoreUtil.getData(ConfigUtil.cookie, "")
        uid = DataStoreUtil.getData(ConfigUtil.uid, "0")
        requestLimitCount = DataStoreUtil.getData(ConfigUtil.requestLimitCount, "100").toInt()
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

    fun checkLogin(cookie: String): String {
        val url =
            "https://passportapi.115.com/app/1.0/web/1.0/check/sso?_${System.currentTimeMillis() / 1000}"
        val okHttpClient = OkHttpClient()
        val request: Request = Request.Builder().url(url).addHeader("cookie", cookie)
            .addHeader("Content-Type", "application/json; Charset=UTF-8")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            ).get().build()
        val body = okHttpClient.newCall(request).execute().body
        val uid = run {
            val string = body.string()
            try {
                Gson().fromJson(
                    string, LoginBean::class.java
                ).data.user_id
            } catch (e: Exception) {
                "0"
            }
        }
        return uid
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