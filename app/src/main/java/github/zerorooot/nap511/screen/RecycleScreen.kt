package github.zerorooot.nap511.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.screenitem.RecycleCellItem
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.RecycleViewModel

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun RecycleScreen(recycleViewModel: RecycleViewModel) {
    recycleViewModel.getRecycleFileList()
    var deleteIndex by remember {
        mutableStateOf(-1)
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
            "ModalNavigationDrawerMenu" -> App.instance.openDrawerState()
        }
    }

    RecyclePasswordDialog(recycleViewModel) {
        if (it != "") {
            if (deleteIndex == -1) {
                recycleViewModel.deleteAll()
            } else {
                recycleViewModel.delete(deleteIndex, it.subSequence(0, 6).toString(), true)
                deleteIndex = -1
            }
        }
        recycleViewModel.closeDialog()
    }

    val pullRefreshState = rememberPullRefreshState(refreshing, { recycleViewModel.refresh() })

    Column {
        AppTopBarRecycle("回收站", appBarOnClick)
        MiddleEllipsisText(
            text = "当前文件数：${recycleFileList.size}", modifier = Modifier.padding(8.dp, 4.dp)
        )
        Box(Modifier.pullRefresh(pullRefreshState)) {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(items = recycleFileList, key = { _, item ->
                    item.hashCode()
                }) { index, item ->
                    RecycleCellItem(
                        recycleBean = item,
                        Modifier.animateItemPlacement(),
                        index = index,
                        menuOnClick
                    )
                }
            }

            PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
        }

    }

}