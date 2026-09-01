package github.zerorooot.nap511.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingViewModel : ViewModel() {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    // 1. 账号与安全分组 Flow
    private val accountFlow = combine(
        DataStoreUtil.getDataFlow(ConfigKeyUtil.UID, "0"),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.COOKIE, ""),
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
        DataStoreUtil.getDataFlow(ConfigKeyUtil.MOVE_FAIL_FILE, ""),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.MAX_TXT_SIZE, "200"),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.THEME_MODE, "跟随系统")
    ) { fabPos, limit, moveFail, txtSize, themeMode ->
        PrefGroup(fabPos, limit, moveFail, txtSize, themeMode)
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
        DataStoreUtil.getDataFlow(ConfigKeyUtil.FORCE_LOAD_CACHE, false),
        DataStoreUtil.getDataFlow(ConfigKeyUtil.VIDEO_LINK_MODE, false),
        combine(
            DataStoreUtil.getDataFlow(ConfigKeyUtil.DYNAMIC_COLOR, true),
            DataStoreUtil.getDataFlow(ConfigKeyUtil.AUTO_JUMP_RETRY, true)
        ) { dynamicColor, autoJumpRetry -> dynamicColor to autoJumpRetry }
    ) { torrentSort, logEnabled, forceCache, videoLinkMode, (dynamicColor, autoJumpRetry) ->
        SwitchGroup2(torrentSort, logEnabled, forceCache, videoLinkMode, dynamicColor, autoJumpRetry)
    }

    // 统一暴露给 UI 的 StateFlow
    val uiState: StateFlow<SettingUiState> = combine(
        accountFlow, aria2Flow, uiPrefFlow, switchFlow1, switchFlow2
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
            fabPosition = uiPref.fabPos,
            requestLimitCount = uiPref.limit,
            moveFailFile = uiPref.moveFail,
            txtSize = uiPref.txtSize,
            themeMode = uiPref.themeMode,
            // 开关
            autoRotateEnabled = s1.autoRotate,
            hideLoadingView = s1.hideLoading,
            earlyLoading = s1.earlyLoading,
            saveRequestCache = s1.saveCache,
            positionAfterAt = s1.positionAfterAt,
            torrentSort = s2.torrentSort,
            logEnabled = s2.logEnabled,
            forceLoadCache = s2.forceCache,
            videoLinkMode = s2.videoLinkMode,
            dynamicColorEnabled = s2.dynamicColor,
            autoJumpRetry = s2.autoJumpRetry
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

    /**
     * 导出配置
     */
    fun exportConfig(
        context: Context,
        uri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = gson.toJson(uiState.value)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "导出失败") }
            }
        }
    }

    /**
     * 导入配置：解析为 JsonObject 后自动遍历类型并存入 DataStore
     */
    fun importConfig(
        context: Context,
        uri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw Exception("文件无法读取")

                // 解析为 JsonObject
                val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

                // 动态遍历并保存到 DataStore
                jsonObject.entrySet().forEach { (key, element) ->
                    if (element.isJsonPrimitive) {
                        val primitive = element.asJsonPrimitive
                        when {
                            primitive.isBoolean -> DataStoreUtil.putDataSuspend(
                                key,
                                primitive.asBoolean
                            )

                            primitive.isString -> DataStoreUtil.putDataSuspend(
                                key,
                                primitive.asString
                            )

                            primitive.isNumber -> DataStoreUtil.putDataSuspend(
                                key,
                                primitive.asNumber
                            )
                        }
                    }
                }

                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "解析配置失败") }
            }
        }
    }

    /**
     * 恢复默认设置（保留登录凭证 UID、Cookie、头像信息）
     */
    fun resetConfig(
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUid = DataStoreUtil.getDataSuspend(ConfigKeyUtil.UID, "0")
                val currentCookie = DataStoreUtil.getDataSuspend(ConfigKeyUtil.COOKIE, "")
                val currentAvatar = DataStoreUtil.getDataSuspend(ConfigKeyUtil.AVATAR_BEAN, "")

                DataStoreUtil.clearData()

                if (currentUid != "0") {
                    DataStoreUtil.putDataSuspend(ConfigKeyUtil.UID, currentUid)
                }
                if (currentCookie.isNotEmpty()) {
                    DataStoreUtil.putDataSuspend(ConfigKeyUtil.COOKIE, currentCookie)
                }
                if (currentAvatar.isNotEmpty()) {
                    DataStoreUtil.putDataSuspend(ConfigKeyUtil.AVATAR_BEAN, currentAvatar)
                }

                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "恢复默认设置失败") }
            }
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

    // 内部聚合数据结构辅助类
    private data class PrefGroup(
        val fabPos: String,
        val limit: String,
        val moveFail: String,
        val txtSize: String,
        val themeMode: String
    )


    private data class SwitchGroup1(
        val autoRotate: Boolean,
        val hideLoading: Boolean,
        val earlyLoading: Boolean,
        val saveCache: Boolean,
        val positionAfterAt: Boolean
    )

    private data class SwitchGroup2(
        val torrentSort: Boolean,
        val logEnabled: Boolean,
        val forceCache: Boolean,
        val videoLinkMode: Boolean,
        val dynamicColor: Boolean,
        val autoJumpRetry: Boolean
    )
}