package github.zerorooot.nap511.util

/**
 * DataStoreUtil中的key
 */
class ConfigKeyUtil {
    companion object {
        /**
         * aria2秘钥
         */
        const val ARIA2_TOKEN = "aria2Token"

        /**
         * aria2 url地址
         */
        const val ARIA2_URL = "aria2Url"

        /**
         * aria2默认地址
         */
        const val ARIA2_URL_DEFAULT_VALUE = "http://127.0.0.1:6800/jsonrpc"

        /**
         * cookie
         */
        const val COOKIE = "cookie"

        /**
         * user id
         */
        const val UID = "uid"

        /**
         * 回收站密码
         */
        const val PASSWORD = "password"

        /**
         * sha1 service中使用，原本有发送到aria2和获取文件sha1两种，但现sha1废了，仅有发送到aria2
         */
        const val COMMAND = "command"

        /**
         * 发送到aria2
         */
        const val SENT_TO_ARIA2 = "sentToAria2"

        /**
         * 在设置中，视频是否自动旋转
         */
        const val AUTO_ROTATE = "autoRotate"

        /**
         * 头像信息bean的json信息,包含头像url、过期时间、用户名、过期时间等
         * @see github.zerorooot.nap511.bean.AvatarBean
         */
        const val AVATAR_BEAN = "AVATAR_BEAN"
        /**
         * 默认离线位置
         */
        const val DEFAULT_OFFLINE_CID = "defaultOfflineCid"

        /**
         * 默认请求个数，默认为100，具体在设置中设置
         */
        const val REQUEST_LIMIT_COUNT = "requestLimitCount"

        /**
         * 默认离线个数，默认为5，具体在设置中设置
         */
        const val DEFAULT_OFFLINE_COUNT = "defaultOfflineCount"

        /**
         * 当前缓存的离线任务
         */
        const val CURRENT_OFFLINE_TASK = "currentOfflineTask"

        /**
         * 离线任务缓存方式,true为x分钟后统一下载，false为集满后统一下载，具体在SettingActivity中设置
         */
        const val OFFLINE_METHOD = "offlineMethod"

        /**
         * 默认离线时间，默认为5分钟，具体在设置中设置
         */
        const val DEFAULT_OFFLINE_TIME = "defaultOfflineTime"

        /**
         * 当视频正在加载时，隐藏loadingView
         */
        const val HIDE_LOADING_VIEW="hideLoadingView"

        /**
         * 提前加载上下两个文件夹，具体在设置中设置
         */
        const val EARLY_LOADING = "EarlyLoading"

        /**
         * 磁力链接验证
         */
        const val VERIFY_MAGNET_LINK_ACCOUNT = "磁力链接验证"

        /**
         * 视频播放验证
         */
        const val VERIFY_VIDEO_ACCOUNT = "视频播放验证"

        /**
         * 登录
         */
        const val LOGIN = "登录"

        /**
         * 我的文件
         */
        const val MY_FILE = "我的文件"

        /**
         * 离线下载
         */
        const val OFFLINE_DOWNLOAD = "离线下载"

        /**
         * 离线列表
         */
        const val OFFLINE_LIST = "离线列表"

        /**
         * 网页版
         */
        const val WEB = "网页版"

        /**
         * 回收站
         */
        const val RECYCLE_BIN = "回收站"

        /**
         * 高级设置
         */
        const val ADVANCED_SETTINGS = "高级设置"

        /**
         * 退出应用
         */
        const val EXIT_APPLICATION = "退出应用"

        /**
         * 照片模式
         */
        const val PHOTO = "照片模式"

        /**
         * 日志页面
         */
        const val LOG_SCREEN="日志页面"

        /**
         * 离线task的标签
         */
        const val OFFLINE_TASK_WORKER = "OfflineTaskWorker"
    }
}