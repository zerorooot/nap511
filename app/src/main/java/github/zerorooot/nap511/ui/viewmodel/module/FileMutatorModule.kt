package github.zerorooot.nap511.ui.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.data.model.FileBean
import github.zerorooot.nap511.data.model.uniqueId
import github.zerorooot.nap511.ui.model.FileDialogState
import github.zerorooot.nap511.ui.model.FileListSource
import github.zerorooot.nap511.data.model.RenameBean
import github.zerorooot.nap511.data.model.VideoBean
import github.zerorooot.nap511.data.repository.FileRepository
import github.zerorooot.nap511.data.local.cache.CacheEvent
import github.zerorooot.nap511.data.local.cache.CacheRollback
import github.zerorooot.nap511.data.local.cache.FileCacheManager
import github.zerorooot.nap511.data.local.cache.groupBySourceParentCid
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.util.toUserFriendlyMessage
import github.zerorooot.nap511.platform.CoilCacheCleaner
import github.zerorooot.nap511.platform.FileNavigator
import github.zerorooot.nap511.platform.ToastProvider
import github.zerorooot.nap511.ui.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.ui.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.ui.viewmodel.store.ScrollPositionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.FormBody

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
    private val toastProvider: ToastProvider,
    private val isSkipProtectedEnabled: () -> Boolean
) {

    fun cut(targetBean: FileBean? = null) {
        val currentState = stateHolder.currentContentState
        val itemsToCut = if (targetBean == null) {
            currentState.fileBeanList.filter { it.uniqueId in currentState.selectedFileIds }
        } else {
            listOf(targetBean)
        }
        stateHolder.updateContentState {
            copy(
                isCutState = true,
                cutFileList = itemsToCut,
                isLongClickState = false,
                selectedFileIds = emptySet(),
                appBarTitle = source.appName
            )
        }
    }

    fun cut(index: Int) {
        val targetBean = if (index >= 0) {
            stateHolder.currentContentState.fileBeanList.getOrNull(index)
        } else null
        cut(targetBean)
    }

    fun cancelCut() {
        stateHolder.updateContentState {
            copy(
                isCutState = false,
                cutFileList = emptyList()
            )
        }
    }

    /**
     * 粘贴：把剪切列表里的条目移动到当前目录。
     *
     * 关键点（对应 bug「跨目录剪切粘贴后源目录缓存不同步」）：
     * 1. 剪切列表可能来自 filter/搜索视图，条目天然跨多个父目录，**不能**用 cutList[0] 反推唯一源目录；
     * 2. 原地判定、缓存订正都基于条目自身的 parentId（[github.zerorooot.nap511.util.formatFileBeanList]
     *    已把普通文件的 parentId 归一为 categoryId、文件夹保留真实 parentId）；
     * 3. 只有网络成功后才订正缓存，失败只提示，不做乐观回滚。
     */
    suspend fun removeFile() {
        val state = stateHolder.currentContentState
        // 同一 fid 可能被重复选中（filter 结果里同名项），按 uniqueId 去重后再判断
        val cutList = state.cutFileList.distinctBy { it.uniqueId }
        if (cutList.isEmpty()) {
            XLog.d("removeFile: cutFileList empty, only reset isCutState")
            stateHolder.updateContentState { copy(isCutState = false) }
            return
        }

        // 约束1：仅 Folder 视图允许粘贴；filter/搜索/星标一律拦截，剪切态与 cutFileList 原样保留。
        // 显式覆盖 Star 是为了「粘贴校验只有这一处」的内聚性——即便将来放开进入守卫也不会漏。
        if (state.source !is FileListSource.Folder) {
            XLog.d("removeFile: blocked by source=${state.source.javaClass.simpleName}")
            toastProvider.showToast("当前模式下无法粘贴，退出后再粘贴")
            return
        }

        val targetCid = state.currentCid
        // 原地判定：parentId 即来源目录（formatFileBeanList 已归一）。
        // 前置 isNotEmpty 仅防 currentCid 为空的理论态（默认 "0"）：来源未知的项不算原地，照常移动。
        val (inPlace, movable) = cutList.partition { it.parentId.isNotEmpty() && it.parentId == targetCid }
        XLog.d(
            "removeFile: target=$targetCid, cut=${cutList.size}, " +
                    "movable=${movable.size}, inPlace=${inPlace.size}"
        )

        // 约束3：全部在当前目录 → 清空剪切态，不发请求、不动缓存
        if (movable.isEmpty()) {
            stateHolder.updateContentState { copy(isCutState = false, cutFileList = emptyList()) }
            toastProvider.showToast("所选文件已在当前目录，已取消剪切")
            return
        }

        // 沿用现状：发起即退出剪切态（失败后 cutFileList 残留，属既有行为，本次不修）
        stateHolder.updateContentState { copy(isCutState = false) }
        stateHolder.setRefreshing(true)

        val result = runCatching { fileRepository.removeAllFile(targetCid, movable) }

        // 【转圈时序说明】成功路径**不在这里**复位 isRefreshing：
        // 复位后紧接 refresh → getFiles 会立刻再置 true，中间必被绘制一帧「转圈消失」，
        // 表现为下拉指示器抽搐一下。因此让 isRefreshing 一直保持 true，
        // 由收尾的 refresh → getFiles 连续持有，最后在它自己的出口复位。
        // 但这也意味着：凡是不走 refresh 的退出路径，都必须在本地显式复位，否则转圈永久残留。

        result.exceptionOrNull()?.let { e ->
            stateHolder.setRefreshing(false)
            // 取消必须冒泡：不订正缓存、不弹 toast
            if (e is CancellationException) {
                XLog.d("removeFile: cancelled, rethrow after resetting refreshing")
                throw e
            }
            XLog.e("removeFile failed: target=$targetCid, items=${movable.size}", e)
            toastProvider.showToast(e.toUserFriendlyMessage())
            return
        }
        // 上面 let 已对异常路径做了 return / throw，能走到这里说明 result 必为 Success，
        // 因此 getOrThrow() 不会再抛，可以直接取出业务体判断 state。
        val move = result.getOrThrow()
        if (!move.state) {
            // 业务失败同样不会再走 refresh，转圈无人接管 → 本分支自己收尾
            stateHolder.setRefreshing(false)
            XLog.w("removeFile: server returned state=false, target=$targetCid, items=${movable.size}")
            toastProvider.showToast("移动失败~")
            return
        }

        // 约束 5/6：仅对参与移动的项，按来源父目录分组订正（本 Bug 根因所在）。
        // 跳过项（inPlace）不参与订正，避免误剔目标目录里的同 fid 条目。
        FileCacheManager.removeItemsBySourceParents(movable, fallbackParentCid = targetCid)
        // 约束 7：双键 Coil 清理（唯一实现见 platform/ServiceBridge.kt）+ 高清图按 pickCode 精准清除
        coilCacheCleaner.cleanFileCaches(movable)
        imageCacheStore.removeByPickCodes(movable.map { it.pickCode })

        stateHolder.updateContentState { copy(cutFileList = emptyList()) }
        val toastMsg = if (inPlace.isEmpty()) "移动${movable.size}个文件成功"
        else "移动${movable.size}个文件成功（已跳过${inPlace.size}个在本目录）"
        XLog.d("removeFile success: $toastMsg")
        toastProvider.showToast(toastMsg)

        // 约束9：仍在目标目录 → 既有 refresh（remove + 回源）负责收尾；
        // 用户已离开 → 只失效该目录缓存，不导航、不 refresh：
        //   · navigateTo 会 cancel getFilesJob 并清空列表把视角拉回，等于抢走用户的返回操作；
        //   · refresh(targetCid) 在漂移态会无条件把 source 改写为 Folder(targetCid)，
        //     与 currentCid 错位后 loadMoreFiles 会拿错分页键。
        // 图片缓存按 pickCode 的精准清理已在上面做过，漂移态不再整块 clear(targetCid)。
        if (stateHolder.currentContentState.currentCid == targetCid) {
            // 走 refresh：isRefreshing 由 refresh → getFiles 连续持有，无需在此复位。
            navigator.refresh(targetCid)
        } else {
            // 漂移态不调 refresh，没有人接管转圈 → 必须显式复位，否则永久转圈。
            XLog.d("removeFile: user drifted away from target=$targetCid, invalidating cache only")
            FileCacheManager.remove(targetCid)
            stateHolder.setRefreshing(false)
        }
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
            toastProvider.showToast(message)
        }.onFailureToastAndLog(toastProvider)
    }

    suspend fun delete(fileBean: FileBean) {
        // 可能在搜索模式删除，故使用 pid 而非当前 cid
        deleteMultiple(fileBean.parentId, arrayListOf(fileBean))
    }

    /**
     * 已经在[github.zerorooot.nap511.util.formatFileBeanList]转化过，无论是文件还是文件夹，都是fileId
     */
    suspend fun starFile(fileBean: FileBean) {
        starMultiple(arrayListOf(fileBean))
    }

    suspend fun starMultiple(filter: List<FileBean>) {
        if (filter.isEmpty()) return
        stateHolder.setRefreshing(true)
        val beforeList = stateHolder.currentContentState.fileBeanList
        val filterIds = filter.map { it.fileId }.toSet()
        val targetStar = !filter[0].isStar
        val actionText = if (targetStar) "标记星标" else "取消星标"
        val currentCid = stateHolder.currentContentState.currentCid

        // 1. 瞬时乐观更新 ViewModel UI State 内存列表并重置选择状态
        stateHolder.updateContentState {
            copy(
                fileBeanList = fileBeanList.map {
                    if (it.fileId in filterIds) it.copy(
                        isStar = targetStar,
                        star = if (targetStar) 1 else 0
                    ) else it
                },
                selectedFileIds = emptySet(),
                isLongClickState = false,
                appBarTitle = source.appName
            )
        }


        // 2. 按来源父目录分组更新 FileCacheManager 内存/磁盘缓存，并收集回滚闭包
        // 复用全仓唯一的来源分组实现（filter/搜索视图下同一批条目的 parentId 可能各不相同）
        val rollbacks = mutableListOf<CacheRollback>()
        filter.groupBySourceParentCid(currentCid).forEach { (pid, beans) ->
            FileCacheManager.starItems(pid, beans.map { it.fileId }, targetStar)?.let {
                rollbacks.add(it)
            }
        }

        runCatching {
            val result = fileRepository.star(filterIds.joinToString(","), targetStar)
            check(result.state) { result.errorMsg.ifEmpty { "${actionText}失败" } }
            result
        }.onSuccess {
            toastProvider.showToast("${actionText}成功")
        }.onFailure { ex ->
            // UI 状态与持久化缓存同步双重回滚
            stateHolder.updateContentState {
                copy(fileBeanList = beforeList, selectedFileIds = emptySet())
            }
            rollbacks.forEach { it.rollback() }
            val errorMsg = ex.message ?: "${actionText}失败"
            toastProvider.showToast(errorMsg)
            XLog.e("starMultiple failed, rolled back", ex)
        }

        stateHolder.setRefreshing(false)
    }

    suspend fun starMultiple() {
        val currentState = stateHolder.currentContentState
        val filter = currentState.fileBeanList.filter { it.uniqueId in currentState.selectedFileIds }
        starMultiple(filter)
    }

    /**
     * 文件/文件夹置顶与取消置顶（单项）
     */
    suspend fun topFile(fileBean: FileBean) {
        topMultiple(arrayListOf(fileBean))
    }

    /**
     * 批量置顶/取消置顶文件或文件夹。
     * 置顶接口调用成功后直接触发全量刷新重新拉取服务端最新排序，失败时重置刷新状态并提示错误。
     */
    suspend fun topMultiple(filter: List<FileBean>) {
        if (filter.isEmpty()) return
        val filterIds = filter.map { it.fileId.ifEmpty { it.categoryId } }.toSet()
        val targetTop = !(filter[0].isPinned || filter[0].isTop == 1)
        val actionText = if (targetTop) "置顶" else "取消置顶"
        XLog.d("topMultiple: targetTop=$targetTop, count=${filter.size}, filterIds=$filterIds")

        stateHolder.setRefreshing(true)
        runCatching {
            val result = fileRepository.top(filterIds.joinToString(","), targetTop)
            check(result.state) { result.errorMsg.ifEmpty { "${actionText}失败" } }
            result
        }.onSuccess {
            XLog.d("topMultiple: ${actionText}成功，调用 navigator.refresh()")
            toastProvider.showToast("${actionText}成功")
            // 无参 refresh 兼容所有数据源模式，由拉取过程接管 loading 动画并在返回时清空多选状态
            navigator.refresh()
        }.onFailure { ex ->
            stateHolder.setRefreshing(false)
            val errorMsg = ex.message ?: "${actionText}失败"
            toastProvider.showToast(errorMsg)
            XLog.e("topMultiple failed", ex)
        }
    }

    suspend fun topMultiple() {
        val currentState = stateHolder.currentContentState
        val filter = currentState.fileBeanList.filter { it.uniqueId in currentState.selectedFileIds }
        topMultiple(filter)
    }

    suspend fun rename(
        fileBean: FileBean? = stateHolder.currentDialogState.targetFileBean,
        name: String
    ) {
        stateHolder.setRefreshing(true)
        val targetBean = fileBean ?: stateHolder.currentDialogState.targetFileBean ?: return
        val cid = stateHolder.currentContentState.currentCid
        val beforeList = stateHolder.currentContentState.fileBeanList
        // 提前在不可变列表替换，提升响应速度并获取回滚闭包
        stateHolder.updateContentState {
            copy(
                fileBeanList = fileBeanList.map {
                    if (it.fileId == targetBean.fileId) it.copy(name = name) else it
                },
                selectedFileIds = emptySet(),
                isLongClickState = false,
                appBarTitle = source.appName
            )
        }
        val rollback = FileCacheManager.renameItem(cid, targetBean.fileId, name)

        runCatching {
            val renameResult =
                fileRepository.rename(RenameBean(targetBean.fileId, name).toRequestBody())
            check(renameResult.state) { "重命名失败" }
            renameResult
        }.onSuccess {
            toastProvider.showToast("重命名成功")
        }.onFailure { ex ->
            stateHolder.updateContentState {
                copy(fileBeanList = beforeList, selectedFileIds = emptySet())
            }
            rollback?.rollback()
            val errorMsg = ex.message ?: "重命名失败"
            toastProvider.showToast(errorMsg)
            XLog.e("rename failed, rolled back", ex)
        }
        stateHolder.setRefreshing(false)
    }

    /**
     * 批量重命名文件或文件夹
     * 1. 安全过滤未变动项与空文件名；
     * 2. 瞬时乐观更新 ViewModel UI State 内存列表并退出多选状态；
     * 3. 同步批量落盘并更新 FileCacheManager 本地缓存；
     * 4. 按 100 项切片分批调用 115 batch_rename 接口；
     * 5. 首批失败或单批失败支持本地精准原子回滚；若多批次部分成功后突发失败，通知远端刷新保证云端一致性。
     */
    suspend fun batchRename(renameMap: Map<String, String>) {
        if (renameMap.isEmpty()) return

        // fid与cid已经在 formatFileBeanList 中统一了，所以统一使用 fileId
        val validMap = renameMap.filter { (fid, newName) ->
            val current = stateHolder.currentContentState.fileBeanList.find { it.fileId == fid }
            newName.isNotBlank() && current != null && current.name != newName
        }
        if (validMap.isEmpty()) return

        stateHolder.setRefreshing(true)
        val cid = stateHolder.currentContentState.currentCid
        val currentList = stateHolder.currentContentState.fileBeanList

        var completedChunks = 0
        runCatching {
            // 按 100 项安全切片分批请求 115 批量接口
            val chunks = validMap.entries.chunked(100)
            for (chunk in chunks) {
                val formBuilder = FormBody.Builder()
                chunk.forEach { (fid, newName) ->
                    formBuilder.add("files_new_name[$fid]", newName)
                }
                val result = fileRepository.rename(formBuilder.build())
                val errorMsg = result.error.ifEmpty {
                    result.errorMsg.ifEmpty { result.message.ifEmpty { "批量重命名失败" } }
                }
                check(result.state) { errorMsg }
                completedChunks++
            }
        }.onSuccess {
            // 1. 全部请求成功后，更新 UI 内存列表并退出多选状态
            stateHolder.updateContentState {
                copy(
                    isLongClickState = false,
                    selectedFileIds = emptySet(),
                    fileBeanList = fileBeanList.map {
                        val newName = validMap[it.fileId]
                        if (newName != null) {
                            it.copy(name = newName)
                        } else {
                            it
                        }
                    },
                    appBarTitle = source.appName
                )
            }

            // 2. 按来源父目录分组更新各父目录本地缓存（支持搜索/标星跨目录及常规目录）
            val affectedItems = currentList.filter { validMap.containsKey(it.fileId) }
            affectedItems.groupBySourceParentCid(cid).forEach { (pid, items) ->
                // 内层 let 的参数必须显式命名，否则内层 it 会遮蔽外层的 item
                // （那会让 mapNotNull 的 it.fileId 解析到 String 上，编译期报 unresolved reference）
                FileCacheManager.renameItems(
                    pid,
                    items.mapNotNull { item ->
                        validMap[item.fileId]?.let { newName -> item.fileId to newName }
                    }.toMap()
                )
            }

            toastProvider.showToast("成功重命名 ${validMap.size} 个文件")
            XLog.d("batchRename: 成功重命名 ${validMap.size} 项")
        }.onFailure { ex ->
            val errorMsg = ex.message ?: "批量重命名失败"
            XLog.e("batchRename failed: $errorMsg, completedChunks: $completedChunks", ex)
            if (completedChunks > 0) {
                // 存在部分批次已提交云端成功，强制触发远端全量刷新避免缓存分裂
                val affectedItems = currentList.filter { validMap.containsKey(it.fileId) }
                // 来源分组统一走 groupBySourceParentCid（兜底 cid）：只取 key 即各来源父目录
                affectedItems.groupBySourceParentCid(cid).keys.forEach { pid ->
                    FileCacheManager.notifyCacheEvent(CacheEvent.RemoteRefreshRequired(pid))
                }
            }
            toastProvider.showToast(errorMsg)
        }
        stateHolder.setRefreshing(false)
    }

    suspend fun deleteMultiple(
        targetCid: String,
        selectedFiles: List<FileBean>,
        skippedCount: Int = 0
    ) {
        if (selectedFiles.isEmpty()) return
        val paginationKey = stateHolder.currentContentState.source.paginationKey
        val beforeList = stateHolder.currentContentState.fileBeanList
        val beforeHighlight = scrollPositionStore.getHighlight(paginationKey)
        // 发起前就捕获数据源模式快照：若在请求飞行中用户退出了搜索/过滤回到 Folder，
        // 回包时再读 source 会翻转判定，导致该做的跨目录订正被漏掉（或反之）。
        val wasFolderMode = stateHolder.currentContentState.source is FileListSource.Folder
        val mapOf = hashMapOf<String, String>()
        mapOf["ignore_warn"] = "1"
        mapOf["pid"] = targetCid

        selectedFiles.forEachIndexed { index: Int, fileBean: FileBean ->
            mapOf["fid[$index]"] = fileBean.fileId
            // update image cache
            imageCacheStore.remove(targetCid, fileBean.pickCode)
        }
        // 提前删除并获取批量回滚闭包
        val selectedIds = selectedFiles.map { it.fileId }.toSet()
        val beforeTotalCount = stateHolder.currentContentState.totalCount
        val willClearHighlight = beforeHighlight != null && beforeHighlight in selectedIds
        // 瞬时乐观更新 UI 状态并重置选择状态（同时清空可能被跳过未删条目的选中状态）
        stateHolder.updateContentState {
            copy(
                fileBeanList = fileBeanList.filterNot { it.fileId in selectedIds },
                totalCount = (totalCount - selectedFiles.size).coerceAtLeast(0),
                selectedFileIds = emptySet(),
                highlightedFileId = if (willClearHighlight) null else highlightedFileId,
                isLongClickState = false,
                appBarTitle = source.appName
            )
        }
        if (willClearHighlight) {
            scrollPositionStore.clearHighlight(paginationKey)
        }
        // 先行更新内存与落盘，取得回滚句柄
        val rollback = FileCacheManager.removeItems(targetCid, selectedIds)

        runCatching {
            val deleteMultipleResult = fileRepository.deleteMultiple(mapOf)
            check(deleteMultipleResult.state) { deleteMultipleResult.errorMsg.ifEmpty { "删除失败" } }
            deleteMultipleResult
        }.onSuccess {
            // 约束 4：仅网络成功后才订正。搜索/过滤/星标视图下条目真实父目录 ≠ targetCid，
            // 需按来源父目录逐目录剔除（currentCid 那一份已由上面的 removeItems 处理）。
            // 用发起前的 wasFolderMode 快照判定，避免请求飞行中切换视图造成误判。
            if (!wasFolderMode) {
                FileCacheManager.removeItemsBySourceParents(selectedFiles)
            }
            // 仅在网络成功后才物理清理 Coil 缩略图
            selectedFiles.forEach { fileBean ->
                if (fileBean.photoThumb.isNotEmpty()) {
                    coilCacheCleaner.deleteCoilCache(fileBean.pickCode)
                }
            }
            val toastMsg = if (skippedCount > 0) {
                "成功删除 ${selectedFiles.size} 个文件（已跳过 $skippedCount 个置顶/星标）"
            } else if (selectedFiles.size == 1) {
                "删除 ${selectedFiles.first().name} 成功"
            } else {
                "成功删除 ${selectedFiles.size} 个文件"
            }
            toastProvider.showToast(toastMsg)
        }.onFailure { ex ->
            // 无论是接口业务返回 false 还是网络超时/断网/500，100% 触发回滚！
            stateHolder.updateContentState {
                copy(
                    fileBeanList = beforeList,
                    totalCount = beforeTotalCount,
                    selectedFileIds = emptySet(),
                    highlightedFileId = beforeHighlight
                )
            }
            rollback?.rollback()
            if (beforeHighlight != null) {
                scrollPositionStore.setHighlight(paginationKey, beforeHighlight)
            }
            val errorMsg = ex.message ?: "网络异常，删除失败"
            toastProvider.showToast(errorMsg)
            XLog.e("deleteMultiple failed, rolled back", ex)
        }
    }

    suspend fun deleteMultiple() {
        val currentState = stateHolder.currentContentState
        val filter = currentState.fileBeanList.filter { it.uniqueId in currentState.selectedFileIds }
        if (filter.isEmpty()) return

        val currentCid = stateHolder.currentContentState.currentCid
        val skipEnabled = isSkipProtectedEnabled()

        // 仅在多选删除时生效：根据设置决定是否跳过置顶或星标项目
        val (skipped, toDelete) = if (skipEnabled) {
            filter.partition { it.isPinned || it.isTop == 1 || it.isStar || it.star == 1 }
        } else {
            emptyList<FileBean>() to filter
        }

        XLog.d("deleteMultiple: skipEnabled=$skipEnabled, totalSelected=${filter.size}, skipped=${skipped.size}, toDelete=${toDelete.size}")

        if (toDelete.isEmpty()) {
            XLog.d("deleteMultiple: all selected items are protected, aborting delete")
            // 全部受保护，不向远端提交删除，仅退出多选状态并提示用户
            stateHolder.updateContentState {
                copy(
                    selectedFileIds = emptySet(),
                    isLongClickState = false,
                    appBarTitle = source.appName
                )
            }
            toastProvider.showToast("所选项目均为置顶/星标，已全部跳过")
            return
        }

        deleteMultiple(currentCid, toDelete, skippedCount = skipped.size)
        // 注意：搜索/过滤模式下「被删条目真实父目录 ≠ currentCid」的多源缓存订正
        // 已移入 deleteMultiple(targetCid, …) 的 onSuccess 成功分支（失败不再误剔源目录缓存），
        // 此处不再重复订正。
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
            toastProvider.showToast("移除文件信息不全，删除取消")
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

    fun openBatchRenameDialog() {
        val hasSelected = stateHolder.currentContentState.selectedFileIds.isNotEmpty()
        if (!hasSelected) {
            toastProvider.showToast("请先选择需要重命名的文件")
            return
        }
        stateHolder.updateDialogState { copy(activeDialog = FileDialogState.BatchRename) }
    }

    fun closeBatchRenameDialog() {
        stateHolder.updateDialogState { copy(activeDialog = FileDialogState.None) }
    }

    /**
     * 多选状态下的重命名智能分流：
     * 1. selectedCount == 1 -> 调起单文件重命名弹窗 (RenameFileDialog)；
     * 2. selectedCount > 1 -> 调起批量重命名弹窗 (BatchRenameDialog)；
     * 3. selectedCount == 0 -> 提示请先选择。
     */
    fun onMultiSelectRename() {
        val currentState = stateHolder.currentContentState
        val selectedItems = currentState.fileBeanList.filter { it.uniqueId in currentState.selectedFileIds }
        when (selectedItems.size) {
            0 -> toastProvider.showToast("请先选择需要重命名的文件")
            1 -> {
                XLog.d("onMultiSelectRename: 单文件重命名，fid=${selectedItems.first().fileId}")
                openRenameFileDialog(selectedItems.first())
            }

            else -> {
                XLog.d("onMultiSelectRename: 批量重命名，共 ${selectedItems.size} 项")
                openBatchRenameDialog()
            }
        }
    }
}
