package github.zerorooot.nap511.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.zerorooot.nap511.bean.OfflineListCount
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.dialog.OfflineFileInfoDialog
import github.zerorooot.nap511.screenitem.OfflineCellItem
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import java.util.StringJoiner
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

/**
 * 离线文件页面的纯 UI 状态封装
 */
data class OfflineFileUiState(
    val offlineInfo: OfflineListCount = OfflineListCount(),
    val isRefreshing: Boolean = false,
    val downloadingList: List<OfflineTask> = emptyList(),
    val failedList: List<OfflineTask> = emptyList(),
    val completedList: List<OfflineTask> = emptyList(),
    val isOpenOfflineDialog: Boolean = false,
    val selectedOfflineTask: OfflineTask? = null,
)

@Composable
fun OfflineFileScreen(
    offlineFileViewModel: OfflineFileViewModel,
    isExpandedScreen: Boolean,
    getFiles: (String) -> Unit,
    onClick: (String) -> Unit,
) {
    val offlineInfo by offlineFileViewModel.offlineInfo.collectAsStateWithLifecycle()
    val refreshing by offlineFileViewModel.isRefreshing.collectAsStateWithLifecycle()
    val downloadingList by offlineFileViewModel.downloadingList.collectAsStateWithLifecycle()
    val failedList by offlineFileViewModel.failedList.collectAsStateWithLifecycle()
    val completedList by offlineFileViewModel.completedList.collectAsStateWithLifecycle()

    val uiState = OfflineFileUiState(
        offlineInfo = offlineInfo,
        isRefreshing = refreshing,
        downloadingList = downloadingList,
        failedList = failedList,
        completedList = completedList,
        isOpenOfflineDialog = offlineFileViewModel.isOpenOfflineDialog,
        selectedOfflineTask = if (offlineFileViewModel.isOpenOfflineDialog) offlineFileViewModel.offlineTask else null
    )

    OfflineFileContent(
        uiState = uiState,
        isExpandedScreen = isExpandedScreen,
        onRefresh = { offlineFileViewModel.refresh() },
        onClearFinish = { offlineFileViewModel.clearFinish() },
        onClearError = { offlineFileViewModel.clearError() },
        onDeleteTask = { offlineFileViewModel.delete(it) },
        onOpenTaskDialog = { offlineFileViewModel.openOfflineDialog(it) },
        onCloseTaskDialog = { offlineFileViewModel.closeOfflineDialog() },
        onLoadMoreCompleted = { offlineFileViewModel.loadMoreCompletedTasks() },
        onLoadMoreDownloading = { offlineFileViewModel.loadMoreDownloadingTasks() },
        onLoadMoreFailed = { offlineFileViewModel.loadMoreFailedTasks() },
        getFiles = getFiles,
        onClick = onClick
    )
}

/**
 * 无状态（Stateless）UI 组件：不依赖任何 ViewModel
 */
