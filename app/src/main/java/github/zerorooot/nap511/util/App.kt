package github.zerorooot.nap511.util

import android.app.Application
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

class App : Application() {
    companion object {
        lateinit var instance: App
        var cookie by mutableStateOf("")

        //页面导航
        var selectedItem by mutableStateOf("我的文件")

        //页面手势
        var gesturesEnabled by mutableStateOf(true)

        //验证账号网址
        var captchaUrl by mutableStateOf("")

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
            .addHeader("Content-Type", "application/json; Charset=UTF-8").addHeader(
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

    fun closeDrawerState() {
        if (isScopeInitialized()) {
            scope.launch {
                drawerState.close()
            }
        }
    }
}