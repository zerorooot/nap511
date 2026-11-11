package github.zerorooot.nap511.screen.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.di.toast
import androidx.core.net.toUri

/**
 * 开源许可证微标类型（映射到 Material 3 色系语义）
 */
enum class LicenseBadgeType {
    APACHE, // 浅蓝/主色调
    MIT,    // 薄荷绿/辅助色调
    GPL,    // 蓝紫/次级色调
    BSD     // 暖橙/强调色调
}

/**
 * 开源库数据模型
 * @param name 库名称
 * @param author 作者或维护团队/出品方
 * @param license 开源协议类型
 * @param licenseBadgeType 协议微标色彩类别
 * @param description 功能与用途简述
 * @param githubUrl GitHub 仓库主页地址
 */
data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String,
    val licenseBadgeType: LicenseBadgeType = LicenseBadgeType.APACHE,
    val description: String,
    val githubUrl: String
)

/**
 * 免责声明条款数据模型
 */
data class DisclaimerClause(
    val index: String,
    val title: String,
    val content: String
)

/**
 * 打开外部浏览器辅助方法
 */
fun openExternalUrl(context: Context, url: String) {
    try {
        XLog.i("准备打开外部链接: $url")
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        XLog.e("打开外部链接失败: $url", e)
        context.toast("无法打开外部链接: ${e.message}")
    }
}

/**
 * 关于应用页面相关静态常量与配置定义
 */
object AboutConstants {
    const val GITHUB_REPO_URL = "https://github.com/zerorooot/nap511"
    const val GITHUB_COMMITS_URL = "https://github.com/zerorooot/nap511/commits"
    const val GITHUB_RELEASES_URL = "https://github.com/zerorooot/nap511/releases"
    const val GITHUB_ISSUES_URL = "https://github.com/zerorooot/nap511/issues"
    const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/zerorooot/nap511/releases/latest"
    
    const val APP_SLOGAN = "一个 Android 自用的 115 网盘客户端"
    const val COPYRIGHT = "Copyright © 2024-2026 nap511 Contributors\nAll rights reserved."

