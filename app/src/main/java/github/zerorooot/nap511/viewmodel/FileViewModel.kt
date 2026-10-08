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
import github.zerorooot.nap511.bean.FileDialogState
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@SuppressLint("MutableCollectionMutableState")
class FileViewModel(
    application: Application,
) : AndroidViewModel(application) {
    internal val context = getApplication<Application>()

    val settingUiStateFlow: StateFlow<SettingUiState> =
        SettingsRepository.getInstance().settingUiStateFlow
    val settingUiState: SettingUiState
        get() = settingUiStateFlow.value

    var fileBeanList = mutableStateListOf<FileBean>()
    var unzipBeanList = mutableStateOf(ZipBeanList())
    var remainingSpace by mutableStateOf(RemainingSpaceBean())
    var textBodyByteArray by mutableStateOf<ByteArray?>(null)
    var webBodyByteArray by mutableStateOf<ByteArray?>(null)

    var appBarTitle by mutableStateOf(context.getString(R.string.app_name))

    var currentCid by mutableStateOf("0")

    internal var saveRequestCache by mutableStateOf(true)

    var pathList by mutableStateOf<List<PathBean>>(emptyList())
        private set

    internal var cutFileList = emptyList<FileBean>()


    internal val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()


    var torrentBean by mutableStateOf(TorrentFileBean())
    val torrentBeanCache = hashMapOf<String, TorrentFileBean>()

    var activeDialog by mutableStateOf<FileDialogState>(FileDialogState.None)
        internal set

    fun closeDialog() {
        activeDialog = FileDialogState.None
    }


    /**
     * 电池优化状态（整个应用生命周期内只在初始化时检测一次）
     */
    var isIgnoringBatteryOptimizations by mutableStateOf(false)
        private set

    fun refreshBatteryOptimizations() {
        isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations()
    }

    init {
        // 仅在进程启动/ViewModel 初始化时执行唯一一次检测
        isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations()

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


    /**
     *所选中的文件/文件夹
     */
    var selectIndex by mutableIntStateOf(0)

    //图片浏览相关
    var photoFileBeanList = mutableListOf<FileBean>()
    var photoIndexOf by mutableIntStateOf(-1)

    val imageBeanCache = mutableStateMapOf<String, HashMap<String, ImageBean>>()
    internal val imageLoadingSet = hashSetOf<String>()

    //位置与点击记录相关
    val clickMap = mutableStateMapOf<String, Int>()
    private var currentLocation = hashMapOf<String, LocationBean>()

    //相关状态
    var isLongClickState: Boolean by mutableStateOf(false)
        internal set
    var isCutState: Boolean by mutableStateOf(false)
        internal set
    var isSearchState: Boolean by mutableStateOf(false)
        internal set

    var fileInfo by mutableStateOf(FileInfo())

    var orderBean = OrderBean(OrderEnum.name, 1)
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
        _isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // 优先校验登录状态
            if (UserSessionManager.cookie.isBlank()) {
                _isRefreshing.value = false
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
            recoverFromLongPress()
            unSelect()
            return
        }

        if (isSearchState) {
            fileBeanList.clear()
            setFiles(FileCacheManager.getDate(currentCid)!!)
            appBarTitle = context.getString(R.string.app_name)
            isSearchState = false
            return
        }

        if (currentCid != "0") {
            getFiles(pathList[pathList.size - 2].cid)
            return
        }

        if (isCutState) {
            isCutState = false
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
        return currentLocation[currentCid] ?: run {
            LocationBean(0, 0)
        }
    }


    fun setRefreshingStatus(status: Boolean) {
        _isRefreshing.value = status
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
                    remainingSpace = Gson().fromJson(spaceInfoJson, RemainingSpaceBean::class.java)
                }
            }.onFailureToastAndLog()
        }
    }

    /**
     * 获取文件列表
     */
    fun getFiles(cid: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            // 1. 尝试读取缓存
            if (FileCacheManager.containsKey(cid)) {
                setFiles(FileCacheManager[cid]!!)
                _isRefreshing.value = false
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
                if (expiredTip != null) {
                    FileCacheManager.clearAll()
                    UserSessionManager.clearSession()
                    _navigationEvent.send(NavEvent.NavigateToScreen(Route.Login))
                } else {
                    XLog.e("getFiles Exception ", it)
                    App.instance.toast("${it.message}，请重试～")
                }
            }
            _isRefreshing.value = false

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

    fun selectToUp() {
        try {
            val indexOf = fileBeanList.indexOf(fileBeanList.filter { i -> i.isSelect }[0])
            for (i in 0..indexOf) {
                select(i)
            }
        } catch (_: Exception) {
            App.instance.toast("????????")
        }

    }

    fun selectToDown() {
        try {
            val indexOf = fileBeanList.indexOf(fileBeanList.filter { i -> i.isSelect }[0])
            for (i in indexOf until fileBeanList.size) {
                select(i)
            }
        } catch (_: Exception) {
            App.instance.toast("????????")
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
        isSearchState = false
        recoverFromLongPress()
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
                    cleanFileListCoilCache(fileBeanList.toList())
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
        XLog.d("handleCacheEvent $event")
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
                        recoverFromLongPress()
                        unSelect()
                        fileBeanList.clear()
                        fileBeanList.addAll(updatedCache.fileBeanList)
                        pathList = updatedCache.path
                    } else {
                        // 优雅降级容错：若本地内存缓存意外丢失，兜底触发网络拉取
                        getFiles(currentCid)
                    }
                }
            }

            is CacheEvent.RemoteRefreshRequired -> {
                // 【远程 API 刷新逻辑】：
                // 针对新增文件、解压完成、离线下载完成、字幕上传、回收站还原等场景，云端生成了新文件或变动：
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
        isLongClickState = false
        appBarTitle = if (isSearchState) {
            "搜索"
        } else {
            context.getString(R.string.app_name)
        }
    }

    fun search(searchKey: String) {
        _isRefreshing.value = true
        viewModelScope.launch {
            isSearchState = true
            runCatching {
                val files = fileRepository.search(currentCid, searchKey)
                files.fileBeanList = formatFileBeanList(files.fileBeanList)
                fileBeanList.clear()
                fileBeanList.addAll(files.fileBeanList)
                appBarTitle = "搜索 - $searchKey"
            }.onFailureToastAndLog()
            _isRefreshing.value = false
        }
    }

    fun filterFile(type: Int, name: String) {
        _isRefreshing.value = true
        viewModelScope.launch {
            isSearchState = true
            runCatching {
                val files = fileRepository.filterFile(currentCid, type)
                files.fileBeanList = formatFileBeanList(files.fileBeanList)
                fileBeanList.clear()
                fileBeanList.addAll(files.fileBeanList)
                appBarTitle = "过滤 - $name"
            }.onFailureToastAndLog()
            _isRefreshing.value = false
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
        val sorted = fileBeanList.sortedByDescending { it.playLong }
        fileBeanList.clear()
        fileBeanList.addAll(sorted)
    }

    fun selectReverse() {
        val updatedList = fileBeanList.map { it.copy(isSelect = !it.isSelect) }
        fileBeanList.clear()
        fileBeanList.addAll(updatedList)

        appBarTitle = fileBeanList.filter { i -> i.isSelect }.size.toString()
    }

    fun select(index: Int) {
        val fb = fileBeanList[index]
        fileBeanList[index] = fb.copy(isSelect = !fb.isSelect)
        appBarTitle = fileBeanList.filter { i -> i.isSelect }.size.toString()
    }

    fun unSelect() {
        val updatedList = fileBeanList.map { it.copy(isSelect = false) }
        fileBeanList.clear()
        fileBeanList.addAll(updatedList)
    }


    private fun setFiles(files: FilesBean) {
        fileBeanList.clear()
        fileBeanList.addAll(files.fileBeanList)
        currentCid = files.cid
        pathList = files.path
        viewModelScope.launch { FileCacheManager[currentCid] = files }
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


            // 将 LiveData 转为 Flow 或者直接观察（这里利用 WorkManager 提供的 LiveData 转换为 Flow）
//            workManager.getWorkInfoByIdLiveData(request.id).asFlow() // 将 LiveData 转换为 Flow
//                .collect { workInfo ->
//                    if (workInfo != null) {
//                        if (workInfo.state == WorkInfo.State.SUCCEEDED || workInfo.state == WorkInfo.State.FAILED) {
//                            refresh(defaultOfflineCid)
//                        }
//                    }
//                }
        }
    }
}
