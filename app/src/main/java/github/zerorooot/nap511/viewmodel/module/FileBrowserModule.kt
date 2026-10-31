package github.zerorooot.nap511.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.viewmodel.formatFileBeanList
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.PagingStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

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
    private val settingUiStateFlow: StateFlow<SettingUiState>
) {
    private val _navigationEvent = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<NavEvent> = _navigationEvent.asSharedFlow()

    private var isInitialized = false

    //应用启动或 ViewModel 初始化时，settingUiStateFlow 会自动发射当前的设置数据
    //如果不为null,会导致应用一打开就向服务端发起一次无意义的 fileRepository.order(...) 请求
    private var lastKeepFoldersOnTop: Boolean? = null
    private val settingUiState: SettingUiState
        get() = settingUiStateFlow.value

    /**
     * 获取当前活跃模式的排序规则：
     * - 过滤模式：获取当前过滤专用的排序规则
     * - 目录/其他模式：获取当前目录的排序规则
     * 供排序弹窗回显及外部组件统一消费
     */
    val currentOrderBean: OrderBean
        get() = when (val s = stateHolder.currentContentState.source) {
            is FileListSource.Filter -> s.order
            else -> stateHolder.currentContentState.orderBean
        }

    /**
     * 115 文件夹/文件混排参数：
     * - fc_mix = 0：文件夹置顶（默认）
     * - fc_mix = 1：文件与文件夹混排
     */
    val currentFcMix: Int
        get() = if (settingUiState.keepFoldersOnTop) 0 else 1

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

            // FileCacheManager.loadAllCache() 已在 SplashScreenManager 加载阶段完成预加载
            getFiles("0")
        }
    }

    /**
     * 预加载指定 cid 的缓存
     */
    suspend fun updateFileCache(cid: String) {
        if (FileCacheManager[cid] != null) {
            return
        }
        runCatching {
            val files = fileRepository.getFiles(
                cid = cid,
                order = stateHolder.currentContentState.orderBean.type,
                asc = stateHolder.currentContentState.orderBean.asc,
                fcMix = currentFcMix
            )
            files.fileBeanList = formatFileBeanList(files.fileBeanList)
            FileCacheManager[cid] = files
        }.onFailureToastAndLog()
    }

    /**
     * 获取文件列表
     */
    suspend fun getFiles(cid: String) {
        stateHolder.setRefreshing(true)
        // 1. 尝试读取缓存（内存与磁盘双级读取，支持冷启动及 LRU 淘汰后从磁盘恢复）
        val cached = FileCacheManager[cid]
        if (cached != null) {
            val files = cached.copy(fileBeanList = formatFileBeanList(cached.fileBeanList))
            setFiles(files)
            stateHolder.setRefreshing(false)
            return
        }

        runCatching {
            val files = fileRepository.getFiles(
                cid = cid,
                order = stateHolder.currentContentState.orderBean.type,
                asc = stateHolder.currentContentState.orderBean.asc,
                fcMix = currentFcMix
            )
            // 请求的 cid 不是 0, 但返回的 cid 是 0。证明请求的 cid 不存在
            if (cid != "0" && files.cid == "0") {
                App.instance.toast("当前文件夹被删除！")
            }
            files.fileBeanList = formatFileBeanList(files.fileBeanList)

            // 网络请求返回最新文件列表后，同步清空该目录旧的高清图缓存，
            // 避免在等待网络期间提前清空导致屏幕上的旧列表条目闪烁或抢跑发起重复请求
            imageCacheStore.clear(cid)
            // 3. 网络请求成功后写入缓存
            FileCacheManager[cid] = files
            setFiles(files)
        }.onFailure {
            val expiredTip = when (it) {
                is NullPointerException -> "获取文件列表失败，建议更新您的Cookie"
                else -> null
            }
            val message = if (expiredTip != null) {
                FileCacheManager.clearAll()
                UserSessionManager.clearSession()
                _navigationEvent.emit(NavEvent.NavigateToScreen(Route.Login))
                expiredTip
            } else {
                XLog.e("getFiles Exception ", it)
                "${it.message}，请重试～"
            }
            App.instance.toast(message)
        }
        stateHolder.setRefreshing(false)
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

        // 检查是否此前已被标记为加载完成
        if (pagingStateStore.isComplete(paginationKey)) {
            pagingStateStore.releaseFetch(paginationKey)
            return
        }

        val currentList = state.fileBeanList
        if (currentList.isEmpty()) {
            pagingStateStore.releaseFetch(paginationKey)
            return
        }

        val cachedData =
            if (source is FileListSource.Folder) FileCacheManager.getDate(source.cid) else null
        val totalCount = if (source is FileListSource.Folder) {
            state.totalCount.takeIf { it > 0 } ?: (cachedData?.count ?: 0)
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
                App.instance.toast("加载完毕")
            }
            return
        }

        stateHolder.setRefreshing(true)

        // 记录请求发起时的上下文版本，用于并发校验防串位
        val currentRequestSource = source
        val currentRequestOrder = currentOrderBean
        val currentFcMix = this.currentFcMix

        // 获取当前物理游标起点，若未记录则默认以当前列表大小作为初始物理游标
        val currentPhysicalOffset =
            pagingStateStore.getOrInitOffset(paginationKey, currentList.size)

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

            // 推进记录服务端物理窗口游标，消灭因去重产生的游标倒退漂移
            pagingStateStore.setOffset(paginationKey, result.nextPhysicalOffset)

            // 【并发竞争保护】：仅在当前屏幕仍停留在发起请求的模式与上下文时才更新 UI
            if (
                stateHolder.currentContentState.source == currentRequestSource &&
                currentOrderBean == currentRequestOrder
            ) {
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
                    App.instance.toast("加载完毕")
                }
            }
        } catch (e: Exception) {
            XLog.e("loadMoreFiles Exception", e)
            App.instance.toast("加载更多失败")
        } finally {
            pagingStateStore.releaseFetch(paginationKey)
            if (stateHolder.currentContentState.source.paginationKey == paginationKey) {
                stateHolder.setRefreshing(false)
            }
        }
    }

    suspend fun order(isRefresh: Boolean = true, order: OrderBean? = null) {
        val currentCid = stateHolder.currentContentState.currentCid
        val b = order ?: stateHolder.currentContentState.orderBean
        val map = mapOf(
            "user_order" to b.type,
            "user_asc" to b.asc.toString(),
            "file_id" to currentCid,
            "fc_mix" to currentFcMix.toString()
        )

        runCatching {
            val orderResult = fileRepository.order(map)
            XLog.d("order=$orderResult, currentCid=$currentCid")
            if (orderResult.state) {
                if (isRefresh) {
                    refresh(currentCid)
                }
            } else {
                App.instance.toast("排序失败")
            }
        }.onFailureToastAndLog()
    }

    suspend fun updateOrder(order: OrderBean) {
        when (val s = stateHolder.currentContentState.source) {
            is FileListSource.Search -> {
                App.instance.toast("暂不支持搜索模式排序")
            }

            is FileListSource.Filter -> {
                this.order(false, order)
                filterFile(type = s.filterType, name = s.name, order = order)
            }

            is FileListSource.Folder -> {
                stateHolder.updateContentState { copy(orderBean = order) }
                this.order()
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
                // 携带 s.order 刷新，确保用户调整后的过滤排序在下拉刷新时不丢失
                filterFile(type = s.filterType, name = s.name, order = s.order)
            }
        }
    }

    suspend fun refresh(cid: String, forceCache: Boolean = false) {
        clearSelection()
        val paginationKey = "folder:$cid"
        pagingStateStore.reset(paginationKey)
        stateHolder.updateContentState { copy(source = FileListSource.Folder(cid)) }
        val refreshCurrent = (cid == stateHolder.currentContentState.currentCid)
        XLog.d(
            "refresh refreshCurrent:$refreshCurrent, settingUiState.forceLoadCache:${settingUiState.forceLoadCache}, forceCache:$forceCache," +
                    "order = ${stateHolder.currentContentState.orderBean.type}, asc = ${stateHolder.currentContentState.orderBean.asc}, fcMix = $currentFcMix"
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
            // 当前目录由 getFiles(currentCid) 在新列表返回时同步调用 clearImageCache(currentCid)
            getFiles(stateHolder.currentContentState.currentCid)
        } else {
            imageCacheStore.clear(cid)
            updateFileCache(cid)
        }
    }

    /**
     * 在 IO 调度器下彻底清理指定文件列表对应的 Coil 内存与磁盘缓存
     */
    suspend fun cleanFileListCoilCache(list: List<FileBean>) = withContext(Dispatchers.IO) {
        list.forEach { bean ->
            if (bean.fileId.isNotEmpty()) {
                coilCacheCleaner.deleteCoilCache(bean.fileId)
            }
            if (bean.pickCode.isNotEmpty()) {
                coilCacheCleaner.deleteCoilCache(bean.pickCode)
            }
        }
    }

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
        val targetSource = FileListSource.Search(currentCid, searchKey)
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
                    appBarTitle = stringProvider.getSearchTitle(searchKey)
                )
            }
        }.onFailureToastAndLog()
        stateHolder.setRefreshing(false)
    }

    suspend fun filterFile(
        type: Int,
        name: String,
        order: OrderBean = stateHolder.currentContentState.orderBean
    ) {
        val currentCid = stateHolder.currentContentState.currentCid
        val fcMix = currentFcMix
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
                FileListSource.Filter(currentCid, type, name, OrderBean.from(files), fcMix)

            if (formattedList.size < limit || (files.count > 0 && formattedList.size >= files.count)) {
                pagingStateStore.markComplete(targetSource.paginationKey)
            }
            stateHolder.updateContentState {
                copy(
                    source = targetSource,
                    isLongClickState = false,
                    fileBeanList = formattedList,
                    totalCount = files.count,
                    appBarTitle = stringProvider.getFilterTitle(name)
                )
            }
        }.onFailureToastAndLog()
        stateHolder.setRefreshing(false)
    }

    fun sortByVideoTime() {
        stateHolder.updateContentState {
            copy(
                fileBeanList = fileBeanList.sortedByDescending { it.playLong },
                videoOrderVersion = videoOrderVersion + 1
            )
        }
    }

    // ==================== 选择模式内聚逻辑 ====================

    fun selectToUp() {
        stateHolder.updateContentState {
            val firstSelectedIndex = fileBeanList.indexOfFirst { it.isSelect }
            if (firstSelectedIndex == -1) return@updateContentState this

            val list = fileBeanList.mapIndexed { idx, item ->
                if (idx <= firstSelectedIndex) item.copy(isSelect = true) else item
            }
            copy(fileBeanList = list, appBarTitle = list.count { it.isSelect }.toString())
        }
    }

    fun selectToDown() {
        stateHolder.updateContentState {
            val firstSelectedIndex = fileBeanList.indexOfFirst { it.isSelect }
            if (firstSelectedIndex == -1) return@updateContentState this

            val list = fileBeanList.mapIndexed { idx, item ->
                if (idx >= firstSelectedIndex) item.copy(isSelect = true) else item
            }
            copy(fileBeanList = list, appBarTitle = list.count { it.isSelect }.toString())
        }
    }

    fun selectReverse() {
        stateHolder.updateContentState {
            val updatedList = fileBeanList.map { it.copy(isSelect = !it.isSelect) }
            val count = updatedList.count { it.isSelect }
            copy(
                fileBeanList = updatedList,
                appBarTitle = if (count > 0) count.toString() else stringProvider.defaultAppName
            )
        }
    }

    fun select(index: Int) {
        toggleSelect(index)
    }

    fun toggleSelect(index: Int) {
        stateHolder.updateContentState {
            val list = fileBeanList.toMutableList()
            if (index in list.indices) {
                val item = list[index]
                list[index] = item.copy(isSelect = !item.isSelect)
                val count = list.count { it.isSelect }
                copy(
                    fileBeanList = list,
                    appBarTitle = if (count > 0) count.toString() else stringProvider.defaultAppName
                )
            } else {
                this
            }
        }
    }

    fun startMultiSelect(index: Int) {
        stateHolder.updateContentState {
            val list = fileBeanList.toMutableList()
            if (index in list.indices) {
                list[index] = list[index].copy(isSelect = true)
            }
            copy(
                isLongClickState = true,
                fileBeanList = list,
                appBarTitle = "1"
            )
        }
    }

    fun clearSelection() {
        stateHolder.updateContentState {
            val updatedList = fileBeanList.map { it.copy(isSelect = false) }
            copy(
                fileBeanList = updatedList,
                isLongClickState = false,
                appBarTitle = stringProvider.defaultAppName
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

        if (state.isSearchState) {
            val currentCid = state.currentCid
            val cache = FileCacheManager.getDate(currentCid)
            if (cache != null) {
                cache.fileBeanList = formatFileBeanList(cache.fileBeanList)
                setFiles(cache)
                stateHolder.updateContentState {
                    copy(
                        source = FileListSource.Folder(currentCid),
                        appBarTitle = stringProvider.defaultAppName
                    )
                }
                return BackResult.Handled
            } else {
                stateHolder.updateContentState {
                    copy(
                        source = FileListSource.Folder(currentCid),
                        appBarTitle = stringProvider.defaultAppName
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
        val currentOrder = OrderBean.from(files)
        val folderSource = FileListSource.Folder(files.cid)
        stateHolder.updateContentState {
            copy(
                source = folderSource,
                fileBeanList = files.fileBeanList.toList(),
                currentCid = files.cid,
                totalCount = files.count,
                orderBean = currentOrder,
                pathList = files.path,
                isRefreshing = false,
                isLongClickState = false,
                appBarTitle = stringProvider.defaultAppName
            )
        }
        val limit = UserSessionManager.requestLimitCount
        if (files.fileBeanList.size < limit || (files.count > 0 && files.fileBeanList.size >= files.count)) {
            pagingStateStore.markComplete(folderSource.paginationKey)
        }
    }
}
