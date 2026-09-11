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
import androidx.compose.material.icons.outlined.CleaningServices
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

data class DropdownMenuItemSpec(
    val text: String,
    val icon: ImageVector? = null,
    val isDestructive: Boolean = false
)

@Composable
private fun MyDropdownMenu(
    listItems: List<DropdownMenuItemSpec>,
    modifier: Modifier,
    icon: @Composable () -> Unit,
    onClick: (String, Int) -> Unit
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
                val isDelete = item.isDestructive || item.text.contains("删除")
                if (isDelete && itemIndex > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                DropdownMenuItem(
                    onClick = {
                        onClick.invoke(item.text, itemIndex)
                        expanded = false
                    },
                    text = {
                        Text(
                            text = item.text,
                            color = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = item.icon?.let { imageVector ->
                        {
                            Icon(
                                imageVector = imageVector,
                                contentDescription = item.text,
                                tint = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun FileMoreMenu(onClick: (String, Int) -> Unit) {
    val listOf = listOf(
        DropdownMenuItemSpec("剪切文件", Icons.Outlined.ContentCut),
        DropdownMenuItemSpec("重新命名", Icons.Outlined.Edit),
        DropdownMenuItemSpec("文件信息", Icons.Outlined.Info),
        DropdownMenuItemSpec("强行打开", Icons.AutoMirrored.Outlined.OpenInNew),
        DropdownMenuItemSpec("Aria2下载", Icons.Outlined.Download),
        DropdownMenuItemSpec("删除文件", Icons.Outlined.Delete, isDestructive = true)
    )
    BaseMoreMenu(listOf, onClick)
}

@Composable
fun RecycleMoreMenu(onClick: (String, Int) -> Unit) {
    val listOf = listOf(
        DropdownMenuItemSpec("还原文件", Icons.Outlined.Restore),
        DropdownMenuItemSpec("删除文件", Icons.Outlined.Delete, isDestructive = true)
    )
    BaseMoreMenu(listOf, onClick)
}

@Composable
fun OfflineFileMoreMenu(onClick: (String, Int) -> Unit) {
    val listOf = listOf(
        DropdownMenuItemSpec("复制链接", Icons.Outlined.ContentCopy),
        DropdownMenuItemSpec("文件信息", Icons.Outlined.Info),
        DropdownMenuItemSpec("删除文件", Icons.Outlined.Delete, isDestructive = true)
    )
    BaseMoreMenu(listOf, onClick)
}

@Composable
fun BaseMoreMenu(listOf: List<DropdownMenuItemSpec>, onClick: (String, Int) -> Unit) {
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
fun FileAppTopBarDropdownMenu(onClick: (String, Int) -> Unit) {
    val listOf = listOf(
        DropdownMenuItemSpec("大图模式", Icons.Outlined.Image),
        DropdownMenuItemSpec("缓存清空", Icons.Outlined.CleaningServices),
        DropdownMenuItemSpec("文件排序", Icons.AutoMirrored.Outlined.Sort),
        DropdownMenuItemSpec("刷新文件", Icons.Outlined.Refresh),
        DropdownMenuItemSpec("视频时间", Icons.Outlined.Schedule)
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
fun OfflineFileAppTopBarDropdownMenu(onClick: (String, Int) -> Unit) {
    val listOf = listOf(
        DropdownMenuItemSpec("刷新文件", Icons.Outlined.Refresh),
        DropdownMenuItemSpec("复制本页链接", Icons.Outlined.ContentCopy),
        DropdownMenuItemSpec("清空已完成", Icons.Outlined.DoneAll),
        DropdownMenuItemSpec("清空已失败", Icons.Outlined.Cancel, isDestructive = true)
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
fun LogScreenTopBarDropdownMenu(onClick: (String, Int) -> Unit) {
    val listOf = listOf(
        DropdownMenuItemSpec("滚动顶部", Icons.Outlined.VerticalAlignTop),
        DropdownMenuItemSpec("滚动底部", Icons.Outlined.VerticalAlignBottom),
        DropdownMenuItemSpec("导出日志", Icons.Outlined.Share),
        DropdownMenuItemSpec("刷新日志", Icons.Outlined.Refresh),
        DropdownMenuItemSpec("清空日志", Icons.Outlined.DeleteSweep, isDestructive = true),
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
fun RepeatFileTopBarDropdownMenu(onClick: (String, Int) -> Unit) {
    val listOf = listOf(
        DropdownMenuItemSpec("开始查重", Icons.Outlined.FindInPage),
        DropdownMenuItemSpec("一键去重", Icons.Outlined.AutoFixHigh),
        DropdownMenuItemSpec("删空文件", Icons.Outlined.Delete, isDestructive = true)
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
fun HtmlWebViewTopBarDropdownMenu(onClick: (String, Int) -> Unit) {
    val listOf = listOf(
        DropdownMenuItemSpec("页面刷新", Icons.Outlined.Refresh),
        DropdownMenuItemSpec("页内查找", Icons.Outlined.FindInPage),
        DropdownMenuItemSpec("分享链接", Icons.Outlined.Share),
        DropdownMenuItemSpec("修改编码", Icons.Outlined.Translate)
    )
    BaseAppTorBarMenu(listOf = listOf, onClick = onClick)
}

@Composable
private fun BaseAppTorBarMenu(listOf: List<DropdownMenuItemSpec>, onClick: (String, Int) -> Unit) {
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