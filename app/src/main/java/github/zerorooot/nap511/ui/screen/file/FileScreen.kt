package github.zerorooot.nap511.ui.screen.file

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import github.zerorooot.nap511.ui.model.FileBannerActions
import github.zerorooot.nap511.ui.model.FileBannerState
import github.zerorooot.nap511.data.model.FileBean
import github.zerorooot.nap511.ui.model.FileContentActions
import github.zerorooot.nap511.ui.model.FileDisplayConfig
import github.zerorooot.nap511.ui.model.FileItemActions
import github.zerorooot.nap511.ui.model.FileItemUiModel
import github.zerorooot.nap511.ui.model.FileListDataState
import github.zerorooot.nap511.data.model.uniqueId
import github.zerorooot.nap511.ui.model.FileListScrollState
import github.zerorooot.nap511.ui.model.FileListSource
import github.zerorooot.nap511.ui.model.FilePathActions
import github.zerorooot.nap511.ui.model.FileScaffoldActions
import github.zerorooot.nap511.ui.model.FileScaffoldState
import github.zerorooot.nap511.data.model.ForceOpenType
import github.zerorooot.nap511.ui.navigation.Route
import github.zerorooot.nap511.ui.model.SettingUiState
import github.zerorooot.nap511.ui.dialog.ForceOpenDialog
import github.zerorooot.nap511.ui.components.common.AppBarAction
import github.zerorooot.nap511.ui.components.common.MenuItemAction
import github.zerorooot.nap511.ui.components.bar.TopBarAction
import github.zerorooot.nap511.platform.LocalToastProvider
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.ui.navigation.FileOpener
import github.zerorooot.nap511.util.asScrollState
import github.zerorooot.nap511.util.copy
import github.zerorooot.nap511.util.isNotificationEnabled
import github.zerorooot.nap511.ui.viewmodel.AudioViewModel
import github.zerorooot.nap511.ui.viewmodel.FileViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
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
    var pendingStateScrollToIndex by remember { mutableIntStateOf(-1) }
    val contentState by fileViewModel.contentState.collectAsStateWithLifecycle()
    val fileBeanList = contentState.fileBeanList
    val refreshing = contentState.isRefreshing
    val context = LocalContext.current
    val toastProvider = LocalToastProvider.current
    var showForceOpenDialogBean by remember { mutableStateOf<FileBean?>(null) }

    /**
     * 是否开启图片瀑布流模式
     * null: 使用自动判断逻辑(isAutoImagePreview)
     * true: 手动强制开启
     * false: 手动强制关闭
     */
    var isImagePreviewMode by rememberSaveable { mutableStateOf<Boolean?>(null) }

    // 用 derivedStateOf 解决翻页闪烁问题
    // 在翻页追加数据（Load More）的过程中，fileBeanList 包含的是“第一页 + 新加载页”的总列表，其中的图片数量是单调递增的（例如 15 张 -> 30 张）。
    // 只要初始第一页满足阈值（> threshold），在下滑翻页期间 isAutoImagePreview 的计算结果就会持续保持为 true，绝不会中途临时变为 false，
    // 从而彻底杜绝了退出重入的问题。
    val isAutoImagePreview by remember(
        contentState.source,
        fileBeanList,
        settingUiState.autoImagePreviewCount
    ) {
        derivedStateOf {
            val threshold = settingUiState.autoImagePreviewCount.toIntOrNull() ?: 0
            threshold > 0 && fileBeanList.count { it.photoThumb.isNotEmpty() } > threshold
        }
    }

    LaunchedEffect(contentState.source) {
        isImagePreviewMode = null
    }

    val isPreviewActive by rememberUpdatedState(isImagePreviewMode ?: isAutoImagePreview)

    // 优化：异步获取通知开关状态，首帧默认置为 true，避免主线程初次组合时执行 Binder IPC 跨进程查询
    var isNotificationEnabled by remember {
        mutableStateOf(true)
    }
    LaunchedEffect(Unit) {
        isNotificationEnabled = context.isNotificationEnabled()
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

    val listLocation = fileViewModel.getListLocation(contentState.source.paginationKey)
    val listState = key(contentState.source.paginationKey) {
        rememberLazyListState(
            listLocation.firstVisibleItemIndex, listLocation.firstVisibleItemScrollOffset
        )
    }
    val gridState = key(contentState.source.paginationKey) {
        rememberLazyGridState(
            listLocation.firstVisibleItemIndex, listLocation.firstVisibleItemScrollOffset
        )
    }
    val staggeredGrid = key(contentState.source.paginationKey) {
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


    val currentScrollState =
        remember(isPreviewActive, isGridScreen, staggeredGrid, gridState, listState) {
            when {
                isPreviewActive -> staggeredGrid.asScrollState
                isGridScreen -> gridState.asScrollState
                else -> listState.asScrollState
            }
        }

    // 采用标准 Compose snapshotFlow 惯用法监听当前视图滚动触底状态并触发加载更多
    LaunchedEffect(currentScrollState) {
        snapshotFlow { currentScrollState.isNearBottom }
            .distinctUntilChanged()
            .collect { isNearBottom ->
                if (isNearBottom && !contentState.isLongClickState) {
                    fileViewModel.loadMoreFiles()
                }
            }
    }

    LaunchedEffect(fileViewModel) {
        fileViewModel.videoResultEvent.collect { index ->
            // 根据当前页面视图模式滚动，使位置接近中央
            val visibleCount = currentScrollState.visibleItemsCount
            val halfCount = if (visibleCount > 0) visibleCount / 2 else 4
            val targetTopIndex = (index - halfCount).coerceAtLeast(0)
            pendingStateScrollToIndex = targetTopIndex
        }
    }

    LaunchedEffect(fileViewModel) {
        fileViewModel.scrollToTopEvent.collect {
            pendingStateScrollToIndex = 0
        }
    }
    // 此时 composition 已 apply、列表已用排序后的数据 measure
    LaunchedEffect(pendingStateScrollToIndex) {
        if (pendingStateScrollToIndex != -1) {
            currentScrollState.requestScrollToItem(pendingStateScrollToIndex, 0)
            pendingStateScrollToIndex = -1
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

    showForceOpenDialogBean?.let { bean ->
        if (bean.isFolder) {
            toastProvider.showToast("此功能仅支持文件，不支持文件夹")
            showForceOpenDialogBean = null
        } else {
            ForceOpenDialog(
                bean.name,
                onDismissRequest = { showForceOpenDialogBean = null },
            ) {
                fileViewModel.setRefreshingStatus(true)
                when (it) {
                    ForceOpenType.VIDEO -> clickHandler.handleVideoClick(bean, isForceOpen = true)
                    ForceOpenType.AUDIO -> clickHandler.handleAudioClick(bean)
                    ForceOpenType.IMAGE -> clickHandler.handlePhotoClick(bean, isForceOpen = true)
                    ForceOpenType.TEXT -> clickHandler.handleTextClick(bean, isForceOpen = true)
                    ForceOpenType.WEB -> clickHandler.handleWebClick(bean)
                    ForceOpenType.ARCHIVE -> clickHandler.handleZipClick(bean)
                    ForceOpenType.TORRENT -> clickHandler.handleTorrentClick(bean)
                }
            }
        }
    }

    fun myItemOnClick(fileBean: FileBean) {
        if (fileBean.isFolder) {
            isBottomBarShow = true
            isTopBarShow = true
        }
        clickHandler.myItemOnClick(fileBean)
    }


    fun onMenuAria2Download(fileBean: FileBean) {
        if (settingUiState.aria2Url.ifEmpty { ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE } == ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE) {
            fileViewModel.openAria2Dialog()
        } else {
            fileViewModel.startSendAria2Service(fileBean)
        }
    }

    // ============================================================
    // Phase 2.3: Extract onBackClick
    // ============================================================
    fun onBack() {
        if (drawerState.invoke()) {
            return
        }
        // 仅当处于普通目录浏览时才保存滚动偏移。若当前为搜索或过滤模式，严禁将搜索结果视口覆盖写入当前父目录的 listLocation！
        if (contentState.currentCid != "0" && !contentState.isLongClickState && !contentState.isQueryState) {
            fileViewModel.setListLocation(contentState.source.paginationKey, currentScrollState)
        }
        isBottomBarShow = true
        isTopBarShow = true
        //触发路径和数据源的改变，重组后交由上方滚动
        fileViewModel.back()
    }

    BackHandler(
        contentState.currentCid != "0" || contentState.isLongClickState || contentState.isQueryState,
        ::onBack
    )

    fun refresh(forceCache: Boolean = false) {
        // Coil 内存与磁盘清理已全部收拢至 FileViewModel 的 IO 协程中执行，此处纯粹发起刷新
        fileViewModel.refresh(forceCache)
    }

    fun myAppBarOnClick(action: AppBarAction) {
        when (action) {
            TopBarAction.BACK -> {
                if (contentState.currentCid == "0" && !contentState.isLongClickState && !contentState.isQueryState) {
                    openDrawer()
                    return
                }
                onBack()
            }

            TopBarAction.SEARCH -> {
                if (!contentState.isQueryState) {
                    fileViewModel.setListLocation(
                        contentState.source.paginationKey,
                        currentScrollState
                    )
                }
                fileViewModel.openSearchDialog()
            }

            TopBarAction.TOP -> {
                fileViewModel.topMultiple()
            }

            TopBarAction.STAR -> {
                fileViewModel.starMultiple()
            }

            TopBarAction.STAR_FILE_SCREEN -> {
                if (!contentState.isQueryState) {
                    fileViewModel.setListLocation(
                        contentState.source.paginationKey,
                        currentScrollState
                    )
                }
                fileViewModel.starFileScreen()
            }

            TopBarAction.SELECT_UP -> fileViewModel.selectToUp()
            TopBarAction.SELECT_DOWN -> fileViewModel.selectToDown()
            TopBarAction.CUT -> fileViewModel.cut()
            TopBarAction.DELETE -> fileViewModel.deleteMultiple()
            TopBarAction.SELECT_REVERSE -> fileViewModel.selectReverse()
            TopBarAction.TOGGLE_SELECT_ALL -> fileViewModel.toggleSelectAll()
            TopBarAction.UNZIP_ALL -> {
                fileViewModel.openUnzipAllFileDialog()
            }

            TopBarAction.BATCH_RENAME -> {
                fileViewModel.onMultiSelectRename()
            }

            MenuItemAction.GALLERY_MODE -> {
                isImagePreviewMode = !isPreviewActive
            }

            MenuItemAction.FILE_SORT -> {
                if (contentState.source is FileListSource.Search) {
                    toastProvider.showToast("搜索模式暂不支持排序")
                } else {
                    fileViewModel.openFileOrderDialog()
                }
            }

            MenuItemAction.REFRESH_FILES -> {
                refresh(true)
            }

            else -> {}
        }
    }

    fun itemOnLongClick(fileBean: FileBean) {
        if (!fileViewModel.isLongClickState) {
            fileViewModel.startMultiSelect(fileBean)
            if (!isTopBarShow) {
                isTopBarShow = true
            }
            if (!isBottomBarShow) {
                isBottomBarShow = true
            }
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
                fileViewModel.hideBatteryBanner()
            }
        )

        val pathActions = FilePathActions(
            onPathClick = {
                val state = fileViewModel.contentState.value
                val pathString = state.pathList.joinToString("/") { it.name }
                pathString.copy(context)
                toastProvider.showToast("$pathString 已复制到剪切板")
            },
            onPathDoubleClick = {
                scope.launch {
                    currentScrollState.animateScrollToItem(0, 0)
                }
            },
            onPathLongClick = { name, cid ->
                val state = fileViewModel.contentState.value
                val index = state.pathList.indexOfFirst { it.cid == cid }
                val pathString = state.pathList.take(index + 1)
                    .joinToString(separator = "/") { it.name }
                fileViewModel.setDefaultOfflinePath(name, cid, pathString)
            },
            onPathItemClick = { targetCid ->
                val state = fileViewModel.contentState.value
                if (targetCid != state.currentCid) {
                    if (state.currentCid != "0" && !state.isLongClickState && !state.isQueryState) {
                        fileViewModel.setListLocation(
                            state.source.paginationKey,
                            currentScrollState
                        )
                    }
                    fileViewModel.getFiles(targetCid)
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
            onRename = { fileBean ->
                fileViewModel.openRenameFileDialog(fileBean)
            },
            onFileInfo = { fileBean ->
                fileViewModel.getFileInfo(fileBean)
            },
            onTop = { fileBean ->
                fileViewModel.topFile(fileBean)
            },
            onStar = { fileBean ->
                fileViewModel.starFile(fileBean)
            },
            onUnzip = { fileBean ->
                if (fileBean.isFolder) {
                    toastProvider.showToast("不能解压文件夹！")
                    return@FileItemActions
                }
                if (fileBean.fileIco != R.drawable.zip) {
                    toastProvider.showToast("非压缩文件！")
                    return@FileItemActions
                }
                fileViewModel.unzipFile(fileBean)
            },
            onSetOfflineCid = { fileBean ->
                fileViewModel.setDefaultOfflinePath(fileBean)
            },
            onAria2Download = ::onMenuAria2Download,
            onForceOpen = { showForceOpenDialogBean = it },
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
        context
    ) {
        fileContentActions()
    }

    val scaffoldState = FileScaffoldState(
        isLongClickState = contentState.isLongClickState,
//        appBarTitle = contentState.appBarTitle,
        appBarTitle = when (val s = contentState.source) {
            is FileListSource.Folder -> contentState.pathList.lastOrNull()?.name ?: s.appName
            else -> s.appName
        },
        isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
        isGridScreen = isGridScreen,
        isBottomBarShow = isBottomBarShow,
        isTopBarShow = isTopBarShow,
        hasCurrentMusic = audioViewModel.uiState.playback.currentMusic != null,
        isCutState = contentState.isCutState,
        fabPosition = fabPosition,
        nestedScrollConnection = nestedScrollConnection,
        currentCid = contentState.currentCid,
        isQueryState = contentState.isQueryState,
        isAllSelected = contentState.isAllSelected,
        selectedCount = contentState.selectedCount
    )

    val scaffoldActions = remember(
        gridState,
        listState,
        staggeredGrid,
        contentState.source.paginationKey
    ) {
        FileScaffoldActions(
            onAppBarClick = { myAppBarOnClick(it) },
            onMusicDetailNav = { onNav(Route.MusicDetail) },
            onCancelCut = { fileViewModel.cancelCut() },
            onCutPaste = { fileViewModel.removeFile() },
            onAddFolder = { fileViewModel.openCreateFolderDialog() })
    }

    val fileItemList = remember(contentState.fileBeanList, contentState.selectedFileIds, contentState.highlightedFileId) {
        contentState.fileBeanList.map { bean ->
            val isSelected = bean.uniqueId in contentState.selectedFileIds
            FileItemUiModel(
                fileBean = bean,
                isSelected = isSelected,
                isHighlighted = (bean.uniqueId == contentState.highlightedFileId) && !isSelected
            )
        }
    }

    val contentDataState = FileListDataState(
        currentCid = contentState.currentCid,
        pathList = contentState.pathList,
        fileItemList = fileItemList,
        refreshing = refreshing,
        imageCache = fileViewModel.imageBeanCache[contentState.currentCid],
        paginationKey = contentState.source.paginationKey
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