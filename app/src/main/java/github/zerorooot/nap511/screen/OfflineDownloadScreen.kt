package github.zerorooot.nap511.screen



import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    val screenWidthDp = with(density) { containerSize.width.toDp() }
    val screenHeightDp =  with(density) { containerSize.height.toDp() }
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

    val minHeightPercentage = 0.5f // 最小高度百分比
    val maxHeightPercentage = 0.65f // 最大高度百分比

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
                        //LocalConfiguration.current.screenWidthDp
                        .width((maxHeightPercentage * screenWidthDp.value).dp)
                        .heightIn(
                            //LocalConfiguration.current.screenHeightDp
                            min = (minHeightPercentage * screenHeightDp.value).dp,
                            max = (maxHeightPercentage * screenHeightDp.value).dp
                        )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "本月配额：剩${quotaBean.surplus}/总${quotaBean.count}个")
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = path,
                    label = { Text(text = "离线位置") },
                    readOnly = true,
                    onValueChange = { },
                    modifier = Modifier.width((maxHeightPercentage * screenWidthDp.value).dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 28.dp),
                ) {
                    Button(onClick = {
                        clickFun.invoke("offline", urlText)
                        urlText = ""
                        urlCount = "链接"
                    }) {
                        Text(text = "开始离线下载")
                    }
                }
            }
        }
    }
}