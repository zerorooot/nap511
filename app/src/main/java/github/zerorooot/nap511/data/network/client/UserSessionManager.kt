package github.zerorooot.nap511.data.network.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import github.zerorooot.nap511.ui.model.SettingUiState

/**
 * 全局用户会话内存状态管理器 (UserSessionManager)
 *
 * 遵循职责单一与纯内存状态原则：
 * 仅负责应用运行期间会话凭证（Cookie、UID、请求阈值等）的快速存取与状态观察。
 * 磁盘持久化完全解耦，由上层领域仓储（如 SettingsRepository / AuthRepository）统一负责。
 */
object UserSessionManager {
    var cookie: String by mutableStateOf("")
        private set

    var uid: String by mutableStateOf("")
        private set

    var requestLimitCount: Int by mutableIntStateOf(SettingUiState().requestLimitCount.toInt())
        private set

    fun init(cookie: String, uid: String, requestLimitCount: Int) {
        this.cookie = cookie
        this.uid = uid
        this.requestLimitCount = requestLimitCount
    }

    fun updateSession(newCookie: String, newUid: String) {
        this.cookie = newCookie
        this.uid = newUid
    }

    fun clearSession() {
        updateSession("", "")
    }

    fun updateCookie(newCookie: String) {
        this.cookie = newCookie
    }

    fun updateRequestLimitCount(count: String) {
        this.requestLimitCount = count.toInt()
    }
}

