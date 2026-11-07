package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.worker.UnzipQueueManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * TwoLevelCacheEngine 存储引擎专项单元测试集合
 * 验证：
 * 1. 根目录常驻保护（Root Pinning 永不被 LRU 淘汰）
 * 2. 普通目录 LRU 淘汰与动态 trimToSize 缩容
 * 3. 前置序列化彻底杜绝并发修改异常（CME 防护验证）
 * 4. 墓碑机制与物理删除时序一致性（杜绝旧快照复活）
 * 5. 单 Key 原地原子 update 机制
 * 6. 批量删除 removeBatch 与磁盘清理
 * 7. Engine 生命周期管控（Closeable 与线程池释放）
 */
class TwoLevelCacheEngineTest {

    private lateinit var tempDir: File
    private lateinit var engine: TwoLevelCacheEngine

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        UnzipQueueManager.clear()
        tempDir = Files.createTempDirectory("tlce_test").toFile()
        engine = TwoLevelCacheEngine(
            cacheDir = tempDir,
            maxMemoryDirs = 3,
            saveRequestCacheInitial = true
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            engine.clearAll()
        }
        engine.close()
        tempDir.deleteRecursively()
    }

    private fun createDummyFiles(cid: String, name: String): FilesBean {
        val file = FileBean(fileId = "f_$cid", name = name, isFolder = false)
        return FilesBean(
            fileBeanList = arrayListOf(file),
            cid = cid,
            count = 1,
            order = "",
            path = listOf(PathBean(cid = cid, name = name, pid = "0"))
        )
    }

    @Test
    fun testRootPinningNeverEvictedByLru() = runBlocking {
        // 1. 写入根目录 "0"
        val rootFiles = createDummyFiles("0", "根目录")
        engine.put("0", rootFiles)
        assertTrue(engine.containsMemory("0"))

        // 2. 连续写入 5 个普通目录（容量仅为 3）
        for (i in 1..5) {
            engine.put("dir_$i", createDummyFiles("dir_$i", "目录$i"))
        }

        // 3. 验证根目录 "0" 依然常驻在内存中，未被淘汰
        assertTrue("根目录必须常驻内存，严禁被普通 LRU 淘汰", engine.containsMemory("0"))
        assertEquals("根目录", engine.peekMemory("0")?.path?.firstOrNull()?.name)

        // 4. 验证普通目录中较老的已按 LRU 淘汰（dir_1, dir_2 应已不在内存）
        assertFalse("最老普通目录 dir_1 应被内存淘汰", engine.containsMemory("dir_1"))
        assertFalse("次老普通目录 dir_2 应被内存淘汰", engine.containsMemory("dir_2"))
        assertTrue("最新写入的普通目录 dir_5 必须在内存中", engine.containsMemory("dir_5"))
    }

    @Test
    fun testDynamicTrimToSize() = runBlocking {
        // 容量为 3，写入 3 个普通目录
        engine.put("d1", createDummyFiles("d1", "d1"))
        engine.put("d2", createDummyFiles("d2", "d2"))
        engine.put("d3", createDummyFiles("d3", "d3"))

        assertTrue(engine.containsMemory("d1"))
        assertTrue(engine.containsMemory("d2"))
        assertTrue(engine.containsMemory("d3"))

        // 动态将容量调小为 1
        engine.updateConfig(maxMemoryDirs = 1)
        assertEquals(1, engine.getMaxMemoryDirs())

        // 验证多余目录被淘汰，仅保留最新访问的 d3
        assertFalse("d1 应在缩容时被淘汰", engine.containsMemory("d1"))
        assertFalse("d2 应在缩容时被淘汰", engine.containsMemory("d2"))
        assertTrue("d3 应保留", engine.containsMemory("d3"))
    }

    @Test
    fun testPreSerializationPreventsConcurrentModificationException() = runBlocking {
        val largeList = ArrayList<FileBean>()
        for (i in 1..500) {
            largeList.add(FileBean(fileId = "f_$i", name = "file_$i.txt", isFolder = false))
        }
        val files = FilesBean(
            fileBeanList = largeList,
            cid = "large_cid",
            count = largeList.size,
            order = "",
            path = emptyList()
        )

        engine.put("large_cid", files)

        // 并发进行极高频修改与落盘更新，验证绝不抛出 ConcurrentModificationException
        val jobs = List(10) { index ->
            launch(Dispatchers.Default) {
                for (j in 1..50) {
                    engine.update("large_cid") { current ->
                        val copyList = ArrayList(current.fileBeanList)
                        copyList.add(FileBean(fileId = "new_${index}_$j", name = "concurrent.txt"))
                        current.copy(fileBeanList = copyList, count = copyList.size)
                    }
                }
            }
        }

        jobs.forEach { it.join() }

        val finalResult = engine.get("large_cid")
        assertNotNull(finalResult)
        assertEquals(500 + 10 * 50, finalResult?.count)
    }

    @Test
    fun testAtomicUpdate() = runBlocking {
        val files = createDummyFiles("u1", "old_name")
        engine.put("u1", files)

        val updated = engine.update("u1") { current ->
            val list = ArrayList(current.fileBeanList)
            list[0] = list[0].copy(name = "updated_name")
            current.copy(fileBeanList = list)
        }

        assertNotNull(updated)
        assertEquals("updated_name", engine.peekMemory("u1")?.fileBeanList?.firstOrNull()?.name)

        // 等待异步落盘完成后验证磁盘
        delay(100)
        val fromDisk = engine.getDiskCache("u1")
        assertEquals("updated_name", fromDisk?.data?.fileBeanList?.firstOrNull()?.name)
    }

    @Test
    fun testRemoveAndTombstone() = runBlocking {
        val files = createDummyFiles("rem_1", "rem_name")
        engine.put("rem_1", files)
        delay(100)

        val diskFile = File(tempDir, "rem_1.json")
        assertTrue(diskFile.exists())

        // 执行物理删除
        engine.remove("rem_1")

        assertFalse(engine.containsMemory("rem_1"))
        assertFalse(diskFile.exists())
        assertNull("删除后读取必须返回 null", engine.get("rem_1"))
    }

    @Test
    fun testRemoveBatch() = runBlocking {
        engine.put("b1", createDummyFiles("b1", "b1"))
        engine.put("b2", createDummyFiles("b2", "b2"))
        engine.put("b3", createDummyFiles("b3", "b3"))
        delay(100)

        assertTrue(File(tempDir, "b1.json").exists())
        assertTrue(File(tempDir, "b2.json").exists())
        assertTrue(File(tempDir, "b3.json").exists())

        engine.removeBatch(listOf("b1", "b3"))

        assertFalse(engine.containsMemory("b1"))
        assertTrue(engine.containsMemory("b2"))
        assertFalse(engine.containsMemory("b3"))

        assertFalse(File(tempDir, "b1.json").exists())
        assertTrue(File(tempDir, "b2.json").exists())
        assertFalse(File(tempDir, "b3.json").exists())
    }

    @Test
    fun testSaveRequestCacheImmediateEffect() = runBlocking {
        engine.updateConfig(saveRequestCache = false)
        assertFalse(engine.saveRequestCache)

        engine.put("no_disk", createDummyFiles("no_disk", "no_disk"))
        delay(100)

        // 验证禁止落盘时磁盘未生成文件
        val diskFile = File(tempDir, "no_disk.json")
        assertFalse("关闭落盘时磁盘不应生成文件", diskFile.exists())
        assertTrue("但内存中依然有效", engine.containsMemory("no_disk"))
    }

    @Test
    fun testConcurrentRemoveAndPutMaintainsStrictOrder() = runBlocking {
        for (round in 1..20) {
            val key = "race_key_$round"
            val initial = createDummyFiles(key, "initial_$round")
            engine.put(key, initial)

            // 并发启动 remove 与 put
            val removeJob = launch(Dispatchers.Default) {
                engine.remove(key)
            }
            val putJob = launch(Dispatchers.Default) {
                delay(1)
                engine.put(key, createDummyFiles(key, "new_$round"))
            }

            removeJob.join()
            putJob.join()

            // 无论哪个最后执行，两者绝对是严格串行的；
            // 若 put 最后执行，则最终状态为 new_$round 且文件存在
            // 若 remove 最后执行，则最终状态为 null 且文件不存在
            // 绝不允许出现：内存中为 new_$round，但磁盘被后滞的物理删除任务误删
            delay(50)
            val memoryData = engine.peekMemory(key)
            val diskFile = File(tempDir, "$key.json")
            if (memoryData != null) {
                assertEquals("new_$round", memoryData.path.firstOrNull()?.name)
                assertTrue("内存有最新值时磁盘必须存在对应文件，严禁被滞后的物理删除误删", diskFile.exists())
            } else {
                assertFalse("内存无值时磁盘文件必须已被删除", diskFile.exists())
            }
        }
    }

    @Test
    fun testPreloadDoesNotResurrectTombstonedKeys() = runBlocking {
        // 准备磁盘文件
        val file1 = File(tempDir, "t1.json")
        val file2 = File(tempDir, "t2.json")
        engine.put("t1", createDummyFiles("t1", "t1"))
        engine.put("t2", createDummyFiles("t2", "t2"))
        delay(100)
        assertTrue(file1.exists())
        assertTrue(file2.exists())

        // 清空内存
        engine.clearMemoryOnly()
        assertFalse(engine.containsMemory("t1"))
        assertFalse(engine.containsMemory("t2"))

        // 执行删除 t1
        engine.remove("t1")
        assertFalse(file1.exists())

        // 启动预加载
        engine.preload()
        delay(100)

        assertFalse("已删除的 t1 绝不能在 preload 中复活", engine.containsMemory("t1"))
        assertTrue("未删除的 t2 应当被预加载", engine.containsMemory("t2"))
    }

    @Test
    fun testAtomicFileCrashRecoveryAndCleanup() = runBlocking {
        val key = "crash_recovery_key"
        engine.put(key, createDummyFiles(key, "good_snapshot"))
        delay(100)

        val jsonFile = File(tempDir, "$key.json")
        val bakFile = File(tempDir, "$key.json.bak")
        val newFile = File(tempDir, "$key.json.new")
        assertTrue("原始文件应已持久化落盘", jsonFile.exists())

        // 模拟写入中途进程强杀场景：
        // 原始文件被重命名为 .bak，当前文件残留半截损坏数据
        jsonFile.renameTo(bakFile)
        jsonFile.writeText("{ broken partial json content...")

        // 清空内存，强制触发磁盘回源
        engine.clearMemoryOnly()
        assertFalse(engine.containsMemory(key))

        // 通过 AtomicFile 机制读取：应自动利用 .bak 恢复完整快照
        val recovered = engine.get(key)
        assertNotNull("遭遇写入中断与损坏文件时，必须能够从 .bak 备份中恢复", recovered)
        assertEquals("good_snapshot", recovered?.path?.firstOrNull()?.name)

        // 验证删除时通过 AtomicFile.delete() 彻底清理所有关联文件（包括 .bak 与 .new）
        // 先手动创建一个 .new 临时残留文件
        newFile.writeText("temporary leftover")
        assertTrue(newFile.exists())

        engine.remove(key)
        assertFalse("主文件必须已被删除", jsonFile.exists())
        assertFalse(".bak 备份文件必须已被删除", bakFile.exists())
        assertFalse(".new 临时文件必须已被彻底清理", newFile.exists())
    }

    @Test
    fun testCoroutineCancellationDoesNotDeleteDiskCache() = runBlocking {
        val key = "cancel_test_key"
        engine.put(key, createDummyFiles(key, "cancel_data"))
        delay(100)

        val diskFile = File(tempDir, "$key.json")
        assertTrue("写入后磁盘必须存在对应文件", diskFile.exists())

        // 验证捕获 CancellationException 场景：模拟协程读取中途被取消
        try {
            kotlinx.coroutines.coroutineScope {
                val readJob = async(Dispatchers.IO) {
                    // 读取磁盘并模拟 CancellationException
                    engine.getDiskCache(key)
                    throw CancellationException("Simulated job cancellation")
                }
                readJob.await()
            }
        } catch (_: CancellationException) {}

        // 验证磁盘文件绝未因协程取消而被误删
        assertTrue("协程取消时，合法的磁盘缓存文件必须完好保留，严禁误删", diskFile.exists())
        val data = engine.get(key)
        assertNotNull(data)
        assertEquals("cancel_data", data?.path?.firstOrNull()?.name)
    }

    @Test
    fun testPreloadDoesNotOverwriteExistingRootMemoryCache() = runBlocking {
        // 1. 模拟磁盘上存在旧的根目录快照
        engine.put("0", createDummyFiles("0", "old_root_on_disk"))
        delay(100)
        val diskFile = File(tempDir, "0.json")
        assertTrue(diskFile.exists())

        // 2. 模拟内存中已通过网络或者业务层写入了最新的根目录数据
        val freshRoot = createDummyFiles("0", "fresh_root_in_memory")
        engine.put("0", freshRoot)

        // 3. 此时触发 preload 启动预加载
        var rootLoadedInvoked = false
        engine.preload {
            rootLoadedInvoked = true
        }
        delay(100)

        // 4. 验证内存中的根目录未被旧快照覆盖
        assertEquals(
            "内存中已有的最新根目录严禁被磁盘旧快照覆写",
            "fresh_root_in_memory",
            engine.peekMemory("0")?.path?.firstOrNull()?.name
        )
        assertFalse("内存已有根目录时，preload 不应重复触发 onRootLoaded 回调", rootLoadedInvoked)
    }

    @Test
    fun testImmediateEffectOfSaveRequestCacheSetter() = runBlocking {
        // 验证同步 setter 立即生效
        engine.saveRequestCache = false
        assertFalse("saveRequestCache setter 必须同步立即生效", engine.saveRequestCache)

        engine.put("no_disk_setter", createDummyFiles("no_disk_setter", "test"))
        delay(50)
        assertFalse("禁止落盘时磁盘不应生成文件", File(tempDir, "no_disk_setter.json").exists())

        engine.saveRequestCache = true
        assertTrue("saveRequestCache setter 开启时必须同步立即生效", engine.saveRequestCache)

        engine.put("disk_setter", createDummyFiles("disk_setter", "test"))
        delay(100)
        assertTrue("开启落盘时磁盘应正常生成文件", File(tempDir, "disk_setter.json").exists())
    }

    @Test
    fun testConcurrentPreloadAndRootPutMaintainsFreshData() = runBlocking {
        for (round in 1..10) {
            // 准备磁盘上的旧根目录
            engine.put("0", createDummyFiles("0", "old_disk_root_$round"))
            delay(50)
            engine.clearMemoryOnly()
            assertFalse(engine.containsMemory("0"))

            // 并发触发 preload 与网络写入 put("0", fresh)
            val preloadJob = launch(Dispatchers.Default) {
                engine.preload()
            }
            val freshPutJob = launch(Dispatchers.Default) {
                delay(1)
                engine.put("0", createDummyFiles("0", "fresh_network_root_$round"))
            }

            preloadJob.join()
            freshPutJob.join()

            // 无论时序如何，由于互斥锁与双重校验，最终内存与磁盘中必须是最新数据，严禁被旧快照覆写
            val finalRoot = engine.peekMemory("0")
            assertNotNull(finalRoot)
            assertEquals("fresh_network_root_$round", finalRoot?.path?.firstOrNull()?.name)
        }
    }

    @Test
    fun testPreloadCallbackDoesNotDeadlockWhenAccessingEngine() = runBlocking {
        // 1. 准备磁盘根目录缓存
        engine.put("0", createDummyFiles("0", "root_for_callback_test"))
        delay(100)
        engine.clearMemoryOnly()
        assertFalse(engine.containsMemory("0"))

        var callbackExecuted = false
        var nestedGetSuccess = false

        // 2. 触发预加载并在回调中嵌套调用挂起读写方法（若在 mutex 锁内执行则会引发 Mutex 非重入死锁）
        engine.preload { rootData ->
            callbackExecuted = true
            // 在回调内部嵌套调用 engine.get("0")，验证锁已释放，绝不发生自我死锁
            runBlocking {
                val data = engine.get("0")
                if (data != null && data.cid == "0") {
                    nestedGetSuccess = true
                }
            }
        }

        assertTrue("preload 应当成功触发 onRootLoaded 回调", callbackExecuted)
        assertTrue("onRootLoaded 回调在锁外执行，嵌套调用挂起方法严禁死锁", nestedGetSuccess)
    }

    @Test
    fun testRemoveWhenCacheDirIsNullDoesNotTouchDisk() = runBlocking {
        // 构造纯内存运行的 Engine（cacheDir = null）
        val memoryOnlyEngine = TwoLevelCacheEngine(
            cacheDir = null,
            maxMemoryDirs = 5,
            saveRequestCacheInitial = true
        )

        try {
            memoryOnlyEngine.put("m1", createDummyFiles("m1", "mem_only_1"))
            memoryOnlyEngine.put("m2", createDummyFiles("m2", "mem_only_2"))
            memoryOnlyEngine.put("0", createDummyFiles("0", "mem_only_root"))

            assertTrue(memoryOnlyEngine.containsMemory("m1"))
            assertTrue(memoryOnlyEngine.containsMemory("m2"))
            assertTrue(memoryOnlyEngine.containsMemory("0"))

            // 单项删除与批量删除：cacheDir == null 必须安全快速执行，严禁抛异常或触碰相对路径
            memoryOnlyEngine.remove("m1")
            assertFalse(memoryOnlyEngine.containsMemory("m1"))

            memoryOnlyEngine.removeBatch(listOf("m2", "0"))
            assertFalse(memoryOnlyEngine.containsMemory("m2"))
            assertFalse(memoryOnlyEngine.containsMemory("0"))
        } finally {
            memoryOnlyEngine.close()
        }
    }
}
