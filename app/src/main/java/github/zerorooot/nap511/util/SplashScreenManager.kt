package github.zerorooot.nap511.util

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.network.UserSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 启动屏统一管理与解耦封装类 (SplashScreenManager)
 *
 * 设计目标：
 * 1. 降低 Activity 与 SplashScreen API 细节的直接耦合；
 * 2. 统一管理启动屏保持就绪条件，防止启动期并发导致的多处维护困难；
 * 3. 封装丝滑平顺的渐隐退场（Fade-out）动画，避免加载完成后突兀切屏，提升用户体验与视觉连贯性；
 * 4. 高复用设计：可在任何继承自 ComponentActivity 的组件中一行初始化。
 */
object SplashScreenManager {

    /** 启动屏退出时的淡出动画时长（毫秒）优化：缩短至 200ms 以加速首屏视觉感知 */
    private const val EXIT_ANIMATION_DURATION = 200L

    var isReady: Boolean by mutableStateOf(false)
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
        settingsRepository: SettingsRepository,
    ): SplashScreen {
        // 1. 初始化并挂载系统 SplashScreen
        val splashScreen = activity.installSplashScreen()

        scope.launch(Dispatchers.IO) {
            // 等待 DataStore 基础配置载入完成
            val settings = settingsRepository.settingUiStateFlow.first { it.isLoaded }

            // 1. 同步快速完成会话网络凭证初始化
            val limit = settings.requestLimitCount.toIntOrNull() ?: 200
            UserSessionManager.init(
                cookie = settings.cookie,
                uid = settings.uid,
                requestLimitCount = limit
            )

            // 2. 【冷启动性能优化】磁盘文件缓存（如 0.json 等）在后台异步加载，不阻塞 SplashScreen 淡出退场
            launch {
                FileCacheManager.saveRequestCache = settings.saveRequestCache
                FileCacheManager.loadAllCache()
            }

            // 3. 核心账号凭证就绪后，立即通知 UI 线程将 isReady 设为 true，触发 SplashScreen 退场
            withContext(Dispatchers.Main) {
                isReady = true
            }
        }

        // 2. 挂起控制：在核心状态（如 DataStore 配置及预加载）加载完成前，保持动态 SplashScreen 显示
        // 返回 true 表示正在加载必要配置，继续保持动画展示；
        //返回 false 表示就绪，触发平滑退出动画。
        splashScreen.setKeepOnScreenCondition {
            !isReady
        }

        // 3. 定制优雅平滑的退场动效（Alpha 渐隐动画）
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashView = splashScreenViewProvider.view

            // 使用 ObjectAnimator 创建透明度从 1.0f 到 0.0f 的平滑过渡
            val fadeOut = ObjectAnimator.ofFloat(
                splashView,
                View.ALPHA,
                1f,
                0f
            ).apply {
                interpolator = AccelerateInterpolator()
                duration = EXIT_ANIMATION_DURATION
                // 动画结束时必须调用 remove()，由系统安全移除 SplashScreen 视图层，防止内存泄漏
                doOnEnd {
                    splashScreenViewProvider.remove()
                }
            }

            fadeOut.start()
        }

        return splashScreen
    }
}
