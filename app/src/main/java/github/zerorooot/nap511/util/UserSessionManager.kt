package github.zerorooot.nap511.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object UserSessionManager {
    var cookie: String by mutableStateOf("")
        private set

    var uid: String by mutableStateOf("0")
        private set

    var requestLimitCount: Int by mutableIntStateOf(200)
        private set

    fun init(cookie: String, uid: String, requestLimitCount: Int) {
        this.cookie = cookie
        this.uid = uid
        this.requestLimitCount = requestLimitCount
    }

    suspend fun updateSession(newCookie: String, newUid: String) {
        this.cookie = newCookie
        this.uid = newUid
        DataStoreUtil.putDataSuspend(ConfigKeyUtil.COOKIE, newCookie)
        DataStoreUtil.putDataSuspend(ConfigKeyUtil.UID, newUid)
    }

    fun updateCookie(newCookie: String) {
        this.cookie = newCookie
    }

    fun updateRequestLimitCount(count: String) {
        this.requestLimitCount = count.toInt()
    }
}
