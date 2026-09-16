package github.zerorooot.nap511.screen.file

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import coil.annotation.ExperimentalCoilApi
import github.zerorooot.nap511.bean.FileBannerState
import github.zerorooot.nap511.bean.FileContentActions
import github.zerorooot.nap511.bean.FileDisplayConfig
import github.zerorooot.nap511.bean.FileListDataState
import github.zerorooot.nap511.bean.FileListScrollState
import github.zerorooot.nap511.bean.FileScaffoldActions
import github.zerorooot.nap511.bean.FileScaffoldState
import github.zerorooot.nap511.screen.components.AppTopBarMultiple
import github.zerorooot.nap511.screen.components.AppTopBarNormal
import github.zerorooot.nap511.screen.components.MiniPlayerBar
import github.zerorooot.nap511.screenitem.FileListContent
import github.zerorooot.nap511.screenitem.FilePathBar
import github.zerorooot.nap511.screenitem.FileScreenFab
import github.zerorooot.nap511.screenitem.NotificationPermissionBanner

@Composable
fun FileScaffold(
    state: FileScaffoldState,
    actions: FileScaffoldActions,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        //直接设置
        // contentWindowInsets = WindowInsets(0, 0, 0, 0) ,
        // 会彻底清空 所有方向（上、下、左、右） 的系统安全边距（System Insets）
        //造成：1、底部导航栏/手势条重叠（Bottom Insets 丢失）；2、横屏及左右安全边距丢失（Horizontal Insets 丢失）；3、软键盘自动弹起避让失效（IME Insets 丢失）
        contentWindowInsets = if (state.isTopBarShow) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            // 仅在隐藏控制栏时排除 Top 边距，保留 Bottom（底部导航栏/手势）和 Horizontal（左右）
            ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
            )
        },
        topBar = {
            AnimatedVisibility(
                visible = state.isTopBarShow,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                AnimatedContent(
                    targetState = state.isLongClickState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = ""
                ) { isLongClick ->
                    if (isLongClick) {
                        AppTopBarMultiple(
                            title = state.appBarTitle,
                            isExpandedScreen = state.isExpandedScreen,
                            onClick = actions.onAppBarClick
                        )
                    } else {
                        AppTopBarNormal(state.appBarTitle, actions.onAppBarClick)
                    }
                }
            }
        },
        modifier = modifier.nestedScroll(state.nestedScrollConnection),
        bottomBar = {
            AnimatedVisibility(
                visible = state.hasCurrentMusic && state.isBottomBarShow,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                MiniPlayerBar(audioViewModel = state.audioViewModel) {
                    actions.onMusicDetailNav()
                }
            }
        },
        floatingActionButton = {
            FileScreenFab(
                isCutState = state.isCutState,
                visible = state.isBottomBarShow,
                onCancelCut = actions.onCancelCut,
                onCutPaste = actions.onCutPaste,
                onAddFolder = actions.onAddFolder
            )
        },
        floatingActionButtonPosition = state.fabPosition,
        content = content
    )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun FileScreenContent(
    innerPadding: PaddingValues,
    dataState: FileListDataState,
    bannerState: FileBannerState,
    displayConfig: FileDisplayConfig,
    scrollState: FileListScrollState,
    actions: FileContentActions,
    modifier: Modifier = Modifier,
    isTopBarShow: Boolean = true
) {
    val showNotificationBanner =
        !bannerState.isNotificationEnabled && !bannerState.isNotificationBannerDismissed
    val showBatteryBanner =
        !showNotificationBanner && !bannerState.isIgnoringBatteryOptimizations && !bannerState.isBatteryBannerDismissed

    Column(
        modifier = modifier
            .padding(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            )
            .consumeWindowInsets(innerPadding)
    ) {
        AnimatedVisibility(
            visible = showNotificationBanner || showBatteryBanner,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            if (showNotificationBanner) {
                NotificationPermissionBanner(
                    text = "未开启通知权限，可能无法及时收到离线下载提醒",
                    icon = Icons.Default.Notifications,
                    onOpenSettings = actions.bannerActions.onOpenNotificationSettings,
                    onDismiss = actions.bannerActions.onDismissNotificationBanner
                )
            } else if (showBatteryBanner) {
                NotificationPermissionBanner(
                    text = "未允许无限制后台运行，后台解压可能会暂停",
                    icon = Icons.Default.BatteryAlert,
                    onOpenSettings = actions.bannerActions.onOpenBatterySettings,
                    onDismiss = actions.bannerActions.onDismissBatteryBanner
                )
            }
        }

        AnimatedVisibility(
            visible = isTopBarShow,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            FilePathBar(
                pathList = dataState.pathList,
                actions = actions.pathActions
            )
        }

        FileListContent(
            dataState = dataState,
            displayConfig = displayConfig,
            scrollState = scrollState,
            itemActions = actions.itemActions
        )
    }
}
