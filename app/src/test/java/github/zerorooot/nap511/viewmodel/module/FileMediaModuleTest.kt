package github.zerorooot.nap511.viewmodel.module

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.VideoStarAction
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.repository.FakeFileRepository
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
import github.zerorooot.nap511.viewmodel.platform.ServiceBridge
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.ScrollPositionStore
import github.zerorooot.nap511.viewmodel.store.TorrentCacheStore
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class FileMediaModuleTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            XLog.init()
        }
    }

    private lateinit var stateHolder: FileUiStateHolder
    private lateinit var imageCacheStore: ImageCacheStore
    private lateinit var torrentCacheStore: TorrentCacheStore
    private lateinit var scrollPositionStore: ScrollPositionStore
    private lateinit var module: FileMediaModule
    private val fakeToastProvider = FakeToastProvider()

    private var sentFileBeanToAria2: FileBean? = null
    private val fakeServiceBridge = object : ServiceBridge {
        override fun startSha1Service(index: Int) {}
        override fun startSendAria2Service(fileBean: FileBean) {
            sentFileBeanToAria2 = fileBean
        }
    }

    private var coilCacheMockUrl: String? = null
    private val fakeCoilCleaner = object : CoilCacheCleaner {
        override fun deleteCoilCache(key: String) {}
        override fun clearMemoryCache() {}
        override fun clearDiskCache() {}
        override fun getCachedUrl(key: String): String? = coilCacheMockUrl
    }

    private val fakeNavigator = object : FileNavigator {
        override fun navigateTo(cid: String) {}
        override fun refresh(forceCache: Boolean) {}
        override fun refresh(cid: String, forceCache: Boolean) {}
        override fun updateOrder(order: OrderBean, fcMix: Int) {}
    }

    private var shouldThrowStarError = false
    val starCalls = mutableListOf<Pair<String, Boolean>>()
    private val fakeFileRepo = object : FakeFileRepository() {
        override suspend fun star(fid: String, star: Boolean): BaseReturnMessage {
            if (shouldThrowStarError) throw java.io.IOException("Star network timeout")
            starCalls.add(fid to star)
            return BaseReturnMessage(state = true)
        }
    }

    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @org.junit.After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }
    private val fakeStringProvider = object : AppStringProvider {
        override val defaultAppName: String = "nap511"
        override fun getSearchTitle(keyword: String): String = "搜索: $keyword"
        override fun getFilterTitle(name: String): String = "分类: $name"
        override fun getStarTitle(): String ="星标文件"
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val tempDir = java.nio.file.Files.createTempDirectory("media_test").toFile()
        FileCacheManager.init(tempDir)
        FileCacheManager.saveRequestCache = true

        stateHolder = FileUiStateHolder(fakeStringProvider)
        imageCacheStore = ImageCacheStore()
        torrentCacheStore = TorrentCacheStore()
        scrollPositionStore = ScrollPositionStore()
        sentFileBeanToAria2 = null
        coilCacheMockUrl = null
        fakeToastProvider.clear()
        starCalls.clear()
        shouldThrowStarError = false

        module = FileMediaModule(
            stateHolder = stateHolder,
            fileRepository = fakeFileRepo,
            imageCacheStore = imageCacheStore,
            torrentCacheStore = torrentCacheStore,
            scrollPositionStore = scrollPositionStore,
            coilCacheCleaner = fakeCoilCleaner,
            serviceBridge = fakeServiceBridge,
            navigator = fakeNavigator,
            onEnqueueUnzip = {},
            toastProvider = fakeToastProvider
        )
    }

    private fun createFileBean(id: String, name: String, isFolder: Boolean = false): FileBean {
        return FileBean(
            fileId = id,
            name = name,
            isFolder = isFolder,
            pickCode = "pick_$id"
        )
    }

    @Test
    fun `test aria2 dialog controls and service bridge invocation`() {
        val item = createFileBean("1", "file.mp4")
        stateHolder.updateContentState {
            copy(fileBeanList = listOf(item))
        }

        module.openAria2Dialog()
        assertEquals(FileDialogState.Aria2, stateHolder.currentDialogState.activeDialog)

        stateHolder.closeDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)

        module.startSendAria2Service(0)
        assertEquals(item, sentFileBeanToAria2)

        // 尝试下载文件夹时拦截并触发 Toast
        val folderItem = createFileBean("2", "my_folder", isFolder = true)
        stateHolder.updateContentState {
            copy(fileBeanList = listOf(folderItem))
        }
        module.startSendAria2Service(0)
        assertTrue(fakeToastProvider.messages.contains("暂时无法下载文件夹"))
    }

    @Test
    fun `test unzip password and unzip all file dialog controls`() {
        // 解压全部弹窗
        module.openUnzipAllFileDialog()
        assertEquals(FileDialogState.UnzipAllFile, stateHolder.currentDialogState.activeDialog)
        stateHolder.closeDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)

        // 密码输入弹窗
        val item = createFileBean("z1", "encrypted.zip")
        module.openUnzipPasswordDialog(item)
        assertEquals(FileDialogState.UnzipPassword, stateHolder.currentDialogState.activeDialog)
        assertEquals(item, stateHolder.currentDialogState.targetFileBean)

        module.closeUnzipPasswordDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)
    }

    @Test
    fun `test getImage hits coil cache and caches to imageCacheStore`() = runTest {
        val item = createFileBean("pic1", "image.jpg")
        coilCacheMockUrl = "https://example.com/disk_cached_image.jpg"

        module.getImage(item)

        val cached = imageCacheStore.get("0", "pick_pic1")
        assertNotNull(cached)
        assertEquals("https://example.com/disk_cached_image.jpg", cached?.url)
        assertEquals("image.jpg", cached?.fileName)
    }

    @Test
    fun `test onVideoActivityResult with starList updates cache and UI state`() = runTest {
        val cid = "folder_test"
        val item1 = createFileBean("v1", "video1.mp4").copy(parentId = cid, isStar = false)
        val item2 = createFileBean("v2", "video2.mp4").copy(parentId = cid, isStar = true, star = 1)
        FileCacheManager.put(
            cid,
            FilesBean(
                fileBeanList = arrayListOf(item1, item2),
                cid = cid,
                count = 2,
                order = "",
                path = emptyList()
            )
        )

        stateHolder.updateContentState {
            copy(
                currentCid = cid,
                source = FileListSource.Folder(cid, fakeStringProvider),
                fileBeanList = listOf(item1, item2)
            )
        }

        module.onVideoActivityResult(
            pickCode = "pick_v1",
            cid = cid,
            videoHistoryMap = emptyMap(),
            starList = listOf(
                VideoStarAction("v1", true),
                VideoStarAction("v2", false)
            )
        )

        // 验证服务端 API 调用
        assertTrue("starCalls should contain v1 true, actual=$starCalls", starCalls.any { it.first == "v1" && it.second })
        assertTrue("starCalls should contain v2 false, actual=$starCalls", starCalls.any { it.first == "v2" && !it.second })

        // 验证 StateHolder 内存状态更新
        val uiList = stateHolder.currentContentState.fileBeanList
        assertTrue("uiList v1 should be star, actual=$uiList", uiList.first { it.fileId == "v1" }.isStar)
        assertTrue("uiList v2 should not be star, actual=$uiList", !uiList.first { it.fileId == "v2" }.isStar)

        // 验证 FileCacheManager 缓存更新
        val cacheList = FileCacheManager.getDate(cid)?.fileBeanList
        assertTrue("cacheList v1 should be star, actual=${cacheList?.map { it.fileId to it.isStar }}", cacheList?.first { it.fileId == "v1" }?.isStar == true)
        assertTrue("cacheList v2 should not be star, actual=${cacheList?.map { it.fileId to it.isStar }}", cacheList?.first { it.fileId == "v2" }?.isStar == false)
    }

    @Test
    fun `test onVideoActivityResult with starList rolls back on failure`() = runTest {
        val cid = "folder_test"
        val item1 = createFileBean("v1", "video1.mp4").copy(parentId = cid, isStar = false)
        FileCacheManager.put(
            cid,
            FilesBean(
                fileBeanList = arrayListOf(item1),
                cid = cid,
                count = 1,
                order = "",
                path = emptyList()
            )
        )

        stateHolder.updateContentState {
            copy(
                currentCid = cid,
                source = FileListSource.Folder(cid, fakeStringProvider),
                fileBeanList = listOf(item1)
            )
        }

        shouldThrowStarError = true

        module.onVideoActivityResult(
            pickCode = "",
            cid = cid,
            videoHistoryMap = emptyMap(),
            starList = listOf(VideoStarAction("v1", true))
        )

        // 验证失败提示与状态回滚
        assertTrue(fakeToastProvider.messages.any { it.contains("Star network timeout") })
        val uiList = stateHolder.currentContentState.fileBeanList
        assertTrue(!uiList.first { it.fileId == "v1" }.isStar)
        val cacheList = FileCacheManager.getDate(cid)?.fileBeanList
        assertTrue(cacheList?.first { it.fileId == "v1" }?.isStar == false)
    }
}
