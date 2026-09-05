package github.zerorooot.nap511.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.dialog.RecyclePasswordDialog
import github.zerorooot.nap511.screenitem.RecycleCellItem
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

/**
 * 回收站页面的纯 UI 状态封装
 */
data class RecycleUiState(
    val recycleFileList: List<RecycleBean> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOpenRecyclePasswordDialog: Boolean = false
)

/**
 * 有状态组件：连接 RecycleViewModel 与无状态 UI Component
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecycleScreen(
    recycleViewModel: RecycleViewModel,
    isExpandedScreen: Boolean,
    onClick: () -> Unit
) {
    LaunchedEffect(Unit) {
        recycleViewModel.getRecycleFileList()
    }

    val scope = rememberCoroutineScope()
    var deleteIndex by remember {
        mutableIntStateOf(-1)
    }

    val refreshing by recycleViewModel.isRefreshing.collectAsStateWithLifecycle()

    val uiState = RecycleUiState(
        recycleFileList = recycleViewModel.recycleFileList,
        isRefreshing = refreshing,
        isOpenRecyclePasswordDialog = recycleViewModel.isOpenRecyclePasswordDialog
    )

    RecycleContent(
        uiState = uiState,
        isExpandedScreen = isExpandedScreen,
        onRefresh = { recycleViewModel.refresh() },
        onRevert = { index -> recycleViewModel.revert(index) },
        onDelete = { index ->
            deleteIndex = index
            recycleViewModel.delete(index)
        },
        onDeleteAll = { recycleViewModel.deleteAll() },
        onPasswordEntered = { password ->
            if (!password.isNullOrEmpty()) {
                scope.launch {
                    DataStoreUtil.putDataSuspend(ConfigKeyUtil.PASSWORD, password)
                }

                if (deleteIndex == -1) {
                    recycleViewModel.deleteAll()
                } else {
                    recycleViewModel.delete(
                        deleteIndex,
                        password.subSequence(0, 6).toString(),
                        true
                    )
                    deleteIndex = -1
                }
            }
            recycleViewModel.closeDialog()
        },
        onClick = onClick
    )
}

/**
 * 无状态（Stateless）UI 组件：不依赖任何 ViewModel
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecycleContent(
    uiState: RecycleUiState,
    isExpandedScreen: Boolean,
    onRefresh: () -> Unit,
    onRevert: (index: Int) -> Unit,
    onDelete: (index: Int) -> Unit,
    onDeleteAll: () -> Unit,
    onPasswordEntered: (String?) -> Unit,
    onClick: () -> Unit
) {
    val menuOnClick = { name: String, index: Int ->
        when (name) {
            "还原" -> onRevert(index)
            "删除" -> onDelete(index)
        }
    }

    val appBarOnClick = { name: String ->
        when (name) {
            "清空所有文件" -> onDeleteAll()
            "ModalNavigationDrawerMenu" -> onClick()
        }
    }

    RecyclePasswordDialog(
        isOpen = uiState.isOpenRecyclePasswordDialog,
        enter = onPasswordEntered
    )

    Column {
        AppTopBarRecycle(ConfigKeyUtil.RECYCLE_BIN, appBarOnClick)
        MiddleEllipsisText(
            text = "当前文件数：${uiState.recycleFileList.size}",
            modifier = Modifier.padding(8.dp, 4.dp)
        )
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh
        ) {
            if (uiState.recycleFileList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无文件")
                }
            } else if (isExpandedScreen) {
                val gridState = rememberLazyGridState()
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
                            items = uiState.recycleFileList,
                            key = { _, item -> item.hashCode() }
                        ) { index, item ->
                            RecycleCellItem(
                                recycleBean = item,
                                modifier = Modifier.animateItem(),
                                index = index,
                                menuOnClick = menuOnClick
                            )
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumnScrollbar(
                    state = listState,
                    settings = ScrollbarSettings.Default.copy(
                        thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(
                            items = uiState.recycleFileList,
                            key = { _, item -> item.hashCode() }
                        ) { index, item ->
                            RecycleCellItem(
                                recycleBean = item,
                                modifier = Modifier.animateItem(),
                                index = index,
                                menuOnClick = menuOnClick
                            )
                        }
                    }
                }
            }
        }
    }
}
