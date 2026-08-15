package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.gson.Gson
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.DrawerMenuItem
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.factory.CookieViewModelFactory
import github.zerorooot.nap511.screen.CaptchaVideoWebViewScreen
import github.zerorooot.nap511.screen.CaptchaWebViewScreen
import github.zerorooot.nap511.screen.CookieDialog
import github.zerorooot.nap511.screen.CreateDialogs
import github.zerorooot.nap511.screen.ExitApp
import github.zerorooot.nap511.screen.FileScreen
import github.zerorooot.nap511.screen.LogScreen
import github.zerorooot.nap511.screen.LoginWebViewScreen
import github.zerorooot.nap511.screen.MyPhotoScreen
import github.zerorooot.nap511.screen.OfflineDownloadScreen
import github.zerorooot.nap511.screen.OfflineFileScreen
import github.zerorooot.nap511.screen.RecycleScreen
import github.zerorooot.nap511.screen.RepeatFileScreen
import github.zerorooot.nap511.screen.SettingScreen
import github.zerorooot.nap511.screen.WebViewScreen
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.RepeatFileViewModel
import github.zerorooot.nap511.viewmodel.cut
import github.zerorooot.nap511.viewmodel.deleteMultiple
import github.zerorooot.nap511.viewmodel.openFileOrderDialog
import github.zerorooot.nap511.viewmodel.openSearchDialog
import github.zerorooot.nap511.viewmodel.openUnzipAllFileDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Nap511Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val cookie = remember { App.cookie }
                    if (cookie == "") {
                        Login(null)
                    } else {
                        Init(cookie)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Init(cookie: String) {
        //初始化
        val factory = remember { CookieViewModelFactory(cookie, application) }
        val fileViewModel: FileViewModel = viewModel(factory = factory)
        val offlineFileViewModel: OfflineFileViewModel = viewModel(factory = factory)
        val recycleViewModel: RecycleViewModel = viewModel(factory = factory)
        val audioViewModel: AudioViewModel = viewModel(factory = factory)
        val repeatViewModel: RepeatFileViewModel = viewModel(factory = factory)
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
            navController
        )

        CreateDialogs(fileViewModel, offlineFileViewModel) {
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
        navController: NavHostController
    ) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // 监听当前导航栈顶的路由，用于高亮显示 Drawer 中选中的 Item
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        // 拦截返回键：如果 Drawer 展开则关闭 Drawer；若已关闭则由 NavController 自动处理返回栈
        BackHandler(drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        val menuItems = remember {
            arrayListOf(
                DrawerMenuItem(R.drawable.baseline_login_24, ConfigKeyUtil.LOGIN, Route.Login),
                DrawerMenuItem(R.drawable.baseline_cloud_24, ConfigKeyUtil.MY_FILE, Route.MyFile),
                DrawerMenuItem(
                    R.drawable.baseline_cloud_download_24,
                    ConfigKeyUtil.OFFLINE_DOWNLOAD,
                    Route.OfflineDownload
                ),
                DrawerMenuItem(
                    R.drawable.baseline_cloud_done_24, ConfigKeyUtil.OFFLINE_LIST, Route.OfflineList
                ),
                //       DrawerMenuItem(R.drawable.baseline_web_24, ConfigKeyUtil.WEB, Route.Web),
                DrawerMenuItem(
                    R.drawable.ic_baseline_delete_24, ConfigKeyUtil.RECYCLE_BIN, Route.RecycleBin
                ),
                DrawerMenuItem(
                    R.drawable.baseline_settings_24,
                    ConfigKeyUtil.ADVANCED_SETTINGS,
                    Route.AdvancedSettings
                ),
            ).apply {
                if (DataStoreUtil.getData(ConfigKeyUtil.LOG, false)) {
                    this.add(
                        DrawerMenuItem(
                            R.drawable.baseline_log_24, ConfigKeyUtil.LOG_SCREEN, Route.LogScreen
                        )
                    )
                }
                this.add(
                    DrawerMenuItem(
                        R.drawable.android_exit, ConfigKeyUtil.EXIT_APPLICATION, Route.ExitApp
                    )
                )
            }

        }

        ModalNavigationDrawer(
            gesturesEnabled = fileViewModel.gesturesEnabled,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(6.dp))
                    Avatar(fileViewModel)
                    Spacer(Modifier.height(6.dp))

                    menuItems.forEach { item ->
                        // 判断当前路由是否匹配该 Item 的路由类型
                        val isSelected = currentDestination?.hasRoute(item.route::class) == true

                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    painterResource(item.iconRes), contentDescription = item.label
                                )
                            }, label = { Text(item.label) }, selected = isSelected, onClick = {
                                fileViewModel.gesturesEnabled = true
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
                    enterTransition = {
                        fadeIn(animationSpec = tween(300)) + scaleIn(
                            initialScale = 0.95f,
                            animationSpec = tween(300)
                        )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300)) + scaleOut(
                            targetScale = 1.05f,
                            animationSpec = tween(300)
                        )
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(300)) + scaleIn(
                            initialScale = 1.05f,
                            animationSpec = tween(300)
                        )
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(300)) + scaleOut(
                            targetScale = 0.95f,
                            animationSpec = tween(300)
                        )
                    }
                ) {
                    composable<Route.Login> {
                        fileViewModel.gesturesEnabled = false
                        Login({ scope.launch { drawerState.open() } }) {
                            navController.popBackStack()
                        }
                    }

                    composable<Route.MyFile> {
                        fileViewModel.gesturesEnabled = true
                        FileScreen(
                            fileViewModel,
                            offlineFileViewModel,
                            audioViewModel,
                            { navController.navigate(Route.Photo) },
                            appBarClick(fileViewModel)
                        ) {
                            val open = drawerState.isOpen
                            if (open) {
                                scope.launch { drawerState.close() }
                            }
                            return@FileScreen open
                        }
                    }

                    composable<Route.OfflineDownload> {
                        OfflineDownloadScreen(
                            offlineFileViewModel,
                            fileViewModel,
                            { scope.launch { drawerState.open() } },
                            { navController.navigate(Route.VerifyMagnetLinkAccount) }
                        )

                    }

                    composable<Route.OfflineList> {
                        OfflineFileScreen(
                            offlineFileViewModel,
                            fileViewModel
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
                        fileViewModel.gesturesEnabled = false
                        WebViewScreen() {
                            scope.launch { drawerState.open() }
                        }
                    }

                    composable<Route.AdvancedSettings> {
                        SettingScreen {
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
                            }
                        }
                    }
                    composable<Route.RecycleBin> {
                        RecycleScreen(recycleViewModel) {
                            scope.launch { drawerState.open() }
                        }
                    }

                    composable<Route.VerifyMagnetLinkAccount> {
                        CaptchaWebViewScreen(fileViewModel) {
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
                        CaptchaVideoWebViewScreen(fileViewModel) {
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
                        RepeatFileScreen(repeatViewModel, { scope.launch { drawerState.open() } }) {
                            fileViewModel.getFiles(it)
                            navController.navigate(Route.MyFile) {
                                popUpTo<Route.RepeatFile> {
                                    inclusive = true
                                }
                            }
                        }
                    }

                }
            })
    }

    /**
     * 头像、网名、uid、已用空间
     */
    @Composable
    private fun Avatar(fileViewModel: FileViewModel) {
        val remainingSpaceBean = fileViewModel.remainingSpace

        val avatarBean = remember {
            mutableStateOf(
                Gson().fromJson(
                    DataStoreUtil.getData(
                        ConfigKeyUtil.AVATAR_BEAN, "{}"
                    ), AvatarBean::class.java
                )
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, // 水平居中
        ) {
            //头像
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(avatarBean.value.face)
                    .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED).scale(coil.size.Scale.FILL)
                    .memoryCacheKey(avatarBean.value.userId).diskCacheKey(avatarBean.value.userId)
                    .placeholder(R.drawable.avatar).build(),
                modifier = Modifier
                    .size(100.dp)
                    //圆形裁剪
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                contentDescription = "Avatar",
            )
            Spacer(Modifier.height(6.dp))
            //用户名
            Text(
                text = avatarBean.value.userName, style = MaterialTheme.typography.titleMedium
            )
            //uid
            Text(text = avatarBean.value.userId)
            //会员到期时间
            Text(
                text = "会员到期时间：${
                    avatarBean.value.expireString
                }", style = MaterialTheme.typography.titleSmall
            )