@Composable
fun OfflineFileContent(
    uiState: OfflineFileUiState,
    isExpandedScreen: Boolean,
    onRefresh: () -> Unit,
    onClearFinish: () -> Unit,
    onClearError: () -> Unit,
    onDeleteTask: (OfflineTask) -> Unit,
    onOpenTaskDialog: (OfflineTask) -> Unit,
    onCloseTaskDialog: () -> Unit,
    onLoadMoreCompleted: () -> Unit,
    onLoadMoreDownloading: () -> Unit,
    onLoadMoreFailed: () -> Unit,
    getFiles: (String) -> Unit,
    onClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tab 页标题及数量提示
    val tabs = listOf(
        "完成记录 (${uiState.offlineInfo.finishedCount})",
        "正在下载 (${uiState.offlineInfo.downloadingCount})",
        "下载失败 (${uiState.offlineInfo.failedCount})",
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    OfflineFileInfoDialog(
        isOpen = uiState.isOpenOfflineDialog,
        task = uiState.selectedOfflineTask,
        onDismissRequest = onCloseTaskDialog
    )

    // 点击列表项逻辑：直接接收点击的 OfflineTask 对象
    val itemOnClick = { offlineTask: OfflineTask ->
        val cid = if (offlineTask.fileId == "") offlineTask.wpPathId else offlineTask.fileId
        onClick.invoke("MyFile")
        getFiles.invoke(cid)
    }

    // 菜单操作逻辑：直接接收选中的 OfflineTask 对象
    val menuOnClick = { name: String, item: OfflineTask ->
        when (name) {
            "复制链接" -> copyDownloadUrl(context, item.url, 1)
            "删除文件" -> onDeleteTask(item)
            "文件信息" -> onOpenTaskDialog(item)
        }
    }

    val appBarOnClick = { name: String ->
        when (name) {
            "刷新文件" -> onRefresh()
            "清空已完成" -> onClearFinish()
            "清空已失败" -> onClearError()
            "复制本页链接" -> {
                val stringJoiner = StringJoiner("\n")
                val allTasks = when (pagerState.currentPage) {
                    0 -> uiState.completedList
                    1 -> uiState.downloadingList
                    2 -> uiState.failedList
                    else -> uiState.completedList
                }
                allTasks.forEach { i ->
                    stringJoiner.add(
                        i.url.replace(Regex("&dn=.*"), "").trim()
                    )
                }
                copyDownloadUrl(context, stringJoiner.toString(), allTasks.size)
            }

            "ModalNavigationDrawerMenu" -> onClick.invoke("ModalNavigationDrawerMenu")
        }
    }

    Column {
        AppTopBarOfflineFile(ConfigKeyUtil.OFFLINE_LIST, appBarOnClick)

        // PrimaryTabRow 顶部切换栏
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = title) }
                )
            }
        }

        // HorizontalPager 滑动容器
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val listState = key(page) { rememberLazyListState() }
            val gridState = key(page) { rememberLazyGridState() }

            val shouldLoadMore = remember(isExpandedScreen) {
                derivedStateOf {
                    val totalItems: Int
                    val lastVisibleIndex: Int
                    if (isExpandedScreen) {
                        val layoutInfo = gridState.layoutInfo
                        totalItems = layoutInfo.totalItemsCount
                        lastVisibleIndex =
                            (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
                    } else {
                        val layoutInfo = listState.layoutInfo
                        totalItems = layoutInfo.totalItemsCount
                        lastVisibleIndex =
                            (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
                    }
                    totalItems > 0 && lastVisibleIndex >= totalItems - 5
                }
            }

            // 监听触底状态变化并触发加载
            LaunchedEffect(shouldLoadMore) {
                snapshotFlow { shouldLoadMore.value }
                    .collect { isNearBottom ->
                        if (!isNearBottom) {
                            return@collect
                        }
                        when (page) {
                            0 -> onLoadMoreCompleted()
                            1 -> onLoadMoreDownloading()
                            2 -> onLoadMoreFailed()
                        }
                    }
            }

            val currentSubList = when (page) {
                0 -> uiState.completedList
                1 -> uiState.downloadingList
                2 -> uiState.failedList
                else -> emptyList()
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                if (currentSubList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无记录")
                    }
                } else if (isExpandedScreen) {
                    LazyVerticalGridScrollbar(
                        state = gridState,
                        settings = ScrollbarSettings.Default.copy(
                            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 340.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItemsIndexed(
                                items = currentSubList,
                                key = { _, item -> item.infoHash }
                            ) { index, item ->
                                OfflineCellItem(
                                    offlineTask = item,
                                    index = index,
                                    itemOnClick = { _ ->
                                        if (page == 0) {
                                            itemOnClick(item)
                                        }
                                    },
                                    menuOnClick = { menuName, _ -> menuOnClick(menuName, item) }
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
                            modifier = Modifier.fillMaxSize(),
                            state = listState
                        ) {
                            itemsIndexed(
                                items = currentSubList,
                                key = { _, item -> item.infoHash }
                            ) { index, item ->
                                OfflineCellItem(
                                    offlineTask = item,
                                    index = index,
                                    itemOnClick = { _ ->
                                        // 仅当在“完成记录” Tab (page == 0) 时允许点击触发跳转
                                        if (page == 0) {
                                            itemOnClick(item)
                                        }
                                    },
                                    menuOnClick = { menuName, _ -> menuOnClick(menuName, item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun copyDownloadUrl(context: Context, text: String, count: Int) {
    val clipboard = getSystemService(context, ClipboardManager::class.java)
    val clip = ClipData.newPlainText("label", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "$count 个下载链接复制成功~", Toast.LENGTH_SHORT).show()
}
