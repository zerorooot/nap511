package github.zerorooot.nap511.screen

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.nativeClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ForceOpenType
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.dialog.ForceOpenDialog
import github.zerorooot.nap511.screenitem.FileCellItem
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.cancelCut
import github.zerorooot.nap511.viewmodel.cut
import github.zerorooot.nap511.viewmodel.delete
import github.zerorooot.nap511.viewmodel.downloadText
import github.zerorooot.nap511.viewmodel.getFileInfo
import github.zerorooot.nap511.viewmodel.getTorrentTask
import github.zerorooot.nap511.viewmodel.getVideoInfo
import github.zerorooot.nap511.viewmodel.getZipListFile
import github.zerorooot.nap511.viewmodel.openAria2Dialog
import github.zerorooot.nap511.viewmodel.openCreateFolderDialog
import github.zerorooot.nap511.viewmodel.openCreateSelectTorrentFileDialog
import github.zerorooot.nap511.viewmodel.openRenameFileDialog
import github.zerorooot.nap511.viewmodel.removeFile
import github.zerorooot.nap511.viewmodel.startSendAria2Service
import github.zerorooot.nap511.viewmodel.updateVideoFileBean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    audioViewModel: AudioViewModel,
    isExpandedScreen: Boolean,
    onNav: (Route) -> Unit,
    appBarOnClick: (String) -> Unit,
    drawerState: () -> Boolean
) {
    val fabPositionSetting by DataStoreUtil.getDataFlow(
        ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION,
        "End"
    )
        .collectAsStateWithLifecycle(initialValue = "End")
    val fabPosition = remember(fabPositionSetting) {
        when (fabPositionSetting) {
            "Start" -> FabPosition.Start
            "Center" -> FabPosition.Center
            "End" -> FabPosition.End
            "EndOverlay" -> FabPosition.EndOverlay
            else -> FabPosition.End
        }
    }
    val earlyLoading by DataStoreUtil.getDataFlow(ConfigKeyUtil.EARLY_LOADING, false)
        .collectAsStateWithLifecycle(initialValue = false)
    val maxTxtSizeStr by DataStoreUtil.getDataFlow(ConfigKeyUtil.MAX_TXT_SIZE, "200")
        .collectAsStateWithLifecycle(initialValue = "200")
    val aria2UrlConfig by DataStoreUtil.getDataFlow(
        ConfigKeyUtil.ARIA2_URL,
        ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE
    )
        .collectAsStateWithLifecycle(initialValue = ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE)

    val fileBeanList = fileViewModel.fileBeanList
    val path by fileViewModel.currentPath.collectAsState()
    val refreshing by fileViewModel.isRefreshing.collectAsState()
    val context = LocalContext.current
    var showDialog by rememberSaveable { mutableIntStateOf(-1) }

    val listLocation = fileViewModel.getListLocation(path)
    val listState = key(path) {
        rememberLazyListState(
            listLocation.firstVisibleItemIndex,
            listLocation.firstVisibleItemScrollOffset
        )
    }
    val gridState = key(path) {
        rememberLazyGridState(
            listLocation.firstVisibleItemIndex,
            listLocation.firstVisibleItemScrollOffset
        )
    }
    val density = LocalDensity.current
    // 1. 设置 35dp 的防抖阈值
    val thresholdPx = rememberSaveable(density) { with(density) { 35.dp.toPx() } }
    var isBottomBarShow by rememberSaveable { mutableStateOf(true) }

    // 2. 嵌套滚动监听
    val nestedScrollConnection = remember {
        var accumulatedDelta = 0f

        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y

                // 方向改变，重置滑动累加值
                if ((delta > 0 && accumulatedDelta < 0) || (delta < 0 && accumulatedDelta > 0)) {
                    accumulatedDelta = 0f
                }

                accumulatedDelta += delta

                // 【关键点】增加状态判断 (`&& isBottomBarShow` / `&& !isBottomBarShow`)，防止重复更新状态引发卡顿
                if (accumulatedDelta < -thresholdPx && isBottomBarShow) {
                    isBottomBarShow = false
                } else if (accumulatedDelta > thresholdPx && !isBottomBarShow) {
                    isBottomBarShow = true
                }
                return Offset.Zero
            }
        }
    }


    val videoActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK) {
            val index = data?.getIntExtra("fileBeanIndex", -1) ?: -1
            val duration = data?.getIntExtra("current_time", 0) ?: 0
            val pickCode = data?.getStringExtra("pickCode") ?: "0"
            fileViewModel.updateVideoFileBean(fileViewModel.currentCid, index, duration, pickCode)
        }
        if (result.resultCode == Activity.RESULT_CANCELED) {
            val nav = data?.getStringExtra("nav") ?: ""
            if (nav == "VerifyVideoAccount") {
                onNav.invoke(Route.VerifyVideoAccount)
            }
            val message = data?.getStringExtra("toast") ?: ""
            if (message.isNotEmpty()) {
                App.instance.toast(message)
            }
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

    fun handleFolderClick(i: Int, fileBean: FileBean) {
        isBottomBarShow = true
        if (earlyLoading) {
            listOf(i - 1, i + 1)
                .mapNotNull { fileBeanList.getOrNull(it) }
                .filter { it.isFolder }
                .forEach { fileViewModel.updateFileCache(it.categoryId) }
        }
        fileViewModel.getFiles(fileBean.categoryId)
    }

    fun handleVideoClick(i: Int, fileBean: FileBean) {
        audioViewModel.pause()
        fileViewModel.getVideoInfo(fileBean.pickCode, i, fileBean.name)
    }

    fun handleAudioClick(fileBean: FileBean) {
        isBottomBarShow = true
        fileViewModel.setRefreshingStatus(false)
        audioViewModel.playAudio(fileBean)
    }

    fun handlePhotoClick(fileBean: FileBean) {
        audioViewModel.pause()
        val photoList = fileBeanList.filter { it.photoThumb != "" }
        if (photoList.isEmpty()) {
            App.instance.toast("图片打开失败，找不到图片url！")
        } else {
            fileViewModel.photoFileBeanList.clear()
            fileViewModel.photoFileBeanList.addAll(photoList)
            fileViewModel.photoIndexOf = photoList.indexOf(fileBean)
            onNav.invoke(Route.Photo)
        }
        fileViewModel.setRefreshingStatus(false)
    }


    fun handleTorrentClick(fileBean: FileBean) {
        fileViewModel.getTorrentTask(fileBean.sha1)
        fileViewModel.openCreateSelectTorrentFileDialog()
    }

    fun handleZipClick(i: Int) {
        fileViewModel.selectIndex = i
        fileViewModel.getZipListFile()
    }

    fun handleTextClick(i: Int, fileBean: FileBean) {
        val txtSize = maxTxtSizeStr.toIntOrNull() ?: 200
        if (fileBean.size.toLong() < txtSize * 1024) {
            fileViewModel.selectIndex = i
            fileViewModel.downloadText(fileBean, onNav)
        } else {
            fileViewModel.setRefreshingStatus(false)
            App.instance.toast("仅支持打开${txtSize}kb以下的文件")
        }
    }


    if (showDialog != -1) {
        val bean = fileViewModel.fileBeanList[showDialog]
        if (bean.isFolder) {
            App.instance.toast("此功能仅支持文件，不支持文件夹")
            showDialog = -1
        } else {
            ForceOpenDialog(
                bean.name,
                onDismissRequest = { showDialog = -1 },
            ) {
                fileViewModel.setRefreshingStatus(true)
                when (it) {
                    ForceOpenType.VIDEO -> {
                        handleVideoClick(showDialog, bean)
                    }

                    ForceOpenType.AUDIO -> {
                        handleAudioClick(bean)
                    }

                    ForceOpenType.IMAGE -> {
                        handlePhotoClick(bean)
                    }

                    ForceOpenType.TEXT -> {
                        handleTextClick(showDialog, bean)
                    }

                    ForceOpenType.ARCHIVE -> {
                        handleZipClick(showDialog)
                    }

                    ForceOpenType.TORRENT -> {
                        handleTorrentClick(bean)
                    }
                }
            }
        }
    }

// 记录上次点击时间，使用 longArrayOf 避免无意义的重组
    val lastClickTime = remember { longArrayOf(0L) }

    // Assembled myItemOnClick — routes to focused handlers
    fun myItemOnClick(i: Int) {
        if (fileViewModel.isLongClickState) {
            fileViewModel.select(i)
        } else {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime[0] < 200L) { // 200ms 内的连点会被忽略
                return
            }
            lastClickTime[0] = currentTime


            fileViewModel.setRefreshingStatus(true)

            //记录上级目录当前的位置
            if (isExpandedScreen) {
                fileViewModel.setListLocationAndClickCache(i, gridState)
            } else {
                fileViewModel.setListLocationAndClickCache(i, listState)
            }
            val fileBean = fileBeanList[i]

            when {
                fileBean.isFolder -> handleFolderClick(i, fileBean)
                fileBean.isVideo == 1 -> handleVideoClick(i, fileBean)
                fileBean.fileIco == R.drawable.torrent -> handleTorrentClick(fileBean)
                fileBean.fileIco == R.drawable.zip -> handleZipClick(i)
                fileBean.fileIco == R.drawable.txt -> handleTextClick(i, fileBean)
                fileBean.fileIco == R.drawable.mp3 -> handleAudioClick(fileBean)
                fileBean.photoThumb.isNotEmpty() -> handlePhotoClick(fileBean)
                else -> fileViewModel.setRefreshingStatus(false)
            }

        }
    }

    fun onMenuAria2Download(index: Int) {
        if (aria2UrlConfig == ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE) {
            fileViewModel.openAria2Dialog()
        } else {
            fileViewModel.startSendAria2Service(index)
        }
    }

    val scope = rememberCoroutineScope()

    // ============================================================
    // Phase 2.3: Extract onBackClick
    // ============================================================
    fun onBack() {
        if (drawerState.invoke()) {
            return
        }
        if (path != "/根目录" && !fileViewModel.isLongClickState) {
            if (isExpandedScreen) {
                fileViewModel.setListLocation(path, gridState)
            } else {
                fileViewModel.setListLocation(path, listState)
            }
        }
        isBottomBarShow = true
        //触发路径和数据源的改变，重组后交由上方滚动
        fileViewModel.back()
    }

    BackHandler(
        path != "/根目录" || fileViewModel.isLongClickState || fileViewModel.isSearchState,
        ::onBack
    )

    fun myAppBarOnClick(name: String) {
        when (name) {
            "back" -> {
                onBack()
            }

            "视频时间" -> {
                scope.launch {
                    fileViewModel.fileBeanList.sortByDescending { fileBean -> fileBean.playLong }
                    delay(10.milliseconds)
                    if (isExpandedScreen) {
                        gridState.requestScrollToItem(0, 0)
                    } else {
                        listState.requestScrollToItem(0, 0)
                    }
                }

            }

            "缓存清空" -> {
                fileViewModel.refresh(true)
            }

            else -> {
                appBarOnClick(name)
            }
        }
    }

    val clipboardManager = LocalClipboard.current

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

    Scaffold(
        topBar = {
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
        },
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        bottomBar = {
            AnimatedVisibility(
                visible = audioViewModel.currentMusic != null && isBottomBarShow,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                MiniPlayerBar(audioViewModel = audioViewModel) {
                    onNav(Route.MusicDetail)
                }
            }
        },
        floatingActionButton = {
            FileScreenFab(
                isCutState = fileViewModel.isCutState,
                visible = isBottomBarShow,
                onCancelCut = { fileViewModel.cancelCut() },
                onCutPaste = { fileViewModel.removeFile() },
                onAddFolder = { fileViewModel.openCreateFolderDialog() }
            )
        },
        floatingActionButtonPosition = fabPosition
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
                .consumeWindowInsets(innerPadding)
        ) {
            FilePathBar(
                pathList = fileViewModel.pathList,
                onPathClick = {
                    clipboardManager.nativeClipboardManager.setPrimaryClip(
                        ClipData.newPlainText(
                            "path",
                            path
                        )
                    )
                    App.instance.toast("$path 已复制到剪切板")
                },
                onPathDoubleClick = {
                    scope.launch {
                        if (isExpandedScreen) {
                            gridState.requestScrollToItem(0, 0)
                        } else {
                            listState.requestScrollToItem(0, 0)
                        }
                    }
                },
                onPathLongClick = { name, cid ->
                    scope.launch {
                        DataStoreUtil.putDataSuspend(
                            ConfigKeyUtil.DEFAULT_OFFLINE_CID,
                            cid
                        )
                    }
                    App.instance.toast("设置默认离线位置为: $name")
                },
                onItemClick = {
                    fileViewModel.getFiles(it)
                }
            )

            FileListContent(
                refreshing = refreshing,
                fileBeanList = fileBeanList,
                path = path,
                listState = listState,
                gridState = gridState,
                isExpandedScreen = isExpandedScreen,
                clickIndex = fileViewModel.clickMap.getOrDefault(path, -1),
                onRefresh = { fileViewModel.refresh() },
                onItemClick = ::myItemOnClick,
                onItemLongClick = ::itemOnLongClick,
                onCut = { fileViewModel.cut(it) },
                onDelete = { fileViewModel.delete(it) },
                onRename = { index ->
                    fileViewModel.selectIndex = index
                    fileViewModel.openRenameFileDialog()
                },
                onFileInfo = { index ->
                    fileViewModel.selectIndex = index
                    fileViewModel.getFileInfo(index)
                },
                onAria2Download = ::onMenuAria2Download,
                onForceOpen = { index -> showDialog = index }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilePathBar(
    pathList: List<PathBean>,
    onPathClick: () -> Unit,
    onPathDoubleClick: () -> Unit,
    onPathLongClick: (String, String) -> Unit,
    onItemClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    // 路径变化时自动滚动到最右侧末尾
    LaunchedEffect(pathList.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPathClick,
                onDoubleClick = onPathDoubleClick,
                onLongClick = {
                    val path = pathList.last()
                    onPathLongClick.invoke(path.name, path.cid)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pathList.forEachIndexed { index, path ->
                // interactionSource 以便组件和点击修饰符同步水波纹与焦点状态
                val interactionSource = remember { MutableInteractionSource() }
                Box {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = {
                            Text(text = path.name.ifEmpty { "根目录" })
                        },
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                        interactionSource = interactionSource
                    )
                    // 添加一个完全匹配尺寸的透明层，统一处理单击和长按
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null, // 彻底关闭浮层的水波纹渲染，由底层 Chip 自行展示
                                onClick = { onItemClick(path.cid) },
                                onLongClick = { onPathLongClick.invoke(path.name, path.cid) }
                            )
                    )
                }

                // 间隔符
                if (index < pathList.size - 1) {
                    MiddleEllipsisText(
                        text = "/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
//                        modifier = Modifier.padding(0.dp, 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FileScreenFab(
    isCutState: Boolean,
    visible: Boolean,
    onCancelCut: () -> Unit,
    onCutPaste: () -> Unit,
    onAddFolder: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + scaleIn() + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + scaleOut() + fadeOut()
    ) {
        AnimatedContent(
            targetState = isCutState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "FabAnimation"
        ) { isCut ->
            if (isCut) {
                Column {
                    FloatingActionButton(onClick = onCancelCut) {
                        Icon(Icons.Filled.Close, "close")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(onClick = onCutPaste) {
                        Icon(Icons.Default.ContentPaste, "cut")
                    }
                }
            } else {
                FloatingActionButton(onClick = onAddFolder) {
                    Icon(Icons.Filled.Add, "add")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileListContent(
    refreshing: Boolean,
    fileBeanList: List<FileBean>,
    path: String,
    listState: LazyListState,
    gridState: LazyGridState,
    isExpandedScreen: Boolean,
    clickIndex: Int,
    onRefresh: () -> Unit,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onCut: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onRename: (Int) -> Unit,
    onFileInfo: (Int) -> Unit,
    onAria2Download: (Int) -> Unit,
    onForceOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        if (fileBeanList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无文件")
            }
        } else {
            key(path) {
                if (isExpandedScreen) {
                    LazyVerticalGridScrollbar(
                        state = gridState,
                        settings = ScrollbarSettings.Default.copy(
                            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 340.dp),
//                            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItemsIndexed(
                                items = fileBeanList,
                                key = { _, item ->
                                    item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
                                },
                            ) { index, item ->
                                FileCellItem(
                                    fileBean = item,
                                    index = index,
                                    clickIndex = clickIndex,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null
                                    ),
                                    itemOnClick = onItemClick,
                                    itemOnLongClick = onItemLongClick,
                                    onCut = onCut,
                                    onDelete = onDelete,
                                    onRename = onRename,
                                    onFileInfo = onFileInfo,
                                    onForceOpen = onForceOpen,
                                    onAria2Download = onAria2Download
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
                            state = listState,
//                            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
                        ) {
                            itemsIndexed(
                                items = fileBeanList,
                                key = { _, item ->
                                    item.fileId.ifEmpty { item.pickCode.ifEmpty { item.uuid.toString() } }
                                },
                            ) { index, item ->
                                FileCellItem(
                                    fileBean = item,
                                    index = index,
                                    clickIndex = clickIndex,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null
                                    ),
                                    itemOnClick = onItemClick,
                                    itemOnLongClick = onItemLongClick,
                                    onCut = onCut,
                                    onDelete = onDelete,
                                    onRename = onRename,
                                    onFileInfo = onFileInfo,
                                    onForceOpen = onForceOpen,
                                    onAria2Download = onAria2Download
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}