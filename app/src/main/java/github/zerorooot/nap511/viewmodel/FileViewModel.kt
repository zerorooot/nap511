package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
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
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.DialogEvent
import github.zerorooot.nap511.util.DialogEventBus
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.worker.OfflineTaskWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File


@SuppressLint("MutableCollectionMutableState")
class FileViewModel(internal val cookie: String, internal val context: Context) : ViewModel() {
    var fileBeanList = mutableStateListOf<FileBean>()
    var unzipBeanList = mutableStateOf(ZipBeanList())
    var remainingSpace by mutableStateOf(RemainingSpaceBean())
    var textBodyByteArray: ByteArray? = null

    var appBarTitle by mutableStateOf(context.getString(R.string.app_name))

    private val _currentPath = MutableStateFlow("")
    var currentPath = _currentPath.asStateFlow()

    var currentCid: String by mutableStateOf("0")

    //当前cid下的文件数量
    private var count: Int by mutableIntStateOf(0)

    internal val saveRequestCache = DataStoreUtil.getData(ConfigKeyUtil.SAVE_REQUEST_CACHE, true)

    internal val fileListCache by lazy {
        FileCacheManager<FilesBean>(
            cacheDir = File(context.cacheDir, "file_list_cache"),
            classType = FilesBean::class.java,
            saveRequestCache
        )
    }
    private var pathList = emptyList<PathBean>()

    internal var cutFileList = emptyList<FileBean>()


    internal val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()

    //页面导航
//    var selectedItem by mutableStateOf(ConfigKeyUtil.MY_FILE)

    //页面手势
    var gesturesEnabled by mutableStateOf(true)


    /**
     * 打开对话框相关（状态下沉到 ViewModel 本地）
     */
    internal val dialogEventBus = DialogEventBus.getInstance()

    var isOpenCreateFolderDialog by mutableStateOf(false)
        internal set
    var isOpenSearchDialog by mutableStateOf(false)
        internal set
    var isOpenRenameFileDialog by mutableStateOf(false)
        internal set
    var isOpenFileInfoDialog by mutableStateOf(false)
        internal set
    var isOpenFileOrderDialog by mutableStateOf(false)
        internal set
    var isOpenAria2Dialog by mutableStateOf(false)
        internal set
    var isOpenUnzipDialog by mutableStateOf(false)
        internal set
    var isOpenUnzipPasswordDialog by mutableStateOf(false)
        internal set
    var isOpenTextBodyDialog by mutableStateOf(false)
        internal set
    var isOpenUnzipAllFileDialog by mutableStateOf(false)
        internal set
    var isOpenCreateSelectTorrentFileDialog by mutableStateOf(false)
        internal set

