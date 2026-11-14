package github.zerorooot.nap511.ui.screen.file

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.data.model.CategoryDetailResponse
import github.zerorooot.nap511.ui.components.common.AppBarAction
import github.zerorooot.nap511.ui.components.bar.AppTopBarRepeatFile
import github.zerorooot.nap511.ui.components.common.MenuItemAction
import github.zerorooot.nap511.ui.components.common.MiddleEllipsisText
import github.zerorooot.nap511.ui.components.bar.TopBarAction
import github.zerorooot.nap511.ui.components.item.RepeatFileCardItem
import github.zerorooot.nap511.ui.viewmodel.RepeatFileViewModel
import github.zerorooot.nap511.ui.viewmodel.RepeatUiState
import github.zerorooot.nap511.platform.LocalToastProvider
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import androidx.compose.foundation.lazy.grid.items as gridItems

/**
 * 文件去重界面用户交互动作集合 (RepeatFileActions)
 *
 * 聚合顶部栏操作、下拉刷新、触底加载、路径面包屑点击等事件。
 */
@Immutable
data class RepeatFileActions(
    val onOneKeyDedupClick: () -> Unit,
    val onStartScanClick: () -> Unit,
    val onDeleteEmptyFilesClick: () -> Unit,
    val onDrawerClick: () -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    val onPathClick: (cid: String) -> Unit
)

/**
 * 纯无状态文件去重内容组件 (RepeatFileContent)
 *
 * 100% 剥离 ViewModel 依赖，仅依赖不可变领域状态 [RepeatUiState] 与 [RepeatFileActions]，
 * 支持 Android Studio @Preview 与独立的 Compose UI 自动化测试。
 *
 * @param uiState 文件查重与列表展示状态
 * @param isGridScreen 是否以网格视图展示
 * @param gridCellMinSize 网格单元格最小尺寸
 * @param actions 界面操作回调集合
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatFileContent(
    uiState: RepeatUiState,
    isGridScreen: Boolean,
    gridCellMinSize: Dp,
    actions: RepeatFileActions,
    modifier: Modifier = Modifier
) {
    val count = uiState.statusData?.fileCount ?: "0"
    val formattedSize = formatBytes(uiState.statusData?.fileSize?.toLongOrNull() ?: 0L)

    // 1. 创建并监听 ListState 与 GridState
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // 2. 校验触底逻辑：当滑动到倒数第 3 项以内且未在加载中时，触发 onLoadMore
    val shouldLoadMore by remember(isGridScreen) {
        derivedStateOf {
            val totalItems: Int
            val lastVisibleItem: Int
            if (isGridScreen) {
                val layoutInfo = gridState.layoutInfo
                totalItems = layoutInfo.totalItemsCount
                lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            } else {
                val layoutInfo = listState.layoutInfo
                totalItems = layoutInfo.totalItemsCount
                lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            }
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
    }

    // 3. 触底事件监听器
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoadingList && !uiState.isListEndReached) {
            actions.onLoadMore()
        }
    }

    val emptyText = when {
        !uiState.hasStartedDedup -> "点击右上角开始查重"
        uiState.totalCount == 0 -> "暂无重复文件"
        else -> null
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        AppTopBarRepeatFile("文件去重", onClick = { action ->
            when (action) {
                MenuItemAction.ONE_KEY_DEDUP -> actions.onOneKeyDedupClick()
                MenuItemAction.START_DEDUP -> actions.onStartScanClick()
                MenuItemAction.DELETE_EMPTY_FILES -> actions.onDeleteEmptyFilesClick()
                TopBarAction.DRAWER_MENU -> actions.onDrawerClick()
                else -> {}
            }
        })

        MiddleEllipsisText(
            text = "共${count}个重复文件，占用空间${formattedSize}",
            modifier = Modifier.padding(8.dp, 4.dp)
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRefresh
        ) {
            if (emptyText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emptyText)
                }
            } else if (isGridScreen) {
                LazyVerticalGridScrollbar(
                    state = gridState,
                    settings = ScrollbarSettings.Default.copy(
                        thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = gridCellMinSize),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(uiState.fileList, key = { it.fileId }) { item ->
                            RepeatFileCardItem(
                                item = item,
                                onPathClick = { actions.onPathClick(item.parentId) }
                            )
                        }
                    }
                }
            } else {
                LazyColumnScrollbar(
                    state = listState,
                    settings = ScrollbarSettings.Default.copy(
                        thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(uiState.fileList, key = { it.fileId }) { item ->
                            RepeatFileCardItem(
                                item = item,
                                onPathClick = { actions.onPathClick(item.parentId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 文件去重主界面 (Stateful Container)
 *
 * 绑定 [RepeatFileViewModel]，处理弹窗显隐、网络请求与业务调用。
 *
 * @param viewModel 去重业务 ViewModel
 * @param isGridScreen 是否为网格展示
 * @param gridCellMinSize 网格单元最小尺寸
 * @param onClick 抽屉菜单开启回调
 * @param jumpClick 路径目录跳转回调
 */
