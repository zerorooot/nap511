package github.zerorooot.nap511.screen.file

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBannerActions
import github.zerorooot.nap511.bean.FileBannerState
import github.zerorooot.nap511.bean.FileContentActions
import github.zerorooot.nap511.bean.FileDisplayConfig
import github.zerorooot.nap511.bean.FileItemActions
import github.zerorooot.nap511.bean.FileListDataState
import github.zerorooot.nap511.bean.FileListScrollState
import github.zerorooot.nap511.bean.FilePathActions
import github.zerorooot.nap511.bean.FileScaffoldActions
import github.zerorooot.nap511.bean.FileScaffoldState
import github.zerorooot.nap511.bean.ForceOpenType
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.dialog.ForceOpenDialog
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.screen.components.AppBarAction
import github.zerorooot.nap511.screen.components.MenuItemAction
import github.zerorooot.nap511.screen.components.TopBarAction
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.FileOpener
import github.zerorooot.nap511.util.asScrollState
import github.zerorooot.nap511.util.copy
import github.zerorooot.nap511.util.isNotificationEnabled
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.cancelCut
import github.zerorooot.nap511.viewmodel.cut
import github.zerorooot.nap511.viewmodel.delete
import github.zerorooot.nap511.viewmodel.deleteMultiple
import github.zerorooot.nap511.viewmodel.getFileInfo
import github.zerorooot.nap511.viewmodel.getImage
import github.zerorooot.nap511.viewmodel.openAria2Dialog
import github.zerorooot.nap511.viewmodel.openCreateFolderDialog
import github.zerorooot.nap511.viewmodel.openFileOrderDialog
import github.zerorooot.nap511.viewmodel.openRenameFileDialog
import github.zerorooot.nap511.viewmodel.openSearchDialog
import github.zerorooot.nap511.viewmodel.openUnzipAllFileDialog
import github.zerorooot.nap511.viewmodel.removeFile
import github.zerorooot.nap511.viewmodel.startSendAria2Service
import github.zerorooot.nap511.viewmodel.unzipFile
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalCoilApi::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    settingUiState: SettingUiState,
    audioViewModel: AudioViewModel,
    isGridScreen: Boolean,
    gridCellMinSize: Dp,
    onNav: (Route) -> Unit,
    openDrawer: () -> Unit,
    drawerState: () -> Boolean,
    fileOpener: FileOpener
) {

    val fabPosition = when (settingUiState.fabPosition) {
        "Start" -> FabPosition.Start
        "Center" -> FabPosition.Center
        "End" -> FabPosition.End
        "EndOverlay" -> FabPosition.EndOverlay
        else -> FabPosition.End
    }

    val contentState by fileViewModel.contentState.collectAsStateWithLifecycle()
    val fileBeanList = contentState.fileBeanList
    val refreshing = contentState.isRefreshing
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
    LaunchedEffect(
        contentState.pathList,
        refreshing,
        fileBeanList,
        settingUiState.autoImagePreviewCount
    ) {
        val threshold = settingUiState.autoImagePreviewCount.toIntOrNull() ?: 0
        if (threshold > 0 && !refreshing) {
            val imageCount = fileBeanList.count { it.photoThumb.isNotEmpty() }
            isAutoImagePreview = (imageCount > threshold)
        }
    }

    LaunchedEffect(contentState.pathList) {
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

    val isIgnoringBatteryOptimizations by fileViewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
    val isBatteryBannerDismissed = settingUiState.hideBatteryBanner

    val batterySettingLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        fileViewModel.refreshBatteryOptimizations()
    }

    val listLocation = fileViewModel.getListLocation(contentState.currentCid)
    val listState = key(contentState.currentCid) {
        rememberLazyListState(
            listLocation.firstVisibleItemIndex, listLocation.firstVisibleItemScrollOffset
        )
    }
    val gridState = key(contentState.currentCid) {
        rememberLazyGridState(
            listLocation.firstVisibleItemIndex, listLocation.firstVisibleItemScrollOffset
        )
    }
    val staggeredGrid = key(contentState.currentCid) {
        rememberLazyStaggeredGridState(
            listLocation.firstVisibleItemIndex, listLocation.firstVisibleItemScrollOffset
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
    LaunchedEffect(isTopBarShow) {
        val window = (view.context as? Activity)?.window
        val insetsController =
            window?.let { WindowCompat.getInsetsController(it, window.decorView) }

        if (!isTopBarShow) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 嵌套滚动监听
    val nestedScrollConnection = rememberFileNestedScrollConnection(
        thresholdPx = thresholdPx,
        isPreviewActive = isPreviewActive,
        isBottomBarShow = isBottomBarShow,
        onTopBarShowChange = { isTopBarShow = it },
        onBottomBarShowChange = { isBottomBarShow = it })


    LaunchedEffect(fileViewModel) {
        fileViewModel.videoResultEvent.collect { index ->
            val state = when {
                isPreviewActive -> staggeredGrid.asScrollState
                isGridScreen -> gridState.asScrollState
                else -> listState.asScrollState
            }
            // 根据当前页面视图模式滚动，将当前行提前 x 行显示，使位置接近中央
            val scrollIndex = (index - 4).coerceAtLeast(0)
            state.animateScrollToItem(scrollIndex, 0)
        }
    }

    // 记录上次点击时间，使用 longArrayOf 避免无意义的重组
    val lastClickTime = remember { longArrayOf(0L) }


    val clickHandler = remember(
        fileViewModel,
        fileOpener,
        settingUiState,
        scope,
        isPreviewActive,
        isAutoImagePreview,
        isGridScreen,
        staggeredGrid,
        gridState,
        listState
    ) {
        FileClickHandler(
            fileViewModel = fileViewModel,
            fileOpener = fileOpener,
            settingUiState = settingUiState,
            coroutineScope = scope,
            isPreviewActive = isPreviewActive,
            isAutoImagePreview = isAutoImagePreview,
            isExpandedScreen = isGridScreen,
            staggeredGrid = staggeredGrid,
            gridState = gridState,
            listState = listState,
            onTopBarShowChange = { isTopBarShow = it },
            onBottomBarShowChange = { isBottomBarShow = it },
            lastClickTime = lastClickTime
        )
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
                    ForceOpenType.VIDEO -> clickHandler.handleVideoClick(bean)
                    ForceOpenType.AUDIO -> clickHandler.handleAudioClick(bean)
                    ForceOpenType.IMAGE -> clickHandler.handlePhotoClick(bean)
                    ForceOpenType.TEXT -> clickHandler.handleTextClick(bean)
                    ForceOpenType.WEB -> clickHandler.handleWebClick(bean)
                    ForceOpenType.ARCHIVE -> clickHandler.handleZipClick(bean)
                    ForceOpenType.TORRENT -> clickHandler.handleTorrentClick(bean)
                }
            }
        }
    }

    fun myItemOnClick(i: Int) = clickHandler.myItemOnClick(i)

    suspend fun scrollToTop() {
        when {
            isPreviewActive -> staggeredGrid.scrollToItem(0, 0)
            isGridScreen -> gridState.scrollToItem(0, 0)
            else -> listState.scrollToItem(0, 0)
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
        if (contentState.currentCid != "0" && !contentState.isLongClickState) {
            val currentCid = contentState.currentCid
            val state = when {
                isPreviewActive -> staggeredGrid.asScrollState
                isGridScreen -> gridState.asScrollState
                else -> listState.asScrollState
            }
            fileViewModel.setListLocation(currentCid, state)
        }
        isBottomBarShow = true
        isTopBarShow = true
        //触发路径和数据源的改变，重组后交由上方滚动
        fileViewModel.back()
    }

    BackHandler(
        contentState.currentCid != "0" || contentState.isLongClickState || contentState.isSearchState,
        ::onBack
    )

    fun refresh(forceCache: Boolean = false) {
        // Coil 内存与磁盘清理已全部收拢至 FileViewModel 的 IO 协程中执行，此处纯粹发起刷新
        fileViewModel.refresh(forceCache)
    }

    fun myAppBarOnClick(action: AppBarAction) {
        when (action) {
            TopBarAction.BACK -> {
                if (contentState.currentCid == "0" && !contentState.isLongClickState) {
                    openDrawer()
                    return
                }
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
                scope.launch {
                    scrollToTop()
                }
            }

            MenuItemAction.FILE_SORT -> fileViewModel.openFileOrderDialog()
            MenuItemAction.REFRESH_FILES -> {
                refresh(true)
            }

            else -> {}
        }
    }

    fun itemOnLongClick(i: Int) {
        if (!contentState.isLongClickState) {
            fileViewModel.startMultiSelect(i)
        } else {
            fileViewModel.clearSelection()
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
                val pathString = contentState.pathList.joinToString("/") { it.name }
                pathString.copy(context)
                App.instance.toast("$pathString 已复制到剪切板")
            },
            onPathDoubleClick = {
                scope.launch {
                    scrollToTop()
                }
            },
            onPathLongClick = { name, cid ->
                scope.launch {
                    SettingsRepository.saveData(ConfigKeyUtil.DEFAULT_OFFLINE_CID, cid)
                    val index = contentState.pathList.indexOfFirst { it.cid == cid }
                    val pathString = contentState.pathList.take(index + 1)
                        .joinToString(separator = "/") { it.name }
                    SettingsRepository.saveData(ConfigKeyUtil.DEFAULT_OFFLINE_PATH, pathString)
                }
                App.instance.toast("设置默认离线位置为: $name")
            },
            onPathItemClick = {
                if (it != contentState.currentCid) {
                    fileViewModel.getFiles(it)
                }
            }
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
                contentState.fileBeanList.getOrNull(index)?.let { fileBean ->
                    fileViewModel.openRenameFileDialog(fileBean)
                }
            },
            onFileInfo = { fileBean ->
                fileViewModel.getFileInfo(fileBean)
            },
            onUnzip = { fileBean ->
                if (fileBean.isFolder) {
                    App.instance.toast("不能解压文件夹！")
                    return@FileItemActions
                }
                if (fileBean.fileIco != R.drawable.zip) {
                    App.instance.toast("非压缩文件！")
                    return@FileItemActions
                }
                fileViewModel.unzipFile(fileBean)
            },
            onSetOfflineCid = { fileBean ->
                scope.launch {
                    SettingsRepository.saveData(
                        ConfigKeyUtil.DEFAULT_OFFLINE_CID, fileBean.categoryId
                    )
                    val pathString =
                        contentState.pathList.joinToString(separator = "/") { it.name } + "/${fileBean.name}"
                    SettingsRepository.saveData(ConfigKeyUtil.DEFAULT_OFFLINE_PATH, pathString)
                }
                App.instance.toast("设置默认离线位置为: ${fileBean.name}")
            },
            onAria2Download = ::onMenuAria2Download,
            onForceOpen = { showForceOpenDialog = it },
            onLoadImage = { fileBean ->
                fileViewModel.getImage(fileBean)
            })

        return FileContentActions(
            bannerActions = bannerActions, pathActions = pathActions, itemActions = itemActions
        )
    }

    val contentActions = remember(
        isGridScreen,
        isPreviewActive,
        staggeredGrid,
        gridState,
        listState,
        imageLoader,
        clipboardManager,
        scope,
        context,
        contentState
    ) {
        fileContentActions()
    }

    val scaffoldState = FileScaffoldState(
        isLongClickState = contentState.isLongClickState,
        appBarTitle = contentState.appBarTitle,
        isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
        isGridScreen = isGridScreen,
        isBottomBarShow = isBottomBarShow,
        isTopBarShow = isTopBarShow,
        hasCurrentMusic = audioViewModel.uiState.playback.currentMusic != null,
        isCutState = contentState.isCutState,
        fabPosition = fabPosition,
        nestedScrollConnection = nestedScrollConnection,
        currentCid = contentState.currentCid
    )

    val currentAppBarOnClick by rememberUpdatedState(::myAppBarOnClick)
    val scaffoldActions = remember {
        FileScaffoldActions(
            onAppBarClick = { currentAppBarOnClick(it) },
            onMusicDetailNav = { onNav(Route.MusicDetail) },
            onCancelCut = { fileViewModel.cancelCut() },
            onCutPaste = { fileViewModel.removeFile() },
            onAddFolder = { fileViewModel.openCreateFolderDialog() })
    }

    val contentDataState = FileListDataState(
        currentCid = contentState.currentCid,
        pathList = contentState.pathList,
        fileBeanList = fileBeanList,
        refreshing = refreshing,
        clickIndex = fileViewModel.clickMap.getOrDefault(contentState.currentCid, -1),
        imageCache = fileViewModel.imageBeanCache[contentState.currentCid]
    )

    val bannerState = FileBannerState(
        isNotificationEnabled = isNotificationEnabled,
        isNotificationBannerDismissed = isNotificationBannerDismissed,
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
        isBatteryBannerDismissed = isBatteryBannerDismissed
    )

    val displayConfig = FileDisplayConfig(
        isExpandedScreen = isGridScreen,
        isPreviewActive = isPreviewActive,
        isImageHdPreview = settingUiState.imageHdPreview,
        gridCellMinSize = gridCellMinSize
    )

    val listScrollState = FileListScrollState(
        listState = listState, gridState = gridState, staggeredGridState = staggeredGrid
    )

    FileScaffold(
        state = scaffoldState, actions = scaffoldActions, audioViewModel = audioViewModel
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