package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.VideoBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.StringJoiner
import kotlin.math.roundToInt

/**
 * 缓存数据变更通知事件
 */
sealed interface CacheEvent {
    /** 需请求远端 115 API 全量刷新指定目录（新增文件、解压、离线下载完成、字幕上传、回收站还原等） */
    data class RemoteRefreshRequired(val cid: String) : CacheEvent

    /** 本地缓存已就地同步完毕，仅需刷新 UI 列表，严禁发起远程 API 请求（删除、重命名、播放进度更新、终端预拉取等） */
    data class LocalUiUpdated(val cid: String) : CacheEvent

    /** 目录本身及其所有子孙目录被级联删除（rm -r / deleteMultiple） */
    data class FolderDeleted(val folderCid: String) : CacheEvent

    /** 批量目录及其子孙目录被级联删除（聚合事件，防止批量删除时引发事件风暴） */
    data class FoldersDeleted(val folderCids: Set<String>) : CacheEvent

    /** 全局缓存被清空 */
    data object AllCleared : CacheEvent
}

/**
 * 缓存乐观操作撤销句柄
 */
fun interface CacheRollback {
    suspend fun rollback()
}

object FileCacheManager {
    private var cacheDir: File? = null

    // 最大内存目录缓存容量，超过时基于 LRU 淘汰旧目录，保障深度扫描时的内存安全与防 OOM
    private const val MAX_MEMORY_DIRS = 500

    @Volatile
    private var _saveRequestCache: Boolean = true

    // 默认实例化支持纯内存运行的 Engine，未调用 init(cacheDir) 时同样可以安全在内存中读写，杜绝未初始化崩溃
    @Volatile
    private var engine: TwoLevelCacheEngine = TwoLevelCacheEngine(
        cacheDir = null,
        maxMemoryDirs = MAX_MEMORY_DIRS,
        saveRequestCacheInitial = _saveRequestCache
    )

    // 独立后台协程作用域，用于不阻塞关键链路的磁盘异步持久化与后台加载任务
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var saveRequestCache: Boolean
        get() = engine.saveRequestCache
        set(value) {
            updateConfig(saveRequestCache = value)
        }

    /**
     * 动态热更新配置项（容量与落盘开关）
     */
    fun updateConfig(maxMemoryDirs: Int? = null, saveRequestCache: Boolean? = null): Job {
        if (saveRequestCache != null) {
            _saveRequestCache = saveRequestCache
            // 同步立即生效底层标志位，防止单测/启动设值竞态
            engine.saveRequestCache = saveRequestCache
        }
        return scope.launch {
            if (maxMemoryDirs != null || saveRequestCache != null) {
                engine.updateConfig(maxMemoryDirs = maxMemoryDirs, saveRequestCache = saveRequestCache)
                XLog.d("FileCacheManager.updateConfig applied: maxMemoryDirs=$maxMemoryDirs, saveRequestCache=$saveRequestCache")
            }
        }
    }

    fun getMaxMemoryDirs(): Int = engine.getMaxMemoryDirs()

    // 全局缓存变更事件流（带缓冲，避免高频变更时产生阻塞挂起）
    private val _cacheEvents = MutableSharedFlow<CacheEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cacheEvents: SharedFlow<CacheEvent> = _cacheEvents.asSharedFlow()

    /**
     * 手动向外广播指定 CacheEvent 事件
     */
    fun notifyCacheEvent(event: CacheEvent) {
        _cacheEvents.tryEmit(event)
    }

    /**
     * 通知远端数据发生变动（新增文件、解压完成、离线下载完成、字幕上传、回收站还原等）。
     * 清理本地缓存并广播 RemoteRefreshRequired 事件，促使 FileViewModel 在对应目录下发起 115 API 全量刷新。
     */
    suspend fun notifyRemoteRefresh(cid: String) =
        withContext(Dispatchers.IO) {
            engine.remove(cid)
            XLog.d("FileCacheManager.notifyRemoteRefresh: cid=$cid disk file deleted, emitting RemoteRefreshRequired")
            _cacheEvents.tryEmit(CacheEvent.RemoteRefreshRequired(cid))
        }

