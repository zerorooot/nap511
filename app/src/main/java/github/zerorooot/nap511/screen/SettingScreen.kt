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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.screenitem.EditTextPreferenceItem
import github.zerorooot.nap511.screenitem.ListPreferenceItem
import github.zerorooot.nap511.screenitem.PreferenceItem
import github.zerorooot.nap511.screenitem.SwitchPreferenceItem
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()

    val logEnabled by DataStoreUtil.getDataFlow(ConfigKeyUtil.LOG, false)
        .collectAsState(initial = false)
    val aria2Url by DataStoreUtil.getDataFlow(
        ConfigKeyUtil.ARIA2_URL,
        ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE
    ).collectAsState(initial = ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE)
    val aria2Token by DataStoreUtil.getDataFlow(ConfigKeyUtil.ARIA2_TOKEN, "")
        .collectAsState(initial = "")
    val autoRotateEnabled by DataStoreUtil.getDataFlow(ConfigKeyUtil.AUTO_ROTATE, false)
        .collectAsState(initial = false)

    val hideLoadingView by DataStoreUtil.getDataFlow(ConfigKeyUtil.HIDE_LOADING_VIEW, false)
        .collectAsState(initial = false)

    val earlyLoading by DataStoreUtil.getDataFlow(ConfigKeyUtil.EARLY_LOADING, false)
        .collectAsState(initial = false)

    val saveRequestCache by DataStoreUtil.getDataFlow(ConfigKeyUtil.SAVE_REQUEST_CACHE, true)
        .collectAsState(initial = true)

    val positionAfterAt by DataStoreUtil.getDataFlow(ConfigKeyUtil.POSITION_AFTER_AT, false)
        .collectAsState(initial = false)

    val torrentSort by DataStoreUtil.getDataFlow(ConfigKeyUtil.TORRENT_SORT, false)
        .collectAsState(initial = false)
    val currentOfflineTask by DataStoreUtil.getDataFlow(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
        .collectAsState(initial = "")
    // EditText 变量状态管理
    val uid by DataStoreUtil.getDataFlow(ConfigKeyUtil.UID, "0")
        .collectAsState(initial = "0")

    val cookie by DataStoreUtil.getDataFlow(ConfigKeyUtil.COOKIE, "cookie")
        .collectAsState(initial = "cookie")

    val password by DataStoreUtil.getDataFlow(ConfigKeyUtil.PASSWORD, "")
        .collectAsState(initial = "")

    val requestLimitCount by DataStoreUtil.getDataFlow(ConfigKeyUtil.REQUEST_LIMIT_COUNT, "200")
        .collectAsState(initial = "200")

    val defaultOfflineCid by DataStoreUtil.getDataFlow(ConfigKeyUtil.DEFAULT_OFFLINE_CID, "")
        .collectAsState(initial = "")
    val fabPosition by DataStoreUtil.getDataFlow(
        ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION,
        "End"
    ).collectAsState(initial = "End")

    val fabArray = stringArrayResource(R.array.floatingActionButtonPosition)

    val moveFailFile by DataStoreUtil.getDataFlow(ConfigKeyUtil.MOVE_FAIL_FILE, "")
        .collectAsState(initial = "")

    val defaultOfflineTime by DataStoreUtil.getDataFlow(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5")
        .collectAsState(initial = "5")

    fun <T : Any> saveDate(key: String, newValue: T) {
        coroutineScope.launch {
            DataStoreUtil.putDataSuspend(
                key,
                newValue
            ) // 保存后 logEnabled 会自动刷新 UI
        }
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
                            saveDate(ConfigKeyUtil.COOKIE, it)
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
                            saveDate(ConfigKeyUtil.PASSWORD, it)
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
                            saveDate(ConfigKeyUtil.ARIA2_URL, it)
                        }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "aria2秘钥",
                        summary = aria2Token.ifEmpty { "没有留空即可" },
                        value = aria2Token,
                        onValueSave = {
                            saveDate(ConfigKeyUtil.ARIA2_TOKEN, it)
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
                            saveDate(ConfigKeyUtil.REQUEST_LIMIT_COUNT, it)
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
                            saveDate(ConfigKeyUtil.DEFAULT_OFFLINE_CID, it)
                        }
                    )
                }
                item {
                    ListPreferenceItem(
                        title = "浮动按钮位置",
                        value = fabPosition,
                        entries = fabArray,
                        entryValues = fabArray,
                        onValueSave = {
                            saveDate(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION, it)
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
                            saveDate(ConfigKeyUtil.MOVE_FAIL_FILE, it)
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
                            saveDate(ConfigKeyUtil.LOG, it)
                        }

                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "屏幕自动旋转",
                        summary = "根据视频横竖自动旋转屏幕",
                        checked = autoRotateEnabled,
                        onCheckedChange = {
                            saveDate(ConfigKeyUtil.AUTO_ROTATE, it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "隐藏视频加载提示",
                        summary = "当视频正在加载时，隐藏加载提示",
                        checked = hideLoadingView,
                        onCheckedChange = {
                            saveDate(ConfigKeyUtil.HIDE_LOADING_VIEW, it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "提前加载",
                        summary = "进入下级目录时，提前加载当前文件夹上下两个文件夹内的文件",
                        checked = earlyLoading,
                        onCheckedChange = {
                            saveDate(ConfigKeyUtil.EARLY_LOADING, it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "保存请求缓存",
                        summary = "保存请求文件缓存到application.cacheDir/fileListCache.json中，便于提升加载速度",
                        checked = saveRequestCache,
                        onCheckedChange = {
                            saveDate(ConfigKeyUtil.SAVE_REQUEST_CACHE, it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "光标重定位",
                        summary = "重命名时，光标定位在'@'或'空格'后",
                        checked = positionAfterAt,
                        onCheckedChange = {
                            saveDate(ConfigKeyUtil.POSITION_AFTER_AT, it)
                        }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "种子排序",
                        summary = "种子文件按文件大小从大到小排序",
                        checked = torrentSort,
                        onCheckedChange = {
                            saveDate(ConfigKeyUtil.TORRENT_SORT, it)
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
                            saveDate(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, it)
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
                    EditTextPreferenceItem(
                        title = "离线任务缓存",
                        summary = currentOfflineTask.ifEmpty { "当前尚未添加离线任务的链接" },
                        value = currentOfflineTask,
                        onValueSave = {
                            saveDate(ConfigKeyUtil.CURRENT_OFFLINE_TASK, it)
                        }
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