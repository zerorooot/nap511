package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.gson.Gson
import com.google.gson.JsonElement
import github.zerorooot.nap511.factory.FileCookieViewModelFactory
import github.zerorooot.nap511.factory.OfflineFileCookieViewModelFactory
import github.zerorooot.nap511.screen.*
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.SharedPreferencesUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread


class MainActivity : ComponentActivity() {
    private lateinit var selectedItem: MutableState<String>

    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Nap511Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val cookie = SharedPreferencesUtil(this).get("cookie")
                    selectedItem = remember {
                        mutableStateOf("我的文件")
                    }
                    if (cookie == null) {
                        Login()
                        return@Surface
                    }

                    val fileViewModel by viewModels<FileViewModel> {
                        FileCookieViewModelFactory(
                            cookie,
                            this.application
                        )
                    }
                    val offlineFileViewModel by viewModels<OfflineFileViewModel> {
                        OfflineFileCookieViewModelFactory(
                            cookie,
                            this.application
                        )
                    }

                    //恢复因MyPhotoScreen而造成的isSystemBarsVisible为false的情况
                    var visible by remember {
                        mutableStateOf(true)
                    }
                    rememberSystemUiController().apply {
                        isSystemBarsVisible = visible
                    }
                    BackHandler(selectedItem.value == "photo") {
                        selectedItem.value = "我的文件"
                        visible = true
                    }

                    MyNavigationDrawer(fileViewModel, offlineFileViewModel)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MyNavigationDrawer(
        fileViewModel: FileViewModel,
        offlineFileViewModel: OfflineFileViewModel
    ) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val itemMap = linkedMapOf(
            R.drawable.baseline_login_24 to "登录",
            R.drawable.baseline_cloud_24 to "我的文件",
            R.drawable.baseline_cloud_download_24 to "离线下载",
            R.drawable.baseline_cloud_done_24 to "离线列表",
            R.drawable.baseline_settings_24 to "高级设置",
            R.drawable.ic_baseline_delete_24 to "回收站"
        )

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    itemMap.forEach { (t, u) ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    painterResource(t),
                                    contentDescription = u
                                )
                            },
                            label = { Text(u) },
                            selected = u == selectedItem.value,
                            onClick = {
                                scope.launch { drawerState.close() }
                                selectedItem.value = u
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            },
            content = {
                when (selectedItem.value) {
                    "登录" -> Login()
                    "我的文件" -> MyFileScreen(fileViewModel)
                    "photo" -> {
                        MyPhotoScreen(fileViewModel)
                    }
                    "离线下载" -> OfflineDownloadScreen(fileViewModel)
                    "离线列表" -> MyOfflineScreen(offlineFileViewModel)
                }
            }
        )
    }

    @Composable
    private fun Login() {
        val context = LocalContext.current
        CookieDialog {
            if (it != "") {
                val replace = it.replace(" ", "").replace("\r|\n".toRegex(), "");
                thread {
                    val checkLogin = checkLogin(replace)
                    if (checkLogin == "") {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "登录失败~", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        SharedPreferencesUtil(context).save("cookie", replace)
                        SharedPreferencesUtil(context).save("uid", checkLogin)
                    }
                    finish()
                    startActivity(intent)
                }

            } else {
                Toast.makeText(this, "请输入cookie", Toast.LENGTH_SHORT).show()
                selectedItem.value = "我的文件"
            }
        }
    }

    //todo click action
    @Composable
    private fun MyOfflineScreen(offlineFileViewModel: OfflineFileViewModel) {
        OfflineFileScreen(offlineFileViewModel, {}, { _, _ -> },{})
    }

    @Composable
    private fun MyFileScreen(fileViewModel: FileViewModel) {
        fileViewModel.init()

        FileScreen(
            fileViewModel,
            selectedItem,
            appBarClick(fileViewModel),
        )
    }

    private fun appBarClick(fileViewModel: FileViewModel) =
        fun(name: String) {
            when (name) {
//                "back"->{FileScreen里}
                "cut" -> fileViewModel.cut()
                "search" -> {}
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
        val request: Request = Request
            .Builder()
            .url(url)
            .addHeader("cookie", cookie)
            .addHeader("Content-Type", "application/json; Charset=UTF-8")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            )
            .get()
            .build()
        val body = okHttpClient.newCall(request).execute().body
        val uid = if (body != null) {
            val string = body.string()
            try {
                Gson().fromJson(
                    string,
                    JsonElement::class.java
                ).asJsonObject.getAsJsonObject("data").get("user_id").asString
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
        return uid
    }
}



