package github.zerorooot.nap511.screen

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.CategoryDetailResponse
import github.zerorooot.nap511.screenitem.RepeatFileCardItem
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.RepeatFileViewModel
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatFileScreen(
    viewModel: RepeatFileViewModel,
    onClick: () -> Unit,
    jumpClick: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
    val uiState by viewModel.uiState.collectAsState()
    val categoryDetail by viewModel.categoryDetail.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val count = uiState.statusData?.fileCount ?: "0"
    val formattedSize = formatBytes(uiState.statusData?.fileSize?.toLongOrNull() ?: 0L)

    // 1. 创建并监听 LazyColumn 的 ListState
    val listState = rememberLazyListState()

    val onDeleteStrategySelect = { field: String, order: String ->
        viewModel.executeDelete(
            field,
            order
        )
    }
    val onPathClick = { cid: String ->
        viewModel.fetchCategoryDetail(cid)
    }

    // 2. 校验触底逻辑：当滑动到倒数第 3 项以内且未在加载中时，触发 loadNextPage
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) false
            else {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItem >= totalItems - 3
            }
        }
    }

    // 3. 触底事件监听器
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoadingList && !uiState.isListEndReached) {
            viewModel.loadNextPage()
        }
    }
    val appBarOnClick = { name: String ->
        when (name) {
            "一键去重" -> {
                showDeleteDialog = true
            }

            "开始查重" -> {
                viewModel.triggerForceRefresh()
            }

            "刷新页面" -> {
                App.instance.toast("页面刷新中～")
                viewModel.refreshList(true)
            }

            "ModalNavigationDrawerMenu" -> {
                onClick.invoke()
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AppTopBarRepeatFile("文件去重", appBarOnClick)
        if (uiState.totalCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无重复文件，点击右上角刷新页面")
            }
        } else {
            MiddleEllipsisText(
                text = "共${count}个重复文件，占用空间${formattedSize}",
                modifier = Modifier.padding(8.dp, 4.dp)
            )
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshList() }) {
                LazyColumnScrollbar(
                    state = listState, settings = ScrollbarSettings.Default.copy(
                        thumbUnselectedColor = Purple80
                    )
                ) {
                    LazyColumn(
                        state = listState, modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.fileList, key = { it.fileId }) { item ->
                            RepeatFileCardItem(
                                item = item,
                                onPathClick = { onPathClick(item.parentId) }
                            )
                        }
                    }
                }
            }

        }
    }

    // 3. 文件详情弹窗与去重策略弹窗绑定
    categoryDetail?.let { detail ->
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
                onDeleteStrategySelect(field, order)
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