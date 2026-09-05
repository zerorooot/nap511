package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.DrawerMenuItem
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.dialog.ExitApp
import github.zerorooot.nap511.screen.CaptchaVideoWebViewScreen
import github.zerorooot.nap511.screen.CaptchaWebViewScreen
import github.zerorooot.nap511.screen.CreateDialogs
import github.zerorooot.nap511.screen.FileScreen
import github.zerorooot.nap511.screen.LogScreen
import github.zerorooot.nap511.screen.LoginCredential
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
import github.zerorooot.nap511.screenitem.Avatar
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.RepeatFileViewModel
import github.zerorooot.nap511.viewmodel.SettingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {
    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeViewTreeOwners()
        enableEdgeToEdge()
        setContent {
            val dynamicColor by DataStoreUtil.getDataFlow(ConfigKeyUtil.DYNAMIC_COLOR, true)
                .collectAsStateWithLifecycle(initialValue = true)
            val themeMode by DataStoreUtil.getDataFlow(ConfigKeyUtil.THEME_MODE, "跟随系统")
                .collectAsStateWithLifecycle(initialValue = "跟随系统")

            val darkTheme = when (themeMode) {
                "亮色模式" -> false
                "暗色模式" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            // 实时更新状态栏和导航栏颜色，确保主题切换立即生效
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            Nap511Theme(dynamicColor = dynamicColor, darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Init()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Init() {
        //初始化
        val fileViewModel: FileViewModel = viewModel()
        val offlineFileViewModel: OfflineFileViewModel = viewModel()
        val recycleViewModel: RecycleViewModel = viewModel()
        val audioViewModel: AudioViewModel = viewModel()
        val repeatViewModel: RepeatFileViewModel = viewModel()
        val settingViewModel: SettingViewModel = viewModel()
        val navController = rememberNavController()
        val context = LocalContext.current


        LaunchedEffect(Unit) {
            fileViewModel.loadCacheFile()
            //允许通知， 方便离线下载交互 OfflineTaskActivity
            if (!App.instance.isNotificationEnabled(this@MainActivity)) {
                App.instance.toast("检测到未开启通知权限，为保证交互效果，建议开启")
                App.instance.goToNotificationSetting(this@MainActivity)
            }
            //检测添加的离线链接。防止因为种种原因，app添加离线链接，但链接没有上传到115
            fileViewModel.handleOfflineTask()

            fileViewModel.getRemainingSpace()
            //冷启动处理
            fileViewModel.handleDeepLink(intent)

            fileViewModel.navigationEvent.collect { event ->
                when (event) {
                    is NavEvent.NavigateToScreen -> {
                        if (event.route == Route.Login) {
                            navController.navigate(Route.Login) {
                                // 弹出 MyFile，让 Login 直接替换设置页在栈中的位置，防止返回
                                popUpTo<Route.MyFile> {
                                    inclusive = true
                                }
                            }
                            return@collect
                        }
                        // 直接导航到真正的 UI 目标页面
                        navController.navigate(event.route)
                    }
                }
            }
        }

        MyNavigationDrawer(
            fileViewModel,
            offlineFileViewModel,
            recycleViewModel,
            audioViewModel,
            repeatViewModel,
            settingViewModel,
            navController
        )

        CreateDialogs(fileViewModel) {
            navController.navigate(it)
        }

        // 监听 Activity 的 onNewIntent 事件
        DisposableEffect(context) {
            val activity = context as? ComponentActivity
            val listener = Consumer<Intent> { newIntent ->
                activity?.intent = newIntent // 更新 Activity 绑定的 intent
                fileViewModel.handleDeepLink(newIntent)
            }

            activity?.addOnNewIntentListener(listener)

            onDispose {
                activity?.removeOnNewIntentListener(listener)
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MyNavigationDrawer(
        fileViewModel: FileViewModel,
        offlineFileViewModel: OfflineFileViewModel,
        recycleViewModel: RecycleViewModel,
        audioViewModel: AudioViewModel,
        repeatViewModel: RepeatFileViewModel,
        settingViewModel: SettingViewModel,
        navController: NavHostController
    ) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val remainingSpaceBean = fileViewModel.remainingSpace
        val avatarJson by DataStoreUtil.getDataFlow(ConfigKeyUtil.AVATAR_BEAN, "{}")
            .collectAsStateWithLifecycle(initialValue = "{}")
        val avatarBean = remember(avatarJson) {
            try {
                Gson().fromJson(avatarJson, AvatarBean::class.java) ?: AvatarBean()
            } catch (_: Exception) {
                AvatarBean()
            }
        }
        var navGesturesEnabled by remember { mutableStateOf(true) }
        // 记录上一次触发返回的时间戳
        var lastBackPressTime by remember { mutableLongStateOf(0L) }

        val isExpandedConfig by DataStoreUtil.getDataFlow(ConfigKeyUtil.EXPANDED_SCREEN, true)
            .collectAsStateWithLifecycle(initialValue = true)
        val isExpandedScreen =
            (LocalConfiguration.current.screenWidthDp >= 600) && isExpandedConfig


        // 监听当前导航栈顶的路由，用于高亮显示 Drawer 中选中的 Item
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        // 拦截返回键：如果 Drawer 展开则关闭 Drawer；若已关闭则由 NavController 自动处理返回栈
        BackHandler(drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        BackHandler(drawerState.isClosed && fileViewModel.pathList.size == 1) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 1500L) {
                //直接关闭应用并返回桌面
                // finish()
                //将应用压入后台保留状态（类似按 Home 键）
                moveTaskToBack(true)
            } else {
                lastBackPressTime = currentTime
                App.instance.toast("再滑一次返回桌面")
            }
        }
        val isLogEnabled by DataStoreUtil.getDataFlow(ConfigKeyUtil.LOG, false)
            .collectAsStateWithLifecycle(initialValue = false)

        val menuItems = remember(isLogEnabled) {
            arrayListOf(
//                DrawerMenuItem(Icons.AutoMirrored.Filled.Login, ConfigKeyUtil.LOGIN, Route.Login),
                DrawerMenuItem(Icons.Default.Cloud, ConfigKeyUtil.MY_FILE, Route.MyFile),
                DrawerMenuItem(
                    Icons.Default.CloudDownload,
                    ConfigKeyUtil.OFFLINE_DOWNLOAD,
                    Route.OfflineDownload
                ),
                DrawerMenuItem(
                    Icons.Default.CloudDone, ConfigKeyUtil.OFFLINE_LIST, Route.OfflineList
                ),

                DrawerMenuItem(Icons.Default.Web, ConfigKeyUtil.WEB, Route.WebScreen),
                DrawerMenuItem(
                    Icons.Default.Delete, ConfigKeyUtil.RECYCLE_BIN, Route.RecycleBin
                ),
                DrawerMenuItem(
                    Icons.Default.Settings,
                    ConfigKeyUtil.ADVANCED_SETTINGS,
                    Route.AdvancedSettings
                ),
            ).apply {
                if (isLogEnabled) {
                    this.add(
                        DrawerMenuItem(
                            Icons.Default.Android, ConfigKeyUtil.LOG_SCREEN, Route.LogScreen
                        )
                    )
                }
                this.add(
                    DrawerMenuItem(
                        Icons.AutoMirrored.Default.ExitToApp,
                        ConfigKeyUtil.EXIT_APPLICATION,
                        Route.ExitApp
                    )
                )
            }

        }

        ModalNavigationDrawer(
            gesturesEnabled = navGesturesEnabled || drawerState.isOpen,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(6.dp))
                    Avatar(remainingSpaceBean, avatarBean)
                    Spacer(Modifier.height(6.dp))

                    menuItems.forEach { item ->
                        // 判断当前路由是否匹配该 Item 的路由类型
                        val isSelected = currentDestination?.hasRoute(item.route::class) == true

                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    item.iconVector, contentDescription = item.label
                                )
                            }, label = { Text(item.label) }, selected = isSelected, onClick = {
                                navGesturesEnabled = true
                                scope.launch { drawerState.close() }

                                // 优先尝试直接弹出回目标顶级页面（如果当前正处于该页面的子页面中）
                                val isPopped =
                                    navController.popBackStack(item.route, inclusive = false)

                                // 如果栈中没有该目标页面（即从其他 Tab 切过来），则执行标准的顶级导航
                                if (!isPopped) {
                                    // 切换 Drawer 顶级页面的标准导航写法
                                    navController.navigate(item.route) {
                                        // 弹出到起始页，避免生成无限多的返回栈
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true  // 避免重复创建同一个页面
                                        restoreState = true // 恢复之前保存的状态
                                    }
                                }
                            }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            },
            content = {
                // 使用 NavHost 管理页面切换
                NavHost(
                    navController, startDestination = Route.MyFile,
                ) {
                    composable<Route.Login> {
                        navGesturesEnabled = false
                        Login {
                            navController.navigate(Route.MyFile) {
                                popUpTo<Route.Login> {
                                    inclusive = true
                                }
                            }
                            fileViewModel.getFiles("0")
                        }
                    }

                    composable<Route.MyFile> {
                        navGesturesEnabled = true
                        FileScreen(
                            fileViewModel,
                            audioViewModel,
                            isExpandedScreen,
                            {
                                scope.launch(Dispatchers.Main) {
                                    navController.navigate(it)
                                }
                            }
                        ) {
                            val open = drawerState.isOpen
                            if (open) {
                                scope.launch { drawerState.close() }
                            }
                            return@FileScreen open
                        }
                    }

                    composable<Route.OfflineDownload> {
                        LaunchedEffect(Unit) {
                            offlineFileViewModel.quota()
                        }

                        val currentPath by fileViewModel.currentPath.collectAsStateWithLifecycle()
                        val quotaBean by offlineFileViewModel.quotaBean.collectAsState()
                        val urlText by offlineFileViewModel.urlText

                        OfflineDownloadScreen(
                            currentPath,
                            quotaBean,
                            urlText,
                            { scope.launch { drawerState.open() } }
                        ) { list ->
                            offlineFileViewModel.addTask(list, fileViewModel.currentCid) {
                                if (it) {
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
                            { fileViewModel.getFiles(it) }
                        ) {
                            when (it) {
                                "ModalNavigationDrawerMenu" -> {
                                    scope.launch { drawerState.open() }
                                }

                                "MyFile" -> {
                                    // 弹出栈顶页面，返回上一个页面
                                    navController.popBackStack()
                                }
                            }
                        }
                    }

                    composable<Route.WebScreen> {
                        navGesturesEnabled = false
                        WebViewScreen {
                            scope.launch { drawerState.open() }
                        }
                    }

                    composable<Route.AdvancedSettings> {
                        SettingScreen(settingViewModel) {
                            when (it) {
                                "topAppBarActionButtonOnClick" -> {
                                    scope.launch { drawerState.open() }
                                }

                                "VerifyVideoAccount" -> {
                                    navController.navigate(Route.VerifyVideoAccount) {
                                        // 弹出 AdvancedSettings，让 RepeatFile 直接替换设置页在栈中的位置
                                        popUpTo<Route.AdvancedSettings> {
                                            inclusive = true
                                        }
                                    }
                                }

                                "VerifyMagnetLinkAccount" -> {
                                    navController.navigate(Route.VerifyMagnetLinkAccount) {
                                        // 弹出 AdvancedSettings，让 RepeatFile 直接替换设置页在栈中的位置
                                        popUpTo<Route.AdvancedSettings> {
                                            inclusive = true
                                        }
                                    }
                                }

                                "handleOfflineTask" -> {
                                    fileViewModel.handleOfflineTask(true)
                                }

                                "RepeatFile" -> {
                                    navController.navigate(Route.RepeatFile) {
                                        // 弹出 AdvancedSettings，让 RepeatFile 直接替换设置页在栈中的位置
                                        popUpTo<Route.AdvancedSettings> {
                                            inclusive = true
                                        }
                                    }
                                }

                                "Login" -> {
                                    navController.navigate(Route.Login) {
                                        // 弹出 AdvancedSettings，让 Login 直接替换设置页在栈中的位置
                                        popUpTo<Route.AdvancedSettings> {
                                            inclusive = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    composable<Route.RecycleBin> {
                        RecycleScreen(recycleViewModel, isExpandedScreen) {
                            scope.launch { drawerState.open() }
                        }
                    }

                    composable<Route.VerifyMagnetLinkAccount> {
                        CaptchaWebViewScreen(
                            { fileViewModel.handleOfflineTask() }) {
                            when (it) {
                                "topAppBarActionButtonOnClick" -> {
                                    scope.launch { drawerState.open() }
                                }

                                "select" -> {
                                    scope.launch(Dispatchers.Main) {
                                        navController.navigate(Route.MyFile) {
                                            // 将 VerifyMagnetLinkAccount 中转页从返回栈中彻底弹出，用户返回时，就会直接退回首页，而不会退回中转页
                                            popUpTo<Route.VerifyMagnetLinkAccount> {
                                                inclusive = true
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    }

                    composable<Route.VerifyVideoAccount> {
                        CaptchaVideoWebViewScreen {
                            when (it) {
                                "topAppBarActionButtonOnClick" -> {
                                    scope.launch { drawerState.open() }
                                }

                                "select" -> {
                                    scope.launch(Dispatchers.Main) {
                                        navController.navigate(Route.MyFile) {
                                            // 将 VerifyVideoAccount 中转页从返回栈中彻底弹出，用户返回时，就会直接退回首页，而不会退回中转页
                                            popUpTo<Route.VerifyVideoAccount> {
                                                inclusive = true
                                            }
                                        }
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
                            scope.launch { drawerState.open() }
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
                            { scope.launch { drawerState.open() } }) {
                            fileViewModel.getFiles(it)
                            navController.navigate(Route.MyFile) {
                                popUpTo<Route.RepeatFile> {
                                    inclusive = true
                                }
                            }
                        }
                    }
                    composable<Route.TxtReader> {
                        val byteArray = fileViewModel.textBodyByteArray
                        val fileBean =
                            fileViewModel.fileBeanList.getOrNull(fileViewModel.selectIndex)

                        LaunchedEffect(byteArray) {
                            if (byteArray == null) {
                                navController.popBackStack() // 安全退出
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

                }
            })
    }


    @SuppressLint("UnrememberedMutableState")
    @Composable
    private fun Login(onLoginSuccess: () -> Unit) {
        val scope = rememberCoroutineScope()
        LoginScreen { credential ->
            scope.launch(Dispatchers.IO) {
                val success = when (credential) {
                    is LoginCredential.Cookie -> {
                        val replace = credential.cookieString.replace(" ", "")
                            .replace("[\r\n]".toRegex(), "")
                        App.instance.checkLogin(replace)
                    }

                    is LoginCredential.ConfigFile -> {
                        try {
                            val gson = GsonBuilder().setPrettyPrinting().create()
                            val jsonString = credential.rawJson
                            // 解析为 JsonObject
                            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

                            // 动态遍历并保存到 DataStore
                            jsonObject.entrySet().forEach { (key, element) ->
                                if (element.isJsonPrimitive) {
                                    val primitive = element.asJsonPrimitive
                                    when {
                                        primitive.isBoolean -> DataStoreUtil.putDataSuspend(
                                            key,
                                            primitive.asBoolean
                                        )

                                        primitive.isString -> DataStoreUtil.putDataSuspend(
                                            key,
                                            primitive.asString
                                        )

                                        primitive.isNumber -> DataStoreUtil.putDataSuspend(
                                            key,
                                            primitive.asNumber
                                        )
                                    }
                                }
                            }
                            val cookie = jsonObject.get(ConfigKeyUtil.COOKIE).asString
                            App.instance.checkLogin(cookie)
                        } catch (e: Exception) {
                            App.instance.toast("解析配置失败")
                            XLog.d("LoginScreen LoginCredential.ConfigFile jsonString ${credential.rawJson}")
                            false
                        }
                    }
                }
                if (success) {
                    withContext(Dispatchers.Main) {
                        onLoginSuccess.invoke()
                    }
                }
            }
        }
    }

    // 重点：当 Activity 被复用时，新的 Intent 会走这里
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 更新 Activity 的 intent 引用，确保 fileViewModel 能捕获最新的 Deep Link
    }
}



