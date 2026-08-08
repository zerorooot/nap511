package github.zerorooot.nap511.bean

data class SettingUiState(
    val uid: String = "0",
    val cookie: String = "cookie",
    val password: String = "",
    val aria2Url: String = "",
    val aria2Token: String = "",
    val autoRotateEnabled: Boolean = false,
    val hideLoadingView: Boolean = false,
    val earlyLoading: Boolean = false,
    val saveRequestCache: Boolean = true,
    val positionAfterAt: Boolean = false,
    val forceLoadCache: Boolean = false,
    val torrentSort: Boolean = false,
    val logEnabled: Boolean = false,
    val currentOfflineTask: String = "",
    val requestLimitCount: String = "200",
    val defaultOfflineCid: String = "",
    val fabPosition: String = "End",
    val moveFailFile: String = "",
    val defaultOfflineTime: String = "5"
)