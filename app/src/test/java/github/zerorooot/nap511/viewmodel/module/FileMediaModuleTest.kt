package github.zerorooot.nap511.viewmodel.module

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.platform.ServiceBridge
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.ScrollPositionStore
import github.zerorooot.nap511.viewmodel.store.TorrentCacheStore
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        override fun updateOrder(order: OrderBean) {}
    }

    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()

    @org.junit.After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        stateHolder = FileUiStateHolder("nap511")
        imageCacheStore = ImageCacheStore()
        torrentCacheStore = TorrentCacheStore()
        scrollPositionStore = ScrollPositionStore()
        sentFileBeanToAria2 = null
        coilCacheMockUrl = null

        module = FileMediaModule(
            stateHolder = stateHolder,
            fileRepository = FileRepository(),
            imageCacheStore = imageCacheStore,
            torrentCacheStore = torrentCacheStore,
            scrollPositionStore = scrollPositionStore,
            coilCacheCleaner = fakeCoilCleaner,
            serviceBridge = fakeServiceBridge,
            navigator = fakeNavigator,
            onEnqueueUnzip = {}
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
}
