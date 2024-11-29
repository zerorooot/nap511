package github.zerorooot.nap511.util

class ConfigUtil {
    companion object {
        //aria2秘钥
        const val aria2Token = "aria2Token"
        const val aria2Url = "aria2Url"
        const val aria2UrldefValue = "http://127.0.0.1:6800/jsonrpc"
        const val cookie = "cookie"
        const val uid = "uid"
        const val password = "password"
        const val command = "command"
        const val sentToAria2 = "sentToAria2"
        const val autoRotate = "autoRotate"

        //默认离线位置
        const val defaultOfflineCid = "defaultOfflineCid"
        const val requestLimitCount = "requestLimitCount"
        const val defaultOfflineCount = "defaultOfflineCount"
        const val currentOfflineTask = ""
        const val offlineMethod = "offlineMethod"
        const val defaultOfflineTime = "defaultOfflineTime"
        const val errorDownloadCid = "errorDownloadCid"

        //提前加载上下两个文件夹
        const val earlyLoading = "EarlyLoading"

        const val VERIFY_MAGNET_LINK_ACCOUNT = "磁力链接验证"
        const val VERIFY_VIDEO_ACCOUNT = "视频播放验证"
        const val LOGIN = "登录"
        const val MY_FILE = "我的文件"
        const val OFFLINE_DOWNLOAD = "离线下载"
        const val OFFLINE_LIST = "离线列表"
        const val WEB = "网页版"
        const val RECYCLE_BIN="回收站"
        const val ADVANCED_SETTINGS = "高级设置"
        const val EXIT_APPLICATION = "退出应用"
        const val PHOTO = "照片模式"

    }
}