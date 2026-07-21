package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.app.ComponentCaller
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.factory.CookieViewModelFactory
import github.zerorooot.nap511.screen.CaptchaVideoWebViewScreen
import github.zerorooot.nap511.screen.CaptchaWebViewScreen
import github.zerorooot.nap511.screen.CookieDialog
import github.zerorooot.nap511.screen.ExitApp
import github.zerorooot.nap511.screen.FileScreen
import github.zerorooot.nap511.screen.LogScreen
import github.zerorooot.nap511.screen.LoginWebViewScreen
import github.zerorooot.nap511.screen.MyPhotoScreen
import github.zerorooot.nap511.screen.OfflineDownloadScreen
import github.zerorooot.nap511.screen.OfflineFileScreen
import github.zerorooot.nap511.screen.RecycleScreen
import github.zerorooot.nap511.screen.SettingScreenNew
import github.zerorooot.nap511.screen.WebViewScreen
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.LocalDrawerState
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
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

    @Composable
    private fun Init(cookie: String) {
        //初始化
        val factory = remember { CookieViewModelFactory(cookie, application) }
        val fileViewModel: FileViewModel = viewModel(factory = factory)
        val offlineFileViewModel: OfflineFileViewModel = viewModel(factory = factory)
        val recycleViewModel: RecycleViewModel = viewModel(factory = factory)
        val audioViewModel: AudioViewModel = viewModel(factory = factory)

        LaunchedEffect(Unit) {
            fileViewModel.loadCacheFile()
            //允许通知， 方便离线下载交互 OfflineTaskActivity
            if (!App.instance.isNotificationEnabled(this@MainActivity)) {
                App.instance.toast("检测到未开启通知权限，为保证交互效果，建议开启")
                App.instance.goToNotificationSetting(this@MainActivity)
            }

            val isHandle = handleIntent(intent)
            //仅没处理intent时才检测未上传的磁力链接
            if (!isHandle) {
                //检测添加的离线链接。防止因为种种原因，app添加离线链接，但链接没有上传到115
                fileViewModel.handleOfflineTask()
            }
            fileViewModel.getRemainingSpace()
        }

        BackHandler(fileViewModel.selectedItem != ConfigKeyUtil.MY_FILE) {
            fileViewModel.selectedItem = ConfigKeyUtil.MY_FILE
            if (!fileViewModel.gesturesEnabled) {
                fileViewModel.gesturesEnabled = true
            }
        }

        MyNavigationDrawer(fileViewModel, offlineFileViewModel, recycleViewModel, audioViewModel)

    }


    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        XLog.d("onNewIntent $intent")
        setIntent(intent) // 更新 Intent
        handleIntent(intent)
    }

    /**
     * 处理验证磁力账号、跳转默认下载目录、复制失败的磁力链接的intent
     */
    private fun handleIntent(intent: Intent): Boolean {
        var isHandle = false
        // 动态获取 ViewModel 实例
        val factory = CookieViewModelFactory(App.cookie, application)
        val fileViewModel = ViewModelProvider(this, factory)[FileViewModel::class.java]


        when (intent.action) {
            //直接添加磁力，但提示请验证账号;跳转到验证账号界面
            "check" -> {
                fileViewModel.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
                XLog.d("handleIntent check $intent")
                intent.action = ""
                isHandle = true
            }
            //跳转到默认下载目录
            "jump" -> {
                val cid = intent.getStringExtra("cid") ?: DataStoreUtil.getData(
                    ConfigKeyUtil.DEFAULT_OFFLINE_CID, "0"
                )
                fileViewModel.getFiles(cid)

                XLog.d("handleIntent jump $intent $cid $fileViewModel")
                //清除action,不然会一直跳转到默认下载目录
                intent.action = ""
                isHandle = true
            }

            "copy" -> {
                val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
                val clip = ClipData.newPlainText("label", intent.getStringExtra("link"))
                clipboard?.setPrimaryClip(clip)
                XLog.d("handleIntent copy $intent")
                intent.action = ""
                isHandle = true
                App.instance.toast("复制磁力链接成功!")
            }

            "unzipError" -> {
                val message = intent.getStringExtra("message")
                val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
                val clip = ClipData.newPlainText("unzipError", message)
                clipboard?.setPrimaryClip(clip)
                XLog.d("handleIntent unzipError $intent $message")
                App.instance.toast("解压失败信息已复制到剪切板!")
                intent.action = ""
                isHandle = true

            }
        }
        return isHandle

    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MyNavigationDrawer(
        fileViewModel: FileViewModel,
        offlineFileViewModel: OfflineFileViewModel,
        recycleViewModel: RecycleViewModel,
        audioViewModel: AudioViewModel
    ) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        BackHandler(drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }

        val itemMap = linkedMapOf(
            R.drawable.baseline_login_24 to ConfigKeyUtil.LOGIN,
            R.drawable.baseline_cloud_24 to ConfigKeyUtil.MY_FILE,
            R.drawable.baseline_cloud_download_24 to ConfigKeyUtil.OFFLINE_DOWNLOAD,
            R.drawable.baseline_cloud_done_24 to ConfigKeyUtil.OFFLINE_LIST,
//            R.drawable.baseline_web_24 to ConfigKeyUtil.WEB,
            R.drawable.ic_baseline_delete_24 to ConfigKeyUtil.RECYCLE_BIN,
            R.drawable.baseline_settings_24 to ConfigKeyUtil.ADVANCED_SETTINGS,
        )
        if (DataStoreUtil.getData(ConfigKeyUtil.LOG, false)) {
            itemMap[R.drawable.baseline_log_24] = ConfigKeyUtil.LOG_SCREEN
        }
        itemMap[R.drawable.android_exit] = ConfigKeyUtil.EXIT_APPLICATION

        CompositionLocalProvider(LocalDrawerState provides drawerState) {
            ModalNavigationDrawer(
                gesturesEnabled = fileViewModel.gesturesEnabled,
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Spacer(Modifier.height(6.dp))
                        //头像
                        Avatar(fileViewModel)
                        //菜单栏
                        Spacer(Modifier.height(6.dp))
                        itemMap.forEach { (t, u) ->
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        painterResource(t), contentDescription = u
                                    )
                                },
                                label = { Text(u) },
                                selected = u == fileViewModel.selectedItem,
                                onClick = {
                                    fileViewModel.gesturesEnabled = true
                                    scope.launch { drawerState.close() }
                                    fileViewModel.selectedItem = u
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                },
                content = {
                    when (fileViewModel.selectedItem) {
                        ConfigKeyUtil.LOGIN -> {
                            fileViewModel.gesturesEnabled = false
                            Login {
                                scope.launch { drawerState.open() }
                            }
                        }

                        ConfigKeyUtil.MY_FILE -> FileScreen(
                            fileViewModel,
                            offlineFileViewModel,
                            audioViewModel,
                            appBarClick(fileViewModel)
                        )

                        ConfigKeyUtil.OFFLINE_DOWNLOAD -> OfflineDownloadScreen(
                            offlineFileViewModel, fileViewModel
                        )

                        ConfigKeyUtil.OFFLINE_LIST -> OfflineFileScreen(
                            offlineFileViewModel, fileViewModel
                        )

                        ConfigKeyUtil.WEB -> WebViewScreen(fileViewModel)
                        ConfigKeyUtil.RECYCLE_BIN -> RecycleScreen(recycleViewModel)
                        ConfigKeyUtil.ADVANCED_SETTINGS -> {
                            SettingScreenNew {
                                scope.launch { drawerState.open() }
                            }
                        }

                        ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT -> {
                            CaptchaWebViewScreen()
                        }

                        ConfigKeyUtil.VERIFY_VIDEO_ACCOUNT -> {
                            CaptchaVideoWebViewScreen()
                        }

                        ConfigKeyUtil.LOG_SCREEN -> LogScreen()
                        ConfigKeyUtil.EXIT_APPLICATION -> ExitApp()

                        ConfigKeyUtil.PHOTO -> {
                            MyPhotoScreen(fileViewModel)
                        }
                    }
                })
        }
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
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(avatarBean.value.face)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .scale(coil.size.Scale.FILL)
                        .memoryCacheKey(avatarBean.value.userId)
                        .diskCacheKey(avatarBean.value.userId)
                        .placeholder(R.drawable.avatar)
                        .build(),
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
            Text(text = App.uid)
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
    private fun Login(onClick: (() -> Unit)? = null) {
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
            }
        }
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



