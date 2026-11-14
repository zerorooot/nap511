package github.zerorooot.nap511

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
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
import javax.inject.Inject
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.SplashScreenManager
import github.zerorooot.nap511.viewmodel.platform.LocalToastProvider
import github.zerorooot.nap511.viewmodel.platform.ToastProvider

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var toastProvider: ToastProvider

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // 统一通过 SplashScreenManager 管理动态启动屏的安装、挂起判断与渐隐退场
        // 确保必须在 super.onCreate 之前调用
        // 在 SplashScreen 显示期间发起预加载流程（数据就绪后自动将 isReady 设为 true 促使 SplashScreen 淡出）
        SplashScreenManager.setup(this, lifecycleScope)
        super.onCreate(savedInstanceState)
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
                        SystemBarStyle.light(
                            TRANSPARENT,
                            TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            TRANSPARENT,
                            TRANSPARENT
                        )
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
                            settingUiState = settingUiState,
                            onMoveTaskToBack = { moveTaskToBack(it) }
                        )
                    }
                }
            }
        }
    }

    // 重点：当 Activity 被复用时，新的 Intent 会走这里
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 更新 Activity 的 intent 引用，确保 fileViewModel 能捕获最新的 Deep Link
    }
}
