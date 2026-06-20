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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.FileBean
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
import github.zerorooot.nap511.viewmodel.getZipListFile
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

    val listState = rememberLazyListState()
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
            fileViewModel.updateVideoFileBean(fileViewModel.currentCid, index, duration)
        }
    }

    // ============================================================
    // Phase 1: Split myItemOnClick into focused handler functions
    // ============================================================

    fun handleMultiSelectClick(i: Int) {
        fileViewModel.select(i)
    }

    fun handleFolderClick(i: Int, fileBean: FileBean) {
//        fileViewModel.setListLocationAndClickCache(i)
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
        val intent = Intent(context, VideoActivity::class.java).apply {
            putExtra("cookie", App.cookie)
            putExtra("title", fileBean.name)
            putExtra("pick_code", fileBean.pickCode)
            putExtra("fileBeanIndex", i)
        }
        videoActivityLauncher.launch(intent)
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
        fileViewModel.openCreateSelectTorrentFileDialog()
        offlineFileViewModel.getTorrentTask(fileBean.sha1)
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

    fun handleScrollToDirectory(fileBean: FileBean) {
        fileViewModel.getListLocation("$path/${fileBean.name}", listState)
    }

    // Assembled myItemOnClick — routes to focused handlers
    val myItemOnClick = { i: Int ->
        if (fileViewModel.isLongClickState) {
            handleMultiSelectClick(i)
        } else {
            //记录上级目录当前的位置
            fileViewModel.setListLocationAndClickCache(i, listState)
            val fileBean = fileBeanList[i]

            if (fileBean.isFolder) {
                handleFolderClick(i, fileBean)
                //滚动到当前目录
                handleScrollToDirectory(fileBean)
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
    val onCutPasteClick: () -> Unit = { fileViewModel.removeFile() }
    val onAddFolderClick: () -> Unit = { fileViewModel.openCreateFolderDialog() }
    val onCancelCutClick: () -> Unit = { fileViewModel.cancelCut() }

    // ============================================================
    // Phase 2.2: Eliminate menu string dispatch
    // ============================================================
    val onMenuCut: (Int) -> Unit = { index -> fileViewModel.cut(index) }
    val onMenuDelete: (Int) -> Unit = { index -> fileViewModel.delete(index) }
    val onMenuRename: (Int) -> Unit = { index ->
        fileViewModel.selectIndex = index
        fileViewModel.openRenameFileDialog()
    }
    val onMenuFileInfo: (Int) -> Unit = { index ->
        fileViewModel.selectIndex = index
        fileViewModel.getFileInfo(index)
//        fileViewModel.openFileInfoDialog()
    }
    val onMenuAria2Download: (Int) -> Unit = { index ->
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
    val onBack = {
        val lastIndexOf = path.lastIndexOf("/")
        val parentDirectory = if (lastIndexOf == -1) {
            ""
        } else {
            path.subSequence(0, lastIndexOf).toString()
        }
        fileViewModel.back()

        if (path != "/根目录" && !fileViewModel.isLongClickState) {
            fileViewModel.setListLocation(path, listState)
            fileViewModel.getListLocation(parentDirectory, listState)
        }
    }
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()

    val onBackClick: () -> Unit = {
        if (path == "/根目录" && !fileViewModel.isSearchState && !fileViewModel.isLongClickState) {
            scope.launch { drawerState.open() }
        } else {
            onBack()
        }
    }

    BackHandler(
        path != "/根目录" || fileViewModel.isLongClickState || fileViewModel.isSearchState,
        onBack
    )

    val myAppBarOnClick = fun(name: String) {
        when (name) {
            "back" -> {
                onBackClick()
            }

            "视频时间" -> {
                fileViewModel.fileBeanList.sortByDescending { fileBean -> fileBean.playLong }
                fileViewModel.getListLocation("null", listState)
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
    val onPathClick: () -> Unit = {
        clipboardManager.nativeClipboard.setPrimaryClip(
            ClipData.newPlainText("path", path)
        )
        App.instance.toast("$path 已复制到剪切板")
    }

    val onPathDoubleClick: () -> Unit = {
        fileViewModel.getListLocation("null", listState)
    }

    val onPathLongClick: () -> Unit = {
        clipboardManager.nativeClipboard.setPrimaryClip(
            ClipData.newPlainText("currentCid", fileViewModel.currentCid)
        )
        App.instance.toast("cid ${fileViewModel.currentCid} 已复制到剪切板")
    }

    // ============================================================
    // Phase 4: inline itemOnLongClick (no remember needed)
    // ============================================================
    val itemOnLongClick = { i: Int ->
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
                AppTopBarMultiple(fileViewModel.appBarTitle, myAppBarOnClick)
            } else {
                AppTopBarNormal(fileViewModel.appBarTitle, myAppBarOnClick)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onPathClick,
                    onDoubleClick = onPathDoubleClick,
                    onLongClick = onPathLongClick,
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
                            FloatingActionButton(onClick = onCancelCutClick) {
                                Icon(Icons.Filled.Close, "close")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            FloatingActionButton(onClick = onCutPasteClick) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_content_paste_24),
                                    "cut"
                                )
                            }
                        }
                    } else {
                        FloatingActionButton(onClick = onAddFolderClick) {
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
                            key = { _, item -> item.hashCode() }
                        ) { index, item ->
                            FileCellItem(
                                item,
                                index,
                                fileViewModel.clickMap.getOrDefault(path, -1),
                                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                myItemOnClick,
                                itemOnLongClick = itemOnLongClick,
                                onCut = onMenuCut,
                                onDelete = onMenuDelete,
                                onRename = onMenuRename,
                                onFileInfo = onMenuFileInfo,
                                onAria2Download = onMenuAria2Download,
                            )
                        }
                    }
                }
            }
        }
    }
}
