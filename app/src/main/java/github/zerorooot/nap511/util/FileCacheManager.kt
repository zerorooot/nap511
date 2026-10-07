package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.VideoBean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.StringJoiner
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

data class CacheWrapper(
    val data: FilesBean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 缓存数据变更通知事件
 */
sealed interface CacheEvent {
    /** 目录内部条目发生变动（添加、删除、重命名、移动、播放进度变更等） */
    data class ContentUpdated(val cid: String) : CacheEvent

    /** 目录本身及其所有子孙目录被级联删除（rm -r / deleteMultiple） */
    data class FolderDeleted(val folderCid: String) : CacheEvent

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

    // 独立后台协程作用域，用于不阻塞启动流程的后台加载任务
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 内存 LRU 缓存：只要 App 运行，始终存在且有效
    private val memoryCache = ConcurrentHashMap<String, CacheWrapper>(30)

    // 全局缓存变更事件流（带缓冲，避免高频变更时产生阻塞挂起）
    private val _cacheEvents = MutableSharedFlow<CacheEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cacheEvents: SharedFlow<CacheEvent> = _cacheEvents.asSharedFlow()

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
            //cleanExpiredDiskCache()

            cacheDir.listFiles()?.forEach { file ->
                val key = file.name.substringBeforeLast(".")
                if (key != "0") {
                    getDiskCache(key)?.let {
                        memoryCache[key] = it
                    }
                }
            }
        }
    }

    fun deleteIndividualFile() {
        if (!::cacheDir.isInitialized) return
        val diskCache = memoryCache["0"] ?: return

        val fileList =
            cacheDir.listFiles()?.map { i -> i.name.substringBeforeLast(".") }?.toMutableList()
                ?: return
        fileList.remove("0")

        fun walk(cid: String) {
            val walkCache = memoryCache[cid] ?: return
            val folderList = walkCache.data.fileBeanList.filter { it.isFolder }
            for (item in folderList) {
                walk(item.categoryId)
            }
            fileList.remove(cid)
        }

        diskCache.data.fileBeanList.forEach {
            if (it.isFolder) {
                walk(it.categoryId)
            }
        }
        val length = fileList.size
        if (length == 0) {
            return
        }

        val stringJoiner = StringJoiner("；")
        fileList.forEach { i ->
            memoryCache[i]?.data?.path?.last()?.name?.let {
                stringJoiner.add(it)
            }
            deleteDiskFile(i)
        }

        XLog.d("deleteIndividualFile 删除${length}个单一文件 $stringJoiner")
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
                    deleteDiskFile(key)
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
                flushToDiskInternal(key)
                _cacheEvents.tryEmit(CacheEvent.ContentUpdated(key))
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
        mutex.withLock {
            val cache = memoryCache[parentCid]?.data ?: return@withContext null
            val index = cache.fileBeanList.indexOfFirst { it.fileId == fid || it.categoryId == fid }
            if (index < 0) return@withContext null

            val oldBean = cache.fileBeanList[index]
            val oldName = oldBean.name
            cache.fileBeanList[index] = oldBean.copy(name = newName)
            flushToDiskInternal(parentCid)

            val isFolder = oldBean.isFolder
            val folderCid = oldBean.categoryId.ifEmpty { fid }
            if (isFolder) {
                memoryCache[folderCid]?.data?.let { subCache ->
                    subCache.path.lastOrNull()?.name = newName
                    flushToDiskInternal(folderCid)
                }
            }

            _cacheEvents.tryEmit(CacheEvent.ContentUpdated(parentCid))
            if (isFolder) {
                _cacheEvents.tryEmit(CacheEvent.ContentUpdated(folderCid))
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
            val targetIndex = cache.fileBeanList.indexOfFirst { it.fileId == fid || it.categoryId == fid }
            if (targetIndex < 0) return@withContext null

            val originalList = ArrayList(cache.fileBeanList)
            val originalCount = cache.count

            val removedItem = cache.fileBeanList.removeAt(targetIndex)
            cache.count = (cache.count - 1).coerceAtLeast(0)
            flushToDiskInternal(parentCid)

            val actualIsFolder = isFolder ?: removedItem.isFolder
            val targetFolderCid = removedItem.categoryId.ifEmpty { fid }
            if (actualIsFolder) {
                removeFolderRecursivelyInternal(targetFolderCid)
            }

            _cacheEvents.tryEmit(CacheEvent.ContentUpdated(parentCid))
            if (actualIsFolder) {
                _cacheEvents.tryEmit(CacheEvent.FolderDeleted(targetFolderCid))
            }

            CacheRollback {
                mutex.withLock {
                    val curCache = memoryCache[parentCid]?.data ?: return@withLock
                    curCache.fileBeanList.clear()
                    curCache.fileBeanList.addAll(originalList)
                    curCache.count = originalCount
                    flushToDiskInternal(parentCid)
                    _cacheEvents.tryEmit(CacheEvent.ContentUpdated(parentCid))
                }
            }
        }
    }

    /**
     * 就地批量从父目录缓存中移除指定条目，支持级联子目录清理与完整回滚恢复。
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
            val deletedFolders = mutableListOf<String>()

            val iterator = cache.fileBeanList.listIterator()
            var removedCount = 0
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.fileId in fidSet || item.categoryId in fidSet) {
                    removedCount++
                    iterator.remove()
                    if (item.isFolder) {
                        val subCid = item.categoryId.ifEmpty { item.fileId }
                        deletedFolders.add(subCid)
                        removeFolderRecursivelyInternal(subCid)
                    }
                }
            }
            if (removedCount == 0) return@withContext null

            cache.count = (cache.count - removedCount).coerceAtLeast(0)
            flushToDiskInternal(parentCid)

            _cacheEvents.tryEmit(CacheEvent.ContentUpdated(parentCid))
            deletedFolders.forEach { _cacheEvents.tryEmit(CacheEvent.FolderDeleted(it)) }

            CacheRollback {
                mutex.withLock {
                    val curCache = memoryCache[parentCid]?.data ?: return@withLock
                    curCache.fileBeanList.clear()
                    curCache.fileBeanList.addAll(originalList)
                    curCache.count = originalCount
                    flushToDiskInternal(parentCid)
                    _cacheEvents.tryEmit(CacheEvent.ContentUpdated(parentCid))
                }
            }
        }
    }

    /**
     * 递归深度清理目录及其所有子孙目录在 FileCacheManager 中的缓存，并向外广播 FolderDeleted 事件。
     */
    suspend fun removeFolderRecursively(folderCid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            removeFolderRecursivelyInternal(folderCid)
        }
        _cacheEvents.tryEmit(CacheEvent.FolderDeleted(folderCid))
    }

    private fun removeFolderRecursivelyInternal(folderCid: String) {
        val list = memoryCache[folderCid]?.data?.fileBeanList ?: emptyList()
        for (item in list) {
            if (item.isFolder) {
                val subFolderCid = item.categoryId.ifEmpty { item.fileId }
                if (subFolderCid.isNotEmpty()) {
                    removeFolderRecursivelyInternal(subFolderCid)
                }
            }
        }
        memoryCache.remove(folderCid)
        deleteDiskFile(folderCid)
    }

    /**
     * 就地向父目录缓存中追加新创建的目录，并预埋新目录自身的空缓存（若未创建）。
     */
    suspend fun addFolder(parentCid: String, folderName: String, newCid: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val parentCache = memoryCache[parentCid]?.data
                if (parentCache != null) {
                    if (parentCache.fileBeanList.none { it.isFolder && it.name == folderName }) {
                        val newFolderBean = FileBean(
                            name = folderName,
                            categoryId = newCid,
                            fileId = newCid,
                            isFolder = true
                        )
                        parentCache.fileBeanList.add(newFolderBean)
                        parentCache.count = (parentCache.count + 1)
                        flushToDiskInternal(parentCid)
                    }
                }

                // 预埋新目录自身的空缓存（若未创建）
                if (!memoryCache.containsKey(newCid)) {
                    val parentPathList = parentCache?.path ?: emptyList()
                    val newPathList = parentPathList + PathBean(cid = newCid, name = folderName, pid = parentCid)
                    val newFilesBean = FilesBean(
                        fileBeanList = arrayListOf(),
                        cid = newCid,
                        count = 0,
                        order = "",
                        path = newPathList
                    )
                    val entry = CacheWrapper(data = newFilesBean)
                    memoryCache[newCid] = entry
                    flushToDiskInternal(newCid)
                }

                _cacheEvents.tryEmit(CacheEvent.ContentUpdated(parentCid))
            }
        }

    /**
     * 就地向父目录缓存追加普通文件条目并落盘。
     */
    suspend fun addFile(parentCid: String, fileBean: FileBean) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val cache = memoryCache[parentCid]?.data ?: return@withContext
                if (cache.fileBeanList.none { !it.isFolder && it.name == fileBean.name }) {
                    cache.fileBeanList.add(fileBean)
                    cache.count = (cache.count + 1)
                    flushToDiskInternal(parentCid)
                    _cacheEvents.tryEmit(CacheEvent.ContentUpdated(parentCid))
                }
            }
        }

    /**
     * 就地处理跨目录移动：从源父目录移出，并添加到目标目录缓存中。
     */
    suspend fun moveItem(srcParentCid: String, targetCid: String, fid: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val srcCache = memoryCache[srcParentCid]?.data
                val targetBean = srcCache?.fileBeanList?.find { it.fileId == fid || it.categoryId == fid }
                if (srcCache != null && targetBean != null) {
                    srcCache.fileBeanList.remove(targetBean)
                    srcCache.count = (srcCache.count - 1).coerceAtLeast(0)
                    flushToDiskInternal(srcParentCid)
                    _cacheEvents.tryEmit(CacheEvent.ContentUpdated(srcParentCid))
                }

                if (targetBean != null) {
                    val targetCache = memoryCache[targetCid]?.data
                    if (targetBean.isFolder) {
                        val folderCid = targetBean.categoryId.ifEmpty { fid }
                        if (targetCache != null && targetCache.fileBeanList.none { it.isFolder && it.name == targetBean.name }) {
                            targetCache.fileBeanList.add(targetBean)
                            targetCache.count = (targetCache.count + 1)
                            flushToDiskInternal(targetCid)
                        }
                        // 若移动的是文件夹，由于其完整层级路径发生变更，清理被移动文件夹本身的缓存以保证下次进入时重新生成正确面包屑
                        memoryCache.remove(folderCid)
                        deleteDiskFile(folderCid)
                    } else {
                        val movedBean = targetBean.copy(categoryId = targetCid)
                        if (targetCache != null && targetCache.fileBeanList.none { !it.isFolder && it.name == movedBean.name }) {
                            targetCache.fileBeanList.add(movedBean)
                            targetCache.count = (targetCache.count + 1)
                            flushToDiskInternal(targetCid)
                        }
                    }
                    _cacheEvents.tryEmit(CacheEvent.ContentUpdated(targetCid))
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
                        fileList[index] = fileBean.copy(playLongRatio = playTimeRatio)
                        hasChanges = true
                    }
                }
            }

            if (hasChanges) {
                flushToDiskInternal(cid)
                _cacheEvents.tryEmit(CacheEvent.ContentUpdated(cid))
            }
        }
    }

    /**
     * 删除某个 Key 的缓存（内存 + 磁盘）
     */
    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            memoryCache.remove(key)
            deleteDiskFile(key)
        }
    }

    /**
     * 仅清空磁盘文件，保留内存中的缓存
     */
    suspend fun clearDiskOnly() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (::cacheDir.isInitialized) {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    /**
     * 清空全部缓存（内存 + 磁盘）
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            memoryCache.clear()
            if (::cacheDir.isInitialized) {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
            _cacheEvents.tryEmit(CacheEvent.AllCleared)
        }
    }

    /**
     * 清理磁盘过期的缓存文件
     */
    suspend fun cleanExpiredDiskCache() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!::cacheDir.isInitialized) return@withContext
            val files = cacheDir.listFiles() ?: return@withContext
            val now = System.currentTimeMillis()

            for (file in files) {
                try {
                    val json = file.readText()
                    val entry: CacheWrapper? = gson.fromJson(json, wrapperType)
                    if (entry == null || (now - entry.timestamp > ttlMillis)) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    file.delete()
                }
            }
        }
    }

    private fun flushToDiskInternal(key: String) {
        if (!saveRequestCache || !::cacheDir.isInitialized) return
        val entry = memoryCache[key] ?: return
        try {
            val diskFile = getDiskFile(key)
            val json = gson.toJson(entry, wrapperType)
            diskFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDiskFile(key: String): File = File(cacheDir, "${key}.json")

    private fun deleteDiskFile(key: String) {
        if (!::cacheDir.isInitialized) return
        val file = getDiskFile(key)
        if (file.exists()) file.delete()
    }
}