    /**
     * 通知本地缓存已完成就地更新（删除、重命名、视频进度更新等）。
     * 广播 LocalUiUpdated 事件，驱动 UI 在目录匹配时刷新展示列表，绝不触发网络 API 请求。
     */
    fun notifyLocalUpdated(cid: String) {
        _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(cid))
    }

    fun init(cacheDir: File) {
        this.cacheDir = cacheDir
        engine.close()
        engine = TwoLevelCacheEngine(
            cacheDir = cacheDir,
            maxMemoryDirs = MAX_MEMORY_DIRS,
            saveRequestCacheInitial = _saveRequestCache
        )
    }

    fun containsKey(key: String): Boolean = engine.containsMemory(key)

    //fun getMemoryCache(key: String): FilesBean? = memoryCache[key]?.data

    /**
     * 同步获取纯内存缓存（非挂起函数，直接查询内存 LRU，不触发磁盘 IO） getMemoryCache
     * 在内存未命中且开启落盘时，自动从磁盘文件同步回源并回填内存
     */
    fun getDate(key: String): FilesBean? = engine.getSync(key)

    /**
     * 同步获取纯内存缓存（直接查询内存 LRU，不触发磁盘 IO）
     */
    fun peekMemory(key: String): FilesBean? = engine.peekMemory(key)

    suspend fun loadAllCache() = withContext(Dispatchers.IO) {
        engine.preload()
    }

    /**
     * 清理磁盘过期、孤立文件
     */
    suspend fun deleteOrphansAndExpiredFile() = withContext(Dispatchers.IO) {
        val allDiskKeys = engine.getAllDiskKeys().toMutableSet()
        allDiskKeys.remove("0")
        if (allDiskKeys.isEmpty()) return@withContext

        // 严格边界防护：若根目录无法加载（冷启动未就绪或已过期），直接中止清理，严防误判导致全盘缓存被当作孤立文件清空
        if (engine.get("0") == null) {
            XLog.w("deleteOrphansAndExpiredFile: 根目录 '0' 缓存不存在，终止清理以防误删全盘缓存")
            return@withContext
        }

        val reachableKeys = mutableSetOf<String>()

        // 2. 深度优先遍历可达树 (Supports Disk Fallback)
        suspend fun walk(cid: String) {
            // 优先查内存，未命中则查磁盘
            val cacheWrapper = engine.get(cid) ?: return

            // 标记当前目录为可达
            if (cid != "0") {
                reachableKeys.add(cid)
            }

            val folderList = cacheWrapper.fileBeanList.filter { it.isFolder }
            for (item in folderList) {
                val subCid = item.categoryId.ifEmpty { item.fileId }
                if (subCid.isNotEmpty() && subCid != "0" && !reachableKeys.contains(subCid)) {
                    walk(subCid)
                }
            }
        }

        // 3. 从根节点 "0" 开始递归遍历
        walk("0")

        // 4. 孤立文件 = 磁盘所有文件 - 可达文件
        val orphanKeys = allDiskKeys - reachableKeys
        if (orphanKeys.isEmpty()) return@withContext

        // 5. 同步清理内存与磁盘
        val deletedNames = StringJoiner("；")
        orphanKeys.forEach { key ->
            engine.peekMemory(key)?.path?.lastOrNull()?.name?.let {
                deletedNames.add(it)
            }
        }
        engine.removeBatch(orphanKeys)

        XLog.d("deleteIndividualFile 清理了 ${orphanKeys.size} 个孤立/过期缓存文件: $deletedNames")
    }

    /**
     * 读取缓存：
     * 1. 优先查【内存缓存】（无视 readDisk 参数，只要内存有就直接返回）
     * 2. 内存未命中且 readDisk == true 时，才查【磁盘文件】
     */
    suspend operator fun get(key: String): FilesBean? = engine.get(key)

    suspend operator fun set(key: String, value: FilesBean) = put(key, value)

    /**
     * 写入/更新缓存：
     * 1. 【内存缓存】：无条件写入！保证运行时始终有缓存
     * 2. 【磁盘缓存】：仅当 saveToDisk == true 时写入文件
     */
    suspend fun put(key: String, value: FilesBean) = engine.put(key, value)

    suspend fun putAndNotify(key: String, value: FilesBean) {
        put(key, value)
        _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(key))
    }

    /**
     * 就地批量更新父目录缓存中指定条目的星标状态。
     * 加锁保证线程安全，同步持久化落盘，广播变更事件，并返回撤销句柄。
     */
    suspend fun starItems(
        parentCid: String,
        fids: Collection<String>,
        isStar: Boolean
    ): CacheRollback? = withContext(Dispatchers.IO) {
        if (fids.isEmpty()) return@withContext null
        val fidSet = fids.toSet()
        val oldBeans = mutableMapOf<String, FileBean>()
        val starInt = if (isStar) 1 else 0
        var hasChange = false

        val updated = engine.update(parentCid) { cache ->
            val newList = ArrayList(cache.fileBeanList)
            newList.forEachIndexed { index, item ->
                val targetFid = item.categoryId.ifEmpty { item.fileId }
                if (item.fileId in fidSet || item.categoryId in fidSet || targetFid in fidSet) {
                    val key = item.fileId.ifEmpty { item.categoryId }
                    oldBeans[key] = item
                    newList[index] = item.copy(isStar = isStar, star = starInt)
                    hasChange = true
                }
            }
            if (hasChange) cache.copy(fileBeanList = newList) else null
        }

        if (!hasChange || updated == null) return@withContext null

        XLog.d("FileCacheManager.starItems: Successfully starred ${oldBeans.size} items in parentCid=$parentCid, isStar=$isStar")
        _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))

        CacheRollback {
            engine.update(parentCid) { curCache ->
                val restoredList = ArrayList(curCache.fileBeanList)
                restoredList.forEachIndexed { index, item ->
                    val key = item.fileId.ifEmpty { item.categoryId }
                    val oldBean = oldBeans[key]
                    if (oldBean != null) {
                        restoredList[index] = oldBean
                    }
                }
                curCache.copy(fileBeanList = restoredList)
            }
            XLog.d("FileCacheManager.starItems: Rolled back ${oldBeans.size} items in parentCid=$parentCid")
            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
        }
    }


    /**
     * 就地重命名父目录缓存中的文件或子目录，若为目录则同步更新子目录自身缓存中的面包屑末级名称。
     * 加锁保证线程安全，同步持久化落盘，广播变更事件，并返回撤销句柄。
     */
    suspend fun renameItem(
        parentCid: String,
        fid: String,
        newName: String
    ): CacheRollback? = withContext(Dispatchers.IO) {
        var oldName: String? = null
        var isFolder = false
        var folderCid = ""

        val updatedParent = engine.update(parentCid) { cache ->
            val index = cache.fileBeanList.indexOfFirst { it.fileId == fid || it.categoryId == fid }
            if (index < 0) return@update null

            val oldBean = cache.fileBeanList[index]
            oldName = oldBean.name
            isFolder = oldBean.isFolder
            folderCid = oldBean.categoryId.ifEmpty { fid }

            val newList = ArrayList(cache.fileBeanList)
            newList[index] = oldBean.copy(name = newName)
            cache.copy(fileBeanList = newList)
        }

        val capturedOldName = oldName
        if (updatedParent == null || capturedOldName == null) return@withContext null

        if (isFolder && folderCid.isNotEmpty()) {
            engine.update(folderCid) { subCache ->
                val pathList = subCache.path.toMutableList()
                if (pathList.isNotEmpty()) {
                    pathList[pathList.lastIndex] = pathList.last().copy(name = newName)
                    subCache.copy(path = pathList)
                } else null
            }
        }

        _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
        if (isFolder && folderCid.isNotEmpty()) {
            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(folderCid))
        }

        CacheRollback {
            renameItem(parentCid, fid, capturedOldName)
        }
    }

    /**
     * 就地批量重命名父目录缓存中的文件或目录，若包含目录则同步更新子目录自身缓存中的面包屑末级名称。
     * 基于不可变 copy 模式更新，单次加锁、单次异步刷盘、单次广播 LocalUiUpdated 事件，彻底杜绝事件风暴。
     *
     * @param parentCid 父目录 ID
     * @param renameMap 待更新映射字典 (fid/categoryId -> newName)
     * @return 失败或撤销时的 CacheRollback 句柄
     */
    suspend fun renameItems(
        parentCid: String,
        renameMap: Map<String, String>
    ): CacheRollback? = withContext(Dispatchers.IO) {
        if (renameMap.isEmpty()) return@withContext null

        val rollbackMap = mutableMapOf<String, String>()
        val updatedFolders = mutableListOf<Pair<String, String>>()
        var hasChanges = false

        val updatedParent = engine.update(parentCid) { cache ->
            val newList = ArrayList(cache.fileBeanList)
            for (index in newList.indices) {
                val item = newList[index]
                /**
                 * fid与cid已经在 [github.zerorooot.nap511.util.formatFileBeanList] 中统一了，同时兼容 categoryId 容错
                 */
                val key = if (renameMap.containsKey(item.fileId)) {
                    item.fileId
                } else if (renameMap.containsKey(item.categoryId)) {
                    item.categoryId
                } else null

                if (key != null) {
                    val newName = renameMap[key]
                    if (!newName.isNullOrBlank() && newName != item.name) {
                        rollbackMap[key] = item.name
                        // 采用 copy 生成新实例替换，遵守 FileBean 只读属性规范
                        newList[index] = item.copy(name = newName)
                        hasChanges = true

                        if (item.isFolder) {
                            val folderCid = item.categoryId.ifEmpty { key }
                            updatedFolders.add(folderCid to newName)
                        }
                    }
                }
            }
            if (hasChanges) cache.copy(fileBeanList = newList) else null
        }

        if (!hasChanges || updatedParent == null) return@withContext null

        // 同步更新子目录自身缓存中的面包屑末级名称
        updatedFolders.forEach { (folderCid, newName) ->
            engine.update(folderCid) { subCache ->
                val pathList = subCache.path.toMutableList()
                if (pathList.isNotEmpty()) {
                    pathList[pathList.lastIndex] = pathList.last().copy(name = newName)
                    subCache.copy(path = pathList)
                } else null
            }
        }

        // 1. 父目录单次广播
        _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))

        // 2. 同步广播发生名称变更的子目录自身 LocalUiUpdated 事件（与单项 rename 对齐）
        updatedFolders.forEach { (folderCid, _) ->
            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(folderCid))
        }

        XLog.d("renameItems: 成功就地更新目录 $parentCid 下的 ${rollbackMap.size} 项缓存数据")

        // 3. 返回复合回滚撤销句柄
        CacheRollback {
            renameItems(parentCid, rollbackMap)
        }
    }

    /**
     * 就地从父目录缓存中移除指定文件或目录，并递减计数；若删除项为目录，则级联清理其子孙目录缓存。
     * 返回支持撤销现场的 CacheRollback 句柄。
     */
    suspend fun removeItem(
        parentCid: String,
        fid: String,
        isFolder: Boolean? = null
    ): CacheRollback? = withContext(Dispatchers.IO) {
        var originalList: ArrayList<FileBean>? = null
        var originalCount = 0
        var removedItem: FileBean? = null

        val updatedParent = engine.update(parentCid) { cache ->
            val targetIndex =
                cache.fileBeanList.indexOfFirst { it.fileId == fid || it.categoryId == fid }
            if (targetIndex < 0) return@update null

            originalList = ArrayList(cache.fileBeanList)
            originalCount = cache.count

            val newList = ArrayList(cache.fileBeanList)
            removedItem = newList.removeAt(targetIndex)
            val newCount = (cache.count - 1).coerceAtLeast(0)
            cache.copy(fileBeanList = newList, count = newCount)
        }

        val capturedRemovedItem = removedItem
        if (updatedParent == null || capturedRemovedItem == null) return@withContext null

        val actualIsFolder = isFolder ?: capturedRemovedItem.isFolder
        val targetFolderCid = capturedRemovedItem.categoryId.ifEmpty { fid }
        val removedFolderCids = mutableSetOf<String>()
        if (actualIsFolder) {
            collectSubFoldersRecursively(targetFolderCid, removedFolderCids)
            engine.removeBatch(removedFolderCids)
        }

        _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
        if (actualIsFolder) {
            _cacheEvents.tryEmit(CacheEvent.FolderDeleted(targetFolderCid))
        }

        CacheRollback {
            engine.update(parentCid) { curCache ->
                originalList?.let { orig ->
                    curCache.copy(fileBeanList = ArrayList(orig), count = originalCount)
                }
            }
            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
        }
    }

    /**
     * 就地批量从父目录缓存中移除指定条目：
     * 1. 采用 Set 快速过滤；
     * 2. 级联递归移除子文件夹在内存中的记录，并收集全部被删子孙目录集合；
     * 3. 单线程 FIFO 同步后台批量清理对应磁盘缓存，释放 Mutex 关键锁等待时间；
     * 4. 采用聚合 FoldersDeleted 事件广播，彻底杜绝循环发射导致的 UI 事件风暴。
     */
    suspend fun removeItems(
        parentCid: String,
        fids: Collection<String>
    ): CacheRollback? = withContext(Dispatchers.IO) {
        if (fids.isEmpty()) return@withContext null
        val fidSet = fids.toSet()
        var originalList: ArrayList<FileBean>? = null
        var originalCount = 0
        val allDeletedFolderCids = mutableSetOf<String>()
        var removedCount = 0

        val updatedParent = engine.update(parentCid) { cache ->
            val newList = ArrayList(cache.fileBeanList)
            val iterator = newList.listIterator()
            val foldersToRemove = mutableListOf<String>()

            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.fileId in fidSet || item.categoryId in fidSet) {
                    removedCount++
                    iterator.remove()
                    if (item.isFolder) {
                        val subCid = item.categoryId.ifEmpty { item.fileId }
                        foldersToRemove.add(subCid)
                    }
                }
            }
            if (removedCount == 0) return@update null

            originalList = ArrayList(cache.fileBeanList)
            originalCount = cache.count
            allDeletedFolderCids.addAll(foldersToRemove)

            val newCount = (cache.count - removedCount).coerceAtLeast(0)
            cache.copy(fileBeanList = newList, count = newCount)
        }

        if (updatedParent == null || removedCount == 0) return@withContext null

        val subFoldersToRecurse = ArrayList(allDeletedFolderCids)
        allDeletedFolderCids.clear()
        for (folderCid in subFoldersToRecurse) {
            collectSubFoldersRecursively(folderCid, allDeletedFolderCids)
        }

        if (allDeletedFolderCids.isNotEmpty()) {
            engine.removeBatch(allDeletedFolderCids)
        }

        _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
        if (allDeletedFolderCids.isNotEmpty()) {
            _cacheEvents.tryEmit(CacheEvent.FoldersDeleted(allDeletedFolderCids))
        }

        CacheRollback {
            engine.update(parentCid) { curCache ->
                originalList?.let { orig ->
                    curCache.copy(fileBeanList = ArrayList(orig), count = originalCount)
                }
            }
            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
        }
    }

    /**
     * 递归深度清理目录及其所有子孙目录在 FileCacheManager 中的缓存。
     *
     * @param folderCid 待清理的目录 CID
     * @param emitFolderDeleted 是否向外广播 FolderDeleted 事件。
     *                          真实物理删除目录（如 rm -r、批量删除）时传 true；
     *                          仅清理本地缓存（如强制刷新、缓存失效）时必须传 false！
     */
    suspend fun removeFolderRecursively(
        folderCid: String,
        emitFolderDeleted: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val visited = HashSet<String>()
        collectSubFoldersRecursively(folderCid, visited)
        engine.removeBatch(visited)

        if (emitFolderDeleted && folderCid != "0") {
            _cacheEvents.tryEmit(CacheEvent.FolderDeleted(folderCid))
        }
    }

    private suspend fun collectSubFoldersRecursively(
        folderCid: String,
        visited: MutableSet<String> = HashSet()
    ) {
        if (folderCid.isEmpty() || !visited.add(folderCid)) {
            return
        }
        val cache = engine.get(folderCid) ?: return
        val list = cache.fileBeanList
        for (item in list) {
            if (item.isFolder) {
                val subFolderCid = item.categoryId.ifEmpty { item.fileId }
                // 严格边界保护：非空、非根目录且未访问过
                if (subFolderCid.isNotEmpty() && subFolderCid != "0" && !visited.contains(subFolderCid)) {
                    collectSubFoldersRecursively(subFolderCid, visited)
                }
            }
        }
    }

    /**
     * 批量更新指定目录下视频文件的播放进度比例（playLongRatio），加锁更新、同步刷盘并广播变动事件。
     */
    suspend fun updateVideoProgress(
        cid: String,
        videoHistoryMap: Map<String, VideoBean>
    ) = withContext(Dispatchers.IO) {
        if (videoHistoryMap.isEmpty()) return@withContext
        var hasChanges = false

        val updated = engine.update(cid) { cache ->
            val fileList = ArrayList(cache.fileBeanList)
            videoHistoryMap.forEach { (pickCode, bean) ->
                val index = fileList.indexOfFirst { it.pickCode == pickCode }
                if (index != -1) {
                    val fileBean = fileList[index]
                    if (fileBean.isVideo == 1) {
                        val duration = bean.currentDuration
                        val playTime = if (fileBean.playLong == 0.0) {
                            100
                        } else {
                            ((duration.toFloat() / fileBean.playLong) * 100).roundToInt()
                        }
                        val playTimeRatio = "▶️ $playTime%"
                        //同步更新currentPlayTime，避免从缓存中formatFileBeanList时，由于没有更新currentPlayTime，导致playTimeRatio还是原值
                        fileList[index] =
                            fileBean.copy(playLongRatio = playTimeRatio, currentPlayTime = duration)
                        hasChanges = true
                    }
                }
            }
            if (hasChanges) cache.copy(fileBeanList = fileList) else null
        }

        if (hasChanges && updated != null) {
            XLog.d("updateVideoProgress CacheEvent.LocalUiUpdated")
            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(cid))
        }
    }

    /**
     * 删除某个 Key 的缓存（内存 + 磁盘）
     */
    suspend fun remove(key: String) = engine.remove(key)

    /**
     * 仅清空磁盘文件，保留内存中的缓存
     */
    suspend fun clearDiskOnly() = engine.clearDiskOnly()

    /**
     * 仅清空内存缓存（用于模拟低内存淘汰或冷启动，保留磁盘缓存）
     */
    suspend fun clearMemoryOnly() = engine.clearMemoryOnly()

    /**
     * 清空全部缓存（内存 + 磁盘）
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        engine.clearAll()
        _cacheEvents.tryEmit(CacheEvent.AllCleared)
    }
}
