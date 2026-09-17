package github.zerorooot.nap511.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.dialog.ExitApp
import github.zerorooot.nap511.screen.auth.LoginScreen
import github.zerorooot.nap511.screen.file.AdaptiveOfflineScreen
import github.zerorooot.nap511.screen.file.FileScreen
import github.zerorooot.nap511.screen.file.OfflineDownloadScreen
import github.zerorooot.nap511.screen.file.OfflineFileScreen
import github.zerorooot.nap511.screen.file.RecycleScreen
import github.zerorooot.nap511.screen.file.RepeatFileScreen
import github.zerorooot.nap511.screen.setting.SettingScreen
import github.zerorooot.nap511.screen.LogScreen
import github.zerorooot.nap511.screen.viewer.MusicDetailScreen
import github.zerorooot.nap511.screen.viewer.MyPhotoScreen
import github.zerorooot.nap511.screen.viewer.TxtReaderScreen
import github.zerorooot.nap511.screen.web.CaptchaVideoWebViewScreen
import github.zerorooot.nap511.screen.web.CaptchaWebViewScreen
import github.zerorooot.nap511.screen.web.HtmlWebViewScreen
import github.zerorooot.nap511.screen.web.WebViewScreen
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.LoginViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.RepeatFileViewModel
import github.zerorooot.nap511.viewmodel.SettingViewModel

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
    fileViewModel: FileViewModel,
    offlineFileViewModel: OfflineFileViewModel,
    recycleViewModel: RecycleViewModel,
    audioViewModel: AudioViewModel,
    repeatViewModel: RepeatFileViewModel,
    settingViewModel: SettingViewModel,
    loginViewModel: LoginViewModel,
    uiState: SettingUiState,
    isDrawerOpen: () -> Boolean,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSetGesturesEnabled: (Boolean) -> Unit
) {
    val isExpandedScreen = uiState.expandedScreenEnabled
    val gridCellMinSize =
        (uiState.gridCellMinSize.toIntOrNull()?.takeIf { i -> i > 0 } ?: 340).dp
    val isGridScreen = uiState.gridScreenEnabled

    NavDisplay(
        backStack = backStack,
        onBack = onPopBack,
        entryProvider = entryProvider<NavKey> {
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
                    onNav = { route -> onNavigate(route) }
                ) {
                    val open = isDrawerOpen()
                    if (open) {
                        onCloseDrawer()
                    }
                    open
                }
            }

            entry<Route.OfflineDownload> {
                LaunchedEffect(Unit) {
                    offlineFileViewModel.quota()
                }

                val fileUiState by fileViewModel.uiState.collectAsStateWithLifecycle()
                val quotaBean by offlineFileViewModel.quotaBean.collectAsState()
                val urlText by offlineFileViewModel.urlText

                OfflineDownloadScreen(
                    path = fileUiState.path,
                    quotaBean = quotaBean,
                    url = urlText,
                    onClick = { onOpenDrawer() }
                ) { list ->
                    offlineFileViewModel.addTask(list, fileViewModel.currentCid) { needVerify ->
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
                if (isExpandedScreen) {
                    AdaptiveOfflineScreen(
                        offlineFileViewModel = offlineFileViewModel,
                        isGridScreen = isGridScreen,
                        gridCellMinSize = gridCellMinSize,
                        getFiles = { fileViewModel.getFiles(it) },
                        onDrawerClick = onOpenDrawer,
                        onBackToFiles = { onNavigate(Route.MyFile) },
                        onNavigateToNewTask = { onNavigate(Route.OfflineDownload) }
                    )
                } else {
                    OfflineFileScreen(
                        offlineFileViewModel,
                        isGridScreen,
                        gridCellMinSize,
                        { fileViewModel.getFiles(it) }) { action ->
                        when (action) {
                            "ModalNavigationDrawerMenu" -> onOpenDrawer()
                            "MyFile" -> onNavigate(Route.MyFile)
                        }
                    }
                }


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
                        }
                    }
                )
            }

            entry<Route.RecycleBin> {
                RecycleScreen(recycleViewModel, isExpandedScreen, gridCellMinSize) {
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
                LogScreen {
                    onOpenDrawer()
                }
            }

            entry<Route.Photo> {
                MyPhotoScreen(fileViewModel) {
                    onPopBack()
                }
            }

            entry<Route.RepeatFile> {
                RepeatFileScreen(
                    viewModel = repeatViewModel,
                    isExpandedScreen = isExpandedScreen,
                    gridCellMinSize = gridCellMinSize,
                    onClick = { onOpenDrawer() }
                ) { targetCid ->
                    fileViewModel.getFiles(targetCid)
                    onNavigate(Route.MyFile)
                }
            }

            entry<Route.TxtReader> {
                val byteArray = fileViewModel.textBodyByteArray
                val fileBean = fileViewModel.fileBeanList.getOrNull(fileViewModel.selectIndex)

                LaunchedEffect(byteArray) {
                    if (byteArray == null) {
                        onPopBack()
                    }
                }

                if (byteArray != null) {
                    TxtReaderScreen(byteArray, title = fileBean?.name ?: "文本阅读") {
                        onPopBack()
                    }
                }
            }

            entry<Route.MusicDetail> {
                MusicDetailScreen(audioViewModel) {
                    onPopBack()
                }
            }

            entry<Route.HtmlWebViewScreen> {
                val byteArray = fileViewModel.webBodyByteArray
                val fileBean = fileViewModel.fileBeanList.getOrNull(fileViewModel.selectIndex)

                LaunchedEffect(byteArray) {
                    if (byteArray == null) {
                        onPopBack()
                    }
                }

                if (byteArray != null) {
                    onSetGesturesEnabled(false)
                    HtmlWebViewScreen(byteArray, title = fileBean?.name ?: "网页") {
                        onSetGesturesEnabled(true)
                        onPopBack()
                    }
                }
            }
        }
    )
}
