package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Scaffold
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import github.zerorooot.nap511.R
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import java.util.*

@OptIn(ExperimentalMaterialApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UnusedMaterialScaffoldPaddingParameter")
@Composable
fun OfflineFileScreen(
    offlineFileViewModel: OfflineFileViewModel,
    fileViewModel: FileViewModel,
) {
    offlineFileViewModel.getOfflineFileList()
    val offlineInfo by offlineFileViewModel.offlineInfo.collectAsState()
    val refreshing by offlineFileViewModel.isRefreshing.collectAsState()
    val offlineList by offlineFileViewModel.offlineFile.collectAsState()
    val context = LocalContext.current

    OfflineFileInfoDialog(offlineFileViewModel) {
        offlineFileViewModel.closeOfflineDialog()
    }

    val itemOnClick = { i: Int ->
        val offlineTask = offlineList[i]
        val cid = if (offlineTask.fileId == "") offlineTask.wpPathId else offlineTask.fileId
        fileViewModel.selectedItem = "????????????"
        fileViewModel.getFiles(cid)
    }
    val menuOnClick = { name: String, index: Int ->
        when (name) {
            "??????????????????" -> copyDownloadUrl(context, offlineList[index].url)
            "????????????" -> offlineFileViewModel.delete(offlineList[index])
            "????????????" -> offlineFileViewModel.openOfflineDialog(index)
        }
    }
    val appBarOnClick = { name: String ->
        when (name) {
            "????????????" -> offlineFileViewModel.refresh()
            "???????????????" -> offlineFileViewModel.clearFinish()
            "???????????????" -> offlineFileViewModel.clearError()
            "????????????????????????" -> {
                val stringJoiner = StringJoiner("\n")
                offlineList.forEach { i -> stringJoiner.add(i.url) }
                copyDownloadUrl(context, stringJoiner.toString())
            }
            "ModalNavigationDrawerMenu" -> offlineFileViewModel.openDrawerState()
        }
    }

    val pullRefreshState = rememberPullRefreshState(refreshing, { offlineFileViewModel.refresh() })

    Column {
        AppTopBarOfflineFile(stringResource(R.string.app_name), appBarOnClick)

        MiddleEllipsisText(
            text = "??????????????????${offlineInfo.count}????????????${offlineInfo.quota}/${offlineInfo.total}",
            modifier = Modifier.padding(8.dp, 4.dp)
        )

        Box(Modifier.pullRefresh(pullRefreshState)) {
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

            PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
        }

//        Scaffold() {
//            SwipeRefresh(state = rememberSwipeRefreshState(refreshing), onRefresh = {
//                offlineFileViewModel.refresh()
//            }) {
//                LazyColumn(
//                    modifier = Modifier.fillMaxSize(),
//                ) {
//                    itemsIndexed(items = offlineList, key = { _, item ->
//                        item.hashCode()
//                    }) { index, item ->
//                        OfflineCellItem(
//                            offlineTask = item,
//                            index = index,
//                            itemOnClick = itemOnClick,
//                            menuOnClick = menuOnClick
//                        )
//                    }
//                }
//            }
//
//        }
    }

}

fun copyDownloadUrl(context: Context, text: String) {
    val clipboard = getSystemService(context, ClipboardManager::class.java)
    val clip = ClipData.newPlainText("label", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "????????????~", Toast.LENGTH_SHORT).show()
}