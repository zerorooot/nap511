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
import github.zerorooot.nap511.util.network.UserSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val defaultSettingUiState = SettingUiState()

    /**
     * 统一暴露设置状态的 StateFlow (支持预热与状态复用)
     * 【冷启动性能优化】：对 DataStore 磁盘流进行单次映射，
     * 避免了原先 31 个独立 Flow 及多层 combine 产生的协同与微任务调度开销，显著加速冷启动首帧就绪速度。
     */
    val settingUiStateFlow: StateFlow<SettingUiState> = DataStoreUtil.dataStoreFlow
        .map { pref ->
            val default = defaultSettingUiState
            SettingUiState(
                isLoaded = true, // 核心：磁盘 DataStore 产生第一组真实数据后标记为就绪
                uid = pref[stringPreferencesKey(ConfigKeyUtil.UID)] ?: default.uid,
                cookie = pref[stringPreferencesKey(ConfigKeyUtil.COOKIE)] ?: default.cookie,
                password = pref[stringPreferencesKey(ConfigKeyUtil.PASSWORD)] ?: default.password,
                aria2Url = pref[stringPreferencesKey(ConfigKeyUtil.ARIA2_URL)] ?: ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE,
                aria2Token = pref[stringPreferencesKey(ConfigKeyUtil.ARIA2_TOKEN)] ?: default.aria2Token,
                autoRotateEnabled = pref[booleanPreferencesKey(ConfigKeyUtil.AUTO_ROTATE)] ?: default.autoRotateEnabled,
                hideLoadingView = pref[booleanPreferencesKey(ConfigKeyUtil.HIDE_LOADING_VIEW)] ?: default.hideLoadingView,
                earlyLoading = pref[booleanPreferencesKey(ConfigKeyUtil.EARLY_LOADING)] ?: default.earlyLoading,
                saveRequestCache = pref[booleanPreferencesKey(ConfigKeyUtil.SAVE_REQUEST_CACHE)] ?: default.saveRequestCache,
                imageHdPreview = pref[booleanPreferencesKey(ConfigKeyUtil.IMAGE_HD_PREVIEW)] ?: default.imageHdPreview,
                positionAfterAt = pref[booleanPreferencesKey(ConfigKeyUtil.POSITION_AFTER_AT)] ?: default.positionAfterAt,
                forceLoadCache = pref[booleanPreferencesKey(ConfigKeyUtil.FORCE_LOAD_CACHE)] ?: default.forceLoadCache,
                videoLinkMode = pref[booleanPreferencesKey(ConfigKeyUtil.VIDEO_LINK_MODE)] ?: default.videoLinkMode,
                autoJumpRetry = pref[booleanPreferencesKey(ConfigKeyUtil.AUTO_JUMP_RETRY)] ?: default.autoJumpRetry,
                dynamicColorEnabled = pref[booleanPreferencesKey(ConfigKeyUtil.DYNAMIC_COLOR)] ?: default.dynamicColorEnabled,
                themeMode = pref[stringPreferencesKey(ConfigKeyUtil.THEME_MODE)] ?: default.themeMode,
                torrentSort = pref[booleanPreferencesKey(ConfigKeyUtil.TORRENT_SORT)] ?: default.torrentSort,
                logEnabled = pref[booleanPreferencesKey(ConfigKeyUtil.LOG)] ?: default.logEnabled,
                currentOfflineTask = pref[stringPreferencesKey(ConfigKeyUtil.CURRENT_OFFLINE_TASK)] ?: default.currentOfflineTask,
                requestLimitCount = pref[stringPreferencesKey(ConfigKeyUtil.REQUEST_LIMIT_COUNT)] ?: default.requestLimitCount,
                defaultOfflineCid = pref[stringPreferencesKey(ConfigKeyUtil.DEFAULT_OFFLINE_CID)] ?: default.defaultOfflineCid,
                defaultOfflinePath = pref[stringPreferencesKey(ConfigKeyUtil.DEFAULT_OFFLINE_PATH)] ?: default.defaultOfflinePath,
                fabPosition = pref[stringPreferencesKey(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION)] ?: default.fabPosition,
                moveFailFile = pref[stringPreferencesKey(ConfigKeyUtil.MOVE_FAIL_FILE)] ?: default.moveFailFile,
                defaultOfflineTime = pref[stringPreferencesKey(ConfigKeyUtil.DEFAULT_OFFLINE_TIME)] ?: default.defaultOfflineTime,
                txtSize = pref[stringPreferencesKey(ConfigKeyUtil.MAX_TXT_SIZE)] ?: default.txtSize,
                expandedScreenEnabled = pref[booleanPreferencesKey(ConfigKeyUtil.EXPANDED_SCREEN)] ?: default.expandedScreenEnabled,
                gridScreenEnabled = pref[booleanPreferencesKey(ConfigKeyUtil.GRID_SCREEN)] ?: default.gridScreenEnabled,
                gridCellMinSize = pref[stringPreferencesKey(ConfigKeyUtil.GRID_CELL_MIN_SIZE)] ?: default.gridCellMinSize,
                autoImagePreviewCount = pref[stringPreferencesKey(ConfigKeyUtil.AUTO_IMAGE_PREVIEW_COUNT)] ?: default.autoImagePreviewCount,
                hideBatteryBanner = pref[booleanPreferencesKey(ConfigKeyUtil.HIDE_BATTERY_BANNER)] ?: default.hideBatteryBanner
            )
        }.stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingUiState(isLoaded = false)
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

        val dataStoreFlow: Flow<Preferences>
            get() = dataStore.data

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
}
