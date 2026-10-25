package github.zerorooot.nap511.repository.paging

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.OrderBean
import kotlinx.coroutines.flow.Flow

/**
 * 单页步进追加结果载体（专供 UI 触底加载使用）
 *
 * @property rawNewItems 本次网络请求原始返回经 formatFileBeanList 规范化后的新增条目列表
 * @property combinedList 与现有列表合并且 distinctBy 唯一性去重后的全量条目列表
 * @property latestTotalCount 服务端返回的最新的目录/搜索/过滤条目总数 count
 * @property nextPhysicalOffset 服务端物理窗口游标累加值（严禁使用去重后的 combinedList.size，防止漂移倒退）
 * @property isComplete 本次追加后是否已达到全部数据加载完毕状态
 */
data class PageAppendResult(
    val rawNewItems: List<FileBean>,
    val combinedList: List<FileBean>,
    val latestTotalCount: Int,
    val nextPhysicalOffset: Int,
    val isComplete: Boolean
)

/**
 * 全量/流式拉取配置项（专供终端全量扫描与快速短路寻址使用）
 *
 * @property cid 目标目录 ID
 * @property order 排序配置（排序字段与升降序）
 * @property fcMix 混排策略（0：文件夹置顶，1：文件与文件夹混排）
 * @property forceRefresh 是否强制跳过缓存直接发起全新网络拉取
 * @property maxPages 最大拉取页数限制（默认 50 页，路径寻址模式可传 1 实现毫秒级短路）
 * @property onProgress 分页拉取进度回调函数 (当前累计条目数, 服务端总条目数) -> Unit
 */
data class PagingOptions(
    val cid: String,
    val order: OrderBean,
    val fcMix: Int,
    val forceRefresh: Boolean = false,
    val maxPages: Int = MAX_PAGES_LIMIT,
    val onProgress: ((loaded: Int, total: Int) -> Unit)? = null
) {
    companion object {
        /** 单目录全量拉取安全硬上限：50 页 * 1150 = 57,500 项，防御超巨型目录引发 OOM */
        const val MAX_PAGES_LIMIT = 50

        /** 快速路径解析与寻址短路模式：仅拉取第 1 页 */
        const val FAST_MODE_PAGES = 1
    }

    /** 终端批处理全量拉取的 Single-Flight 并发消峰隔离键 */
    val batchFlightKey: String
        get() = "batch:$cid:${order.type}:${order.asc}:$fcMix:$forceRefresh:$maxPages"
}

/**
 * 统一目录分页与缓存协同器核心接口
 *
 * 统管网络请求分发、服务端物理游标推进、防风控平滑微延时、条目唯一去重合并、
 * 仓储级 Single-Flight 并发消峰以及分阶段事务性缓存落盘。
 */
interface DirectoryPagingCoordinator {

    /**
     * 【模式 1：UI 触底单页步进追加】
     * 针对 FileListSource（Folder/Search/Filter）基于现有列表追加下一页。
     * 内部完成网络分发、严格物理游标累加、规范化去重合并、完备性判定，并在 Folder 模式下同步就地更新缓存。
     *
     * @param source 数据源模式（Folder/Search/Filter）
     * @param currentList 当前 UI 已经持有的条目列表
     * @param currentPhysicalOffset 当前向服务端请求使用的物理游标起点（从 0 开始累计物理返回量）
     * @param currentTotalCount 当前记录的服务端总条目数
     * @param order 排序规则
     * @param fcMix 文件夹置顶/混排参数
     * @return [PageAppendResult] 包含新条目、去重合并后全集、下一页物理游标及完备性状态
     */
    suspend fun fetchNextPage(
        source: FileListSource,
        currentList: List<FileBean>,
        currentPhysicalOffset: Int,
        currentTotalCount: Int,
        order: OrderBean,
        fcMix: Int
    ): PageAppendResult

    /**
     * 【模式 2：全量/分阶段拉取】
     * 适用于终端全量命令（ls/find）及快速短路寻址（resolveTarget）。
     * 具备三位一体缓存门禁、零时间衰减从 0 重拉、协程局部内存暂存、仓储级 Single-Flight 保护与完成时原子广播落盘。
     *
     * @param options 分页拉取配置选项
     * @return 完整目录条目列表（已去重与规范化）
     */
    suspend fun fetchDirectory(options: PagingOptions): List<FileBean>

    /**
     * 【模式 3：反应式流式输出】
     * 适用于支持管道短路的命令（如 find ... | head -n 5）。
     * 逐页发射条目批次，下游管道断开时自动级联取消上游未发起的网络请求；
     * 若正常完整消费完毕，自动通过 Best-Effort 旁路沉淀落盘到 FileCacheManager。
     *
     * @param options 分页拉取配置选项
     * @return 逐页发射条目的反应式 Flow
     */
    fun fetchDirectoryStream(options: PagingOptions): Flow<List<FileBean>>
}
