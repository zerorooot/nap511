package github.zerorooot.nap511.platform

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.staticCompositionLocalOf
import com.elvishew.xlog.XLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息与轻提示通知抽象接口
 * 解耦领域模块对 Android 系统 Toast 与 Application 单例的依赖，
 * 支持在单元测试中进行消息捕获与断言，或在不同 UI 形态下切换为 Snackbar/日志输出。
 */
interface ToastProvider {
    /** 显示指定文本的轻提示 */
    fun showToast(message: String, isLong: Boolean = false)

    /** 显示指定字符串资源 ID 的轻提示 */
    fun showToast(resId: Int)
}

/**
 * Compose 静态轻提示 CompositionLocal 提供者
 * 顶层由 MainActivity.setContent 注入应用级 ToastProvider 单例。
 * 默认提供安全的 No-op 空实现，保障 @Preview 与隔离单测环境永不抛出异常。
 */
val LocalToastProvider = staticCompositionLocalOf<ToastProvider> {
    object : ToastProvider {
        override fun showToast(message: String, isLong: Boolean) {}
        override fun showToast(resId: Int) {}
    }
}

/**
 * Android 平台 Toast 提示器实现
 * 统管主线程切换、Toast 连续触发时的及时取消，保证体验平滑。
 * 强制仅持有 ApplicationContext，杜绝 Context 泄漏。
 */
@Singleton
class AndroidToastProvider @Inject constructor(
    @ApplicationContext context: Context
) : ToastProvider {
    private val appContext: Context = context.applicationContext ?: context
    private var currentToast: Toast? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun showToast(message: String, isLong: Boolean) {
        if (message.isBlank()) return
        XLog.d("AndroidToastProvider: $message")
        scope.launch {
            currentToast?.cancel()
            val duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            currentToast = Toast.makeText(appContext, message, duration).also {
                it.show()
            }
        }
    }

    override fun showToast(resId: Int) {
        showToast(appContext.getString(resId))
    }
}
