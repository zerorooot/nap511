package github.zerorooot.nap511.screen.file

import android.app.Activity
import android.content.ClipData
import android.content.Intent
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
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
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
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.dialog.ForceOpenDialog
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.screen.components.AppBarAction
import github.zerorooot.nap511.screen.components.MenuItemAction
import github.zerorooot.nap511.screen.components.TopBarAction
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
import github.zerorooot.nap511.viewmodel.updateVideoFileBeans
import kotlinx.coroutines.launch
import java.lang.reflect.Type

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class, ExperimentalCoilApi::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    settingUiState: SettingUiState,
    audioViewModel: AudioViewModel,
    isGridScreen: Boolean,
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

    var isIgnoringBatteryOptimizations by rememberSaveable {
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

    // 嵌套滚动监听
    val nestedScrollConnection = rememberFileNestedScrollConnection(
        thresholdPx = thresholdPx,
        isPreviewActive = isPreviewActive,
        isBottomBarShow = isBottomBarShow,
        onTopBarShowChange = { isTopBarShow = it },
        onBottomBarShowChange = { isBottomBarShow = it }
    )

    val videoActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK) {
            val videoHistoryJson = data?.getStringExtra("videoHistory") ?: "{}"
            val type: Type = object : TypeToken<MutableMap<String, VideoBean>>() {}.type
            val videoHistoryMap: MutableMap<String, VideoBean> =
                Gson().fromJson(videoHistoryJson, type) ?: mutableMapOf()

            if (videoHistoryMap.isNotEmpty()) {
                fileViewModel.updateVideoFileBeans(
                    fileViewModel.currentCid,
                    videoHistoryMap
                )
            }
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
        fileViewModel.launchVideoEvent.collect { launchVideoParams ->
            val launchVideoParamsJson =
                Gson().toJson(launchVideoParams, LaunchVideoParams::class.java)
            val intent = Intent(context, VideoActivity::class.java).apply {
                putExtra("bean", launchVideoParamsJson)
            }
            videoActivityLauncher.launch(intent)
        }
    }

    // 记录上次点击时间，使用 longArrayOf 避免无意义的重组
    val lastClickTime = remember { longArrayOf(0L) }
    val clickHandler = remember(
        fileViewModel,
        audioViewModel,
        settingUiState,
        isPreviewActive,
        isGridScreen,
        staggeredGrid,
        gridState,
        listState
    ) {
        FileClickHandler(
            fileViewModel = fileViewModel,
            audioViewModel = audioViewModel,
            settingUiState = settingUiState,
            onNav = onNav,
            isPreviewActive = isPreviewActive,
            isExpandedScreen = isGridScreen,
            staggeredGrid = staggeredGrid,
            gridState = gridState,
            listState = listState,
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
                    ForceOpenType.TEXT -> clickHandler.handleTextClick(showForceOpenDialog, bean)
                    ForceOpenType.WEB -> clickHandler.handleWebClick(showForceOpenDialog, bean)
                    ForceOpenType.ARCHIVE -> clickHandler.handleZipClick(showForceOpenDialog)
                    ForceOpenType.TORRENT -> clickHandler.handleTorrentClick(bean)
                }
            }
        }
    }

    fun myItemOnClick(i: Int) = clickHandler.myItemOnClick(i)

    fun scrollToTop() {
        when {
            isPreviewActive -> staggeredGrid.requestScrollToItem(0, 0)
            isGridScreen -> gridState.requestScrollToItem(0, 0)
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
                isGridScreen -> fileViewModel.setListLocation(path, gridState)
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
                        ConfigKeyUtil.DEFAULT_OFFLINE_CID,
                        fileBean.categoryId
                    )
                    val pathString =
                        fileViewModel.pathList.joinToString(separator = "/") { it.name } + "/${fileBean.name}"
                    SettingsRepository.saveData(ConfigKeyUtil.DEFAULT_OFFLINE_PATH, pathString)
                }
                App.instance.toast("设置默认离线位置为: ${fileBean.name}")
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
        isLongClickState = fileViewModel.isLongClickState,
        appBarTitle = fileViewModel.appBarTitle,
        isExpandedScreen = isGridScreen,
        isBottomBarShow = isBottomBarShow,
        isTopBarShow = isTopBarShow,
        hasCurrentMusic = audioViewModel.uiState.playback.currentMusic != null,
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
        isExpandedScreen = isGridScreen,
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