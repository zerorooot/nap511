package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.app.ComponentCaller
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.navigation.compose.rememberNavController
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.elvishew.xlog.XLog
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.activity.OfflineTaskWorker
import github.zerorooot.nap511.activity.UnzipAllFileWorker
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
import github.zerorooot.nap511.util.DialogSwitchUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import kotlinx.coroutines.launch
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Nap511Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val cookie = App.cookie
                    if (cookie == "") {
                        Login()
                        return@Surface
                    }
                    //初始化
                    Init(cookie)
                    //允许通知， 方便离线下载交互 OfflineTaskActivity
                    if (!App.instance.isNotificationEnabled(this)) {
                        App.instance.toast("检测到未开启通知权限，为保证交互效果，建议开启")
                        App.instance.goToNotificationSetting(this)
                    }

                    val isHandle = handleIntent(intent)
                    //仅没处理intent时才检测未上传的磁力链接
                    if (!isHandle) {
                        checkOfflineTask(cookie)
                    }

                }
            }
        }
    }


    @Composable
    private fun Init(cookie: String) {
        val cookieViewModelFactory = CookieViewModelFactory(cookie, this.application)
        val fileViewModel by viewModels<FileViewModel> { cookieViewModelFactory }
        val offlineFileViewModel by viewModels<OfflineFileViewModel> { cookieViewModelFactory }
        val recycleViewModel by viewModels<RecycleViewModel> { cookieViewModelFactory }

        //恢复因MyPhotoScreen而造成的isSystemBarsVisible为false的情况
        var visible by remember {
            mutableStateOf(true)
        }
        rememberSystemUiController().apply {
            isSystemBarsVisible = visible
        }
        BackHandler(App.selectedItem != ConfigKeyUtil.MY_FILE) {
            App.selectedItem = ConfigKeyUtil.MY_FILE
            visible = true
            if (!App.gesturesEnabled) {
                App.gesturesEnabled = true
            }
        }

        MyNavigationDrawer(fileViewModel, offlineFileViewModel, recycleViewModel)
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

        val action = intent.action
        when (action) {
            //直接添加磁力，但提示请验证账号;跳转到验证账号界面
            "check" -> {
                App.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
                XLog.d("handleIntent check $intent")
                intent.action = ""
                isHandle = true
            }
            //跳转到默认下载目录
            "jump" -> {
                val fileViewModel: FileViewModel by viewModels()
                val cid = intent.getStringExtra("cid").let {
                    if (it == null) {
                        DataStoreUtil.getData(
                            ConfigKeyUtil.DEFAULT_OFFLINE_CID,
                            "0"
                        )
                    } else it
                }
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
                XLog.d("handleIntent unzipError $intent $message")
                intent.action = ""
                isHandle = true
            }
        }
        return isHandle

    }

    /**
     * 检测添加的离线链接。防止因为种种原因，app添加离线链接，但链接没有上传到115
     * 后台添加离线链接的代码在OfflineTaskActivity
     *
     */
    private fun checkOfflineTask(cookie: String) {
        val workQuery = WorkQuery.Builder.fromStates(
            listOf(
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
//                WorkInfo.State.SUCCEEDED,
//                WorkInfo.State.FAILED,
                WorkInfo.State.BLOCKED,
                WorkInfo.State.CANCELLED
            )
        ).build()
        val workInfos: List<WorkInfo> =
            WorkManager.getInstance(applicationContext).getWorkInfos(workQuery).get()
        //todo 检测是否与解压文件相冲突
//        val workInfos: List<WorkInfo> =
//            WorkManager.getInstance(applicationContext)
//                .getWorkInfosByTag(ConfigKeyUtil.OFFLINE_TASK_WORKER).get()
        val size = workInfos.size
        if (size != 0) {
            return
        }
        //size等于0,证明后台没有正在添加离线链接
        val currentOfflineTask = DataStoreUtil.getData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
            .split("\n")
            .filter { i -> i != "" && i != " " }
            .toSet()
            .toMutableList()
        //currentOfflineTask等于0,证明没有离线链接缓存
        if (currentOfflineTask.isEmpty()) {
            return
        }
        XLog.d(
            "checkOfflineTask workManager workInfos $workInfos currentOfflineTask size ${currentOfflineTask.size}"
        )
        //添加离线链接
        val listType = object : TypeToken<List<String?>?>() {}.type
        val list = Gson().toJson(currentOfflineTask, listType)
        val data: Data =
            Data.Builder().putString("cookie", cookie).putString("list", list)
                .build()
        val request: OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java)
                .addTag(ConfigKeyUtil.OFFLINE_TASK_WORKER)
                .setInputData(data)
                .build()
        WorkManager.getInstance(App.instance.applicationContext).enqueue(request)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MyNavigationDrawer(
        fileViewModel: FileViewModel,
        offlineFileViewModel: OfflineFileViewModel,
        recycleViewModel: RecycleViewModel
    ) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        App.drawerState = drawerState
        App.scope = scope
        val itemMap = linkedMapOf(
            R.drawable.baseline_login_24 to ConfigKeyUtil.LOGIN,
            R.drawable.baseline_cloud_24 to ConfigKeyUtil.MY_FILE,
            R.drawable.baseline_cloud_download_24 to ConfigKeyUtil.OFFLINE_DOWNLOAD,
            R.drawable.baseline_cloud_done_24 to ConfigKeyUtil.OFFLINE_LIST,
//            R.drawable.baseline_video_moderator_24 to ConfigKeyUtil.VERIFY_VIDEO_ACCOUNT,
//            R.drawable.baseline_magent_moderator_24 to ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT,
//            R.drawable.baseline_web_24 to ConfigKeyUtil.WEB,
            R.drawable.ic_baseline_delete_24 to ConfigKeyUtil.RECYCLE_BIN,
            R.drawable.baseline_settings_24 to ConfigKeyUtil.ADVANCED_SETTINGS,
        )
        if (DataStoreUtil.getData(ConfigKeyUtil.LOG_SCREEN, false)) {
            itemMap.put(R.drawable.baseline_log_24, ConfigKeyUtil.LOG_SCREEN)
        }
        itemMap.put(R.drawable.android_exit, ConfigKeyUtil.EXIT_APPLICATION)
        ModalNavigationDrawer(
            gesturesEnabled = App.gesturesEnabled,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(6.dp))
                    //头像
                    Avatar()
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
                            selected = u == App.selectedItem,
                            onClick = {
                                App.gesturesEnabled = true
                                scope.launch { drawerState.close() }
                                App.selectedItem = u
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            },
            content = {
                when (App.selectedItem) {
                    ConfigKeyUtil.LOGIN -> Login()
                    ConfigKeyUtil.MY_FILE -> MyFileScreen(fileViewModel)
                    ConfigKeyUtil.OFFLINE_DOWNLOAD -> OfflineDownloadScreen(
                        offlineFileViewModel,
                        fileViewModel
                    )

                    ConfigKeyUtil.OFFLINE_LIST -> OfflineFileScreen(
                        offlineFileViewModel,
                        fileViewModel
                    )

                    ConfigKeyUtil.WEB -> WebViewScreen()
                    ConfigKeyUtil.RECYCLE_BIN -> RecycleScreen(recycleViewModel)
                    ConfigKeyUtil.ADVANCED_SETTINGS -> {
                        App.gesturesEnabled = false
                        SettingScreenNew {
                            App.gesturesEnabled = true
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

    /**
     * 头像、网名、uid、已用空间
     */
    @Composable
    private fun Avatar() {
        val fileViewModel = viewModel<FileViewModel>()
        fileViewModel.getRemainingSpace()
        val remainingSpaceBean = fileViewModel.remainingSpace

        val avatarBean = remember {
            mutableStateOf(
                Gson().fromJson(
                    DataStoreUtil.getData(
                        ConfigKeyUtil.AVATAR_BEAN,
                        "{}"
                    ), AvatarBean::class.java
                )
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, // 水平居中
        ) {
            //头像
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(avatarBean.value.face)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .apply(block = fun ImageRequest.Builder.() {
                            scale(coil.size.Scale.FILL)
                            placeholder(R.drawable.avatar)
                        }).build()
                ),
                modifier = Modifier
                    .height(100.dp)
                    .width(100.dp)
                    //圆形裁剪
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                contentDescription = "",
            )
            Spacer(Modifier.height(6.dp))
            //用户名
            Text(
                text = avatarBean.value.userName,
                style = MaterialTheme.typography.titleMedium
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
                text = "总计${remainingSpaceBean.allTotalString}，已用${remainingSpaceBean.allUseString}，剩余${remainingSpaceBean.allRemainString}",
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
    private fun Login() {
        var isOpenLoginWebView by remember {
            mutableStateOf(false)
        }
        if (isOpenLoginWebView) {
            LoginWebViewScreen()
        }
        CookieDialog {
            if (it == "通过网页登陆") {
                isOpenLoginWebView = true
                return@CookieDialog
            }
            if (it != "") {
                val replace = it.replace(" ", "").replace("[\r\n]".toRegex(), "");
                thread {
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

    @Composable
    private fun MyFileScreen(
        fileViewModel: FileViewModel
    ) {
        fileViewModel.init()

        FileScreen(
            appBarClick(fileViewModel),
        )
    }

    private fun appBarClick(fileViewModel: FileViewModel) = fun(name: String) {
        val dialogSwitchUtil = DialogSwitchUtil.getInstance()
        when (name) {
//                "back"->{FileScreen里}
            //具体实现在AlertDialog#UnzipAllFile()里
            "unzipAllFile" -> {
                //fileViewModel.isOpenUnzipAllFileDialog = true
                unzipAllFile(fileViewModel)
            }

            "selectToUp" -> fileViewModel.selectToUp()
            "selectToDown" -> fileViewModel.selectToDown()
            "cut" -> fileViewModel.cut()
            //具体实现在FileScreen#CreateDialogs()里
            "search" -> dialogSwitchUtil.isOpenSearchDialog = true
            "delete" -> fileViewModel.deleteMultiple()
//            "selectAll" -> fileViewModel.selectAll()
            "selectReverse" -> fileViewModel.selectReverse()
            //具体实现在FileScreen#CreateDialogs()里
            "文件排序" -> dialogSwitchUtil.isOpenFileOrderDialog = true
            "刷新文件" -> fileViewModel.refresh()
        }
    }

    private fun unzipAllFile(fileViewModel: FileViewModel) {
        App.instance.toast("后台解压中......")
        val dataBuilder: Data.Builder = Data.Builder()
        val filter =
            fileViewModel.fileBeanList.filter { i -> i.isSelect && i.fileIco == R.drawable.zip }
                .map { a -> Pair<String, String>(a.name, a.pickCode) }.toList()
        val listType = object : TypeToken<List<Pair<String, String>>?>() {}.type
        val list = Gson().toJson(filter, listType)
//todo 支持批量解压带密码的压缩文件
//        if (it != "") {
//            dataBuilder.putString("pwd", it)
//        }
        dataBuilder.putString("list", list)
        dataBuilder.putString("cid", fileViewModel.currentCid)

        val request: OneTimeWorkRequest = OneTimeWorkRequest
            .Builder(UnzipAllFileWorker::class.java)
            .addTag("UnzipAllFileWorker")
            .setInputData(dataBuilder.build()).build()
        val workManager = WorkManager.getInstance(App.instance.applicationContext)
        workManager.enqueue(request)

        fileViewModel.recoverFromLongPress()
        fileViewModel.unSelect()

        workManager.getWorkInfoByIdLiveData(request.id).observe(this) {
            if (it != null && it.state.isFinished) {
                fileViewModel.refresh()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val fileViewModel = ViewModelProvider(this)[FileViewModel::class.java]
        fileViewModel.saveFileCache()
    }

    override fun onStop() {
        super.onStop()
        val fileViewModel = ViewModelProvider(this)[FileViewModel::class.java]
        fileViewModel.saveFileCache()
    }

    override fun onRestart() {
        super.onRestart()
        val fileViewModel = ViewModelProvider(this)[FileViewModel::class.java]
        fileViewModel.saveFileCache()
    }

}



