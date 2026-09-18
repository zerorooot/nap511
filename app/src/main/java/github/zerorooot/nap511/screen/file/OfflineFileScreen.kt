package github.zerorooot.nap511.screen.file

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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.dialog.OfflineFileInfoDialog
import github.zerorooot.nap511.screen.components.AppBarAction
import github.zerorooot.nap511.screen.components.AppTopBarOfflineFile
import github.zerorooot.nap511.screen.components.MenuItemAction
import github.zerorooot.nap511.screen.components.TopBarAction
import github.zerorooot.nap511.screenitem.OfflineCellItem
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.copy
import github.zerorooot.nap511.viewmodel.OfflineFileUiState
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import java.util.StringJoiner
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed


/**
 * OfflineFileContent 的状态管理包装层，统一托管 ViewModel 的动作分发。
 */
@Composable
fun OfflineFileContainer(
    offlineFileViewModel: OfflineFileViewModel,
    uiState: OfflineFileUiState,
    gridCellMinSize: Dp,
    isGridScreen: Boolean,
    itemOnClick: (OfflineTask) -> Unit,
    onClick: () -> Unit,
    onOpenTaskDialog: (OfflineTask) -> Unit = offlineFileViewModel::openOfflineDialog,
) {
    OfflineFileContent(
        uiState = uiState,
        gridCellMinSize = gridCellMinSize,
        isGridScreen = isGridScreen,
        getListLocation = offlineFileViewModel::getListLocation,
        onSaveScrollPosition = offlineFileViewModel::setListLocation,
        onRefresh = offlineFileViewModel::refresh,
        onClearFinish = offlineFileViewModel::clearFinish,
        onClearError = offlineFileViewModel::clearError,
        onDeleteTask = offlineFileViewModel::delete,
        onOpenTaskDialog = onOpenTaskDialog,
        onCloseTaskDialog = offlineFileViewModel::closeOfflineDialog,
        onLoadMoreCompleted = offlineFileViewModel::loadMoreCompletedTasks,
        onLoadMoreDownloading = offlineFileViewModel::loadMoreDownloadingTasks,
        onLoadMoreFailed = offlineFileViewModel::loadMoreFailedTasks,
        itemOnClick = itemOnClick,
        onClick = onClick,
    )
}

@Composable
fun OfflineFileScreen(
    offlineFileViewModel: OfflineFileViewModel,
    isGridScreen: Boolean,
    gridCellMinSize: Dp,
    itemOnClick: (OfflineTask) -> Unit,
    onClick: () -> Unit,
) {
    val uiState by offlineFileViewModel.uiState.collectAsStateWithLifecycle()

    OfflineFileContainer(
        offlineFileViewModel = offlineFileViewModel,
        uiState = uiState,
        gridCellMinSize = gridCellMinSize,
        isGridScreen = isGridScreen,
        itemOnClick = itemOnClick,
        onClick = onClick
    )
}

/**
 * 无状态（Stateless）UI 组件：不依赖任何 ViewModel
 */
@Composable
fun OfflineFileContent(
    uiState: OfflineFileUiState,
    gridCellMinSize: Dp,
    isGridScreen: Boolean,
    getListLocation: (Int) -> LocationBean,
    onSaveScrollPosition: (page: Int, state: Any) -> Unit,
    onRefresh: () -> Unit,
    onClearFinish: () -> Unit,
    onClearError: () -> Unit,
    onDeleteTask: (OfflineTask) -> Unit,
    onOpenTaskDialog: (OfflineTask) -> Unit,
    onCloseTaskDialog: () -> Unit,
    onLoadMoreCompleted: () -> Unit,
    onLoadMoreDownloading: () -> Unit,
    onLoadMoreFailed: () -> Unit,
    itemOnClick: (OfflineTask) -> Unit,
    onClick: () -> Unit,
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


    // 菜单操作逻辑：直接接收选中的 OfflineTask 对象
    val menuOnClick = { action: MenuItemAction, item: OfflineTask ->
        when (action) {
            MenuItemAction.COPY_LINK -> copyDownloadUrl(context, item.url, 1, item.name)
            MenuItemAction.DELETE_FILE -> onDeleteTask(item)
            MenuItemAction.FILE_INFO -> onOpenTaskDialog(item)
            else -> {}
        }
    }

    val appBarOnClick = { action: AppBarAction ->
        when (action) {
            MenuItemAction.REFRESH_FILES -> onRefresh()
            MenuItemAction.CLEAR_COMPLETED -> onClearFinish()
            MenuItemAction.CLEAR_FAILED -> onClearError()
            MenuItemAction.COPY_PAGE_LINK -> {
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

            TopBarAction.DRAWER_MENU -> onClick()
            else -> {}
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
            val listLocation = getListLocation(page)
            val listState = key(page) {
                rememberLazyListState(
                    initialFirstVisibleItemIndex = listLocation.firstVisibleItemIndex,
                    initialFirstVisibleItemScrollOffset = listLocation.firstVisibleItemScrollOffset
                )
            }
            val gridState = key(page) {
                rememberLazyGridState(
                    initialFirstVisibleItemIndex = listLocation.firstVisibleItemIndex,
                    initialFirstVisibleItemScrollOffset = listLocation.firstVisibleItemScrollOffset
                )
            }

            DisposableEffect(page, listState, gridState, isGridScreen) {
                onDispose {
                    onSaveScrollPosition(
                        page,
                        if (isGridScreen) gridState else listState
                    )
                }
            }

            val shouldLoadMore = remember(isGridScreen) {
                derivedStateOf {
                    val totalItems: Int
                    val lastVisibleIndex: Int
                    if (isGridScreen) {
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
                                        itemOnClick(item)
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
                                        itemOnClick(item)
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

fun copyDownloadUrl(context: Context, text: String, count: Int, name: String? = null) {
    text.copy(context)
    val toast = "${name?.plus(" ") ?: "$count 个"}下载链接复制成功"
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}
