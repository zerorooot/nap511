package github.zerorooot.nap511.screenitem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import github.zerorooot.nap511.bean.DrawerMenuItem
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.util.ConfigKeyUtil

object DrawerMenuItems {
    fun buildMenuItems(isLogEnabled: Boolean): List<DrawerMenuItem> {
        return arrayListOf(
            DrawerMenuItem(Icons.Default.Cloud, ConfigKeyUtil.MY_FILE, Route.MyFile),
            DrawerMenuItem(
                Icons.Default.CloudDownload,
                ConfigKeyUtil.OFFLINE_DOWNLOAD,
                Route.OfflineDownload
            ),
            DrawerMenuItem(
                Icons.Default.CloudDone, ConfigKeyUtil.OFFLINE_LIST, Route.OfflineList
            ),
            // DrawerMenuItem(Icons.Default.Web, ConfigKeyUtil.WEB, Route.WebScreen),
            DrawerMenuItem(
                Icons.Default.Delete, ConfigKeyUtil.RECYCLE_BIN, Route.RecycleBin
            ),
            DrawerMenuItem(
                Icons.Default.Settings,
                ConfigKeyUtil.ADVANCED_SETTINGS,
                Route.AdvancedSettings
            ),
        ).apply {
            if (isLogEnabled) {
                add(
                    DrawerMenuItem(
                        Icons.Default.Android, ConfigKeyUtil.LOG_SCREEN, Route.LogScreen
                    )
                )
            }
            add(
                DrawerMenuItem(
                    Icons.AutoMirrored.Default.ExitToApp,
                    ConfigKeyUtil.EXIT_APPLICATION,
                    Route.ExitApp
                )
            )
        }
    }
}