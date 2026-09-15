package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.util.ConfigKeyUtil

/**
 * 设置界面的 UI 状态数据类
 */
data class SettingUiState(
    /** 用户 ID */
    @SerializedName(ConfigKeyUtil.UID) val uid: String = "",

    /** 账号 Cookie（登录凭证） */
    @SerializedName(ConfigKeyUtil.COOKIE) val cookie: String = "cookie",

    /** 安全操作密钥（清空回收站时输入的数字密码） */
    @SerializedName(ConfigKeyUtil.PASSWORD) val password: String = "",

    /** Aria2 RPC 地址 */
    @SerializedName(ConfigKeyUtil.ARIA2_URL) val aria2Url: String = "",

    /** Aria2 授权密钥 */
    @SerializedName(ConfigKeyUtil.ARIA2_TOKEN) val aria2Token: String = "",

    /** 是否开启屏幕自动旋转（根据视频画面的宽高比自动切换横竖屏） */
    @SerializedName(ConfigKeyUtil.AUTO_ROTATE) val autoRotateEnabled: Boolean = false,

    /** 是否隐藏加载提示（视频缓冲加载时隐藏居中的加载动画） */
    @SerializedName(ConfigKeyUtil.HIDE_LOADING_VIEW) val hideLoadingView: Boolean = false,

    /** 是否开启前后文件夹预加载（进入子目录时，自动预加载前后相邻文件夹的文件数据） */
    @SerializedName(ConfigKeyUtil.EARLY_LOADING) val earlyLoading: Boolean = false,

    /** 是否开启请求磁盘缓存（将请求的文件列表缓存至本地存储，提升再次加载速度） */
    @SerializedName(ConfigKeyUtil.SAVE_REQUEST_CACHE) val saveRequestCache: Boolean = true,

    /** 是否开启高清瀑布视图（开启后，瀑布流视图下将自动请求高清原图） */
    @SerializedName(ConfigKeyUtil.IMAGE_HD_PREVIEW) val imageHdPreview: Boolean = false,

    /** 是否开启改名光标定位（重命名文件时，输入光标自动定位至 '@' 或 '空格' 字符后） */
    @SerializedName(ConfigKeyUtil.POSITION_AFTER_AT) val positionAfterAt: Boolean = false,

    /** 是否开启下拉刷新缓存清空（下拉刷新时，强制清除当前目录下所有已缓存的文件数据） */
    @SerializedName(ConfigKeyUtil.FORCE_LOAD_CACHE) val forceLoadCache: Boolean = false,

    /** 视频解析模式（开启：通过 API 获取播放链接；关闭：直接请求视频链接） */
    @SerializedName(ConfigKeyUtil.VIDEO_LINK_MODE) val videoLinkMode: Boolean = false,

    /** 是否开启自动跳转重试（未开启“视频解析模式”时生效。若播放提示“视频地址错误”，自动重新解析并获取正确链接） */
    @SerializedName(ConfigKeyUtil.AUTO_JUMP_RETRY) val autoJumpRetry: Boolean = true,

    /** 是否启用应用动态配色（根据系统壁纸自动衍生应用配色，仅支持 Android 12+） */
    @SerializedName(ConfigKeyUtil.DYNAMIC_COLOR) val dynamicColorEnabled: Boolean = true,

    /** 主题颜色模式（如跟随系统、浅色模式、深色模式） */
    @SerializedName(ConfigKeyUtil.THEME_MODE) val themeMode: String = "跟随系统",

    /** 是否开启种子文件大小排序（解析种子文件列表时按文件体积从大到小排列） */
    @SerializedName(ConfigKeyUtil.TORRENT_SORT) val torrentSort: Boolean = false,

    /** 是否启用调试日志（记录并输出应用核心运行日志，以便排查异常） */
    @SerializedName(ConfigKeyUtil.LOG) val logEnabled: Boolean = false,

    /** 暂存未下的离线任务链接 */
    @SerializedName(ConfigKeyUtil.CURRENT_OFFLINE_TASK) val currentOfflineTask: String = "",

    /** 单次请求加载的文件数量 */
    @SerializedName(ConfigKeyUtil.REQUEST_LIMIT_COUNT) val requestLimitCount: String = "200",

    /** 默认离线保存目录的文件夹 CID */
    @SerializedName(ConfigKeyUtil.DEFAULT_OFFLINE_CID) val defaultOfflineCid: String = "",

    /** 默认离线保存目录路径 */
    @SerializedName(ConfigKeyUtil.DEFAULT_OFFLINE_PATH) val defaultOfflinePath: String = "",

    /** 悬浮按钮位置 */
    @SerializedName(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION) val fabPosition: String = "End",

    /** 解压失败转移目录名称（解压失败的压缩包将移至：解压目录\指定名称） */
    @SerializedName(ConfigKeyUtil.MOVE_FAIL_FILE) val moveFailFile: String = "",

    /** 离线任务延迟时间（单位：分钟） */
    @SerializedName(ConfigKeyUtil.DEFAULT_OFFLINE_TIME) val defaultOfflineTime: String = "5",

    /** 文本预览最大限制（单位：KB） */
    @SerializedName(ConfigKeyUtil.MAX_TXT_SIZE) val txtSize: String = "200",

    /** 是否启用大屏扩展模式（在平板或大屏设备上启用大屏展开布局） */
    @SerializedName(ConfigKeyUtil.EXPANDED_SCREEN) val expandedScreenEnabled: Boolean = true,

    /** 大屏宽度阈值（单位：dp，屏幕宽度达到该值时触发大屏布局） */
    @SerializedName(ConfigKeyUtil.EXPANDED_SCREEN_THRESHOLD) val expandedScreenThreshold: String = "600",

    /** 网格布局单列最小宽度（单位：dp） */
    @SerializedName(ConfigKeyUtil.GRID_CELL_MIN_SIZE) val gridCellMinSize: String = "340",

    /** 自动切换到瀑布流视图的图片数量阈值 */
    @SerializedName(ConfigKeyUtil.AUTO_IMAGE_PREVIEW_COUNT) val autoImagePreviewCount: String = "1150",

    /** 是否隐藏电池提醒（关闭首页弹出的后台电池优化提醒 Banner） */
    @SerializedName(ConfigKeyUtil.HIDE_BATTERY_BANNER) val hideBatteryBanner: Boolean = false,
)
