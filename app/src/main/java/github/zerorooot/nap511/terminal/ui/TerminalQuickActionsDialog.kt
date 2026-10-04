package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalQuickActionsSheet(
    onDismiss: () -> Unit,
    onExecuteCommand: (String) -> Unit,
    onClearScreen: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseTerminal: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "终端快捷操作 (Quick Actions)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            QuickActionItem(
                icon = Icons.AutoMirrored.Filled.Help,
                title = "查看命令帮助手册 (?)",
                onClick = {
                    onDismiss()
                    onExecuteCommand("?")
                }
            )

            QuickActionItem(
                icon = Icons.Default.Folder,
                title = "列出当前文件详情 (ls -l)",
                onClick = {
                    onDismiss()
                    onExecuteCommand("ls -l")
                }
            )

            QuickActionItem(
                icon = Icons.Default.PieChart,
                title = "查看网盘容量配额 (df -h)",
                onClick = {
                    onDismiss()
                    onExecuteCommand("df -h")
                }
            )

            QuickActionItem(
                icon = Icons.Default.ClearAll,
                title = "清空终端屏幕 (clear)",
                onClick = {
                    onDismiss()
                    onClearScreen()
                }
            )

            QuickActionItem(
                icon = Icons.Default.ContentCopy,
                title = "复制终端全部输出内容",
                onClick = {
                    onDismiss()
                    onCopyAll()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            QuickActionItem(
                icon = Icons.Default.Menu,
                title = "打开应用侧边栏",
                onClick = {
                    onDismiss()
                    onOpenDrawer()
                }
            )

            QuickActionItem(
                icon = Icons.Default.Close,
                title = "退出命令行终端",
                onClick = {
                    onDismiss()
                    onCloseTerminal()
                }
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, fontSize = 14.sp)
    }
}
