package github.zerorooot.nap511.ui.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.ui.model.FileListSource
import github.zerorooot.nap511.data.local.cache.CacheEvent
import github.zerorooot.nap511.data.local.cache.FileCacheManager
import github.zerorooot.nap511.data.model.FilesBean
import github.zerorooot.nap511.util.formatFileBeanList
import github.zerorooot.nap511.platform.AppStringProvider
import github.zerorooot.nap511.platform.CoilCacheCleaner
import github.zerorooot.nap511.platform.FileNavigator
import github.zerorooot.nap511.platform.ToastProvider
import github.zerorooot.nap511.ui.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.ui.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.ui.viewmodel.store.PagingStateStore

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
    /**
     * 非当前展示目录的静默预拉回调。
     *
     * @param cid  目标目录 cid
     * @param sort 目标目录的排序上下文快照（order / isAsc / fcMix），
     *             由 [FileCacheManager.notifyRemoteRefresh] 在删缓存前采集；
     *             为 null 时由实现方回退到「当前展示目录排序 + 全局 fcmix」兜底。
     */
    private val onUpdateFileCache: suspend (String, FilesBean?) -> Unit,
    private val toastProvider: ToastProvider
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
                        stateHolder.updateContentState {
                            copy(
                                fileBeanList = formatFileBeanList(updatedCache.fileBeanList),
                                pathList = updatedCache.path,
                                totalCount = updatedCache.count,
                                selectedFileIds = emptySet(),
                                isLongClickState = false,
                                appBarTitle = source.appName
                            )
                        }

                        // 【分页游标不再在此写入】
                        // 原实现有 setOffset(key, cache.size) 与 markComplete，二者都已删除：
                        // 1) cache.fileBeanList.size 是去重后的展示条数，不等于服务端物理游标；
                        //    本地加星/改名/删除/播放进度回写会改变 size，若拿它当 offset 会造成跳页/重复拉取。
                        // 2) Folder 的 offset/complete 唯一权威已在 FilesBean.physicalOffset / isComplete，
                        //    本地就地维护缓存时由 data class copy 天然沿用，无需也不应在此改写。
                        // 这里只做「读缓存刷新 UI」，缓存丢失由下方 navigateTo 兜底重进。
                    } else {
                        // 若本地内存缓存意外丢失，兜底触发网络拉取
                        navigator.navigateTo(currentCid)
                    }
                }
            }

            is CacheEvent.RemoteRefreshRequired -> {
                // 【远程 API 刷新逻辑】：
                // 针对新增文件、解压完成、离线下载完成、字幕上传、回收站还原等场景，云端生成了新文件或变动：
                /**不需要删除缓存，因为在 [FileCacheManager.notifyRemoteRefresh] 里已经删除了**/
                if (stateHolder.currentContentState.source is FileListSource.Folder) {
                    if (event.cid == currentCid) {
                        // 当前展示目录变动，刷新当前 UI 页面。
                        // 当前目录永远以 stateHolder 里的本目录权威排序为准，不需要也不应该用快照覆盖，
                        // 所以这里继续沿用不带 sort 的整页刷新路径。
                        navigator.refresh(currentCid, true)
                    } else {
                        // 非当前展示目录：FileCacheManager 在 notifyRemoteRefresh 中已清空对应 cid 的缓存
                        // 此时无需修改 UI 的 source，后台静默拉取/更新该 cid 缓存即可（或等切入时懒加载）
                        // 关键修复点：把事件里携带的**目标目录排序快照**透传下去，
                        // 让静默预拉使用「目标目录自己的 order / isAsc / fcMix」而不是当前展示目录的排序，
                        // 从而修复解压完成后自动刷新跳序 / 不遵循 fcmix 的问题。
                        // 快照为 null（冷目录）时由 onUpdateFileCache 实现方回退兜底，与修复前行为一致。
                        XLog.d(
                            "RemoteRefreshRequired: non-current cid=${event.cid}, " +
                                    "currentCid=$currentCid, " +
                                    "sort=${event.sort?.let { "order=${it.order}, isAsc=${it.isAsc}, fcMix=${it.fcMix}" } ?: "null"}"
                        )
                        onUpdateFileCache(event.cid, event.sort)
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
                    navigator.navigateTo("0")
                }
            }
        }
    }

    /**
     * 清理磁盘过期与孤立文件缓存
     */
    suspend fun deleteOrphansAndExpiredFile() {
        FileCacheManager.deleteOrphansAndExpiredFile()
    }

    /**
     * 高性能检查被删目录集合是否包含当前目录或祖先目录
     */
    private fun handleFolderDeletedCheck(deletedCids: Set<String>) {
        if (deletedCids.isEmpty()) return
        val validCids = deletedCids.filter { it != "0" }.toSet()
        if (validCids.isEmpty()) return

        validCids.forEach {
            imageCacheStore.clear(it)
            pagingStateStore.reset(FileListSource.folderKey(it))
        }

        val currentCid = stateHolder.currentContentState.currentCid
        val pathList = stateHolder.currentContentState.pathList
        val isCurrentDeleted = currentCid in validCids
        val isAncestorDeleted = pathList.any { it.cid in validCids }
        XLog.d("handleFolderDeletedCheck isCurrentDeleted=$isCurrentDeleted, isAncestorDeleted=$isAncestorDeleted, count=${validCids.size}")

        if (isCurrentDeleted || isAncestorDeleted) {
            toastProvider.showToast("当前所在目录已被删除")
            val survivingAncestorCid =
                pathList.takeWhile { it.cid !in validCids }.lastOrNull()?.cid ?: "0"
            navigator.navigateTo(survivingAncestorCid)
        }
    }

    private fun resolveDirName(cid: String): String {
        val currentCid = stateHolder.currentContentState.currentCid
        if (cid == currentCid) return stateHolder.currentContentState.pathList.lastOrNull()?.name
            ?: "当前目录($cid)"
        // 尝试从内存缓存中获取其面包屑末级名称
        return FileCacheManager.getDate(cid)?.path?.lastOrNull()?.name ?: "cid=$cid"
    }
}
