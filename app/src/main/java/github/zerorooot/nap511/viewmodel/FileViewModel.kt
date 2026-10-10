package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.FileDialogUiState
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderEnum
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
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
import retrofit2.HttpException
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
    private val _contentState = MutableStateFlow(FileContentUiState(appBarTitle = context.getString(R.string.app_name)))
    val contentState: StateFlow<FileContentUiState> = _contentState.asStateFlow()

    /** 2. 弹窗交互独立状态流（与列表展示彻底物理隔离） */
    private val _dialogState = MutableStateFlow(FileDialogUiState())
    val dialogState: StateFlow<FileDialogUiState> = _dialogState.asStateFlow()

    /** 3. 存储空间状态流（供 MainScreen 抽屉独立观察） */
    private val _remainingSpace = MutableStateFlow(RemainingSpaceBean())
    val remainingSpace: StateFlow<RemainingSpaceBean> = _remainingSpace.asStateFlow()

    /** 4. 电池优化状态流（初始化与手动刷新时发射） */
    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

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


    // ==================== 内部线程安全缓存（解耦，不触发 UI 整体重组） ====================
    val clickMap = ConcurrentHashMap<String, Int>()
    val imageBeanCache = ConcurrentHashMap<String, MutableMap<String, ImageBean>>()
    internal val imageLoadingSet = hashSetOf<String>()
    val torrentBeanCache = ConcurrentHashMap<String, TorrentFileBean>()
    private val currentLocation = ConcurrentHashMap<String, LocationBean>()

    internal var saveRequestCache: Boolean = true

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
                val files =
                    fileRepository.getFiles(cid = cid, order = orderBean.type, asc = orderBean.asc)
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
            }
            updateContentState {
                copy(
                    isSearchState = false,
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

    fun setListLocation(cid: String, scrollState: LazyScrollState) {
        currentLocation[cid] = LocationBean(
            scrollState.firstVisibleItemIndex,
            scrollState.firstVisibleItemScrollOffset
        )
    }

    fun setListLocationAndClickCache(index: Int, state: LazyScrollState) {
        setListLocation(currentCid, state)
        clickMap[currentCid] = index
    }

    fun getListLocation(currentCid: String): LocationBean {
        return currentLocation[currentCid] ?: LocationBean(0, 0)
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
                    _remainingSpace.value = Gson().fromJson(spaceInfoJson, RemainingSpaceBean::class.java)
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
                setFiles(FileCacheManager[cid]!!)
                setRefreshingStatus(false)
                return@launch
            }

            runCatching {
                val files =
                    fileRepository.getFiles(cid = cid, order = orderBean.type, asc = orderBean.asc)
                //请求的cid不是0,但返回的cid是0。证明请求的cid不存在
                if (cid != "0" && files.cid == "0") {
                    App.instance.toast("当前文件夹被删除！")
                }
                files.fileBeanList = formatFileBeanList(files.fileBeanList)
                // 3. 网络请求成功后写入缓存
                setFiles(files)
            }.onFailure {
                val expiredTip = when (it) {
                    is NullPointerException -> "获取文件列表失败，建议更新您的Cookie"
                    is HttpException if it.code() == 405 -> "HttpException 405，建议更新您的Cookie"
                    else -> null
                }
                val message=if (expiredTip != null) {
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

    fun order() {
        val map = mapOf(
            "user_order" to orderBean.type,
            "user_asc" to orderBean.asc.toString(),
            "file_id" to currentCid,
            "fc_mix" to "0"
        )
        viewModelScope.launch {
            runCatching {
                val order = fileRepository.order(map)
                if (order.state) {
                    refresh(currentCid)
                } else {
                    App.instance.toast("排序失败")
                }
            }.onFailureToastAndLog()
        }
    }

    fun updateOrder(order: OrderBean) {
        updateContentState { copy(orderBean = order) }
        this.order()
    }

    fun selectToUp() {
        updateContentState {
            val firstSelectedIndex = fileBeanList.indexOfFirst { it.isSelect }
            if (firstSelectedIndex == -1) return@updateContentState this

            val list = fileBeanList.mapIndexed { idx, item ->
                if (idx <= firstSelectedIndex) item.copy(isSelect = true) else item
            }
            copy(
                fileBeanList = list,
                appBarTitle = list.count { it.isSelect }.toString()
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
                fileBeanList = list,
                appBarTitle = list.count { it.isSelect }.toString()
            )
        }
    }

    fun deleteIndividualFile() {
        FileCacheManager.deleteIndividualFile()
    }

    fun refresh(forceCache: Boolean = false) {
        refresh(currentCid, forceCache)
    }

    @OptIn(ExperimentalCoilApi::class)
    internal fun refresh(cid: String, forceCache: Boolean = false) {
        clearSelection()
        updateContentState { copy(isSearchState = false) }
        val refreshCurrent = (cid == currentCid)
        XLog.d("refresh refreshCurrent:$refreshCurrent, settingUiState.forceLoadCache:${settingUiState.forceLoadCache}, forceCache:$forceCache")
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
                getFiles(currentCid)
            } else {
                updateFileCache(cid)
            }
            imageBeanCache.remove(cid)
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
                if (event.cid == currentCid) {
                    val updatedCache = FileCacheManager.getDate(event.cid)
                    if (updatedCache != null) {
                        updateContentState {
                            val unselectedList = updatedCache.fileBeanList.map { it.copy(isSelect = false) }
                            copy(
                                fileBeanList = unselectedList,
                                pathList = updatedCache.path,
                                isLongClickState = false,
                                appBarTitle = if (isSearchState) "搜索" else context.getString(R.string.app_name)
                            )
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
                if (event.cid == currentCid) {
                    // 若正处于当前展示目录，立即重新请求 115 API 全量更新整个目录列表及 UI
                    getFiles(currentCid)
                } else {
                    // 延迟失效策略：若非当前展示目录，直接清理 FileCacheManager 该 cid 的旧缓存，
                    // 避免无意义的后台网络流量消耗与 API 频控，待用户切入该目录时按需触发最新拉取。
                    viewModelScope.launch {
                        FileCacheManager.remove(event.cid)
                    }
                }
            }

            is CacheEvent.FolderDeleted -> {
                // 根目录 "0" 永远不可被判定为删除
                if (event.folderCid == "0") return

                // 判断当前所在目录或其祖先目录是否被删除
                val isCurrentDeleted = (currentCid == event.folderCid)
                val isAncestorDeleted = pathList.any { it.cid == event.folderCid }
                XLog.d("CacheEvent.FolderDeleted isCurrentDeleted=$isCurrentDeleted, isAncestorDeleted=$isAncestorDeleted")

                if (isCurrentDeleted || isAncestorDeleted) {
                    App.instance.toast("当前所在目录已被删除")
                    // 回退至最近仍存在的祖先目录，兜底为根目录 "0"
                    val survivingAncestorCid = pathList
                        .takeWhile { it.cid != event.folderCid }
                        .lastOrNull()?.cid ?: "0"
                    getFiles(survivingAncestorCid)
                }
            }

            is CacheEvent.AllCleared -> {
                if (currentCid != "0") {
                    getFiles("0")
                }
            }
        }
    }
    private  fun resolveDirName(cid: String): String {
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
        setRefreshingStatus(true)
        viewModelScope.launch {
            runCatching {
                val files = fileRepository.search(currentCid, searchKey)
                val formattedList = formatFileBeanList(files.fileBeanList)
                updateContentState {
                    copy(
                        isSearchState = true,
                        isLongClickState = false,
                        fileBeanList = formattedList,
                        appBarTitle = "搜索 - $searchKey"
                    )
                }
            }.onFailureToastAndLog()
            setRefreshingStatus(false)
        }
    }

    fun filterFile(type: Int, name: String) {
        setRefreshingStatus(true)
        viewModelScope.launch {
            runCatching {
                val files = fileRepository.filterFile(currentCid, type)
                val formattedList = formatFileBeanList(files.fileBeanList)
                updateContentState {
                    copy(
                        isSearchState = true,
                        isLongClickState = false,
                        fileBeanList = formattedList,
                        appBarTitle = "过滤 - $name"
                    )
                }
            }.onFailureToastAndLog()
            setRefreshingStatus(false)
        }
    }


    //    fun selectAll() {
//        val a = arrayListOf<FileBean>()
//        fileBeanList.forEach { i ->
//            i.isSelect = true
//            a.add(i)
//        }
//        fileBeanList.clear()
//        fileBeanList.addAll(a)
//        appBarTitle = fileBeanList.size.toString()
//    }
    fun sortByVideoTime() {
        updateContentState {
            copy(fileBeanList = fileBeanList.sortedByDescending { it.playLong })
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
                    fileBeanList = list,
                    appBarTitle = if (count > 0) count.toString() else "nap511"
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
                isLongClickState = true,
                fileBeanList = list,
                appBarTitle = "1"
            )
        }
    }

    fun clearSelection() {
        updateContentState {
            val updatedList = fileBeanList.map { it.copy(isSelect = false) }
            copy(
                fileBeanList = updatedList,
                isLongClickState = false,
                appBarTitle = if (isSearchState) "搜索" else context.getString(R.string.app_name)
            )
        }
    }

    private fun setFiles(files: FilesBean) {
        updateContentState {
            copy(
                fileBeanList = files.fileBeanList.toList(),// 使用 .toList() 生成不可变浅拷贝，切断引用共享
                currentCid = files.cid,
                pathList = files.path,
                isRefreshing = false,
                isLongClickState = false,
                appBarTitle = if (isSearchState) "搜索" else context.getString(R.string.app_name)
            )
        }
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
            val currentOfflineTask = settingUiState.currentOfflineTask
                .split("\n")
                .filter { i -> i.isNotBlank() } // 简化过滤逻辑
                .toSet()
                .toMutableList()

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
            val data = Data.Builder().putString("list", list)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java)
                .addTag(ConfigKeyUtil.OFFLINE_TASK_WORKER)
                .setInputData(data)
                .setConstraints(constraints)
                .build()

            workManager.enqueue(request)
        }
    }
}
