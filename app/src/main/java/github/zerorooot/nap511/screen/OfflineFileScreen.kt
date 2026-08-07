package github.zerorooot.nap511.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.screenitem.OfflineCellItem
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import java.util.StringJoiner

@Composable
fun OfflineFileScreen(
    offlineFileViewModel: OfflineFileViewModel,
    fileViewModel: FileViewModel,
    onClick: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        offlineFileViewModel.getOfflineFileList()
    }

    val offlineInfo by offlineFileViewModel.offlineInfo.collectAsState()
    val refreshing by offlineFileViewModel.isRefreshing.collectAsState()

    // 1. 订阅ViewModel拆分出的 3 个独立列表
    val downloadingList by offlineFileViewModel.downloadingList.collectAsState()
    val failedList by offlineFileViewModel.failedList.collectAsState()
    val completedList by offlineFileViewModel.completedList.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Tab 页标题及数量提示
    val tabs = listOf(
        "完成记录 (${offlineInfo.finishedCount})",
        "正在下载 (${offlineInfo.downloadingCount})",
        "下载失败 (${offlineInfo.failedCount})",
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    OfflineFileInfoDialog(offlineFileViewModel, {
        offlineFileViewModel.closeOfflineDialog()
    }) {
        val clip = ClipData.newPlainText("label", it)
        clipboardManager.nativeClipboard.setPrimaryClip(clip)
        XLog.d("OfflineFileInfoDialog copy $it")
        App.instance.toast("复制磁力链接成功!")
    }

    // 点击列表项逻辑：直接接收点击的 OfflineTask 对象
    val itemOnClick = { offlineTask: OfflineTask ->
        val cid = if (offlineTask.fileId == "") offlineTask.wpPathId else offlineTask.fileId
        onClick.invoke("MyFile")
        fileViewModel.getFiles(cid)
    }

    // 菜单操作逻辑：直接接收选中的 OfflineTask 对象
    val menuOnClick = { name: String, item: OfflineTask ->
        when (name) {
            "复制下载链接" -> copyDownloadUrl(context, item.url, 1)
            "删除文件" -> offlineFileViewModel.delete(item)
            "文件信息" -> offlineFileViewModel.openOfflineDialog(item)
        }
    }

    val appBarOnClick = { name: String ->
        when (name) {
            "刷新文件" -> offlineFileViewModel.refresh()
            "清空已完成" -> offlineFileViewModel.clearFinish()
            "清空已失败" -> offlineFileViewModel.clearError()
            "复制本页链接" -> {
                val stringJoiner = StringJoiner("\n")
                val allTasks = when (pagerState.currentPage) {
                    0 -> {
                        completedList
                    }

                    1 -> {
                        downloadingList
                    }

                    2 -> {
                        failedList
                    }

                    else -> {
                        completedList
                    }
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

//        MiddleEllipsisText(
//            text = "当前下载量：${offlineInfo.totalCount}，配额：${offlineInfo.quota}/${offlineInfo.total}",
//            modifier = Modifier.padding(8.dp, 4.dp)
//        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            MiddleEllipsisText(
                text = "当前下载量：${offlineInfo.totalCount}  |  配额：${offlineInfo.quota}/${offlineInfo.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

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
            val shouldLoadMore = remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleItemIndex =
                        (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1

                    totalItems > 0 && lastVisibleItemIndex >= totalItems - 5
                }
            }
// 2. 监听触底状态变化并触发加载
            LaunchedEffect(shouldLoadMore) {
                snapshotFlow { shouldLoadMore.value }
                    .collect { isNearBottom ->
                        if (!isNearBottom) {
                            return@collect
                        }
                        when (page) {
                            0 -> {
                                offlineFileViewModel.loadMoreCompletedTasks()
                            }

                            1 -> {
                                offlineFileViewModel.loadMoreDownloadingTasks()
                            }

                            2 -> {
                                offlineFileViewModel.loadMoreFailedTasks()
                            }
                        }

                    }
            }

            val currentSubList = when (page) {
                0 -> completedList
                1 -> downloadingList
                2 -> failedList
                else -> emptyList()
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { offlineFileViewModel.refresh() },
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
                } else {
                    LazyColumnScrollbar(
                        state = listState,
                        settings = ScrollbarSettings.Default.copy(
                            thumbUnselectedColor = Purple80
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
                                        //仅当在“完成记录” Tab (page == 0) 时允许点击触发跳转
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