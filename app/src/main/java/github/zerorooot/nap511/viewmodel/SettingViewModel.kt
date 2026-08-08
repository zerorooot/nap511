package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingViewModel : ViewModel() {

    // 1. 账号与安全分组 Flow
    private val accountFlow = combine(
        DataStoreUtil.getDataFlow(ConfigKeyUtil.UID, "0"),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.COOKIE, "cookie"),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.PASSWORD, "")
    ) { uid, cookie, password ->
        Triple(uid, cookie, password)
    }

    // 2. Aria2 与下载分组 Flow
    private val aria2Flow = combine(
        DataStoreUtil.getDataFlow(ConfigKeyUtil.ARIA2_URL, ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.ARIA2_TOKEN, ""),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.DEFAULT_OFFLINE_CID, ""),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5"),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
    ) { url, token, cid, time, task ->
        Aria2Group(url, token, cid, time, task)
    }

    // 3. 界面偏好分组 Flow
    private val uiPrefFlow = combine(
        DataStoreUtil.getDataFlow(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION, "End"),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.REQUEST_LIMIT_COUNT, "200"),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.MOVE_FAIL_FILE, "")
    ) { fabPos, limit, moveFail ->
        Triple(fabPos, limit, moveFail)
    }

    // 4. 开关配置分组 Flow (Part 1)
    private val switchFlow1 = combine(
        DataStoreUtil.getDataFlow(ConfigKeyUtil.AUTO_ROTATE, false),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.HIDE_LOADING_VIEW, false),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.EARLY_LOADING, false),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.SAVE_REQUEST_CACHE, true),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.POSITION_AFTER_AT, false)
    ) { autoRotate, hideLoading, earlyLoading, saveCache, positionAfterAt ->
        SwitchGroup1(autoRotate, hideLoading, earlyLoading, saveCache, positionAfterAt)
    }

    // 5. 开关配置分组 Flow (Part 2)
    private val switchFlow2 = combine(
        DataStoreUtil.getDataFlow(ConfigKeyUtil.TORRENT_SORT, false),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.LOG, false),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.FORCE_LOAD_CACHE, false)
    ) { torrentSort, logEnabled, forceCache ->
        Triple(torrentSort, logEnabled, forceCache)
    }

    // 统一暴露给 UI 的 StateFlow
    val uiState: StateFlow<SettingUiState> = combine(
        accountFlow,
        aria2Flow,
        uiPrefFlow,
        switchFlow1,
        switchFlow2
    ) { account, aria2, uiPref, s1, s2 ->
        SettingUiState(
            // 账号
            uid = account.first,
            cookie = account.second,
            password = account.third,
            // Aria2
            aria2Url = aria2.url,
            aria2Token = aria2.token,
            defaultOfflineCid = aria2.cid,
            defaultOfflineTime = aria2.time,
            currentOfflineTask = aria2.task,
            // 界面
            fabPosition = uiPref.first,
            requestLimitCount = uiPref.second,
            moveFailFile = uiPref.third,
            // 开关
            autoRotateEnabled = s1.autoRotate,
            hideLoadingView = s1.hideLoading,
            earlyLoading = s1.earlyLoading,
            saveRequestCache = s1.saveCache,
            positionAfterAt = s1.positionAfterAt,
            torrentSort = s2.first,
            logEnabled = s2.second,
            forceLoadCache = s2.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000), // 当无界面订阅 5 秒后自动停止收集，节省资源
        initialValue = SettingUiState()
    )

    /**
     * 通用配置保存函数
     */
    fun <T : Any> saveData(key: String, newValue: T) {
        viewModelScope.launch {
            DataStoreUtil.putDataSuspend(key, newValue)
        }
    }

    // 内部聚合数据结构辅助类
    private data class Aria2Group(
        val url: String,
        val token: String,
        val cid: String,
        val time: String,
        val task: String
    )

    private data class SwitchGroup1(
        val autoRotate: Boolean,
        val hideLoading: Boolean,
        val earlyLoading: Boolean,
        val saveCache: Boolean,
        val positionAfterAt: Boolean
    )
}