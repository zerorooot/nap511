package github.zerorooot.nap511.data.local.cache

import androidx.core.util.AtomicFile
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.data.model.FilesBean
import github.zerorooot.nap511.data.model.cache.CacheWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.util.Collections
import java.util.concurrent.Executors

/**
 * 两级安全缓存存储引擎（L1 内存 LRU + L2 磁盘持久化）
 *
 * 核心机制：
 * 1. 根目录常驻：key="0" 的目录由专有引用 rootCache 常驻内存，不占用普通 LRU 额度，永不参与淘汰。
 * 2. 普通目录 LRU：标准 LinkedHashMap 管理非 "0" 普通目录，超额自动淘汰最老条目。
 * 3. 前置 JSON 序列化：在互斥锁内生成只读 JSON 文本后再派发异步写盘，彻底杜绝多线程读写引发的 CME。
 * 4. 互斥排队与墓碑屏障：删除时加锁设置墓碑并在单线程中物理删除，杜绝旧快照复活与写覆盖乱序。
 * 5. 崩溃一致性落盘：采用 AtomicFile 进行原子写入，避免进程强杀残留损坏文件。
 * 6. 生命周期管理：实现 Closeable 接口，优雅释放单线程 Executor 与协程作用域。
 */
class TwoLevelCacheEngine(
    private val cacheDir: File? = null,
    maxMemoryDirs: Int = 500,
    saveRequestCacheInitial: Boolean = true
) : Closeable {

    @Volatile
    var saveRequestCache: Boolean = saveRequestCacheInitial
        set(value) {
            val changed = field != value
            field = value
            if (changed && !value) {
                scope.launch { clearDiskOnly() }
            }
        }

    private val ttlMillis: Long = 7 * 24 * 3600 * 1000L // 7 天过期

    private val gson = Gson()
    private val mutex = Mutex()
    private val wrapperType = CacheWrapper::class.java

    // 单线程 FIFO 执行器：保证落盘与删除的时序绝对一致，避免多线程异步写覆盖和回滚反序竞态
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TwoLevelCacheEngine-DiskIO").apply { isDaemon = true }
    }
    private val diskDispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(diskDispatcher + SupervisorJob())

    // 根目录专用常驻引用（Root Pinning 永不参与 LRU 淘汰）
    @Volatile
    private var rootCache: CacheWrapper? = null

    // 普通目录 LRU 缓存容量
    @Volatile
    private var maxCapacity = maxMemoryDirs

    private class LruCacheMap(private val capacityProvider: () -> Int) :
        LinkedHashMap<String, CacheWrapper>(capacityProvider(), 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheWrapper>?): Boolean {
            return size > capacityProvider()
        }
    }

    // 内存 LRU 缓存（纯净非 "0" 普通目录，使用 synchronizedMap 保证并发安全）
    private val memoryCache: MutableMap<String, CacheWrapper> =
        Collections.synchronizedMap(LruCacheMap { maxCapacity })

    // 处于失效/物理删除处理中的 CID 墓碑集合，任何磁盘读操作若命中该集合直接判定为未命中，彻底杜绝异步时序差
    private val tombstoneKeys = Collections.synchronizedSet(HashSet<String>())

    init {
        if (cacheDir != null && !cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    // ---------------- 基础读写接口 ----------------

    /**
     * 同步获取纯内存缓存（非挂起函数，直接查询内存 LRU/rootCache，不触发磁盘 IO）
     */
    fun peekMemory(key: String): FilesBean? {
        val now = System.currentTimeMillis()
        if (key == "0") {
            val root = rootCache ?: return null
            if (now - root.timestamp > ttlMillis) {
                rootCache = null
                return null
            }
            return root.data
        }
        val entry = memoryCache[key] ?: return null
        if (now - entry.timestamp > ttlMillis) {
            memoryCache.remove(key)
            return null
        }
        return entry.data
    }

    /**
     * 判断纯内存中是否包含有效缓存
     */
    fun containsMemory(key: String): Boolean {
        return peekMemory(key) != null
    }

    /**
     * 读取缓存：
     * 1. 优先查【内存缓存】（根目录查 rootCache，普通目录查 LRU）
     * 2. 内存未命中且 saveRequestCache == true 时，查【磁盘缓存】
     */
    suspend fun get(key: String): FilesBean? = withContext(Dispatchers.IO) {
        mutex.withLock {
            getWithoutLock(key)
        }
    }

    /**
     * 同步获取缓存：
     * 1. 优先查【内存缓存】（根目录查 rootCache，普通目录查 LRU）
     * 2. 内存未命中且 saveRequestCache == true 时，同步从【磁盘缓存】加载并回填内存
     */
    fun getSync(key: String): FilesBean? {
        val now = System.currentTimeMillis()

        // 1. 内存缓存检查
        if (key == "0") {
            val root = rootCache
            if (root != null) {
                if (now - root.timestamp > ttlMillis) {
                    rootCache = null
                    deleteDiskFileSync("0")
                    return null
                }
                return root.data
            }
        } else {
            val memEntry = memoryCache[key]
            if (memEntry != null) {
                if (now - memEntry.timestamp > ttlMillis) {
                    memoryCache.remove(key)
                    deleteDiskFileSync(key)
                    return null
                }
                return memEntry.data
            }
        }

        // 2. 磁盘缓存回源
        if (saveRequestCache) {
            val diskEntry = getDiskCacheSync(key, now) ?: return null
            if (key == "0") {
                rootCache = diskEntry
            } else {
                memoryCache[key] = diskEntry
            }
            return diskEntry.data
        }
        return null
    }

    /**
     * 无锁版本的缓存读取（供内部已持锁的 mutex.withLock 场景调用，彻底避免 Kotlin Mutex 非可重入引发的死锁/假死）
     */
    internal fun getWithoutLock(key: String): FilesBean? {
        return getSync(key)
    }

    /**
     * 读取磁盘缓存（同步版本，基于 AtomicFile 崩溃恢复机制、带墓碑拦截与 TTL 检测）
     */
    fun getDiskCacheSync(key: String, now: Long = System.currentTimeMillis()): CacheWrapper? {
        if (cacheDir == null || !cacheDir.exists()) return null
        // 墓碑拦截：若当前 key 正处于物理删除处理中，立即判定未命中，严禁读取旧快照
        if (tombstoneKeys.contains(key)) {
            XLog.d("TwoLevelCacheEngine.getDiskCacheSync: key=$key is tombstoned, skipping disk read")
            return null
        }
        val diskFile = getDiskFile(key)
        val bakFile = File(cacheDir, "${diskFile.name}.bak")
        if (!diskFile.exists() && !bakFile.exists()) {
            return null
        }
        val atomicFile = AtomicFile(diskFile)
        try {
            val bytes = atomicFile.readFully()
            val json = String(bytes, Charsets.UTF_8)
            val diskEntry: CacheWrapper? = gson.fromJson(json, wrapperType)

            if (diskEntry != null) {
                if (now - diskEntry.timestamp > ttlMillis) {
                    atomicFile.delete()
                    return null
                }
                return diskEntry
            }
        } catch (_: FileNotFoundException) {
            return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            XLog.w("TwoLevelCacheEngine.getDiskCacheSync: corrupted file for key=$key, deleting", e)
            atomicFile.delete()
        }
        return null
    }

    /**
     * 读取磁盘缓存（基于 AtomicFile 崩溃恢复机制、带墓碑拦截与 TTL 检测）
     */
    suspend fun getDiskCache(key: String, now: Long = System.currentTimeMillis()): CacheWrapper? =
        withContext(Dispatchers.IO) {
            getDiskCacheSync(key, now)
        }

    /**
     * 写入/更新缓存：
     * 1. 【内存缓存】：无条件写入内存（0 写入 rootCache，其他写入 memoryCache），主动拔除墓碑
     * 2. 【磁盘缓存】：前置序列化为 JSON 字符串，异步投递到单线程落盘队列
     */
    suspend fun put(key: String, value: FilesBean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            tombstoneKeys.remove(key)
            val entry = CacheWrapper(data = value)
            if (key == "0") {
                rootCache = entry
            } else {
                memoryCache[key] = entry
            }
            if (saveRequestCache) {
                val json = gson.toJson(entry, wrapperType)
                flushToDiskAsync(key, json)
            }
        }
    }

    /**
     * 单 Key 原地原子更新：在互斥锁保护下读取、变换并写回
     */
    suspend fun update(key: String, transform: (FilesBean) -> FilesBean?): FilesBean? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = getWithoutLock(key) ?: return@withLock null
                val updated = transform(current) ?: return@withLock null
                tombstoneKeys.remove(key)
                val entry = CacheWrapper(data = updated)
                if (key == "0") {
                    rootCache = entry
                } else {
                    memoryCache[key] = entry
                }
                if (saveRequestCache) {
                    val json = gson.toJson(entry, wrapperType)
                    flushToDiskAsync(key, json)
                }
                updated
            }
        }

    // ---------------- 安全删除与维护接口 ----------------

    /**
     * 删除某个 Key 的缓存（内存 + 磁盘）
     */
    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        val deleteJob = mutex.withLock {
            tombstoneKeys.add(key)
            if (key == "0") {
                rootCache = null
            } else {
                memoryCache.remove(key)
            }
            // 在单线程 FIFO 调度器中派发物理删除任务，立墓碑后立即释放互斥锁，避免阻塞全局读写
            scope.launch {
                try {
                    if (cacheDir == null) return@launch
                    val file = getDiskFile(key)
                    AtomicFile(file).delete()
                    XLog.d("TwoLevelCacheEngine.remove: key=$key disk file deleted")
                } catch (e: Exception) {
                    XLog.e("TwoLevelCacheEngine.remove failed for key: $key", e)
                } finally {
                    tombstoneKeys.remove(key)
                }
            }
        }
        deleteJob.join()
    }

    /**
     * 批量安全删除 key 集合（原子从内存移除，立墓碑，并挂起等待单线程物理删除完成）
     */
    suspend fun removeBatch(keys: Collection<String>) = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext
        val keySet = keys.toSet()
        val deleteJob = mutex.withLock {
            tombstoneKeys.addAll(keySet)
            if ("0" in keySet) {
                rootCache = null
            }
            keySet.forEach { key ->
                if (key != "0") {
                    memoryCache.remove(key)
                }
            }
            // 在单线程 FIFO 调度器中批量派发物理删除任务，立墓碑后立即释放互斥锁
            scope.launch {
                try {
                    if (cacheDir == null) return@launch
                    keySet.forEach { key ->
                        val file = getDiskFile(key)
                        AtomicFile(file).delete()
                    }
                    XLog.d("TwoLevelCacheEngine.removeBatch: ${keySet.size} disk files deleted")
                } catch (e: Exception) {
                    XLog.e("TwoLevelCacheEngine.removeBatch failed for keys: $keySet", e)
                } finally {
                    tombstoneKeys.removeAll(keySet)
                }
            }
        }
        deleteJob.join()
    }

    /**
     * 仅清空内存缓存（用于模拟低内存淘汰或冷启动，保留磁盘缓存）
     */
    suspend fun clearMemoryOnly() = withContext(Dispatchers.IO) {
        mutex.withLock {
            rootCache = null
            memoryCache.clear()
        }
    }

    /**
     * 仅清空磁盘文件，保留内存中的缓存
     */
    suspend fun clearDiskOnly() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (cacheDir != null && cacheDir.exists()) {
                withContext(diskDispatcher) {
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            if (file.name.endsWith(".json")) {
                                AtomicFile(file).delete()
                            } else if (file.name.endsWith(".json.bak") || file.name.endsWith(".json.new")) {
                                file.delete()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 清空全部缓存（内存 + 磁盘）
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            rootCache = null
            memoryCache.clear()
            if (cacheDir != null && cacheDir.exists()) {
                withContext(diskDispatcher) {
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            if (file.name.endsWith(".json")) {
                                AtomicFile(file).delete()
                            } else if (file.name.endsWith(".json.bak") || file.name.endsWith(".json.new")) {
                                file.delete()
                            }
                        }
                    }
                    cacheDir.mkdirs()
                }
            }
        }
    }

    /**
     * 启动预加载：优先同步加载根目录 "0"，后台异步预加载其余磁盘缓存
     */
    suspend fun preload(onRootLoaded: ((FilesBean) -> Unit)? = null) = withContext(Dispatchers.IO) {
        if (!saveRequestCache) {
            clearDiskOnly()
            return@withContext
        }
        if (cacheDir == null || !cacheDir.exists()) return@withContext

        // 1. 优先只加载 key="0"（根目录/首页）的缓存，确保主界面快速获得数据
        // 防御性校验：若内存中已有根目录（如网络/初始化已填入最新数据）或已立墓碑，严禁以陈旧磁盘快照盲目覆写
        if (rootCache == null && !tombstoneKeys.contains("0")) {
            getDiskCache("0")?.let { diskEntry ->
                val loadedData: FilesBean? = mutex.withLock {
                    if (rootCache == null && !tombstoneKeys.contains("0")) {
                        rootCache = diskEntry
                        XLog.d("TwoLevelCacheEngine.preload: rootCache successfully preloaded from disk")
                        diskEntry.data
                    } else null
                }
                // 移出互斥锁外触发外部回调，彻底杜绝回调内部若调用引擎挂起方法引发 Mutex 非重入死锁
                loadedData?.let { onRootLoaded?.invoke(it) }
            }
        }

        // 2. 在后台异步加载其余文件及清理过期缓存，不阻塞启动流程/SplashScreen；
        // 使用 Dispatchers.IO 执行反序列化，避免长时间霸占专用于写盘/物理删除的 diskDispatcher 单线程
        scope.launch(Dispatchers.IO) {
            cacheDir.listFiles()?.let { files ->
                for (file in files) {
                    if (file.isFile && file.name.endsWith(".json")) {
                        val key = file.name.substringBeforeLast(".")
                        if (key != "0") {
                            getDiskCache(key)?.let { diskWrapper ->
                                // 墓碑拦截与原子 putIfAbsent：避免启动时后台预加载覆盖运行中产生的新缓存或复活已删除目录
                                mutex.withLock {
                                    if (!tombstoneKeys.contains(key) && getDiskFile(key).exists()) {
                                        memoryCache.putIfAbsent(key, diskWrapper)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取当前磁盘上存在的全部有效 key 集合（排除临时/备份文件）
     */
    suspend fun getAllDiskKeys(): Set<String> = withContext(Dispatchers.IO) {
        if (cacheDir == null || !cacheDir.exists()) return@withContext emptySet()
        cacheDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".json.bak")) }
            ?.map { it.name.removeSuffix(".bak").substringBeforeLast(".") }
            ?.toSet() ?: emptySet()
    }

    /**
     * 动态更新配置（支持容量缩容主动淘汰与磁盘开关联动）
     */
    suspend fun updateConfig(maxMemoryDirs: Int? = null, saveRequestCache: Boolean? = null) =
        withContext(Dispatchers.IO) {
            if (saveRequestCache != null) {
                this@TwoLevelCacheEngine.saveRequestCache = saveRequestCache
            }
            if (maxMemoryDirs != null && maxMemoryDirs > 0) {
                mutex.withLock {
                    maxCapacity = maxMemoryDirs
                    trimToSize(maxMemoryDirs)
                }
            }
        }

    /**
     * 针对普通目录 LRU 进行容量裁剪（绝不影响 rootCache）
     */
    fun trimToSize(maxSize: Int) {
        synchronized(memoryCache) {
            val iterator = memoryCache.entries.iterator()
            while (memoryCache.size > maxSize && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    fun getMaxMemoryDirs(): Int = maxCapacity

    override fun close() {
        scope.cancel()
        executor.shutdown()
    }

    // ---------------- 内部 IO 工具方法 ----------------

    private fun flushToDiskAsync(key: String, json: String) {
        if (!saveRequestCache || cacheDir == null) return
        scope.launch {
            try {
                val file = getDiskFile(key)
                val atomicFile = AtomicFile(file)
                val stream = atomicFile.startWrite()
                try {
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    atomicFile.finishWrite(stream)
                } catch (e: Exception) {
                    atomicFile.failWrite(stream)
                    throw e
                }
            } catch (e: Exception) {
                XLog.e("TwoLevelCacheEngine.flushToDiskAsync failed for key: $key", e)
            }
        }
    }

    private fun deleteDiskFileSync(key: String) {
        if (cacheDir == null) return
        try {
            val file = getDiskFile(key)
            AtomicFile(file).delete()
            XLog.d("TwoLevelCacheEngine.deleteDiskFileSync: key=$key deleted")
        } catch (e: Exception) {
            XLog.e("TwoLevelCacheEngine.deleteDiskFileSync failed for key: $key", e)
        }
    }

    private fun getDiskFile(key: String): File = File(cacheDir ?: File("."), "${key}.json")
}