    // 定义全局异常处理器处理，防止
    val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        when (throwable) {
            is retrofit2.HttpException -> {
                App.instance.toast("HTTP请求错误: ${throwable.code()}，请重试")
                setRefreshingStatus(false)
            }

            else -> {
                App.instance.toast("错误: ${throwable.message}")
                setRefreshingStatus(false)
            }
        }
    }

    init {
        viewModelScope.launch {
            dialogEventBus.events.collect { event ->
                when (event) {
                    is DialogEvent.OpenCreateFolder -> isOpenCreateFolderDialog = true
                    is DialogEvent.OpenSearch -> isOpenSearchDialog = true
                    is DialogEvent.OpenRenameFile -> isOpenRenameFileDialog = true
                    is DialogEvent.OpenFileInfo -> isOpenFileInfoDialog = true
                    is DialogEvent.OpenFileOrder -> isOpenFileOrderDialog = true
                    is DialogEvent.OpenAria2Dialog -> isOpenAria2Dialog = true
                    is DialogEvent.OpenUnzipDialog -> isOpenUnzipDialog = true
                    is DialogEvent.OpenUnzipPasswordDialog -> isOpenUnzipPasswordDialog = true
                    is DialogEvent.OpenTextBodyDialog -> isOpenTextBodyDialog = true
                    is DialogEvent.OpenUnzipAllFileDialog -> isOpenUnzipAllFileDialog = true
                    is DialogEvent.OpenCreateSelectTorrentFileDialog -> isOpenCreateSelectTorrentFileDialog =
                        true
                    // 不属于 FileViewModel 的事件，忽略
                    is DialogEvent.OpenOfflineDialog,
                    is DialogEvent.OpenRecyclePasswordDialog -> { /* ignore */
                    }
                }
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

    val imageBeanCache = mutableStateMapOf<String, HashMap<Int, ImageBean>>()

    //位置与点击记录相关
    val clickMap = mutableStateMapOf<String, Int>()
    private var currentLocation = hashMapOf<String, LocationBean>()

    //相关状态
    var isLongClickState: Boolean by mutableStateOf(false)
    var isCutState: Boolean by mutableStateOf(false)
    var isSearchState: Boolean by mutableStateOf(false)

    var fileInfo by mutableStateOf(FileInfo())

    //小文件缓存
    internal var textFileCache = hashMapOf<FileBean, ByteArray?>()
    var orderBean = OrderBean(OrderEnum.name, 1)
    internal val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(cookie)
    }

    internal val _launchVideoEvent = MutableSharedFlow<VideoInfoBean>()
    val launchVideoEvent = _launchVideoEvent.asSharedFlow()

    private val _navigationEvent = Channel<NavEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()


    // 统一处理传入的 Deep Link Intent
    fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return

        // 解析 Scheme 和 Host: nap511://detail/check?param=3213
        if (uri.scheme == "nap511" && uri.host == "detail") {
            val command = uri.lastPathSegment // "check" 或 "copy"
            val param = uri.getQueryParameter("param") ?: ""
            XLog.d("FileViewModel handleDeepLink $uri")
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
                    getFiles(param)
                }

                // adb shell am start -W -a android.intent.action.VIEW -d "nap511://detail/copy?param=copy_test" github.zerorooot.nap511
                "copy" -> {
                    val clipboard =
                        ContextCompat.getSystemService(context, ClipboardManager::class.java)
                    val clip = ClipData.newPlainText("label", param)
                    clipboard?.setPrimaryClip(clip)

                    XLog.d("handleIntent copy $param")
                    App.instance.toast("复制磁力链接成功!")
                }

                "unzipError" -> {
                    val clipboard =
                        ContextCompat.getSystemService(context, ClipboardManager::class.java)
                    val clip = ClipData.newPlainText("unzipError", param)
                    clipboard?.setPrimaryClip(clip)
                    XLog.d("handleIntent unzipError $intent $param")
                    App.instance.toast("解压失败信息已复制到剪切板!")
                }
            }
        }
    }

    fun loadCacheFile() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!saveRequestCache) {
                // 不保存磁盘缓存时，仅清理硬盘旧文件，保留内存缓存
                fileListCache.clearDiskOnly()
            } else {
                // 开启磁盘保存时，在后台检查清理过期的硬盘缓存
                fileListCache.cleanExpiredDiskCache()
            }
            getFiles(currentCid)
        }
    }

    /**
     * 预加载指定 cid 的缓存
     */
    fun updateFileCache(cid: String) {
        viewModelScope.launch {
            if (fileListCache.containsKey(cid)) {
                return@launch
            }
            val files =
                fileRepository.getFiles(cid = cid, order = orderBean.type, asc = orderBean.asc)
            setFileBeanProperty(files.fileBeanList)
            fileListCache[cid] = files
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
            setFiles(fileListCache.getDate(currentCid)!!)
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

    fun setListLocation(path: String, listState: LazyListState) {
        val locationBean = LocationBean(
            listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset
        )
        currentLocation[path] = locationBean
    }

    fun setListLocationAndClickCache(index: Int, listState: LazyListState) {
        val currentPath = _currentPath.value
        //记录上级目录当前的位置
        setListLocation(currentPath, listState)
        //标记此点击文件，方便确认到底点了那个
        clickMap[currentPath] = index
    }

    fun getListLocation(path: String): LocationBean {
        return currentLocation[path] ?: run {
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
            val gson = fileRepository.remainingSpace()
            if (!gson.get("state").asBoolean) {
                //{"state":false,"error":"登录超时，请重新登录。","errNo":990001,"request":"/files/index_info?count_space_nums=1"}
                return@launch
            }
            val spaceInfoJson = gson.getAsJsonObject("data").get("space_info")
            remainingSpace = Gson().fromJson(spaceInfoJson, RemainingSpaceBean::class.java)
        }
    }

    /**
     * 获取文件列表
     */
    fun getFiles(cid: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            // 1. 尝试读取缓存
            // saveRequestCache 只传给 readDisk 参数。如果内存中有，不用读磁盘，依然能命中内存！
            if (fileListCache.containsKey(cid)) {
                setFiles(fileListCache[cid]!!)
                _isRefreshing.value = false
                return@launch
            }

            // 2. 缓存未命中，发起网络请求
            try {
                val files =
                    fileRepository.getFiles(cid = cid, order = orderBean.type, asc = orderBean.asc)
                setFileBeanProperty(files.fileBeanList)
                // 3. 网络请求成功后写入缓存
                setFiles(files)
            } catch (e: NullPointerException) {
                fileListCache.clearAll()
                App.instance.toast("获取文件列表失败，建议更新您的Cookie")
            } catch (e: Exception) {
                e.printStackTrace()
                App.instance.toast("${e.message}，请重试～")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun order() {
        /**
         *
        user_order:file_size
        file_id:2573609193685653011
        user_asc:1
        fc_mix:0
         */
        val map = mapOf(
            "user_order" to orderBean.type,
            "user_asc" to orderBean.asc.toString(),
            "file_id" to currentCid,
            "fc_mix" to "0"
        )
        viewModelScope.launch {
            val order = fileRepository.order(map)
            if (order.state) {
                refresh(currentCid)
            } else {
                App.instance.toast("排序失败")
            }

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


    fun refresh() {
        refresh(currentCid)
    }

    internal fun refresh(cid: String) {
        isSearchState = false
        recoverFromLongPress()

        viewModelScope.launch {
            fileListCache.remove(cid)
            if (cid == currentCid) {
                getFiles(currentCid)
            } else {
                updateFileCache(cid)
            }
        }

        // 图片缓存清理
        imageBeanCache.remove(cid)
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
            val files = fileRepository.search(currentCid, searchKey)
            setFileBeanProperty(files.fileBeanList)
            fileBeanList.clear()
            fileBeanList.addAll(files.fileBeanList)
            appBarTitle = "搜索 - $searchKey"
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
        count = files.count
        pathList = files.path

        _currentPath.value = pathList.joinToString("/") { it.name }

        if (!fileListCache.containsKey(currentCid)) {
            viewModelScope.launch { fileListCache[currentCid] = files }
        }
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
            val currentOfflineTask =
                DataStoreUtil.getDataSuspend(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
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
                "handleOfflineTask forceRecreate=$forceRecreate, workInfos size=${workInfos.size}, task size=${currentOfflineTask.size} " +
                        "currentOfflineTask:\n $currentOfflineTask"
            )

            val defaultOfflineCid =
                DataStoreUtil.getDataSuspend(ConfigKeyUtil.DEFAULT_OFFLINE_CID, "")
            // 序列化并提交新任务
            val listType = object : TypeToken<List<String?>?>() {}.type
            val list = Gson().toJson(currentOfflineTask, listType)
            val data = Data.Builder()
                .putString("defaultOfflineCid", defaultOfflineCid)
                .putString("list", list)
                .build()

            val request = OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java)
                .addTag(ConfigKeyUtil.OFFLINE_TASK_WORKER)
                .setInputData(data)
                .build()

            workManager.enqueue(request)


            // 将 LiveData 转为 Flow 或者直接观察（这里利用 WorkManager 提供的 LiveData 转换为 Flow）
            workManager.getWorkInfoByIdLiveData(request.id).asFlow() // 将 LiveData 转换为 Flow
                .collect { workInfo ->
                    if (workInfo != null) {
                        if (workInfo.state == WorkInfo.State.SUCCEEDED || workInfo.state == WorkInfo.State.FAILED) {
                            refresh(defaultOfflineCid)
                        }
                    }
                }
        }
    }
}
