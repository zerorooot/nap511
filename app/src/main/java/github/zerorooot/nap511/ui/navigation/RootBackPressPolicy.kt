package github.zerorooot.nap511.ui.navigation

import android.os.SystemClock

/**
 * 根返回防误退拦截策略控制器 (RootBackPressPolicy)
 * 统一管理双击/双滑返回桌面的时间窗口判定、提示、退出触发与连击状态保护
 */
class RootBackPressPolicy(
    private val debounceIntervalMs: Long = 1500L,
    private val uptimeProvider: () -> Long = { SystemClock.uptimeMillis() },
    private val onShowToast: (String) -> Unit,
    private val onExecuteExit: () -> Unit
) {
    private var lastBackTime = 0L
    private var isExiting = false

    /**
     * 处理一次根返回事件
     */
    fun handleRootBackPress() {
        // 已经在退出转场中，静默消费后续滑动，防止重复弹窗或多次触发 Task 压栈
        if (isExiting) return

        val now = uptimeProvider()
        if (lastBackTime != 0L && now - lastBackTime < debounceIntervalMs) {
            lastBackTime = 0L
            isExiting = true
            onExecuteExit()
        } else {
            lastBackTime = now
            onShowToast("再滑一次返回桌面")
        }
    }

    /**
     * 在 Activity 切入后台 (onStop) 或从后台唤醒时重置，恢复初始判定状态
     */
    fun reset() {
        lastBackTime = 0L
        isExiting = false
    }
}
