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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
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
import github.zerorooot.nap511.screenitem.ImageCellItem
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.cancelCut
import github.zerorooot.nap511.viewmodel.cut
import github.zerorooot.nap511.viewmodel.delete
import github.zerorooot.nap511.viewmodel.deleteMultiple
import github.zerorooot.nap511.viewmodel.downloadText
import github.zerorooot.nap511.viewmodel.downloadWeb
import github.zerorooot.nap511.viewmodel.getFileInfo
import github.zerorooot.nap511.viewmodel.getTorrentTask
import github.zerorooot.nap511.viewmodel.getVideoInfo
import github.zerorooot.nap511.viewmodel.getZipListFile
import github.zerorooot.nap511.viewmodel.openAria2Dialog
import github.zerorooot.nap511.viewmodel.openCreateFolderDialog
import github.zerorooot.nap511.viewmodel.openFileOrderDialog
import github.zerorooot.nap511.viewmodel.openRenameFileDialog
import github.zerorooot.nap511.viewmodel.openSearchDialog
import github.zerorooot.nap511.viewmodel.openUnzipAllFileDialog
import github.zerorooot.nap511.viewmodel.removeFile
import github.zerorooot.nap511.viewmodel.startSendAria2Service
import github.zerorooot.nap511.viewmodel.updateVideoFileBean
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class, ExperimentalCoilApi::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    audioViewModel: AudioViewModel,
    isExpandedScreen: Boolean,
    gridCellMinSize: Dp,
    onNav: (Route) -> Unit,
    drawerState: () -> Boolean
) {
    val uiState by fileViewModel.uiState.collectAsStateWithLifecycle()

    val fileBeanList = fileViewModel.fileBeanList
    val path = uiState.path
    val refreshing = uiState.isRefreshing
    val context = LocalContext.current
    var showForceOpenDialog by rememberSaveable { mutableIntStateOf(-1) }
    var isImagePreviewMode by rememberSaveable { mutableStateOf(false) }

    var isNotificationEnabled by remember {
        mutableStateOf(App.instance.isNotificationEnabled(context))
    }
    var isNotificationBannerDismissed by rememberSaveable { mutableStateOf(false) }

    val notificationSettingLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isNotificationEnabled = App.instance.isNotificationEnabled(context)
    }

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
        if (uiState.earlyLoading) {
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
    }

    fun handleZipClick(i: Int) {
        fileViewModel.selectIndex = i
        fileViewModel.getZipListFile()
    }

    fun checkAndDownloadFile(i: Int, fileBean: FileBean, action: () -> Unit) {
        val txtSize = uiState.maxTxtSizeStr.toIntOrNull() ?: 200
        if (fileBean.size.toLong() < txtSize * 1024) {
            fileViewModel.selectIndex = i
            action()
        } else {
            fileViewModel.setRefreshingStatus(false)
            App.instance.toast("仅支持打开${txtSize}kb以下的文件")
        }
    }

    fun handleTextClick(i: Int, fileBean: FileBean) {
        checkAndDownloadFile(i, fileBean) {
            fileViewModel.downloadText(fileBean, onNav)
        }
    }

    fun handleWebClick(i: Int, fileBean: FileBean) {
        checkAndDownloadFile(i, fileBean) {
            fileViewModel.downloadWeb(fileBean, onNav)
        }
    }


    if (showForceOpenDialog != -1) {
        val bean = fileViewModel.fileBeanList[showForceOpenDialog]
        if (bean.isFolder) {
            App.instance.toast("此功能仅支持文件，不支持文件夹")
            showForceOpenDialog = -1
        } else {
            ForceOpenDialog(
                bean.name,
                onDismissRequest = { showForceOpenDialog = -1 },
            ) {
                fileViewModel.setRefreshingStatus(true)
                when (it) {
                    ForceOpenType.VIDEO -> {
                        handleVideoClick(showForceOpenDialog, bean)
                    }

                    ForceOpenType.AUDIO -> {
                        handleAudioClick(bean)
                    }

                    ForceOpenType.IMAGE -> {
                        handlePhotoClick(bean)
                    }

                    ForceOpenType.TEXT -> {
                        handleTextClick(showForceOpenDialog, bean)
                    }

                    ForceOpenType.WEB -> {
                        handleWebClick(showForceOpenDialog, bean)
                    }

                    ForceOpenType.ARCHIVE -> {
                        handleZipClick(showForceOpenDialog)
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
            if (isExpandedScreen || isImagePreviewMode) {
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
                fileBean.fileIco == R.drawable.web -> handleWebClick(i, fileBean)
                fileBean.fileIco == R.drawable.mp3 -> handleAudioClick(fileBean)
                fileBean.photoThumb.isNotEmpty() -> handlePhotoClick(fileBean)
                else -> fileViewModel.setRefreshingStatus(false)
            }

        }
    }

    fun onMenuAria2Download(index: Int) {
        if (uiState.aria2UrlConfig == ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE) {
            fileViewModel.openAria2Dialog()
        } else {
            fileViewModel.startSendAria2Service(index)
        }
    }

    // ============================================================
    // Phase 2.3: Extract onBackClick
    // ============================================================
    fun onBack() {
        if (drawerState.invoke()) {
            return
        }
        if (path != "/根目录" && !fileViewModel.isLongClickState) {
            if (isExpandedScreen || isImagePreviewMode) {
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

            "图片预览" -> {
                isImagePreviewMode = !isImagePreviewMode
            }

            "视频时间" -> {
                fileViewModel.sortByVideoTime()
                if (isExpandedScreen || isImagePreviewMode) {
                    gridState.requestScrollToItem(0, 0)
                } else {
                    listState.requestScrollToItem(0, 0)
                }
            }

            "缓存清空" -> {
                fileViewModel.refresh(true)
            }

            "unzipAllFile" -> {
                fileViewModel.openUnzipAllFileDialog()
            }

            "selectToUp" -> fileViewModel.selectToUp()
            "selectToDown" -> fileViewModel.selectToDown()
            "cut" -> fileViewModel.cut()
            //具体实现在FileScreen#CreateDialogs()里
            "search" -> fileViewModel.openSearchDialog()
            "delete" -> fileViewModel.deleteMultiple()
//            "selectAll" -> fileViewModel.selectAll()
            "selectReverse" -> fileViewModel.selectReverse()
            //具体实现在FileScreen#CreateDialogs()里
            "文件排序" -> fileViewModel.openFileOrderDialog()
            "刷新文件" -> fileViewModel.refresh()

        }
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

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    val imageLoader = context.imageLoader

    val contentActions = remember(
        path,
        isExpandedScreen,
        isImagePreviewMode,
        gridState,
        listState,
        imageLoader,
        clipboardManager,
        scope,
        context
    ) {
        FileContentActions(
            onOpenNotificationSettings = {
                val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                    putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                }
                notificationSettingLauncher.launch(intent)
            },
            onDismissNotificationBanner = { isNotificationBannerDismissed = true },
            onPathClick = {
                clipboardManager.nativeClipboardManager.setPrimaryClip(
                    ClipData.newPlainText("path", path)
                )
                App.instance.toast("$path 已复制到剪切板")
            },
            onPathDoubleClick = {
                scope.launch {
                    if (isExpandedScreen || isImagePreviewMode) {
                        gridState.requestScrollToItem(0, 0)
                    } else {
                        listState.requestScrollToItem(0, 0)
                    }
                }
            },
            onPathLongClick = { name, cid ->
                scope.launch {
                    DataStoreUtil.putDataSuspend(ConfigKeyUtil.DEFAULT_OFFLINE_CID, cid)
                    val index = fileViewModel.pathList.indexOfFirst { it.cid == cid }
                    val pathString = fileViewModel.pathList.take(index + 1)
                        .joinToString(separator = "/") { it.name }
                    DataStoreUtil.putDataSuspend(ConfigKeyUtil.DEFAULT_OFFLINE_PATH, pathString)
                }
                App.instance.toast("设置默认离线位置为: $name")
            },
            onPathItemClick = { fileViewModel.getFiles(it) },
            onRefresh = {
                fileBeanList.forEach { fileBean ->
                    imageLoader.memoryCache?.remove(MemoryCache.Key(fileBean.fileId))
                    imageLoader.diskCache?.remove(fileBean.fileId)
                }
                fileViewModel.refresh()
            },
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
            onForceOpen = { showForceOpenDialog = it }
        )
    }

    FileScaffold(
        isLongClickState = fileViewModel.isLongClickState,
        appBarTitle = fileViewModel.appBarTitle,
        isExpandedScreen = isExpandedScreen,
        isBottomBarShow = isBottomBarShow,
        hasCurrentMusic = audioViewModel.currentMusic != null,
        isCutState = fileViewModel.isCutState,
        fabPosition = uiState.fabPosition,
        nestedScrollConnection = nestedScrollConnection,
        audioViewModel = audioViewModel,
        onAppBarClick = ::myAppBarOnClick,
        onMusicDetailNav = { onNav(Route.MusicDetail) },
        onCancelCut = { fileViewModel.cancelCut() },
        onCutPaste = { fileViewModel.removeFile() },
        onAddFolder = { fileViewModel.openCreateFolderDialog() }
    ) { innerPadding ->
        FileScreenContent(
            innerPadding = innerPadding,
            path = path,
            pathList = fileViewModel.pathList,
            fileBeanList = fileBeanList,
            refreshing = refreshing,
            clickIndex = fileViewModel.clickMap.getOrDefault(path, -1),
            isNotificationEnabled = isNotificationEnabled,
            isNotificationBannerDismissed = isNotificationBannerDismissed,
            isExpandedScreen = isExpandedScreen,
            isImagePreviewMode = isImagePreviewMode,
            gridState = gridState,
            listState = listState,
            gridCellMinSize = gridCellMinSize,
            actions = contentActions
        )
    }
}


@Composable
private fun FileScaffold(
    isLongClickState: Boolean,
    appBarTitle: String,
    isExpandedScreen: Boolean,
    isBottomBarShow: Boolean,
    hasCurrentMusic: Boolean,
    isCutState: Boolean,
    fabPosition: FabPosition,
    nestedScrollConnection: NestedScrollConnection,
    audioViewModel: AudioViewModel,
    onAppBarClick: (String) -> Unit,
    onMusicDetailNav: () -> Unit,
    onCancelCut: () -> Unit,
    onCutPaste: () -> Unit,
    onAddFolder: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isLongClickState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = ""
            ) {
                if (it) {
                    AppTopBarMultiple(
                        title = appBarTitle,
                        isExpandedScreen = isExpandedScreen,
                        onClick = onAppBarClick
                    )
                } else {
                    AppTopBarNormal(appBarTitle, onAppBarClick)
                }
            }
        },
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        bottomBar = {
            AnimatedVisibility(
                visible = hasCurrentMusic && isBottomBarShow,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                MiniPlayerBar(audioViewModel = audioViewModel) {
                    onMusicDetailNav()
                }
            }
        },
        floatingActionButton = {
            FileScreenFab(
                isCutState = isCutState,
                visible = isBottomBarShow,
                onCancelCut = onCancelCut,
                onCutPaste = onCutPaste,
                onAddFolder = onAddFolder
            )
        },
        floatingActionButtonPosition = fabPosition,
        content = content
    )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun FileScreenContent(
    innerPadding: PaddingValues,
    path: String,
    pathList: List<PathBean>,
    fileBeanList: List<FileBean>,
    refreshing: Boolean,
    clickIndex: Int,
    isNotificationEnabled: Boolean,
    isNotificationBannerDismissed: Boolean,
    isExpandedScreen: Boolean,
    isImagePreviewMode: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
    gridCellMinSize: Dp,
    actions: FileContentActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            )
            .consumeWindowInsets(innerPadding)
    ) {
        AnimatedVisibility(
            visible = !isNotificationEnabled && !isNotificationBannerDismissed,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            NotificationPermissionBanner(
                onOpenSettings = actions.onOpenNotificationSettings,
                onDismiss = actions.onDismissNotificationBanner
            )
        }

        FilePathBar(
            pathList = pathList,
            onPathClick = actions.onPathClick,
            onPathDoubleClick = actions.onPathDoubleClick,
            onPathLongClick = actions.onPathLongClick,
            onItemClick = actions.onPathItemClick
        )

        FileListContent(
            refreshing = refreshing,
            fileBeanList = fileBeanList,
            path = path,
            listState = listState,
            gridState = gridState,
            gridCellMinSize = gridCellMinSize,
            isExpandedScreen = isExpandedScreen,
            isImagePreviewMode = isImagePreviewMode,
            clickIndex = clickIndex,
            onRefresh = actions.onRefresh,
            onItemClick = actions.onItemClick,
            onItemLongClick = actions.onItemLongClick,
            onCut = actions.onCut,
            onDelete = actions.onDelete,
            onRename = actions.onRename,
            onFileInfo = actions.onFileInfo,
            onAria2Download = actions.onAria2Download,
            onForceOpen = actions.onForceOpen
        )
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
                        selected = (index != pathList.size - 1),
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
    gridCellMinSize: Dp,
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
    modifier: Modifier = Modifier,
    isImagePreviewMode: Boolean = false
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
            key(path, isImagePreviewMode) {
                if (isExpandedScreen || isImagePreviewMode) {
                    LazyVerticalGridScrollbar(
                        state = gridState,
                        settings = ScrollbarSettings.Default.copy(
                            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = gridCellMinSize),
//                            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItemsIndexed(
                                items = fileBeanList,
                                key = { _, item ->
                                    item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
                                },
                            ) { index, item ->
                                if (isImagePreviewMode) {
                                    ImageCellItem(
                                        fileBean = item,
                                        index = index,
                                        gridCellMinSize = gridCellMinSize,
                                        clickIndex = clickIndex,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null
                                        ),
                                        itemOnClick = onItemClick,
                                        itemOnLongClick = onItemLongClick
                                    )
                                } else {
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

@Composable
private fun NotificationPermissionBanner(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "未开启通知权限，可能无法及时收到离线下载提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("去开启", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 文件内容区域 UI 交互事件封装
 */
private data class FileContentActions(
    val onOpenNotificationSettings: () -> Unit,
    val onDismissNotificationBanner: () -> Unit,
    val onPathClick: () -> Unit,
    val onPathDoubleClick: () -> Unit,
    val onPathLongClick: (name: String, cid: String) -> Unit,
    val onPathItemClick: (cid: String) -> Unit,
    val onRefresh: () -> Unit,
    val onItemClick: (Int) -> Unit,
    val onItemLongClick: (Int) -> Unit,
    val onCut: (Int) -> Unit,
    val onDelete: (Int) -> Unit,
    val onRename: (Int) -> Unit,
    val onFileInfo: (Int) -> Unit,
    val onAria2Download: (Int) -> Unit,
    val onForceOpen: (Int) -> Unit
)
