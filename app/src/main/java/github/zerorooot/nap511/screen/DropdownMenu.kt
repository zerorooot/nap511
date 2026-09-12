package github.zerorooot.nap511.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

sealed interface AppBarAction {
    val label: String
    val icon: ImageVector?
}

enum class MenuItemAction(
    override val label: String,
    override val icon: ImageVector,
    val isDestructive: Boolean = false
) : AppBarAction {
    // 文件列表菜单项
    CUT_FILE("剪切文件", Icons.Outlined.ContentCut),
    RENAME_FILE("重新命名", Icons.Outlined.Edit),
    FILE_INFO("文件信息", Icons.Outlined.Info),
    FORCE_OPEN("强行打开", Icons.AutoMirrored.Outlined.OpenInNew),
    ARIA2_DOWNLOAD("Aria2下载", Icons.Outlined.Download),
    DELETE_FILE("删除文件", Icons.Outlined.Delete, isDestructive = true),

    // 回收站菜单项
    RESTORE_FILE("还原文件", Icons.Outlined.Restore),

    // 离线任务菜单项
    COPY_LINK("复制链接", Icons.Outlined.ContentCopy),

    // 文件页顶栏菜单项
    GALLERY_MODE("瀑布视图", Icons.Outlined.Image),
    FILE_SORT("文件排序", Icons.AutoMirrored.Outlined.Sort),
    REFRESH_FILES("刷新文件", Icons.Outlined.Refresh),
    VIDEO_SCHEDULE("视频时间", Icons.Outlined.Schedule),

    // 离线页顶栏菜单项
    COPY_PAGE_LINK("复制本页链接", Icons.Outlined.ContentCopy),
    CLEAR_COMPLETED("清空已完成", Icons.Outlined.DoneAll),
    CLEAR_FAILED("清空已失败", Icons.Outlined.Cancel, isDestructive = true),

    // 日志页顶栏菜单项
    SCROLL_TOP("滚动顶部", Icons.Outlined.VerticalAlignTop),
    SCROLL_BOTTOM("滚动底部", Icons.Outlined.VerticalAlignBottom),
    EXPORT_LOG("导出日志", Icons.Outlined.Share),
    REFRESH_LOG("刷新日志", Icons.Outlined.Refresh),
    CLEAR_LOG("清空日志", Icons.Outlined.DeleteSweep, isDestructive = true),

    // 查重页顶栏菜单项
    START_DEDUP("开始查重", Icons.Outlined.FindInPage),
    ONE_KEY_DEDUP("一键去重", Icons.Outlined.AutoFixHigh),
    DELETE_EMPTY_FILES("删空文件", Icons.Outlined.Delete, isDestructive = true),

    // WebView 顶栏菜单项
    REFRESH_PAGE("页面刷新", Icons.Outlined.Refresh),
    SEARCH_IN_PAGE("页内查找", Icons.Outlined.FindInPage),
    SHARE_LINK("分享链接", Icons.Outlined.Share),
    CHANGE_ENCODING("修改编码", Icons.Outlined.Translate)
}

@Composable
private fun MyDropdownMenu(
    listItems: List<MenuItemAction>,
    modifier: Modifier,
    icon: @Composable () -> Unit,
    onClick: (MenuItemAction, Int) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = modifier
    ) {
        IconButton(
            content = icon,
            onClick = {
                expanded = true
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
            shape = RoundedCornerShape(16.dp)
        ) {
            listItems.forEachIndexed { itemIndex, item ->
                val isDelete = item.isDestructive || item.label.contains("删除")
                if (isDelete && itemIndex > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                DropdownMenuItem(
                    onClick = {
                        onClick.invoke(item, itemIndex)
                        expanded = false
                    },
                    text = {
                        Text(
                            text = item.label,
                            color = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun FileMoreMenu(onClick: (MenuItemAction, Int) -> Unit) {
    val listOf = listOf(
        MenuItemAction.CUT_FILE,
        MenuItemAction.RENAME_FILE,
        MenuItemAction.FILE_INFO,
        MenuItemAction.FORCE_OPEN,
        MenuItemAction.ARIA2_DOWNLOAD,
        MenuItemAction.DELETE_FILE
    )
    BaseMoreMenu(listOf, onClick)
}

@Composable
fun RecycleMoreMenu(onClick: (MenuItemAction, Int) -> Unit) {
    val listOf = listOf(
        MenuItemAction.RESTORE_FILE,
        MenuItemAction.DELETE_FILE
    )
    BaseMoreMenu(listOf, onClick)
}

@Composable
fun OfflineFileMoreMenu(onClick: (MenuItemAction, Int) -> Unit) {
    val listOf = listOf(
        MenuItemAction.COPY_LINK,
        MenuItemAction.FILE_INFO,
        MenuItemAction.DELETE_FILE
    )
    BaseMoreMenu(listOf, onClick)
}

@Composable
fun BaseMoreMenu(listOf: List<MenuItemAction>, onClick: (MenuItemAction, Int) -> Unit) {
    MyDropdownMenu(
        listOf,
        Modifier
            .fillMaxHeight()
            .wrapContentSize(Alignment.Center),
        {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Open Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }, onClick
    )
}

@Composable
fun FileAppTopBarDropdownMenu(onClick: (MenuItemAction, Int) -> Unit) {
    val listOf = listOf(
        MenuItemAction.GALLERY_MODE,
        MenuItemAction.FILE_SORT,
        MenuItemAction.REFRESH_FILES,
        MenuItemAction.VIDEO_SCHEDULE
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
fun OfflineFileAppTopBarDropdownMenu(onClick: (MenuItemAction, Int) -> Unit) {
    val listOf = listOf(
        MenuItemAction.REFRESH_FILES,
        MenuItemAction.COPY_PAGE_LINK,
        MenuItemAction.CLEAR_COMPLETED,
        MenuItemAction.CLEAR_FAILED
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
fun LogScreenTopBarDropdownMenu(onClick: (MenuItemAction, Int) -> Unit) {
    val listOf = listOf(
        MenuItemAction.SCROLL_TOP,
        MenuItemAction.SCROLL_BOTTOM,
        MenuItemAction.EXPORT_LOG,
        MenuItemAction.REFRESH_LOG,
        MenuItemAction.CLEAR_LOG
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
fun RepeatFileTopBarDropdownMenu(onClick: (MenuItemAction, Int) -> Unit) {
    val listOf = listOf(
        MenuItemAction.START_DEDUP,
        MenuItemAction.ONE_KEY_DEDUP,
        MenuItemAction.DELETE_EMPTY_FILES
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
fun HtmlWebViewTopBarDropdownMenu(onClick: (MenuItemAction, Int) -> Unit) {
    val listOf = listOf(
        MenuItemAction.REFRESH_PAGE,
        MenuItemAction.SEARCH_IN_PAGE,
        MenuItemAction.SHARE_LINK,
        MenuItemAction.CHANGE_ENCODING
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
private fun BaseAppTorBarMenu(listOf: List<MenuItemAction>, onClick: (MenuItemAction, Int) -> Unit) {
    MyDropdownMenu(
        listOf,
        Modifier.wrapContentSize(Alignment.TopEnd),
        {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Open Options"
            )
        }, onClick
    )
}
