package github.zerorooot.nap511.screen.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.screen.components.BaseTopAppBar
import github.zerorooot.nap511.screen.components.TopAppBarActionButton
import github.zerorooot.nap511.screenitem.accountSecurityPreferenceItems
import github.zerorooot.nap511.screenitem.downloadAria2PreferenceItems
import github.zerorooot.nap511.screenitem.expandedScreenPreferenceItems
import github.zerorooot.nap511.screenitem.fileCachePreferenceItems
import github.zerorooot.nap511.screenitem.maintenanceBackupPreferenceItems
import github.zerorooot.nap511.screenitem.mediaPlaybackPreferenceItems
import github.zerorooot.nap511.screenitem.uiExperiencePreferenceItems
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.rememberListDetailDirective
import github.zerorooot.nap511.viewmodel.SettingViewModel
import kotlinx.serialization.Serializable

/**
 * 设置页面的公共交互事件集合
 */
@Immutable
data class SettingActions(
    val onSaveConfig: (key: String, value: Any) -> Unit,
    val onDrawerClick: () -> Unit,
    val onActionClick: (String) -> Unit,
    val onExportConfig: () -> Unit,
    val onImportConfig: () -> Unit,
    val onResetConfig: () -> Unit,
    val onRestartApp: () -> Unit
)

data class SettingCategoryData(
    val title: String,
    val summary: String,
    val icon: ImageVector
)

val SETTING_CATEGORIES = listOf(
    SettingCategoryData("账号与安全", "用户ID、Cookie、安全操作密钥", Icons.Default.AccountCircle),
    SettingCategoryData("下载与Aria2", "Aria2 RPC、离线下载保存目录", Icons.Default.CloudDownload),
    SettingCategoryData("播放与媒体", "自动旋转、视频解析模式、缓冲提示", Icons.Default.PlayCircle),
    SettingCategoryData("大屏与扩展", "自适应网格总开关、单列最小宽度", Icons.Default.AspectRatio),
    SettingCategoryData("文件与缓存", "搜索模式、缓存清理、自动刷新", Icons.Default.Folder),
    SettingCategoryData("界面与体验", "动态取色、亮暗主题、FAB 位置", Icons.Default.Palette),
    SettingCategoryData(
        "维护与备份",
        "重置设置、导出与导入配置",
        Icons.Default.SettingsBackupRestore
    )
)

/**
 * 设置模块内部专用的 Nav3 路由 Key 规范
 */
@Serializable
sealed interface SettingNavKey : NavKey {
    /** 左侧分类列表面板路由 */
    @Serializable
    data object CategoryList : SettingNavKey

    /** 右侧分类详情面板路由，携带分类索引 */
    @Serializable
    data class CategoryDetail(val categoryIndex: Int) : SettingNavKey
}

/**
 * 设置中心统一主界面
 *
 * 核心设计：
 * 1. 统一处理 ViewModel 状态收集（uiState、currentLocation）、配置文件导出与导入 Launcher、重置默认配置 Dialog；
 * 2. 根据 [isExpandedScreen] 进行分流展示：
 *    - isExpandedScreen == true：展示自适应分栏设置界面 [AdaptiveSettingContent]（Nav3 ListDetail 双栏/分类详情模式）；
 *    - isExpandedScreen == false：展示单页紧凑设置界面 [SettingContent]（单页完整滚动列表模式）。
 */
