package github.zerorooot.nap511.viewmodel.module

import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.util.CacheEvent
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.PagingStateStore
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.util.FileCacheManager
import org.junit.After
import java.io.File
import java.nio.file.Files

class FileCacheSyncModuleTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            XLog.init()
        }
    }

    private lateinit var tempDir: File
    private lateinit var stateHolder: FileUiStateHolder
    private lateinit var imageCacheStore: ImageCacheStore
    private lateinit var pagingStateStore: PagingStateStore
    private lateinit var module: FileCacheSyncModule
    private val fakeToastProvider = FakeToastProvider()

    private var memoryCacheCleared = false
    private var diskCacheCleared = false

    private val fakeCoilCleaner = object : CoilCacheCleaner {
        override fun deleteCoilCache(key: String) {}
        override fun clearMemoryCache() {
            memoryCacheCleared = true
        }
        override fun clearDiskCache() {
            diskCacheCleared = true
        }
        override fun getCachedUrl(key: String): String? = null
    }

    private val fakeNavigator = object : FileNavigator {
        override fun navigateTo(cid: String) {}
        override fun refresh(forceCache: Boolean) {}
        override fun refresh(cid: String, forceCache: Boolean) {}
        override fun updateOrder(order: OrderBean) {}
    }

    private val fakeStringProvider = object : AppStringProvider {
        override val defaultAppName: String = "nap511"
        override fun getSearchTitle(keyword: String): String = "搜索: $keyword"
        override fun getFilterTitle(name: String): String = "分类: $name"
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("sync_test").toFile()
        FileCacheManager.init(tempDir)
        FileCacheManager.saveRequestCache = true

        stateHolder = FileUiStateHolder("nap511")
        imageCacheStore = ImageCacheStore()
        pagingStateStore = PagingStateStore()
        memoryCacheCleared = false
        diskCacheCleared = false
        fakeToastProvider.clear()

        module = FileCacheSyncModule(
            stateHolder = stateHolder,
            navigator = fakeNavigator,
            imageCacheStore = imageCacheStore,
            pagingStateStore = pagingStateStore,
            coilCacheCleaner = fakeCoilCleaner,
            stringProvider = fakeStringProvider,
            onUpdateFileCache = {},
            toastProvider = fakeToastProvider
        )
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.runBlocking {
            FileCacheManager.clearAll()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test handleCacheEvent AllCleared purges stores and coil cache`() = runTest {
        imageCacheStore.put("100", "pick1", ImageBean("url1", "name1", "pick1"))
        pagingStateStore.setOffset(FileListSource.Folder.key("0"), 20)

        module.handleCacheEvent(CacheEvent.AllCleared)

        assertNull(imageCacheStore.get("100", "pick1"))
        assertEquals(0, pagingStateStore.getOrInitOffset(FileListSource.Folder.key("0"), 0))
        assertTrue(memoryCacheCleared)
        assertTrue(diskCacheCleared)
    }

    @Test
    fun `test handleCacheEvent FolderDeleted purges target directory stores`() = runTest {
        imageCacheStore.put("100", "pick1", ImageBean("url1", "name1", "pick1"))
        imageCacheStore.put("200", "pick2", ImageBean("url2", "name2", "pick2"))
        pagingStateStore.setOffset(FileListSource.Folder.key("100"), 30)
        pagingStateStore.setOffset(FileListSource.Folder.key("200"), 50)

        module.handleCacheEvent(CacheEvent.FolderDeleted("100"))

        assertNull(imageCacheStore.get("100", "pick1"))
        assertEquals(0, pagingStateStore.getOrInitOffset(FileListSource.Folder.key("100"), 0))

        // 其他目录保持不受影响
        assertEquals("url2", imageCacheStore.get("200", "pick2")?.url)
        assertEquals(50, pagingStateStore.getOrInitOffset(FileListSource.Folder.key("200"), 0))
    }

    @Test
    fun `test handleCacheEvent FolderDeleted on current directory emits toast`() = runTest {
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("current_dir"),
                pathList = listOf(PathBean(cid = "current_dir", name = "当前目录", pid = "0"))
            )
        }

        module.handleCacheEvent(CacheEvent.FolderDeleted("current_dir"))

        assertTrue(fakeToastProvider.messages.contains("当前所在目录已被删除"))
    }

    @Test
    fun `test handleCacheEvent LocalUiUpdated updates UI state even when size differs`() = runTest {
        val oldItem1 = FileBean(fileId = "f1", name = "item1", parentId = "dir1")
        val oldItem2 = FileBean(fileId = "f2", name = "item2", parentId = "dir1")
        val oldItem3 = FileBean(fileId = "f3", name = "item3", parentId = "dir1")
        // 模拟当前 UI 位于 dir1 且已经分页加载了 3 项
        stateHolder.updateContentState {
            copy(
                currentCid = "dir1",
                source = FileListSource.Folder("dir1"),
                fileBeanList = listOf(oldItem1, oldItem2, oldItem3),
                totalCount = 3
            )
        }

        // 缓存中更新为 1 项带播放进度（例如看完视频后，缓存首批数据被更新）
        val updatedItem1 = FileBean(
            fileId = "f1",
            pickCode = "pc1",
            name = "movie.mp4",
            isVideo = 1,
            playLong = 200.0,
            currentPlayTime = 100,
            parentId = "dir1"
        )
        val files = FilesBean(
            fileBeanList = arrayListOf(updatedItem1),
            cid = "dir1",
            count = 1,
            order = "",
            path = listOf(PathBean(cid = "dir1", name = "测试目录", pid = "0"))
        )
        FileCacheManager.put("dir1", files)

        // 触发 LocalUiUpdated 事件
        module.handleCacheEvent(CacheEvent.LocalUiUpdated("dir1"))

        // 校验：即使 UI 展示的列表长度（3）与缓存列表长度（1）不一致，依然能整表覆盖刷新
        val currentUiList = stateHolder.currentContentState.fileBeanList
        assertEquals(1, currentUiList.size)
        assertEquals("f1", currentUiList[0].fileId)
        assertEquals("▶️ 50%", currentUiList[0].playLongRatio)
        assertEquals(1, pagingStateStore.getOrInitOffset(FileListSource.Folder.key("dir1"), 0))
    }

    @Test
    fun `test handleCacheEvent LocalUiUpdated on different cid does not touch UI`() = runTest {
        val item1 = FileBean(fileId = "f1", name = "item1", parentId = "dir1")
        stateHolder.updateContentState {
            copy(
                currentCid = "dir1",
                source = FileListSource.Folder("dir1"),
                fileBeanList = listOf(item1),
                totalCount = 1
            )
        }

        val otherFiles = FilesBean(
            fileBeanList = arrayListOf(FileBean(fileId = "f_other", name = "other", parentId = "dir2")),
            cid = "dir2",
            count = 1,
            order = "",
            path = emptyList()
        )
        FileCacheManager.put("dir2", otherFiles)

        // 触发不同 cid 的 LocalUiUpdated 事件
        module.handleCacheEvent(CacheEvent.LocalUiUpdated("dir2"))

        // 当前 UI 列表绝不被篡改
        val currentUiList = stateHolder.currentContentState.fileBeanList
        assertEquals(1, currentUiList.size)
        assertEquals("f1", currentUiList[0].fileId)
    }
}
