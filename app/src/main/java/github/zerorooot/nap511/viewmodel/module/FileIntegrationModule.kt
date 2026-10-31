package github.zerorooot.nap511.viewmodel.module

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.viewmodel.platform.BackgroundScheduler
import github.zerorooot.nap511.viewmodel.platform.DeepLinkAction
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import github.zerorooot.nap511.viewmodel.platform.ToastProvider
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外部集成领域模块
 * 负责 DeepLink 业务动作分发、WorkManager 后台离线任务调度、
 * 系统电池优化检测以及 115 剩余存储空间查询。
 * 纯 JVM 领域模块，零 android.* 依赖。
 */
class FileIntegrationModule(
    private val stateHolder: FileUiStateHolder,
    private val navigator: FileNavigator,
    private val fileRepository: FileRepository,
    private val backgroundScheduler: BackgroundScheduler,
    private val onCopyToClipboard: (String) -> Unit,
    private val toastProvider: ToastProvider
) {

    private val _navigationEvent = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<NavEvent> = _navigationEvent.asSharedFlow()

    private val _remainingSpace = MutableStateFlow(RemainingSpaceBean())
    val remainingSpace: StateFlow<RemainingSpaceBean> = _remainingSpace.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> =
        _isIgnoringBatteryOptimizations.asStateFlow()

    init {
        // 仅在进程启动/ViewModel 初始化时执行唯一一次检测
        refreshBatteryOptimizations()
    }

    fun refreshBatteryOptimizations() {
        _isIgnoringBatteryOptimizations.value = backgroundScheduler.isIgnoringBatteryOptimizations()
    }

    /**
     * 统一处理 DeepLink 业务动作（纯 JVM，零 android.* 依赖）
     */
    suspend fun handleAction(action: DeepLinkAction) {
        XLog.i("FileIntegrationModule handleAction $action")
        when (action) {
            //  adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/check?param=3213" github.zerorooot.nap511
            is DeepLinkAction.CheckMagnet -> {
                _navigationEvent.emit(NavEvent.NavigateToScreen(Route.VerifyMagnetLinkAccount))
            }
            // adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/jump?param=0" github.zerorooot.nap511
            is DeepLinkAction.JumpDirectory -> {
                FileCacheManager.remove(stateHolder.currentContentState.currentCid)
                navigator.navigateTo(action.targetCid)
            }
            // adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/copy?param=copy_test" github.zerorooot.nap511
            is DeepLinkAction.CopyText -> {
                onCopyToClipboard(action.content)
                XLog.d("FileIntegrationModule copy ${action.content}")
                toastProvider.showToast(action.toastTip)
            }
        }
    }

    /**
     * 获取剩余空间
     */
    suspend fun getRemainingSpace() {
        runCatching {
            val gson = fileRepository.remainingSpace()
            if (gson.get("state").asBoolean) {
                val spaceInfoJson = gson.getAsJsonObject("data").get("space_info")
                _remainingSpace.value =
                    Gson().fromJson(spaceInfoJson, RemainingSpaceBean::class.java)
            }
        }.onFailureToastAndLog(toastProvider)
    }

    /**
     * 处理离线下载任务
     */
    suspend fun handleOfflineTask(currentOfflineTask: String, forceRecreate: Boolean = false) {
        backgroundScheduler.handleOfflineTask(currentOfflineTask, forceRecreate)
    }
}
