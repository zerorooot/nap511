package github.zerorooot.nap511.bean

import kotlinx.serialization.Serializable

// 导航配置项辅助类
data class DrawerMenuItem(val iconRes: Int, val label: String, val route: Route)

sealed interface Route {
    // 抽屉导航页面
    @Serializable
    data object Login : Route

    @Serializable
    data object MyFile : Route

    @Serializable
    data object OfflineDownload : Route

    @Serializable
    data object OfflineList : Route

    @Serializable
    data object RecycleBin : Route

    @Serializable
    data object AdvancedSettings : Route

    @Serializable
    data object LogScreen : Route

    @Serializable
    data object Photo : Route

    @Serializable
    data object WebScreen : Route

    @Serializable
    data object ExitApp : Route

    @Serializable
    data object VerifyMagnetLinkAccount : Route

    @Serializable
    data object VerifyVideoAccount : Route


    @Serializable
    data object RepeatFile : Route
    // 假设在别的 Screen 中跳转的“详情页”或“子页面”（带参数示例）
//    @Serializable
//    data class FileDetail(val fileId: String, val fileName: String) : Route
}

// 定义导航事件
sealed interface NavEvent {
    data class NavigateToScreen(val route: Route) : NavEvent
}