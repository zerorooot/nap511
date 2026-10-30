package github.zerorooot.nap511.viewmodel.module

import android.content.Context
import android.content.Intent
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.copy
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.viewmodel.platform.BackgroundScheduler
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外部集成领域模块
 * 负责 DeepLink Intent 协议分发、WorkManager 后台离线任务调度、
 * 系统电池优化检测以及 115 剩余存储空间查询。
 */
class FileIntegrationModule(
    private val stateHolder: FileUiStateHolder,
    private val navigator: FileNavigator,
    private val fileRepository: FileRepository,
    private val backgroundScheduler: BackgroundScheduler
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
     * 统一处理传入的 Deep Link Intent
     */
    suspend fun handleDeepLink(intent: Intent?, context: Context) {
        val uri = intent?.data ?: return

        // 解析 Scheme 和 Host: nap511://detail/check?param=3213
        if (uri.scheme == "nap511" && uri.host == "detail") {
            val command = uri.lastPathSegment // "check" 或 "copy"
            val param = uri.getQueryParameter("param") ?: ""
            XLog.i("FileIntegrationModule handleDeepLink $uri")
            when (command) {
                "addTask" -> {
                    XLog.d(param)
//                    DataStoreUtil.putData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, param)
//                    handleOfflineTask(true)
                }
                //  adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/check?param=3213" github.zerorooot.nap511
                "check" -> {
                    _navigationEvent.emit(NavEvent.NavigateToScreen(Route.VerifyMagnetLinkAccount))
                }
                // adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/jump?param=0" github.zerorooot.nap511
                "jump" -> {
                    FileCacheManager.remove(stateHolder.currentContentState.currentCid)
                    navigator.getFiles(param)
                }

                // adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/copy?param=copy_test" github.zerorooot.nap511
                "copy" -> {
                    param.copy(context)
                    XLog.d("handleIntent copy $param")
                    App.instance.toast("复制磁力链接成功!")
                }

                "unzipError" -> {
                    param.copy(context)
                    XLog.d("handleIntent unzipError $intent $param")
                    App.instance.toast("解压失败信息已复制到剪切板!")
                }
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
        }.onFailureToastAndLog()
    }

    /**
     * 处理离线下载任务
     */
    suspend fun handleOfflineTask(currentOfflineTask: String, forceRecreate: Boolean = false) {
        backgroundScheduler.handleOfflineTask(currentOfflineTask, forceRecreate)
    }
}
