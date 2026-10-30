package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileContentUiState
import github.zerorooot.nap511.bean.FileDialogUiState
import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.bean.NavEvent
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinatorImpl
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.LazyScrollState
import github.zerorooot.nap511.viewmodel.module.FileBrowserModule
import github.zerorooot.nap511.viewmodel.module.FileCacheSyncModule
import github.zerorooot.nap511.viewmodel.module.FileIntegrationModule
import github.zerorooot.nap511.viewmodel.module.FileMediaModule
import github.zerorooot.nap511.viewmodel.module.FileMutatorModule
import github.zerorooot.nap511.viewmodel.platform.AndroidAppStringProvider
import github.zerorooot.nap511.viewmodel.platform.AndroidBackgroundScheduler
import github.zerorooot.nap511.viewmodel.platform.AndroidCoilCacheCleaner
import github.zerorooot.nap511.viewmodel.platform.AndroidServiceBridge
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.PagingStateStore
import github.zerorooot.nap511.viewmodel.store.ScrollPositionStore
import github.zerorooot.nap511.viewmodel.store.TorrentCacheStore
import github.zerorooot.nap511.worker.UnzipQueueManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件管理门面 ViewModel (Facade)
 * 采用高内聚、低耦合的模块化设计：
 * - 核心状态与缓存由 4 大纯 JVM Store 及 FileUiStateHolder 独立托管；
 * - 业务逻辑由 5 大领域模块（浏览/变更/媒体/缓存同步/外部集成）协同承载；
 * - 门面负责协程作用域调度、Job 竞态取消以及向后兼容的公开方法透传。
 */
