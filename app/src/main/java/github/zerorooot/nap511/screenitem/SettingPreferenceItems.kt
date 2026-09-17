package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.lazy.LazyListScope
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.network.UserSessionManager

/**
 * 账号与安全 设置项分组
 */
fun LazyListScope.accountSecurityPreferenceItems(
    uiState: SettingUiState,
    onSaveConfig: (String, Any) -> Unit,
    onActionClick: (String) -> Unit
) {
    item { PreferenceCategoryHeader("账号与凭据") }
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
            onValueSave = {
                UserSessionManager.updateCookie(it)
                onSaveConfig(ConfigKeyUtil.COOKIE, it)
            }
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
    item {
        PreferenceItem(
            title = "应用登录页面",
            summary = "进入应用登录页面，重新登录",
            onClick = { onActionClick("Login") }
        )
    }
}

/**
 * 下载与 Aria2 设置项分组
 */
fun LazyListScope.downloadAria2PreferenceItems(
    uiState: SettingUiState,
    onSaveConfig: (String, Any) -> Unit,
    onActionClick: (String) -> Unit
) {
    item { PreferenceCategoryHeader("Aria2 与离线配置") }
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
            summary = if (uiState.defaultOfflinePath.isEmpty()) "长按目录可设置为默认位置" else "默认离线位置为: ${uiState.defaultOfflinePath}",
            value = uiState.defaultOfflineCid,
            label = "文件夹CID",
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
            title = "暂存未下任务链接",
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
}

/**
 * 播放与媒体 设置项分组
 */
fun LazyListScope.mediaPlaybackPreferenceItems(
    uiState: SettingUiState,
    onSaveConfig: (String, Any) -> Unit
) {
    item { PreferenceCategoryHeader("视频与音频") }
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
            title = "隐藏电池提醒",
            summary = "关闭首页弹出的后台电池优化提醒 Banner",
            checked = uiState.hideBatteryBanner,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.HIDE_BATTERY_BANNER, it) }
        )
    }
    item {
        SwitchPreferenceItem(
            title = "自动跳转重试",
            summary = "未开启“视频解析模式”时生效。若播放提示“视频地址错误”，自动重新解析并获取正确链接",
            checked = uiState.autoJumpRetry,
            enabled = !uiState.videoLinkMode,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.AUTO_JUMP_RETRY, it) }
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
}

/**
 * 大屏与扩展 设置项分组
 */
fun LazyListScope.expandedScreenPreferenceItems(
    uiState: SettingUiState,
    onSaveConfig: (String, Any) -> Unit
) {
    item { PreferenceCategoryHeader("自适应与大屏布局") }
    item {
        EditTextPreferenceItem(
            title = "网格最小宽度",
            summary = "自适应网格排布时，单列最小单元目标宽度为 ${uiState.gridCellMinSize} dp",
            value = uiState.gridCellMinSize,
            isNumber = true,
            enabled = uiState.expandedScreenEnabled,
            onValueSave = { onSaveConfig(ConfigKeyUtil.GRID_CELL_MIN_SIZE, it) }
        )
    }
    item {
        EditTextPreferenceItem(
            title = "切换瀑布视图",
            summary = "当图片文件数量大于 ${uiState.autoImagePreviewCount} 个时，自动切换到瀑布流视图",
            value = uiState.autoImagePreviewCount,
            isNumber = true,
            onValueSave = { onSaveConfig(ConfigKeyUtil.AUTO_IMAGE_PREVIEW_COUNT, it) }
        )
    }
    item {
        SwitchPreferenceItem(
            title = "多列网格模式",
            summary = "开启时由系统根据屏幕宽度原生自适应排布为多列网格；关闭时无论屏幕多宽强制保持经典单列",
            checked = uiState.gridScreenEnabled,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.GRID_SCREEN, it) }
        )
    }
    item {
        SwitchPreferenceItem(
            title = "大屏扩展模式",
            summary = "把部分页面修改适配自适应 (Adaptive)页面",
            checked = uiState.expandedScreenEnabled,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.EXPANDED_SCREEN, it) }
        )
    }
    item {
        SwitchPreferenceItem(
            title = "高清瀑布视图",
            summary = "开启后，瀑布流视图下将自动请求高清原图",
            checked = uiState.imageHdPreview,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.IMAGE_HD_PREVIEW, it) }
        )
    }
}

