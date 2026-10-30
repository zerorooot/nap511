package github.zerorooot.nap511.viewmodel.module

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.ScrollPositionStore
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class FileMutatorModuleTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            XLog.init()
        }
    }

    private lateinit var stateHolder: FileUiStateHolder
    private lateinit var scrollPositionStore: ScrollPositionStore
    private lateinit var imageCacheStore: ImageCacheStore
    private lateinit var module: FileMutatorModule

    private val fakeNavigator = object : FileNavigator {
        override suspend fun getFiles(cid: String) {}
        override suspend fun refresh(forceCache: Boolean) {}
        override suspend fun refresh(cid: String, forceCache: Boolean) {}
        override suspend fun updateOrder(order: OrderBean) {}
    }

    private val fakeStringProvider = object : AppStringProvider {
        override val defaultAppName: String = "nap511"
        override fun getSearchTitle(keyword: String): String = "搜索: $keyword"
        override fun getFilterTitle(name: String): String = "分类: $name"
    }

    private val fakeCoilCleaner = object : CoilCacheCleaner {
        override fun deleteCoilCache(key: String) {}
        override fun clearMemoryCache() {}
        override fun clearDiskCache() {}
        override fun getCachedUrl(key: String): String? = null
    }

    @Before
    fun setUp() {
        stateHolder = FileUiStateHolder("nap511")
        scrollPositionStore = ScrollPositionStore()
        imageCacheStore = ImageCacheStore()

        module = FileMutatorModule(
            stateHolder = stateHolder,
            navigator = fakeNavigator,
            fileRepository = FileRepository(),
            scrollPositionStore = scrollPositionStore,
            imageCacheStore = imageCacheStore,
            coilCacheCleaner = fakeCoilCleaner,
            stringProvider = fakeStringProvider
        )
    }

    private fun createFileBean(id: String, name: String, isSelect: Boolean = false): FileBean {
        return FileBean(
            fileId = id,
            name = name,
            isSelect = isSelect,
            pickCode = "pick_$id"
        )
    }

    @Test
    fun `test cut with index -1 cuts selected items and clears selection`() {
        val item1 = createFileBean("1", "item1", isSelect = true)
        val item2 = createFileBean("2", "item2", isSelect = false)
        val item3 = createFileBean("3", "item3", isSelect = true)
        stateHolder.updateContentState {
            copy(
                fileBeanList = listOf(item1, item2, item3),
                isLongClickState = true
            )
        }

        module.cut(-1)

        val content = stateHolder.currentContentState
        assertTrue(content.isCutState)
        assertFalse(content.isLongClickState)
        assertEquals(2, content.cutFileList.size)
        assertEquals("1", content.cutFileList[0].fileId)
        assertEquals("3", content.cutFileList[1].fileId)
        // 原始列表选中状态全部清空
        assertTrue(content.fileBeanList.none { it.isSelect })
        assertEquals("nap511", content.appBarTitle)
    }

    @Test
    fun `test cut with specific index cuts single target item`() {
        val item1 = createFileBean("1", "item1")
        val item2 = createFileBean("2", "item2")
        stateHolder.updateContentState {
            copy(fileBeanList = listOf(item1, item2))
        }

        module.cut(1)

        val content = stateHolder.currentContentState
        assertTrue(content.isCutState)
        assertEquals(1, content.cutFileList.size)
        assertEquals("2", content.cutFileList[0].fileId)
    }

    @Test
    fun `test cancelCut resets cut state and clears list`() {
        val item = createFileBean("1", "item1")
        stateHolder.updateContentState {
            copy(
                isCutState = true,
                cutFileList = listOf(item)
            )
        }

        module.cancelCut()

        val content = stateHolder.currentContentState
        assertFalse(content.isCutState)
        assertTrue(content.cutFileList.isEmpty())
    }

    @Test
    fun `test createFolder and rename dialog controls`() {
        module.openCreateFolderDialog()
        assertEquals(FileDialogState.CreateFolder, stateHolder.currentDialogState.activeDialog)
        stateHolder.closeDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)

        val item = createFileBean("1", "renameMe")
        module.openRenameFileDialog(item)
        assertEquals(FileDialogState.RenameFile, stateHolder.currentDialogState.activeDialog)
        assertEquals(item, stateHolder.currentDialogState.targetFileBean)
        stateHolder.closeDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)
    }

    @Test
    fun `test discard videos dialog controls`() {
        val video = VideoBean(fileId = "v1", name = "video1.mp4", pickCode = "pick_v1")
        val discardList = listOf(video)

        module.openDiscardVideosDialog(discardList, "target_cid_1")
        assertEquals(FileDialogState.DiscardVideos, stateHolder.currentDialogState.activeDialog)
        assertEquals(discardList, stateHolder.currentDialogState.discardVideoList)
        assertEquals("target_cid_1", stateHolder.currentDialogState.targetCid)

        stateHolder.closeDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)
    }
}
