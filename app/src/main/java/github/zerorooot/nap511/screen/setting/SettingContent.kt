package github.zerorooot.nap511.screen.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.screen.components.TopAppBarActionButton
import github.zerorooot.nap511.screenitem.accountSecurityPreferenceItems
import github.zerorooot.nap511.screenitem.downloadAria2PreferenceItems
import github.zerorooot.nap511.screenitem.expandedScreenPreferenceItems
import github.zerorooot.nap511.screenitem.fileCachePreferenceItems
import github.zerorooot.nap511.screenitem.maintenanceBackupPreferenceItems
import github.zerorooot.nap511.screenitem.mediaPlaybackPreferenceItems
import github.zerorooot.nap511.screenitem.uiExperiencePreferenceItems
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingContent(
    uiState: SettingUiState,
    currentLocation: LocationBean,
    onSaveScrollPosition: (Int, Int) -> Unit,
    onSaveConfig: (String, Any) -> Unit,
    onDrawerClick: () -> Unit = {},
    onActionClick: (String) -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onResetConfig: () -> Unit,
    onRestartApp: () -> Unit
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentLocation.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = currentLocation.firstVisibleItemScrollOffset
    )

    DisposableEffect(listState) {
        onDispose {
            onSaveScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    val fabArray = stringArrayResource(R.array.floatingActionButtonPosition)
    val themeArray = stringArrayResource(R.array.themeMode)

    Column {
        TopAppBar(
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
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
                    onClick = {
                        onDrawerClick()
                        onActionClick("topAppBarActionButtonOnClick")
                    }
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
                    .fillMaxSize()
                    .widthIn(max = 800.dp)
                    .align(Alignment.CenterHorizontally),
                state = listState
            ) {
                // --- 1. 账号与安全 ---
                accountSecurityPreferenceItems(
                    uiState = uiState,
                    onSaveConfig = onSaveConfig,
                    onActionClick = onActionClick
                )

                // --- 2. 下载与 Aria2 ---
                downloadAria2PreferenceItems(
                    uiState = uiState,
                    onSaveConfig = onSaveConfig,
                    onActionClick = onActionClick
                )

                // --- 3. 播放与媒体 ---
                mediaPlaybackPreferenceItems(
                    uiState = uiState,
                    onSaveConfig = onSaveConfig
                )

                // --- 4. 大屏与扩展 ---
                expandedScreenPreferenceItems(
                    uiState = uiState,
                    onSaveConfig = onSaveConfig
                )

                // --- 5. 文件与缓存 ---
                fileCachePreferenceItems(
                    uiState = uiState,
                    onSaveConfig = onSaveConfig
                )

                // --- 6. 界面与体验 ---
                uiExperiencePreferenceItems(
                    uiState = uiState,
                    fabArray = fabArray,
                    themeArray = themeArray,
                    onSaveConfig = onSaveConfig
                )

                // --- 7. 维护与备份 ---
                maintenanceBackupPreferenceItems(
                    onExportConfig = onExportConfig,
                    onImportConfig = onImportConfig,
                    onActionClick = onActionClick,
                    onResetConfig = onResetConfig,
                    onRestartApp = onRestartApp
                )
            }
        }
    }
}