    /**
     * 语义化版本号对比工具
     * @return 若 [latest] 版本号高于 [current]，返回 true，否则返回 false
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = current.removePrefix("v").removePrefix("V").trim()

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        if (latestParts.isEmpty() || currentParts.isEmpty()) return false

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * 结构化第三方开源依赖清单
     */
    val LIBRARIES = listOf(
        OpenSourceLibrary(
            name = "Android Jetpack & Compose",
            author = "The Android Open Source Project (Google)",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "现代 Android 声明式 UI 体系、Material 3 组件设计系统、Adaptive 自适应大屏布局及 Navigation 路由框架。",
            githubUrl = "https://github.com/androidx/androidx"
        ),
        OpenSourceLibrary(
            name = "Kotlin & Coroutines",
            author = "JetBrains s.r.o.",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "现代化移动编程语言核心、协程并发调度体系及响应式 Flow 异步数据流。",
            githubUrl = "https://github.com/Kotlin/kotlinx.coroutines"
        ),
        OpenSourceLibrary(
            name = "Kotlinx Serialization",
            author = "JetBrains s.r.o.",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "无反射轻量级 JSON 编解码器与跨组件页面导航强类型序列化参数支持。",
            githubUrl = "https://github.com/Kotlin/kotlinx.serialization"
        ),
        OpenSourceLibrary(
            name = "OkHttp",
            author = "Square, Inc.",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "高效可靠的 HTTP/2 传输客户端，提供连接池复用、透明压缩、TLS 握手及网络请求拦截支持。",
            githubUrl = "https://github.com/square/okhttp"
        ),
        OpenSourceLibrary(
            name = "Retrofit",
            author = "Square, Inc.",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "类型安全的 RESTful API 客户端，将 HTTP API 声明式映射为 Kotlin 挂起函数。",
            githubUrl = "https://github.com/square/retrofit"
        ),
        OpenSourceLibrary(
            name = "Coil",
            author = "Coil Contributors",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "基于 Kotlin 协程构建的高性能轻量图片加载引擎，支持 GIF 动图解码与内存/磁盘二级缓存。",
            githubUrl = "https://github.com/coil-kt/coil"
        ),
        OpenSourceLibrary(
            name = "GSYVideoPlayer",
            author = "CarGuo",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "高度可定制的多核心 Android 视频播放器框架，支持手势调节、全屏无缝切换与弹幕扩展。",
            githubUrl = "https://github.com/CarGuo/GSYVideoPlayer"
        ),
        OpenSourceLibrary(
            name = "AndroidX Media3 / ExoPlayer",
            author = "Google LLC",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "底层音视频流媒体解码播放内核，提供精细化媒体缓存、自适应码率切换与网络数据源桥接。",
            githubUrl = "https://github.com/androidx/media"
        ),
        OpenSourceLibrary(
            name = "XLog",
            author = "Elvis Hew",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "轻量可靠的高性能 Android 格式化日志框架，支持控制台彩色输出、文件存储与按需拦截过滤。",
            githubUrl = "https://github.com/elvishew/xLog"
        ),
        OpenSourceLibrary(
            name = "ZXing",
            author = "ZXing Authors",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "条形码与二维码多格式编解码库，用于局域网设备互联与快速扫码同步。",
            githubUrl = "https://github.com/zxing/zxing"
        ),
        OpenSourceLibrary(
            name = "Process Phoenix",
            author = "Jake Wharton",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "Android 进程热重启工具，在切换核心配置或重置状态时安全重建应用主进程。",
            githubUrl = "https://github.com/JakeWharton/ProcessPhoenix"
        ),
        OpenSourceLibrary(
            name = "Gson",
            author = "Google LLC",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "成熟稳定的 Java/Kotlin JSON 数据对象序列化与反序列化工具库。",
            githubUrl = "https://github.com/google/gson"
        ),
        OpenSourceLibrary(
            name = "Compose Zoom",
            author = "usuiat",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "为 Jetpack Compose 视图提供流畅的手势平移、双击缩放与自由变换交互支持。",
            githubUrl = "https://github.com/usuiat/Zoomable"
        ),
        OpenSourceLibrary(
            name = "LazyColumnScrollbar",
            author = "nanihadesuka",
            license = "Apache 2.0",
            licenseBadgeType = LicenseBadgeType.APACHE,
            description = "为 Compose LazyColumn 列表提供跟手平滑的可视化滚动条指示器与拖拽快滑支持。",
            githubUrl = "https://github.com/nanihadesuka/LazyColumnScrollbar"
        )
    )

    /**
     * 结构化免责声明条款
     */
    val DISCLAIMER_CLAUSES = listOf(
        DisclaimerClause(
            index = "01",
            title = "非官方客户端声明",
            content = "本项目（nap511）为个人独立开发者自用的第三方开源工具，用于学习 Jetpack Compose 架构与日常自用。本项目与 115 官方（广东一一五科技股份有限公司）无任何隶属、合作、雇佣或商业授权关系。"
        ),
        DisclaimerClause(
            index = "02",
            title = "本地运行与隐私保障",
            content = "本项目绝不架设任何第三方中转或数据收集服务器。所有网络请求均在您的本地设备与 115 官方服务器之间直接通信；账号凭据、Cookie 及个人信息仅加密保存在本地设备存储中，绝不上传至任何未经授权的第三方。"
        ),
        DisclaimerClause(
            index = "03",
            title = "使用合规与风险自担",
            content = "使用者在借助本工具访问 115 网盘时，应自觉遵守国家法律法规及 115 官方服务条款。请勿利用本客户端进行自动化爬虫、高频并发滥用或违规内容转存；因使用者违规操作或不当使用引起的账号受限、封禁或相关损失，均由使用者自行承担。"
        ),
        DisclaimerClause(
            index = "04",
            title = "开源协议与无担保声明",
            content = "本项目依据 GNU General Public License v3.0 协议开源免费发布，代码基于“现状”（AS IS）提供。开发者不对软件的连续可用性、功能完整性或数据稳定性提供任何明示或暗示的担保。"
        )
    )
}
