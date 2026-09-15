package github.zerorooot.nap511.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.dialog.ExitApp
import github.zerorooot.nap511.screen.CaptchaVideoWebViewScreen
import github.zerorooot.nap511.screen.CaptchaWebViewScreen
import github.zerorooot.nap511.screen.FileScreen
import github.zerorooot.nap511.screen.HtmlWebViewScreen
import github.zerorooot.nap511.screen.LogScreen
import github.zerorooot.nap511.screen.LoginScreen
import github.zerorooot.nap511.screen.MusicDetailScreen
import github.zerorooot.nap511.screen.MyPhotoScreen
import github.zerorooot.nap511.screen.OfflineDownloadScreen
import github.zerorooot.nap511.screen.OfflineFileScreen
import github.zerorooot.nap511.screen.RecycleScreen
import github.zerorooot.nap511.screen.RepeatFileScreen
import github.zerorooot.nap511.screen.SettingScreen
import github.zerorooot.nap511.screen.TxtReaderScreen
import github.zerorooot.nap511.screen.WebViewScreen
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.LoginViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.RepeatFileViewModel
import github.zerorooot.nap511.viewmodel.SettingViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    fileViewModel: FileViewModel,
    offlineFileViewModel: OfflineFileViewModel,
    recycleViewModel: RecycleViewModel,
    audioViewModel: AudioViewModel,
    repeatViewModel: RepeatFileViewModel,
    settingViewModel: SettingViewModel,
    loginViewModel: LoginViewModel,
    uiState: SettingUiState,
    isExpandedScreen: Boolean,
    gridCellMinSize: Dp,
    isDrawerOpen: () -> Boolean,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSetGesturesEnabled: (Boolean) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Route.MyFile,
    ) {
        composable<Route.Login> {
            onSetGesturesEnabled(false)
            LoginScreen { credential ->
                loginViewModel.performLogin(credential) {
                    fileViewModel.getRemainingSpace()
                    fileViewModel.getFiles("0")
                    navController.navigate(Route.MyFile) {
                        popUpTo<Route.Login> {
                            inclusive = true
                        }
                    }
                }
            }
        }

        composable<Route.MyFile> {
            onSetGesturesEnabled(true)
            FileScreen(
                fileViewModel,
                uiState,
                audioViewModel,
                isExpandedScreen,
                gridCellMinSize,
                { route ->
                    navController.navigate(route)
                }
            ) {
                val open = isDrawerOpen()
                if (open) {
                    onCloseDrawer()
                }
                open
            }
        }

        composable<Route.OfflineDownload> {
            LaunchedEffect(Unit) {
                offlineFileViewModel.quota()
            }

            val fileUiState by fileViewModel.uiState.collectAsStateWithLifecycle()
            val quotaBean by offlineFileViewModel.quotaBean.collectAsState()
            val urlText by offlineFileViewModel.urlText

            OfflineDownloadScreen(
                fileUiState.path,
                quotaBean,
                urlText,
                { onOpenDrawer() }
            ) { list ->
                offlineFileViewModel.addTask(list, fileViewModel.currentCid) { needVerify ->
                    if (needVerify) {
                        navController.navigate(Route.VerifyMagnetLinkAccount)
                    }
                }
            }
        }

        composable<Route.OfflineList> {
            LaunchedEffect(Unit) {
                offlineFileViewModel.getOfflineFileList()
            }
            OfflineFileScreen(
                offlineFileViewModel,
                isExpandedScreen,
                gridCellMinSize,
                { fileViewModel.getFiles(it) }
            ) { action ->
                when (action) {
                    "ModalNavigationDrawerMenu" -> onOpenDrawer()
                    "MyFile" -> navController.popBackStack()
                }
            }
        }

        composable<Route.WebScreen> {
            onSetGesturesEnabled(false)
            WebViewScreen {
                onOpenDrawer()
            }
        }

        composable<Route.AdvancedSettings> {
            SettingScreen(settingViewModel) { action ->
                when (action) {
                    "topAppBarActionButtonOnClick" -> onOpenDrawer()

                    "VerifyVideoAccount" -> {
                        navController.navigate(Route.VerifyVideoAccount) {
                            popUpTo<Route.AdvancedSettings> { inclusive = true }
                        }
                    }

                    "VerifyMagnetLinkAccount" -> {
                        navController.navigate(Route.VerifyMagnetLinkAccount) {
                            popUpTo<Route.AdvancedSettings> { inclusive = true }
                        }
                    }

                    "handleOfflineTask" -> {
                        fileViewModel.handleOfflineTask(true)
                    }

                    "RepeatFile" -> {
                        navController.navigate(Route.RepeatFile) {
                            popUpTo<Route.AdvancedSettings> { inclusive = true }
                        }
                    }

                    "Login" -> {
                        navController.navigate(Route.Login) {
                            popUpTo<Route.AdvancedSettings> { inclusive = true }
                        }
                    }
                }
            }
        }

        composable<Route.RecycleBin> {
            RecycleScreen(recycleViewModel, isExpandedScreen, gridCellMinSize) {
                onOpenDrawer()
            }
        }

        composable<Route.VerifyMagnetLinkAccount> {
            CaptchaWebViewScreen({ fileViewModel.handleOfflineTask() }) { action ->
                when (action) {
                    "topAppBarActionButtonOnClick" -> onOpenDrawer()
                    "select" -> {
                        navController.navigate(Route.MyFile) {
                            popUpTo<Route.VerifyMagnetLinkAccount> { inclusive = true }
                        }
                    }
                }
            }
        }

        composable<Route.VerifyVideoAccount> {
            CaptchaVideoWebViewScreen { action ->
                when (action) {
                    "topAppBarActionButtonOnClick" -> onOpenDrawer()
                    "select" -> {
                        navController.navigate(Route.MyFile) {
                            popUpTo<Route.VerifyVideoAccount> { inclusive = true }
                        }
                    }
                }
            }
        }

        composable<Route.ExitApp> {
            ExitApp {
                navController.popBackStack()
            }
        }

        composable<Route.LogScreen> {
            LogScreen {
                onOpenDrawer()
            }
        }

        composable<Route.Photo> {
            MyPhotoScreen(fileViewModel) {
                navController.popBackStack()
            }
        }

        composable<Route.RepeatFile> {
            RepeatFileScreen(
                repeatViewModel,
                isExpandedScreen,
                gridCellMinSize,
                { onOpenDrawer() }
            ) { targetCid ->
                fileViewModel.getFiles(targetCid)
                navController.navigate(Route.MyFile) {
                    popUpTo<Route.RepeatFile> { inclusive = true }
                }
            }
        }

        composable<Route.TxtReader> {
            val byteArray = fileViewModel.textBodyByteArray
            val fileBean = fileViewModel.fileBeanList.getOrNull(fileViewModel.selectIndex)

            LaunchedEffect(byteArray) {
                if (byteArray == null) {
                    navController.popBackStack()
                }
            }

            if (byteArray != null) {
                TxtReaderScreen(byteArray, title = fileBean?.name ?: "文本阅读") {
                    navController.popBackStack()
                }
            }
        }

        composable<Route.MusicDetail> {
            MusicDetailScreen(audioViewModel) {
                navController.popBackStack()
            }
        }

        composable<Route.HtmlWebViewScreen> {
            val byteArray = fileViewModel.webBodyByteArray
            val fileBean = fileViewModel.fileBeanList.getOrNull(fileViewModel.selectIndex)

            LaunchedEffect(byteArray) {
                if (byteArray == null) {
                    navController.popBackStack()
                }
            }

            if (byteArray != null) {
                onSetGesturesEnabled(false)
                HtmlWebViewScreen(byteArray, title = fileBean?.name ?: "网页") {
                    onSetGesturesEnabled(true)
                    navController.popBackStack()
                }
            }
        }
    }
}
