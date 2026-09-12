package github.zerorooot.nap511.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.UserSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsRepository {

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository().also { INSTANCE = it }
            }
        }

        /**
         * 静态快捷调用的单项 Flow 获取
         */
        fun <T : Any> getDataFlow(key: String, defaultValue: T): Flow<T> =
            getInstance().getDataFlow(key, defaultValue)

        /**
         * 静态快捷调用的单项挂起读取
         */
        suspend fun <T : Any> getDataSuspend(key: String, defaultValue: T): T =
            getInstance().getDataSuspend(key, defaultValue)

        /**
         * 静态快捷调用的单项保存
         */
        suspend fun <T : Any> saveData(key: String, newValue: T) =
            getInstance().saveData(key, newValue)
    }

    // 独立的作用域，用于后台预热 DataStore
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 获取指定 Key 的 Flow 数据流（轻量读取单个 Key）
     */
    fun <T : Any> getDataFlow(key: String, defaultValue: T): Flow<T> {
        return DataStoreUtil.getDataFlow(key, defaultValue)
    }

    /**
     * 挂起函数获取指定 Key 的当前值（轻量读取单个 Key）
     */
    suspend fun <T : Any> getDataSuspend(key: String, defaultValue: T): T {
        return DataStoreUtil.getDataSuspend(key, defaultValue)
    }

    // 1. 账号与安全分组 Flow
    private val accountFlow = combine(
        getDataFlow(ConfigKeyUtil.UID, ""),
        getDataFlow(ConfigKeyUtil.COOKIE, ""),
        getDataFlow(ConfigKeyUtil.PASSWORD, "")
    ) { uid, cookie, password ->
        Triple(uid, cookie, password)
    }

    // 2. Aria2 与下载分组 Flow
    private val aria2Flow: Flow<Aria2Group> = combine(
        getDataFlow(ConfigKeyUtil.ARIA2_URL, ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE),
        getDataFlow(ConfigKeyUtil.ARIA2_TOKEN, ""),
        getDataFlow(ConfigKeyUtil.DEFAULT_OFFLINE_CID, ""),
        getDataFlow(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5"),
        getDataFlow(ConfigKeyUtil.CURRENT_OFFLINE_TASK, ""),
        getDataFlow(ConfigKeyUtil.DEFAULT_OFFLINE_PATH, "")
    ) { values: Array<String> ->
        Aria2Group(
            url = values[0],
            token = values[1],
            cid = values[2],
            time = values[3],
            task = values[4],
            cidPath = values[5]
        )
    }

    // 3. 界面偏好分组 Flow
    private val uiPrefFlow: Flow<PrefGroup> = combine(
        getDataFlow(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION, "End"),
        getDataFlow(ConfigKeyUtil.REQUEST_LIMIT_COUNT, "200"),
        getDataFlow(ConfigKeyUtil.MOVE_FAIL_FILE, ""),
        getDataFlow(ConfigKeyUtil.MAX_TXT_SIZE, "200"),
        getDataFlow(ConfigKeyUtil.THEME_MODE, "跟随系统"),
        getDataFlow(ConfigKeyUtil.EXPANDED_SCREEN_THRESHOLD, "600"),
        getDataFlow(ConfigKeyUtil.GRID_CELL_MIN_SIZE, "340")
    ) { values: Array<String> ->
        PrefGroup(
            fabPos = values[0],
            limit = values[1],
            moveFail = values[2],
            txtSize = values[3],
            themeMode = values[4],
            expandedThreshold = values[5],
            gridCellMinSize = values[6]
        )
    }

    // 4. 开关配置分组 Flow
    private val switchFlow: Flow<SwitchGroup> = combine(
        getDataFlow(ConfigKeyUtil.TORRENT_SORT, false),
        getDataFlow(ConfigKeyUtil.LOG, false),
        getDataFlow(ConfigKeyUtil.FORCE_LOAD_CACHE, false),
        getDataFlow(ConfigKeyUtil.VIDEO_LINK_MODE, false),
        getDataFlow(ConfigKeyUtil.DYNAMIC_COLOR, true),
        getDataFlow(ConfigKeyUtil.AUTO_JUMP_RETRY, true),
        getDataFlow(ConfigKeyUtil.EXPANDED_SCREEN, true),

        getDataFlow(ConfigKeyUtil.AUTO_ROTATE, false),
        getDataFlow(ConfigKeyUtil.HIDE_LOADING_VIEW, false),
        getDataFlow(ConfigKeyUtil.EARLY_LOADING, false),
        getDataFlow(ConfigKeyUtil.SAVE_REQUEST_CACHE, true),
        getDataFlow(ConfigKeyUtil.POSITION_AFTER_AT, false),
        getDataFlow(ConfigKeyUtil.IMAGE_HD_PREVIEW, false)
    ) { values: Array<Boolean> ->
        SwitchGroup(
            torrentSort = values[0],
            logEnabled = values[1],
            forceCache = values[2],
            videoLinkMode = values[3],
            dynamicColor = values[4],
            autoJumpRetry = values[5],
            expandedScreen = values[6],
            autoRotate = values[7],
            hideLoading = values[8],
            earlyLoading = values[9],
            saveCache = values[10],
            positionAfterAt = values[11],
            imageHdPreview = values[12]
        )
    }

    /**
     * 统一暴露设置状态的 StateFlow (支持预热与状态复用)
     * 将 Flow 提升为预热的 StateFlow（Eagerly 立即启动）
     */
    val settingUiStateFlow: StateFlow<SettingUiState> = combine(
        accountFlow, aria2Flow, uiPrefFlow, switchFlow
    ) { account, aria2, uiPref, s2 ->
        SettingUiState(
            // 账号
            uid = account.first,
            cookie = account.second,
            password = account.third,
            // Aria2
            aria2Url = aria2.url,
            aria2Token = aria2.token,
            defaultOfflineCid = aria2.cid,
            defaultOfflinePath = aria2.cidPath,
            defaultOfflineTime = aria2.time,
            currentOfflineTask = aria2.task,
            // 界面
            fabPosition = uiPref.fabPos,
            requestLimitCount = uiPref.limit,
            moveFailFile = uiPref.moveFail,
            txtSize = uiPref.txtSize,
            themeMode = uiPref.themeMode,
            expandedScreenThreshold = uiPref.expandedThreshold,
            gridCellMinSize = uiPref.gridCellMinSize,
            // 开关
            autoRotateEnabled = s2.autoRotate,
            hideLoadingView = s2.hideLoading,
            earlyLoading = s2.earlyLoading,
            saveRequestCache = s2.saveCache,
            positionAfterAt = s2.positionAfterAt,
            torrentSort = s2.torrentSort,
            logEnabled = s2.logEnabled,
            forceLoadCache = s2.forceCache,
            videoLinkMode = s2.videoLinkMode,
            dynamicColorEnabled = s2.dynamicColor,
            autoJumpRetry = s2.autoJumpRetry,
            expandedScreenEnabled = s2.expandedScreen,
            imageHdPreview = s2.imageHdPreview
        )
    }.stateIn(
        scope = repositoryScope,
        // 只要单例创建，立即开始在后台读取加载
        started = SharingStarted.Eagerly,
        initialValue = SettingUiState()
    )

    /**
     * 保存单个配置项
     */
    suspend fun <T : Any> saveData(key: String, newValue: T) {
        DataStoreUtil.putDataSuspend(key, newValue)
    }

    /**
     * 导入并解析 JsonObject 配置信息，存储至 DataStore
     */
    suspend fun importConfig(jsonObject: JsonObject) {
        jsonObject.entrySet().forEach { (key, element) ->
            if (element.isJsonPrimitive) {
                val primitive = element.asJsonPrimitive
                when {
                    primitive.isBoolean -> DataStoreUtil.putDataSuspend(key, primitive.asBoolean)
                    primitive.isString -> DataStoreUtil.putDataSuspend(key, primitive.asString)
                    primitive.isNumber -> DataStoreUtil.putDataSuspend(key, primitive.asNumber)
                }
            }
        }

        // 如果导入配置包含 Cookie / UID，同步更新 UserSessionManager
        val newCookie =
            if (jsonObject.has(ConfigKeyUtil.COOKIE)) jsonObject.get(ConfigKeyUtil.COOKIE).asString else null
        val newUid =
            if (jsonObject.has(ConfigKeyUtil.UID)) jsonObject.get(ConfigKeyUtil.UID).asString else null
        if (!newCookie.isNullOrEmpty()) {
            UserSessionManager.updateSession(newCookie, newUid ?: UserSessionManager.uid)
        }
    }

    /**
     * 重置除登录凭证外的所有配置
     */
    suspend fun resetConfig() {
        val currentUid = getDataSuspend(ConfigKeyUtil.UID, "")
        val currentCookie = getDataSuspend(ConfigKeyUtil.COOKIE, "")
        val currentAvatar = getDataSuspend(ConfigKeyUtil.AVATAR_BEAN, "")
        val password = getDataSuspend(ConfigKeyUtil.PASSWORD, "")

        DataStoreUtil.clearData()

        if (currentUid.isNotEmpty()) {
            DataStoreUtil.putDataSuspend(ConfigKeyUtil.UID, currentUid)
        }
        if (currentCookie.isNotEmpty()) {
            DataStoreUtil.putDataSuspend(ConfigKeyUtil.COOKIE, currentCookie)
        }
        if (currentAvatar.isNotEmpty()) {
            DataStoreUtil.putDataSuspend(ConfigKeyUtil.AVATAR_BEAN, currentAvatar)
        }
        if (password.isNotEmpty()) {
            DataStoreUtil.putDataSuspend(ConfigKeyUtil.PASSWORD, password)
        }
    }

    // =========================================================================
    //  私有 DataStore 存取工具：仅限 SettingsRepository 内部访问
    // =========================================================================
    private object DataStoreUtil {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Setting")

        private val dataStore: DataStore<Preferences>
            get() = App.instance.applicationContext.dataStore

        @Suppress("UNCHECKED_CAST")
        private fun <T> getValueKey(key: String, defaultValue: T): Preferences.Key<T> {
            return when (defaultValue) {
                is Int -> intPreferencesKey(key)
                is Long -> longPreferencesKey(key)
                is String -> stringPreferencesKey(key)
                is Boolean -> booleanPreferencesKey(key)
                is Float -> floatPreferencesKey(key)
                is Double -> doublePreferencesKey(key)
                is Set<*> -> stringSetPreferencesKey(key)
                else -> throw IllegalArgumentException("Unsupported DataStore type for key: $key")
            } as Preferences.Key<T>
        }

        fun <T : Any> getDataFlow(key: String, defaultValue: T): Flow<T> {
            val prefKey = getValueKey(key, defaultValue)
            return dataStore.data.map { preferences ->
                preferences[prefKey] ?: defaultValue
            }
        }

        suspend fun <T : Any> getDataSuspend(key: String, defaultValue: T): T {
            return getDataFlow(key, defaultValue).first()
        }

        suspend fun <T : Any> putDataSuspend(key: String, value: T) {
            val prefKey = getValueKey(key, value)
            dataStore.edit { preferences ->
                preferences[prefKey] = value
            }
        }

        suspend fun clearData() {
            dataStore.edit { it.clear() }
        }
    }

    // 内部数据传输模型
    private data class Aria2Group(
        val url: String,
        val token: String,
        val cid: String,
        val cidPath: String,
        val time: String,
        val task: String
    )

    private data class PrefGroup(
        val fabPos: String,
        val limit: String,
        val moveFail: String,
        val txtSize: String,
        val themeMode: String,
        val expandedThreshold: String,
        val gridCellMinSize: String
    )

    private data class SwitchGroup(
        val torrentSort: Boolean,
        val logEnabled: Boolean,
        val forceCache: Boolean,
        val videoLinkMode: Boolean,
        val dynamicColor: Boolean,
        val autoJumpRetry: Boolean,
        val expandedScreen: Boolean,
        val autoRotate: Boolean,
        val hideLoading: Boolean,
        val earlyLoading: Boolean,
        val saveCache: Boolean,
        val positionAfterAt: Boolean,
        val imageHdPreview: Boolean
    )
}
