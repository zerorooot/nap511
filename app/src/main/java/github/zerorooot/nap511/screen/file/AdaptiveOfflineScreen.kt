package github.zerorooot.nap511.screen.file

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import github.zerorooot.nap511.util.isDualPane
import github.zerorooot.nap511.util.rememberListDetailDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import kotlinx.serialization.Serializable

/**
 * 离线下载任务模块 Nav3 路由 Key 规范
 */
@Serializable
sealed interface OfflineNavKey : NavKey {
    /** 任务列表路由（支持按“全部/下载中/已完成/失败”分类浏览） */
    @Serializable
    data object TaskList : OfflineNavKey

    /** 任务详情路由，携带特定任务的 infoHash */
    @Serializable
    data class TaskDetail(val infoHash: String) : OfflineNavKey
}

/**
 * 自适应离线下载中心主界面
 *
 * 核心设计：
 * 1. 采用 Nav3 [ListDetailSceneStrategy] 与 [NavDisplay] 驱动；
 * 2. 多设备自适应：
 *    - 手机端：左侧任务列表独占屏幕，点击某任务进入全屏详情页，左上角提供返回箭头；
 *    - 平板/折叠屏/桌面：双栏并排，左侧为任务列表与操作栏，右侧常驻展示任务详情；
 * 3. 智能选择与占位：
 *    - 宽屏进入时，若未选中任何任务，自动选择首条任务展开；
 *    - 若当前没有任何离线任务，右侧显示视觉友好的“新建下载”引导卡片。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveOfflineScreen(
    offlineFileViewModel: OfflineFileViewModel,
    isGridScreen: Boolean,
    gridCellMinSize: Dp,
    getFiles: (String) -> Unit,
    onDrawerClick: () -> Unit,
    onBackToFiles: () -> Unit,
    onNavigateToNewTask: () -> Unit = {}
) {
    val uiState by offlineFileViewModel.uiState.collectAsStateWithLifecycle()

    // 1. 依据屏幕宽度规格计算分栏指令与双栏激活状态
    val directive = rememberListDetailDirective()
    val isDualPane = directive.isDualPane

    // 2. 当前选中的离线任务对象与 Nav3 返回栈
    var selectedTask by remember { mutableStateOf<OfflineTask?>(null) }
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(OfflineNavKey.TaskList)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    // 3. 辅助函数：根据 infoHash 查找对应的离线任务
    fun findTask(infoHash: String?): OfflineTask? {
        if (infoHash == null) return null
        if (selectedTask?.infoHash == infoHash) return selectedTask
        return uiState.completedList.find { it.infoHash == infoHash }
            ?: uiState.downloadingList.find { it.infoHash == infoHash }
            ?: uiState.failedList.find { it.infoHash == infoHash }
    }

    // 4. 宽屏初始状态下自动选取第一条任务
    LaunchedEffect(uiState.completedList, uiState.downloadingList, uiState.failedList, isDualPane) {
        if (selectedTask == null && isDualPane) {
            val first = uiState.completedList.firstOrNull()
                ?: uiState.downloadingList.firstOrNull()
                ?: uiState.failedList.firstOrNull()
            selectedTask = first
        }
    }

    // 5. 详情面板公共渲染方法（统一复用，避免在 placeholder 与 entry 间冗余重复）
    val renderTaskDetail: @Composable (task: OfflineTask, showBackButton: Boolean, onDeleted: () -> Unit) -> Unit =
        { targetTask, showBack, onDeleted ->
            TaskDetailPane(
                task = targetTask,
                showBackButton = showBack,
                onBack = { backStack.removeLastOrNull() },
                onDeleteTask = {
                    offlineFileViewModel.delete(it)
                    onDeleted()
                },
                onOpenFile = { targetCid ->
                    onBackToFiles()
                    getFiles(targetCid)
                }
            )
        }

    // 6. Nav3 驱动的自适应展示容器
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategy = listDetailStrategy,
        entryProvider = entryProvider<NavKey> {
            // 左侧任务列表面板
            entry<OfflineNavKey.TaskList>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        val current = selectedTask
                        if (current != null) {
                            renderTaskDetail(current, false) { selectedTask = null }
                        } else {
                            OfflineTaskEmptyPlaceholder(onNavigateToNewTask)
                        }
                    }
                )
            ) {
                OfflineFileContent(
                    uiState = uiState,
                    gridCellMinSize = gridCellMinSize,
                    isGridScreen = isGridScreen,
                    onRefresh = { offlineFileViewModel.refresh() },
                    onClearFinish = { offlineFileViewModel.clearFinish() },
                    onClearError = { offlineFileViewModel.clearError() },
                    onDeleteTask = { offlineFileViewModel.delete(it) },
                    onOpenTaskDialog = { task ->
                        selectedTask = task
                        // 手机单栏模式下点击进入详情路由；双栏模式下仅刷新选中的任务
                        if (!isDualPane) {
                            backStack.add(OfflineNavKey.TaskDetail(task.infoHash))
                        }
                    },
                    onCloseTaskDialog = { offlineFileViewModel.closeOfflineDialog() },
                    onLoadMoreCompleted = { offlineFileViewModel.loadMoreCompletedTasks() },
                    onLoadMoreDownloading = { offlineFileViewModel.loadMoreDownloadingTasks() },
                    onLoadMoreFailed = { offlineFileViewModel.loadMoreFailedTasks() },
                    getFiles = getFiles,
                    onClick = { action ->
                        when (action) {
                            "ModalNavigationDrawerMenu" -> onDrawerClick()
                            "MyFile" -> onBackToFiles()
                        }
                    }
                )
            }

            // 右侧任务详情面板
            entry<OfflineNavKey.TaskDetail>(
                metadata = ListDetailSceneStrategy.detailPane()
            ) { detailKey ->
                val task = findTask(detailKey.infoHash)
                if (task != null) {
                    renderTaskDetail(task, !isDualPane) {
                        backStack.removeLastOrNull()
                    }
                } else {
                    TaskNotFoundPane()
                }
            }
        }
    )
}

/**
 * 离线任务空状态占位面板（大屏未选中或列表为空时展示）
 */
@Composable
private fun OfflineTaskEmptyPlaceholder(onNavigateToNewTask: () -> Unit) {
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

/**
 * 任务未找到占位面板
 */
@Composable
private fun TaskNotFoundPane() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text("未找到该任务")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailPane(
    task: OfflineTask,
    showBackButton: Boolean,
    onBack: () -> Unit = {},
    onDeleteTask: (OfflineTask) -> Unit,
    onOpenFile: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务详情") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                }
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
            TaskDetailInfoItem(icon = Icons.Default.Storage, label = "文件总大小", value = task.sizeString)
            TaskDetailInfoItem(icon = Icons.Default.Schedule, label = "创建时间", value = task.timeString)
            TaskDetailInfoItem(icon = Icons.Default.Info, label = "任务哈希", value = task.infoHash)
            if (task.url.isNotEmpty()) {
                TaskDetailInfoItem(icon = Icons.Default.Link, label = "下载链接", value = task.url)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val targetCid = if (task.fileId.isEmpty()) task.wpPathId else task.fileId
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
    value: String
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
        Column(modifier = Modifier.weight(1f)) {
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
            showBackButton = false,
            onBack = {},
            onDeleteTask = {},
            onOpenFile = {}
        )
    }
}

