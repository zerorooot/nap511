package github.zerorooot.nap511.viewmodel.module

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.ScrollPositionStore
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
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

    private lateinit var tempDir: java.io.File
    private val deletedCoilKeys = mutableListOf<String>()
    private val fakeToastProvider = FakeToastProvider()
    private var mockDeleteResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = true)
    private var mockDeleteMultipleResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = true)
    private var mockRenameResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = true)
    private var renameCallCount = 0
    private var shouldThrowNetworkError = false

    private val fakeRepo = object : FileRepository() {
        override suspend fun delete(pid: String, fid: String): github.zerorooot.nap511.bean.BaseReturnMessage {
            if (shouldThrowNetworkError) throw java.io.IOException("Network Timeout")
            return mockDeleteResult
        }

        override suspend fun deleteMultiple(data: Map<String, String>): github.zerorooot.nap511.bean.BaseReturnMessage {
            if (shouldThrowNetworkError) throw java.io.IOException("Network Timeout")
            return if (!mockDeleteResult.state) mockDeleteResult else mockDeleteMultipleResult
        }

        override suspend fun star(fid: String, star: Boolean): github.zerorooot.nap511.bean.BaseReturnMessage {
            if (shouldThrowNetworkError) throw java.io.IOException("Network Timeout")
            return mockDeleteResult
        }

        override suspend fun rename(renameBean: okhttp3.RequestBody): github.zerorooot.nap511.bean.BaseReturnMessage {
            if (shouldThrowNetworkError) throw java.io.IOException("Network Timeout")
            renameCallCount++
            return mockRenameResult
        }
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
        override fun getStarTitle(): String ="星标文件"
    }

    private val fakeCoilCleaner = object : CoilCacheCleaner {
        override fun deleteCoilCache(key: String) {
            deletedCoilKeys.add(key)
        }
        override fun clearMemoryCache() {}
        override fun clearDiskCache() {}
        override fun getCachedUrl(key: String): String? = null
    }

    @Before
    fun setUp() {
        tempDir = java.nio.file.Files.createTempDirectory("fmm_test").toFile()
        github.zerorooot.nap511.util.FileCacheManager.init(tempDir)
        github.zerorooot.nap511.util.FileCacheManager.saveRequestCache = true

        stateHolder = FileUiStateHolder(fakeStringProvider)
        scrollPositionStore = ScrollPositionStore()
        imageCacheStore = ImageCacheStore()
        deletedCoilKeys.clear()
        fakeToastProvider.clear()
        mockDeleteResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = true)
        mockDeleteMultipleResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = true)
        mockRenameResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = true)
        renameCallCount = 0
        shouldThrowNetworkError = false

        module = FileMutatorModule(
            stateHolder = stateHolder,
            navigator = fakeNavigator,
            fileRepository = fakeRepo,
            scrollPositionStore = scrollPositionStore,
            imageCacheStore = imageCacheStore,
            coilCacheCleaner = fakeCoilCleaner,
            toastProvider = fakeToastProvider
        )
    }

    @org.junit.After
    fun tearDown() {
        kotlinx.coroutines.runBlocking {
            github.zerorooot.nap511.util.FileCacheManager.clearAll()
        }
        tempDir.deleteRecursively()
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

    @Test
    fun `test delete rolls back on API failure and does not delete coil cache`() = kotlinx.coroutines.test.runTest {
        val item = createFileBean("del_1", "item_del_1").copy(parentId = "0", photoThumb = "thumb_url")
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0",fakeStringProvider),
                fileBeanList = listOf(item)
            )
        }
        scrollPositionStore.setClick(FileListSource.folderKey("0"), 5)
        mockDeleteResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = false, errorMsg = "删除失败权限不足")

        module.delete(item)

        // 验证失败后列表与点击位均已无死角回滚
        assertEquals(1, stateHolder.currentContentState.fileBeanList.size)
        assertEquals("del_1", stateHolder.currentContentState.fileBeanList[0].fileId)
        assertEquals(5, scrollPositionStore.getClick(FileListSource.folderKey("0")))
        // Coil 缓存决不能被删除
        assertTrue(deletedCoilKeys.isEmpty())
    }

    @Test
    fun `test delete rolls back on network exception`() = kotlinx.coroutines.test.runTest {
        val item = createFileBean("del_2", "item_del_2").copy(parentId = "0")
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0",fakeStringProvider),
                fileBeanList = listOf(item)
            )
        }
        scrollPositionStore.setClick(FileListSource.folderKey("0"), 3)
        shouldThrowNetworkError = true

        module.delete(item)

        assertEquals(1, stateHolder.currentContentState.fileBeanList.size)
        assertEquals(3, scrollPositionStore.getClick(FileListSource.folderKey("0")))
        assertTrue(deletedCoilKeys.isEmpty())
    }

    @Test
    fun `test deleteMultiple rolls back on network exception and preserves totalCount`() = kotlinx.coroutines.test.runTest {
        val item1 = createFileBean("m1", "multi1").copy(parentId = "0", isSelect = true, photoThumb = "thumb1")
        val item2 = createFileBean("m2", "multi2").copy(parentId = "0", isSelect = true, photoThumb = "thumb2")
        val item3 = createFileBean("m3", "multi3").copy(parentId = "0", isSelect = false)

        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0",fakeStringProvider),
                fileBeanList = listOf(item1, item2, item3),
                totalCount = 3
            )
        }
        scrollPositionStore.setClick(FileListSource.folderKey("0"), 10)
        shouldThrowNetworkError = true

        module.deleteMultiple("0", listOf(item1, item2))

        // 验证列表、总量、点击索引均已完全恢复
        assertEquals(3, stateHolder.currentContentState.fileBeanList.size)
        assertEquals(3, stateHolder.currentContentState.totalCount)
        assertEquals(10, scrollPositionStore.getClick(FileListSource.folderKey("0")))
        assertTrue(deletedCoilKeys.isEmpty())
    }

    @Test
    fun `test deleteMultiple succeeds and cleans coil cache only on success`() = kotlinx.coroutines.test.runTest {
        val item1 = createFileBean("m1", "multi1").copy(parentId = "0", photoThumb = "thumb1")
        val item2 = createFileBean("m2", "multi2").copy(parentId = "0", photoThumb = "") // 无缩略图

        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0",fakeStringProvider),
                fileBeanList = listOf(item1, item2),
                totalCount = 2
            )
        }
        scrollPositionStore.setClick(FileListSource.folderKey("0"), 8)
        mockDeleteMultipleResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = true)

        module.deleteMultiple("0", listOf(item1, item2))

        // 成功删除后列表被移除，Coil 缓存中仅有 photoThumb 的项被清理
        assertTrue(stateHolder.currentContentState.fileBeanList.isEmpty())
        assertEquals(0, stateHolder.currentContentState.totalCount)
        assertEquals(6, scrollPositionStore.getClick(FileListSource.folderKey("0")))
        assertEquals(listOf("pick_m1"), deletedCoilKeys)
    }

    @Test
    fun `test delete emits toast message on server failure`() = kotlinx.coroutines.test.runTest {
        val item = createFileBean("del_toast", "item_del_toast").copy(parentId = "0")
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0",fakeStringProvider),
                fileBeanList = listOf(item)
            )
        }
        mockDeleteResult = github.zerorooot.nap511.bean.BaseReturnMessage(state = false, errorMsg = "文件不存在")

        module.delete(item)

        assertTrue(fakeToastProvider.messages.contains("文件不存在"))
    }

    @Test
    fun `test starMultiple toggles star state and updates cache and toasts`() = kotlinx.coroutines.test.runTest {
        val item1 = createFileBean("s1", "star1").copy(parentId = "0", isStar = false)
        val item2 = createFileBean("s2", "star2").copy(parentId = "0", isStar = false)
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0",fakeStringProvider),
                fileBeanList = listOf(item1, item2)
            )
        }

        module.starMultiple(listOf(item1, item2))

        val list = stateHolder.currentContentState.fileBeanList
        assertTrue(list.all { it.isStar && it.star == 1 })
        assertTrue(fakeToastProvider.messages.contains("标记星标成功"))
    }

    @Test
    fun `test starMultiple rolls back on failure`() = kotlinx.coroutines.test.runTest {
        val item1 = createFileBean("s1", "star1").copy(parentId = "0", isStar = false)
        val item2 = createFileBean("s2", "star2").copy(parentId = "0", isStar = false)
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0",fakeStringProvider),
                fileBeanList = listOf(item1, item2)
            )
        }
        shouldThrowNetworkError = true

        module.starMultiple(listOf(item1, item2))

        val list = stateHolder.currentContentState.fileBeanList
        assertTrue(list.all { !it.isStar })
        assertTrue(fakeToastProvider.messages.contains("Network Timeout"))
    }

    @Test
    fun `test openBatchRenameDialog requires selected items`() {
        // 无选中项时拦截并 Toast
        stateHolder.updateContentState {
            copy(fileBeanList = listOf(createFileBean("1", "old1.txt", isSelect = false)))
        }
        module.openBatchRenameDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)
        assertTrue(fakeToastProvider.messages.contains("请先选择需要重命名的文件"))

        // 有选中项时正常开启
        stateHolder.updateContentState {
            copy(fileBeanList = listOf(createFileBean("1", "old1.txt", isSelect = true)))
        }
        module.openBatchRenameDialog()
        assertEquals(FileDialogState.BatchRename, stateHolder.currentDialogState.activeDialog)

        module.closeBatchRenameDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)
    }

    @Test
    fun `test batchRename successfully updates UI, clears selection, and calls repository`() = kotlinx.coroutines.test.runTest {
        val item1 = createFileBean("1", "old1.txt", isSelect = true)
        val item2 = createFileBean("2", "old2.txt", isSelect = true)
        val item3 = createFileBean("3", "old3.txt", isSelect = false)
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0", fakeStringProvider),
                currentCid = "0",
                fileBeanList = listOf(item1, item2, item3),
                isLongClickState = true
            )
        }

        val renameMap = mapOf(
            "1" to "new1.txt",
            "2" to "new2.txt"
        )
        module.batchRename(renameMap)

        val updatedList = stateHolder.currentContentState.fileBeanList
        assertEquals("new1.txt", updatedList.find { it.fileId == "1" }?.name)
        assertEquals("new2.txt", updatedList.find { it.fileId == "2" }?.name)
        assertEquals("old3.txt", updatedList.find { it.fileId == "3" }?.name)
        assertFalse("多选状态应被重置", stateHolder.currentContentState.isLongClickState)
        assertTrue("所有条目的选中状态应被清除", updatedList.none { it.isSelect })
        assertEquals(1, renameCallCount)
        assertTrue(fakeToastProvider.messages.contains("成功重命名 2 个文件"))
    }

    @Test
    fun `test batchRename chunks into batches of 100`() = kotlinx.coroutines.test.runTest {
        val items = (1..150).map { i ->
            createFileBean("id_$i", "old_$i.txt", isSelect = true)
        }
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0", fakeStringProvider),
                currentCid = "0",
                fileBeanList = items,
                isLongClickState = true
            )
        }

        val renameMap = (1..150).associate { i -> "id_$i" to "new_$i.txt" }
        module.batchRename(renameMap)

        assertEquals("150 项应按 100 拆分成 2 次分批请求", 2, renameCallCount)
        assertTrue(fakeToastProvider.messages.contains("成功重命名 150 个文件"))
        val updatedList = stateHolder.currentContentState.fileBeanList
        assertEquals("new_1.txt", updatedList.first().name)
        assertEquals("new_150.txt", updatedList.last().name)
    }

    @Test
    fun `test batchRename rolls back on network error`() = kotlinx.coroutines.test.runTest {
        val item1 = createFileBean("1", "old1.txt", isSelect = true)
        val item2 = createFileBean("2", "old2.txt", isSelect = true)
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0", fakeStringProvider),
                currentCid = "0",
                fileBeanList = listOf(item1, item2),
                isLongClickState = true
            )
        }

        shouldThrowNetworkError = true
        val renameMap = mapOf("1" to "new1.txt", "2" to "new2.txt")
        module.batchRename(renameMap)

        val list = stateHolder.currentContentState.fileBeanList
        assertEquals("old1.txt", list.find { it.fileId == "1" }?.name)
        assertEquals("old2.txt", list.find { it.fileId == "2" }?.name)
        assertTrue(fakeToastProvider.messages.contains("Network Timeout"))
    }

    @Test
    fun `test batchRename supports folders with categoryId`() = kotlinx.coroutines.test.runTest {
        val folder = FileBean(fileId = "", categoryId = "c100", name = "旧目录", isFolder = true, isSelect = true)
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0", fakeStringProvider),
                currentCid = "0",
                fileBeanList = listOf(folder),
                isLongClickState = true
            )
        }

        val renameMap = mapOf("c100" to "新目录")
        module.batchRename(renameMap)

        val updated = stateHolder.currentContentState.fileBeanList.first()
        assertEquals("新目录", updated.name)
        assertFalse(updated.isSelect)
        assertEquals(1, renameCallCount)
        assertTrue(fakeToastProvider.messages.contains("成功重命名 1 个文件"))
    }

    @Test
    fun `test batchRename across different parent directories`() = kotlinx.coroutines.test.runTest {
        val itemP1 = FileBean(fileId = "f1", name = "item1.txt", parentId = "p1", isSelect = true)
        val itemP2 = FileBean(fileId = "f2", name = "item2.txt", parentId = "p2", isSelect = true)
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Search("0", "query", fakeStringProvider),
                currentCid = "0",
                fileBeanList = listOf(itemP1, itemP2),
                isLongClickState = true
            )
        }

        val renameMap = mapOf("f1" to "item1_renamed.txt", "f2" to "item2_renamed.txt")
        module.batchRename(renameMap)

        val updated = stateHolder.currentContentState.fileBeanList
        assertEquals("item1_renamed.txt", updated.find { it.fileId == "f1" }?.name)
        assertEquals("item2_renamed.txt", updated.find { it.fileId == "f2" }?.name)
        assertFalse(stateHolder.currentContentState.isLongClickState)
        assertTrue(fakeToastProvider.messages.contains("成功重命名 2 个文件"))
    }
}
