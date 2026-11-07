package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.VideoBean
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * FileCacheManager 单元测试集合
 * 验证：
 * 1. renameItem、removeItem、removeItems 的原子操作与状态维护
 * 2. 文件夹重命名时子目录面包屑末级同步
 * 3. 递归删除子孙目录 (removeFolderRecursively)
 * 4. 视频播放进度批量计算与更新 (updateVideoProgress)
 * 5. CacheRollback 撤销恢复机制
 * 6. CacheEvent (LocalUiUpdated / RemoteRefreshRequired / FolderDeleted / AllCleared) 响应式事件发射
 */
class FileCacheManagerTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        XLog.init()
        tempDir = Files.createTempDirectory("fcm_test").toFile()
        FileCacheManager.init(tempDir)
        FileCacheManager.saveRequestCache = true
    }

    @After
    fun tearDown() {
        runBlocking {
            FileCacheManager.clearAll()
        }
        tempDir.deleteRecursively()
    }

    @Test
    fun testRenameFileAndRollback() = runBlocking {
        val file = FileBean(fileId = "f1", name = "old.txt", isFolder = false)
        val parentFiles = FilesBean(
            fileBeanList = arrayListOf(file),
            cid = "0",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "0", name = "根目录", pid = "0"))
        )
        FileCacheManager.put("0", parentFiles)

        var lastEvent: CacheEvent? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect { lastEvent = it }
        }

        // 1. 重命名文件
        val rollback = FileCacheManager.renameItem("0", "f1", "new.txt")
        assertNotNull(rollback)
        val updated = FileCacheManager.getDate("0")?.fileBeanList?.firstOrNull()
        assertEquals("new.txt", updated?.name)
        assertTrue(lastEvent is CacheEvent.LocalUiUpdated && (lastEvent as CacheEvent.LocalUiUpdated).cid == "0")

        // 2. 执行回滚
        rollback?.rollback()
        val restored = FileCacheManager.getDate("0")?.fileBeanList?.firstOrNull()
        assertEquals("old.txt", restored?.name)

        job.cancel()
    }

    @Test
    fun testRenameFolderSynchronizesBreadcrumb() = runBlocking {
        val folder = FileBean(fileId = "c100", categoryId = "c100", name = "旧目录", isFolder = true)
        val parentFiles = FilesBean(
            fileBeanList = arrayListOf(folder),
            cid = "0",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "0", name = "根目录", pid = "0"))
        )
        val folderSelf = FilesBean(
            fileBeanList = arrayListOf(),
            cid = "c100",
            count = 0,
            order = "",
            path = listOf(
                PathBean(cid = "0", name = "根目录", pid = "0"),
                PathBean(cid = "c100", name = "旧目录", pid = "0")
            )
        )
        FileCacheManager.put("0", parentFiles)
        FileCacheManager.put("c100", folderSelf)

        // 重命名目录
        val rollback = FileCacheManager.renameItem("0", "c100", "新目录")
        assertNotNull(rollback)

        // 验证父目录中的条目更新
        assertEquals("新目录", FileCacheManager.getDate("0")?.fileBeanList?.firstOrNull()?.name)

        // 验证子目录自身缓存内的面包屑末级同步更新
        val breadcrumbLast = FileCacheManager.getDate("c100")?.path?.lastOrNull()?.name
        assertEquals("新目录", breadcrumbLast)

        // 测试回滚
        rollback?.rollback()
        assertEquals("旧目录", FileCacheManager.getDate("0")?.fileBeanList?.firstOrNull()?.name)
        assertEquals("旧目录", FileCacheManager.getDate("c100")?.path?.lastOrNull()?.name)
    }

    @Test
    fun testRemoveItemAndRollback() = runBlocking {
        val f1 = FileBean(fileId = "10", name = "10.txt", isFolder = false)
        val f2 = FileBean(fileId = "20", name = "20.txt", isFolder = false)
        val parentFiles = FilesBean(
            fileBeanList = arrayListOf(f1, f2),
            cid = "0",
            count = 2,
            order = "",
            path = emptyList()
        )
        FileCacheManager.put("0", parentFiles)

        val rollback = FileCacheManager.removeItem("0", "10", isFolder = false)
        assertNotNull(rollback)

        val cacheAfterRemove = FileCacheManager.getDate("0")
        assertEquals(1, cacheAfterRemove?.count)
        assertEquals(1, cacheAfterRemove?.fileBeanList?.size)
        assertEquals("20.txt", cacheAfterRemove?.fileBeanList?.first()?.name)

        // 回滚
        rollback?.rollback()
        val cacheAfterRollback = FileCacheManager.getDate("0")
        assertEquals(2, cacheAfterRollback?.count)
        assertEquals(2, cacheAfterRollback?.fileBeanList?.size)
        assertEquals("10.txt", cacheAfterRollback?.fileBeanList?.first()?.name)
    }

    @Test
    fun testRemoveItemsBatch() = runBlocking {
        val f1 = FileBean(fileId = "1", name = "1.txt", isFolder = false)
        val f2 = FileBean(fileId = "2", name = "2.txt", isFolder = false)
        val f3 = FileBean(fileId = "3", name = "3.txt", isFolder = false)
        val parentFiles = FilesBean(
            fileBeanList = arrayListOf(f1, f2, f3),
            cid = "0",
            count = 3,
            order = "",
            path = emptyList()
        )
        FileCacheManager.put("0", parentFiles)

        val rollback = FileCacheManager.removeItems("0", listOf("1", "3"))
        assertNotNull(rollback)

        val cache = FileCacheManager.getDate("0")
        assertEquals(1, cache?.count)
        assertEquals(listOf("2.txt"), cache?.fileBeanList?.map { it.name })

        // 回滚批量删除
        rollback?.rollback()
        val restored = FileCacheManager.getDate("0")
        assertEquals(3, restored?.count)
        assertEquals(listOf("1.txt", "2.txt", "3.txt"), restored?.fileBeanList?.map { it.name })
    }

    @Test
    fun testRemoveFolderRecursively() = runBlocking {
        // 构建树：root (0) -> sub1 (100) -> sub2 (200)
        val sub2 = FileBean(fileId = "200", categoryId = "200", name = "sub2", isFolder = true)
        val sub1 = FileBean(fileId = "100", categoryId = "100", name = "sub1", isFolder = true)

        FileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(sub2), cid = "100", count = 1, order = "", path = emptyList()))
        FileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = emptyList()))

        assertTrue(FileCacheManager.containsKey("100"))
        assertTrue(FileCacheManager.containsKey("200"))

        // 递归删除 sub1 (100)
        FileCacheManager.removeFolderRecursively("100")

        assertFalse(FileCacheManager.containsKey("100"))
        assertFalse(FileCacheManager.containsKey("200"))
    }

    @Test
    fun testRemoveFolderRecursively_SilentModeDoesNotEmitFolderDeleted() = runBlocking {
        val folder = FileBean(fileId = "100", categoryId = "100", name = "sub1", isFolder = true)
        FileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(folder), cid = "100", count = 1, order = "", path = emptyList()))

        var receivedFolderDeleted: CacheEvent.FolderDeleted? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect {
                if (it is CacheEvent.FolderDeleted) {
                    receivedFolderDeleted = it
                }
            }
        }

        // 静默模式清理缓存
        FileCacheManager.removeFolderRecursively("100", emitFolderDeleted = false)

        assertFalse(FileCacheManager.containsKey("100"))
        // 验证静默模式下未向外发射 FolderDeleted
        assertTrue(receivedFolderDeleted == null)

        job.cancel()
    }

    @Test
    fun testRemoveFolderRecursively_DefaultEmitsFolderDeleted() = runBlocking {
        val folder = FileBean(fileId = "100", categoryId = "100", name = "sub1", isFolder = true)
        FileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(folder), cid = "100", count = 1, order = "", path = emptyList()))

        var receivedFolderDeleted: CacheEvent.FolderDeleted? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect {
                if (it is CacheEvent.FolderDeleted) {
                    receivedFolderDeleted = it
                }
            }
        }

        // 默认模式（真实删除）清理缓存
        FileCacheManager.removeFolderRecursively("100", emitFolderDeleted = true)

        assertFalse(FileCacheManager.containsKey("100"))
        // 验证向外广播了 FolderDeleted
        assertEquals("100", receivedFolderDeleted?.folderCid)

        job.cancel()
    }

    @Test
    fun testRemoveFolderRecursively_RootDirectoryNeverEmitsFolderDeleted() = runBlocking {
        FileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        var receivedFolderDeleted: CacheEvent.FolderDeleted? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect {
                if (it is CacheEvent.FolderDeleted) {
                    receivedFolderDeleted = it
                }
            }
        }

        // 针对根目录执行递归清理，即便 emitFolderDeleted = true 也绝对不应发射 FolderDeleted("0")
        FileCacheManager.removeFolderRecursively("0", emitFolderDeleted = true)

        assertFalse(FileCacheManager.containsKey("0"))
        assertTrue(receivedFolderDeleted == null)

        job.cancel()
    }

    @Test
    fun testRemoveFolderRecursively_CycleProtectionPreventsInfiniteRecursion() = runBlocking {
        // 构建成环依赖：subA (100) 引用 subB (200)，subB (200) 又反向引用 subA (100)
        val toB = FileBean(fileId = "200", categoryId = "200", name = "subB", isFolder = true)
        val toA = FileBean(fileId = "100", categoryId = "100", name = "subA", isFolder = true)

        FileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(toB), cid = "100", count = 1, order = "", path = emptyList()))
        FileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(toA), cid = "200", count = 1, order = "", path = emptyList()))

        // 执行清理，验证依靠 visited 集合能正常退出而不发生 StackOverflowError
        FileCacheManager.removeFolderRecursively("100")

        assertFalse(FileCacheManager.containsKey("100"))
        assertFalse(FileCacheManager.containsKey("200"))
    }


    @Test
    fun testUpdateVideoProgress() = runBlocking {
        val v1 = FileBean(
            fileId = "v1",
            pickCode = "pick_v1",
            name = "movie.mp4",
            isVideo = 1,
            playLong = 100.0,
            playLongRatio = ""
        )
        val v2 = FileBean(
            fileId = "v2",
            pickCode = "pick_v2",
            name = "doc.txt",
            isVideo = 0,
            playLong = 0.0,
            playLongRatio = ""
        )
        FileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(v1, v2), cid = "0", count = 2, order = "", path = emptyList()))

        val videoHistoryMap = mapOf(
            "pick_v1" to VideoBean(currentDuration = 50, pickCode = "pick_v1"),
            "pick_v2" to VideoBean(currentDuration = 20, pickCode = "pick_v2")
        )
        XLog.init()
        FileCacheManager.updateVideoProgress("0", videoHistoryMap)

        val updatedList = FileCacheManager.getDate("0")?.fileBeanList
        val updatedV1 = updatedList?.first { it.fileId == "v1" }
        val updatedV2 = updatedList?.first { it.fileId == "v2" }

        // 50 / 100 = 50%
        assertEquals("▶️ 50%", updatedV1?.playLongRatio)
        // 非视频文件保持原样
        assertEquals("", updatedV2?.playLongRatio)
    }

    @Test
    fun testNotifyRemoteRefresh_ClearsCacheAndEmitsEvent() = runBlocking {
        val fb = FilesBean(fileBeanList = arrayListOf(), cid = "123", count = 0, order = "", path = emptyList())
        FileCacheManager.put("123", fb)
        assertTrue(FileCacheManager.containsKey("123"))

        var receivedEvent: CacheEvent? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect {
                receivedEvent = it
            }
        }

        FileCacheManager.notifyRemoteRefresh("123")

        assertFalse(FileCacheManager.containsKey("123"))
        assertTrue(receivedEvent is CacheEvent.RemoteRefreshRequired && (receivedEvent as CacheEvent.RemoteRefreshRequired).cid == "123")

        job.cancel()
    }

    @Test
    fun testNotifyLocalUpdated_EmitsEvent() = runBlocking {
        var receivedEvent: CacheEvent? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect {
                receivedEvent = it
            }
        }

        FileCacheManager.notifyLocalUpdated("123")

        assertTrue(receivedEvent is CacheEvent.LocalUiUpdated && (receivedEvent as CacheEvent.LocalUiUpdated).cid == "123")

        job.cancel()
    }

    @Test
    fun testNotifyCacheEvent_EmitsCustomEvent() = runBlocking {
        var receivedEvent: CacheEvent? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect {
                receivedEvent = it
            }
        }

        FileCacheManager.notifyCacheEvent(CacheEvent.AllCleared)

        assertEquals(CacheEvent.AllCleared, receivedEvent)

        job.cancel()
    }

    @Test
    fun testRemoveAndGet_NeverRevivesOldSnapshot() = runBlocking {
        val file = FileBean(fileId = "f_snap", name = "snap.txt", isFolder = false)
        val files = FilesBean(
            fileBeanList = arrayListOf(file),
            cid = "snap_cid",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "snap_cid", name = "快照目录", pid = "0"))
        )
        // 1. 写入并排队落盘
        FileCacheManager.put("snap_cid", files)
        assertTrue(FileCacheManager.containsKey("snap_cid"))

        // 等待单线程落盘完成并验证磁盘文件存在
        kotlinx.coroutines.delay(100)
        val diskFile = File(tempDir, "snap_cid.json")
        assertTrue("磁盘缓存文件必须存在", diskFile.exists())

        // 2. 调用 remove 删除
        FileCacheManager.remove("snap_cid")

        // 3. 验证内存被清空，且物理磁盘文件必定已被同步删除
        assertFalse("内存缓存必须已移除", FileCacheManager.containsKey("snap_cid"))
        assertFalse("磁盘缓存文件必须已同步物理删除", diskFile.exists())

        // 4. 紧接着调用 get("snap_cid")，验证绝对不从磁盘复活旧快照，返回 null
        val fetched = FileCacheManager.get("snap_cid")
        assertNull("删除后读取必须返回 null，严禁复活旧快照", fetched)
        assertFalse(FileCacheManager.containsKey("snap_cid"))
    }

    @Test
    fun testNotifyRemoteRefresh_EnsuresDiskDeletedBeforeEvent() = runBlocking {
        val file = FileBean(fileId = "f_rem", name = "remote.txt", isFolder = false)
        val files = FilesBean(
            fileBeanList = arrayListOf(file),
            cid = "rem_cid",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "rem_cid", name = "远端目录", pid = "0"))
        )
        FileCacheManager.put("rem_cid", files)
        kotlinx.coroutines.delay(100)
        val diskFile = File(tempDir, "rem_cid.json")
        assertTrue(diskFile.exists())

        var diskExistedWhenEventReceived = true
        var receivedEvent: CacheEvent? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect { event ->
                if (event is CacheEvent.RemoteRefreshRequired && event.cid == "rem_cid") {
                    receivedEvent = event
                    diskExistedWhenEventReceived = diskFile.exists()
                }
            }
        }

        FileCacheManager.notifyRemoteRefresh("rem_cid")

        assertNotNull(receivedEvent)
        assertFalse("广播 RemoteRefreshRequired 事件时，磁盘旧文件必须已经被物理删除", diskExistedWhenEventReceived)
        assertFalse("此时磁盘物理文件不存在", diskFile.exists())

        job.cancel()
    }

    @Test
    fun testRenameItemsAndRollback() = runBlocking {
        val file1 = FileBean(fileId = "f1", name = "file1.txt", isFolder = false)
        val file2 = FileBean(fileId = "f2", name = "file2.txt", isFolder = false)
        val folder = FileBean(fileId = "c100", categoryId = "c100", name = "旧目录", isFolder = true)
        val parentFiles = FilesBean(
            fileBeanList = arrayListOf(file1, file2, folder),
            cid = "0",
            count = 3,
            order = "",
            path = listOf(PathBean(cid = "0", name = "根目录", pid = "0"))
        )
        val folderSelf = FilesBean(
            fileBeanList = arrayListOf(),
            cid = "c100",
            count = 0,
            order = "",
            path = listOf(
                PathBean(cid = "0", name = "根目录", pid = "0"),
                PathBean(cid = "c100", name = "旧目录", pid = "0")
            )
        )
        FileCacheManager.put("0", parentFiles)
        FileCacheManager.put("c100", folderSelf)

        val events = mutableListOf<CacheEvent>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            FileCacheManager.cacheEvents.collect { events.add(it) }
        }

        // 批量重命名文件与文件夹
        val renameMap = mapOf(
            "f1" to "file1_renamed.txt",
            "c100" to "新目录"
        )
        val rollback = FileCacheManager.renameItems("0", renameMap)
        assertNotNull(rollback)

        // 验证父目录中的条目更新
        val updatedList = FileCacheManager.getDate("0")?.fileBeanList
        assertEquals("file1_renamed.txt", updatedList?.find { it.fileId == "f1" }?.name)
        assertEquals("file2.txt", updatedList?.find { it.fileId == "f2" }?.name)
        assertEquals("新目录", updatedList?.find { it.fileId == "c100" }?.name)

        // 验证子目录自身面包屑同步更新
        assertEquals("新目录", FileCacheManager.getDate("c100")?.path?.lastOrNull()?.name)

        // 验证事件广播：父目录 1 次 LocalUiUpdated，子目录 1 次 LocalUiUpdated
        assertTrue(events.any { it is CacheEvent.LocalUiUpdated && it.cid == "0" })
        assertTrue(events.any { it is CacheEvent.LocalUiUpdated && it.cid == "c100" })

        // 测试原子回滚
        rollback?.rollback()
        val restoredList = FileCacheManager.getDate("0")?.fileBeanList
        assertEquals("file1.txt", restoredList?.find { it.fileId == "f1" }?.name)
        assertEquals("旧目录", restoredList?.find { it.fileId == "c100" }?.name)
        assertEquals("旧目录", FileCacheManager.getDate("c100")?.path?.lastOrNull()?.name)

        job.cancel()
    }

    @Test
    fun testRenameItemsNoChangesReturnsNull() = runBlocking {
        val file1 = FileBean(fileId = "f1", name = "file1.txt", isFolder = false)
        val parentFiles = FilesBean(
            fileBeanList = arrayListOf(file1),
            cid = "0",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "0", name = "根目录", pid = "0"))
        )
        FileCacheManager.put("0", parentFiles)

        // 同名或空映射
        val rollbackEmpty = FileCacheManager.renameItems("0", emptyMap())
        assertNull(rollbackEmpty)

        val rollbackSame = FileCacheManager.renameItems("0", mapOf("f1" to "file1.txt"))
        assertNull(rollbackSame)
    }

    @Test
    fun testDeleteOrphansAbortsWhenRootNotLoaded() = runBlocking {
        // 准备一个非根目录的缓存文件
        val file1 = FileBean(fileId = "sub_f1", name = "sub.txt", isFolder = false)
        val subFiles = FilesBean(
            fileBeanList = arrayListOf(file1),
            cid = "c100",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "c100", name = "子目录", pid = "0"))
        )
        FileCacheManager.put("c100", subFiles)
        kotlinx.coroutines.delay(100)

        val diskFile = File(tempDir, "c100.json")
        assertTrue(diskFile.exists())

        // 此时根目录 "0" 根本未被写入或加载
        assertNull(FileCacheManager["0"])

        // 执行 GC 清理：必须严格中止，绝不能误删 c100.json
        FileCacheManager.deleteOrphansAndExpiredFile()

        assertTrue("根目录不存在时，孤立文件清理必须严格中止，禁止误删任何磁盘文件", diskFile.exists())
    }

    @Test
    fun testStarItemsRollbackWithRawFolderWithoutFileId() = runBlocking {
        // 构造未经 formatFileBeanList 统一处理的原始数据（fileId 为空，仅有 categoryId）
        val rawFolder1 = FileBean(fileId = "", categoryId = "folder_101", name = "RawFolder1", isFolder = true, star = 0)
        val rawFolder2 = FileBean(fileId = "", categoryId = "folder_102", name = "RawFolder2", isFolder = true, star = 0)
        val parentFiles = FilesBean(
            fileBeanList = arrayListOf(rawFolder1, rawFolder2),
            cid = "0",
            count = 2,
            order = "",
            path = listOf(PathBean(cid = "0", name = "Root", pid = "0"))
        )
        FileCacheManager.put("0", parentFiles)

        // 批量对两个原始文件夹进行加星
        val rollback = FileCacheManager.starItems("0", listOf("folder_101", "folder_102"), true)
        assertNotNull(rollback)

        val starredData = FileCacheManager["0"]
        assertNotNull(starredData)
        assertTrue(starredData!!.fileBeanList.all { it.isStar && it.star == 1 })

        // 触发撤销回滚
        rollback?.rollback()

        // 验证两个文件夹均正确恢复为未星标状态，没有被空字符串 key 冲突覆盖
        val restoredData = FileCacheManager["0"]
        assertNotNull(restoredData)
        assertEquals(0, restoredData!!.fileBeanList[0].star)
        assertFalse(restoredData.fileBeanList[0].isStar)
        assertEquals(0, restoredData.fileBeanList[1].star)
        assertFalse(restoredData.fileBeanList[1].isStar)
    }

    @Test
    fun testUpdateConfigPassThroughAndImmediateEffect() = runBlocking {
        // 1. 通过门面层 updateConfig 关闭磁盘落盘
        val disableJob = FileCacheManager.updateConfig(saveRequestCache = false)
        disableJob.join()

        // 验证 FileCacheManager 与底层 Engine 标志位同步立即生效
        assertFalse(FileCacheManager.saveRequestCache)

        val file1 = FileBean(fileId = "f_no_disk", name = "no_disk.txt")
        val files1 = FilesBean(
            fileBeanList = arrayListOf(file1),
            cid = "c_no_disk",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "c_no_disk", name = "NoDisk", pid = "0"))
        )
        FileCacheManager.put("c_no_disk", files1)
        kotlinx.coroutines.delay(100)

        // 验证内存中有缓存，但磁盘上没有生成文件
        assertTrue(FileCacheManager.containsKey("c_no_disk"))
        assertFalse(File(tempDir, "c_no_disk.json").exists())

        // 2. 通过属性 setter 再次开启（内部路由给 updateConfig）
        FileCacheManager.saveRequestCache = true
        assertTrue(FileCacheManager.saveRequestCache)

        val file2 = FileBean(fileId = "f_with_disk", name = "with_disk.txt")
        val files2 = FilesBean(
            fileBeanList = arrayListOf(file2),
            cid = "c_with_disk",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "c_with_disk", name = "WithDisk", pid = "0"))
        )
        FileCacheManager.put("c_with_disk", files2)
        kotlinx.coroutines.delay(100)

        // 验证开启后磁盘正常落盘
        assertTrue(FileCacheManager.containsKey("c_with_disk"))
        assertTrue(File(tempDir, "c_with_disk.json").exists())
    }

    private fun makeDummy(cid: String) = FilesBean(
        fileBeanList = arrayListOf(FileBean(fileId = "f_$cid", name = cid)),
        cid = cid,
        count = 1,
        order = "",
        path = listOf(PathBean(cid = cid, name = cid, pid = "0"))
    )

    @Test
    fun testUpdateConfigDynamicTrimToSizeViaFacade() = runBlocking {

        // 写入根目录与 4 个普通子目录
        FileCacheManager.put("0", makeDummy("0"))
        FileCacheManager.put("d1", makeDummy("d1"))
        FileCacheManager.put("d2", makeDummy("d2"))
        FileCacheManager.put("d3", makeDummy("d3"))
        FileCacheManager.put("d4", makeDummy("d4"))

        assertTrue(FileCacheManager.containsKey("0"))
        assertTrue(FileCacheManager.containsKey("d1"))
        assertTrue(FileCacheManager.containsKey("d2"))
        assertTrue(FileCacheManager.containsKey("d3"))
        assertTrue(FileCacheManager.containsKey("d4"))

        // 通过门面层 updateConfig 动态缩容普通目录容量为 2
        val trimJob = FileCacheManager.updateConfig(maxMemoryDirs = 2)
        trimJob.join()

        assertEquals(2, FileCacheManager.getMaxMemoryDirs())

        // 根目录 "0" 享有常驻保护，严禁被缩容淘汰
        assertTrue("根目录必须常驻内存，严禁被 trimToSize 淘汰", FileCacheManager.containsKey("0"))

        // 普通目录较老的前两个（d1, d2）被 LRU 淘汰，最新访问的 d3, d4 保留在内存中
        assertFalse("最老普通目录 d1 应当被淘汰", FileCacheManager.containsKey("d1"))
        assertFalse("次老普通目录 d2 应当被淘汰", FileCacheManager.containsKey("d2"))
        assertTrue("最新普通目录 d3 应当保留", FileCacheManager.containsKey("d3"))
        assertTrue("最新普通目录 d4 应当保留", FileCacheManager.containsKey("d4"))

        // 恢复默认容量以避免影响后续测试
        FileCacheManager.updateConfig(maxMemoryDirs = 500).join()
    }

    @Test
    fun testGetDateFallsBackToDiskWhenMemoryCleared() = runBlocking {
        val dummy = makeDummy("cid_disk_fallback")
        FileCacheManager.put("cid_disk_fallback", dummy)
        kotlinx.coroutines.delay(100.milliseconds) // 等待单线程落盘

        // 清空纯内存
        FileCacheManager.clearMemoryOnly()
        assertFalse(FileCacheManager.containsKey("cid_disk_fallback"))
        assertNull(FileCacheManager.peekMemory("cid_disk_fallback"))

        // getDate 必须自动从磁盘回源读取并回填内存
        val loaded = FileCacheManager.getDate("cid_disk_fallback")
        assertNotNull("getDate 在内存淘汰但磁盘存在时必须成功加载", loaded)
        assertEquals("cid_disk_fallback", loaded?.cid)
        assertTrue("getDate 成功读取磁盘后必须自动回填内存", FileCacheManager.containsKey("cid_disk_fallback"))
        assertEquals("cid_disk_fallback", FileCacheManager.peekMemory("cid_disk_fallback")?.cid)
    }
}