@SuppressLint("MutableCollectionMutableState")
class FileViewModel(
    application: Application,
) : AndroidViewModel(application) {
    internal val context = getApplication<Application>()

    val settingUiStateFlow: StateFlow<SettingUiState> =
        SettingsRepository.getInstance().settingUiStateFlow
    // ==================== 1. 独立状态容器与缓存 (Store 层) ====================

    val stateHolder = FileUiStateHolder(context.getString(R.string.app_name))
    val scrollPositionStore = ScrollPositionStore()
    val pagingStateStore = PagingStateStore()
    val imageCacheStore = ImageCacheStore()
    val torrentCacheStore = TorrentCacheStore()

    // 统一目录分页与缓存协同器
    val pagingCoordinator: DirectoryPagingCoordinator = DirectoryPagingCoordinatorImpl.getInstance()
    internal val fileRepository: FileRepository by lazy { FileRepository.getInstance() }

    // ==================== 2. 状态流与事件流向外暴露 (100% 保持 UI 观察兼容) ====================

    /** 1. 文件列表核心浏览与模式状态流 */
    val contentState: StateFlow<FileContentUiState> = stateHolder.contentState

    /** 2. 弹窗交互独立状态流（与列表展示彻底物理隔离） */
    val dialogState: StateFlow<FileDialogUiState> = stateHolder.dialogState

    /** 二级 Compose SnapshotStateMap：外层按目录 cid 隔离，内层按文件 pickCode 触发列表单条目精准重组 */
    val imageBeanCache get() = imageCacheStore.imageBeanCache

    /** 记录各目录最后点击项的索引位置 (供 FileScreen 观察维持滚动定位) */
    val clickMap: ConcurrentHashMap<String, Int> get() = scrollPositionStore.clicks

    // 便捷只读属性访问器（对齐原有 UI/外部组件调用）
    val fileBeanList: List<FileBean> get() = contentState.value.fileBeanList
    val currentCid: String get() = contentState.value.currentCid
    val pathList: List<PathBean> get() = contentState.value.pathList
    val isLongClickState: Boolean get() = contentState.value.isLongClickState
    val isSearchState: Boolean get() = contentState.value.isSearchState
    val isRefreshing: StateFlow<Boolean> = contentState
        .map { it.isRefreshing }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val totalCount: Int get() = contentState.value.totalCount
    val orderBean: OrderBean get() = contentState.value.orderBean
    val currentOrderBean: OrderBean get() = browserModule.currentOrderBean

    // ==================== 3. 构造平台适配器并装配 5 大 Module ====================

    private val coilCacheCleaner = AndroidCoilCacheCleaner(context)
    private val serviceBridge = AndroidServiceBridge(context)
    private val backgroundScheduler = AndroidBackgroundScheduler(context)
    private val stringProvider = AndroidAppStringProvider(context)

    val browserModule = FileBrowserModule(
        stateHolder = stateHolder,
        pagingStateStore = pagingStateStore,
        imageCacheStore = imageCacheStore,
        fileRepository = fileRepository,
        pagingCoordinator = pagingCoordinator,
        stringProvider = stringProvider,
        coilCacheCleaner = coilCacheCleaner,
        settingUiStateFlow = settingUiStateFlow
    )

    val mutatorModule = FileMutatorModule(
        stateHolder = stateHolder,
        navigator = browserModule,
        fileRepository = fileRepository,
        scrollPositionStore = scrollPositionStore,
        imageCacheStore = imageCacheStore,
        coilCacheCleaner = coilCacheCleaner,
        stringProvider = stringProvider
    )

    val mediaModule = FileMediaModule(
        stateHolder = stateHolder,
        fileRepository = fileRepository,
        imageCacheStore = imageCacheStore,
        torrentCacheStore = torrentCacheStore,
        scrollPositionStore = scrollPositionStore,
        coilCacheCleaner = coilCacheCleaner,
        serviceBridge = serviceBridge,
        navigator = browserModule,
        onEnqueueUnzip = { taskItems ->
            UnzipQueueManager.enqueueAndStartWorker(taskItems, context)
        }
    )

    val syncModule = FileCacheSyncModule(
        stateHolder = stateHolder,
        navigator = browserModule,
        imageCacheStore = imageCacheStore,
        pagingStateStore = pagingStateStore,
        coilCacheCleaner = coilCacheCleaner,
        stringProvider = stringProvider,
        onUpdateFileCache = { cid -> browserModule.updateFileCache(cid) }
    )

    val integrationModule = FileIntegrationModule(
        stateHolder = stateHolder,
        navigator = browserModule,
        fileRepository = fileRepository,
        backgroundScheduler = backgroundScheduler
    )

    // ==================== 4. 一次性事件 (One-shot Events) 与生命周期状态 ====================

    /** 统一页面跳转导航事件流 */
    val navigationEvent: Flow<NavEvent> = merge(
        browserModule.navigationEvent,
        integrationModule.navigationEvent
    )

    /** 视频播放结果回传定位事件流 */
    val videoResultEvent: SharedFlow<Int> = mediaModule.videoResultEvent

    /** 存储空间状态流（供 MainScreen 抽屉独立观察） */
    val remainingSpace: StateFlow<RemainingSpaceBean> = integrationModule.remainingSpace

    /** 电池优化状态流（初始化与手动刷新时发射） */
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> =
        integrationModule.isIgnoringBatteryOptimizations

    fun refreshBatteryOptimizations() {
        integrationModule.refreshBatteryOptimizations()
    }

    // ==================== 5. Job 句柄集中管控与竞态取消 ====================

    private var getFilesJob: Job? = null
    private var searchJob: Job? = null

    init {
        // 集中监听缓存事件（长轮询任务随 ViewModel 作用域取消）
        viewModelScope.launch {
            FileCacheManager.cacheEvents.collect { event ->
                syncModule.handleCacheEvent(event)
            }
        }

        // 监听设置变化（排序、置顶变动）
        viewModelScope.launch {
            settingUiStateFlow.collect { settings ->
                browserModule.onSettingsChanged(settings.keepFoldersOnTop)
            }
        }
    }

    // ==================== 6. 对外兼容性方法透传 (UI 层零修改) ====================

    // 滚动位置与点击
    fun setListLocation(key: String, scrollState: LazyScrollState) =
        scrollPositionStore.setLocation(key, scrollState)

    fun getListLocation(key: String): LocationBean =
        scrollPositionStore.getLocation(key)

    fun setListLocationAndClickCache(index: Int, state: LazyScrollState) {
        scrollPositionStore.setClick(currentCid, index)
        scrollPositionStore.setLocation(currentCid, state)
    }

    // 状态与弹窗控制
    fun setRefreshingStatus(status: Boolean) = stateHolder.setRefreshing(status)
    fun closeDialog() = stateHolder.closeDialog()

    // 浏览与选择操作
    fun loadCacheFile() = viewModelScope.launch { browserModule.loadCacheFile() }
    fun updateFileCache(cid: String) = viewModelScope.launch { browserModule.updateFileCache(cid) }

    fun getFiles(cid: String) {
        getFilesJob?.cancel()
        getFilesJob = viewModelScope.launch {
            browserModule.getFiles(cid)
        }
    }

    fun back() = viewModelScope.launch { browserModule.back() }
    fun refresh(forceCache: Boolean = false) =
        viewModelScope.launch { browserModule.refresh(forceCache) }

    internal fun refresh(cid: String, forceCache: Boolean = false) =
        viewModelScope.launch { browserModule.refresh(cid, forceCache) }

    fun loadMoreFiles() = viewModelScope.launch { browserModule.loadMoreFiles() }
    fun order(isRefresh: Boolean = true, order: OrderBean? = null) =
        viewModelScope.launch { browserModule.order(isRefresh, order) }

    fun updateOrder(order: OrderBean) = viewModelScope.launch { browserModule.updateOrder(order) }

    fun search(searchKey: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { browserModule.search(searchKey) }
    }

    fun filterFile(type: Int, name: String, order: OrderBean = orderBean) =
        viewModelScope.launch { browserModule.filterFile(type, name, order) }

    fun sortByVideoTime() = browserModule.sortByVideoTime()
    fun select(index: Int) = browserModule.select(index)
    fun selectToUp() = browserModule.selectToUp()
    fun selectToDown() = browserModule.selectToDown()
    fun selectReverse() = browserModule.selectReverse()
    fun clearSelection() = browserModule.clearSelection()
    fun startMultiSelect(index: Int) = browserModule.startMultiSelect(index)
    fun recoverFromLongPress() = browserModule.recoverFromLongPress()

    // 文件操作（增删改移）
    fun createFolder(name: String) = viewModelScope.launch { mutatorModule.createFolder(name) }
    fun rename(fileBean: FileBean? = null, name: String) =
        viewModelScope.launch { mutatorModule.rename(fileBean, name) }

    fun delete(fileBean: FileBean) = viewModelScope.launch { mutatorModule.delete(fileBean) }
    fun deleteMultiple() = viewModelScope.launch { mutatorModule.deleteMultiple() }
    fun deleteIndividualFile() = mutatorModule.deleteIndividualFile()
    fun cut(index: Int = -1) = mutatorModule.cut(index)
    fun cancelCut() = mutatorModule.cancelCut()
    fun removeFile() = viewModelScope.launch { mutatorModule.removeFile() }
    fun confirmDiscardVideos(discardList: List<VideoBean>, targetCid: String) =
        viewModelScope.launch { mutatorModule.confirmDiscardVideos(discardList, targetCid) }

    fun cancelDiscardVideos(discardList: List<VideoBean>, targetCid: String) =
        viewModelScope.launch { mutatorModule.cancelDiscardVideos(discardList, targetCid) }

    // 媒体与任务操作
    fun getImage(fileBean: FileBean) = viewModelScope.launch { mediaModule.getImage(fileBean) }
    fun getFileInfo(fileBean: FileBean) =
        viewModelScope.launch { mutatorModule.getFileInfo(fileBean) }

    fun onVideoActivityResult(pickCode: String, cid: String, map: Map<String, VideoBean>) =
        viewModelScope.launch { mediaModule.onVideoActivityResult(pickCode, cid, map) }

    fun startSendAria2Service(index: Int) = mediaModule.startSendAria2Service(index)
    fun getTorrentTask(sha1: String) = viewModelScope.launch { mediaModule.getTorrentTask(sha1) }
    fun addTorrentTask(
        infoHash: String,
        savePath: String,
        wanted: String,
        handle: (Boolean) -> Unit
    ) =
        viewModelScope.launch { mediaModule.addTorrentTask(infoHash, savePath, wanted, handle) }

    fun getZipListFile(
        fileBean: FileBean? = null,
        fileName: String = "",
        paths: String = "文件",
        isCheck: Boolean = true
    ) =
        viewModelScope.launch { mediaModule.getZipListFile(fileBean, fileName, paths, isCheck) }

    fun unzipFile(fileBean: FileBean) = viewModelScope.launch {
        mediaModule.unzipFile(
            fileBean,
            settingUiStateFlow.value.moveFailFile
        )
    }

    fun unzipFile(fileBeansList: List<FileBean>, cid: String, pwd: String = "") =
        viewModelScope.launch { mediaModule.unzipFile(fileBeansList, cid, pwd) }

    fun decryptZip(fileBean: FileBean? = null, secret: String) =
        viewModelScope.launch { mediaModule.decryptZip(fileBean, secret) }

    // 弹窗快捷开启与关闭方法透传
    fun openCreateFolderDialog() = mutatorModule.openCreateFolderDialog()
    fun openRenameFileDialog(fileBean: FileBean) = mutatorModule.openRenameFileDialog(fileBean)
    fun openDiscardVideosDialog(discardList: List<VideoBean>, targetCid: String) =
        mutatorModule.openDiscardVideosDialog(discardList, targetCid)

    fun openSearchDialog() = browserModule.openSearchDialog()
    fun openFileOrderDialog() = browserModule.openFileOrderDialog()
    fun openAria2Dialog() = mediaModule.openAria2Dialog()
    fun openUnzipAllFileDialog() = mediaModule.openUnzipAllFileDialog()
    fun closeUnzipPasswordDialog() = mediaModule.closeUnzipPasswordDialog()

    fun closeCreateFolderDialog() = closeDialog()
    fun closeSearchDialog() = closeDialog()
    fun closeRenameFileDialog() = closeDialog()
    fun closeFileInfoDialog() = closeDialog()
    fun closeFileOrderDialog() = closeDialog()
    fun closeAria2Dialog() = closeDialog()
    fun closeUnzipDialog() = closeDialog()
    fun closeUnzipAllFileDialog() = closeDialog()
    fun closeCreateSelectTorrentFileDialog() = closeDialog()
    fun closeDiscardVideosDialog() = closeDialog()

    // 外部集成
    fun handleDeepLink(intent: Intent?) =
        viewModelScope.launch { integrationModule.handleDeepLink(intent, context) }

    fun handleOfflineTask(forceRecreate: Boolean = false) =
        viewModelScope.launch {
            integrationModule.handleOfflineTask(
                settingUiStateFlow.value.currentOfflineTask,
                forceRecreate
            )
        }

    fun getRemainingSpace() =
        viewModelScope.launch { integrationModule.getRemainingSpace() }
}
