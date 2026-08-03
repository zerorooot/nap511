package github.zerorooot.nap511.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.screenitem.EditTextPreferenceItem
import github.zerorooot.nap511.screenitem.PreferenceItem
import github.zerorooot.nap511.screenitem.SwitchPreferenceItem
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    fileViewModel: FileViewModel,
    topAppBarActionButtonOnClick: () -> Unit,
    onNav: (Route) -> Unit
) {
    val listState = rememberLazyListState()
    var logEnabled by remember { mutableStateOf(DataStoreUtil.getData(ConfigKeyUtil.LOG, false)) }
    var autoRotateEnabled by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.AUTO_ROTATE,
                false
            )
        )
    }
    var hideLoadingView by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.HIDE_LOADING_VIEW,
                false
            )
        )
    }
    var earlyLoading by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.EARLY_LOADING,
                false
            )
        )
    }
    var saveRequestCache by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.SAVE_REQUEST_CACHE,
                true
            )
        )
    }
    var positionAfterAt by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.POSITION_AFTER_AT,
                false
            )
        )
    }
    var torrentSort by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.TORRENT_SORT,
                false
            )
        )
    }

    // EditText 变量状态管理
    var uid by remember { mutableStateOf(DataStoreUtil.getData(ConfigKeyUtil.UID, "0")) }
    var cookie by remember { mutableStateOf(DataStoreUtil.getData(ConfigKeyUtil.COOKIE, "cookie")) }
    var password by remember { mutableStateOf(DataStoreUtil.getData(ConfigKeyUtil.PASSWORD, "")) }
    var aria2Url by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.ARIA2_URL,
                ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE
            )
        )
    }
    var requestLimitCount by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.REQUEST_LIMIT_COUNT,
                "200"
            )
        )
    }
    var defaultOfflineCid by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.DEFAULT_OFFLINE_CID,
                ""
            )
        )
    }
    var moveFailFile by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.MOVE_FAIL_FILE,
                ""
            )
        )
    }
    var defaultOfflineTime by remember {
        mutableStateOf(
            DataStoreUtil.getData(
                ConfigKeyUtil.DEFAULT_OFFLINE_TIME,
                "5"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = ConfigKeyUtil.ADVANCED_SETTINGS) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
                navigationIcon = {
                    TopAppBarActionButton(
                        imageVector = Icons.Rounded.Menu,
                        description = "navigationIcon",
                        onClick = topAppBarActionButtonOnClick
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumnScrollbar(
            state = listState,
            settings = ScrollbarSettings.Default.copy(
                thumbUnselectedColor = Purple80
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                state = listState
            ) {
                // 1. 用户 ID（禁用状态）
                item {
                    EditTextPreferenceItem(
                        title = "用户id",
                        summary = uid,
                        value = uid,
                        enabled = false,
                        onValueSave = {}
                    )
                }

                // 2. Cookie & 密码设置
                item {
                    EditTextPreferenceItem(
                        title = "登录Cookie",
                        summary = "点击更改",
                        value = cookie,
                        onValueSave = {
                            cookie = it
                            DataStoreUtil.putData("cookie", it)
                        }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "数字安全密钥",
                        summary = "清空回收站文件时输入的密码",
                        value = password,
                        isNumber = true,
                        onValueSave = {
                            password = it
                            DataStoreUtil.putData(ConfigKeyUtil.PASSWORD, it)
                        }
                    )
                }

                // 3. Aria2 地址设置
                item {
                    EditTextPreferenceItem(
                        title = "aria2地址",
                        summary = aria2Url,
                        value = aria2Url,
                        onValueSave = {
                            aria2Url = it
                            DataStoreUtil.putData(ConfigKeyUtil.ARIA2_URL, it)
                        }
                    )
                }

                // 4. 请求限制数
                item {
                    EditTextPreferenceItem(
                        title = "每次请求文件数",
                        summary = requestLimitCount,
                        value = requestLimitCount,
                        isNumber = true,
                        onValueSave = {
                            requestLimitCount = it
                            DataStoreUtil.putData(ConfigKeyUtil.REQUEST_LIMIT_COUNT, it)
                        }
                    )
                }

                // 5. 默认离线位置
                item {
                    val summaryText =
                        defaultOfflineCid.ifEmpty { "输入文件夹cid，长按目录可复制当前目录cid" }
                    EditTextPreferenceItem(
                        title = "默认离线位置",
                        summary = summaryText,
                        value = defaultOfflineCid,
                        onValueSave = {
                            defaultOfflineCid = it
                            DataStoreUtil.putData(ConfigKeyUtil.DEFAULT_OFFLINE_CID, it)
                        }
                    )
                }

                // 6. 移动失败文件文件夹
                item {
                    val summaryText = if (moveFailFile.isEmpty()) {
                        "后台解压失败后，压缩包将移动至解压目录此名称的文件夹中，留空则不移动"
                    } else {
                        "后台解压失败后，压缩包将移动至\"解压目录\\$moveFailFile\"中"
                    }
                    EditTextPreferenceItem(
                        title = "解压失败存放文件夹",
                        summary = summaryText,
                        value = moveFailFile,
                        onValueSave = {
                            moveFailFile = it
                            DataStoreUtil.putData(ConfigKeyUtil.MOVE_FAIL_FILE, it)
                        }
                    )
                }

                // 7. 账号验证按钮
                item {
                    PreferenceItem(
                        title = "视频播放验证",
                        summary = "打开视频播放异常验证页面",
                        onClick = {
                            onNav.invoke(Route.VerifyVideoAccount)
                        }
                    )
                }
                item {
                    PreferenceItem(
                        title = "磁力链接验证",
                        summary = "打开磁力链接添加异常验证页面",
                        onClick = {
                            onNav.invoke(Route.VerifyMagnetLinkAccount)
                        }
                    )
                }

                // 8. 开关配置（Switches）
                item {
                    SwitchPreferenceItem(
                        title = "日志记录",
                        summary = "输出程序中部分关键节点内容，方便调试",
                        checked = logEnabled,
                        onCheckedChange = {
                            logEnabled = it
                            DataStoreUtil.putData("log", it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "屏幕自动旋转",
                        summary = "根据视频横竖自动旋转屏幕",
                        checked = autoRotateEnabled,
                        onCheckedChange = {
                            autoRotateEnabled = it
                            DataStoreUtil.putData("autoRotate", it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "隐藏视频加载提示",
                        summary = "当视频正在加载时，隐藏加载提示",
                        checked = hideLoadingView,
                        onCheckedChange = {
                            hideLoadingView = it
                            DataStoreUtil.putData("hideLoadingView", it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "提前加载",
                        summary = "进入下级目录时，提前加载当前文件夹上下两个文件夹内的文件",
                        checked = earlyLoading,
                        onCheckedChange = {
                            earlyLoading = it
                            DataStoreUtil.putData("EarlyLoading", it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "保存请求缓存",
                        summary = "保存请求文件缓存到application.cacheDir/fileListCache.json中，便于提升加载速度",
                        checked = saveRequestCache,
                        onCheckedChange = {
                            saveRequestCache = it
                            DataStoreUtil.putData("SaveRequestCache", it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "光标重定位",
                        summary = "重命名时，光标定位在'@'或'空格'后",
                        checked = positionAfterAt,
                        onCheckedChange = {
                            positionAfterAt = it
                            DataStoreUtil.putData("PositionAfterAt", it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "种子排序",
                        summary = "种子文件按文件大小从大到小排序",
                        checked = torrentSort,
                        onCheckedChange = {
                            torrentSort = it
                            DataStoreUtil.putData("TorrentSort", it)
                        }
                    )
                }

                // 9. 离线延迟时间
                item {
                    EditTextPreferenceItem(
                        title = "离线任务延迟时间",
                        summary = "延迟${defaultOfflineTime}分钟后统一离线下载",
                        value = defaultOfflineTime,
                        isNumber = true,
                        onValueSave = {
                            defaultOfflineTime = it
                            DataStoreUtil.putData(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, it)
                        }
                    )
                }

                // 10. 立即下载与重启应用
                item {
                    PreferenceItem(
                        title = "立即下载",
                        summary = "立刻下载缓存的离线任务",
                        onClick = { fileViewModel.handleOfflineTask(true) }
                    )
                }
                item {
                    PreferenceItem(
                        title = "重启应用",
                        summary = "点我重新启动应用",
                        onClick = {
                            App.instance.toast("重启中～")
                            ProcessPhoenix.triggerRebirth(App.instance)
                        }
                    )
                }
            }
        }
    }
}