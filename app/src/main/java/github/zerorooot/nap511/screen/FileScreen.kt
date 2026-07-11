package github.zerorooot.nap511.screen

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.screenitem.FileCellItem
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.LocalDrawerState
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.cancelCut
import github.zerorooot.nap511.viewmodel.cut
import github.zerorooot.nap511.viewmodel.delete
import github.zerorooot.nap511.viewmodel.downloadText
import github.zerorooot.nap511.viewmodel.getFileInfo
import github.zerorooot.nap511.viewmodel.getVideoInfo
import github.zerorooot.nap511.viewmodel.getZipListFile
import github.zerorooot.nap511.viewmodel.openAria2Dialog
import github.zerorooot.nap511.viewmodel.openCreateFolderDialog
import github.zerorooot.nap511.viewmodel.openCreateSelectTorrentFileDialog
import github.zerorooot.nap511.viewmodel.openRenameFileDialog
import github.zerorooot.nap511.viewmodel.removeFile
import github.zerorooot.nap511.viewmodel.startSendAria2Service
import github.zerorooot.nap511.viewmodel.updateVideoFileBean
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun FileScreen(
    appBarOnClick: (String) -> Unit
) {
    val fileViewModel = viewModel<FileViewModel>()
    val offlineFileViewModel = viewModel<OfflineFileViewModel>()

    val fabPosition by remember {
        mutableStateOf(
            when (DataStoreUtil.getData(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION, "End")) {
                "Start" -> FabPosition.Start
                "Center" -> FabPosition.Center
                "End" -> FabPosition.End
                "EndOverlay" -> FabPosition.EndOverlay
                else -> FabPosition.End
            }
        )
    }

    val fileBeanList = fileViewModel.fileBeanList
    val path by fileViewModel.currentPath.collectAsState()

    val listLocation = fileViewModel.getListLocation(path)
    val listState = key(path) {
        rememberLazyListState(
            listLocation.firstVisibleItemIndex,
            listLocation.firstVisibleItemScrollOffset
        )
    }

    val refreshing by fileViewModel.isRefreshing.collectAsState()

    val context = LocalContext.current

    CreateDialogs()

    val videoActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val index = data?.getIntExtra("fileBeanIndex", -1) ?: -1
            val duration = data?.getIntExtra("current_time", 0) ?: 0
            val pickCode = data?.getStringExtra("pickCode") ?: "0"
            fileViewModel.updateVideoFileBean(fileViewModel.currentCid, index, duration, pickCode)
        }
    }

    LaunchedEffect(Unit) {
        fileViewModel.launchVideoEvent.collect { videoDate ->
            val videoInfoBeanJson = Gson().toJson(videoDate, VideoInfoBean::class.java)
            val intent = Intent(context, VideoActivity::class.java).apply {
                putExtra("fileBeanIndex", videoDate.index)
                putExtra("bean", videoInfoBeanJson)
            }
            videoActivityLauncher.launch(intent)
        }
    }

    fun handleMultiSelectClick(i: Int) {
        fileViewModel.select(i)
    }

    fun handleFolderClick(i: Int, fileBean: FileBean) {
        if (DataStoreUtil.getData(ConfigKeyUtil.EARLY_LOADING, false)) {
            val before = i - 1
            val after = i + 1
            if (before >= 0 && fileBeanList[before].isFolder) {
                fileViewModel.updateFileCache(fileBeanList[before].categoryId)
            }
            if (after < fileBeanList.size && fileBeanList[after].isFolder) {
                fileViewModel.updateFileCache(fileBeanList[after].categoryId)
            }
        }
        fileViewModel.getFiles(fileBean.categoryId)
    }

    fun handleVideoClick(i: Int, fileBean: FileBean) {
        fileViewModel.getVideoInfo(fileBean.pickCode, i)
    }

    fun handlePhotoClick(fileBean: FileBean) {
        val photoList = fileBeanList.filter { it.photoThumb != "" }
        fileViewModel.photoFileBeanList.clear()
        fileViewModel.photoFileBeanList.addAll(photoList)
        fileViewModel.photoIndexOf = photoList.indexOf(fileBean)
        fileViewModel.selectedItem = ConfigKeyUtil.PHOTO
    }


    fun handleTorrentClick(fileBean: FileBean) {
        fileViewModel.setRefreshingStatus(true)
        offlineFileViewModel.getTorrentTask(fileBean.sha1)
        fileViewModel.openCreateSelectTorrentFileDialog()
    }

    fun handleZipClick(i: Int) {
        fileViewModel.setRefreshingStatus(true)
        fileViewModel.selectIndex = i
        fileViewModel.getZipListFile()
    }

    fun handleTextClick(i: Int, fileBean: FileBean) {
        if (fileBean.size.toLong() < 1 * 1024 * 100) {
            fileViewModel.setRefreshingStatus(true)
            fileViewModel.selectIndex = i
            fileViewModel.downloadText(fileBean)
        } else {
            App.instance.toast("仅支持打开100kb以下的文件")
        }
    }

    // Assembled myItemOnClick — routes to focused handlers
    fun myItemOnClick(i: Int) {
        if (fileViewModel.isLongClickState) {
            handleMultiSelectClick(i)
        } else {
            //记录上级目录当前的位置
            fileViewModel.setListLocationAndClickCache(i, listState)
            val fileBean = fileBeanList[i]

            if (fileBean.isFolder) {
                handleFolderClick(i, fileBean)
            }

            if (fileBean.isVideo == 1) {
                handleVideoClick(i, fileBean)
            }

            if (fileBean.photoThumb != "") {
                handlePhotoClick(fileBean)
            }

            if (fileBean.fileIco == R.drawable.torrent) {
                handleTorrentClick(fileBean)
            }

            if (fileBean.fileIco == R.drawable.zip) {
                handleZipClick(i)
            }

            if (fileBean.fileIco == R.drawable.txt) {
                handleTextClick(i, fileBean)
            }
        }
    }

    // ============================================================
    // Phase 2.1: Eliminate FAB string dispatch
    // ============================================================
    fun onCutPasteClick() {
        fileViewModel.removeFile()
    }

    fun onAddFolderClick() {
        fileViewModel.openCreateFolderDialog()
    }

    fun onCancelCutClick() {
        fileViewModel.cancelCut()
    }

    // ============================================================
    // Phase 2.2: Eliminate menu string dispatch
    // ============================================================
    fun onMenuCut(index: Int) {
        fileViewModel.cut(index)
    }

    fun onMenuDelete(index: Int) {
        fileViewModel.delete(index)
    }

    fun onMenuRename(index: Int) {
        fileViewModel.selectIndex = index
        fileViewModel.openRenameFileDialog()
    }

    fun onMenuFileInfo(index: Int) {
        fileViewModel.selectIndex = index
        fileViewModel.getFileInfo(index)
//        fileViewModel.openFileInfoDialog()
    }

    fun onMenuAria2Download(index: Int) {
        val aria2Url = DataStoreUtil.getData(ConfigKeyUtil.ARIA2_URL, "")
        if (aria2Url == "") {
            fileViewModel.openAria2Dialog()
        } else {
            fileViewModel.startSendAria2Service(index)
        }
    }

    // ============================================================
    // Phase 2.3: Extract onBackClick
    // ============================================================
    fun onBack() {
        if (path != "/根目录" && !fileViewModel.isLongClickState) {
            fileViewModel.setListLocation(path, listState)
        }
        //触发路径和数据源的改变，重组后交由上方滚动
        fileViewModel.back()
    }

    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()

    fun onBackClick() {
        if (path == "/根目录" && !fileViewModel.isSearchState && !fileViewModel.isLongClickState) {
            scope.launch { drawerState.open() }
        } else {
            onBack()
        }
    }

    BackHandler(
        path != "/根目录" || fileViewModel.isLongClickState || fileViewModel.isSearchState,
        ::onBack
    )

    fun myAppBarOnClick(name: String) {
        when (name) {
            "back" -> {
                onBackClick()
            }

            "视频时间" -> {
                fileViewModel.fileBeanList.sortByDescending { fileBean -> fileBean.playLong }
                //滚动到首行
                listState.requestScrollToItem(0, 0)
            }

            else -> {
                appBarOnClick(name)
            }
        }
    }

    val clipboardManager = LocalClipboard.current

    // ============================================================
    // Phase 3: Extract path bar click callbacks
    // ============================================================
    fun onPathClick() {
        clipboardManager.nativeClipboard.setPrimaryClip(
            ClipData.newPlainText("path", path)
        )
        App.instance.toast("$path 已复制到剪切板")
    }

    fun onPathDoubleClick() {
        listState.requestScrollToItem(0, 0)
    }

    fun onPathLongClick() {
        clipboardManager.nativeClipboard.setPrimaryClip(
            ClipData.newPlainText("currentCid", fileViewModel.currentCid)
        )
        App.instance.toast("cid ${fileViewModel.currentCid} 已复制到剪切板")
    }

    // ============================================================
    // Phase 4: inline itemOnLongClick (no remember needed)
    // ============================================================
    fun itemOnLongClick(i: Int) {
        fileViewModel.isLongClickState = !fileViewModel.isLongClickState
        if (fileViewModel.isLongClickState) {
            fileViewModel.select(i)
        } else {
            fileViewModel.appBarTitle = "nap511"
        }
    }

    Column {
        AnimatedContent(
            targetState = fileViewModel.isLongClickState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) {
            if (it) {
                AppTopBarMultiple(fileViewModel.appBarTitle, ::myAppBarOnClick)
            } else {
                AppTopBarNormal(fileViewModel.appBarTitle, ::myAppBarOnClick)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = ::onPathClick,
                    onDoubleClick = ::onPathDoubleClick,
                    onLongClick = ::onPathLongClick,
                ),
        ) {
            MiddleEllipsisText(
                text = path, modifier = Modifier.padding(8.dp, 4.dp)
            )
        }

        Scaffold(
            floatingActionButton = {
                AnimatedContent(
                    targetState = fileViewModel.isCutState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = ""
                ) {
                    if (it) {
                        Column {
                            FloatingActionButton(onClick = ::onCancelCutClick) {
                                Icon(Icons.Filled.Close, "close")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            FloatingActionButton(onClick = ::onCutPasteClick) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_content_paste_24),
                                    "cut"
                                )
                            }
                        }
                    } else {
                        FloatingActionButton(onClick = ::onAddFolderClick) {
                            Icon(Icons.Filled.Add, "add")
                        }
                    }
                }
            },
            floatingActionButtonPosition = fabPosition
        ) { _ ->
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { fileViewModel.refresh() }
            ) {
                //当 path 改变时，强制销毁并重新创建 Scrollbar，让它正确绑定新传入的 listState
                key(path) {
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
                                items = fileBeanList,
                                key = { _, item -> item.sha1 }
                            ) { index, item ->
                                FileCellItem(
                                    item,
                                    index,
                                    fileViewModel.clickMap.getOrDefault(path, -1),
                                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                    ::myItemOnClick,
                                    itemOnLongClick = ::itemOnLongClick,
                                    onCut = ::onMenuCut,
                                    onDelete = ::onMenuDelete,
                                    onRename = ::onMenuRename,
                                    onFileInfo = ::onMenuFileInfo,
                                    onAria2Download = ::onMenuAria2Download,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
