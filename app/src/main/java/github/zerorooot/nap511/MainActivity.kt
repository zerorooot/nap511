package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.activity.OfflineTaskWorker
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.TorrentFileListWeb
import github.zerorooot.nap511.factory.CookieViewModelFactory
import github.zerorooot.nap511.screen.*
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import kotlinx.coroutines.launch
import kotlin.concurrent.thread


class MainActivity : ComponentActivity() {
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
                    //直接添加磁力，但提示请验证账号;跳转到验证账号界面
                    if (intent.action == "jump") {
                        App.selectedItem = "验证账号"
                    }
                }
            }
        }
    }

    @Composable
    private fun Init(cookie: String) {
        val fileViewModel by viewModels<FileViewModel> {
            CookieViewModelFactory(
                cookie, this.application
            )
        }
        val offlineFileViewModel by viewModels<OfflineFileViewModel> {
            CookieViewModelFactory(
                cookie, this.application
            )
        }
        val recycleViewModel by viewModels<RecycleViewModel> {
            CookieViewModelFactory(
                cookie, this.application
            )
        }
        //恢复因MyPhotoScreen而造成的isSystemBarsVisible为false的情况
        var visible by remember {
            mutableStateOf(true)
        }
        rememberSystemUiController().apply {
            isSystemBarsVisible = visible
        }
        BackHandler(App.selectedItem == "photo" || App.selectedItem == "webView") {
            App.selectedItem = "我的文件"
            visible = true
        }

        MyNavigationDrawer(fileViewModel, offlineFileViewModel, recycleViewModel)

        checkOfflineTask(cookie)

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
                WorkInfo.State.SUCCEEDED,
                WorkInfo.State.FAILED,
                WorkInfo.State.BLOCKED,
                WorkInfo.State.CANCELLED
            )
        ).build()
        val workInfos: List<WorkInfo> =
            WorkManager.getInstance(applicationContext).getWorkInfos(workQuery).get()
        val size = workInfos.size
        println("checkOfflineTask workManager size $size workInfos $workInfos")
        if (size != 0) {
            return
        }
        //size等于0,证明后台没有正在添加离线链接
        val currentOfflineTask = DataStoreUtil.getData(ConfigUtil.currentOfflineTask, "")
            .split("\n")
            .filter { i -> i != "" && i != " " }
            .toSet()
            .toMutableList()
        println("checkOfflineTask currentOfflineTask size ${currentOfflineTask.size}")
        //currentOfflineTask等于0,证明没有离线链接缓存
        if (currentOfflineTask.size == 0) {
            return
        }
        //添加离线链接
        val listType = object : TypeToken<List<String?>?>() {}.type
        val list = Gson().toJson(currentOfflineTask, listType)
        val data: Data =
            Data.Builder().putString("cookie", cookie).putString("list", list)
                .build()
        val request: OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java).setInputData(data)
                .build()
//            WorkManager.getInstance(applicationContext).enqueue(request)
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "addOfflineTaskByCount",
                ExistingWorkPolicy.APPEND,
                request
            )

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
            R.drawable.baseline_login_24 to "登录",
            R.drawable.baseline_cloud_24 to "我的文件",
            R.drawable.baseline_cloud_download_24 to "离线下载",
            R.drawable.baseline_cloud_done_24 to "离线列表",
//            R.drawable.baseline_add_moderator_24 to "验证video账号",
//            R.drawable.baseline_web_24 to "网页版",
            R.drawable.ic_baseline_delete_24 to "回收站",
            R.drawable.baseline_settings_24 to "高级设置",
            R.drawable.android_exit to "退出应用"
        )

        ModalNavigationDrawer(gesturesEnabled = App.gesturesEnabled,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    itemMap.forEach { (t, u) ->
                        NavigationDrawerItem(icon = {
                            Icon(
                                painterResource(t), contentDescription = u
                            )
                        }, label = { Text(u) }, selected = u == App.selectedItem, onClick = {
                            App.gesturesEnabled = true
                            scope.launch { drawerState.close() }
                            App.selectedItem = u
                        }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            },
            content = {
                when (App.selectedItem) {
                    "登录" -> Login()
                    "我的文件" -> MyFileScreen(offlineFileViewModel, fileViewModel)
                    "离线下载" -> OfflineDownloadScreen(offlineFileViewModel, fileViewModel)
                    "离线列表" -> OfflineFileScreen(offlineFileViewModel, fileViewModel)
                    "网页版" -> WebViewScreen()
                    "回收站" -> RecycleScreen(recycleViewModel)
                    "高级设置" -> SettingScreen()
                    "验证账号" -> {
                        App.captchaUrl =
                            "https://captchaapi.115.com/?ac=security_code&type=web&cb=Close911_" + System.currentTimeMillis()
                        CaptchaWebViewScreen()
                    }

                    "验证video账号" -> {
                        CaptchaVideoWebViewScreen()
                    }

                    "退出应用" -> ExitApp()
                    "captchaWebView" -> CaptchaWebViewScreen()
                    "loginWebView" -> LoginWebViewScreen()
                    "photo" -> {
                        MyPhotoScreen(fileViewModel)
                    }
                }
            })
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
                    val checkLogin = App().checkLogin(replace)
                    val message = if (checkLogin == "0") {
                        "登录失败~，请重试"
                    } else {
                        DataStoreUtil.putData(ConfigUtil.cookie, replace)
                        DataStoreUtil.putData(ConfigUtil.uid, checkLogin)
                        App.cookie = replace
//                        App.isInit = true
                        "登陆成功,请重启应用！"
                    }
                    App.instance.toast(message)
                }
            } else {
                App.instance.toast("请输入cookie")
            }
        }
    }

    @Composable
    private fun MyFileScreen(
        offlineFileViewModel: OfflineFileViewModel,
        fileViewModel: FileViewModel
    ) {
        fileViewModel.init()

        FileScreen(
            fileViewModel,
            offlineFileViewModel,
            appBarClick(fileViewModel),
        )
    }

    private fun appBarClick(fileViewModel: FileViewModel) = fun(name: String) {
        when (name) {
//                "back"->{FileScreen里}
            "selectToUp" -> fileViewModel.selectToUp()
            "selectToDown" -> fileViewModel.selectToDown()
            "cut" -> fileViewModel.cut()
            //具体实现在FileScreen#CreateDialogs()里
            "search" -> fileViewModel.isOpenSearchDialog = true
            "delete" -> fileViewModel.deleteMultiple()
            "selectAll" -> fileViewModel.selectAll()
            "selectReverse" -> fileViewModel.selectReverse()
            //具体实现在FileScreen#CreateDialogs()里
            "文件排序" -> fileViewModel.isOpenFileOrderDialog = true
            "刷新文件" -> fileViewModel.refresh()
        }
    }
}