//            Spacer(Modifier.height(6.dp))
            //已用空间
            Text(
                text = "总计${remainingSpaceBean.total.sizeFormat}，已用${remainingSpaceBean.use.sizeFormat}，剩余${remainingSpaceBean.remain.sizeFormat}",
                style = MaterialTheme.typography.titleSmall
            )
//            //进度条
//            LinearProgressIndicator(
//                progress = (remainingSpaceBean.value.allUse.toDouble() / remainingSpaceBean.value.allTotal).toFloat(),
//                color = Color.Cyan,
//                modifier = Modifier
//                    .fillMaxWidth(0.7f)
//                    .clip(shape = RoundedCornerShape(100.dp))
//            )
        }
    }


    @SuppressLint("UnrememberedMutableState")
    @Composable
    private fun Login(onClick: (() -> Unit)? = null, onNav: (() -> Unit)? = null) {
        var isOpenLoginWebView by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        if (isOpenLoginWebView) {
            if (onClick == null) {
                //首次进入，且选择通过网页登录
                LoginWebViewScreen {
                    isOpenLoginWebView = false
                }
            } else {
                LoginWebViewScreen(onClick)
            }
            return
        }


        CookieDialog {
            if (it == "通过网页登陆") {
                isOpenLoginWebView = true
                return@CookieDialog
            }
            if (it != null && it != "") {
                val replace = it.replace(" ", "").replace("[\r\n]".toRegex(), "");
                scope.launch(Dispatchers.IO) {
                    val pair = App().checkLogin(replace)
                    if (pair.first) {
                        ProcessPhoenix.triggerRebirth(applicationContext);
                    }
                    App.instance.toast(pair.second)
                }
            } else {
                App.instance.toast("请输入cookie")
                onNav?.invoke()
            }
        }
    }

    // 重点：当 Activity 被复用时，新的 Intent 会走这里
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 更新 Activity 的 intent 引用，确保 fileViewModel 能捕获最新的 Deep Link
    }

    private fun appBarClick(fileViewModel: FileViewModel) = fun(name: String) {
        when (name) {
//                "back"->{FileScreen里}
            //具体实现在AlertDialog#UnzipAllFile()里
            "unzipAllFile" -> {
                fileViewModel.openUnzipAllFileDialog()
            }

            "selectToUp" -> fileViewModel.selectToUp()
            "selectToDown" -> fileViewModel.selectToDown()
            "cut" -> fileViewModel.cut()
            //具体实现在FileScreen#CreateDialogs()里
            "search" -> fileViewModel.openSearchDialog()
            "delete" -> fileViewModel.deleteMultiple()
//            "selectAll" -> fileViewModel.selectAll()
            "selectReverse" -> fileViewModel.selectReverse()
            //具体实现在FileScreen#CreateDialogs()里
            "文件排序" -> fileViewModel.openFileOrderDialog()
            "刷新文件" -> fileViewModel.refresh()
            "视频时间" -> {
                //具体实现在FileScreen#myAppBarOnClick里
            }
        }
    }
}



