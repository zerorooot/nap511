package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.bean.cache.CacheWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.StringJoiner
import java.util.concurrent.Executors
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
    private lateinit var cacheDir: File

    @Volatile
    var saveRequestCache: Boolean = true

    private const val ttlMillis: Long = 7 * 24 * 3600 * 1000L // 7 天过期

    private val gson = Gson()
    private val mutex = Mutex()
    private val wrapperType = CacheWrapper::class.java

    // 单线程 FIFO 调度器：天然保证磁盘落盘与删除的时序绝对一致，避免多线程异步写覆盖和回滚反序竞态
    private val diskDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "FileCacheManager-DiskIO").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    // 独立后台协程作用域，用于不阻塞关键链路的磁盘异步持久化与后台加载任务
    private val scope = CoroutineScope(diskDispatcher + SupervisorJob())

    // 最大内存目录缓存容量，超过时基于 LRU 淘汰旧目录，保障深度扫描时的内存安全与防 OOM
    private const val MAX_MEMORY_DIRS = 50

    private class LruCacheMap(private val capacity: Int) :
        LinkedHashMap<String, CacheWrapper>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheWrapper>?): Boolean {
            if (size <= capacity) return false
            // 若最老元素是根目录 "0"，不能直接淘汰 "0"，应在超过容量时保留 "0" 并移除次老元素
            if (eldest?.key == "0") {
                // 找到第一个非 "0" 的键进行淘汰
                val iterator = iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key != "0") {
                        iterator.remove()
                        break
                    }
                }
                return false
            }
            return true
        }
    }

    // 处于失效/物理删除处理中的 CID 墓碑集合，任何磁盘读操作若命中该集合直接判定为未命中，彻底杜绝异步时序差
    private val tombstoneKeys = Collections.synchronizedSet(HashSet<String>())

    // 内存 LRU 缓存：只要 App 运行，始终存在且有效（使用 synchronizedMap 保证高并发读写安全）
    private val memoryCache: MutableMap<String, CacheWrapper> =
        Collections.synchronizedMap(LruCacheMap(MAX_MEMORY_DIRS))

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
            tombstoneKeys.add(cid)
            try {
                mutex.withLock {
                    memoryCache.remove(cid)
                }
                // 挂起等待单线程调度器物理删除完成，杜绝异步时序差与旧快照复活
                deleteDiskFileSync(cid)
                XLog.d("FileCacheManager.notifyRemoteRefresh: cid=$cid disk file deleted, emitting RemoteRefreshRequired")
                _cacheEvents.tryEmit(CacheEvent.RemoteRefreshRequired(cid))
            } finally {
                tombstoneKeys.remove(cid)
            }
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
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun containsKey(key: String): Boolean = memoryCache.containsKey(key)

    fun getDate(key: String): FilesBean? = memoryCache[key]?.data

    suspend fun loadAllCache() = withContext(Dispatchers.IO) {
        // 不保存磁盘缓存时，仅清理硬盘旧文件，保留内存缓存
        if (!saveRequestCache) {
            clearDiskOnly()
            return@withContext
        }

        if (!::cacheDir.isInitialized) return@withContext

        // 1. 优先只加载 key="0"（根目录/首页）的缓存，确保主界面快速获得数据
        getDiskCache("0")?.let {
            memoryCache["0"] = it
        }

        // 2. 在后台异步加载其余文件及清理过期缓存，不阻塞启动流程/SplashScreen
        scope.launch {
            cacheDir.listFiles()?.forEach { file ->
                val key = file.name.substringBeforeLast(".")
                if (key != "0") {
                    getDiskCache(key)?.let { diskWrapper ->
                        // 使用 putIfAbsent 避免启动时后台预加载覆盖运行中刚刚产生的新缓存
                        memoryCache.putIfAbsent(key, diskWrapper)
                    }
                }
            }
        }
    }

    /**
     * 清理磁盘过期、孤立文件
     */
    suspend fun deleteOrphansAndExpiredFile() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!::cacheDir.isInitialized) return@withContext
            val now = System.currentTimeMillis()

            // 1. 获取磁盘上所有缓存文件的 key 集合（排除根目录 "0"）
            val allDiskKeys = cacheDir.listFiles()
                ?.map { it.name.substringBeforeLast(".") }
                ?.toMutableSet() ?: return@withContext
            allDiskKeys.remove("0")

            val reachableKeys = mutableSetOf<String>()

            // 2. 深度优先遍历可达树 (Supports Disk Fallback)
            suspend fun walk(cid: String) {
                // 优先查内存，未命中则查磁盘
                val cacheWrapper = memoryCache[cid] ?: getDiskCache(cid, now) ?: return

                // 如果该目录缓存本身已过期，不再继续向下遍历
                if (now - cacheWrapper.timestamp > ttlMillis) return

                // 标记当前目录为可达
                if (cid != "0") {
                    reachableKeys.add(cid)
                }

                val folderList = cacheWrapper.data.fileBeanList.filter { it.isFolder }
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
                memoryCache[key]?.data?.path?.lastOrNull()?.name?.let {
                    deletedNames.add(it)
                }
                memoryCache.remove(key)
                val file = getDiskFile(key)
                if (file.exists()) file.delete()
            }

            XLog.d("deleteIndividualFile 清理了 ${orphanKeys.size} 个孤立/过期缓存文件: $deletedNames")
        }
    }


    /**
     * 读取缓存：
     * 1. 优先查【内存缓存】（无视 readDisk 参数，只要内存有就直接返回）
     * 2. 内存未命中且 readDisk == true 时，才查【磁盘文件】
     */
    suspend operator fun get(key: String): FilesBean? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()

            // 1. 【内存缓存】最高优先级：无论 saveRequestCache 为何值，内存命中直接返回
            val memEntry = memoryCache[key]
            if (memEntry != null) {
                if (now - memEntry.timestamp > ttlMillis) {
                    memoryCache.remove(key)
                    deleteDiskFileSync(key)
                    return@withContext null
                }
                return@withContext memEntry.data
            }

            // 2. 内存未命中，当允许读磁盘时，才查【磁盘缓存】
            if (saveRequestCache) {
                val diskEntry = getDiskCache(key, now) ?: return@withContext null
                memoryCache[key] = diskEntry
                return@withContext diskEntry.data
            }
            return@withContext null
        }
    }

    suspend fun getDiskCache(key: String, now: Long = System.currentTimeMillis()): CacheWrapper? =
        withContext(Dispatchers.IO) {
            if (!::cacheDir.isInitialized) return@withContext null
            // 墓碑拦截：若当前 key 正处于物理删除处理中，立即判定未命中，严禁读取旧快照
            if (tombstoneKeys.contains(key)) {
                XLog.d("FileCacheManager.getDiskCache: key=$key is tombstoned, skipping disk read")
                return@withContext null
            }
            val diskFile = getDiskFile(key)
            if (!diskFile.exists()) {
                return@withContext null
            }
            try {
                val json = diskFile.readText()
                val diskEntry: CacheWrapper? = gson.fromJson(json, wrapperType)

                if (diskEntry != null) {
                    if (now - diskEntry.timestamp > ttlMillis) {
                        diskFile.delete()
                        return@withContext null
                    }
                    return@withContext diskEntry
                }
            } catch (e: Exception) {
                diskFile.delete()
            }
            return@withContext null
        }

    suspend operator fun set(key: String, value: FilesBean) = put(key, value)


    /**
     * 写入/更新缓存：
     * 1. 【内存缓存】：无条件写入！保证运行时始终有缓存
     * 2. 【磁盘缓存】：仅当 saveToDisk == true 时写入文件
     */
    suspend fun put(key: String, value: FilesBean) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val entry = CacheWrapper(data = value)
                memoryCache[key] = entry
                flushToDiskAsync(key, entry)
            }
        }

    suspend fun putAndNotify(key: String, value: FilesBean) {
        put(key, value)
        _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(key))
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
        mutex.withLock {
            val cache = memoryCache[parentCid]?.data ?: return@withContext null
            val index = cache.fileBeanList.indexOfFirst { it.fileId == fid || it.categoryId == fid }
            if (index < 0) return@withContext null

            val oldBean = cache.fileBeanList[index]
            val oldName = oldBean.name
            cache.fileBeanList[index] = oldBean.copy(name = newName)
            memoryCache[parentCid]?.let { flushToDiskAsync(parentCid, it) }

            val isFolder = oldBean.isFolder
            val folderCid = oldBean.categoryId.ifEmpty { fid }
            if (isFolder) {
                memoryCache[folderCid]?.let { subEntry ->
                    subEntry.data.path.lastOrNull()?.name = newName
                    flushToDiskAsync(folderCid, subEntry)
                }
            }

            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
            if (isFolder) {
                _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(folderCid))
            }

            CacheRollback {
                renameItem(parentCid, fid, oldName)
            }
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
        mutex.withLock {
            val cache = memoryCache[parentCid]?.data ?: return@withContext null
            val targetIndex =
                cache.fileBeanList.indexOfFirst { it.fileId == fid || it.categoryId == fid }
            if (targetIndex < 0) return@withContext null

            val originalList = ArrayList(cache.fileBeanList)
            val originalCount = cache.count

            val removedItem = cache.fileBeanList.removeAt(targetIndex)
            cache.count = (cache.count - 1).coerceAtLeast(0)
            memoryCache[parentCid]?.let { flushToDiskAsync(parentCid, it) }

            val actualIsFolder = isFolder ?: removedItem.isFolder
            val targetFolderCid = removedItem.categoryId.ifEmpty { fid }
            val removedFolderCids = mutableSetOf<String>()
            if (actualIsFolder) {
                removeFolderRecursivelyInternal(targetFolderCid, removedFolderCids)
                deleteDiskFilesSync(removedFolderCids)
            }

            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
            if (actualIsFolder) {
                _cacheEvents.tryEmit(CacheEvent.FolderDeleted(targetFolderCid))
            }

            CacheRollback {
                mutex.withLock {
                    val curCache = memoryCache[parentCid]?.data ?: return@withLock
                    curCache.fileBeanList.clear()
                    curCache.fileBeanList.addAll(originalList)
                    curCache.count = originalCount
                    memoryCache[parentCid]?.let { flushToDiskAsync(parentCid, it) }
                    _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
                }
            }
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
        mutex.withLock {
            val cache = memoryCache[parentCid]?.data ?: return@withContext null
            val fidSet = fids.toSet()
            val originalList = ArrayList(cache.fileBeanList)
            val originalCount = cache.count
            val allDeletedFolderCids = mutableSetOf<String>()

            val iterator = cache.fileBeanList.listIterator()
            var removedCount = 0
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.fileId in fidSet || item.categoryId in fidSet) {
                    removedCount++
                    iterator.remove()
                    if (item.isFolder) {
                        val subCid = item.categoryId.ifEmpty { item.fileId }
                        removeFolderRecursivelyInternal(subCid, allDeletedFolderCids)
                    }
                }
            }
            if (removedCount == 0) return@withContext null

            cache.count = (cache.count - removedCount).coerceAtLeast(0)
            memoryCache[parentCid]?.let { flushToDiskAsync(parentCid, it) }
            if (allDeletedFolderCids.isNotEmpty()) {
                deleteDiskFilesSync(allDeletedFolderCids)
            }

            _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
            if (allDeletedFolderCids.isNotEmpty()) {
                _cacheEvents.tryEmit(CacheEvent.FoldersDeleted(allDeletedFolderCids))
            }

            CacheRollback {
                mutex.withLock {
                    val curCache = memoryCache[parentCid]?.data ?: return@withLock
                    curCache.fileBeanList.clear()
                    curCache.fileBeanList.addAll(originalList)
                    curCache.count = originalCount
                    memoryCache[parentCid]?.let { flushToDiskAsync(parentCid, it) }
                    _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(parentCid))
                }
            }
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
        mutex.withLock {
            removeFolderRecursivelyInternal(folderCid, visited)
            deleteDiskFilesSync(visited)
        }
        if (emitFolderDeleted && folderCid != "0") {
            _cacheEvents.tryEmit(CacheEvent.FolderDeleted(folderCid))
        }
    }

    private fun removeFolderRecursivelyInternal(
        folderCid: String,
        visited: MutableSet<String> = HashSet()
    ) {
        if (folderCid.isEmpty() || !visited.add(folderCid)) {
            return
        }
        val list = memoryCache[folderCid]?.data?.fileBeanList ?: emptyList()
        for (item in list) {
            if (item.isFolder) {
                val subFolderCid = item.categoryId.ifEmpty { item.fileId }
                // 严格边界保护：非空、非根目录且未访问过
                if (subFolderCid.isNotEmpty() && subFolderCid != "0" && !visited.contains(
                        subFolderCid
                    )
                ) {
                    removeFolderRecursivelyInternal(subFolderCid, visited)
                }
            }
        }
        memoryCache.remove(folderCid)
    }


    /**
     * 批量更新指定目录下视频文件的播放进度比例（playLongRatio），加锁更新、同步刷盘并广播变动事件。
     */
    suspend fun updateVideoProgress(
        cid: String,
        videoHistoryMap: Map<String, VideoBean>
    ) = withContext(Dispatchers.IO) {
        if (videoHistoryMap.isEmpty()) return@withContext
        mutex.withLock {
            val cache = memoryCache[cid]?.data ?: return@withLock
            var hasChanges = false
            val fileList = cache.fileBeanList

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

            if (hasChanges) {
                memoryCache[cid]?.let { flushToDiskAsync(cid, it) }
                XLog.d("updateVideoProgress CacheEvent.LocalUiUpdated")
                _cacheEvents.tryEmit(CacheEvent.LocalUiUpdated(cid))
            }
        }
    }

    /**
     * 删除某个 Key 的缓存（内存 + 磁盘）
     */
    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        tombstoneKeys.add(key)
        try {
            mutex.withLock {
                memoryCache.remove(key)
            }
            deleteDiskFileSync(key)
            XLog.d("FileCacheManager.remove: key=$key disk file deleted")
        } finally {
            tombstoneKeys.remove(key)
        }
    }

    /**
     * 仅清空磁盘文件，保留内存中的缓存
     */
    suspend fun clearDiskOnly() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (::cacheDir.isInitialized) {
                withContext(diskDispatcher) {
                    cacheDir.listFiles()?.forEach { it.delete() }
                }
            }
        }
    }

    /**
     * 仅清空内存缓存（用于模拟低内存淘汰或冷启动，保留磁盘缓存）
     */
    suspend fun clearMemoryOnly() = withContext(Dispatchers.IO) {
        mutex.withLock {
            memoryCache.clear()
        }
    }

    /**
     * 清空全部缓存（内存 + 磁盘）
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            memoryCache.clear()
            if (::cacheDir.isInitialized) {
                withContext(diskDispatcher) {
                    cacheDir.listFiles()?.forEach { it.delete() }
                    cacheDir.mkdirs()
                }
            }
            _cacheEvents.tryEmit(CacheEvent.AllCleared)
        }
    }


    /**
     * 单线程 FIFO 异步落盘：将 JSON 序列化与文件 IO 移至后台 scope 执行，避免阻塞全局 Mutex 锁与调用方线程，且杜绝并发写乱序
     */
    private fun flushToDiskAsync(key: String, entry: CacheWrapper) {
        if (!saveRequestCache || !::cacheDir.isInitialized) return
        scope.launch {
            try {
                val diskFile = getDiskFile(key)
                val json = gson.toJson(entry, wrapperType)
                diskFile.writeText(json)
            } catch (e: Exception) {
                XLog.e("flushToDiskAsync failed for key: $key", e)
            }
        }
    }

    /**
     * 在单线程 FIFO 调度器中挂起执行物理文件删除，确保队列前序落盘完成且物理文件已被移除
     */
    private suspend fun deleteDiskFileSync(key: String) {
        if (!::cacheDir.isInitialized) return
        withContext(diskDispatcher) {
            try {
                val file = getDiskFile(key)
                if (file.exists()) {
                    file.delete()
                    XLog.d("FileCacheManager.deleteDiskFileSync: key=$key deleted")
                }
            } catch (e: Exception) {
                XLog.e("deleteDiskFileSync failed for key: $key", e)
            }
        }
    }

    private suspend fun deleteDiskFilesSync(keys: Set<String>) {
        if (!::cacheDir.isInitialized || keys.isEmpty()) return
        withContext(diskDispatcher) {
            keys.forEach { key ->
                try {
                    val file = getDiskFile(key)
                    if (file.exists()) {
                        file.delete()
                        XLog.d("FileCacheManager.deleteDiskFilesSync: key=$key deleted")
                    }
                } catch (e: Exception) {
                    XLog.e("deleteDiskFilesSync failed for key: $key", e)
                }
            }
        }
    }

    private fun getDiskFile(key: String): File = File(cacheDir, "${key}.json")
}
