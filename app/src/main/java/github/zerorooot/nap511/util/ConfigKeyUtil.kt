package github.zerorooot.nap511.util

import github.zerorooot.nap511.R


/**
 * DataStoreUtil中的key
 */
class ConfigKeyUtil {
    companion object {
        /**
         * aria2秘钥
         */
        val ARIA2_TOKEN by lazy { App.instance.getStringRes(R.string.ARIA2_TOKEN) }

        /**
         * aria2 url地址
         */
        val ARIA2_URL by lazy { App.instance.getStringRes(R.string.ARIA2_URL) }

        /**
         * aria2默认地址
         */
        val ARIA2_URL_DEFAULT_VALUE by lazy { App.instance.getStringRes(R.string.ARIA2_URL_DEFAULT_VALUE) }

        /**
         * cookie
         */
        val COOKIE by lazy { App.instance.getStringRes(R.string.COOKIE) }

        /**
         * user id
         */
        val UID by lazy { App.instance.getStringRes(R.string.UID) }

        /**
         * 回收站密码
         */
        val PASSWORD by lazy { App.instance.getStringRes(R.string.PASSWORD) }

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
        val AUTO_ROTATE by lazy { App.instance.getStringRes(R.string.AUTO_ROTATE) }


        /**
         * 头像信息bean的json信息,包含头像url、过期时间、用户名、过期时间等
         * @see github.zerorooot.nap511.bean.AvatarBean
         */
        const val AVATAR_BEAN = "AVATAR_BEAN"

        /**
         * 默认离线位置
         */
        val DEFAULT_OFFLINE_CID by lazy { App.instance.getStringRes(R.string.DEFAULT_OFFLINE_CID) }

        /**
         * 默认请求个数，默认为100，具体在设置中设置
         */
        val REQUEST_LIMIT_COUNT by lazy { App.instance.getStringRes(R.string.REQUEST_LIMIT_COUNT) }

        /**
         * 默认离线个数，默认为5，具体在设置中设置
         */
        val DEFAULT_OFFLINE_COUNT by lazy { App.instance.getStringRes(R.string.DEFAULT_OFFLINE_COUNT) }


        /**
         * 当前缓存的离线任务
         */
        val CURRENT_OFFLINE_TASK by lazy { App.instance.getStringRes(R.string.CURRENT_OFFLINE_TASK) }


        /**
         * 离线任务缓存方式,true为x分钟后统一下载，false为集满后统一下载，具体在SettingActivity中设置
         */
        val OFFLINE_METHOD by lazy { App.instance.getStringRes(R.string.OFFLINE_METHOD) }


        /**
         * 默认离线时间，默认为5分钟，具体在设置中设置
         */
        val DEFAULT_OFFLINE_TIME by lazy { App.instance.getStringRes(R.string.DEFAULT_OFFLINE_TIME) }


        /**
         * 当视频正在加载时，隐藏loadingView
         */
        val HIDE_LOADING_VIEW by lazy { App.instance.getStringRes(R.string.HIDE_LOADING_VIEW) }


        /**
         * 提前加载上下两个文件夹，具体在设置中设置
         */
        val EARLY_LOADING by lazy { App.instance.getStringRes(R.string.EARLY_LOADING) }

        /**
         *重命名时，光标定位在@后
         */
        val POSITION_AFTER_AT by lazy { App.instance.getStringRes(R.string.POSITION_AFTER_AT) }

        /**
         * 保存请求缓存
         */
        val SAVE_REQUEST_CACHE by lazy { App.instance.getStringRes(R.string.SAVE_REQUEST_CACHE) }


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
        val LOG_SCREEN by lazy { App.instance.getStringRes(R.string.LOG_SCREEN) }


        /**
         * 离线task的标签
         */
        const val OFFLINE_TASK_WORKER = "OfflineTaskWorker"
    }
}