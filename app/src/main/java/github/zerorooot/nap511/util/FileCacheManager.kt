package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FilesBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.StringJoiner
import java.util.concurrent.ConcurrentHashMap

data class CacheWrapper(
    val data: FilesBean,
    val timestamp: Long = System.currentTimeMillis()
)

class FileCacheManager(
    private val cacheDir: File,
    private val saveRequestCache: Boolean,
    private val ttlMillis: Long = 7 * 24 * 3600 * 1000L // 7 天过期
) {
    private val gson = Gson()
    private val mutex = Mutex()
    private val wrapperType = CacheWrapper::class.java

    // 内存 LRU 缓存：只要 App 运行，始终存在且有效
    private val memoryCache = ConcurrentHashMap<String, CacheWrapper>(30)

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun containsKey(key: String): Boolean = memoryCache.containsKey(key)

    fun getDate(key: String): FilesBean? = memoryCache[key]?.data

    suspend fun loadAllCache() = withContext(Dispatchers.IO) {
        // 立即异步启动加载 "0"
        async { getDiskCache("0") }.await()?.let {
            memoryCache["0"] = it
        }
        // 4. 并发加载其他文件
        cacheDir.listFiles()?.map { file ->
            async {
                val key = file.name.substringBeforeLast(".")
                getDiskCache(key)?.let {
                    memoryCache[key] = it
                }
            }
        }?.awaitAll()

//        async { deleteIndividualFile() }.await()
    }

    suspend fun deleteIndividualFile() = withContext(Dispatchers.IO) {
        val diskCache = getDiskCache("0") ?: return@withContext
        val fileList =
            cacheDir.listFiles()?.map { i -> i.name.substringBeforeLast(".") }?.toMutableList()
                ?: return@withContext
        fileList.remove("0")

        fun walk(cid: String) {
            val folderList =
                getDiskCache(cid)?.data?.fileBeanList?.filter { it.isFolder } ?: emptyList()
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
            return@withContext
        }

        val stringJoiner = StringJoiner("；")
        fileList.forEach { i ->
            memoryCache[i]?.data?.path?.last()?.name?.let {
                stringJoiner.add("name: $it")
            }
            deleteDiskFile(i)
        }

        XLog.d("deleteIndividualFile 删除${length}个单一文件\n$stringJoiner")
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


    fun getDiskCache(key: String, now: Long = System.currentTimeMillis()): CacheWrapper? {
        val diskFile = getDiskFile(key)
        if (!diskFile.exists()) {
            return null
        }
        try {
            val json = diskFile.readText()
            val diskEntry: CacheWrapper? = gson.fromJson(json, wrapperType)

            if (diskEntry != null) {
                if (now - diskEntry.timestamp > ttlMillis) {
                    diskFile.delete()
                    return null
                }
                // 磁盘读取成功后，同步一份到内存，下次直接走内存
                //    memoryCache[key] = diskEntry
                return diskEntry
            }
        } catch (e: Exception) {
            diskFile.delete()
        }
        return null
    }

    suspend operator fun set(key: String, value: FilesBean) = put(key, value)


    /**
     * 写入/更新缓存：
     * 1. 【内存缓存】：无条件写入！保证运行时始终有缓存
     * 2. 【磁盘缓存】：仅当 saveToDisk == true 时写入文件；若为 false，则同步清理对应磁盘旧文件
     */
    suspend fun put(key: String, value: FilesBean) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val entry = CacheWrapper(data = value)

                // 1. 内存中无论如何都要保存
                memoryCache[key] = entry

                // 2. 根据开关控制是否落盘
                if (saveRequestCache) {
                    try {
                        val diskFile = getDiskFile(key)
                        val json = gson.toJson(entry, wrapperType)
                        diskFile.writeText(json)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 清空全部缓存（内存 + 磁盘）
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            memoryCache.clear()
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 清理磁盘过期的缓存文件
     */
    suspend fun cleanExpiredDiskCache() = withContext(Dispatchers.IO) {
        mutex.withLock {
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

    private fun getDiskFile(key: String): File = File(cacheDir, "${key}.json")

    private fun deleteDiskFile(key: String) {
        val file = getDiskFile(key)
        if (file.exists()) file.delete()
    }
}