@Composable
fun SettingScreen(
    viewModel: SettingViewModel,
    isExpandedScreen: Boolean = false,
    onDrawerClick: () -> Unit,
    onActionClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var lastClick by remember { mutableStateOf(false) }

    // 配置文件导出 Launcher
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

    // 配置文件导入 Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importConfig(
                context = context,
                uri = it,
                onSuccess = { App.instance.toast("配置导入成功！") },
                onError = { err -> App.instance.toast("导入失败: $err") }
            )
        }
    }

    // 重置配置对话框状态
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认设置") },
            text = { Text("确定要将所有设置恢复为默认状态吗？（账号登录信息将被保留）") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetConfig(
                            onSuccess = { App.instance.toast("设置已恢复为默认值") },
                            onError = { err -> App.instance.toast(err) }
                        )
                    }
                ) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }

    // 缓存所有通用的交互操作，避免层层透传大量 lambda
    val actions = remember(
        viewModel,
        onDrawerClick,
        onActionClick,
        exportLauncher,
        importLauncher,
        context
    ) {
        SettingActions(
            onSaveConfig = { key, value -> viewModel.saveData(key, value) },
            onDrawerClick = onDrawerClick,
            onActionClick = onActionClick,
            onExportConfig = {
                exportLauncher.launch(
                    "nap511_${System.currentTimeMillis().toString().takeLast(13)}.json"
                )
            },
            onImportConfig = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onResetConfig = { showResetDialog = true },
            onRestartApp = {
                if (!lastClick) {
                    lastClick = true
                    ProcessPhoenix.triggerRebirth(context)
                    App.instance.toast("重启中...")
                }
            }
        )
    }

    if (isExpandedScreen) {
        AdaptiveSettingContent(
            uiState = uiState,
            selectedCategoryIndex = viewModel.selectedCategoryIndex,
            onSelectCategory = { index -> viewModel.selectedCategoryIndex = index },
            actions = actions
        )
    } else {
        SettingContent(
            uiState = uiState,
            currentLocation = currentLocation,
            onSaveScrollPosition = { index, offset -> viewModel.setLocation(index, offset) },
            actions = actions
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AdaptiveSettingContent(
    uiState: SettingUiState,
    selectedCategoryIndex: Int,
    onSelectCategory: (Int) -> Unit,
    actions: SettingActions,
) {
    // 1. 自适应分栏指令：计算当前视口是否支持双栏并排 (isDualPane)
    val directive = rememberListDetailDirective()
//    val isDualPane = directive.isDualPane

    // 2. Nav3 返回栈：以分类列表作为起始页
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(SettingNavKey.CategoryList)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    // 3. 抽取详情面板的渲染逻辑
    val renderDetailPane: @Composable (categoryIndex: Int) -> Unit =
        { catIndex ->
            CategoryDetailPane(
                categoryIndex = catIndex,
                uiState = uiState,
                actions = actions
            )
        }

    // 4. Nav3 驱动的自适应分栏展示容器
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider = entryProvider {
            // 左侧列表面板：注册 listPane 元数据
            entry<SettingNavKey.CategoryList>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        // 大屏双栏模式下，当详情栈未显式推入路由时，直接展示当前选中分类的详情
                        renderDetailPane(selectedCategoryIndex)
                    }
                )
            ) {
                CategoryListPane(
                    categories = SETTING_CATEGORIES,
                    selectedIndex = selectedCategoryIndex,
                    onDrawerClick = actions.onDrawerClick,
                    onSelectCategory = { index ->
                        onSelectCategory(index)
                        // 单栏手机模式下，点击分类推入详情路由；双栏模式下仅切换选中索引
//                        if (!isDualPane) {
//                            backStack.add(SettingNavKey.CategoryDetail(index))
//                        }
                    }
                )
            }

            // 右侧详情面板：注册 detailPane 元数据
            entry<SettingNavKey.CategoryDetail>(
                metadata = ListDetailSceneStrategy.detailPane()
            ) { detailKey ->
                renderDetailPane(detailKey.categoryIndex)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryListPane(
    categories: List<SettingCategoryData>,
    selectedIndex: Int,
    onDrawerClick: () -> Unit,
    onSelectCategory: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            BaseTopAppBar(
                title = { Text("设置中心") },
                navigationIcon = {
                    TopAppBarActionButton(
                        imageVector = Icons.Rounded.Menu,
                        description = "菜单"
                    ) {
                        onDrawerClick()
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            itemsIndexed(categories) { index, item ->
                val isSelected = (index == selectedIndex)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectCategory(index) },
                    shape = RoundedCornerShape(12.dp),
                    // 1. 卡片背景色：选中项使用 secondaryContainer 高亮色，未选中使用 surface 背景色
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            // 使用 surfaceContainerLow，让未选中的卡片也能与背景区分开
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    // 2. 卡片阴影/高度：选中项拥有 3.dp 悬浮高度，未选中项为 1.dp
                    elevation = CardDefaults.cardElevation(if (isSelected) 3.dp else 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 3. 图标颜色：选中项使用 onSecondaryContainer，未选中项使用主色 primary
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // 4. 标题文字颜色：选中项使用 onSecondaryContainer，未选中项使用 onSurface
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDetailPane(
    categoryIndex: Int,
    uiState: SettingUiState,
    actions: SettingActions,
) {
    val category = SETTING_CATEGORIES.getOrNull(categoryIndex) ?: SETTING_CATEGORIES[0]
    val fabArray = stringArrayResource(R.array.floatingActionButtonPosition)
    val themeArray = stringArrayResource(R.array.themeMode)

    Scaffold(
        topBar = {
            BaseTopAppBar(
                title = { Text(category.title) },
//                navigationIcon = {
//                    if (showBackButton) {
//                        IconButton(onClick = onBack) {
//                            Icon(
//                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
//                                contentDescription = "返回"
//                            )
//                        }
//                    }
//                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (categoryIndex) {
                0 -> {
                    // --- 1. 账号与安全 ---
                    accountSecurityPreferenceItems(
                        uiState = uiState,
                        actions = actions
                    )
                }

                1 -> {
                    // --- 2. 下载与 Aria2 ---
                    downloadAria2PreferenceItems(
                        uiState = uiState,
                        actions = actions
                    )
                }

                2 -> {
                    // --- 3. 播放与媒体 ---
                    mediaPlaybackPreferenceItems(
                        uiState = uiState,
                        actions = actions
                    )
                }

                3 -> {
                    // --- 4. 大屏与扩展 ---
                    expandedScreenPreferenceItems(
                        uiState = uiState,
                        actions = actions
                    )
                }

                4 -> {
                    // --- 5. 文件与缓存 ---
                    fileCachePreferenceItems(
                        uiState = uiState,
                        actions = actions
                    )
                }

                5 -> {
                    // --- 6. 界面与体验 ---
                    uiExperiencePreferenceItems(
                        uiState = uiState,
                        fabArray = fabArray,
                        themeArray = themeArray,
                        actions = actions
                    )
                }

                6 -> {
                    // --- 7. 维护与备份 ---
                    maintenanceBackupPreferenceItems(
                        actions = actions
                    )
                }
            }
        }
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true)
@Composable
private fun CategoryListPanePreview() {
    MaterialTheme {
        CategoryListPane(
            categories = SETTING_CATEGORIES,
            selectedIndex = 0,
//            isDualPane = true,
            onDrawerClick = {},
            onSelectCategory = {}
        )
    }
}
