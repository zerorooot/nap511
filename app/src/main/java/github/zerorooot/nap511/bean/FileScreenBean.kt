package github.zerorooot.nap511.bean

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.FabPosition
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import github.zerorooot.nap511.screen.AppBarAction
import github.zerorooot.nap511.viewmodel.AudioViewModel


/**
 * Scaffold 页面状态封装
 */
data class FileScaffoldState(
    val isLongClickState: Boolean,
    val appBarTitle: String,
    val isExpandedScreen: Boolean,
    val isBottomBarShow: Boolean,
    val isTopBarShow: Boolean,
    val hasCurrentMusic: Boolean,
    val isCutState: Boolean,
    val fabPosition: FabPosition,
    val nestedScrollConnection: NestedScrollConnection,
    val audioViewModel: AudioViewModel
)

/**
 * Scaffold 页面交互事件封装
 */
data class FileScaffoldActions(
    val onAppBarClick: (AppBarAction) -> Unit,
    val onMusicDetailNav: () -> Unit,
    val onCancelCut: () -> Unit,
    val onCutPaste: () -> Unit,
    val onAddFolder: () -> Unit
)

/**
 * 文件列表/网格数据状态封装
 */
data class FileListDataState(
    val path: String,
    val pathList: List<PathBean>,
    val fileBeanList: List<FileBean>,
    val refreshing: Boolean,
    val clickIndex: Int,
    val imageCache: Map<String, ImageBean>? = null
)

/**
 * 权限与系统提示 Banner 状态封装
 */
data class FileBannerState(
    val isNotificationEnabled: Boolean,
    val isNotificationBannerDismissed: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val isBatteryBannerDismissed: Boolean
)

/**
 * 文件列表布局与显示配置封装
 */
data class FileDisplayConfig(
    val isExpandedScreen: Boolean,
    val isPreviewActive: Boolean,
    val isImageHdPreview: Boolean = false,
    val gridCellMinSize: Dp
)

/**
 * 列表/网格滚动状态封装
 */
data class FileListScrollState(
    val listState: LazyListState,
    val gridState: LazyGridState,
    val staggeredGridState: LazyStaggeredGridState
)

/**
 * Banner 交互事件封装
 */
data class FileBannerActions(
    val onOpenNotificationSettings: () -> Unit,
    val onDismissNotificationBanner: () -> Unit,
    val onOpenBatterySettings: () -> Unit,
    val onDismissBatteryBanner: () -> Unit
)

/**
 * 路径栏交互事件封装
 */
data class FilePathActions(
    val onPathClick: () -> Unit,
    val onPathDoubleClick: () -> Unit,
    val onPathLongClick: (name: String, cid: String) -> Unit,
    val onPathItemClick: (cid: String) -> Unit
)

/**
 * 文件列表项交互事件封装
 */
data class FileItemActions(
    val onRefresh: () -> Unit,
    val onItemClick: (Int) -> Unit,
    val onItemLongClick: (Int) -> Unit,
    val onCut: (Int) -> Unit,
    val onDelete: (Int) -> Unit,
    val onRename: (Int) -> Unit,
    val onFileInfo: (Int) -> Unit,
    val onAria2Download: (Int) -> Unit,
    val onForceOpen: (Int) -> Unit,
    val onLoadImage: ((FileBean) -> Unit)? = null
)

/**
 * 文件内容区域 UI 交互事件总封装
 */
data class FileContentActions(
    val bannerActions: FileBannerActions,
    val pathActions: FilePathActions,
    val itemActions: FileItemActions
)
