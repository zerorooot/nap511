package github.zerorooot.nap511

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import github.zerorooot.nap511.data.local.cache.FileCacheManager
import github.zerorooot.nap511.data.repository.SettingsRepository
import github.zerorooot.nap511.platform.LocalToastProvider
import github.zerorooot.nap511.platform.ToastProvider
import github.zerorooot.nap511.ui.navigation.RootBackPressPolicy
import github.zerorooot.nap511.ui.navigation.SplashScreenManager
import github.zerorooot.nap511.ui.screen.MainScreen
import github.zerorooot.nap511.ui.theme.Nap511Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var toastProvider: ToastProvider

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val rootBackPolicy by lazy {
        RootBackPressPolicy(
            onShowToast = { msg ->
                toastProvider.showToast(msg)
            },
            onExecuteExit = {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { FileCacheManager.deleteOrphansAndExpiredFile() }
                }
                if (!moveTaskToBack(true)) {
                    finish()
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 统一通过 SplashScreenManager 管理动态启动屏的安装、挂起判断与渐隐退场
        // 确保必须在 super.onCreate 之前调用
        // 在 SplashScreen 显示期间发起预加载流程（数据就绪后自动将 isReady 设为 true 促使 SplashScreen 淡出）
        SplashScreenManager.setup(this, lifecycleScope)
        super.onCreate(savedInstanceState)

        // 挂载常驻根拦截器
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                rootBackPolicy.handleRootBackPress()
            }
        })
        //不设置会报错：No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner
        initializeViewTreeOwners()
        enableEdgeToEdge()

        setContent {
            val settingUiState by settingsRepository.settingUiStateFlow
                .collectAsStateWithLifecycle()

            // 3. 计算最终的主题配色模式
            val dynamicColor = settingUiState.dynamicColorEnabled
            val themeMode = settingUiState.themeMode
            val darkTheme = when (themeMode) {
                "亮色模式" -> false
                "暗色模式" -> true
                else -> isSystemInDarkTheme()
            }

            // 4. 实时更新系统状态栏与导航栏
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(TRANSPARENT)
                    } else {
                        SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(TRANSPARENT)
                    } else {
                        SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                    }
                )
            }

            // 5. 渲染应用核心内容
            Nap511Theme(dynamicColor = dynamicColor, darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalToastProvider provides toastProvider
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MainScreen(
                            intent = intent,
                            settingUiState = settingUiState
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        rootBackPolicy.reset()
    }

    // 重点：当 Activity 被复用时，新的 Intent 会走这里
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 更新 Activity 的 intent 引用，确保 fileViewModel 能捕获最新的 Deep Link
    }
}
