package github.zerorooot.nap511.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.formatFileSize
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.util.resolveCallerTag
import github.zerorooot.nap511.util.toUserFriendlyMessage
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import github.zerorooot.nap511.viewmodel.platform.ServiceBridge
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.ScrollPositionStore
import github.zerorooot.nap511.viewmodel.store.TorrentCacheStore
import github.zerorooot.nap511.worker.UnzipTaskItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 媒体与文件查看、离线任务与解压领域模块
 * 负责大图缓存与异步加载、视频播放历史同步与滚动定位、种子任务解析与添加、在线解压及 Aria2 推送。
 */
class FileMediaModule(
    private val stateHolder: FileUiStateHolder,
    private val fileRepository: FileRepository,
    private val imageCacheStore: ImageCacheStore,
    private val torrentCacheStore: TorrentCacheStore,
    private val scrollPositionStore: ScrollPositionStore,
    private val coilCacheCleaner: CoilCacheCleaner,
    private val serviceBridge: ServiceBridge,
    private val navigator: FileNavigator,
    private val onEnqueueUnzip: (List<UnzipTaskItem>) -> Unit
) {

    private val _videoResultEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val videoResultEvent: SharedFlow<Int> = _videoResultEvent.asSharedFlow()

    // ==================== 高清图片缓存与异步加载 ====================

    /**
     * 加载高清原图与二级缓存查询
     */
    suspend fun getImage(fileBean: FileBean) {
        val pickCode = fileBean.pickCode
        val cid = stateHolder.currentContentState.currentCid

        if (pickCode.isEmpty()) {
            XLog.w("FileMediaModule.getImage pickCode is empty: fileBean=${fileBean.name}")
            return
        }

        if (imageCacheStore.get(cid, pickCode) != null) {
            XLog.d(
                "FileMediaModule.getImage hit imageBeanCache: fileBean=${fileBean.name}, cid=$cid, cache=${
                    imageCacheStore.get(
                        cid,
                        pickCode
                    )
                }"
            )
            return
        }

        // 优先匹配 Coil 缓存（按 MemoryCache -> DiskCache 顺序短路判断，在内存时无需磁盘 I/O）
        val cachedUrl = coilCacheCleaner.getCachedUrl(pickCode)
        if (cachedUrl != null) {
            XLog.d("FileMediaModule.getImage hit Coil cache: fileBean=${fileBean.name}, cachedUrl=$cachedUrl")
            val cachedImageBean = ImageBean(
                url = cachedUrl, fileName = fileBean.name, pickCode = pickCode
            )
            imageCacheStore.put(cid, pickCode, cachedImageBean)
            return
        }

        val loadingKey = "$cid-$pickCode"
        if (!imageCacheStore.tryMarkLoading(loadingKey)) {
            XLog.d("FileMediaModule.getImage already loading: fileBean=${fileBean.name}, loadingKey=$loadingKey")
            return
        }

        try {
            runCatching {
                val imageBean = fileRepository.image(
                    pickCode, System.currentTimeMillis() / 1000
                ).imageBean

                imageCacheStore.put(cid, pickCode, imageBean)
                XLog.d("FileMediaModule.getImage request success: fileBean=${fileBean.name}, imageBean=$imageBean")
            }.onFailureToastAndLog()
        } finally {
            imageCacheStore.removeLoading(loadingKey)
        }
    }

    // ==================== 视频播放回调与历史同步 ====================

    /**
     * 处理 VideoActivity 返回的播放历史与定位结果
     */
    suspend fun onVideoActivityResult(
        pickCode: String,
        cid: String,
        videoHistoryMap: Map<String, VideoBean>
    ) {
        if (videoHistoryMap.isNotEmpty()) {
            // 委托 FileCacheManager 进行播放进度更新与落盘，通过全局事件流自动驱动 UI 刷新
            FileCacheManager.updateVideoProgress(cid, videoHistoryMap)
        }
        if (pickCode.isNotEmpty()) {
            val fileList = FileCacheManager.getDate(cid)?.fileBeanList
            val index = fileList?.indexOfFirst { it.pickCode == pickCode } ?: -1
            if (index >= 0) {
                scrollPositionStore.setClick(cid, index)
                // 可能被终端页面影响。所以只有在当前目录才滚动。
                // 无法更新非当前页的位置，未被布局，不知道非当前页面的 firstVisibleItemIndex 和 firstVisibleItemScrollOffset
                if (cid == stateHolder.currentContentState.currentCid) {
                    _videoResultEvent.tryEmit(index)
                }
            }
        }
    }

    fun startSendAria2Service(index: Int) {
        val fileBean = stateHolder.currentContentState.fileBeanList.getOrNull(index) ?: return
        if (fileBean.isFolder) {
            App.instance.toast("暂时无法下载文件夹")
            return
        }
        serviceBridge.startSendAria2Service(fileBean)
    }

    // ==================== 离线种子任务 ====================

    suspend fun getTorrentTask(sha1: String) {
        stateHolder.updateDialogState { copy(torrentBean = TorrentFileBean()) }
        // 命中缓存的情况
        val cached = torrentCacheStore[sha1]
        if (cached != null) {
            stateHolder.updateDialogState {
                copy(
                    torrentBean = cached,
                    activeDialog = FileDialogState.CreateSelectTorrentFile
                )
            }
            stateHolder.setRefreshing(false)
            return
        }

        // 网络请求的情况
        runCatching {
            val sign = fileRepository.getOfflineSign().sign
            fileRepository.getOfflineTorrentTaskList(sha1, sign, UserSessionManager.uid)
        }.onSuccess { torrentTask ->
            XLog.d("getTorrentTask torrentTask $torrentTask")
            if (!torrentTask.state) {
                App.instance.toast(torrentTask.errorMessage)
                stateHolder.setRefreshing(false)
                return@onSuccess
            }
            torrentTask.fileSizeString = torrentTask.fileSize.formatFileSize() + " "
            torrentTask.torrentFileListWeb.forEach { b ->
                b.sizeString = b.size.formatFileSize() + " "
            }
            torrentCacheStore[sha1] = torrentTask
            torrentTask.torrentFileListWeb.removeIf { f -> f.wanted == -1 }
            torrentTask.fileCount = torrentTask.torrentFileListWeb.size
            stateHolder.updateDialogState {
                copy(
                    torrentBean = torrentTask,
                    activeDialog = FileDialogState.CreateSelectTorrentFile
                )
            }
            stateHolder.setRefreshing(false)
        }.onFailure {
            stateHolder.setRefreshing(false)
        }
    }

    suspend fun addTorrentTask(
        infoHash: String, savePath: String, wanted: String, handle: (Boolean) -> Unit
    ) {
        runCatching {
            val offlineSign = fileRepository.getOfflineSign()
            val sign = offlineSign.sign
            val addTorrentTask = fileRepository.addOfflineTorrentTask(
                infoHash, wanted, savePath, UserSessionManager.uid, sign
            )
            if (addTorrentTask.state) {
                navigator.refresh()
                "任务添加成功，文件已保存至 /云下载/${savePath}"
            } else {
                if (addTorrentTask.errorMsg.contains("请验证账号")) {
                    handle.invoke(true)
                }
                "任务添加失败，${addTorrentTask.errorMsg}"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }

    // ==================== 在线解压相关 ====================

    /**
     * 解压相关：获取压缩包列表（淘汰全局 selectIndex，全面显式接收 FileBean 参数）
     */
    suspend fun getZipListFile(
        fileBean: FileBean? = stateHolder.currentDialogState.targetFileBean,
        fileName: String = "",
        paths: String = "文件",
        isCheck: Boolean = true
    ) {
        val targetBean = fileBean ?: stateHolder.currentDialogState.targetFileBean ?: return
        if (isCheck && paths == "文件") {
            var isInterrupted = false
            runCatching {
                fileRepository.checkZipStatus(targetBean.pickCode)
            }.onSuccess { status ->
                when (status) {
                    is ZipStatus.Encrypted -> {
                        XLog.i("${targetBean.name} 是加密压缩包，拦截流程并弹窗")
                        openUnzipPasswordDialog(targetBean)
                        stateHolder.setRefreshing(false)
                        isInterrupted = true
                    }

                    is ZipStatus.UnsupportedOrError -> {
                        XLog.w("业务不支持或发生错误: ${status.message}")
                        App.instance.toast(status.message)
                        stateHolder.setRefreshing(false)
                        isInterrupted = true
                    }

                    is ZipStatus.Normal -> {
                        XLog.d("${targetBean.name} 为普通压缩包，准备直接打开")
                    }

                    is ZipStatus.Loading -> {
                        val message = "正在进行云解压，请稍等...(${status.progress}%)"
                        XLog.i("${targetBean.name} 要云解压，$message")
                        App.instance.toast(message)
                        stateHolder.setRefreshing(false)
                        isInterrupted = true
                    }
                }
            }.onFailureToastAndLog()

            if (isInterrupted) return
        }

        runCatching {
            fileRepository.getZipListFile(targetBean.pickCode, fileName, paths)
        }.onSuccess { zipList ->
            stateHolder.updateDialogState {
                copy(
                    unzipBeanList = zipList,
                    activeDialog = FileDialogState.Unzip,
                    targetFileBean = targetBean
                )
            }
        }.onFailure { e ->
            stateHolder.setRefreshing(false) // 失败时也确保关闭
            val logTag = resolveCallerTag()
            val userMsg = e.toUserFriendlyMessage()
            XLog.e("[$logTag] $userMsg", e)
            App.instance.toast(userMsg)
        }
    }

    fun unzipFile(fileBean: FileBean, moveFailFile: String) {
        val cid = stateHolder.currentContentState.currentCid
        val arrayListOf = arrayListOf(fileBean)
        unzipFile(arrayListOf, cid, moveFailFile)
    }

    fun unzipFile(
        fileBeansList: List<FileBean>,
        cid: String,
        moveFailFile: String,
        pwd: String = ""
    ) {
        val errorCid = moveFailFile.takeIf { it.isNotEmpty() }?.let { data ->
            stateHolder.currentContentState.fileBeanList.firstOrNull { it.isFolder && it.name == data }?.categoryId
        }

        val taskItems = fileBeansList.map { file ->
            UnzipTaskItem(
                fileBean = file,
                targetCid = cid,
                password = pwd.takeIf { it.isNotEmpty() },
                errorCid = errorCid
            )
        }

        // 统一通过门面回调入队并触发 Worker（KEEP 策略保证单 Worker 循环消费动态队列）
        onEnqueueUnzip(taskItems)
    }

    suspend fun decryptZip(
        fileBean: FileBean? = stateHolder.currentDialogState.targetFileBean,
        secret: String
    ) {
        val targetBean = fileBean ?: stateHolder.currentDialogState.targetFileBean ?: return
        val pickCode = targetBean.pickCode
        closeUnzipPasswordDialog()
        runCatching {
            val decryptZip = fileRepository.decryptZip(pickCode, secret)
            if (!decryptZip) {
                App.instance.toast("密码错误～")
                return
            }

            if (fileRepository.tryToExtract(pickCode)) {
                getZipListFile(targetBean, isCheck = true)
            } else {
                App.instance.toast("服务器解压中～")
            }
        }.onFailureToastAndLog()
    }

    // ==================== 弹窗快捷开启入口 ====================

    fun openAria2Dialog() {
        stateHolder.updateDialogState { copy(activeDialog = FileDialogState.Aria2) }
    }

    fun openUnzipAllFileDialog() {
        stateHolder.updateDialogState { copy(activeDialog = FileDialogState.UnzipAllFile) }
    }

    fun openUnzipPasswordDialog(fileBean: FileBean) {
        stateHolder.updateDialogState {
            copy(activeDialog = FileDialogState.UnzipPassword, targetFileBean = fileBean)
        }
    }

    fun closeUnzipPasswordDialog() {
        stateHolder.updateDialogState {
            copy(activeDialog = FileDialogState.None)
        }
    }
}
