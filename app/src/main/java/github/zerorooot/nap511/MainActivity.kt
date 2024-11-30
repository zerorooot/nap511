package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.activity.OfflineTaskWorker
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.factory.CookieViewModelFactory
import github.zerorooot.nap511.screen.CaptchaVideoWebViewScreen
import github.zerorooot.nap511.screen.CaptchaWebViewScreen
import github.zerorooot.nap511.screen.CookieDialog
import github.zerorooot.nap511.screen.ExitApp
import github.zerorooot.nap511.screen.FileScreen
import github.zerorooot.nap511.screen.LoginWebViewScreen
import github.zerorooot.nap511.screen.MyPhotoScreen
import github.zerorooot.nap511.screen.OfflineDownloadScreen
import github.zerorooot.nap511.screen.OfflineFileScreen
import github.zerorooot.nap511.screen.RecycleScreen
import github.zerorooot.nap511.screen.SettingScreen
import github.zerorooot.nap511.screen.WebViewScreen
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
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
                    if (intent.action == "check") {
                        App.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
                        intent.action = ""
                    }
                    //跳转到默认下载目录
                    if (intent.action == "jump") {
                        viewModels<FileViewModel>().value.getFiles(
                            DataStoreUtil.getData(
                                ConfigKeyUtil.DEFAULT_OFFLINE_CID,
                                "0"
                            )
                        )
                        //清除action,不然会一直跳转到默认下载目录
                        intent.action = ""
                    }
                    //检测未上传的磁力链接
                    checkOfflineTask(cookie)
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
        BackHandler(App.selectedItem != ConfigKeyUtil.MY_FILE) {
            App.selectedItem = ConfigKeyUtil.MY_FILE
            visible = true
        }

        MyNavigationDrawer(fileViewModel, offlineFileViewModel, recycleViewModel)
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
                WorkInfo.State.FAILED,
                WorkInfo.State.BLOCKED,
                WorkInfo.State.CANCELLED
            )
        ).build()
        val workInfos: List<WorkInfo> =
            WorkManager.getInstance(applicationContext).getWorkInfos(workQuery).get()
        val size = workInfos.size
        Log.d(
            "nap511 main activity",
            "checkOfflineTask workManager size $size workInfos $workInfos"
        )
        if (size != 0) {
            return
        }
        //size等于0,证明后台没有正在添加离线链接
        val currentOfflineTask = DataStoreUtil.getData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
            .split("\n")
            .filter { i -> i != "" && i != " " }
            .toSet()
            .toMutableList()
        Log.d(
            "nap511 main activity",
            "checkOfflineTask currentOfflineTask size ${currentOfflineTask.size}"
        )

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
            R.drawable.baseline_login_24 to ConfigKeyUtil.LOGIN,
            R.drawable.baseline_cloud_24 to ConfigKeyUtil.MY_FILE,
            R.drawable.baseline_cloud_download_24 to ConfigKeyUtil.OFFLINE_DOWNLOAD,
            R.drawable.baseline_cloud_done_24 to ConfigKeyUtil.OFFLINE_LIST,
            R.drawable.baseline_video_moderator_24 to ConfigKeyUtil.VERIFY_VIDEO_ACCOUNT,
            R.drawable.baseline_magent_moderator_24 to ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT,
//            R.drawable.baseline_web_24 to "ConfigUtil.WEB",
            R.drawable.ic_baseline_delete_24 to ConfigKeyUtil.RECYCLE_BIN,
            R.drawable.baseline_settings_24 to ConfigKeyUtil.ADVANCED_SETTINGS,
            R.drawable.android_exit to ConfigKeyUtil.EXIT_APPLICATION
        )
        ModalNavigationDrawer(gesturesEnabled = App.gesturesEnabled,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(6.dp))
                    //头像
                    Avatar()
                    //菜单栏
                    Spacer(Modifier.height(6.dp))
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
                    ConfigKeyUtil.ADVANCED_SETTINGS -> SettingScreen()
                    ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT -> {
                        CaptchaWebViewScreen()
                    }

                    ConfigKeyUtil.VERIFY_VIDEO_ACCOUNT -> {
                        CaptchaVideoWebViewScreen()
                    }

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
        val remainingSpaceBean = remember {
            mutableStateOf(
                Gson().fromJson(
                    DataStoreUtil.getData(
                        ConfigKeyUtil.REMAINING_SPACE,
                        "{}"
                    ), RemainingSpaceBean::class.java
                )
            )
        }
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
                text = "总计${remainingSpaceBean.value.allTotalString}，已用${remainingSpaceBean.value.allUseString}，剩余${remainingSpaceBean.value.allRemainString}",
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



