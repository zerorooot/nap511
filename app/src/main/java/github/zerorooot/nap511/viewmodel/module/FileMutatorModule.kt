package github.zerorooot.nap511.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.ScrollPositionStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 文件变更操作领域模块
 * 负责文件/文件夹的创建、重命名、单项及批量删除、剪切与移动、视频播放列表丢弃与回滚，
 * 以及相关确认弹窗的开启控制。
 */
class FileMutatorModule(
    private val stateHolder: FileUiStateHolder,
    private val navigator: FileNavigator,
    private val fileRepository: FileRepository,
    private val scrollPositionStore: ScrollPositionStore,
    private val imageCacheStore: ImageCacheStore,
    private val coilCacheCleaner: CoilCacheCleaner,
    private val stringProvider: AppStringProvider,
) {

    fun cut(index: Int = -1) {
        val currentList = stateHolder.currentContentState.fileBeanList
        val itemsToCut = if (index == -1) {
            currentList.filter { it.isSelect }
        } else {
            currentList.getOrNull(index)?.let { listOf(it) } ?: emptyList()
        }
        stateHolder.updateContentState {
            copy(
                isCutState = true,
                cutFileList = itemsToCut,
                isLongClickState = false,
                fileBeanList = currentList.map { it.copy(isSelect = false) },
                appBarTitle = stringProvider.defaultAppName
            )
        }
    }

    fun cancelCut() {
        stateHolder.updateContentState {
            copy(
                isCutState = false,
                cutFileList = emptyList()
            )
        }
    }

    suspend fun removeFile() {
        val cutList = stateHolder.currentContentState.cutFileList
        if (cutList.isEmpty()) {
            stateHolder.updateContentState { copy(isCutState = false) }
            return
        }

        val targetCid = stateHolder.currentContentState.currentCid
        stateHolder.updateContentState { copy(isCutState = false) }

        val fileCid = cutList[0].let { if (it.isFolder) it.parentId else it.categoryId }
        if (fileCid == targetCid) {
            App.instance.toast("禁止原地移动～")
            return
        }

        stateHolder.setRefreshing(true)
        runCatching {
            val move = fileRepository.removeAllFile(targetCid, cutList)
            if (move.state) {
                // 统一委托 FileCacheManager 移除剪切的文件并清理子文件夹缓存
                FileCacheManager.removeItems(fileCid, cutList.map { it.fileId })
                stateHolder.updateContentState { copy(cutFileList = emptyList()) }
                navigator.refresh(targetCid)
                "移动${cutList.size}个文件成功"
            } else {
                "移动失败~"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
        stateHolder.setRefreshing(false)
    }

    suspend fun createFolder(folderName: String) {
        // 提前保存 cid, 防止进入其他文件夹后刷新当前目录
        val cid = stateHolder.currentContentState.currentCid
        runCatching {
            val createFolder = fileRepository.createFolder(cid, folderName)
            if (createFolder.state) {
                navigator.refresh(cid)
                "创建文件夹 $folderName 成功"
            } else {
                "创建失败，${createFolder.error}"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }

    suspend fun getFileInfo(fileBean: FileBean) {
        stateHolder.setRefreshing(true)
        runCatching {
            val info = if (fileBean.isFolder) {
                fileRepository.getFileInfo(fileBean.categoryId)
            } else {
                fileRepository.getFileInfo(fileBean.fileId)
            }
            XLog.d("file ${fileBean.name} fileInfo $info ; file bean $fileBean")
            stateHolder.updateDialogState {
                copy(
                    fileInfo = info,
                    targetFileBean = fileBean,
                    activeDialog = FileDialogState.FileInfo
                )
            }
        }.onFailureToastAndLog()
        stateHolder.setRefreshing(false)
    }

    suspend fun delete(fileBean: FileBean) {
        // 可能在搜索模式删除，故使用 pid 而非当前 cid
        val cid = fileBean.parentId
        val beforeList = stateHolder.currentContentState.fileBeanList
        val beforeClick = scrollPositionStore.getClick(cid)

        // 提前更新内存不可变列表，优化交互响应速度并取得安全撤销句柄
        stateHolder.updateContentState {
            copy(fileBeanList = fileBeanList.filterNot { it.fileId == fileBean.fileId })
        }
        val rollback = FileCacheManager.removeItem(cid, fileBean.fileId, fileBean.isFolder)
        scrollPositionStore.adjustClick(cid, -1)

        // delete image bean
        imageCacheStore.remove(cid, fileBean.pickCode)

        val fid = fileBean.fileId

        runCatching {
            val deleteResult = fileRepository.delete(cid, fid)
            if (deleteResult.state) {
                // 删除 coil 图片缓存
                if (fileBean.photoThumb.isNotEmpty()) {
                    coilCacheCleaner.deleteCoilCache(fileBean.pickCode)
                }
                "删除 ${fileBean.name} 成功"
            } else {
                stateHolder.updateContentState { copy(fileBeanList = beforeList) }
                rollback?.rollback()
                scrollPositionStore.setClick(cid, beforeClick)
                "删除 ${fileBean.name} 失败~${deleteResult.errorMsg}"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }

    suspend fun rename(
        fileBean: FileBean? = stateHolder.currentDialogState.targetFileBean,
        name: String
    ) {
        val targetBean = fileBean ?: stateHolder.currentDialogState.targetFileBean ?: return
        val cid = stateHolder.currentContentState.currentCid
        val beforeList = stateHolder.currentContentState.fileBeanList
        // 提前在不可变列表替换，提升响应速度并获取回滚闭包
        stateHolder.updateContentState {
            copy(
                fileBeanList = fileBeanList.map {
                    if (it.fileId == targetBean.fileId) it.copy(name = name) else it
                }
            )
        }
        val rollback = FileCacheManager.renameItem(cid, targetBean.fileId, name)

        runCatching {
            val renameResult = fileRepository.rename(RenameBean(targetBean.fileId, name).toRequestBody())
            if (renameResult.state) {
                "重命名成功"
            } else {
                stateHolder.updateContentState { copy(fileBeanList = beforeList) }
                rollback?.rollback()
                "重命名失败"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }

    suspend fun deleteMultiple(targetCid: String, selectedFiles: List<FileBean>) {
        if (selectedFiles.isEmpty()) return
        val beforeList = stateHolder.currentContentState.fileBeanList
        val beforeClick = scrollPositionStore.getClick(targetCid)
        val mapOf = hashMapOf<String, String>()
        mapOf["ignore_warn"] = "1"
        mapOf["pid"] = targetCid

        selectedFiles.forEachIndexed { index: Int, fileBean: FileBean ->
            mapOf["fid[$index]"] = fileBean.fileId
            // update image cache
            imageCacheStore.remove(targetCid, fileBean.pickCode)
            if (fileBean.photoThumb.isNotEmpty()) {
                coilCacheCleaner.deleteCoilCache(fileBean.pickCode)
            }
        }
        // 提前删除并获取批量回滚闭包
        val selectedIds = selectedFiles.map { it.fileId }.toSet()
        val beforeTotalCount = stateHolder.currentContentState.totalCount
        // 瞬时乐观更新 UI 状态并重置选择状态
        stateHolder.updateContentState {
            copy(
                fileBeanList = fileBeanList.filterNot { it.fileId in selectedIds },
                totalCount = (totalCount - selectedFiles.size).coerceAtLeast(0),
                isLongClickState = false,
                appBarTitle = stringProvider.defaultAppName
            )
        }
        scrollPositionStore.setClick(
            targetCid,
            (scrollPositionStore.getClick(targetCid) - selectedFiles.size).coerceAtLeast(0)
        )
        // 先行更新内存与落盘，取得回滚句柄
        val rollback = FileCacheManager.removeItems(targetCid, selectedIds)

        runCatching {
            val deleteMultipleResult = fileRepository.deleteMultiple(mapOf)
            if (deleteMultipleResult.state) {
                "成功删除 ${selectedFiles.size} 个文件"
            } else {
                stateHolder.updateContentState {
                    copy(
                        fileBeanList = beforeList,
                        totalCount = beforeTotalCount
                    )
                }
                rollback?.rollback()
                scrollPositionStore.setClick(targetCid, beforeClick)
                "删除 ${selectedFiles.size} 个文件失败~${deleteMultipleResult.errorMsg}"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }

    suspend fun deleteMultiple() {
        val filter = stateHolder.currentContentState.fileBeanList.filter { it.isSelect }
        val currentCid = stateHolder.currentContentState.currentCid
        deleteMultiple(currentCid, filter)
        // 搜索/过滤模式中，因为被删除文件/文件夹的 cid 不是当前 cid，所以只会删除文件，而不会删除缓存，要手动删除
        if (stateHolder.currentContentState.source !is FileListSource.Folder) {
            val groupBy = filter.groupBy { it.parentId }
            groupBy.forEach { (pid, beans) ->
                /**
                 * 在 [github.zerorooot.nap511.viewmodel.formatFileBeanList] 中已经把文件、文件夹的 cid、fid 统一做了转换，这里直接使用 fid
                 */
                FileCacheManager.removeItems(pid, beans.map { it.fileId })
            }
        }
    }

    fun deleteIndividualFile() {
        FileCacheManager.deleteIndividualFile()
    }

    /**
     * 确认批量删除移除视频：
     * 1. 组装 115 批量删除接口请求参数（原子单次请求，彻底防止频控）；
     * 2. 清理对应文件的 Coil 缩略图磁盘/内存缓存与 imageBeanCache；
     * 3. 乐观更新本地响应式列表与 FileCacheManager 持久化缓存；
     * 4. 驱动 LocalUiUpdated 瞬时刷新当前界面，失败时支持回滚。
     */
    suspend fun confirmDiscardVideos(discardList: List<VideoBean>, targetCid: String) {
        if (discardList.isEmpty()) return

        val cid = targetCid.ifEmpty { stateHolder.currentContentState.currentCid }
        val fids = discardList.map { it.fileId }.filter { it.isNotEmpty() }
        if (fids.isEmpty()) {
            App.instance.toast("移除文件信息不全，删除取消")
            return
        }
        val filter = stateHolder.currentContentState.fileBeanList.filter { it.fileId in fids }
        deleteMultiple(cid, filter)
    }

    /**
     * 取消批量删除移除视频（用户点击“取消”或点击遮罩关闭对话框，保留文件）：
     * 1. 关闭对话框；
     * 2. 提取 discardList 中包含有效 pickCode 且 currentDuration > 0 的播放进度映射；
     *    （若播放时长 <= 0，则严格过滤，保留原有历史进度，避免误将既有进度冲刷清零）；
     * 3. 乐观瞬时刷新：批量调用 FileCacheManager.updateVideoProgress 更新本地缓存与持久化磁盘，并自动触发 LocalUiUpdated 刷新 UI；
     * 4. 异步并发调用 fileRepository.updateVideoHistory 同步 115 云端播放历史。
     */
    suspend fun cancelDiscardVideos(discardList: List<VideoBean>, targetCid: String) {
        if (discardList.isEmpty()) return

        val cid = targetCid.ifEmpty { stateHolder.currentContentState.currentCid }
        // 上层严格保护：仅同步真实播放时长 > 0 的记录，防止误冲刷原有历史进度
        val videoHistoryMap = discardList
            .filter { it.pickCode.isNotEmpty() && it.currentDuration > 0 }
            .associateBy { it.pickCode }

        if (videoHistoryMap.isNotEmpty()) {
            // 1. 瞬时刷新本地缓存与 UI（零延迟响应）
            FileCacheManager.updateVideoProgress(cid, videoHistoryMap)

            // 2. 异步并发同步 115 云端播放历史（仅在确定保留不删除且有时长时提交）
            coroutineScope {
                videoHistoryMap.values.forEach { bean ->
                    launch {
                        fileRepository.updateVideoHistory(
                            pickCode = bean.pickCode,
                            currentDuration = bean.currentDuration,
                            name = bean.name
                        )
                    }
                }
            }
        }
    }

    // ==================== 弹窗快捷开启入口 ====================

    fun openCreateFolderDialog() {
        stateHolder.updateDialogState { copy(activeDialog = FileDialogState.CreateFolder) }
    }

    fun openRenameFileDialog(fileBean: FileBean) {
        stateHolder.updateDialogState {
            copy(activeDialog = FileDialogState.RenameFile, targetFileBean = fileBean)
        }
    }



    fun openDiscardVideosDialog(discardList: List<VideoBean>, targetCid: String) {
        stateHolder.updateDialogState {
            copy(
                activeDialog = FileDialogState.DiscardVideos,
                discardVideoList = discardList,
                targetCid = targetCid
            )
        }
    }
}
