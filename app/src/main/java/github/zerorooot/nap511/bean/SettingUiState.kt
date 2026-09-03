package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.util.ConfigKeyUtil

data class SettingUiState(
    @SerializedName(ConfigKeyUtil.UID) val uid: String = "0",
    @SerializedName(ConfigKeyUtil.COOKIE) val cookie: String = "cookie",
    @SerializedName(ConfigKeyUtil.PASSWORD) val password: String = "",
    @SerializedName(ConfigKeyUtil.ARIA2_URL) val aria2Url: String = "",
    @SerializedName(ConfigKeyUtil.ARIA2_TOKEN) val aria2Token: String = "",
    @SerializedName(ConfigKeyUtil.AUTO_ROTATE) val autoRotateEnabled: Boolean = false,
    @SerializedName(ConfigKeyUtil.HIDE_LOADING_VIEW) val hideLoadingView: Boolean = false,
    @SerializedName(ConfigKeyUtil.EARLY_LOADING) val earlyLoading: Boolean = false,
    @SerializedName(ConfigKeyUtil.SAVE_REQUEST_CACHE) val saveRequestCache: Boolean = true,
    @SerializedName(ConfigKeyUtil.POSITION_AFTER_AT) val positionAfterAt: Boolean = false,
    @SerializedName(ConfigKeyUtil.FORCE_LOAD_CACHE) val forceLoadCache: Boolean = false,
    @SerializedName(ConfigKeyUtil.VIDEO_LINK_MODE) val videoLinkMode: Boolean = false,
    @SerializedName(ConfigKeyUtil.AUTO_JUMP_RETRY) val autoJumpRetry: Boolean = true,
    @SerializedName(ConfigKeyUtil.DYNAMIC_COLOR) val dynamicColorEnabled: Boolean = true,
    @SerializedName(ConfigKeyUtil.THEME_MODE) val themeMode: String = "跟随系统",
    @SerializedName(ConfigKeyUtil.TORRENT_SORT) val torrentSort: Boolean = false,
    @SerializedName(ConfigKeyUtil.LOG) val logEnabled: Boolean = false,
    @SerializedName(ConfigKeyUtil.CURRENT_OFFLINE_TASK) val currentOfflineTask: String = "",
    @SerializedName(ConfigKeyUtil.REQUEST_LIMIT_COUNT) val requestLimitCount: String = "200",
    @SerializedName(ConfigKeyUtil.DEFAULT_OFFLINE_CID) val defaultOfflineCid: String = "",
    @SerializedName(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION) val fabPosition: String = "End",
    @SerializedName(ConfigKeyUtil.MOVE_FAIL_FILE) val moveFailFile: String = "",
    @SerializedName(ConfigKeyUtil.DEFAULT_OFFLINE_TIME) val defaultOfflineTime: String = "5",
    @SerializedName(ConfigKeyUtil.MAX_TXT_SIZE) val txtSize: String = "200",
    @SerializedName(ConfigKeyUtil.EXPANDED_SCREEN) val expandedScreenEnabled: Boolean = true
)