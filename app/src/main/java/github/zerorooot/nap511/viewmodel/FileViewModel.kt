package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.concurrent.futures.await
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileContentUiState
import github.zerorooot.nap511.bean.FileDialogUiState
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinatorImpl
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.CacheEvent
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.LazyScrollState
import github.zerorooot.nap511.util.copy
import github.zerorooot.nap511.util.deleteCoilCache
import github.zerorooot.nap511.util.isIgnoringBatteryOptimizations
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.worker.OfflineTaskWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MutableCollectionMutableState")
class FileViewModel(
    application: Application,
) : AndroidViewModel(application) {
    internal val context = getApplication<Application>()

    val settingUiStateFlow: StateFlow<SettingUiState> =
        SettingsRepository.getInstance().settingUiStateFlow
    val settingUiState: SettingUiState
        get() = settingUiStateFlow.value

    // ==================== 核心状态流 (分域双 Flow 规范) ====================

    /** 1. 文件列表核心浏览与模式状态流 */
    private val _contentState =
        MutableStateFlow(FileContentUiState(appBarTitle = context.getString(R.string.app_name)))
    val contentState: StateFlow<FileContentUiState> = _contentState.asStateFlow()

    /** 2. 弹窗交互独立状态流（与列表展示彻底物理隔离） */
    private val _dialogState = MutableStateFlow(FileDialogUiState())
    val dialogState: StateFlow<FileDialogUiState> = _dialogState.asStateFlow()

    /** 3. 存储空间状态流（供 MainScreen 抽屉独立观察） */
    private val _remainingSpace = MutableStateFlow(RemainingSpaceBean())
    val remainingSpace: StateFlow<RemainingSpaceBean> = _remainingSpace.asStateFlow()

    /** 4. 电池优化状态流（初始化与手动刷新时发射） */
    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> =
        _isIgnoringBatteryOptimizations.asStateFlow()

    // 便捷只读属性访问器（供内部方法与平滑过渡）
    val fileBeanList: List<FileBean>
        get() = _contentState.value.fileBeanList
    val currentCid: String
        get() = _contentState.value.currentCid
    val pathList: List<PathBean>
        get() = _contentState.value.pathList
    val isLongClickState: Boolean
        get() = _contentState.value.isLongClickState
    val isCutState: Boolean
        get() = _contentState.value.isCutState
    val isSearchState: Boolean
        get() = _contentState.value.isSearchState
    val isRefreshing: StateFlow<Boolean>
        get() = MutableStateFlow(_contentState.value.isRefreshing).asStateFlow()
    val orderBean: OrderBean
        get() = _contentState.value.orderBean
    val totalCount: Int
        get() = _contentState.value.totalCount

    // ==================== 内部线程安全与细粒度响应式缓存（解耦，不触发 UI 整体重组） ====================
    val clickMap = ConcurrentHashMap<String, Int>()

    // 二级 Compose SnapshotStateMap：外层按目录 cid 隔离，内层按文件 pickCode 触发列表单条目精准重组
    val imageBeanCache = mutableStateMapOf<String, SnapshotStateMap<String, ImageBean>>()
    internal val imageLoadingSet = hashSetOf<String>()
    val torrentBeanCache = ConcurrentHashMap<String, TorrentFileBean>()
    private val currentLocation = ConcurrentHashMap<String, LocationBean>()

    // 统一目录分页与缓存协同器
    val pagingCoordinator: DirectoryPagingCoordinator = DirectoryPagingCoordinatorImpl.getInstance()

    // 记录各目录及数据源模式触底分页加载是否已完全加载完毕，避免重复触发无效请求与 Toast
    private val isLoadedCompleteMap = ConcurrentHashMap<String, Boolean>()

    // 记录各数据源模式服务端请求的物理偏移量（严格按服务端返回 batch.size 累加，绝不等于去重后的 list.size，消灭游标漂移）
    private val physicalOffsets = ConcurrentHashMap<String, Int>()

    // 记录正在执行触底拉取更多任务的 paginationKey，按模式及目录级提供原子互斥保障，防止高频触底引发并发重入
    private val fetchingMoreKeys = ConcurrentHashMap.newKeySet<String>()

    /** 当前是否正处于触底加载更多状态 */
    val isFetchingMore: Boolean
        get() = fetchingMoreKeys.isNotEmpty()

    internal var saveRequestCache: Boolean = true
    internal var lastKeepFoldersOnTop: Boolean? = null


    /**
     * 获取当前活跃模式的排序规则：
     * - 过滤模式：获取当前过滤专用的排序规则
     * - 目录/其他模式：获取当前目录的排序规则
     * 供排序弹窗回显及外部组件统一消费
     */
    val currentOrderBean: OrderBean
        get() = when (val s = _contentState.value.source) {
            is FileListSource.Filter -> s.order
            else -> orderBean
        }

    /**
     * 115 文件夹/文件混排参数：
     * - fc_mix = 0：文件夹置顶（默认）
     * - fc_mix = 1：文件与文件夹混排
     */
    val currentFcMix: Int
        get() = if (settingUiState.keepFoldersOnTop) 0 else 1

    fun closeDialog() {
        _dialogState.update { FileDialogUiState() }
    }

    fun refreshBatteryOptimizations() {
        _isIgnoringBatteryOptimizations.value = context.isIgnoringBatteryOptimizations()
    }

    init {
        // 仅在进程启动/ViewModel 初始化时执行唯一一次检测
        _isIgnoringBatteryOptimizations.value = context.isIgnoringBatteryOptimizations()

        viewModelScope.launch {
            settingUiStateFlow.collect { settings ->
                saveRequestCache = settings.saveRequestCache
                //不一致，说明用户切换了排序设置，需要手动再order一次，否则如何只要不点击排序，当前顺序永远无法改变
                if (lastKeepFoldersOnTop != null && lastKeepFoldersOnTop != settings.keepFoldersOnTop) {
                    order(false)
                }
                lastKeepFoldersOnTop = settings.keepFoldersOnTop
            }
        }
        // 监听 FileCacheManager 全局缓存变动事件（如终端操作、视频进度更新等），实现 UI 实时同步
        viewModelScope.launch {
            FileCacheManager.cacheEvents.collect { event ->
                handleCacheEvent(event)
            }
        }
    }

    /** 内部状态更新减速器助手 */
    internal fun updateContentState(reducer: FileContentUiState.() -> FileContentUiState) {
        _contentState.update(reducer)
    }

    internal fun updateDialogState(reducer: FileDialogUiState.() -> FileDialogUiState) {
        _dialogState.update(reducer)
    }

    internal val fileRepository: FileRepository by lazy {
        FileRepository.getInstance()
    }

    internal val _videoResultEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val videoResultEvent = _videoResultEvent.asSharedFlow()


    private val _navigationEvent = Channel<NavEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()


    // 统一处理传入的 Deep Link Intent
    fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return

        // 解析 Scheme 和 Host: nap511://detail/check?param=3213
        if (uri.scheme == "nap511" && uri.host == "detail") {
            val command = uri.lastPathSegment // "check" 或 "copy"
            val param = uri.getQueryParameter("param") ?: ""
            XLog.i("FileViewModel handleDeepLink $uri")
            when (command) {
                "addTask" -> {
                    XLog.d(param)
//                    DataStoreUtil.putData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, param)
//                    handleOfflineTask(true)
                }
                //  adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/check?param=3213" github.zerorooot.nap511
                "check" -> {
                    viewModelScope.launch {
                        _navigationEvent.send(NavEvent.NavigateToScreen(Route.VerifyMagnetLinkAccount))
                    }
                }
                //adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/jump?param=0" github.zerorooot.nap511
                "jump" -> {
                    viewModelScope.launch {
                        FileCacheManager.remove(currentCid)
                        getFiles(param)
                    }
                }

                // adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/copy?param=copy_test" github.zerorooot.nap511
                "copy" -> {
                    param.copy(context)
                    XLog.d("handleIntent copy $param")
                    App.instance.toast("复制磁力链接成功!")
                }

                "unzipError" -> {
                    param.copy(context)
                    XLog.d("handleIntent unzipError $intent $param")
                    App.instance.toast("解压失败信息已复制到剪切板!")
                }
            }
        }
    }

    private var isInitialized = false

    fun loadCacheFile() {
        if (isInitialized) return
        isInitialized = true
        setRefreshingStatus(true)
        viewModelScope.launch(Dispatchers.IO) {
            // 优先校验登录状态
            if (UserSessionManager.cookie.isBlank()) {
                setRefreshingStatus(false)
                // 未登录：静默发送跳转登录页事件
                _navigationEvent.send(NavEvent.NavigateToScreen(Route.Login))
                return@launch
            }

            // FileCacheManager.loadAllCache() 已在 SplashScreenManager 加载阶段完成预加载
            getFiles("0")
        }
    }

    /**
     * 预加载指定 cid 的缓存
     */
    fun updateFileCache(cid: String) {
        viewModelScope.launch {
            if (FileCacheManager.containsKey(cid)) {
                return@launch
            }
            runCatching {
                val files = fileRepository.getFiles(
                    cid = cid, order = orderBean.type, asc = orderBean.asc, fcMix = currentFcMix
                )
                files.fileBeanList = formatFileBeanList(files.fileBeanList)
                FileCacheManager[cid] = files
            }.onFailureToastAndLog()
        }
    }

    fun back() {
        if (isLongClickState) {
            clearSelection()
            return
        }

        if (isSearchState) {
            val cache = FileCacheManager.getDate(currentCid)
            if (cache != null) {
                setFiles(cache)
            } else {
                getFiles(currentCid)
            }
            updateContentState {
                copy(
                    source = FileListSource.Folder(currentCid),
                    appBarTitle = context.getString(R.string.app_name)
                )
            }
            return
        }

        if (currentCid != "0" && pathList.size >= 2) {
            getFiles(pathList[pathList.size - 2].cid)
            return
        }

        if (isCutState) {
            updateContentState { copy(isCutState = false, cutFileList = emptyList()) }
            return
        }
    }

    fun setListLocation(key: String, scrollState: LazyScrollState) {
        currentLocation[key] = LocationBean(
            scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset
        )
    }

    fun setListLocationAndClickCache(index: Int, state: LazyScrollState) {
        setListLocation(_contentState.value.source.paginationKey, state)
        clickMap[currentCid] = index
    }

    fun getListLocation(key: String): LocationBean {
        return currentLocation[key] ?: LocationBean(0, 0)
    }

    fun setRefreshingStatus(status: Boolean) {
        updateContentState { copy(isRefreshing = status) }
    }

    /**
     * 获取剩余空间
     */
    fun getRemainingSpace() {
        viewModelScope.launch {
            runCatching {
                val gson = fileRepository.remainingSpace()
                if (gson.get("state").asBoolean) {
                    val spaceInfoJson = gson.getAsJsonObject("data").get("space_info")
                    _remainingSpace.value =
                        Gson().fromJson(spaceInfoJson, RemainingSpaceBean::class.java)
                }
            }.onFailureToastAndLog()
        }
    }

    /**
     * 获取文件列表
     */
    fun getFiles(cid: String) {
        viewModelScope.launch {
            setRefreshingStatus(true)
            // 1. 尝试读取缓存
            if (FileCacheManager.containsKey(cid)) {
                val files = FileCacheManager[cid]!!
                files.fileBeanList = formatFileBeanList(files.fileBeanList)
                setFiles(files)
                setRefreshingStatus(false)
                return@launch
            }

            runCatching {
                val files = fileRepository.getFiles(
                    cid = cid, order = orderBean.type, asc = orderBean.asc, fcMix = currentFcMix
                )
                //请求的cid不是0,但返回的cid是0。证明请求的cid不存在
                if (cid != "0" && files.cid == "0") {
                    App.instance.toast("当前文件夹被删除！")
                }
                files.fileBeanList = formatFileBeanList(files.fileBeanList)

                // 网络请求返回最新文件列表后，同步清空该目录旧的高清图缓存，
                // 避免在等待网络期间提前清空导致屏幕上的旧列表条目闪烁或抢跑发起重复请求
                clearImageCache(cid)
                // 3. 网络请求成功后写入缓存
                setFiles(files)
            }.onFailure {
                val expiredTip = when (it) {
                    is NullPointerException -> "获取文件列表失败，建议更新您的Cookie"
                    //  is HttpException if it.code() == 405 -> "HttpException 405，建议更新您的Cookie"
                    else -> null
                }
                val message = if (expiredTip != null) {
                    FileCacheManager.clearAll()
                    UserSessionManager.clearSession()
                    _navigationEvent.send(NavEvent.NavigateToScreen(Route.Login))
                    expiredTip
                } else {
                    XLog.e("getFiles Exception ", it)
                    "${it.message}，请重试～"
                }
                App.instance.toast(message)
            }
            setRefreshingStatus(false)
        }
    }

    /**
     * 滑动触底自动加载下一页文件（分页加载更多）
     * UI 表现层专注状态机与人机交互门禁，数据分发、去重合并与游标递增完全委托协同器
     */
    fun loadMoreFiles() {
        val state = _contentState.value
        val source = state.source
        val paginationKey = source.paginationKey

        // 多选模式、当前数据源正在加载或正处于下拉刷新中，均不触发
        if (state.isLongClickState || state.isRefreshing || fetchingMoreKeys.contains(paginationKey)) {
            return
        }

        // 检查是否此前已被标记为加载完成
        if (isLoadedCompleteMap.getOrDefault(paginationKey, false)) {
            return
        }

        val currentList = state.fileBeanList
        if (currentList.isEmpty()) {
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
            isLoadedCompleteMap[paginationKey] = true
            if (totalCount > limit) {
                App.instance.toast("加载完毕")
            }
            return
        }

        if (!fetchingMoreKeys.add(paginationKey)) {
            return
        }
        setRefreshingStatus(true)

        // 记录请求发起时的上下文版本，用于并发校验防串位
        val currentRequestSource = source
        // 需要区分过滤时的order, 防止过滤的order对Folder的排序产生干扰
        val currentRequestOrder = currentOrderBean
        val currentRequestKeepFolders = settingUiState.keepFoldersOnTop
        val currentFcMix = this.currentFcMix

        // 获取当前物理游标起点，若未记录则默认以当前列表大小作为初始物理游标
        val currentPhysicalOffset = physicalOffsets.getOrPut(paginationKey) { currentList.size }

        viewModelScope.launch {
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
                physicalOffsets[paginationKey] = result.nextPhysicalOffset

                // 【并发竞争保护】：仅在当前屏幕仍停留在发起请求的模式与上下文时才更新 UI
                if (
                // 1. 校验当前页面展示的数据源（CID/搜索词/筛选类型）是否与请求发起时一致
                    _contentState.value.source == currentRequestSource &&

                    // 2. 校验当前的排序规则（按时间/名称、升降序）是否与请求发起时一致
                    currentOrderBean == currentRequestOrder &&

                    // 3. 如果是搜索页面（不受文件夹置顶影响）或者“文件夹置顶”设置未发生改变
                    (currentRequestSource is FileListSource.Search || settingUiState.keepFoldersOnTop == currentRequestKeepFolders)
                ) {
                    // 只有当上下文环境完全没变时，才真正把追加的数据更新到 UI 状态中
                    updateContentState {
                        copy(
                            fileBeanList = result.combinedList,
                            totalCount = result.latestTotalCount
                        )
                    }
                }

                // 【第二级：完备性状态闭环】
                if (result.isComplete) {
                    isLoadedCompleteMap[paginationKey] = true
                    if (result.combinedList.size > limit) {
                        App.instance.toast("加载完毕")
                    }
                }
            } catch (e: Exception) {
                XLog.e("loadMoreFiles Exception", e)
                App.instance.toast("加载更多失败")
            } finally {
                fetchingMoreKeys.remove(paginationKey)
                if (_contentState.value.source.paginationKey == paginationKey) {
                    setRefreshingStatus(false)
                }
            }
        }
    }

    fun order(isRefresh: Boolean = true, order: OrderBean? = null) {
        val b = order ?: orderBean
        val map = mapOf(
            "user_order" to b.type,
            "user_asc" to b.asc.toString(),
            "file_id" to currentCid,
            "fc_mix" to currentFcMix.toString()
        )

        viewModelScope.launch {
            runCatching {
                val order = fileRepository.order(map)
                XLog.d("order=$order, currentCid=${currentCid}")
                if (order.state) {
                    if (isRefresh) {
                        refresh(currentCid)
                    }
                } else {
                    App.instance.toast("排序失败")
                }
            }.onFailureToastAndLog()
        }
    }

    fun updateOrder(order: OrderBean) {
        when (val s = _contentState.value.source) {
            is FileListSource.Search -> {
                App.instance.toast("暂不支持搜索模式排序")
            }

            is FileListSource.Filter -> {
                this.order(false, order)
                filterFile(type = s.filterType, name = s.name, order = order)
            }

            is FileListSource.Folder -> {
                updateContentState { copy(orderBean = order) }
                this.order()
            }
        }
    }


    fun selectToUp() {
        updateContentState {
            val firstSelectedIndex = fileBeanList.indexOfFirst { it.isSelect }
            if (firstSelectedIndex == -1) return@updateContentState this

            val list = fileBeanList.mapIndexed { idx, item ->
                if (idx <= firstSelectedIndex) item.copy(isSelect = true) else item
            }
            copy(
                fileBeanList = list, appBarTitle = list.count { it.isSelect }.toString()
            )
        }
    }

    fun selectToDown() {
        updateContentState {
            val firstSelectedIndex = fileBeanList.indexOfFirst { it.isSelect }
            if (firstSelectedIndex == -1) return@updateContentState this

            val list = fileBeanList.mapIndexed { idx, item ->
                if (idx >= firstSelectedIndex) item.copy(isSelect = true) else item
            }
            copy(
                fileBeanList = list, appBarTitle = list.count { it.isSelect }.toString()
            )
        }
    }

    fun deleteIndividualFile() {
        FileCacheManager.deleteIndividualFile()
    }

    fun refresh(forceCache: Boolean = false) {
        when (val s = _contentState.value.source) {
            is FileListSource.Folder -> refresh(currentCid, forceCache)

            is FileListSource.Search -> {
                clearSelection()
                isLoadedCompleteMap[s.paginationKey] = false
                physicalOffsets.remove(s.paginationKey)
                search(s.keyword)
            }

            is FileListSource.Filter -> {
                clearSelection()
                isLoadedCompleteMap[s.paginationKey] = false
                physicalOffsets.remove(s.paginationKey)
                // 携带 s.order 刷新，确保用户调整后的过滤排序在下拉刷新时不丢失
                filterFile(type = s.filterType, name = s.name, order = s.order)
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    internal fun refresh(cid: String, forceCache: Boolean = false) {
        clearSelection()
        val paginationKey = "folder:$cid"
        isLoadedCompleteMap[paginationKey] = false
        physicalOffsets.remove(paginationKey)
        updateContentState { copy(source = FileListSource.Folder(cid)) }
        val refreshCurrent = (cid == currentCid)
        XLog.d(
            "refresh refreshCurrent:$refreshCurrent, settingUiState.forceLoadCache:${settingUiState.forceLoadCache}, forceCache:$forceCache," + "order = ${orderBean.type}, asc = ${orderBean.asc}, fcMix = $currentFcMix"
        )
        viewModelScope.launch {
            if (settingUiState.forceLoadCache || forceCache) {
                if (cid == "0") {
                    // 【根目录全量重置】：直接在 IO 协程中清空 Coil 内存与磁盘缓存，并清空全部文件缓存。
                    // 秒级完成，彻底避免无意义的逐层树遍历及 ANR / StackOverflowError。
                    withContext(Dispatchers.IO) {
                        context.imageLoader.memoryCache?.clear()
                        context.imageLoader.diskCache?.clear()
                        FileCacheManager.clearAll()
                    }
                } else {
                    // 【子目录精准定向清理】：在 IO 线程异步清理当前展示条目图标与原图缩略图，并静默递归清理子树缓存
                    cleanFileListCoilCache(fileBeanList)
                    removeFolderCacheRecursively(cid)
                }
            } else {
                FileCacheManager.remove(cid)
            }

            if (refreshCurrent) {
                // 当前目录由 getFiles(currentCid) 在新列表返回时同步调用 clearImageCache(currentCid)
                getFiles(currentCid)
            } else {
                clearImageCache(cid)
                updateFileCache(cid)
            }
        }
    }

    /**
     * 响应 FileCacheManager 的全局缓存变更事件，实现 UI 实时同步
     */
    private fun handleCacheEvent(event: CacheEvent) {
        val dirName = when (event) {
            is CacheEvent.LocalUiUpdated -> resolveDirName(event.cid)
            is CacheEvent.RemoteRefreshRequired -> resolveDirName(event.cid)
            is CacheEvent.FolderDeleted -> resolveDirName(event.folderCid)
            is CacheEvent.AllCleared -> "全部目录"
        }
        XLog.d("handleCacheEvent $event (dir: $dirName) currentCid $currentCid")
        when (event) {
            is CacheEvent.LocalUiUpdated -> {
                // 【本地 UI 刷新逻辑】：
                // 1. 若 event.cid == currentCid：
                //    当前展示目录与变动目录一致（如终端在此目录执行 rm 删除文件、改名或视频播放进度更新）。
                //    FileCacheManager 已经完成就地缓存维护，此时仅需从缓存读取并原地刷新 fileBeanList 即可，绝不发起远程 115 API 请求！
                //    若发生意外缓存未命中（如极端并发或内存淘汰），优雅降级调用 getFiles(currentCid) 兜底拉取。
                //
                // 2. 若 event.cid != currentCid：
                //    为什么会出现这种情况？主要源于“操作发生的目录”与“当前 UI 展示的目录”不一致：
                //    - 场景 A（终端跨目录操作）：用户当前在目录 A，但终端工作目录在目录 B，或在终端输入了跨路径命令（如 rm ../other/file.txt），操作发生在其他目录；
                //    - 场景 B（跨目录移动/剪切）：文件从源目录移动到目标目录时，源目录被扣减条目（FileCacheManager.removeItems），会发出源目录的 LocalUiUpdated 事件，而此时 UI 已经进入了目标目录；
                //    - 场景 C（子文件夹重命名）：重命名子文件夹时，不仅父目录会触发更新，子目录自身缓存的面包屑路径也会更新并发出子目录自身的 LocalUiUpdated 事件；
                //    - 场景 D（后台视频播放进度同步）：视频在后台播放更新进度时，若用户已切出该目录，进度通知的 cid 亦非当前目录。
                //    处理策略：FileCacheManager 已经在后台内存和磁盘中维护好了 event.cid 对应的最新数据；由于用户当前并没有在看 event.cid，
                //    因此当前屏幕展示的 fileBeanList 绝对不需要变动，无需做任何处理。待用户未来切入该目录时即可天然读到已更新好的最新缓存。
                if (event.cid == currentCid && _contentState.value.source is FileListSource.Folder) {
                    val updatedCache = FileCacheManager.getDate(event.cid)
                    if (updatedCache != null) {
                        updateContentState {
                            val unselectedList =
                                updatedCache.fileBeanList.map { it.copy(isSelect = false) }
                            copy(
                                fileBeanList = unselectedList,
                                pathList = updatedCache.path,
                                totalCount = updatedCache.count,
                                isLongClickState = false,
                                appBarTitle = getDefaultAppBarTitle(context)
                            )
                        }
                        // 对齐复合键分页完成状态与物理游标（以 FilesBean.isComplete 为唯一信源 SSOT）
                        val paginationKey = "folder:${event.cid}"
                        physicalOffsets[paginationKey] = updatedCache.fileBeanList.size
                        if (updatedCache.isComplete) {
                            isLoadedCompleteMap[paginationKey] = true
                        }
                    } else {
                        // 若本地内存缓存意外丢失，兜底触发网络拉取
                        getFiles(currentCid)
                    }
                }
            }

            is CacheEvent.RemoteRefreshRequired -> {
                // 【远程 API 刷新逻辑】：
                // 针对新增文件、解压完成、离线下载完成、字幕上传、回收站还原等场景，云端生成了新文件或变动：
                /**不需要删除缓存，因为在 [FileCacheManager.notifyRemoteRefresh] 里已经删除了**/
                if (_contentState.value.source is FileListSource.Folder) {
                    refresh(event.cid, true)
                }
            }
            //todo 当使用 FileViewModel.deleteMultiple 删除当前目录的一堆文件后，FileCacheManager.removeItems 会对文件挨个检测，可能拖慢速度
            is CacheEvent.FolderDeleted -> {
                // 根目录 "0" 永远不可被判定为删除
                if (event.folderCid == "0") return

                imageBeanCache.remove(event.folderCid)

                // 判断当前所在目录或其祖先目录是否被删除
                val isCurrentDeleted = (currentCid == event.folderCid)
                val isAncestorDeleted = pathList.any { it.cid == event.folderCid }
                XLog.d("CacheEvent.FolderDeleted isCurrentDeleted=$isCurrentDeleted, isAncestorDeleted=$isAncestorDeleted")

                if (isCurrentDeleted || isAncestorDeleted) {
                    App.instance.toast("当前所在目录已被删除")
                    // 回退至最近仍存在的祖先目录，兜底为根目录 "0"
                    val survivingAncestorCid =
                        pathList.takeWhile { it.cid != event.folderCid }.lastOrNull()?.cid ?: "0"
                    getFiles(survivingAncestorCid)
                }
            }

            is CacheEvent.AllCleared -> {
                // 保留当前正在刷新目录的条目直至新列表返回，避免刷新中途 UI 抢跑，其余目录直接移除
                val keysToRemove = imageBeanCache.keys.filter { it != currentCid }
                keysToRemove.forEach { imageBeanCache.remove(it) }
                if (currentCid != "0") {
                    getFiles("0")
                }
            }
        }
    }

    private fun resolveDirName(cid: String): String {
        if (cid == currentCid) return pathList.lastOrNull()?.name ?: "当前目录($cid)"
        // 尝试从内存缓存中获取其面包屑末级名称
        return FileCacheManager.getDate(cid)?.path?.lastOrNull()?.name ?: "cid=$cid"
    }

    /**
     * 在 IO 调度器下彻底清理指定文件列表对应的 Coil 内存与磁盘缓存
     * （对应原 FileScreen 在主线程执行的缓存操作，现统一收口至 ViewModel 异步执行）
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun cleanFileListCoilCache(list: List<FileBean>) = withContext(Dispatchers.IO) {
        list.forEach { bean ->
            // 文件列表条目图标/缩略图缓存 Key
            if (bean.fileId.isNotEmpty()) {
                context.imageLoader.deleteCoilCache(bean.fileId)
            }
            // 大图模式/原图缓存 Key
            if (bean.pickCode.isNotEmpty()) {
                context.imageLoader.deleteCoilCache(bean.pickCode)
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun removeFolderCacheRecursively(categoryId: String) = withContext(Dispatchers.IO) {
        if (categoryId == "0") return@withContext
        val visited = HashSet<String>()

        suspend fun cleanCoilCache(cid: String) {
            if (cid.isEmpty() || !visited.add(cid)) return

            val fileBeanList = FileCacheManager[cid]?.fileBeanList ?: emptyList()
            fileBeanList.forEach {
                if (it.isFolder) {
                    val nextCid = it.categoryId.ifEmpty { it.fileId }
                    if (nextCid.isNotEmpty() && nextCid != "0") {
                        cleanCoilCache(nextCid)
                    }
                } else {
                    if (it.fileId.isNotEmpty()) {
                        context.imageLoader.deleteCoilCache(it.fileId)
                    }
                    if (it.photoThumb.isNotEmpty() && it.pickCode.isNotEmpty()) {
                        context.imageLoader.deleteCoilCache(it.pickCode)
                    }
                }
            }
        }

        cleanCoilCache(categoryId)
        // 关键：以静默模式 (emitFolderDeleted = false) 清理缓存，防止误发 FolderDeleted 导致 UI 退出
        FileCacheManager.removeFolderRecursively(categoryId, emitFolderDeleted = false)
    }

    /**
     * 从长按状态恢复
     */
    fun recoverFromLongPress() {
        clearSelection()
    }

    fun search(searchKey: String) {
        val targetSource = FileListSource.Search(currentCid, searchKey)
        val limit = UserSessionManager.requestLimitCount
        setRefreshingStatus(true)
        viewModelScope.launch {
            runCatching {
                val files = fileRepository.search(
                    cid = currentCid, searchValue = searchKey, offset = 0, limit = limit
                )
                val formattedList = formatFileBeanList(files.fileBeanList)
                isLoadedCompleteMap[targetSource.paginationKey] =
                    formattedList.size < limit || (files.count > 0 && formattedList.size >= files.count)
                updateContentState {
                    copy(
                        source = targetSource,
                        isLongClickState = false,
                        fileBeanList = formattedList,
                        totalCount = files.count,
                        appBarTitle = "搜索 - $searchKey"
                    )
                }
            }.onFailureToastAndLog()
            setRefreshingStatus(false)
        }
    }

    fun filterFile(type: Int, name: String, order: OrderBean = orderBean) {
        val fcMix = currentFcMix
        val limit = UserSessionManager.requestLimitCount
        setRefreshingStatus(true)
        viewModelScope.launch {
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

                isLoadedCompleteMap[targetSource.paginationKey] =
                    formattedList.size < limit || (files.count > 0 && formattedList.size >= files.count)
                updateContentState {
                    copy(
                        source = targetSource,
                        isLongClickState = false,
                        fileBeanList = formattedList,
                        totalCount = files.count,
                        appBarTitle = "过滤 - $name"
                    )
                }
            }.onFailureToastAndLog()
            setRefreshingStatus(false)
        }
    }

    fun sortByVideoTime() {
        updateContentState {
            copy(
                fileBeanList = fileBeanList.sortedByDescending { it.playLong },
                videoOrderVersion = videoOrderVersion + 1
            )
        }
    }

    fun selectReverse() {
        updateContentState {
            val updatedList = fileBeanList.map { it.copy(isSelect = !it.isSelect) }
            val count = updatedList.count { it.isSelect }
            copy(
                fileBeanList = updatedList,
                appBarTitle = if (count > 0) count.toString() else "nap511"
            )
        }
    }

    fun select(index: Int) {
        toggleSelect(index)
    }

    fun toggleSelect(index: Int) {
        updateContentState {
            val list = fileBeanList.toMutableList()
            if (index in list.indices) {
                val item = list[index]
                list[index] = item.copy(isSelect = !item.isSelect)
                val count = list.count { it.isSelect }
                copy(
                    fileBeanList = list, appBarTitle = if (count > 0) count.toString() else "nap511"
                )
            } else {
                this
            }
        }
    }

    fun startMultiSelect(index: Int) {
        updateContentState {
            val list = fileBeanList.toMutableList()
            if (index in list.indices) {
                list[index] = list[index].copy(isSelect = true)
            }
            copy(
                isLongClickState = true, fileBeanList = list, appBarTitle = "1"
            )
        }
    }

    fun clearSelection() {
        updateContentState {
            val updatedList = fileBeanList.map { it.copy(isSelect = false) }
            copy(
                fileBeanList = updatedList,
                isLongClickState = false,
                appBarTitle = getDefaultAppBarTitle(context)
            )
        }
    }

    private fun setFiles(files: FilesBean) {
        val currentOrder = OrderBean.from(files)
        val folderSource = FileListSource.Folder(files.cid)
        updateContentState {
            copy(
                source = folderSource,
                fileBeanList = files.fileBeanList.toList(),
                currentCid = files.cid,
                totalCount = files.count,
                orderBean = currentOrder,
                pathList = files.path,
                isRefreshing = false,
                isLongClickState = false,
                appBarTitle = context.getString(R.string.app_name)
            )
        }
        // 若初始加载/缓存读取的条目已达服务端总量，或单页数量少于单页上限，说明该目录全部条目已加载完毕，标记为完成，避免触底无效触发
        val limit = UserSessionManager.requestLimitCount
        isLoadedCompleteMap[folderSource.paginationKey] =
            files.fileBeanList.size < limit || (files.count > 0 && files.fileBeanList.size >= files.count)

        viewModelScope.launch { FileCacheManager[files.cid] = files }
    }


    fun handleOfflineTask(forceRecreate: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context)
            val workQuery = WorkQuery.Builder.fromStates(
                listOf(
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING,
//                    WorkInfo.State.SUCCEEDED,
//                    WorkInfo.State.FAILED,
                    WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED
                )
            ).build()

            // 统一使用异步挂起，避免阻塞
            val workInfos = workManager.getWorkInfos(workQuery).await()

            if (workInfos.isNotEmpty()) {
                if (forceRecreate) {
                    // 如果是强制重新添加，则取消之前的所有任务
                    workInfos.forEach { workManager.cancelWorkById(it.id) }
                } else {
                    // 如果只是检查，发现已有任务则直接返回
                    return@launch
                }
            }

            // 获取并过滤本地缓存任务
            val currentOfflineTask = settingUiState.currentOfflineTask.split("\n")
                .filter { i -> i.isNotBlank() } // 简化过滤逻辑
                .toSet().toMutableList()

            if (currentOfflineTask.isEmpty()) {
                // 如果是主动添加模式且列表为空，弹 Toast 提示
                if (forceRecreate) {
                    App.instance.toast("没有离线任务！")
                }
                return@launch
            }
            App.instance.toast("开始下载！")
            XLog.d(
                "handleOfflineTask forceRecreate=$forceRecreate, workInfos size=${workInfos.size}"
            )

            // 序列化并提交新任务
            val listType = object : TypeToken<List<String?>?>() {}.type
            val list = Gson().toJson(currentOfflineTask, listType)
            val data = Data.Builder().putString("list", list).build()

            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val request = OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java)
                .addTag(ConfigKeyUtil.OFFLINE_TASK_WORKER).setInputData(data)
                .setConstraints(constraints).build()

            workManager.enqueue(request)
        }
    }
}
