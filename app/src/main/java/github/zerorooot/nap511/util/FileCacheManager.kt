package github.zerorooot.nap511.util

import androidx.collection.LruCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class CacheWrapper<T>(
    val data: T,
    val timestamp: Long = System.currentTimeMillis()
)

class FileCacheManager<T>(
    private val cacheDir: File,
    private val classType: java.lang.reflect.Type,
    private val saveRequestCache: Boolean,
    maxMemoryEntries: Int = 300,                  // 内存 LRU 保留容量
    private val ttlMillis: Long = 7 * 24 * 3600 * 1000L // 7 天过期
) {
    private val gson = Gson()
    private val mutex = Mutex()

    // 内存 LRU 缓存：只要 App 运行，始终存在且有效
    private val memoryCache = object : LruCache<String, CacheWrapper<T>>(maxMemoryEntries) {}

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun containsKey(key: String): Boolean = memoryCache[key] != null

    fun getDate(key: String): T? {
        return memoryCache[key]?.data
    }

    /**
     * 读取缓存：
     * 1. 优先查【内存缓存】（无视 readDisk 参数，只要内存有就直接返回）
     * 2. 内存未命中且 readDisk == true 时，才查【磁盘文件】
     */
    suspend operator fun get(key: String): T? = withContext(Dispatchers.IO) {
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
                val diskFile = getDiskFile(key)
                if (diskFile.exists()) {
                    try {
                        val json = diskFile.readText()
                        val wrapperType =
                            TypeToken.getParameterized(CacheWrapper::class.java, classType).type
                        val diskEntry: CacheWrapper<T>? = gson.fromJson(json, wrapperType)

                        if (diskEntry != null) {
                            if (now - diskEntry.timestamp > ttlMillis) {
                                diskFile.delete()
                                return@withContext null
                            }
                            // 磁盘读取成功后，同步一份到内存，下次直接走内存
                            memoryCache.put(key, diskEntry)
                            return@withContext diskEntry.data
                        }
                    } catch (e: Exception) {
                        diskFile.delete()
                    }
                }
            }
            return@withContext null
        }
    }

    suspend operator fun set(key: String, value: T) {
        put(key, value)
    }

    /**
     * 写入/更新缓存：
     * 1. 【内存缓存】：无条件写入！保证运行时始终有缓存
     * 2. 【磁盘缓存】：仅当 saveToDisk == true 时写入文件；若为 false，则同步清理对应磁盘旧文件
     */
    suspend fun put(key: String, value: T) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val entry = CacheWrapper(data = value)

                // 1. 内存中无论如何都要保存
                memoryCache.put(key, entry)

                // 2. 根据开关控制是否落盘
                if (saveRequestCache) {
                    try {
                        val diskFile = getDiskFile(key)
                        val wrapperType =
                            TypeToken.getParameterized(CacheWrapper::class.java, classType).type
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
            memoryCache.evictAll()
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
            val wrapperType = TypeToken.getParameterized(CacheWrapper::class.java, classType).type

            for (file in files) {
                try {
                    val json = file.readText()
                    val entry: CacheWrapper<T>? = gson.fromJson(json, wrapperType)
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