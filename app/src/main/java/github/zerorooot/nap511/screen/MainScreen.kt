package github.zerorooot.nap511.screen

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import com.google.gson.Gson
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.navigation.AppNavHost
import github.zerorooot.nap511.navigation.DrawerMenuItems
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.ui.navigation.AppDrawer
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.LoginViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.RepeatFileViewModel
import github.zerorooot.nap511.viewmodel.SettingViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    intent: Intent,
    settingUiState: SettingUiState,
    onMoveTaskToBack: (Boolean) -> Unit
) {
    val fileViewModel: FileViewModel = viewModel()
    val offlineFileViewModel: OfflineFileViewModel = viewModel()
    val recycleViewModel: RecycleViewModel = viewModel()
    val audioViewModel: AudioViewModel = viewModel()
    val repeatViewModel: RepeatFileViewModel = viewModel()
    val settingViewModel: SettingViewModel = viewModel()
    val loginViewModel: LoginViewModel = viewModel()

    val navController = rememberNavController()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var navGesturesEnabled by remember { mutableStateOf(true) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    val remainingSpaceBean = fileViewModel.remainingSpace
    val avatarJson by SettingsRepository.getDataFlow(ConfigKeyUtil.AVATAR_BEAN, "{}")
        .collectAsStateWithLifecycle(initialValue = "{}")
    val avatarBean = remember(avatarJson) {
        try {
            Gson().fromJson(avatarJson, AvatarBean::class.java) ?: AvatarBean()
        } catch (_: Exception) {
            AvatarBean()
        }
    }

    val isExpandedConfig = settingUiState.expandedScreenEnabled
    val expandedScreenThreshold =
        settingUiState.expandedScreenThreshold.toIntOrNull()?.takeIf { i -> i > 0 } ?: 600
    val isExpandedScreen =
        (LocalConfiguration.current.screenWidthDp >= expandedScreenThreshold) && isExpandedConfig
    val gridCellMinSize =
        (settingUiState.gridCellMinSize.toIntOrNull()?.takeIf { i -> i > 0 } ?: 340).dp

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val menuItems = remember(settingUiState.logEnabled) {
        DrawerMenuItems.buildMenuItems(settingUiState.logEnabled)
    }

    LaunchedEffect(Unit) {
        fileViewModel.loadCacheFile()
        fileViewModel.handleOfflineTask()
        fileViewModel.getRemainingSpace()
        fileViewModel.handleDeepLink(intent)

        fileViewModel.navigationEvent.collect { event ->
            when (event) {
                is NavEvent.NavigateToScreen -> {
                    if (event.route == Route.Login) {
                        navController.navigate(Route.Login) {
                            popUpTo<Route.MyFile> {
                                inclusive = true
                            }
                        }
                        return@collect
                    }
                    navController.navigate(event.route)
                }
            }
        }
    }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val listener = Consumer<Intent> { newIntent ->
            activity?.intent = newIntent
            fileViewModel.handleDeepLink(newIntent)
        }

        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(drawerState.isClosed && fileViewModel.pathList.size == 1) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 1500L) {
            fileViewModel.deleteIndividualFile()
            onMoveTaskToBack(true)
        } else {
            lastBackPressTime = currentTime
            App.instance.toast("再滑一次返回桌面")
        }
    }

    AppDrawer(
        drawerState = drawerState,
        gesturesEnabled = navGesturesEnabled,
        remainingSpaceBean = remainingSpaceBean,
        avatarBean = avatarBean,
        menuItems = menuItems,
        currentDestination = currentDestination,
        onMenuItemClick = { route ->
            navGesturesEnabled = true
            scope.launch { drawerState.close() }

            val isPopped = navController.popBackStack(route, inclusive = false)
            if (!isPopped) {
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    ) {
        AppNavHost(
            navController = navController,
            fileViewModel = fileViewModel,
            offlineFileViewModel = offlineFileViewModel,
            recycleViewModel = recycleViewModel,
            audioViewModel = audioViewModel,
            repeatViewModel = repeatViewModel,
            settingViewModel = settingViewModel,
            loginViewModel = loginViewModel,
            uiState = settingUiState,
            isExpandedScreen = isExpandedScreen,
            gridCellMinSize = gridCellMinSize,
            isDrawerOpen = { drawerState.isOpen },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onCloseDrawer = { scope.launch { drawerState.close() } },
            onSetGesturesEnabled = { navGesturesEnabled = it }
        )
    }

    CreateDialogs(fileViewModel, settingUiState) {
        navController.navigate(it)
    }
}
