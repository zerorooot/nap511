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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

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
}
