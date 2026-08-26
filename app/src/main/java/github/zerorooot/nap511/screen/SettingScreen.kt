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
import androidx.compose.material3.MaterialTheme
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
            colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
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
                thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
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
                        title = "用户 ID",
                        summary = uiState.uid,
                        value = uiState.uid,
                        enabled = false,
                        onValueSave = {}
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "账号 Cookie",
                        summary = "点击修改登录凭证",
                        value = uiState.cookie,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.COOKIE, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "安全操作密钥",
                        summary = "清空回收站时输入的数字密码",
                        value = uiState.password,
                        isNumber = true,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.PASSWORD, it) }
                    )
                }

                // --- 2. 下载与 Aria2 ---
                item { PreferenceCategoryHeader("下载与 Aria2") }
                item {
                    EditTextPreferenceItem(
                        title = "Aria2 RPC 地址",
                        summary = uiState.aria2Url,
                        value = uiState.aria2Url,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.ARIA2_URL, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "Aria2 授权密钥",
                        summary = uiState.aria2Token.ifEmpty { "未设置（若无密码请留空）" },
                        value = uiState.aria2Token,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.ARIA2_TOKEN, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "默认离线保存目录",
                        summary = uiState.defaultOfflineCid.ifEmpty { "文件夹 CID，长按目录可设置为默认位置" },
                        value = uiState.defaultOfflineCid,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.DEFAULT_OFFLINE_CID, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "离线任务延迟时间",
                        summary = "延迟 ${uiState.defaultOfflineTime} 分钟后统一提交离线下载",
                        value = uiState.defaultOfflineTime,
                        isNumber = true,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "待处理离线任务",
                        summary = if (uiState.currentOfflineTask.isEmpty()) {
                            "当前无暂存的离线任务"
                        } else {
                            "共有 ${uiState.currentOfflineTask.split("\n").size} 个任务等待提交"
                        },
                        value = uiState.currentOfflineTask,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.CURRENT_OFFLINE_TASK, it) }
                    )
                }
                item {
                    PreferenceItem(
                        title = "立即处理离线任务",
                        summary = "立即提交并下载当前暂存的离线任务",
                        onClick = { onActionClick("handleOfflineTask") }
                    )
                }

                // --- 3. 播放与媒体 ---
                item { PreferenceCategoryHeader("播放与媒体") }
                item {
                    SwitchPreferenceItem(
                        title = "屏幕自动旋转",
                        summary = "根据视频画面的宽高比自动切换横竖屏",
                        checked = uiState.autoRotateEnabled,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.AUTO_ROTATE, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "视频解析模式",
                        summary = "开启：通过 API 获取播放链接（稳定但稍慢）；关闭：直接请求视频链接（更快但可能失效）",
                        checked = uiState.videoLinkMode,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.VIDEO_LINK_MODE, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "隐藏加载提示",
                        summary = "视频缓冲加载时隐藏居中的加载动画",
                        checked = uiState.hideLoadingView,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.HIDE_LOADING_VIEW, it) }
                    )
                }

                // --- 4. 文件与缓存 ---
                item { PreferenceCategoryHeader("文件与缓存") }
                item {
                    EditTextPreferenceItem(
                        title = "单页文件加载数",
                        summary = "每次请求加载 ${uiState.requestLimitCount} 个文件",
                        value = uiState.requestLimitCount,
                        isNumber = true,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.REQUEST_LIMIT_COUNT, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "解压失败转移目录",
                        summary = if (uiState.moveFailFile.isEmpty()) {
                            "解压失败时不移动压缩包；填写名称后将移至“解压目录\\指定名称”下"
                        } else {
                            "解压失败的压缩包将移至：解压目录\\${uiState.moveFailFile}"
                        },
                        value = uiState.moveFailFile,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.MOVE_FAIL_FILE, it) }
                    )
                }
                item {
                    EditTextPreferenceItem(
                        title = "文本预览最大限制",
                        summary = "支持直接打开并预览 ${uiState.txtSize} KB 以内的文本文件",
                        value = uiState.txtSize,
                        isNumber = true,
                        onValueSave = { onSaveConfig(ConfigKeyUtil.MAX_TXT_SIZE, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "开启请求磁盘缓存",
                        summary = "将请求的文件列表缓存至本地存储，提升再次加载速度",
                        checked = uiState.saveRequestCache,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.SAVE_REQUEST_CACHE, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "预加载相邻目录",
                        summary = "进入子目录时，自动预加载前后相邻文件夹的文件数据",
                        checked = uiState.earlyLoading,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.EARLY_LOADING, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "种子文件按大小排序",
                        summary = "解析种子文件列表时按文件体积从大到小排列",
                        checked = uiState.torrentSort,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.TORRENT_SORT, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "刷新时清空目录缓存",
                        summary = "下拉刷新时，强制清除当前目录下已缓存的文件数据",
                        checked = uiState.forceLoadCache,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.FORCE_LOAD_CACHE, it) }
                    )
                }

                // --- 5. 界面与体验 ---
                item { PreferenceCategoryHeader("界面与体验") }
                item {
                    ListPreferenceItem(
                        title = "悬浮按钮位置",
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
                    SwitchPreferenceItem(
                        title = "重命名自动定位光标",
                        summary = "重命名文件时，输入光标自动定位至 '@' 或 '空格' 字符后",
                        checked = uiState.positionAfterAt,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.POSITION_AFTER_AT, it) }
                    )
                }
                item {
                    SwitchPreferenceItem(
                        title = "启用调试日志",
                        summary = "记录并输出应用核心运行日志，以便排查异常",
                        checked = uiState.logEnabled,
                        onCheckedChange = { onSaveConfig(ConfigKeyUtil.LOG, it) }
                    )
                }

                // --- 6. 维护与备份 ---
                item { PreferenceCategoryHeader("维护与备份") }
                item {
                    PreferenceItem(
                        title = "导出配置",
                        summary = "将当前所有应用设置导出为 JSON 配置文件",
                        onClick = onExportConfig
                    )
                }
                item {
                    PreferenceItem(
                        title = "导入配置",
                        summary = "从 JSON 配置文件恢复应用设置并重启应用",
                        onClick = onImportConfig
                    )
                }
                item {
                    PreferenceItem(
                        title = "重复文件排查",
                        summary = "扫描并清理网盘中的重复文件",
                        onClick = { onActionClick("RepeatFile") }
                    )
                }
                item {
                    PreferenceItem(
                        title = "视频播放排错验证",
                        summary = "打开视频播放异常问题诊断页面",
                        onClick = { onActionClick("VerifyVideoAccount") }
                    )
                }
                item {
                    PreferenceItem(
                        title = "磁力链接排错验证",
                        summary = "打开磁力添加失败问题诊断页面",
                        onClick = { onActionClick("VerifyMagnetLinkAccount") }
                    )
                }
                item {
                    PreferenceItem(
                        title = "重启应用",
                        summary = "强制重新启动应用程序",
                        onClick = onRestartApp
                    )
                }
            }
        }
    }
}