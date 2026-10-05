package github.zerorooot.nap511.terminal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.zerorooot.nap511.screen.components.BaseTopAppBar

/**
 * 终端顶部导航栏组件 (Terminal Top Bar)
 *
 * 整合标题、路径上下文信息、清屏/更多下拉菜单，以及底部执行状态极光流光扫描线 (Laser Scanline)。
 * 独立抽离实现高内聚，彻底解耦 TerminalScreen。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalTopBar(
    currentPath: String,
    isExecuting: Boolean,
    onBack: () -> Unit,
    onClearScreen: () -> Unit,
    onShowHelp: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTopDropdown by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        BaseTopAppBar(
            title = {
                Column {
                    Text(
                        text = "Terminal",
                        fontSize = 17.sp,
                        color = TerminalColors.TextPrimary
                    )
                    Text(
                        text = currentPath,
                        fontSize = 11.sp,
                        color = TerminalColors.TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TerminalColors.TextPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = onClearScreen) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清屏",
                        tint = TerminalColors.TextSecondary
                    )
                }
                IconButton(onClick = { showTopDropdown = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = TerminalColors.TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = showTopDropdown,
                    onDismissRequest = { showTopDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("帮助手册") },
                        onClick = {
                            showTopDropdown = false
                            onShowHelp()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("复制全部") },
                        onClick = {
                            showTopDropdown = false
                            onCopyAll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("打开侧边") },
                        onClick = {
                            showTopDropdown = false
                            onOpenDrawer()
                        }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TerminalColors.SurfaceTopBar
            )
        )

        // 顶栏底部极光流光扫描线 (Laser Scanline)
        AnimatedVisibility(
            visible = isExecuting,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = TerminalColors.Prompt,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
