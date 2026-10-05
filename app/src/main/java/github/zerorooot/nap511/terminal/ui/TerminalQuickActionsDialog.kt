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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 终端快捷操作配置模型 (Quick Action Model)
 *
 * @param id 动作唯一标识符
 * @param title 操作项展示文案
 * @param icon 图标资源
 * @param hasDividerBefore 是否在当前项前插入分割线
 * @param onClick 点击事件回调
 */
@Immutable
data class TerminalQuickAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val hasDividerBefore: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 终端快捷操作行为聚合回调
 */
@Immutable
data class TerminalQuickActionCallbacks(
    val onExecuteCommand: (String) -> Unit = {},
    val onClearScreen: () -> Unit = {},
    val onCopyAll: () -> Unit = {},
    val onOpenDrawer: () -> Unit = {},
    val onCloseTerminal: () -> Unit = {}
)

/**
 * 终端标准快捷操作生成工厂
 */
object TerminalQuickActionDefaults {

    fun defaultActions(
        onDismiss: () -> Unit,
        callbacks: TerminalQuickActionCallbacks
    ): List<TerminalQuickAction> = listOf(
        TerminalQuickAction(
            id = "help",
            title = "查看命令帮助手册 (?)",
            icon = Icons.AutoMirrored.Filled.Help,
            onClick = {
                onDismiss()
                callbacks.onExecuteCommand("?")
            }
        ),
        TerminalQuickAction(
            id = "ls",
            title = "列出当前文件详情 (ls -l)",
            icon = Icons.Default.Folder,
            onClick = {
                onDismiss()
                callbacks.onExecuteCommand("ls -l")
            }
        ),
        TerminalQuickAction(
            id = "df",
            title = "查看网盘容量配额 (df -h)",
            icon = Icons.Default.PieChart,
            onClick = {
                onDismiss()
                callbacks.onExecuteCommand("df -h")
            }
        ),
        TerminalQuickAction(
            id = "clear",
            title = "清空终端屏幕 (clear)",
            icon = Icons.Default.ClearAll,
            onClick = {
                onDismiss()
                callbacks.onClearScreen()
            }
        ),
        TerminalQuickAction(
            id = "copy_all",
            title = "复制终端全部输出内容",
            icon = Icons.Default.ContentCopy,
            onClick = {
                onDismiss()
                callbacks.onCopyAll()
            }
        ),
        TerminalQuickAction(
            id = "drawer",
            title = "打开应用侧边栏",
            icon = Icons.Default.Menu,
            hasDividerBefore = true,
            onClick = {
                onDismiss()
                callbacks.onOpenDrawer()
            }
        ),
        TerminalQuickAction(
            id = "exit",
            title = "退出命令行终端",
            icon = Icons.Default.Close,
            onClick = {
                onDismiss()
                callbacks.onCloseTerminal()
            }
        )
    )
}

/**
 * 数据驱动的模型化终端快捷操作底部抽屉 (Terminal Quick Actions Sheet)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalQuickActionsSheet(
    actions: List<TerminalQuickAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "终端快捷操作 (Quick Actions)"
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            actions.forEach { action ->
                if (action.hasDividerBefore) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }
                QuickActionItem(
                    icon = action.icon,
                    title = action.title,
                    onClick = action.onClick
                )
            }
        }
    }
}

/**
 * 聚合行为回调的便捷重载方法
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalQuickActionsSheet(
    onDismiss: () -> Unit,
    callbacks: TerminalQuickActionCallbacks,
    modifier: Modifier = Modifier
) {
    val actions = remember(onDismiss, callbacks) {
        TerminalQuickActionDefaults.defaultActions(onDismiss, callbacks)
    }
    TerminalQuickActionsSheet(
        actions = actions,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * 兼容原有扁平参数签名的重载方法，防止外部未重构调用处编译失败
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalQuickActionsSheet(
    onDismiss: () -> Unit,
    onExecuteCommand: (String) -> Unit,
    onClearScreen: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenDrawer: () -> Unit,
    onCloseTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callbacks = remember(onExecuteCommand, onClearScreen, onCopyAll, onOpenDrawer, onCloseTerminal) {
        TerminalQuickActionCallbacks(
            onExecuteCommand = onExecuteCommand,
            onClearScreen = onClearScreen,
            onCopyAll = onCopyAll,
            onOpenDrawer = onOpenDrawer,
            onCloseTerminal = onCloseTerminal
        )
    }
    TerminalQuickActionsSheet(
        onDismiss = onDismiss,
        callbacks = callbacks,
        modifier = modifier
    )
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
