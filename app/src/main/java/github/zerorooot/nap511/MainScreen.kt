package github.zerorooot.nap511

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
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.google.gson.Gson
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.screen.AppNavHost
import github.zerorooot.nap511.screen.file.CreateDialogs
import github.zerorooot.nap511.screenitem.AppDrawer
import github.zerorooot.nap511.screenitem.DrawerMenuItems
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

    // ==========================================
    // 导航与路由管理 (Jetpack Navigation 3)
    // ==========================================
    // 1. 初始化 Nav3 返回栈，默认以 Route.MyFile（我的文件）为栈底起始路由
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(Route.MyFile)
    val currentRoute = backStack.lastOrNull() as? Route ?: Route.MyFile


    val menuItems = remember(settingUiState.logEnabled) {
        DrawerMenuItems.buildMenuItems(settingUiState.logEnabled)
    }

    /**
     * 统一路由导航跳转
     * 自动处理抽屉状态、登录鉴权栈重置以及已有路由的回退复用
     */
    fun navigateTo(route: Route) {
        // 1. 若侧边栏打开，自动收起抽屉并恢复手势
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
        navGesturesEnabled = true

        // 2. 如果目标页面就是当前栈顶页面，不进行重复跳转
        if (backStack.lastOrNull() == route) return

        // 3. 登录页特殊处理：清空返回栈，确保未授权状态下无法退回已授权页面；如果是从设置页面进来的，则不用管
        if (route == Route.Login && !backStack.contains(Route.AdvancedSettings)) {
            backStack.clear()
            backStack.add(Route.Login)
            return
        }

        // 4. 从登录页登录成功并跳转其他页面时，清除登录路由
        if (backStack.contains(Route.Login)) {
            backStack.clear()
            backStack.add(route)
            return
        }

        // 5. 核心逻辑：若目标路由已在栈中（如 MyFile 在栈底），弹出其上方所有页面；若不在则压入栈顶
        val index = backStack.indexOf(route)
        if (index >= 0) {
            while (backStack.size > index + 1) {
                backStack.removeLastOrNull()
            }
        } else {
            backStack.add(route)
        }
    }

    /**
     * 路由返回出栈
     * @return true 表示成功出栈，false 表示当前已处于栈底
     */
    fun popBack(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeLastOrNull()
            true
        } else {
            false
        }
    }

    /**
     * 滑动两次返回桌面
     */
    fun slideTwoBackHome(fileViewModel: FileViewModel?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 1500L) {
            fileViewModel?.deleteIndividualFile()
            onMoveTaskToBack(true)
        } else {
            lastBackPressTime = currentTime
            App.instance.toast("再滑一次返回桌面")
        }
    }

    LaunchedEffect(Unit) {
        fileViewModel.loadCacheFile()
        fileViewModel.handleOfflineTask()
        fileViewModel.getRemainingSpace()
        fileViewModel.handleDeepLink(intent)

        fileViewModel.navigationEvent.collect { event ->
            when (event) {
                is NavEvent.NavigateToScreen -> {
                    navigateTo(event.route)
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

    BackHandler(drawerState.isClosed && backStack.size > 1) {
        popBack()
    }
    //登陆页面的再滑一次返回桌面
    BackHandler(backStack.size == 1 && backStack[0] == Route.Login) {
        slideTwoBackHome(null)
    }
    //MyFile页面的再滑一次返回桌面
    BackHandler(drawerState.isClosed && backStack.size == 1 && fileViewModel.pathList.size == 1) {
        slideTwoBackHome(fileViewModel)
    }

    AppDrawer(
        drawerState = drawerState,
        gesturesEnabled = navGesturesEnabled,
        remainingSpaceBean = remainingSpaceBean,
        avatarBean = avatarBean,
        menuItems = menuItems,
        currentRoute = currentRoute,
        onMenuItemClick = { route ->
            navigateTo(route)
        }
    ) {
        AppNavHost(
            backStack = backStack,
            onNavigate = { navigateTo(it) },
            onPopBack = { popBack() },
            fileViewModel = fileViewModel,
            offlineFileViewModel = offlineFileViewModel,
            recycleViewModel = recycleViewModel,
            audioViewModel = audioViewModel,
            repeatViewModel = repeatViewModel,
            settingViewModel = settingViewModel,
            loginViewModel = loginViewModel,
            uiState = settingUiState,
            isDrawerOpen = { drawerState.isOpen },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onCloseDrawer = { scope.launch { drawerState.close() } },
            onSetGesturesEnabled = { navGesturesEnabled = it }
        )
    }

    CreateDialogs(fileViewModel, settingUiState) {
        navigateTo(it)
    }
}
