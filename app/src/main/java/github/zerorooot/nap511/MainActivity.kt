package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.factory.CookieViewModelFactory
import github.zerorooot.nap511.screen.CookieDialog
import github.zerorooot.nap511.screen.FileScreen
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.SharedPreferencesUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.launch

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

                    selectedItem = remember {
                        mutableStateOf(
                            SharedPreferencesUtil(this).get()?.let {
                                "我的文件"
                            } ?: kotlin.run {
                                "登录"
                            }
                        )
                    }
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
                                "我的文件" -> MyFileScreen()

                            }
                        }
                    )

                }
            }
        }
    }

    @Composable
    private fun Login(restart: Boolean = false) {
        CookieDialog {
            if (it != "") {
                SharedPreferencesUtil(this).save(it.replace(" ", ""))
                if (restart) {
                    finish()
                    startActivity(intent)
                } else {
                    selectedItem.value = "我的文件"
                }
            } else {
                Toast.makeText(this, "请输入cookie", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    private fun MyFileScreen() {
        val cookie = SharedPreferencesUtil(this).get() ?: kotlin.run {
            Login(true)
            return
        }

        val fileViewModel by viewModels<FileViewModel> {
            CookieViewModelFactory(
                cookie,
                this.application
            )
        }


        fileViewModel.init()

        FileScreen(
            fileViewModel,
            onItemClick(cookie, fileViewModel),
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


    private fun onItemClick(cookie: String, fileViewModel: FileViewModel) =
        fun(index: Int) {
            val fileBean = fileViewModel.fileBeanList[index]
            if (fileBean.isFolder) {
                fileViewModel.getFiles(fileBean.categoryId)
            }
            if (fileBean.isVideo == 1) {
                val intent = Intent(this, VideoActivity::class.java)
                intent.putExtra("cookie", cookie)
                intent.putExtra("title", fileBean.name)
                intent.putExtra("pick_code", fileBean.pickCode)
                startActivity(intent)
            }
        }

}



