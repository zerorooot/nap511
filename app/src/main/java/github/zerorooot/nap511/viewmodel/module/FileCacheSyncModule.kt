package github.zerorooot.nap511.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.CacheEvent
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.PagingStateStore

/**
 * 全局缓存事件同步领域模块
 * 负责监听 [FileCacheManager] 全局广播事件（本地缓存变更、远端变动触发刷新、文件夹级联删除、全量清空等），
 * 实现多页面、终端后台操作与当前文件界面的实时高一致性同步。
 */
class FileCacheSyncModule(
    private val stateHolder: FileUiStateHolder,
    private val navigator: FileNavigator,
    private val imageCacheStore: ImageCacheStore,
    private val pagingStateStore: PagingStateStore,
    private val coilCacheCleaner: CoilCacheCleaner,
    private val stringProvider: AppStringProvider,
    private val onUpdateFileCache: suspend (String) -> Unit
) {

    /**
     * 响应 FileCacheManager 的全局缓存变更事件，实现 UI 实时同步
     */
    suspend fun handleCacheEvent(event: CacheEvent) {
        val currentCid = stateHolder.currentContentState.currentCid
        val dirName = when (event) {
            is CacheEvent.LocalUiUpdated -> resolveDirName(event.cid)
            is CacheEvent.RemoteRefreshRequired -> resolveDirName(event.cid)
            is CacheEvent.FolderDeleted -> resolveDirName(event.folderCid)
            is CacheEvent.FoldersDeleted -> "批量目录(${event.folderCids.size}个)"
            CacheEvent.AllCleared -> "全部缓存"
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
                if (event.cid == currentCid && stateHolder.currentContentState.source is FileListSource.Folder) {
                    val updatedCache = FileCacheManager.getDate(event.cid)
                    if (updatedCache != null) {
                        val currentList = stateHolder.currentContentState.fileBeanList
                        // 判定当前展示的条目与缓存是否已完全一致（解决 deleteMultiple 先行乐观更新后的重复重组）
                        val isSameList = currentList.size == updatedCache.fileBeanList.size &&
                                currentList.map { it.fileId } == updatedCache.fileBeanList.map { it.fileId }
                        if (isSameList) {
                            stateHolder.updateContentState {
                                val unselectedList =
                                    updatedCache.fileBeanList.map { it.copy(isSelect = false) }
                                copy(
                                    fileBeanList = unselectedList,
                                    pathList = updatedCache.path,
                                    totalCount = updatedCache.count,
                                    isLongClickState = false,
                                    appBarTitle = stringProvider.defaultAppName
                                )
                            }
                        }

                        // 对齐复合键分页完成状态与物理游标（以 FilesBean.isComplete 为唯一信源 SSOT）
                        val paginationKey = "folder:${event.cid}"
                        pagingStateStore.setOffset(paginationKey, updatedCache.fileBeanList.size)
                        if (updatedCache.isComplete) {
                            pagingStateStore.markComplete(paginationKey)
                        }
                    } else {
                        // 若本地内存缓存意外丢失，兜底触发网络拉取
                        navigator.getFiles(currentCid)
                    }
                }
            }

            is CacheEvent.RemoteRefreshRequired -> {
                // 【远程 API 刷新逻辑】：
                // 针对新增文件、解压完成、离线下载完成、字幕上传、回收站还原等场景，云端生成了新文件或变动：
                /**不需要删除缓存，因为在 [FileCacheManager.notifyRemoteRefresh] 里已经删除了**/
                if (stateHolder.currentContentState.source is FileListSource.Folder) {
                    if (event.cid == currentCid) {
                        // 当前展示目录变动，刷新当前 UI 页面
                        navigator.refresh(currentCid, true)
                    } else {
                        // 非当前展示目录：FileCacheManager 在 notifyRemoteRefresh 中已清空对应 cid 的缓存
                        // 此时无需修改 UI 的 source，后台静默拉取/更新该 cid 缓存即可（或等切入时懒加载）
                        onUpdateFileCache(event.cid)
                    }
                }
            }

            is CacheEvent.FolderDeleted -> {
                handleFolderDeletedCheck(setOf(event.folderCid))
            }

            is CacheEvent.FoldersDeleted -> {
                handleFolderDeletedCheck(event.folderCids)
            }

            is CacheEvent.AllCleared -> {
                // 保留当前正在刷新目录的条目直至新列表返回，避免刷新中途 UI 抢跑，其余目录直接移除
                val keysToRemove = imageCacheStore.imageBeanCache.keys.filter { it != currentCid }
                keysToRemove.forEach { imageCacheStore.clear(it) }
                pagingStateStore.clear()
                coilCacheCleaner.clearMemoryCache()
                coilCacheCleaner.clearDiskCache()
                if (currentCid != "0") {
                    navigator.getFiles("0")
                }
            }
        }
    }

    /**
     * 高性能检查被删目录集合是否包含当前目录或祖先目录
     */
    private suspend fun handleFolderDeletedCheck(deletedCids: Set<String>) {
        if (deletedCids.isEmpty()) return
        val validCids = deletedCids.filter { it != "0" }.toSet()
        if (validCids.isEmpty()) return

        validCids.forEach {
            imageCacheStore.clear(it)
            pagingStateStore.reset("folder:$it")
        }

        val currentCid = stateHolder.currentContentState.currentCid
        val pathList = stateHolder.currentContentState.pathList
        val isCurrentDeleted = currentCid in validCids
        val isAncestorDeleted = pathList.any { it.cid in validCids }
        XLog.d("handleFolderDeletedCheck isCurrentDeleted=$isCurrentDeleted, isAncestorDeleted=$isAncestorDeleted, count=${validCids.size}")

        if (isCurrentDeleted || isAncestorDeleted) {
            App.instance.toast("当前所在目录已被删除")
            val survivingAncestorCid =
                pathList.takeWhile { it.cid !in validCids }.lastOrNull()?.cid ?: "0"
            navigator.getFiles(survivingAncestorCid)
        }
    }

    private fun resolveDirName(cid: String): String {
        val currentCid = stateHolder.currentContentState.currentCid
        if (cid == currentCid) return stateHolder.currentContentState.pathList.lastOrNull()?.name ?: "当前目录($cid)"
        // 尝试从内存缓存中获取其面包屑末级名称
        return FileCacheManager.getDate(cid)?.path?.lastOrNull()?.name ?: "cid=$cid"
    }
}
