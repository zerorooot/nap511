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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.screenitem.RecycleCellItem
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

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

    var deleteIndex by remember {
        mutableIntStateOf(-1)
    }

    val refreshing by recycleViewModel.isRefreshing.collectAsState()
    val recycleFileList = recycleViewModel.recycleFileList


    val menuOnClick = { name: String, index: Int ->
        when (name) {
            "还原" -> recycleViewModel.revert(index)
            "删除" -> {
                deleteIndex = index
                recycleViewModel.delete(index)
            }
        }
    }

    val appBarOnClick = { name: String ->
        when (name) {
            "清空所有文件" -> recycleViewModel.deleteAll()
            "ModalNavigationDrawerMenu" -> {
                onClick.invoke()
            }
        }
    }

    RecyclePasswordDialog(recycleViewModel) {
        if (it != null && it != "") {
            DataStoreUtil.putData(ConfigKeyUtil.PASSWORD, it)

            if (deleteIndex == -1) {
                recycleViewModel.deleteAll()
            } else {
                recycleViewModel.delete(deleteIndex, it.subSequence(0, 6).toString(), true)
                deleteIndex = -1
            }
        }
        recycleViewModel.closeDialog()
    }

    Column {
        AppTopBarRecycle(ConfigKeyUtil.RECYCLE_BIN, appBarOnClick)
        MiddleEllipsisText(
            text = "当前文件数：${recycleFileList.size}", modifier = Modifier.padding(8.dp, 4.dp)
        )
        PullToRefreshBox(
            isRefreshing = refreshing, onRefresh = { recycleViewModel.refresh() }) {
            if (recycleFileList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()), // 添加垂直滚动支持以分发下拉手势,
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
                        gridItemsIndexed(items = recycleFileList, key = { _, item ->
                            item.hashCode()
                        }) { index, item ->
                            RecycleCellItem(
                                recycleBean = item,
                                Modifier.animateItem(),
                                index = index,
                                menuOnClick
                            )
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumnScrollbar(
                    state = listState, settings = ScrollbarSettings.Default.copy(
                        thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(items = recycleFileList, key = { _, item ->
                            item.hashCode()
                        }) { index, item ->
                            RecycleCellItem(
                                recycleBean = item,
                                Modifier.animateItem(),
                                index = index,
                                menuOnClick
                            )
                        }
                    }
                }
            }
        }

    }

}