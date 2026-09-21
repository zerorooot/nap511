package github.zerorooot.nap511.screen.file

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.screen.components.BaseTopAppBar
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.copy
import kotlinx.serialization.Serializable

/**
 * 离线下载任务模块 Nav3 路由 Key 规范
 */
@Serializable
sealed interface OfflineNavKey : NavKey {
    /** 任务列表路由（支持按“全部/下载中/已完成/失败”分类浏览） */
    @Serializable
    data object TaskList : OfflineNavKey

    /** 任务详情路由，携带特定任务的 offlineTask */
    @Serializable
    data class TaskDetail(val offlineTask: OfflineTask) : OfflineNavKey
}


/**
 * 离线任务空状态占位面板（大屏未选中或列表为空时展示）
 */
@Composable
fun OfflineTaskEmptyPlaceholder(onNavigateToNewTask: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无选中任务",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onNavigateToNewTask) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新建离线下载")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailPane(
    task: OfflineTask,
    onDeleteTask: (OfflineTask) -> Unit,
    onOpenFile: (String) -> Unit
) {
    Scaffold(
        topBar = {
            BaseTopAppBar(
                title = { Text("任务详情") },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 头部卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (task.percentDone.toFloat() / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "状态: ${task.percentString}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "进度: ${task.percentDone}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 属性列表
            TaskDetailInfoItem(
                icon = Icons.Default.Storage,
                label = "文件总大小",
                value = task.sizeString
            )
            TaskDetailInfoItem(
                icon = Icons.Default.Schedule,
                label = "创建时间",
                value = task.timeString
            )
            TaskDetailInfoItem(icon = Icons.Default.Info, label = "任务哈希", value = task.infoHash)
            if (task.url.isNotEmpty()) {
                val current = LocalContext.current
                TaskDetailInfoItem(
                    icon = Icons.Default.Link,
                    label = "下载链接",
                    value = task.url
                ) {
                    task.url.copy(current)
                    App.instance.toast("${task.name} 下载链接复制成功")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val targetCid = task.fileId.ifEmpty { task.wpPathId }
                if (targetCid.isNotEmpty()) {
                    Button(
                        onClick = { onOpenFile(targetCid) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打开文件目录")
                    }
                }

                OutlinedButton(
                    onClick = { onDeleteTask(task) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除任务")
                }
            }
        }
    }
}

@Composable
private fun TaskDetailInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick != null) {
                    onClick!!.invoke()
                }) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}


@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true)
@Composable
private fun TaskDetailPanePreview() {
    MaterialTheme {
        TaskDetailPane(
            task = OfflineTask(
                infoHash = "sample_hash_12345",
                name = "Ubuntu-24.04-desktop-amd64.iso",
                sizeString = "5.2 GB",
                percentString = "100%",
                percentDone = 100.0,
                status = 11,
                timeString = "2026-09-17 12:00:00",
                fileId = "sample_file_id"
            ),
//            showBackButton = false,
//            onBack = {},
            onDeleteTask = {},
            onOpenFile = {}
        )
    }
}

