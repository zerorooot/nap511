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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.screenitem.FileCellItem
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
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

    if (!fileViewModel.isFileScreenListState()) {
        fileViewModel.fileScreenListState = rememberLazyListState()
    }

    val listState = fileViewModel.fileScreenListState
    val refreshing by fileViewModel.isRefreshing.collectAsState()

    val context = LocalContext.current

    CreateDialogs()

    val itemOnLongClick = remember {
        { i: Int ->
            fileViewModel.isLongClickState = !fileViewModel.isLongClickState
            if (fileViewModel.isLongClickState) {
                fileViewModel.select(i)
            } else {
                fileViewModel.appBarTitle = "nap511"
            }
        }
    }

    val floatingActionButtonOnClick = remember {
        { i: String ->
            when (i) {
                "CutFloatingActionButton" -> fileViewModel.removeFile()
                //打开添加文件夹
                "AddFloatingActionButton" -> fileViewModel.openCreateFolderDialog()
                "CloseFloatingActionButton" -> fileViewModel.cancelCut()
            }
        }
    }

    val menuOnClick = remember {
        { itemName: String, index: Int ->
            when (itemName) {
                "剪切" -> fileViewModel.cut(index)
                "删除" -> fileViewModel.delete(index)
                "重命名" -> {
                    fileViewModel.selectIndex = index
                    fileViewModel.openRenameFileDialog()
                }

                "文件信息" -> {
                    fileViewModel.selectIndex = index
                    fileViewModel.getFileInfo(index)
                    fileViewModel.openFileInfoDialog()
                }

                "通过aria2下载" -> {
//                val aria2Url = SharedPreferencesUtil(activity).get(ConfigUtil.aria2Url)
                    val aria2Url = DataStoreUtil.getData(ConfigKeyUtil.ARIA2_URL, "")
                    if (aria2Url == "") {
                        fileViewModel.openAria2Dialog()
                    } else {
                        fileViewModel.startSendAria2Service(index)
                    }
                }
            }
        }
    }

    val videoActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            // 从 Intent 中取出 VideoActivity 返回的数据
            val index = data?.getIntExtra("fileBeanIndex", -1) ?: -1
            val duration = data?.getIntExtra("current_time", 0) ?: 0
            fileViewModel.updateVideoFileBean(fileViewModel.currentCid, index, duration)
        }
    }

    val myItemOnClick = { i: Int ->
        if (fileViewModel.isLongClickState) {
            fileViewModel.select(i)
        } else {
//            //记录上级目录当前的位置
            fileViewModel.setListLocationAndClickCache(i)
            //进入下一级
            val fileBean = fileBeanList[i]
            if (fileBean.isFolder) {
                //提前加载上下两个文件夹
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
                //加载文件
                fileViewModel.getFiles(fileBean.categoryId)
            }
            //打开视频
            if (fileBean.isVideo == 1) {
                val intent = Intent(context, VideoActivity::class.java)
                intent.putExtra("cookie", App.cookie)
                intent.putExtra("title", fileBean.name)
                intent.putExtra("pick_code", fileBean.pickCode)
                intent.putExtra("fileBeanIndex", i)
                videoActivityLauncher.launch(intent)
            }
            //打开图片
            if (fileBean.photoThumb != "") {
                //具体实现在MainActivity#MyNavigationDrawer()
                val photoFileBeanList =
                    fileViewModel.fileBeanList.filter { ia -> ia.photoThumb != "" }
                fileViewModel.photoFileBeanList.clear()
                fileViewModel.photoFileBeanList.addAll(photoFileBeanList)
                fileViewModel.photoIndexOf = photoFileBeanList.indexOf(fileBean)
                App.selectedItem = ConfigKeyUtil.PHOTO
            }
            // 打开种子文件
            if (fileBean.fileIco == R.drawable.torrent) {
                fileViewModel.setRefreshingStatus(true)
                fileViewModel.openCreateSelectTorrentFileDialog()
                offlineFileViewModel.getTorrentTask(fileBean.sha1)
            }
            //打开压缩文件
            if (fileBean.fileIco == R.drawable.zip) {
                fileViewModel.setRefreshingStatus(true)
                fileViewModel.selectIndex = i
                fileViewModel.getZipListFile()
            }
            //打开小文本文件 100kb以下的文件
            if (fileBean.fileIco == R.drawable.txt) {
                if (fileBean.size.toLong() < 1 * 1024 * 100) {
                    fileViewModel.setRefreshingStatus(true)
                    fileViewModel.selectIndex = i
                    fileViewModel.downloadText(fileBean)
                } else {
                    App.instance.toast("仅支持打开100kb以下的文件")
                }
            }


            //滚动到当前目录
            if (fileBean.isFolder) {
                fileViewModel.getListLocation(
                    path + "/${fileBean.name}"
                )
            }
        }
    }

    val onBack = {
        val lastIndexOf = path.lastIndexOf("/")
        val parentDirectory = path.subSequence(
            0, if (lastIndexOf == -1) {
                0
            } else {
                lastIndexOf
            }
        ).toString()
        //appBar 也调用了这个，所以再判断一次
        if (path != "/根目录" && !fileViewModel.isLongClickState) {
            //当前目录的位置
            fileViewModel.setListLocation(path)
            fileViewModel.getListLocation(parentDirectory)
        }
        fileViewModel.back()
    }
    BackHandler(
        path != "/根目录" || fileViewModel.isLongClickState || fileViewModel.isSearchState, onBack
    )

    val myAppBarOnClick = { i: String ->
        if (i == "back") {
            if (path == "/根目录" && !fileViewModel.isSearchState && !fileViewModel.isLongClickState) {
                App.instance.openDrawerState()
            } else {
                onBack()
            }
        }
        appBarOnClick.invoke(i)
    }
    val clipboardManager = LocalClipboard.current

    // val clipboardManager: ClipboardManager = LocalClipboardManager.current
    Column {
        AnimatedContent(targetState = fileViewModel.isLongClickState, transitionSpec = {
            fadeIn() togetherWith fadeOut()
        }, label = "") {
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
                    onClick = {
                        // clipboardManager.setText(AnnotatedString((path)))
                        clipboardManager.nativeClipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "path",
                                path
                            )
                        )
                        App.instance.toast("$path 已复制到剪切板")
                    },
                    onDoubleClick = {
                        //滚到顶端
                        fileViewModel.getListLocation("null")
                    },
                    onLongClick = {
//                        clipboardManager.setText(AnnotatedString((fileViewModel.currentCid)))
                        clipboardManager.nativeClipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "currentCid",
                                fileViewModel.currentCid
                            )
                        )
                        App.instance.toast("cid ${fileViewModel.currentCid} 已复制到剪切板")
                    },
                ),
        ) {
            MiddleEllipsisText(
                text = path, modifier = Modifier.padding(8.dp, 4.dp)
            )
        }

        Scaffold(floatingActionButton = {
            AnimatedContent(targetState = fileViewModel.isCutState, transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }, label = "") {
                if (it) {
                    Column {
                        FloatingActionButton(onClick = {
                            floatingActionButtonOnClick.invoke("CloseFloatingActionButton")
                        }) {
                            Icon(Icons.Filled.Close, "close")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        FloatingActionButton(onClick = {
                            floatingActionButtonOnClick.invoke("CutFloatingActionButton")
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_content_paste_24),
                                "cut"
                            )
                        }
                    }
                } else {
                    FloatingActionButton(onClick = {
                        floatingActionButtonOnClick.invoke("AddFloatingActionButton")
                    }) {
                        Icon(Icons.Filled.Add, "add")
                    }
                }
            }
        }, floatingActionButtonPosition = fabPosition) { _ ->
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { fileViewModel.refresh() }
            ) {
                LazyColumnScrollbar(
                    state = listState, settings = ScrollbarSettings.Default.copy(
                        thumbUnselectedColor = Purple80
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(), state = listState
                    ) {
                        itemsIndexed(items = fileBeanList, key = { _, item ->
                            item.hashCode()
                        }) { index, item ->
                            FileCellItem(
                                item,
                                index,
                                fileViewModel.clickMap.getOrDefault(path, -1),
                                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                myItemOnClick,
                                itemOnLongClick,
                                menuOnClick
                            )
                        }
                    }
                }
            }

        }
    }
}


