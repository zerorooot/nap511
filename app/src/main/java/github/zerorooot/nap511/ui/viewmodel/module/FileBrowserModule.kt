package github.zerorooot.nap511.ui.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.data.local.cache.FileCacheManager
import github.zerorooot.nap511.data.model.FileBean
import github.zerorooot.nap511.data.model.FilesBean
import github.zerorooot.nap511.data.model.OrderBean
import github.zerorooot.nap511.data.model.PagingCache
import github.zerorooot.nap511.data.model.OrderType
import github.zerorooot.nap511.data.model.PathBean
import github.zerorooot.nap511.data.model.uniqueId
import github.zerorooot.nap511.data.network.client.UserSessionManager
import github.zerorooot.nap511.data.repository.FileRepository
import github.zerorooot.nap511.data.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.platform.AppStringProvider
import github.zerorooot.nap511.platform.CoilCacheCleaner
import github.zerorooot.nap511.platform.ToastProvider
import github.zerorooot.nap511.ui.model.FileDialogState
import github.zerorooot.nap511.ui.model.FileListSource
import github.zerorooot.nap511.ui.model.SettingUiState
import github.zerorooot.nap511.ui.navigation.NavEvent
import github.zerorooot.nap511.ui.navigation.Route
import github.zerorooot.nap511.ui.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.ui.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.ui.viewmodel.store.PagingStateStore
import github.zerorooot.nap511.ui.viewmodel.store.ScrollPositionStore
import github.zerorooot.nap511.util.formatFileBeanList
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 导航快照：记录进入乐观导航前的稳定状态，供网络/数据异常时瞬时平滑回滚
 */
data class NavigationSnapshot(
    val previousCid: String,
    val previousPathList: List<PathBean>,
    val previousSource: FileListSource,
    val previousFileBeans: List<FileBean>,
    val previousTotalCount: Int
)

/**
 * 返回键层级导航判定结果
 */
sealed interface BackResult {
    data object Handled : BackResult
    data class NavigateTo(val targetCid: String) : BackResult
    data object NotHandled : BackResult
}

/**
 * 文件浏览领域模块
 * 负责目录列表加载、触底分页步进、模式化排序、搜索与分类过滤、多选状态机以及返回层级路由判定。
 */
