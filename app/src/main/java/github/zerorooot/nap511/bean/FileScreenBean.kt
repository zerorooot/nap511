package github.zerorooot.nap511.bean

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.FabPosition
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Immutable
import github.zerorooot.nap511.screen.components.AppBarAction
import github.zerorooot.nap511.viewmodel.AudioViewModel

/**
 * 文件列表核心浏览与模式状态（低频大对象变更，严格遵循不可变 UDF）
 */
@Immutable
data class FileContentUiState(
    val currentCid: String = "0",
    val pathList: List<PathBean> = emptyList(),
    val fileBeanList: List<FileBean> = emptyList(),
    val isRefreshing: Boolean = false,
    val appBarTitle: String = "nap511",
    val orderBean: OrderBean = OrderBean(OrderEnum.name, 1),

    // 交互模式状态
    val isLongClickState: Boolean = false,
    val isCutState: Boolean = false,
    val isSearchState: Boolean = false,
    val cutFileList: List<FileBean> = emptyList()
)

/**
 * 弹窗交互独立状态（高频局部交互，彻底与列表数据流物理隔离）
 */
@Immutable
data class FileDialogUiState(
    val activeDialog: FileDialogState = FileDialogState.None,
    val targetFileBean: FileBean? = null,
    val fileInfo: FileInfo = FileInfo(),
    val torrentBean: TorrentFileBean = TorrentFileBean(),
    val unzipBeanList: ZipBeanList = ZipBeanList()
)



/**
 * Scaffold 页面状态封装
 */
data class FileScaffoldState(
    val isLongClickState: Boolean,
    val appBarTitle: String,
    val isLandscape: Boolean,
    val isGridScreen: Boolean,
    val isBottomBarShow: Boolean,
    val isTopBarShow: Boolean,
    val hasCurrentMusic: Boolean,
    val isCutState: Boolean,
    val fabPosition: FabPosition,
    val currentCid: String,
    val nestedScrollConnection: NestedScrollConnection
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
    val currentCid: String,
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
    val onUnzip: (FileBean) -> Unit,
    val onDelete: (FileBean) -> Unit,
    val onRename: (Int) -> Unit,
    val onFileInfo: (FileBean) -> Unit,
    val onSetOfflineCid: (FileBean) -> Unit,
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
