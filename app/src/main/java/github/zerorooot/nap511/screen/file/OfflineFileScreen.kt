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
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
import github.zerorooot.nap511.util.rememberListDetailDirective
import github.zerorooot.nap511.viewmodel.OfflineFileUiState
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import java.util.StringJoiner
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed


/**
 * 离线下载页面的公共交互事件集合
 */
@Immutable
data class OfflineFileActions(
    val getListLocation: (Int) -> LocationBean,
    val onSaveScrollPosition: (page: Int, state: Any) -> Unit,
    val onRefresh: () -> Unit,
    val onClearFinish: () -> Unit,
    val onClearError: () -> Unit,
    val onDeleteTask: (OfflineTask) -> Unit,
    val onOpenTaskDialog: (OfflineTask) -> Unit,
    val onCloseTaskDialog: () -> Unit,
    val onLoadMoreCompleted: () -> Unit,
    val onLoadMoreDownloading: () -> Unit,
    val onLoadMoreFailed: () -> Unit,
    val getFiles: (String) -> Unit,
    val onClick: () -> Unit,
    val onNavigateToNewTask: () -> Unit = {},
    val selectedPage: MutableIntState,
)


@Composable
fun OfflineFileScreen(
    offlineFileViewModel: OfflineFileViewModel,
    isExpandedScreen: Boolean,
    isGridScreen: Boolean,
    gridCellMinSize: Dp,
    getFiles: (String) -> Unit,
    onClick: () -> Unit,
    onNavigateToNewTask: () -> Unit = {}
) {
    val uiState by offlineFileViewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(
        offlineFileViewModel,
        getFiles,
        onClick,
        onNavigateToNewTask
    ) {
        OfflineFileActions(
            getListLocation = offlineFileViewModel::getListLocation,
            onSaveScrollPosition = offlineFileViewModel::setListLocation,
            onRefresh = offlineFileViewModel::refresh,
            onClearFinish = offlineFileViewModel::clearFinish,
            onClearError = offlineFileViewModel::clearError,
            onDeleteTask = offlineFileViewModel::delete,
            onOpenTaskDialog = offlineFileViewModel::openOfflineDialog,
            onCloseTaskDialog = offlineFileViewModel::closeOfflineDialog,
            onLoadMoreCompleted = offlineFileViewModel::loadMoreCompletedTasks,
            onLoadMoreDownloading = offlineFileViewModel::loadMoreDownloadingTasks,
            onLoadMoreFailed = offlineFileViewModel::loadMoreFailedTasks,
            getFiles = getFiles,
            onClick = onClick,
            onNavigateToNewTask = onNavigateToNewTask,
            selectedPage = offlineFileViewModel.selectedPage
        )
    }
    if (isExpandedScreen) {
        AdaptiveOfflineScreen(
            uiState = uiState,
            selectedTaskState = offlineFileViewModel.selectedTask,
            isGridScreen = isGridScreen,
            gridCellMinSize = gridCellMinSize,
            actions = actions
        )
    } else {
        OfflineFileContent(
            uiState = uiState,
            gridCellMinSize = gridCellMinSize,
            isGridScreen = isGridScreen,
            actions = actions,
        )
    }

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
    uiState: OfflineFileUiState,
    selectedTaskState: MutableState<OfflineTask?>,
    isGridScreen: Boolean,
    gridCellMinSize: Dp,
    actions: OfflineFileActions,
) {
    // 1. 依据屏幕宽度规格计算分栏指令与双栏激活状态
    val directive = rememberListDetailDirective()

    // 2. 当前选中的离线任务对象与 Nav3 返回栈
    var selectedTask by selectedTaskState
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(OfflineNavKey.TaskList)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    // 5. 详情面板公共渲染方法（统一复用，避免在 placeholder 与 entry 间冗余重复）
    val renderTaskDetail: @Composable (task: OfflineTask, onDeleted: () -> Unit) -> Unit =
        { targetTask, onDeleted ->
            TaskDetailPane(
                task = targetTask,
                onDeleteTask = {
                    actions.onDeleteTask(it)
                    onDeleted()
                },
                onOpenFile = { targetCid ->
                    actions.getFiles(targetCid)
                }
            )
        }

    // 6. Nav3 驱动的自适应展示容器
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider = entryProvider {
            // 左侧任务列表面板
            entry<OfflineNavKey.TaskList>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        if (selectedTask != null) {
                            renderTaskDetail(selectedTask!!) { selectedTask = null }
                        } else {
                            OfflineTaskEmptyPlaceholder(actions.onNavigateToNewTask)
                        }
                    }
                )
            ) {
                OfflineFileContent(
                    uiState = uiState,
                    gridCellMinSize = gridCellMinSize,
                    isGridScreen = isGridScreen,
                    actions = actions
                )
            }

            // 右侧任务详情面板
            entry<OfflineNavKey.TaskDetail>(
                metadata = ListDetailSceneStrategy.detailPane()
            ) { detailKey ->
                renderTaskDetail(detailKey.offlineTask) {
                    backStack.removeLastOrNull()
                }
            }
        }
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
    actions: OfflineFileActions,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tab 页标题及数量提示
    val tabs = listOf(
        "完成记录 (${uiState.offlineInfo.finishedCount})",
        "正在下载 (${uiState.offlineInfo.downloadingCount})",
        "下载失败 (${uiState.offlineInfo.failedCount})",
    )
    val pagerState = rememberPagerState(
        initialPage = actions.selectedPage.intValue,
        pageCount = { tabs.size }
    )

    // 监听滑动或点击，实时同步当前 settledPage 到 ViewModel 中
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            actions.selectedPage.intValue = page
        }
    }

    OfflineFileInfoDialog(
        isOpen = uiState.isOpenOfflineDialog,
        task = uiState.selectedOfflineTask,
        onDismissRequest = actions.onCloseTaskDialog
    )


    // 菜单操作逻辑：直接接收选中的 OfflineTask 对象
    val menuOnClick = { action: MenuItemAction, item: OfflineTask ->
        when (action) {
            MenuItemAction.COPY_LINK -> copyDownloadUrl(context, item.url, 1, item.name)
            MenuItemAction.DELETE_FILE -> actions.onDeleteTask(item)
            MenuItemAction.FILE_INFO -> actions.onOpenTaskDialog(item)
            else -> {}
        }
    }

    val appBarOnClick = { action: AppBarAction ->
        when (action) {
            MenuItemAction.REFRESH_FILES -> actions.onRefresh()
            MenuItemAction.CLEAR_COMPLETED -> actions.onClearFinish()
            MenuItemAction.CLEAR_FAILED -> actions.onClearError()
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

            TopBarAction.DRAWER_MENU -> actions.onClick()
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
            val listLocation = actions.getListLocation(page)
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
                    actions.onSaveScrollPosition(
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
                            0 -> actions.onLoadMoreCompleted()
                            1 -> actions.onLoadMoreDownloading()
                            2 -> actions.onLoadMoreFailed()
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
                onRefresh = actions.onRefresh,
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
                                        actions.getFiles(item.fileId.ifEmpty { item.wpPathId })
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
                                        actions.getFiles(item.fileId.ifEmpty { item.wpPathId })
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