class FileBrowserModule(
    private val stateHolder: FileUiStateHolder,
    private val pagingStateStore: PagingStateStore,
    private val imageCacheStore: ImageCacheStore,
    private val fileRepository: FileRepository,
    private val pagingCoordinator: DirectoryPagingCoordinator,
    private val stringProvider: AppStringProvider,
    private val coilCacheCleaner: CoilCacheCleaner,
    private val settingUiStateFlow: StateFlow<SettingUiState>,
    private val toastProvider: ToastProvider,
    private val scrollPositionStore: ScrollPositionStore = ScrollPositionStore()
) {
    private val _navigationEvent = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<NavEvent> = _navigationEvent.asSharedFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent: SharedFlow<Unit> = _scrollToTopEvent.asSharedFlow()

    private var isInitialized = false

    //应用启动或 ViewModel 初始化时，settingUiStateFlow 会自动发射当前的设置数据
    //如果不为null,会导致应用一打开就向服务端发起一次无意义的 fileRepository.order(...) 请求
    private var lastKeepFoldersOnTop: Boolean? = null
    private val settingUiState: SettingUiState
        get() = settingUiStateFlow.value

    /** 当前生效的本地自定义排序（仅在内存中针对当前视图生效，用于弹窗回显；刷新/切目录/分页时失效） */
    private var activeLocalOrder: OrderBean? = null

    /** 乐观导航历史快照：记录进入乐观导航前的稳定状态，供网络/数据异常时瞬时平滑回滚 */
    internal var currentSnapshot: NavigationSnapshot? = null

    /**
     * 服务端真实的云端排序（供网络请求及分页严格使用，绝不包含本地排序字段）
     */
    val serverOrderBean: OrderBean
        get() = when (val s = stateHolder.currentContentState.source) {
            is FileListSource.Filter -> s.order
            is FileListSource.Star -> s.order
            else -> stateHolder.currentContentState.orderBean
        }

    /**
     * 获取当前活跃模式的排序规则：
     * - 若当前存在生效的本地自定义排序（标星/时长），优先返回以供排序弹窗高亮回显
     * - 否则回退为当前模式的服务端真实排序规则
     */
    val currentOrderBean: OrderBean
        get() = activeLocalOrder ?: serverOrderBean

    /**
     * 115 文件夹/文件混排参数：
     * - fc_mix = 0：文件夹置顶（默认）
     * - fc_mix = 1：文件与文件夹混排
     */
    val currentFcMix: Int
        get() = if (settingUiState.keepFoldersOnTop) 0 else 1

    /**
     * 当前展示目录实际关联的 FilesBean fcMix 配置（0：文件夹置顶，1：文件混排）。
     */
    val currentFilesBeanFcMix: Int
        get() = stateHolder.currentContentState.fcMix

    /** 响应系统设置变更（保持置顶状态与排序联动） */
    suspend fun onSettingsChanged(keepFoldersOnTop: Boolean) {
        // 不一致，说明用户切换了排序设置，需要手动再 order 一次，否则只要不点击排序，当前顺序永远无法改变
        if (lastKeepFoldersOnTop != null && lastKeepFoldersOnTop != keepFoldersOnTop) {
            order(false)
        }
        lastKeepFoldersOnTop = keepFoldersOnTop
    }

    /**
     * 启动预加载根目录缓存文件
     */
    suspend fun loadCacheFile() {
        if (isInitialized) return
        isInitialized = true
        stateHolder.setRefreshing(true)
        withContext(Dispatchers.IO) {
            // 优先校验登录状态
            if (UserSessionManager.cookie.isBlank()) {
                stateHolder.setRefreshing(false)
                // 未登录：静默发送跳转登录页事件
                _navigationEvent.emit(NavEvent.NavigateToScreen(Route.Login))
                return@withContext
            }

            // 【首屏冷启动优化】：若首帧已直接从内存缓存注入了根目录 "0" 且数据不为空，无需重复覆盖重写 StateFlow 触发二次重组
            val currentState = stateHolder.currentContentState
            if (currentState.currentCid == "0" && currentState.fileBeanList.isNotEmpty()) {
                // 冷启动首帧来自内存缓存，可能无物理游标锚点（旧缓存/Gson 缺字段）。
                // 用 displayable 判定缓存是否自洽可信；不通过则回源重建，避免带着无锚点残卷继续分页。
                // 注意：Folder 的 complete 已从 FilesBean.isComplete 派生，此处不再写 isLoadedCompleteMap（原为死写入）。
                if (PagingCache.displayable(FileCacheManager.getDate("0"), "0")) {
                    XLog.d("FileBrowserModule.loadCacheFile: root files already populated from initial memory cache, items=${currentState.fileBeanList.size}")
                    stateHolder.setRefreshing(false)
                    return@withContext
                }
                XLog.d("FileBrowserModule.loadCacheFile: root cache not displayable, reentry from network")
                reentry("0")
                return@withContext
            }

            // FileCacheManager.loadAllCache() 已在 SplashScreenManager 加载阶段完成预加载
            getFiles("0")
        }
    }

    /**
     * 预加载指定 cid 的缓存
     *
     * 静默预拉该 cid 的目录缓存，不触碰当前 UI 展示状态。
     *
     * @param cid    目标目录 cid
     * @param sort   目标目录的排序上下文快照（来自 [github.zerorooot.nap511.data.local.cache.CacheEvent.RemoteRefreshRequired.sort]）。
     *               为 null 时回退到「当前展示目录的 serverOrderBean + 全局 currentFcMix」，
     *               与修复前的旧行为一致（冷目录兜底，不劣化）。
     *               注意：快照里的 [FilesBean.cid] 仅作日志校验，请求始终以入参 cid 为准。
     * @param force  true 时穿透「已有缓存即早退」的保护。用于并发多次 notify 的场景：
     *               A 的预拉先写回缓存后，B 的预拉不应被早退跳过，否则 B 携带的快照会丢失。
     */
    suspend fun updateFileCache(cid: String, sort: FilesBean? = null, force: Boolean = false) {
        // 缓存已存在则跳过网络请求
        // force = true 时穿透，保证并发 notify 场景下每次携带的排序快照都能落地。
        if (!force && FileCacheManager[cid] != null) {
            XLog.d("updateFileCache: cid=$cid already cached, skip (force=false)")
            return
        }

        // 排序参数优先级：目标目录快照 > 当前展示目录的兜底值
        // order 额外加空串守卫：FilesBean.order 默认值为 ""，旧缓存/非列表来源可能写入空串，
        // 直接透传会在 115 请求里发出 o= 空参数（比修复前更糟），所以空白时回退兜底。
        // isAsc / fcMix 是 Int 无法表达「缺失」，存在快照即直接采用（快照来自服务端回显，同源可信）。
        val targetOrder = sort?.order?.takeIf { it.isNotBlank() } ?: serverOrderBean.type
        val targetAsc = sort?.isAsc ?: serverOrderBean.asc
        val targetFcMix = sort?.fcMix ?: currentFcMix

        XLog.d(
            "updateFileCache: cid=$cid, force=$force, " +
                    "snapshot=[order=${sort?.order}, isAsc=${sort?.isAsc}, fcMix=${sort?.fcMix}] (snapCid=${sort?.cid}), " +
                    "fallback=[order=${serverOrderBean.type}, asc=${serverOrderBean.asc}, fcMix=$currentFcMix], " +
                    "applied=[order=$targetOrder, asc=$targetAsc, fcMix=$targetFcMix]"
        )

        runCatching {
            val files = fileRepository.getFiles(
                cid = cid,
                order = targetOrder,
                asc = targetAsc,
                fcMix = targetFcMix
            )
            // 写回缓存时**不覆盖** order / isAsc / fcMix：
            // getFiles 返回的 FilesBean 自带服务端回显的排序字段，它才是最新权威值。
            // 快照只是「请求参数」的来源，不是「响应结果」的修正项。
            val formatted = files.copy(fileBeanList = formatFileBeanList(files.fileBeanList))
            // 静默预拉也是从 0 首屏消费，写入物理游标锚点供后续触底续拉使用。
            val paged = PagingCache.fromHead(formatted, files.fileBeanList.size, formatted.fileBeanList)
            FileCacheManager[cid] = paged
            XLog.d(
                "updateFileCache: cid=$cid cached, echo=[order=${paged.order}, isAsc=${paged.isAsc}, fcMix=${paged.fcMix}], " +
                        "items=${paged.fileBeanList.size}, count=${paged.count}, physicalOffset=${paged.physicalOffset}"
            )
        }.onFailureToastAndLog(toastProvider)
    }

    /**
     * 准备进入乐观导航状态
     * 1. 优先进行本地缓存短路检查，若命中则 0ms 瞬间直出，避免出现闪烁骨架屏；
     * 2. 仅在稳定态（!isLoadingFolder）记录前序快照，连续点击不被空列表污染；
     * 3. 瞬间清空列表、更新乐观路径并呈现全屏微光骨架屏。
     *
     * @return true 表示需要发起网络拉取；false 表示已瞬时命中缓存
     */
    fun prepareOptimisticNavigation(targetCid: String, targetName: String): Boolean {
        // 1. 缓存即时短路检查（0ms 直出）
        val cached = FileCacheManager.getDate(targetCid)
        // targetCid 是其它目录，其缓存 fcMix 是它自己的历史身份（用户改的设置只作用于当前目录），
        // 故用 displayable 只验自洽即可，不拿当前流身份比对；不通过则走骨架屏网络路径。
        if (PagingCache.displayable(cached, targetCid)) {
            XLog.d("prepareOptimisticNavigation: targetCid=$targetCid hit cache, instant rendering without skeleton")
            val files = cached!!.copy(fileBeanList = formatFileBeanList(cached.fileBeanList))
            setFiles(files)
            return false
        }

        val currentState = stateHolder.currentContentState

        // 2. 关键守卫：仅在当前处于非加载稳定态时才记录快照，防止骨架屏期间连续点击污染快照
        if (!currentState.isLoadingFolder) {
            currentSnapshot = NavigationSnapshot(
                previousCid = currentState.currentCid,
                previousPathList = currentState.pathList,
                previousSource = currentState.source,
                previousFileBeans = currentState.fileBeanList,
                previousTotalCount = currentState.totalCount
            )
            XLog.d("prepareOptimisticNavigation: recorded stable snapshot for previousCid=${currentState.currentCid}")
        }

        // 3. 构建乐观路径（即时在面包屑与顶栏回显）
        val optimisticPath = if (currentState.pathList.any { it.cid == targetCid }) {
            val index = currentState.pathList.indexOfFirst { it.cid == targetCid }
            currentState.pathList.take(index + 1)
        } else {
            val displayName = targetName.ifEmpty {
                if (targetCid == "0") "根目录" else "加载中..."
            }
            currentState.pathList + PathBean(
                name = displayName,
                cid = targetCid,
                pid = currentState.currentCid
            )
        }

        val targetSource = FileListSource.Folder(targetCid, stringProvider)
        pagingStateStore.reset(targetSource.paginationKey)

        // 4. 0ms 即刻更新 UI 状态为骨架屏态
        stateHolder.updateContentState {
            copy(
                source = targetSource,
                currentCid = targetCid,
                pathList = optimisticPath,
                fileBeanList = emptyList(),
                selectedFileIds = emptySet(),
                isLongClickState = false,
                isLoadingFolder = true,
                isRefreshing = false,
                appBarTitle = targetSource.appName
            )
        }
        XLog.d("prepareOptimisticNavigation: switched to optimistic skeleton state for targetCid=$targetCid")
        return true
    }

    /**
     * 撤销乐观进场，瞬间平滑恢复上级快照（明确负责复位 isLoadingFolder = false）
     */
    fun rollbackSnapshot() {
        val snapshot = currentSnapshot ?: run {
            XLog.d("rollbackSnapshot: snapshot is null, simply resetting loading states")
            stateHolder.updateContentState { copy(isLoadingFolder = false, isRefreshing = false) }
            return
        }
        XLog.d("rollbackSnapshot: rolling back to previousCid=${snapshot.previousCid}")
        stateHolder.updateContentState {
            copy(
                source = snapshot.previousSource,
                currentCid = snapshot.previousCid,
                pathList = snapshot.previousPathList,
                fileBeanList = snapshot.previousFileBeans,
                totalCount = snapshot.previousTotalCount,
                isLoadingFolder = false,
                isRefreshing = false,
                appBarTitle = snapshot.previousSource.appName
            )
        }
        currentSnapshot = null
    }

    /**
     * 获取文件列表
     * 保持挂起函数公开签名不变，双重守卫保留缓存兜底，被取消的任务绝不修改 UI 状态
     */
    suspend fun getFiles(cid: String, fcMix: Int = currentFcMix, forceCache: Boolean = false) {
        stateHolder.setRefreshing(true)
        // 1. 尝试读取缓存（内存与磁盘双级读取，支持冷启动、测试及 LRU 淘汰后从磁盘恢复）
        //    进场主路径必须验流身份（resumable）：onSettingsChanged→order(false) 只改 fcMix 不删缓存，
        //    若用宽松的 displayable 命中旧缓存，setFiles 会把 stateHolder.fcMix 覆盖回旧值 → 新流永远不生效。
        //    失配时先 remove 再走网络首屏，杜绝假回源/设置回退。
        val cached = FileCacheManager[cid]
        if (cached != null && PagingCache.resumable(cached, cid, serverOrderBean, fcMix)) {
            val files = cached.copy(
                fileBeanList = formatFileBeanList(cached.fileBeanList),
                cid = cid.ifEmpty { "0" })
            XLog.d("FileBrowserModule.getFiles: cache hit resumable for cid=$cid, offset=${cached.physicalOffset}")
            setFiles(files)
            stateHolder.setRefreshing(false)
            return
        }
        if (cached != null) {
            XLog.d("FileBrowserModule.getFiles: cache exists but not resumable for cid=$cid, remove then refetch. " +
                    "cacheStream=${cached.order}/${cached.isAsc}/${cached.fcMix}, want=${serverOrderBean.type}/${serverOrderBean.asc}/$fcMix")
            FileCacheManager.remove(cid)
        }

        try {
            val files = fileRepository.getFiles(
                cid = cid,
                order = serverOrderBean.type,
                asc = serverOrderBean.asc,
                fcMix = fcMix
            )

            //cookie校验
            if (!files.isSuccess) {
                throw NullPointerException(files.error)
            }

            // 请求的 cid 不是 0, 但返回的 cid 是 0。证明请求的 cid 不存在
            if (cid != "0" && files.cid == "0") {
                throw IllegalStateException("当前文件夹已被删除！")
            }
            val formatted = files.copy(fileBeanList = formatFileBeanList(files.fileBeanList))

            // 智能差量对齐高清图缓存：
            // 强制刷新时全量清空；普通刷新时仅保留仍在列表且已成功落盘的项（未落盘/过期项自动剔除并重拉自愈）
            if (settingUiState.forceLoadCache || forceCache) {
                XLog.d("FileBrowserModule.getFiles: force refresh, clearing imageCacheStore for cid=$cid")
                imageCacheStore.clear(cid)
            } else {
                val cachedKeys = imageCacheStore.imageBeanCache[cid]?.keys
                if (!cachedKeys.isNullOrEmpty()) {
                    val currentPickCodes =
                        formatted.fileBeanList.asSequence().map { it.pickCode }.toSet()
                    val validPickCodes = withContext(Dispatchers.IO) {
                        cachedKeys.filter { key ->
                            key in currentPickCodes && coilCacheCleaner.getCachedUrl(key) != null
                        }.toSet()
                    }
                    XLog.d("FileBrowserModule.getFiles: syncValidKeys for cid=$cid, retained=${validPickCodes.size}, totalCached=${cachedKeys.size}")
                    imageCacheStore.syncValidKeys(cid, validPickCodes)
                }
            }
            // 3. 网络请求成功后写入缓存
            //    首屏从 0 起消费：physicalOffset = 本次原始返回条数（format 只 map 不变长），
            //    作为后续触底续拉的唯一权威游标，与缓存同生共死。
            val paged = PagingCache.fromHead(formatted, files.fileBeanList.size, formatted.fileBeanList)
            XLog.d("FileBrowserModule.getFiles: cache written for cid=$cid, physicalOffset=${paged.physicalOffset}, size=${paged.fileBeanList.size}, count=${paged.count}")
            FileCacheManager[cid] = paged
            setFiles(paged) // 成功出口：在 setFiles 内部负责复位 isLoadingFolder = false
        } catch (ex: Throwable) {
            // 取消异常：静默退出，绝不执行回滚快照与 Toast
            if (ex is CancellationException) {
                XLog.d("getFiles: job cancelled for cid=$cid")
                return
            }
            XLog.e("getFiles error for cid=$cid", ex)

            val expiredTip =
                if (ex is NullPointerException) ex.message
                    ?: "获取文件列表失败，建议更新您的Cookie" else null
            if (expiredTip != null) {
                FileCacheManager.clearAll()
                UserSessionManager.clearSession()
                _navigationEvent.emit(NavEvent.NavigateToScreen(Route.Login))
                toastProvider.showToast(expiredTip)
                rollbackSnapshot()
                return
            }

            rollbackSnapshot()
            val errorMsg = when (ex) {
                is IllegalStateException -> ex.message ?: "文件夹不存在"
                else -> "网络连接异常，已返回上一层"
            }
            toastProvider.showToast(errorMsg)
        } finally {
            // 仅自然结束（非取消）且 CID 仍匹配当前活跃界面的协程，才收尾下拉刷新状态
            // 彻底解除 finally 对 isLoadingFolder 的越权篡改，从根源上消灭状态踩踏
            if (currentCoroutineContext().isActive && stateHolder.currentContentState.currentCid == cid) {
                stateHolder.setRefreshing(false)
            }
        }
    }

    /**
     * 统一回源：删除不合格缓存后走网络首屏重建。
     *
     * 供「进场主路径命中旧流缓存」「触底 resumable 失配」「冷启动首帧无锚点」共用。
     * 必须先 remove 再 getFiles：否则 getFiles 的缓存命中会再次命中旧缓存（其 displayable 不验流身份），
     * 造成假回源、把用户刚改的 fcMix 覆盖回旧值的设置回退。
     * FileCacheManager.remove 同步等待物理删除且不广播 CacheEvent，也不触碰 scrollPositionStore 账本。
     */
    private suspend fun reentry(cid: String) {
        XLog.d("FileBrowserModule.reentry: remove stale cache then refetch cid=$cid")
        FileCacheManager.remove(cid)
        getFiles(cid)
    }

    /**
     * 滑动触底自动加载下一页文件（分页加载更多）
     * UI 表现层专注状态机与人机交互门禁，数据分发、去重合并与游标递增完全委托协同器
     */
    suspend fun loadMoreFiles() {
        val state = stateHolder.currentContentState
        val source = state.source
        val paginationKey = source.paginationKey

        // 多选模式、当前数据源正在加载或正处于下拉刷新中，均不触发
        if (state.isLongClickState || state.isRefreshing || !pagingStateStore.tryAcquireFetch(
                paginationKey
            )
        ) {
            return
        }

        // 单次读取 Folder 缓存快照：完成态、流身份门禁、游标、totalCount 全部基于它，
        // 避免对同一 cid 多次 getDate 在并发淘汰/覆盖时拿到不同版本。
        val folderCache =
            if (source is FileListSource.Folder) FileCacheManager.getDate(source.cid) else null

        // 检查是否此前已被标记为加载完成。
        // Folder 源完成态从缓存的 isComplete 派生（与缓存同生死，LRU 淘汰后自然失效）；
        // non-Folder 源（Search/Filter/Star）仍用 PagingStateStore —— 它们没有缓存回声/LRU 病灶。
        val alreadyComplete = when (source) {
            is FileListSource.Folder -> folderCache?.isComplete ?: false
            else -> pagingStateStore.isComplete(paginationKey)
        }
        if (alreadyComplete) {
            pagingStateStore.releaseFetch(paginationKey)
            return
        }

        val currentList = state.fileBeanList
        if (currentList.isEmpty()) {
            pagingStateStore.releaseFetch(paginationKey)
            return
        }

        val totalCount = if (source is FileListSource.Folder) {
            state.totalCount.takeIf { it > 0 } ?: (folderCache?.count ?: 0)
        } else {
            state.totalCount
        }
        val limit = UserSessionManager.requestLimitCount

        // 【第一级：前置拦截】如果本地已知条目已达服务端总量，直接完成并拦截无意义网络请求
        // 保护：仅当总数量大于单页容量（确实存在多页分页）时才提示“加载完毕”，避免只有几十项的小目录一滑到底就误弹 Toast
        if (totalCount > 0 && currentList.size >= totalCount) {
            pagingStateStore.markComplete(paginationKey)
            pagingStateStore.releaseFetch(paginationKey)
            if (totalCount > limit) {
                toastProvider.showToast("加载完毕")
            }
            return
        }

        stateHolder.setRefreshing(true)

        // 记录请求发起时的上下文版本，用于并发校验防串位（分页网络请求严格采用服务端排序）
        val currentRequestSource = source
        val currentRequestOrder = serverOrderBean
        // 读取首屏已缓存的该目录 fcMix ,防止在 FileOrderDialog 设置了fcmix,但不生效
        val currentFcMix = stateHolder.currentContentState.fcMix

        // 获取当前物理游标起点。
        // Folder：游标唯一权威是缓存的 physicalOffset，且必须先过 resumable 门禁（流身份+自洽）；
        //         失配时先 remove 旧缓存再 reentry 回源，杜绝「假回源/设置回退」（onSettingsChanged 改 fcMix 场景）。
        // non-Folder：沿用 PagingStateStore，首次触底以当前列表大小作为初始游标。
        val currentPhysicalOffset = when (source) {
            is FileListSource.Folder -> {
                if (!PagingCache.resumable(folderCache, source.cid, serverOrderBean, currentFcMix)) {
                    XLog.d("loadMoreFiles: folder cache not resumable for cid=${source.cid}, reentry")
                    pagingStateStore.releaseFetch(paginationKey)
                    reentry(source.cid)
                    return
                }
                folderCache!!.physicalOffset
            }
            else -> pagingStateStore.getOrInitOffset(paginationKey, currentList.size)
        }

        try {
            XLog.d("loadMoreFiles delegating to pagingCoordinator: source=$currentRequestSource, physicalOffset=$currentPhysicalOffset, limit=$limit")

            // 委托统一协同器执行单页步进追加（多态支持 Folder/Search/Filter）
            val result = pagingCoordinator.fetchNextPage(
                source = currentRequestSource,
                currentList = currentList,
                currentPhysicalOffset = currentPhysicalOffset,
                currentTotalCount = totalCount,
                order = currentRequestOrder,
                fcMix = currentFcMix
            )

            // 推进记录服务端物理窗口游标，消灭因去重产生的游标倒退漂移。
            // Folder 的游标由 coordinator 在 append 时写回缓存（唯一权威），此处不再写物理副本；
            // non-Folder 仍由 PagingStateStore 维护。
            if (source !is FileListSource.Folder) {
                pagingStateStore.setOffset(paginationKey, result.nextPhysicalOffset)
            }

            // 【并发竞争保护】：仅在当前屏幕仍停留在发起请求的模式与上下文时才更新 UI
            if (
                stateHolder.currentContentState.source == currentRequestSource &&
                serverOrderBean == currentRequestOrder
            ) {
                // 分页追加数据后重置本地排序，直接以服务端返回的数据集和排序覆盖
                activeLocalOrder = null
                stateHolder.updateContentState {
                    copy(
                        fileBeanList = result.combinedList,
                        totalCount = result.latestTotalCount
                    )
                }
            }

            // 【第二级：完备性状态闭环】
            if (result.isComplete) {
                pagingStateStore.markComplete(paginationKey)
                if (result.combinedList.size > limit) {
                    toastProvider.showToast("加载完毕")
                }
            }
        } catch (e: Exception) {
            XLog.e("loadMoreFiles Exception", e)
            toastProvider.showToast("加载更多失败")
        } finally {
            pagingStateStore.releaseFetch(paginationKey)
            if (stateHolder.currentContentState.source.paginationKey == paginationKey) {
                stateHolder.setRefreshing(false)
            }
        }
    }

    suspend fun order(
        isRefresh: Boolean = true,
        cid: String? = null,
        order: OrderBean? = null,
        fcMix: Int = currentFcMix
    ) {
        val currentCid = cid ?: stateHolder.currentContentState.currentCid
        val b = order ?: stateHolder.currentContentState.orderBean
        val map = mapOf(
            "user_order" to b.type,
            "user_asc" to b.asc.toString(),
            "file_id" to currentCid,
            "fc_mix" to fcMix.toString()
        )

        runCatching {
            val orderResult = fileRepository.order(map)
            XLog.d("order=$orderResult, map=$map")
            if (orderResult.state) {
                // ★ 同时保存新选中的 orderBean 和 fcMix
                // non-Folder 源（Filter/Star）的 paginationKey 含 order/fcMix，order(false) 成功后 key 变化，
                // 旧 key 下的 offset/complete/lock 必须清理，否则新流会复用旧游标。
                // Folder 源此处 key 不变，靠进场/触底的 resumable 门禁失配回源兜底，无需 reset。
                val oldPaginationKey = stateHolder.currentContentState.source.paginationKey
                val isNonFolder = stateHolder.currentContentState.source !is FileListSource.Folder
                stateHolder.updateContentState { copy(orderBean = b, fcMix = fcMix) }
                if (isNonFolder) {
                    pagingStateStore.reset(oldPaginationKey)
                    XLog.d("order: reset non-folder paging state for oldKey=$oldPaginationKey")
                }
                if (isRefresh) {
                    refresh(currentCid, fcMix = fcMix)
                }
                _scrollToTopEvent.tryEmit(Unit)
            } else {
                toastProvider.showToast("排序失败")
            }
        }.onFailureToastAndLog(toastProvider)
    }

    suspend fun updateOrder(order: OrderBean, fcMix: Int) {
        XLog.d("updateOrder called: order=$order, fcMix=$fcMix")
        if (order.orderType.isLocalOnly) {
            applyLocalOrder(order, fcMix)
            return
        }
        activeLocalOrder = null
        when (val s = stateHolder.currentContentState.source) {
            is FileListSource.Search -> {
                toastProvider.showToast("暂不支持搜索模式排序")
            }

            is FileListSource.Filter -> {
                this.order(isRefresh = false, order = order, fcMix = fcMix)
                filterFile(type = s.filterType, name = s.name, order = order)
            }

            is FileListSource.Folder -> {
                this.order(isRefresh = true, order = order, fcMix = fcMix)
            }

            is FileListSource.Star -> {
                this.order(isRefresh = false, cid = "0", order = order, fcMix = fcMix)
                starFileScreen(order = order, fcMix)
            }
        }
    }

    suspend fun refresh(forceCache: Boolean = false) {
        when (val s = stateHolder.currentContentState.source) {
            is FileListSource.Folder -> refresh(
                stateHolder.currentContentState.currentCid,
                forceCache
            )

            is FileListSource.Search -> {
                clearSelection()
                pagingStateStore.reset(s.paginationKey)
                search(s.keyword)
            }

            is FileListSource.Filter -> {
                clearSelection()
                pagingStateStore.reset(s.paginationKey)
                // 携带 s.order 和 s.fcMix 刷新，确保用户调整后的过滤排序在下拉刷新时不丢失
                filterFile(type = s.filterType, name = s.name, order = s.order, fcMix = s.fcMix)
            }

            is FileListSource.Star -> {
                clearSelection()
                pagingStateStore.reset(s.paginationKey)
                // 携带 s.order 和 s.fcMix 刷新，确保用户调整后的排序在下拉刷新时不丢失
                starFileScreen(order = s.order, fcMix = s.fcMix)
            }
        }
    }

    suspend fun refresh(cid: String, forceCache: Boolean = false, fcMix: Int? = null) {
        clearSelection()
        val paginationKey = FileListSource.folderKey(cid)
        pagingStateStore.reset(paginationKey)
        stateHolder.updateContentState { copy(source = FileListSource.Folder(cid, stringProvider)) }
        val refreshCurrent = (cid == stateHolder.currentContentState.currentCid)
        // 如果外部传了明确的 fcMix 就用（比如刚排序完），没传就用 currentFcMix
        val targetFcMix =
            fcMix ?: if (refreshCurrent) stateHolder.currentContentState.fcMix else currentFcMix
        XLog.d(
            "refresh refreshCurrent:$refreshCurrent, settingUiState.forceLoadCache:${settingUiState.forceLoadCache}, forceCache:$forceCache," +
                    "order = ${stateHolder.currentContentState.orderBean.type}, asc = ${stateHolder.currentContentState.orderBean.asc}, fcMix = $targetFcMix"
        )

        if (settingUiState.forceLoadCache || forceCache) {
            if (cid == "0") {
                // 【根目录全量重置】：直接在 IO 协程中清空 Coil 内存与磁盘缓存，并清空全部文件缓存。
                withContext(Dispatchers.IO) {
                    coilCacheCleaner.clearMemoryCache()
                    coilCacheCleaner.clearDiskCache()
                    FileCacheManager.clearAll()
                }
            } else {
                // 【子目录精准定向清理】：在 IO 线程异步清理当前展示条目图标与原图缩略图，并静默递归清理子树缓存
                cleanFileListCoilCache(stateHolder.currentContentState.fileBeanList)
                removeFolderCacheRecursively(cid)
            }
        } else {
            FileCacheManager.remove(cid)
        }

        if (refreshCurrent) {
            getFiles(
                cid = stateHolder.currentContentState.currentCid,
                fcMix = targetFcMix,
                forceCache = forceCache
            )
        } else {
            imageCacheStore.clear(cid)
            updateFileCache(cid)
        }
    }

    /**
     * 在 IO 调度器下彻底清理指定文件列表对应的 Coil 内存与磁盘缓存
     * 双键（fileId 缩略图 + pickCode 高清图）规则已下沉到 platform 扩展，全仓唯一实现，
     * 此处只做委托，避免本模块与 mutator 各写一份。
     */
    suspend fun cleanFileListCoilCache(list: List<FileBean>) = coilCacheCleaner.cleanFileCaches(list)

    suspend fun removeFolderCacheRecursively(categoryId: String) = withContext(Dispatchers.IO) {
        if (categoryId == "0") return@withContext
        val visited = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(categoryId)
        while (queue.isNotEmpty()) {
            val currentCid = queue.removeFirst()
            if (!visited.add(currentCid)) continue
            val cachedFiles = FileCacheManager.getDate(currentCid)
            cachedFiles?.fileBeanList?.forEach { child ->
                if (child.isFolder && child.categoryId.isNotEmpty()) {
                    queue.add(child.categoryId)
                }
                if (child.fileId.isNotEmpty()) {
                    coilCacheCleaner.deleteCoilCache(child.fileId)
                }
                if (child.pickCode.isNotEmpty()) {
                    coilCacheCleaner.deleteCoilCache(child.pickCode)
                }
            }
            FileCacheManager.remove(currentCid)
        }
    }

    suspend fun search(searchKey: String) {
        val currentCid = stateHolder.currentContentState.currentCid
        val targetSource = FileListSource.Search(currentCid, searchKey, stringProvider)
        val limit = UserSessionManager.requestLimitCount
        stateHolder.setRefreshing(true)

        runCatching {
            val files = fileRepository.search(
                cid = currentCid, searchValue = searchKey, offset = 0, limit = limit
            )
            val formattedList = formatFileBeanList(files.fileBeanList)
            if (formattedList.size < limit || (files.count > 0 && formattedList.size >= files.count)) {
                pagingStateStore.markComplete(targetSource.paginationKey)
            }
            stateHolder.updateContentState {
                copy(
                    source = targetSource,
                    isLongClickState = false,
                    fileBeanList = formattedList,
                    totalCount = files.count,
                    appBarTitle = targetSource.appName
                )
            }
        }.onFailureToastAndLog(toastProvider)
        stateHolder.setRefreshing(false)
    }

    suspend fun starFileScreen(order: OrderBean, fcMix: Int = currentFcMix) {
        activeLocalOrder = null
        val limit = UserSessionManager.requestLimitCount

        stateHolder.setRefreshing(true)
        runCatching {
            val files = fileRepository.starFile(0, order, fcMix, limit)
            val formattedList = formatFileBeanList(files.fileBeanList)
            val returnOrder = OrderBean.from(files)
            val targetSource =
                FileListSource.Star("0", returnOrder, fcMix, stringProvider)

            if (formattedList.size < limit || (files.count > 0 && formattedList.size >= files.count)) {
                pagingStateStore.markComplete(targetSource.paginationKey)
            }
            stateHolder.updateContentState {
                copy(
                    source = targetSource,
                    isLongClickState = false,
                    fileBeanList = formattedList,
                    totalCount = files.count,
                    appBarTitle = targetSource.appName,
                    fcMix = fcMix,
                    pathList = files.path,
                    isRefreshing = false,
                )
            }
        }.onFailureToastAndLog(toastProvider)
        stateHolder.setRefreshing(false)
    }

    suspend fun filterFile(
        type: Int,
        name: String,
        order: OrderBean,
        fcMix: Int = currentFilesBeanFcMix
    ) {
        activeLocalOrder = null
        val currentCid = stateHolder.currentContentState.currentCid
        val limit = UserSessionManager.requestLimitCount
        stateHolder.setRefreshing(true)

        runCatching {
            val files = fileRepository.filterFile(
                cid = currentCid,
                type = type,
                order = order.type,
                asc = order.asc,
                offset = 0,
                limit = limit,
                fcMix = fcMix
            )
            val formattedList = formatFileBeanList(files.fileBeanList)
            val targetSource =
                FileListSource.Filter(
                    currentCid,
                    type,
                    name,
                    OrderBean.from(files),
                    fcMix,
                    stringProvider
                )

            if (formattedList.size < limit || (files.count > 0 && formattedList.size >= files.count)) {
                pagingStateStore.markComplete(targetSource.paginationKey)
            }
            stateHolder.updateContentState {
                copy(
                    source = targetSource,
                    fcMix = targetSource.fcMix,
                    isLongClickState = false,
                    fileBeanList = formattedList,
                    totalCount = files.count,
                    appBarTitle = targetSource.appName
                )
            }
        }.onFailureToastAndLog(toastProvider)
        stateHolder.setRefreshing(false)
    }

    /**
     * 应用本地自定义排序（仅在内存中针对当前列表重排，不同步至服务端）
     * 1. 严格遵循 settingUiState.keepFoldersOnTop（开启置顶时文件夹优先，关闭时混排）
     * 2. 标星排序：降序时标星在前，升序时未标星在前
     * 3. 视频时长排序：降序时长从大到小，升序时长从小到大（无时长或非视频项始终沉底）
     * 4. 纯内存视图重排，不改写全局 orderBean，绝不污染后续任何网络请求
     */
    fun applyLocalOrder(order: OrderBean, fcMix: Int = currentFcMix) {
        XLog.d("应用本地自定义排序: type=${order.type}, asc=${order.asc}, fcMix=$fcMix")
        val list = stateHolder.currentContentState.fileBeanList
        if (list.isEmpty()) {
            XLog.d("当前文件列表为空，忽略本地自定义排序")
            return
        }

        // 针对没有匹配项给出提示并直接中断，不改变列表或任何状态
        when (order.orderType) {
            OrderType.STAR_FILE -> {
                if (list.none { it.isStar }) {
                    XLog.d("当前列表没有星标文件，中断本地排序")
                    toastProvider.showToast("当前列表没有星标文件")
                    return
                }
            }

            OrderType.VIDEO_DURATION -> {
                if (list.none { it.playLong > 0 || it.isVideo == 1 }) {
                    XLog.d("当前列表没有视频文件，中断本地排序")
                    toastProvider.showToast("当前列表没有视频文件")
                    return
                }
            }

            else -> {}
        }

        val keepFoldersOnTop = (fcMix == 0)
        val comparator = Comparator<FileBean> { a, b ->
            // 1. 文件夹置顶规则 (keepFoldersOnTop)
            if (keepFoldersOnTop && a.isFolder != b.isFolder) {
                return@Comparator if (a.isFolder) -1 else 1
            }

            // 2. 本地自定义排序规则
            when (order.orderType) {
                OrderType.STAR_FILE -> {
                    val aStar = if (a.isStar) 1 else 0
                    val bStar = if (b.isStar) 1 else 0
                    // 降序(asc==0)：标星(1) 在前，未标星(0) 在后；升序(asc==1)：未标星(0) 在前，标星(1) 在后
                    if (order.isAscending) aStar.compareTo(bStar) else bStar.compareTo(aStar)
                }

                OrderType.VIDEO_DURATION -> {
                    val aDur = a.playLong
                    val bDur = b.playLong
                    val aHasDur = aDur > 0
                    val bHasDur = bDur > 0

                    if (aHasDur && bHasDur) {
                        // 两者均有时长，按时长比较
                        if (order.isAscending) aDur.compareTo(bDur) else bDur.compareTo(aDur)
                    } else if (aHasDur != bHasDur) {
                        // 有时长的排在前面，无时长的沉底（无论是升序还是降序）
                        if (aHasDur) -1 else 1
                    } else {
                        0
                    }
                }

                else -> 0
            }
        }

        val sortedList = list.sortedWith(comparator)
        activeLocalOrder = order
        stateHolder.updateContentState {
            copy(
                fileBeanList = sortedList,
                fcMix = fcMix
            )
        }
        _scrollToTopEvent.tryEmit(Unit)
    }

    // ==================== 选择模式内聚逻辑 ====================

    fun selectToUp() {
        stateHolder.updateContentState {
            val firstSelectedIndex = fileBeanList.indexOfFirst { it.uniqueId in selectedFileIds }
            if (firstSelectedIndex == -1) return@updateContentState this

            val newlySelected = fileBeanList.take(firstSelectedIndex + 1).map { it.uniqueId }
            val newSelectedIds = selectedFileIds + newlySelected
            copy(
                selectedFileIds = newSelectedIds,
                appBarTitle = newSelectedIds.size.toString()
            )
        }
    }

    fun selectToDown() {
        stateHolder.updateContentState {
            val firstSelectedIndex = fileBeanList.indexOfFirst { it.uniqueId in selectedFileIds }
            if (firstSelectedIndex == -1) return@updateContentState this

            val newlySelected = fileBeanList.drop(firstSelectedIndex).map { it.uniqueId }
            val newSelectedIds = selectedFileIds + newlySelected
            copy(
                selectedFileIds = newSelectedIds,
                appBarTitle = newSelectedIds.size.toString()
            )
        }
    }

    fun selectReverse() {
        stateHolder.updateContentState {
            val allIds = fileBeanList.map { it.uniqueId }.toSet()
            val newSelectedIds = allIds - selectedFileIds
            val count = newSelectedIds.size
            copy(
                selectedFileIds = newSelectedIds,
                appBarTitle = count.toString()
            )
        }
    }

    fun select(fileBean: FileBean) {
        toggleSelect(fileBean)
    }

    fun select(index: Int) {
        stateHolder.currentContentState.fileBeanList.getOrNull(index)?.let { select(it) }
    }

    fun toggleSelect(fileBean: FileBean) {
        stateHolder.updateContentState {
            val uid = fileBean.uniqueId
            val newSelectedIds = if (uid in selectedFileIds) {
                selectedFileIds - uid
            } else {
                selectedFileIds + uid
            }
            val count = newSelectedIds.size
            copy(
                selectedFileIds = newSelectedIds,
                appBarTitle = count.toString()
            )
        }
    }

    fun toggleSelect(index: Int) {
        stateHolder.currentContentState.fileBeanList.getOrNull(index)?.let { toggleSelect(it) }
    }

    fun startMultiSelect(fileBean: FileBean) {
        stateHolder.updateContentState {
            val newSelectedIds = selectedFileIds + fileBean.uniqueId
            copy(
                isLongClickState = true,
                selectedFileIds = newSelectedIds,
                appBarTitle = newSelectedIds.size.toString()
            )
        }
    }

    fun startMultiSelect(index: Int) {
        stateHolder.currentContentState.fileBeanList.getOrNull(index)?.let { startMultiSelect(it) }
    }

    fun toggleSelectAll() {
        stateHolder.updateContentState {
            val isAll =
                fileBeanList.isNotEmpty() && fileBeanList.all { it.uniqueId in selectedFileIds }
            val newSelectedIds = if (isAll) emptySet() else fileBeanList.map { it.uniqueId }.toSet()
            val count = newSelectedIds.size
            copy(
                selectedFileIds = newSelectedIds,
                appBarTitle = count.toString(),
                isLongClickState = true // 保留多选状态机，便于继续点选
            )
        }
        XLog.d("toggleSelectAll: 切换全选状态，当前选中数: ${stateHolder.currentContentState.appBarTitle}")
    }

    fun clearSelection() {
        stateHolder.updateContentState {
            copy(
                selectedFileIds = emptySet(),
                isLongClickState = false,
                appBarTitle = source.appName
            )
        }
    }

    fun recoverFromLongPress() {
        clearSelection()
    }

    // ==================== 返回导航层级路由 ====================

    fun back(): BackResult {
        val state = stateHolder.currentContentState
        if (state.isLongClickState) {
            clearSelection()
            return BackResult.Handled
        }

        if (state.isQueryState) {
            val currentCid = state.currentCid
            val cache = FileCacheManager.getDate(currentCid)
            // back 处于 query 态，UI 的 currentFcMix 不代表目录历史身份；
            // 用 displayable 只验缓存自洽，:1029 仍显式沿用 cache.fcMix（既有设计选择）。
            if (PagingCache.displayable(cache, currentCid)) {
                val formattedCache =
                    cache!!.copy(fileBeanList = formatFileBeanList(cache.fileBeanList))
                setFiles(formattedCache)
                stateHolder.updateContentState {
                    copy(
                        source = FileListSource.Folder(currentCid, stringProvider),
                        appBarTitle = source.appName,
                        fcMix = cache.fcMix
                    )
                }
                return BackResult.Handled
            } else {
                stateHolder.updateContentState {
                    copy(
                        source = FileListSource.Folder(currentCid, stringProvider),
                        appBarTitle = source.appName
                    )
                }
                return BackResult.NavigateTo(currentCid)
            }
        }

        if (state.currentCid != "0" && state.pathList.size >= 2) {
            val parentCid = state.pathList[state.pathList.size - 2].cid
            return BackResult.NavigateTo(parentCid)
        }

        if (state.isCutState) {
            stateHolder.updateContentState { copy(isCutState = false, cutFileList = emptyList()) }
            return BackResult.Handled
        }

        return BackResult.NotHandled
    }

    fun openSearchDialog() {
        stateHolder.updateDialogState { copy(activeDialog = FileDialogState.Search) }
    }

    fun openFileOrderDialog() {
        stateHolder.updateDialogState { copy(activeDialog = FileDialogState.FileOrder) }
    }

    private fun setFiles(files: FilesBean) {
        activeLocalOrder = null
        currentSnapshot = null // 数据正式就绪，安全释放快照
        val currentOrder = OrderBean.from(files)
        val folderSource = FileListSource.Folder(files.cid, stringProvider)
        val savedHighlight = scrollPositionStore.getHighlight(folderSource.paginationKey)
        stateHolder.updateContentState {
            copy(
                source = folderSource,
                fileBeanList = files.fileBeanList.toList(),
                selectedFileIds = emptySet(),
                highlightedFileId = savedHighlight,
                currentCid = files.cid,
                totalCount = files.count,
                orderBean = currentOrder,
                fcMix = files.fcMix, // 从网络/缓存返回的真实值同步至 UI 状态
                pathList = files.path,
                isLoadingFolder = false, // 成功出口：数据就绪平滑切出骨架屏
                isRefreshing = false,
                isLongClickState = false,
                appBarTitle = folderSource.appName
            )
        }
        // 这里原有一处 markComplete(folderSource.paginationKey) 已被移除：
        // setFiles 的 source 恒为 Folder，而 Folder 的完成态唯一权威是 FilesBean.isComplete
        // （触底 loadMoreFiles 从其缓存快照派生）。写 isLoadedCompleteMap 只会制造一份与缓存脱钩的死数据，
        // LRU 淘汰缓存后该标记残留会导致重进目录触底被误拦截、永久漏页。
    }
}
