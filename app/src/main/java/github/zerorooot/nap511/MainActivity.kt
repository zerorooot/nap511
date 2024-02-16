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
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.gson.Gson
import com.google.gson.JsonElement
import github.zerorooot.nap511.bean.LoginBean
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
import okhttp3.OkHttpClient
import okhttp3.Request
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
                    Init(cookie)
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
            R.drawable.ic_baseline_delete_24 to "回收站",
            R.drawable.baseline_settings_24 to "高级设置",
            R.drawable.baseline_web_24 to "网页版",
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
                    "我的文件" -> MyFileScreen(fileViewModel)
                    "photo" -> {
                        MyPhotoScreen(fileViewModel)
                    }
                    "离线下载" -> OfflineDownloadScreen(offlineFileViewModel, fileViewModel)
                    "离线列表" -> OfflineFileScreen(offlineFileViewModel, fileViewModel)
                    "回收站" -> RecycleScreen(recycleViewModel)
                    "高级设置" -> SettingScreen()
                    "captchaWebView" -> CaptchaWebViewScreen()
                    "网页版" -> WebViewScreen()
                    "loginWebView" -> LoginWebViewScreen()
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
                    val checkLogin = checkLogin(replace)
                    val message = if (checkLogin == "") {
                        "登录失败~，请重试"
                    } else {
                        DataStoreUtil.putData(ConfigUtil.cookie, replace)
                        DataStoreUtil.putData(ConfigUtil.uid, checkLogin)
                        App.cookie = replace
//                        App.isInit = true
                        "登陆成功!!"
                    }
                    App.instance.toast(message)
                }
            } else {
                App.instance.toast("请输入cookie")
            }
        }
    }

    @Composable
    private fun MyFileScreen(fileViewModel: FileViewModel) {
        fileViewModel.init()

        FileScreen(
            fileViewModel,
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
            "search" -> fileViewModel.isSearch = true
            "delete" -> fileViewModel.deleteMultiple()
            "selectAll" -> fileViewModel.selectAll()
            "selectReverse" -> fileViewModel.selectReverse()
            //具体实现在FileScreen#CreateDialogs()里
            "文件排序" -> fileViewModel.isOpenFileOrderDialog = true
            "刷新文件" -> fileViewModel.refresh()
        }
    }

    private fun checkLogin(cookie: String): String {
        val url =
            "https://passportapi.115.com/app/1.0/web/1.0/check/sso?_${System.currentTimeMillis() / 1000}"
        val okHttpClient = OkHttpClient()
        val request: Request = Request.Builder().url(url).addHeader("cookie", cookie)
            .addHeader("Content-Type", "application/json; Charset=UTF-8").addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            ).get().build()
        val body = okHttpClient.newCall(request).execute().body
        val uid = if (body != null) {
            val string = body.string()
            try {
                Gson().fromJson(
                    string, LoginBean::class.java
                ).data.user_id
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
        return uid
    }
}



