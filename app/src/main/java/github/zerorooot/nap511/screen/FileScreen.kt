package github.zerorooot.nap511.screen

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
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
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.nativeClipboardManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import com.google.gson.Gson
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.FileBannerActions
import github.zerorooot.nap511.bean.FileBannerState
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileContentActions
import github.zerorooot.nap511.bean.FileDisplayConfig
import github.zerorooot.nap511.bean.FileItemActions
import github.zerorooot.nap511.bean.FileListDataState
import github.zerorooot.nap511.bean.FileListScrollState
import github.zerorooot.nap511.bean.FilePathActions
import github.zerorooot.nap511.bean.FileScaffoldActions
import github.zerorooot.nap511.bean.FileScaffoldState
import github.zerorooot.nap511.bean.ForceOpenType
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.dialog.ForceOpenDialog
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.screenitem.FileCellItem
import github.zerorooot.nap511.screenitem.ImageCellItem
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.isIgnoringBatteryOptimizations
import github.zerorooot.nap511.util.isNotificationEnabled
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.cancelCut
import github.zerorooot.nap511.viewmodel.cut
import github.zerorooot.nap511.viewmodel.delete
import github.zerorooot.nap511.viewmodel.deleteMultiple
import github.zerorooot.nap511.viewmodel.downloadText
import github.zerorooot.nap511.viewmodel.downloadWeb
import github.zerorooot.nap511.viewmodel.getFileInfo
import github.zerorooot.nap511.viewmodel.getImage
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
import my.nanihadesuka.compose.LazyVerticalStaggeredGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed as staggeredItemsIndexed

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class, ExperimentalCoilApi::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    settingUiState: SettingUiState,
    audioViewModel: AudioViewModel,
    isExpandedScreen: Boolean,
    gridCellMinSize: Dp,
    onNav: (Route) -> Unit,
    drawerState: () -> Boolean
) {
    val fileUiState by fileViewModel.uiState.collectAsStateWithLifecycle()

    val fabPosition = when (settingUiState.fabPosition) {
        "Start" -> FabPosition.Start
        "Center" -> FabPosition.Center
        "End" -> FabPosition.End
        "EndOverlay" -> FabPosition.EndOverlay
        else -> FabPosition.End
    }

    val fileBeanList = fileViewModel.fileBeanList
    val path = fileUiState.path
    val refreshing = fileUiState.isRefreshing
    val context = LocalContext.current
    var showForceOpenDialog by rememberSaveable { mutableIntStateOf(-1) }

    /**
     * 是否开启图片瀑布流模式
     * null: 使用自动判断逻辑(isAutoImagePreview)
     * true: 手动强制开启
     * false: 手动强制关闭
     */
    var isImagePreviewMode by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var isAutoImagePreview by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(path, refreshing, fileBeanList.toList(), settingUiState.autoImagePreviewCount) {
        val threshold = settingUiState.autoImagePreviewCount.toIntOrNull() ?: 0
        if (threshold > 0 && !refreshing) {
            val imageCount = fileBeanList.count { it.photoThumb.isNotEmpty() }
            isAutoImagePreview = (imageCount > threshold)
        }
    }

    LaunchedEffect(path) {
        isImagePreviewMode = null
    }

    val isPreviewActive by rememberUpdatedState(isImagePreviewMode ?: isAutoImagePreview)

    var isNotificationEnabled by remember {
        mutableStateOf(context.isNotificationEnabled())
    }
    var isNotificationBannerDismissed by rememberSaveable { mutableStateOf(false) }

    val notificationSettingLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isNotificationEnabled = context.isNotificationEnabled()
    }

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations())
    }
    val isBatteryBannerDismissed = settingUiState.hideBatteryBanner

    val batterySettingLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations()
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
    val staggeredGrid = key(path) {
        rememberLazyStaggeredGridState(
            listLocation.firstVisibleItemIndex,
            listLocation.firstVisibleItemScrollOffset
        )
    }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    val imageLoader = context.imageLoader

    val density = LocalDensity.current
    // 1. 设置 35dp 的防抖阈值
    val thresholdPx = rememberSaveable(density) { with(density) { 35.dp.toPx() } }
    var isBottomBarShow by rememberSaveable { mutableStateOf(true) }
    var isTopBarShow by rememberSaveable { mutableStateOf(true) }

    val view = LocalView.current
    DisposableEffect(isTopBarShow) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }

        if (!isTopBarShow) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

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
                if (accumulatedDelta < -thresholdPx) {
                    if (isPreviewActive) {
                        isTopBarShow = false
                    }
                    if (isBottomBarShow) {
                        isBottomBarShow = false
                    }
                }
                if (accumulatedDelta > thresholdPx) {
                    if (isPreviewActive) {
                        isTopBarShow = true
                    }
                    if (!isBottomBarShow) {
                        isBottomBarShow = true
                    }
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
        if (settingUiState.earlyLoading) {
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
        val txtSize = settingUiState.txtSize.toIntOrNull() ?: 200
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
            when {
                isPreviewActive -> fileViewModel.setListLocationAndClickCache(i, staggeredGrid)
                isExpandedScreen -> fileViewModel.setListLocationAndClickCache(i, gridState)
                else -> fileViewModel.setListLocationAndClickCache(i, listState)
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

    fun scrollToTop() {
        when {
            isPreviewActive -> staggeredGrid.requestScrollToItem(0, 0)
            isExpandedScreen -> gridState.requestScrollToItem(0, 0)
            else -> listState.requestScrollToItem(0, 0)
        }
    }

    fun onMenuAria2Download(index: Int) {
        if (settingUiState.aria2Url.ifEmpty { ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE } == ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE) {
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
            when {
                isPreviewActive -> fileViewModel.setListLocation(path, staggeredGrid)
                isExpandedScreen -> fileViewModel.setListLocation(path, gridState)
                else -> fileViewModel.setListLocation(path, listState)
            }
        }
        isBottomBarShow = true
        isTopBarShow = true
        //触发路径和数据源的改变，重组后交由上方滚动
        fileViewModel.back()
    }

    BackHandler(
        path != "/根目录" || fileViewModel.isLongClickState || fileViewModel.isSearchState,
        ::onBack
    )

    fun refresh(forceCache: Boolean = false) {
        if (forceCache) {
            fileBeanList.forEach { fileBean ->
                //文件列表的里图片，ico、thumb图片
                imageLoader.memoryCache?.remove(MemoryCache.Key(fileBean.fileId))
                imageLoader.diskCache?.remove(fileBean.fileId)
                //MyPhotoScreen、大图模式高清模式的图片
                imageLoader.memoryCache?.remove(MemoryCache.Key(fileBean.pickCode))
                imageLoader.diskCache?.remove(fileBean.pickCode)
            }
        }
        fileViewModel.refresh(forceCache)
    }

    fun myAppBarOnClick(action: AppBarAction) {
        when (action) {
            TopBarAction.BACK -> {
                onBack()
            }

            TopBarAction.SEARCH -> {
                fileViewModel.openSearchDialog()
            }

            TopBarAction.SELECT_UP -> fileViewModel.selectToUp()
            TopBarAction.SELECT_DOWN -> fileViewModel.selectToDown()
            TopBarAction.CUT -> fileViewModel.cut()
            TopBarAction.DELETE -> fileViewModel.deleteMultiple()
            TopBarAction.SELECT_REVERSE -> fileViewModel.selectReverse()
            TopBarAction.UNZIP_ALL -> {
                fileViewModel.openUnzipAllFileDialog()
            }

            MenuItemAction.GALLERY_MODE -> {
                isImagePreviewMode = !isPreviewActive
            }

            MenuItemAction.VIDEO_SCHEDULE -> {
                fileViewModel.sortByVideoTime()
                scrollToTop()
            }

            MenuItemAction.FILE_SORT -> fileViewModel.openFileOrderDialog()
            MenuItemAction.REFRESH_FILES -> {
                refresh(true)
            }

            else -> {}
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


    fun fileContentActions(): FileContentActions {
        val bannerActions = FileBannerActions(
            onOpenNotificationSettings = {
                val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                    putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                }
                notificationSettingLauncher.launch(intent)
            },
            onDismissNotificationBanner = { isNotificationBannerDismissed = true },
            onOpenBatterySettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                }
                batterySettingLauncher.launch(intent)
            },
            onDismissBatteryBanner = {
                scope.launch {
                    SettingsRepository.saveData(ConfigKeyUtil.HIDE_BATTERY_BANNER, true)
                }
            }
        )

        val pathActions = FilePathActions(
            onPathClick = {
                clipboardManager.nativeClipboardManager.setPrimaryClip(
                    ClipData.newPlainText("path", path)
                )
                App.instance.toast("$path 已复制到剪切板")
            },
            onPathDoubleClick = {
                scope.launch {
                    scrollToTop()
                }
            },
            onPathLongClick = { name, cid ->
                scope.launch {
                    SettingsRepository.saveData(ConfigKeyUtil.DEFAULT_OFFLINE_CID, cid)
                    val index = fileViewModel.pathList.indexOfFirst { it.cid == cid }
                    val pathString = fileViewModel.pathList.take(index + 1)
                        .joinToString(separator = "/") { it.name }
                    SettingsRepository.saveData(ConfigKeyUtil.DEFAULT_OFFLINE_PATH, pathString)
                }
                App.instance.toast("设置默认离线位置为: $name")
            },
            onPathItemClick = { fileViewModel.getFiles(it) }
        )

        val itemActions = FileItemActions(
            onRefresh = {
                refresh()
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
            onForceOpen = { showForceOpenDialog = it },
            onLoadImage = { fileBean ->
                fileViewModel.getImage(fileBean)
            }
        )

        return FileContentActions(
            bannerActions = bannerActions,
            pathActions = pathActions,
            itemActions = itemActions
        )
    }

    val contentActions = remember(
        path,
        isExpandedScreen,
        isPreviewActive,
        staggeredGrid,
        gridState,
        listState,
        imageLoader,
        clipboardManager,
        scope,
        context
    ) {
        fileContentActions()
    }

    val scaffoldState = FileScaffoldState(
        isLongClickState = fileViewModel.isLongClickState,
        appBarTitle = fileViewModel.appBarTitle,
        isExpandedScreen = isExpandedScreen,
        isBottomBarShow = isBottomBarShow,
        isTopBarShow = isTopBarShow,
        hasCurrentMusic = audioViewModel.currentMusic != null,
        isCutState = fileViewModel.isCutState,
        fabPosition = fabPosition,
        nestedScrollConnection = nestedScrollConnection,
        audioViewModel = audioViewModel
    )

    val scaffoldActions = remember {
        FileScaffoldActions(
            onAppBarClick = ::myAppBarOnClick,
            onMusicDetailNav = { onNav(Route.MusicDetail) },
            onCancelCut = { fileViewModel.cancelCut() },
            onCutPaste = { fileViewModel.removeFile() },
            onAddFolder = { fileViewModel.openCreateFolderDialog() }
        )
    }

    val contentDataState = FileListDataState(
        path = path,
        pathList = fileViewModel.pathList,
        fileBeanList = fileBeanList,
        refreshing = refreshing,
        clickIndex = fileViewModel.clickMap.getOrDefault(path, -1),
        imageCache = fileViewModel.imageBeanCache[fileViewModel.currentCid]
    )

    val bannerState = FileBannerState(
        isNotificationEnabled = isNotificationEnabled,
        isNotificationBannerDismissed = isNotificationBannerDismissed,
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
        isBatteryBannerDismissed = isBatteryBannerDismissed
    )

    val displayConfig = FileDisplayConfig(
        isExpandedScreen = isExpandedScreen,
        isPreviewActive = isPreviewActive,
        isImageHdPreview = settingUiState.imageHdPreview,
        gridCellMinSize = gridCellMinSize
    )

    val listScrollState = FileListScrollState(
        listState = listState,
        gridState = gridState,
        staggeredGridState = staggeredGrid
    )

    FileScaffold(
        state = scaffoldState,
        actions = scaffoldActions
    ) { innerPadding ->
        FileScreenContent(
            innerPadding = innerPadding,
            dataState = contentDataState,
            bannerState = bannerState,
            displayConfig = displayConfig,
            scrollState = listScrollState,
            actions = contentActions,
            isTopBarShow = isTopBarShow
        )
    }
}


@Composable
private fun FileScaffold(
    state: FileScaffoldState,
    actions: FileScaffoldActions,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        //直接设置
        // contentWindowInsets = WindowInsets(0, 0, 0, 0) ,
        // 会彻底清空 所有方向（上、下、左、右） 的系统安全边距（System Insets）
        //造成：1、底部导航栏/手势条重叠（Bottom Insets 丢失）；2、横屏及左右安全边距丢失（Horizontal Insets 丢失）；3、软键盘自动弹起避让失效（IME Insets 丢失）
        contentWindowInsets = if (state.isTopBarShow) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            // 仅在隐藏控制栏时排除 Top 边距，保留 Bottom（底部导航栏/手势）和 Horizontal（左右）
            ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
            )
        },
        topBar = {
            AnimatedVisibility(
                visible = state.isTopBarShow,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                AnimatedContent(
                    targetState = state.isLongClickState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = ""
                ) { isLongClick ->
                    if (isLongClick) {
                        AppTopBarMultiple(
                            title = state.appBarTitle,
                            isExpandedScreen = state.isExpandedScreen,
                            onClick = actions.onAppBarClick
                        )
                    } else {
                        AppTopBarNormal(state.appBarTitle, actions.onAppBarClick)
                    }
                }
            }
        },
        modifier = modifier.nestedScroll(state.nestedScrollConnection),
        bottomBar = {
            AnimatedVisibility(
                visible = state.hasCurrentMusic && state.isBottomBarShow,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                MiniPlayerBar(audioViewModel = state.audioViewModel) {
                    actions.onMusicDetailNav()
                }
            }
        },
        floatingActionButton = {
            FileScreenFab(
                isCutState = state.isCutState,
                visible = state.isBottomBarShow,
                onCancelCut = actions.onCancelCut,
                onCutPaste = actions.onCutPaste,
                onAddFolder = actions.onAddFolder
            )
        },
        floatingActionButtonPosition = state.fabPosition,
        content = content
    )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun FileScreenContent(
    innerPadding: PaddingValues,
    dataState: FileListDataState,
    bannerState: FileBannerState,
    displayConfig: FileDisplayConfig,
    scrollState: FileListScrollState,
    actions: FileContentActions,
    modifier: Modifier = Modifier,
    isTopBarShow: Boolean = true
) {
    val showNotificationBanner =
        !bannerState.isNotificationEnabled && !bannerState.isNotificationBannerDismissed
    val showBatteryBanner =
        !showNotificationBanner && !bannerState.isIgnoringBatteryOptimizations && !bannerState.isBatteryBannerDismissed

    Column(
        modifier = modifier
            .padding(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            )
            .consumeWindowInsets(innerPadding)
    ) {
        AnimatedVisibility(
            visible = showNotificationBanner || showBatteryBanner,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            if (showNotificationBanner) {
                NotificationPermissionBanner(
                    text = "未开启通知权限，可能无法及时收到离线下载提醒",
                    icon = Icons.Default.Notifications,
                    onOpenSettings = actions.bannerActions.onOpenNotificationSettings,
                    onDismiss = actions.bannerActions.onDismissNotificationBanner
                )
            } else if (showBatteryBanner) {
                NotificationPermissionBanner(
                    text = "未允许无限制后台运行，后台解压可能会暂停",
                    icon = Icons.Default.BatteryAlert,
                    onOpenSettings = actions.bannerActions.onOpenBatterySettings,
                    onDismiss = actions.bannerActions.onDismissBatteryBanner
                )
            }
        }

        AnimatedVisibility(
            visible = isTopBarShow,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            FilePathBar(
                pathList = dataState.pathList,
                actions = actions.pathActions
            )
        }

        FileListContent(
            dataState = dataState,
            displayConfig = displayConfig,
            scrollState = scrollState,
            itemActions = actions.itemActions
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilePathBar(
    pathList: List<PathBean>,
    actions: FilePathActions,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // 路径变化时自动滚动到最右侧末尾
    LaunchedEffect(pathList.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = actions.onPathClick,
                onDoubleClick = actions.onPathDoubleClick,
                onLongClick = {
                    val path = pathList.last()
                    actions.onPathLongClick.invoke(path.name, path.cid)
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
                                onClick = { actions.onPathItemClick(path.cid) },
                                onLongClick = {
                                    actions.onPathLongClick.invoke(
                                        path.name,
                                        path.cid
                                    )
                                }
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
    dataState: FileListDataState,
    displayConfig: FileDisplayConfig,
    scrollState: FileListScrollState,
    itemActions: FileItemActions,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = dataState.refreshing,
        onRefresh = itemActions.onRefresh,
        modifier = modifier
    ) {
        if (dataState.fileBeanList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无文件")
            }
        } else {
            key(dataState.path, displayConfig.isPreviewActive) {
                if (displayConfig.isPreviewActive) {
                    LazyVerticalStaggeredGridScrollbar(
                        state = scrollState.staggeredGridState,
                        settings = ScrollbarSettings.Default.copy(
                            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        LazyVerticalStaggeredGrid(
                            state = scrollState.staggeredGridState,
                            columns = StaggeredGridCells.Adaptive(minSize = displayConfig.gridCellMinSize),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            staggeredItemsIndexed(
                                items = dataState.fileBeanList,
                                key = { _, item ->
                                    item.fileId.ifEmpty { item.pickCode.ifEmpty { item.photoThumb } }
                                },
                            ) { index, item ->
                                val imageBean = dataState.imageCache?.get(item.pickCode)
                                ImageCellItem(
                                    fileBean = item,
                                    index = index,
                                    clickIndex = dataState.clickIndex,
                                    imageBean = imageBean,
                                    isImageHdPreview = displayConfig.isImageHdPreview,
                                    onLoadImage = itemActions.onLoadImage,
                                    modifier = Modifier, // 瀑布流快速滑动时不施加 animateItem 动画，防止布局重新计算时元素跳动
                                    itemOnClick = itemActions.onItemClick,
                                    itemOnLongClick = itemActions.onItemLongClick
                                )
                            }
                        }
                    }
                } else if (displayConfig.isExpandedScreen) {
                    LazyVerticalGridScrollbar(
                        state = scrollState.gridState,
                        settings = ScrollbarSettings.Default.copy(
                            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        LazyVerticalGrid(
                            state = scrollState.gridState,
                            columns = GridCells.Adaptive(minSize = displayConfig.gridCellMinSize),
//                            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItemsIndexed(
                                items = dataState.fileBeanList,
                                key = { _, item ->
                                    item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
                                },
                            ) { index, item ->
                                FileCellItem(
                                    fileBean = item,
                                    index = index,
                                    clickIndex = dataState.clickIndex,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null
                                    ),
                                    itemOnClick = itemActions.onItemClick,
                                    itemOnLongClick = itemActions.onItemLongClick,
                                    onCut = itemActions.onCut,
                                    onDelete = itemActions.onDelete,
                                    onRename = itemActions.onRename,
                                    onFileInfo = itemActions.onFileInfo,
                                    onForceOpen = itemActions.onForceOpen,
                                    onAria2Download = itemActions.onAria2Download
                                )
                            }
                        }
                    }
                } else {
                    LazyColumnScrollbar(
                        state = scrollState.listState,
                        settings = ScrollbarSettings.Default.copy(
                            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = scrollState.listState,
//                            contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
                        ) {
                            itemsIndexed(
                                items = dataState.fileBeanList,
                                key = { _, item ->
                                    item.fileId.ifEmpty { item.pickCode.ifEmpty { item.uuid.toString() } }
                                },
                            ) { index, item ->
                                FileCellItem(
                                    fileBean = item,
                                    index = index,
                                    clickIndex = dataState.clickIndex,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null
                                    ),
                                    itemOnClick = itemActions.onItemClick,
                                    itemOnLongClick = itemActions.onItemLongClick,
                                    onCut = itemActions.onCut,
                                    onDelete = itemActions.onDelete,
                                    onRename = itemActions.onRename,
                                    onFileInfo = itemActions.onFileInfo,
                                    onForceOpen = itemActions.onForceOpen,
                                    onAria2Download = itemActions.onAria2Download
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
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Notifications,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = text,
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
