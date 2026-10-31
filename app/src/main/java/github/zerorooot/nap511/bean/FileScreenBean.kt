package github.zerorooot.nap511.bean

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.FabPosition
import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import github.zerorooot.nap511.screen.components.AppBarAction

/**
 * 列表数据源模型：定义当前文件列表展示的数据来源及上下文
 */
sealed interface FileListSource {
    /** 当前关联的目录 CID */
    val cid: String

    /** 用于分页状态、锁隔离及 Compose 滚动状态 Key 的唯一复合键 */
    val paginationKey: String

    /** 普通目录浏览 */
    data class Folder(override val cid: String) : FileListSource {
        override val paginationKey: String get() = key(cid)
        companion object {
            fun key(cid: String): String = "folder:$cid"
        }
    }

    /** 关键词搜索 */
    data class Search(override val cid: String, val keyword: String) : FileListSource {
        override val paginationKey: String get() = key(cid, keyword)

        companion object {
            fun key(cid: String, keyword: String): String = "search:$cid:$keyword"
        }
    }

    /**
     * 分类文件过滤
     *
     * @param cid 当前过滤所属的目录 ID
     * @param filterType 分类过滤类型（1文档、2图片、3音频、4视频、5压缩、6软件）
     * @param name 分类显示名称
     * @param order 过滤模式专用的排序规则（独立内聚，不污染普通目录配置）
     */
    data class Filter(
        override val cid: String,
        val filterType: Int,
        val name: String,
        val order: OrderBean,
        val fcMix: Int,
    ) : FileListSource {
        override val paginationKey: String get() = key(cid, filterType, order, fcMix)

        companion object {
            fun key(
                cid: String,
                filterType: Int,
                order: OrderBean,
                fcMix: Int
            ): String = "filter:$cid:$filterType:${order.type}:${order.asc}:$fcMix"
        }
    }
}

/**
 * 文件列表核心浏览与模式状态（低频大对象变更，严格遵循不可变 UDF）
 */
@Immutable
data class FileContentUiState(
    val currentCid: String = "0",
    val pathList: List<PathBean> = emptyList(),
    val fileBeanList: List<FileBean> = emptyList(),
    val isRefreshing: Boolean = false,
    val appBarTitle: String,
    val orderBean: OrderBean = OrderBean(OrderEnum.name, 1),

    // 交互模式状态
    val isLongClickState: Boolean = false,
    val isCutState: Boolean = false,
    val cutFileList: List<FileBean> = emptyList(),

    // 核心数据源与分页总数
    val source: FileListSource = FileListSource.Folder("0"),
    val totalCount: Int = 0,
    //视频排序版本，用于LaunchedEffect滚动监听
    val videoOrderVersion: Int = 0
) {
    // 兼容现有引用点
    val isQueryState: Boolean
        get() = source !is FileListSource.Folder
}

/**
 * 弹窗交互独立状态（高频局部交互，彻底与列表数据流物理隔离）
 */
@Immutable
data class FileDialogUiState(
    val activeDialog: FileDialogState = FileDialogState.None,
    val targetFileBean: FileBean? = null,
    val fileInfo: FileInfo = FileInfo(),
    val torrentBean: TorrentFileBean = TorrentFileBean(),
    val unzipBeanList: ZipBeanList = ZipBeanList(),
    val discardVideoList: List<VideoBean> = emptyList(),
    val targetCid: String = ""
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
    val nestedScrollConnection: NestedScrollConnection,
    val isSearchState: Boolean = false
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
    val imageCache: Map<String, ImageBean>? = null,
    /**
     * 当前列表对应的数据源唯一分页键（如 folder:0, search:0:keyword, filter:0:1）
     * 明确由上层 contentState.source 提供，不设默认值以杜绝传参疏漏，
     * 用于驱动视图树感知模式切换，安全重置快速滚动条与列表容器
     */
    val paginationKey: String,
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
