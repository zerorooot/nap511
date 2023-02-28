package github.zerorooot.nap511.screen

import github.zerorooot.nap511.R
import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineFileScreen(
    offlineFileViewModel: OfflineFileViewModel,
    itemOnClick: (Int) -> Unit,
    menuOnClick: (String, Int) -> Unit,
    appBarOnClick: (String) -> Unit
) {
    offlineFileViewModel.getOfflineFileList()
    val offlineInfo by offlineFileViewModel.offlineInfo.collectAsState()
    val refreshing by offlineFileViewModel.isRefreshing.collectAsState()
    val offlineList by offlineFileViewModel.offlineFile.collectAsState()

    Column {
        AppTopBarOfflineFile(stringResource(R.string.app_name), appBarOnClick)

        MiddleEllipsisText(
            text = "当前下载量：${offlineInfo.count}，配额：${offlineInfo.quota}/${offlineInfo.total}",
            modifier = Modifier.padding(8.dp, 4.dp)
        )
        Scaffold() {
            SwipeRefresh(state = rememberSwipeRefreshState(refreshing), onRefresh = {
                offlineFileViewModel.refresh()
            }) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items = offlineList, key = { _, item ->
                        item.hashCode()
                    }) { index, item ->
                        OfflineCellItem(
                            offlineTask = item,
                            index = index,
                            itemOnClick = itemOnClick,
                            menuOnClick = menuOnClick
                        )
                    }
                }
            }

        }
    }

}