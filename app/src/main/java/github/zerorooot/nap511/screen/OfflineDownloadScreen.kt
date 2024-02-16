package github.zerorooot.nap511.screen


import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.R
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel


@ExperimentalMaterial3Api
@Composable
fun OfflineDownloadScreen(
    offlineFileViewModel: OfflineFileViewModel,
    fileViewModel: FileViewModel
) {
    offlineFileViewModel.quota()
    val quotaBean by offlineFileViewModel.quotaBean.collectAsState()
    val path by fileViewModel.currentPath.collectAsState()
    val clickFun = { command: String, url: String ->
        when (command) {
            "sha1" -> {}
            "offline" -> {
                val urlList = url.split("\n").filter { i ->
                    i.startsWith("http", true) || i.startsWith(
                        "ftp",
                        true
                    ) || i.startsWith("magnet", true) || i.startsWith("ed2k", true)
                }.toList()
                offlineFileViewModel.addTask(urlList, fileViewModel.currentCid)
            }
        }
    }


    var urlText by remember {
        mutableStateOf("")
    }
    var urlCount by remember {
        mutableStateOf("链接")
    }
    Column {
        TopAppBar(
            title = {
                Text(text = stringResource(R.string.app_name))
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
            navigationIcon = {
                TopAppBarActionButton(
                    imageVector = Icons.Rounded.Menu,
                    description = "navigationIcon"
                ) {
                    App.instance.openDrawerState()
                }
            },
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = urlText,
                    label = { Text(text = urlCount) },
                    placeholder = { Text(text = "支持HTTP、HTTPS、FTP、磁力链和电驴链接，换行可添加多个") },
                    onValueChange = {
                        urlText = it
                        if (it != "") {
                            val size = it.split("\n")
                                .filter { i ->
                                    i.startsWith("http", true) || i.startsWith(
                                        "ftp",
                                        true
                                    ) || i.startsWith("magnet", true) || i.startsWith("ed2k", true)
                                }
                                .size
                            urlCount = "当前总共${size}个链接"
                        }
                    },
                    modifier = Modifier
                        .width(280.dp)
                        .heightIn(380.dp, 600.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "本月配额：剩${quotaBean.surplus}/总${quotaBean.count}个")
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = path,
                    label = { Text(text = "离线位置") },
                    readOnly = true,
                    onValueChange = { },
                    modifier = Modifier.width(280.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 28.dp),
                ) {
                    Button(onClick = {
                        clickFun.invoke("offline", urlText)
                        urlText = ""
                    }) {
                        Text(text = "开始离线下载")
                    }
                }
            }
        }
    }
}