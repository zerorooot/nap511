package github.zerorooot.nap511.util

object UserSessionManager {
    @Volatile
    var cookie: String = ""
        private set

    @Volatile
    var uid: String = "0"
        private set

    @Volatile
    var requestLimitCount: Int = 200
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

    suspend fun updateRequestLimitCount(count: Int) {
        this.requestLimitCount = count
        DataStoreUtil.putDataSuspend(ConfigKeyUtil.REQUEST_LIMIT_COUNT, count.toString())
    }
}