@Composable
fun RepeatFileScreen(
    viewModel: RepeatFileViewModel,
    isGridScreen: Boolean,
    gridCellMinSize: Dp,
    onClick: () -> Unit,
    jumpClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastProvider = LocalToastProvider.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val actions = remember(viewModel, onClick, toastProvider) {
        RepeatFileActions(
            onOneKeyDedupClick = {
                if (uiState.hasStartedDedup) {
                    showDeleteDialog = true
                } else {
                    toastProvider.showToast("请先点击右上角查重，完成后即可去重")
                }
            },
            onStartScanClick = {
                viewModel.triggerForceRefresh()
            },
            onDeleteEmptyFilesClick = {
                viewModel.clearEmptyFile()
            },
            onDrawerClick = onClick,
            onRefresh = {
                viewModel.refreshList()
            },
            onLoadMore = {
                viewModel.loadNextPage()
            },
            onPathClick = { cid ->
                viewModel.fetchCategoryDetail(cid)
            }
        )
    }

    RepeatFileContent(
        uiState = uiState,
        isGridScreen = isGridScreen,
        gridCellMinSize = gridCellMinSize,
        actions = actions
    )

    // 3. 文件详情弹窗与去重策略弹窗绑定
    uiState.categoryDetail?.let { detail ->
        FileDetailDialog(
            detail = detail,
            onDismiss = { viewModel.dismissCategoryDetail() },
            jumpClick = {
                viewModel.dismissCategoryDetail()
                jumpClick.invoke(it)
            }
        )
    }

    if (showDeleteDialog) {
        DeduplicateStrategyDialog(
            onDismiss = { showDeleteDialog = false },
            onSelectStrategy = { field, order ->
                showDeleteDialog = false
                viewModel.executeDelete(field, order)
            }
        )
    }
}


// 文件详情对话框（含路径面包屑点击）
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileDetailDialog(
    detail: CategoryDetailResponse,
    onDismiss: () -> Unit,
    jumpClick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = detail.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(
                    label = "类型",
                    value = if (detail.fileCategory == "1") "文件" else "目录"
                )
                DetailRow(label = "大小", value = detail.size)
                DetailRow(label = "创建时间", value = formatTimestamp(detail.ctime))
                DetailRow(label = "修改时间", value = formatTimestamp(detail.utime))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "位置：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 面包屑层级路径展示：根目录 > test > ...
                    FlowRow(
                        horizontalArrangement = Arrangement.Start,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        detail.paths.forEachIndexed { index, pathItem ->
                            Text(
                                text = pathItem.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    XLog.d("PathClick 点击路径: ${pathItem.fileName}, file_id: ${pathItem.fileId}")
                                    jumpClick.invoke(pathItem.fileId)
                                }
                            )
                            if (index < detail.paths.size - 1) {
                                Text(
                                    text = " > ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// 一键去重保留条件对话框
@Composable
private fun DeduplicateStrategyDialog(
    onDismiss: () -> Unit,
    onSelectStrategy: (field: String, order: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "请选择保留条件", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // 带有加粗“保留条件”的提示文本
                val annotatedText = buildAnnotatedString {
                    append("系统将批量删除")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("保留条件")
                    }
                    append("外的其他重复文件，文件删除不进回收站，请谨慎操作。")
                }

                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 8 个按钮策略列表
                val strategies = remember {
                    listOf(
                        StrategyOption("所在文件夹最长路径", "parents", "desc"),
                        StrategyOption("所在文件夹最短路径", "parents", "asc"),
                        StrategyOption("最后操作时间", "user_utime", "desc"),
                        StrategyOption("最早操作时间", "user_utime", "asc"),
                        StrategyOption("最后上传时间", "user_ptime", "desc"),
                        StrategyOption("最早上传时间", "user_ptime", "asc"),
                        StrategyOption("文件名最长", "file_name", "desc"),
                        StrategyOption("文件名最短", "file_name", "asc")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    strategies.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { item ->
                                OutlinedButton(
                                    onClick = { onSelectStrategy(item.field, item.order) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

private data class StrategyOption(
    val name: String,
    val field: String,
    val order: String
)

// 工具函数：字节转换
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.2f %s",
        bytes / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups]
    )
}

// 工具函数：时间戳格式化
private fun formatTimestamp(timestampStr: String): String {
    val time = timestampStr.toLongOrNull() ?: return timestampStr
    val date = Date(time * 1000)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(date)
}