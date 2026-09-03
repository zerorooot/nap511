package github.zerorooot.nap511.screen

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineDownloadScreen(
    offlineFileViewModel: OfflineFileViewModel,
    currentCid: String,
    path: String,
    onClick: () -> Unit,
    onNav: () -> Unit
) {
    LaunchedEffect(Unit) {
        offlineFileViewModel.quota()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    val screenWidthDp = remember { with(density) { containerSize.width.toDp() } }
    val screenHeightDp = remember { with(density) { containerSize.height.toDp() } }

    val quotaBean by offlineFileViewModel.quotaBean.collectAsState()
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
                offlineFileViewModel.addTask(urlList, currentCid) {
                    if (it) {
                        onNav.invoke()
                    }
                }
            }
        }
    }

    val minHeightPercentage = 0.5f // 最小高度百分比
    val maxHeightPercentage = 0.65f // 最大高度百分比

    var urlText by offlineFileViewModel.urlText
    var urlCount by remember {
        mutableStateOf("链接")
    }

    fun onUrlTextChange(it: String) {
        urlText = it
        if (it.isNotBlank()) {
            val size = it.split("\n")
                .filter { i ->
                    i.startsWith("http", true) || i.startsWith(
                        "ftp",
                        true
                    ) || i.startsWith("magnet", true) || i.startsWith("ed2k", true)
                }
                .size
            urlCount = "当前总共${size}个链接"
        } else {
            urlCount = "链接"
        }
    }
    LaunchedEffect(Unit) {
        onUrlTextChange(urlText)
    }

    val onStartDownload = {
        clickFun.invoke("offline", urlText)
        urlText = ""
        urlCount = "链接"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                title = {
                    Text(text = ConfigKeyUtil.OFFLINE_DOWNLOAD)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    TopAppBarActionButton(
                        imageVector = Icons.Rounded.Menu,
                        description = "navigationIcon"
                    ) {
                        onClick.invoke()
                    }
                },
            )
        }
    ) { innerPadding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding()
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlText,
                    label = { Text(text = urlCount) },
                    placeholder = { Text(text = "支持HTTP、HTTPS、FTP、磁力链和电驴链接，换行可添加多个") },
                    onValueChange = ::onUrlTextChange,
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    OutlinedTextField(
                        value = path,
                        label = { Text(text = "离线位置") },
                        readOnly = true,
                        onValueChange = { },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "本月配额：剩${quotaBean.surplus}/总${quotaBean.count}个")
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onStartDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "开始离线下载")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding()
                    )
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = urlText,
                    label = { Text(text = urlCount) },
                    placeholder = { Text(text = "支持HTTP、HTTPS、FTP、磁力链和电驴链接，换行可添加多个") },
                    onValueChange = ::onUrlTextChange,
                    modifier = Modifier
                        //LocalConfiguration.current.screenWidthDp
                        .width((maxHeightPercentage * screenWidthDp.value).dp)
                        .heightIn(
                            min = (minHeightPercentage * screenHeightDp.value).dp,
                            max = (maxHeightPercentage * screenHeightDp.value).dp
                        )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "本月配额：剩${quotaBean.surplus}/总${quotaBean.count}个")
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = path,
                    label = { Text(text = "离线位置") },
                    readOnly = true,
                    onValueChange = { },
                    modifier = Modifier.width((maxHeightPercentage * screenWidthDp.value).dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onStartDownload,
//                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "开始离线下载")
                }
            }
        }
    }
}