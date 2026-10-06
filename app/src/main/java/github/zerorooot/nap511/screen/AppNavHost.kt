package github.zerorooot.nap511.screen

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.util.AudioPlayerController
import github.zerorooot.nap511.util.FileDialogController
import github.zerorooot.nap511.util.FileOpener
import github.zerorooot.nap511.util.MediaViewerStateHolder
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.dialog.ExitApp
import github.zerorooot.nap511.screen.auth.LoginScreen
import github.zerorooot.nap511.screen.file.FileScreen
import github.zerorooot.nap511.screen.file.OfflineDownloadScreen
import github.zerorooot.nap511.screen.file.OfflineFileScreen
import github.zerorooot.nap511.screen.file.RecycleScreen
import github.zerorooot.nap511.screen.file.RepeatFileScreen
import github.zerorooot.nap511.screen.file.TaskDetailPane
import github.zerorooot.nap511.screen.setting.SettingScreen
import github.zerorooot.nap511.screen.viewer.MusicDetailScreen
import github.zerorooot.nap511.screen.viewer.MyPhotoScreen
import github.zerorooot.nap511.screen.viewer.TxtReaderScreen
import github.zerorooot.nap511.screen.web.CaptchaVideoWebViewScreen
import github.zerorooot.nap511.screen.web.CaptchaWebViewScreen
import github.zerorooot.nap511.screen.web.HtmlWebViewScreen
import github.zerorooot.nap511.screen.web.WebViewScreen
import github.zerorooot.nap511.terminal.ui.TerminalScreen
import github.zerorooot.nap511.terminal.viewmodel.TerminalViewModel
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.LoginViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.RepeatFileViewModel
import github.zerorooot.nap511.viewmodel.SettingViewModel
import github.zerorooot.nap511.viewmodel.getImage
import github.zerorooot.nap511.viewmodel.getTorrentTask
import github.zerorooot.nap511.viewmodel.getZipListFile

/**
 * 应用全局根导航容器 (AppNavHost)
 *
 * 基于 Jetpack Navigation 3 (Nav3) 构建：
 * - 使用强类型 [NavKey] 替代旧版基于 URI/字符串的 NavGraph；
 * - 使用 [NavDisplay] 与 [entryProvider] 注册各个目标页面的渲染逻辑；
 * - 集中处理各页面的手势开启/禁用、深层链接、文件点击及全局抽屉联动。
 */
