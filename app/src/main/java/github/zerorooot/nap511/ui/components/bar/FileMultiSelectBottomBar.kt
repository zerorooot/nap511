package github.zerorooot.nap511.ui.components.bar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 文件多选模式底部操作栏：
 * 1. 位于大屏单手操作黄金舒适区，承载常用高频动作（剪切、重命名、删除、更多）；
 * 2. 严格的 0 项选中禁用保护：selectedCount == 0 时动作全部置灰不可点击，防止无效触发；
 * 3. 显式适配 WindowInsets.navigationBars，确保全面屏手势条或虚拟三键不遮挡按钮；
 * 4. 结合高频直达按钮与 DropdownMenuItem 溢出菜单，提升视觉精致度与交互舒适度；
 * 5. 点击事件统一通过 onActionClick 派发。
 */
@Composable
fun FileMultiSelectBottomBar(
    selectedCount: Int,
    onActionClick: (TopBarAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = selectedCount > 0

    // 常驻高频核心操作
    val primaryActions = listOf(
        TopBarAction.CUT,
        TopBarAction.BATCH_RENAME,
        TopBarAction.DELETE,
    )

    // 归入 DropdownMenu 溢出菜单的次要/拓展操作
    val secondaryActions = listOf(
        TopBarAction.TOP,
        TopBarAction.STAR,
        TopBarAction.UNZIP_ALL,
    )

    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 高频直达按钮
            primaryActions.forEach { action ->
                MultiSelectBottomBarItem(
                    imageVector = action.icon,
                    label = action.label,
                    enabled = isEnabled,
                    modifier = Modifier.weight(1f),
                    isDestructive = action.isDestructive || action == TopBarAction.DELETE,
                    onClick = { onActionClick(action) }
                )
            }

            //  2. “更多” 溢出菜单按钮（包含 DropdownMenu & DropdownMenuItem）
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                MultiSelectBottomBarItem(
                    imageVector = Icons.Default.MoreVert,
                    label = "更多选项",
                    enabled = isEnabled,
                    onClick = { overflowMenuExpanded = true }
                )

                DropdownMenu(
                    expanded = overflowMenuExpanded && isEnabled,
                    onDismissRequest = { overflowMenuExpanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    secondaryActions.forEach { action ->
                        val isDelete = action.isDestructive || action == TopBarAction.DELETE
                        DropdownMenuItem(
                            onClick = {
                                overflowMenuExpanded = false
                                onActionClick(action)
                            },
                            text = {
                                Text(
                                    text = action.label,
                                    color = if (isDelete) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.label,
                                    tint = if (isDelete) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSelectBottomBarItem(
    imageVector: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

