package github.zerorooot.nap511.util

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    fun closeDrawerState() {
        if (isScopeInitialized()) {
            scope.launch {
                drawerState.close()
            }
        }
    }
}