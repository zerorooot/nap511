package github.zerorooot.nap511.ui.navigation

import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import github.zerorooot.nap511.data.local.cache.FileCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

object SplashScreenManager {

    @Volatile
    var isDataReady: Boolean = false
        private set

    /**
     * 为指定的 Activity 配置启动屏生命周期与平滑退出逻辑
     *
     * @param activity 目标宿主 Activity（必须在 super.onCreate() 之前调用）
     * @param scope 在 SplashScreen 展示期间执行异步预加载：
     *      1. 等待 SettingsRepository 的 DataStore 配置加载完成；
     *      2. 快速初始化 UserSessionManager 基础凭证；
     *      3. 将耗时的磁盘缓存加载（loadAllCache）放在后台异步进行，不阻塞 SplashScreen 退场；
     *      4. 标记 isReady = true，促使 SplashScreen 平滑淡出。
     * @return 官方的 [SplashScreen] 实例，便于有额外定制需求时继续扩展
     */
    fun setup(
        activity: ComponentActivity,
        scope: CoroutineScope,
    ): SplashScreen {
        val splashScreen = activity.installSplashScreen()
        isDataReady = false

        var timeoutJob: Job? = null
        scope.launch(Dispatchers.IO) {
            runCatching {
                FileCacheManager.loadAllCache()
            }
            isDataReady = true
            timeoutJob?.cancel()
        }

        // 超时熔断保护：至多保持 2000ms，防止极端 IO 异常导致长时间卡屏
        timeoutJob = scope.launch(Dispatchers.Default) {
            delay(2000L.milliseconds)
            if (!isDataReady) {
                isDataReady = true
            }
        }

        // 官方标准门控：底层由 Jetpack 自带的 OnPreDrawListener 挂起与放行首帧绘制
        splashScreen.setKeepOnScreenCondition {
            !isDataReady
        }

        return splashScreen
    }
}
