package github.zerorooot.nap511.ui.system

import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.ui.navigation.Route

val LocalSystemBarsController: ProvidableCompositionLocal<SystemBarsController> =
    compositionLocalOf { error("SystemBarsController not provided") }

val LocalSystemBarsRoute: ProvidableCompositionLocal<Route?> =
    compositionLocalOf { null }

/**
 * 全局系统栏状态控制器
 * 维护各路由的期望系统栏可见性，负责与 Activity Window 进行 WindowInsetsController 交互
 */
@Stable
class SystemBarsController internal constructor(
    private val window: Window?
) {
    internal val visibilityMap = mutableStateMapOf<Route, Boolean>()
    internal var currentRoute: Route? = null
    internal var lastAppliedVisibility: Boolean? = null

    /**
     * 更新指定路由的系统栏可见性期望
     */
    fun setRouteVisibility(route: Route, visible: Boolean) {
        if (visibilityMap[route] != visible) {
            XLog.d("SystemBarsController: setRouteVisibility route=$route, visible=$visible")
            visibilityMap[route] = visible
        }
    }

    /**
     * 将仲裁后的可见性目标直接下发给 Activity Window
     */
    internal fun applyToWindow(visible: Boolean) {
        val targetWindow = window ?: return
        XLog.d("SystemBarsController: applyToWindow visible=$visible (previous=$lastAppliedVisibility)")
        val insetsController =
            WindowCompat.getInsetsController(targetWindow, targetWindow.decorView)
        if (visible) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        lastAppliedVisibility = visible
    }
}

/**
 * 全局系统栏统管容器
 * 统一仲裁 currentRoute、抽屉交互与生命周期，利用 SideEffect 保证首帧提交零闪烁
 */
@Composable
fun ProvideSystemBars(
    currentRoute: Route,
    isDrawerActive: Boolean,
    activeRoutes: Set<Route>? = null,
    content: @Composable () -> Unit
) {
    val activity = LocalActivity.current
    val controller = remember(activity) { SystemBarsController(activity?.window) }
    controller.currentRoute = currentRoute

    // 内存安全：自动清理已从 BackStack 出栈的路由记忆
    activeRoutes?.let { routes ->
        controller.visibilityMap.keys.retainAll(routes)
    }

    // 综合仲裁最终可见性：抽屉手势优先保护安全区 -> 路由记忆 -> 路由固有默认策略
    val targetVisibility = isDrawerActive ||
            (controller.visibilityMap[currentRoute] ?: currentRoute.defaultSystemBarsVisible)

    // 利用 SideEffect 在 Composition 提交时同步推入 Window，先于绘制阶段，杜绝 1 帧跳闪
    SideEffect {
        if (controller.lastAppliedVisibility != targetVisibility) {
            controller.applyToWindow(targetVisibility)
        }
    }

    // 前后台切换 (ON_RESUME) 同步恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                XLog.d("SystemBarsController: ON_RESUME sync targetVisibility=$targetVisibility")
                controller.applyToWindow(targetVisibility)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalSystemBarsController provides controller) {
        content()
    }
}

/**
 * 业务页面声明式极简 API（任意 Composable 均可一行调用，无 route 耦合）
 */
@Composable
fun SystemBarsVisibility(visible: Boolean) {
    val controller = LocalSystemBarsController.current
    val route = LocalSystemBarsRoute.current ?: controller.currentRoute ?: return
    DisposableEffect(controller, route, visible) {
        XLog.d("SystemBarsVisibility: bind route=$route, visible=$visible")
        controller.setRouteVisibility(route, visible)
        onDispose {}
    }
}
