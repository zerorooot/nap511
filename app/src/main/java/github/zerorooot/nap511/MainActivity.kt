package github.zerorooot.nap511

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.ui.theme.Nap511Theme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //不设置会报错：No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner
        initializeViewTreeOwners()
        enableEdgeToEdge()
        setContent {
            val settingsRepository = SettingsRepository.getInstance()
            val initialUiState = remember { settingsRepository.settingUiStateFlow.value }
            val settingUiState by settingsRepository.settingUiStateFlow
                .collectAsStateWithLifecycle(initialValue = initialUiState)
            val dynamicColor = settingUiState.dynamicColorEnabled
            val themeMode = settingUiState.themeMode

            val darkTheme = when (themeMode) {
                "亮色模式" -> false
                "暗色模式" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            // 实时更新状态栏和导航栏颜色，确保主题切换立即生效
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            Nap511Theme(dynamicColor = dynamicColor, darkTheme = darkTheme) {
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

    // 重点：当 Activity 被复用时，新的 Intent 会走这里
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 更新 Activity 的 intent 引用，确保 fileViewModel 能捕获最新的 Deep Link
    }
}
