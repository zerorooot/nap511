package github.zerorooot.nap511.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.screenitem.EditTextPreferenceItem
import github.zerorooot.nap511.screenitem.ListPreferenceItem
import github.zerorooot.nap511.screenitem.PreferenceCategoryHeader
import github.zerorooot.nap511.screenitem.PreferenceItem
import github.zerorooot.nap511.screenitem.SwitchPreferenceItem
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.SettingViewModel
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@Composable
fun SettingScreen(
    viewModel: SettingViewModel = viewModel(),
    onClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var lastClick by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 1. 导出配置 launcher (创建文件)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportConfig(
                context = context,
                uri = it,
                onSuccess = { App.instance.toast("配置导出成功！") },
                onError = { err -> App.instance.toast("导出失败: $err") }
            )
        }
    }

    // 2. 导入配置 launcher (选择文件)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importConfig(
                context = context,
                uri = it,
                onSuccess = {
                    App.instance.toast("配置导入成功，正在重启！")
                    ProcessPhoenix.triggerRebirth(context)
                },
                onError = { err -> App.instance.toast("导入失败: $err") }
            )
        }
    }
    SettingContent(
        uiState = uiState,
        onSaveConfig = { key, value -> viewModel.saveData(key, value) },
        onActionClick = onClick,
        onExportConfig = {
            exportLauncher.launch(
                "nap511_${
                    (System.currentTimeMillis()).toString().takeLast(13)
                }.json"
            )
        },
        onImportConfig = {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        },
        onRestartApp = {
            if (lastClick) { // 连点会被忽略
                return@SettingContent
            }
            lastClick = true
            App.instance.toast("重启中～")
            ProcessPhoenix.triggerRebirth(context)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingContent(
    uiState: SettingUiState,
    onSaveConfig: (String, Any) -> Unit,
    onActionClick: (String) -> Unit,
    onExportConfig: () -> Unit,      // 导出回调
    onImportConfig: () -> Unit,      // 导入回调
    onRestartApp: () -> Unit
) {
    val listState = rememberLazyListState()
    val fabArray = stringArrayResource(R.array.floatingActionButtonPosition)

    Column {
        TopAppBar(
            title = { Text(text = "高级设置") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
            navigationIcon = {
                TopAppBarActionButton(
                    imageVector = Icons.Rounded.Menu,
                    description = "navigationIcon",
                    onClick = { onActionClick("topAppBarActionButtonOnClick") }
                )
            }
        )

        LazyColumnScrollbar(
            state = listState,
            settings = ScrollbarSettings.Default.copy(
                thumbUnselectedColor = Purple80
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = listState
            ) {
                // --- 1. 账号与安全 ---
                item { PreferenceCategoryHeader("账号与安全") }
                item {
                    EditTextPreferenceItem(
                        title = "用户id",
                        summary = uiState.uid,
                        value = uiState.uid,
                        enabled = false,
                        onValueSave = {}
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "登录Cookie",
                        summary = "点击更改",
                        value = uiState.cookie,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.COOKIE, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "数字安全密钥",
                        summary = "清空回收站文件时输入的密码",
                        value = uiState.password,
                        isNumber = true,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.PASSWORD, it) }
                    )
                }

                // --- 2. Aria2 与下载设置 ---
                item { PreferenceCategoryHeader("Aria2 与下载") }
                item {
                    EditTextPreferenceItem(
                        title = "aria2地址",
                        summary = uiState.aria2Url,
                        value = uiState.aria2Url,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.ARIA2_URL, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "aria2秘钥",
                        summary = uiState.aria2Token.ifEmpty { "没有留空即可" },
                        value = uiState.aria2Token,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.ARIA2_TOKEN, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "默认离线位置",
                        summary = uiState.defaultOfflineCid.ifEmpty { "文件夹cid，长按目录可设置默认离线位置" },
                        value = uiState.defaultOfflineCid,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.DEFAULT_OFFLINE_CID, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "离线任务延迟时间",
                        summary = "延迟${uiState.defaultOfflineTime}分钟后统一离线下载",
                        value = uiState.defaultOfflineTime,
                        isNumber = true,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, it) }
                    )
                }
                item {
                    PreferenceItem(
                        title = "立即下载",
                        summary = "立刻下载缓存的离线任务",
                        onClick = { onActionClick("handleOfflineTask") }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "离线任务缓存",
                        summary = if (uiState.currentOfflineTask.isEmpty()) {
                            "尚未添加离线任务"
                        } else {
                            "共有${uiState.currentOfflineTask.split("\n").size}个离线任务连接"
                        },
                        value = uiState.currentOfflineTask,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.CURRENT_OFFLINE_TASK, it) }
                    )
                }

                // --- 3. 文件与界面偏好 ---
                item { PreferenceCategoryHeader("界面与操作") }
                item {
                    ListPreferenceItem(
                        title = "浮动按钮位置",
                        value = uiState.fabPosition,
                        entries = fabArray,
                        entryValues = fabArray,
                        onValueSave = {
                            onSaveConfig(
                                ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION,
                                it
                            )
                        }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "每次请求文件数",
                        summary = uiState.requestLimitCount,
                        value = uiState.requestLimitCount,
                        isNumber = true,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.REQUEST_LIMIT_COUNT, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "最大文本打开限制",
                        summary = "支持打开${uiState.txtSize}kb以下的文本文件",
                        value = uiState.txtSize,
                        isNumber = true,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.MAX_TXT_SIZE, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "解压失败存放文件夹",
                        summary = if (uiState.moveFailFile.isEmpty()) {
                            "后台解压失败后，压缩包将移动至解压目录此名称的文件夹中，留空则不移动"
                        } else {
                            "后台解压失败后，压缩包将移动至\"解压目录\\${uiState.moveFailFile}\"中"
                        },
                        value = uiState.moveFailFile,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.MOVE_FAIL_FILE, it) }
                    )
                }

                // --- 4. 自动化与开关控制 ---
                item { PreferenceCategoryHeader("功能开关") }
                item {
                    SwitchPreferenceItem(
                        title = "屏幕自动旋转",
                        summary = "根据视频横竖自动旋转屏幕",
                        checked = uiState.autoRotateEnabled,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.AUTO_ROTATE, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "种子文件排序",
                        summary = "种子文件按文件大小从大到小排序",
                        checked = uiState.torrentSort,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.TORRENT_SORT, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "链接解析策略",
                        summary = "开启后通过API获取视频链接（稍慢）；关闭则直接请求视频链接（更快，但视频链接可能失效）",
                        checked = uiState.videoLinkMode,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.VIDEO_LINK_MODE, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "清空当前缓存",
                        summary = "刷新时，清空当前目录下所有文件缓存",
                        checked = uiState.forceLoadCache,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.FORCE_LOAD_CACHE, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "记录日志信息",
                        summary = "输出程序中部分关键节点内容，方便调试",
                        checked = uiState.logEnabled,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.LOG, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "隐藏视频加载",
                        summary = "当视频正在加载时，隐藏加载提示",
                        checked = uiState.hideLoadingView,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.HIDE_LOADING_VIEW, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "提前加载文件",
                        summary = "进入下级目录时，提前加载当前文件夹上下两个文件夹内的文件",
                        checked = uiState.earlyLoading,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.EARLY_LOADING, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "保存请求缓存",
                        summary = "保存请求文件缓存到 cacheDir 中，便于提升加载速度",
                        checked = uiState.saveRequestCache,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.SAVE_REQUEST_CACHE, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "光标重新定位",
                        summary = "重命名时，光标定位在'@'或'空格'后",
                        checked = uiState.positionAfterAt,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.POSITION_AFTER_AT, it) }
                    )
                }
                item { PreferenceCategoryHeader("备份与恢复") }
                item {
                    PreferenceItem(
                        title = "导出配置",
                        summary = "将当前所有设置导出为 JSON 配置文件",
                        onClick = onExportConfig
                    )
                }
                item {
                    PreferenceItem(
                        title = "导入配置",
                        summary = "从 JSON 配置文件中恢复设置",
                        onClick = onImportConfig
                    )
                }
                // --- 5. 工具与维护 ---
                item { PreferenceCategoryHeader("维护与验证") }
                item {
                    PreferenceItem(
                        title = "视频播放验证",
                        summary = "打开视频播放异常验证页面",
                        onClick = { onActionClick("VerifyVideoAccount") }
                    )
                }
                item {
                    PreferenceItem(
                        title = "磁力链接验证",
                        summary = "打开磁力链接添加异常验证页面",
                        onClick = { onActionClick("VerifyMagnetLinkAccount") }
                    )
                }
                item {
                    PreferenceItem(
                        title = "文件查重",
                        summary = "排查重复文件",
                        onClick = { onActionClick("RepeatFile") }
                    )
                }
                item {
                    PreferenceItem(
                        title = "重启应用",
                        summary = "点我重新启动应用",
                        onClick = onRestartApp
                    )
                }
            }
        }
    }
}