@Composable
fun AppNavHost(
    backStack: NavBackStack<NavKey>,
    onNavigate: (Route) -> Unit,
    onPopBack: () -> Unit,
    avatarBean: AvatarBean,
    fileViewModel: FileViewModel,
    offlineFileViewModel: OfflineFileViewModel,
    recycleViewModel: RecycleViewModel,
    audioViewModel: AudioViewModel,
    repeatViewModel: RepeatFileViewModel,
    settingViewModel: SettingViewModel,
    loginViewModel: LoginViewModel,
    terminalViewModel: TerminalViewModel,
    uiState: SettingUiState,
    isDrawerOpen: () -> Boolean,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSetGesturesEnabled: (Boolean) -> Unit
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isExpandedScreen =
        uiState.expandedScreenEnabled && windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val gridCellMinSize =
        (uiState.gridCellMinSize.toIntOrNull()?.takeIf { i -> i > 0 } ?: 340).dp
    val isGridScreen = uiState.gridScreenEnabled
    val context = LocalContext.current
    val mediaViewerStateHolder = remember { MediaViewerStateHolder() }
    val audioPlayerController = remember(audioViewModel) {
        object : AudioPlayerController {
            override fun playAudio(fileBean: FileBean, localSubtitles: List<SubtitleItem>) {
                audioViewModel.playAudio(fileBean, localSubtitles)
            }
            override fun pauseAudio() {
                audioViewModel.pause()
            }
        }
    }
    val fileDialogController = remember(fileViewModel) {
        object : FileDialogController {
            override fun openTorrent(fileBean: FileBean) {
                fileViewModel.getTorrentTask(fileBean.sha1)
            }
            override fun openZip(fileBean: FileBean) {
                val index = fileViewModel.fileBeanList.indexOfFirst { it.pickCode == fileBean.pickCode }
                if (index >= 0) {
                    fileViewModel.selectIndex = index
                }
                fileViewModel.getZipListFile()
            }
        }
    }
    val fileOpener = remember(mediaViewerStateHolder, audioPlayerController, fileDialogController, uiState, fileViewModel, onNavigate) {
        FileOpener(
            context = context,
            mediaViewerStateHolder = mediaViewerStateHolder,
            audioPlayerController = audioPlayerController,
            fileDialogController = fileDialogController,
            settingUiState = { uiState },
            onOpenFolder = { cid ->
                fileViewModel.getFiles(cid)
            },
            onNavigate = onNavigate
        )
    }

    NavDisplay(
        backStack = backStack,
        onBack = onPopBack,
        entryProvider = entryProvider {
            entry<Route.Login> {
                onSetGesturesEnabled(false)
                LoginScreen { credential ->
                    loginViewModel.performLogin(credential) {
                        fileViewModel.getRemainingSpace()
                        fileViewModel.getFiles("0")
                        onNavigate(Route.MyFile)
                    }
                }
            }

            entry<Route.MyFile> {
                onSetGesturesEnabled(true)
                FileScreen(
                    fileViewModel = fileViewModel,
                    settingUiState = uiState,
                    audioViewModel = audioViewModel,
                    isGridScreen = isGridScreen,
                    gridCellMinSize = gridCellMinSize,
                    onNav = { route -> onNavigate(route) },
                    openDrawer = {
                        onSetGesturesEnabled(true)
                        onOpenDrawer()
                    },
                    drawerState = {
                        val open = isDrawerOpen()
                        if (open) {
                            onCloseDrawer()
                        }
                        open
                    },
                    fileOpener = fileOpener
                )
            }

            entry<Route.OfflineDownload> {
                LaunchedEffect(Unit) {
                    offlineFileViewModel.quota()
                }

                val path = "/" + fileViewModel.pathList.joinToString("/") { it.name }
                val quotaBean by offlineFileViewModel.quotaBean.collectAsStateWithLifecycle()
                val urlText by offlineFileViewModel.urlText

                OfflineDownloadScreen(
                    path = path,
                    quotaBean = quotaBean,
                    url = urlText,
                    onClick = { onOpenDrawer() }
                ) { list ->
                    offlineFileViewModel.addTask(
                        list,
                        fileViewModel.currentCid,
                        path.substringAfterLast("/")
                    ) { needVerify ->
                        if (needVerify) {
                            onNavigate(Route.VerifyMagnetLinkAccount)
                        }
                    }
                }
            }

            entry<Route.OfflineList> {
                LaunchedEffect(Unit) {
                    offlineFileViewModel.getOfflineFileList()
                }
                OfflineFileScreen(
                    offlineFileViewModel = offlineFileViewModel,
                    isExpandedScreen = isExpandedScreen,
                    isGridScreen = isGridScreen,
                    gridCellMinSize = gridCellMinSize,
                    getFiles = {
                        fileViewModel.getFiles(it)
                        onNavigate(Route.MyFile)
                    },
                    onNavigate = onNavigate,
                    onClick = onOpenDrawer
                )
            }
            entry<Route.OfflineFileInfoDialog> {
                val task = it.task
                TaskDetailPane(
                    task,
                    onDeleteTask = { offlineTask: OfflineTask ->
                        offlineFileViewModel.delete(offlineTask)
                        onPopBack()
                    },
                    onOpenFile = {
                        fileViewModel.getFiles(task.fileId.ifEmpty { task.wpPathId })
                        onNavigate(Route.MyFile)
                    },
                    onBack = onPopBack
                )
            }

            entry<Route.WebScreen> {
                onSetGesturesEnabled(false)
                WebViewScreen {
                    onOpenDrawer()
                }
            }

            entry<Route.AdvancedSettings> {
                SettingScreen(
                    viewModel = settingViewModel,
                    isExpandedScreen = isExpandedScreen,
                    onDrawerClick = onOpenDrawer,
                    onActionClick = { action ->
                        when (action) {
                            "topAppBarActionButtonOnClick" -> onOpenDrawer()
                            "VerifyVideoAccount" -> onNavigate(Route.VerifyVideoAccount)
                            "VerifyMagnetLinkAccount" -> onNavigate(Route.VerifyMagnetLinkAccount)
                            "handleOfflineTask" -> fileViewModel.handleOfflineTask(true)
                            "RepeatFile" -> onNavigate(Route.RepeatFile)
                            "Login" -> onNavigate(Route.Login)
                            "Web" -> onNavigate(Route.WebScreen)
                            "Terminal" -> onNavigate(Route.Terminal)
                        }
                    }
                )
            }

            entry<Route.Terminal> {
                onSetGesturesEnabled(false)
                terminalViewModel.initDirectoryIfNeeded(fileViewModel.pathList)
                terminalViewModel.updateFileOpener(fileOpener)
                terminalViewModel.avatarBean = avatarBean
                TerminalScreen(
                    viewModel = terminalViewModel,
                    onBack = onPopBack,
                    openDrawer = {
                        onSetGesturesEnabled(true)
                        onOpenDrawer()
                    }
                )
            }

            entry<Route.RecycleBin> {
                RecycleScreen(recycleViewModel, isGridScreen, gridCellMinSize) {
                    onOpenDrawer()
                }
            }

            entry<Route.VerifyMagnetLinkAccount> {
                CaptchaWebViewScreen({ fileViewModel.handleOfflineTask() }) { action ->
                    when (action) {
                        "topAppBarActionButtonOnClick" -> onOpenDrawer()
                        "select" -> onNavigate(Route.MyFile)
                    }
                }
            }

            entry<Route.VerifyVideoAccount> {
                CaptchaVideoWebViewScreen { action ->
                    when (action) {
                        "topAppBarActionButtonOnClick" -> onOpenDrawer()
                        "select" -> onNavigate(Route.MyFile)
                    }
                }
            }

            entry<Route.ExitApp> {
                ExitApp {
                    onPopBack()
                }
            }

            entry<Route.LogScreen> {
                LogScreen(isExpandedScreen) {
                    onOpenDrawer()
                }
            }

            entry<Route.Photo> {
                val photoList = mediaViewerStateHolder.photoFileBeanList.ifEmpty { fileViewModel.photoFileBeanList }
                val photoIndex = mediaViewerStateHolder.photoIndexOf
                val photoCid = mediaViewerStateHolder.photoCid.ifEmpty { fileViewModel.currentCid }
                MyPhotoScreen(
                    photoList = photoList,
                    currentIndex = photoIndex,
                    cid = photoCid,
                    imageCache = fileViewModel.imageBeanCache[photoCid] ?: emptyMap(),
                    onLoadImage = { fileViewModel.getImage(it) },
                    onNav = onPopBack
                )
            }

            entry<Route.RepeatFile> {
                RepeatFileScreen(
                    viewModel = repeatViewModel,
                    isGridScreen = isGridScreen,
                    gridCellMinSize = gridCellMinSize,
                    onClick = { onOpenDrawer() }
                ) { targetCid ->
                    fileViewModel.getFiles(targetCid)
                    onNavigate(Route.MyFile)
                }
            }

            entry<Route.TxtReader> { route ->
                val byteArray = mediaViewerStateHolder.textBodyByteArray ?: fileViewModel.textBodyByteArray

                LaunchedEffect(byteArray) {
                    if (byteArray == null) {
                        onPopBack()
                    }
                }

                if (byteArray != null) {
                    onSetGesturesEnabled(false)
                    TxtReaderScreen(byteArray, title = route.title) {
                        onSetGesturesEnabled(true)
                        onPopBack()
                    }
                }
            }

            entry<Route.MusicDetail> {
                MusicDetailScreen(audioViewModel) {
                    onPopBack()
                }
            }

            entry<Route.HtmlWebViewScreen> { route ->
                val byteArray = mediaViewerStateHolder.webBodyByteArray ?: fileViewModel.webBodyByteArray

                LaunchedEffect(byteArray) {
                    if (byteArray == null) {
                        onPopBack()
                    }
                }

                if (byteArray != null) {
                    onSetGesturesEnabled(false)
                    HtmlWebViewScreen(byteArray, title = route.title) {
                        onSetGesturesEnabled(true)
                        onPopBack()
                    }
                }
            }
        }
    )
}
