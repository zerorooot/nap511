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

class FileCacheSyncModuleTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            XLog.init()
        }
    }

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

    @Test
    fun `test handleCacheEvent AllCleared purges stores and coil cache`() = runTest {
        imageCacheStore.put("100", "pick1", ImageBean("url1", "name1", "pick1"))
        pagingStateStore.setOffset("folder:0", 20)

        module.handleCacheEvent(CacheEvent.AllCleared)

        assertNull(imageCacheStore.get("100", "pick1"))
        assertEquals(0, pagingStateStore.getOrInitOffset("folder:0", 0))
        assertTrue(memoryCacheCleared)
        assertTrue(diskCacheCleared)
    }

    @Test
    fun `test handleCacheEvent FolderDeleted purges target directory stores`() = runTest {
        imageCacheStore.put("100", "pick1", ImageBean("url1", "name1", "pick1"))
        imageCacheStore.put("200", "pick2", ImageBean("url2", "name2", "pick2"))
        pagingStateStore.setOffset("folder:100", 30)
        pagingStateStore.setOffset("folder:200", 50)

        module.handleCacheEvent(CacheEvent.FolderDeleted("100"))

        assertNull(imageCacheStore.get("100", "pick1"))
        assertEquals(0, pagingStateStore.getOrInitOffset("folder:100", 0))

        // 其他目录保持不受影响
        assertEquals("url2", imageCacheStore.get("200", "pick2")?.url)
        assertEquals(50, pagingStateStore.getOrInitOffset("folder:200", 0))
    }

    @Test
    fun `test handleCacheEvent FolderDeleted on current directory emits toast`() = runTest {
        stateHolder.updateContentState {
            copy(
                source = github.zerorooot.nap511.bean.FileListSource.Folder("current_dir"),
                pathList = listOf(github.zerorooot.nap511.bean.PathBean(cid = "current_dir", name = "当前目录", pid = "0"))
            )
        }

        module.handleCacheEvent(CacheEvent.FolderDeleted("current_dir"))

        assertTrue(fakeToastProvider.messages.contains("当前所在目录已被删除"))
    }
}