/**
 * 文件与缓存 设置项分组
 */
fun LazyListScope.fileCachePreferenceItems(
    uiState: SettingUiState,
    onSaveConfig: (String, Any) -> Unit
) {
    item { PreferenceCategoryHeader("缓存与检索") }
    item {
        EditTextPreferenceItem(
            title = "单次请求文件数量",
            summary = "每次请求加载 ${uiState.requestLimitCount} 个文件",
            value = uiState.requestLimitCount,
            isNumber = true,
            onValueSave = {
                UserSessionManager.updateRequestLimitCount(it)
                onSaveConfig(ConfigKeyUtil.REQUEST_LIMIT_COUNT, it)
            }
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
            title = "种子文件大小排序",
            summary = "解析种子文件列表时按文件体积从大到小排列",
            checked = uiState.torrentSort,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.TORRENT_SORT, it) }
        )
    }
    item {
        SwitchPreferenceItem(
            title = "下拉刷新缓存清空",
            summary = "下拉刷新时，强制清除当前目录下所有已缓存的文件数据",
            checked = uiState.forceLoadCache,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.FORCE_LOAD_CACHE, it) }
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
            title = "前后文件夹预加载",
            summary = "进入子目录时，自动预加载前后相邻文件夹的文件数据",
            checked = uiState.earlyLoading,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.EARLY_LOADING, it) }
        )
    }
}

/**
 * 界面与体验 设置项分组
 */
fun LazyListScope.uiExperiencePreferenceItems(
    uiState: SettingUiState,
    fabArray: Array<String>,
    themeArray: Array<String>,
    onSaveConfig: (String, Any) -> Unit
) {
    item { PreferenceCategoryHeader("主题与手势") }
    item {
        ListPreferenceItem(
            title = "悬浮按钮位置",
            value = uiState.fabPosition,
            entries = fabArray,
            entryValues = fabArray,
            onValueSave = { onSaveConfig(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION, it) }
        )
    }
    item {
        ListPreferenceItem(
            title = "主题颜色模式",
            value = uiState.themeMode,
            entries = themeArray,
            entryValues = themeArray,
            onValueSave = { onSaveConfig(ConfigKeyUtil.THEME_MODE, it) }
        )
    }
    item {
        SwitchPreferenceItem(
            title = "应用动态配色",
            summary = "根据系统壁纸自动衍生应用配色（仅支持 Android 12+）",
            checked = uiState.dynamicColorEnabled,
            onCheckedChange = { onSaveConfig(ConfigKeyUtil.DYNAMIC_COLOR, it) }
        )
    }
    item {
        SwitchPreferenceItem(
            title = "改名光标定位",
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
}

/**
 * 维护与备份 设置项分组
 */
fun LazyListScope.maintenanceBackupPreferenceItems(
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onActionClick: (String) -> Unit,
    onResetConfig: () -> Unit,
    onRestartApp: () -> Unit
) {
    item { PreferenceCategoryHeader("系统维护与高级诊断") }
    item {
        PreferenceItem(
            title = "导出配置文件",
            summary = "将当前所有应用设置导出为 JSON 配置文件",
            onClick = onExportConfig
        )
    }
    item {
        PreferenceItem(
            title = "导入配置文件",
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
            title = "视频播放验证",
            summary = "打开视频播放异常问题诊断页面",
            onClick = { onActionClick("VerifyVideoAccount") }
        )
    }
    item {
        PreferenceItem(
            title = "磁力链接验证",
            summary = "打开磁力添加失败问题诊断页面",
            onClick = { onActionClick("VerifyMagnetLinkAccount") }
        )
    }
    item {
        PreferenceItem(
            title = "恢复默认设置",
            summary = "将所有应用设置恢复为默认状态",
            onClick = onResetConfig
        )
    }
    item {
        PreferenceItem(
            title = "强制重启应用",
            summary = "强制重新启动应用程序",
            onClick = onRestartApp
        )
    }
}
