package github.zerorooot.nap511.viewmodel.module

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderEnum
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.repository.paging.PageAppendResult
import github.zerorooot.nap511.repository.paging.PagingOptions
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.CoilCacheCleaner
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import github.zerorooot.nap511.viewmodel.store.ImageCacheStore
import github.zerorooot.nap511.viewmodel.store.PagingStateStore
import github.zerorooot.nap511.viewmodel.store.ScrollPositionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import com.elvishew.xlog.XLog
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.util.FileCacheManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import java.io.File
import java.nio.file.Files

class FileBrowserModuleTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            XLog.init()
        }
    }

    private lateinit var tempDir: File
    private lateinit var stateHolder: FileUiStateHolder
    private lateinit var pagingStateStore: PagingStateStore
    private lateinit var imageCacheStore: ImageCacheStore
    private lateinit var module: FileBrowserModule
    private val fakeToastProvider = FakeToastProvider()

    private val fakeRepo = object : FileRepository() {
        override suspend fun getFiles(
            cid: String,
            showDir: Int,
            aid: Int,
            asc: Int,
            naturalSort: Int,
            order: String,
            limit: Int,
            format: String,
            offset: Int,
            fcMix: Int
        ): FilesBean {
            return FilesBean(
                fileBeanList = arrayListOf(
                    FileBean(fileId = "f_$cid", name = "item_$cid", isFolder = false)
                ),
                cid = cid,
                count = 1,
                order = order,
                path = listOf(
                    PathBean(cid = "0", name = "根目录", pid = "0"),
                    PathBean(cid = cid, name = "dir_$cid", pid = "0")
                )
            )
        }
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

    private val fakeCoordinator = object : DirectoryPagingCoordinator {
        override suspend fun fetchNextPage(
            source: FileListSource,
            currentList: List<FileBean>,
            currentPhysicalOffset: Int,
            currentTotalCount: Int,
            order: OrderBean,
            fcMix: Int
        ): PageAppendResult {
            return PageAppendResult(emptyList(), currentList, currentTotalCount, currentPhysicalOffset, true)
        }

        override suspend fun fetchDirectory(options: PagingOptions): List<FileBean> = emptyList()
        override fun fetchDirectoryStream(options: PagingOptions): Flow<List<FileBean>> = emptyFlow()
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("fbm_test").toFile()
        FileCacheManager.init(tempDir)
        FileCacheManager.saveRequestCache = true

        stateHolder = FileUiStateHolder("nap511")
        pagingStateStore = PagingStateStore()
        imageCacheStore = ImageCacheStore()
        fakeToastProvider.clear()

        module = FileBrowserModule(
            stateHolder = stateHolder,
            pagingStateStore = pagingStateStore,
            imageCacheStore = imageCacheStore,
            fileRepository = fakeRepo,
            pagingCoordinator = fakeCoordinator,
            stringProvider = fakeStringProvider,
            coilCacheCleaner = fakeCoilCleaner,
            settingUiStateFlow = MutableStateFlow(SettingUiState()),
            toastProvider = fakeToastProvider
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            FileCacheManager.clearAll()
        }
        tempDir.deleteRecursively()
    }

    private fun createFileBean(id: String, name: String, isFolder: Boolean = false, isSelect: Boolean = false): FileBean {
        return FileBean(
            fileId = id,
            name = name,
            isFolder = isFolder,
            isSelect = isSelect,
            pickCode = "pick_$id"
        )
    }

    @Test
    fun `test toggleSelect toggles item selection`() {
        val item1 = createFileBean("1", "item1")
        val item2 = createFileBean("2", "item2")
        stateHolder.updateContentState {
            copy(fileBeanList = listOf(item1, item2))
        }

        // 切换第 0 项选中
        module.toggleSelect(0)
        var list = stateHolder.currentContentState.fileBeanList
        assertTrue(list[0].isSelect)
        assertFalse(list[1].isSelect)
        assertEquals("1", stateHolder.currentContentState.appBarTitle)

        // 再次切换，取消选中
        module.toggleSelect(0)
        list = stateHolder.currentContentState.fileBeanList
        assertFalse(list[0].isSelect)
        assertEquals("nap511", stateHolder.currentContentState.appBarTitle)
    }

    @Test
    fun `test startMultiSelect and clearSelection`() {
        val item1 = createFileBean("1", "item1")
        val item2 = createFileBean("2", "item2")
        stateHolder.updateContentState {
            copy(fileBeanList = listOf(item1, item2))
        }

        // 启动长按多选
        module.startMultiSelect(1)
        var content = stateHolder.currentContentState
        assertTrue(content.fileBeanList[1].isSelect)
        assertTrue(content.isLongClickState)
        assertEquals("1", content.appBarTitle)

        // 清空选择
        module.clearSelection()
        content = stateHolder.currentContentState
        assertFalse(content.fileBeanList.any { it.isSelect })
        assertFalse(content.isLongClickState)
        assertEquals("nap511", content.appBarTitle)
    }

    @Test
    fun `test selectReverse`() {
        val item1 = createFileBean("1", "item1", isSelect = true)
        val item2 = createFileBean("2", "item2", isSelect = false)
        stateHolder.updateContentState {
            copy(fileBeanList = listOf(item1, item2))
        }

        module.selectReverse()
        val list = stateHolder.currentContentState.fileBeanList
        assertFalse(list[0].isSelect)
        assertTrue(list[1].isSelect)
        assertEquals("1", stateHolder.currentContentState.appBarTitle)
    }

    @Test
    fun `test selectToUp and selectToDown`() {
        val items = (0..4).map { createFileBean(it.toString(), "item$it") }
        stateHolder.updateContentState {
            // 将中间索引 2 选中
            copy(fileBeanList = items.mapIndexed { idx, item ->
                if (idx == 2) item.copy(isSelect = true) else item
            })
        }

        // 向下全选：索引 2 之后全选
        module.selectToDown()
        var list = stateHolder.currentContentState.fileBeanList
        assertFalse(list[0].isSelect)
        assertFalse(list[1].isSelect)
        assertTrue(list[2].isSelect)
        assertTrue(list[3].isSelect)
        assertTrue(list[4].isSelect)

        // 重新设置仅索引 2 选中并向上全选
        stateHolder.updateContentState {
            copy(fileBeanList = items.mapIndexed { idx, item ->
                if (idx == 2) item.copy(isSelect = true) else item
            })
        }
        module.selectToUp()
        list = stateHolder.currentContentState.fileBeanList
        assertTrue(list[0].isSelect)
        assertTrue(list[1].isSelect)
        assertTrue(list[2].isSelect)
        assertFalse(list[3].isSelect)
        assertFalse(list[4].isSelect)
    }

    @Test
    fun `test dialog open operations`() {
        // 排序弹窗
        module.openFileOrderDialog()
        assertEquals(FileDialogState.FileOrder, stateHolder.currentDialogState.activeDialog)
        stateHolder.closeDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)

        // 搜索弹窗
        module.openSearchDialog()
        assertEquals(FileDialogState.Search, stateHolder.currentDialogState.activeDialog)
        stateHolder.closeDialog()
        assertEquals(FileDialogState.None, stateHolder.currentDialogState.activeDialog)
    }

    @Test
    fun `test currentOrderBean and currentFcMix`() = runTest {
        // 初始 Folder 源
        val normalOrder = OrderBean(OrderEnum.name, 1)
        stateHolder.updateContentState { copy(orderBean = normalOrder) }
        assertEquals(normalOrder, module.currentOrderBean)

        // 切换为 Filter 源，应返回 Filter 专有排序
        val filterOrder = OrderBean(OrderEnum.size, 0)
        stateHolder.updateContentState {
            copy(source = FileListSource.Filter("0", 4, "video", filterOrder, 0))
        }
        assertEquals(filterOrder, module.currentOrderBean)
    }

    @Test
    fun `test getFiles caches fetched files into FileCacheManager`() = runTest {
        val cid = "folder_test_100"
        assertFalse(FileCacheManager.containsKey(cid))

        // 发起获取文件请求，触发网络拉取
        module.getFiles(cid)

        // 验证：成功获取后不仅更新 UI 状态，还必须自动写入 FileCacheManager
        assertTrue("FileCacheManager 应包含该文件夹缓存", FileCacheManager.containsKey(cid))
        val cached = FileCacheManager.getDate(cid)
        assertEquals(1, cached?.fileBeanList?.size)
        assertEquals("f_$cid", cached?.fileBeanList?.first()?.fileId)
        assertEquals(cid, stateHolder.currentContentState.currentCid)
    }

    @Test
    fun `test back navigates to parent folder and restores from cache`() = runTest {
        val rootCid = "0"
        val childCid = "child_folder_200"

        // 先预填充父目录和子目录缓存
        val rootFiles = FilesBean(
            fileBeanList = arrayListOf(FileBean(fileId = "f_root", name = "root_item", isFolder = false)),
            cid = rootCid,
            count = 1,
            order = "",
            path = listOf(PathBean(cid = rootCid, name = "根目录", pid = rootCid))
        )
        val childFiles = FilesBean(
            fileBeanList = arrayListOf(FileBean(fileId = "f_child", name = "child_item", isFolder = false)),
            cid = childCid,
            count = 1,
            order = "",
            path = listOf(
                PathBean(cid = rootCid, name = "根目录", pid = rootCid),
                PathBean(cid = childCid, name = "子目录", pid = rootCid)
            )
        )
        FileCacheManager.put(rootCid, rootFiles)
        FileCacheManager.put(childCid, childFiles)

        // 模拟当前处于子目录
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder(childCid),
                currentCid = childCid,
                pathList = childFiles.path,
                fileBeanList = childFiles.fileBeanList
            )
        }

        // 触发返回上一级
        val backResult = module.back()
        assertEquals(BackResult.NavigateTo(rootCid), backResult)
    }

    @Test
    fun `test back returns NavigateTo when parent cache is missing`() = runTest {
        val rootCid = "0"
        val childCid = "child_no_cache"
        val childFiles = FilesBean(
            fileBeanList = arrayListOf(FileBean(fileId = "f_child", name = "child_item", isFolder = false)),
            cid = childCid,
            count = 1,
            order = "",
            path = listOf(
                PathBean(cid = rootCid, name = "根目录", pid = rootCid),
                PathBean(cid = childCid, name = "子目录", pid = rootCid)
            )
        )
        // Ensure rootCid is not in cache
        FileCacheManager.remove(rootCid)

        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder(childCid),
                currentCid = childCid,
                pathList = childFiles.path,
                fileBeanList = childFiles.fileBeanList
            )
        }

        val backResult = module.back()
        assertEquals(BackResult.NavigateTo(rootCid), backResult)
    }

    @Test
    fun `test back returns NotHandled when at root directory`() = runTest {
        stateHolder.updateContentState {
            copy(
                source = FileListSource.Folder("0"),
                currentCid = "0",
                pathList = listOf(PathBean(cid = "0", name = "根目录", pid = "0")),
                fileBeanList = emptyList()
            )
        }

        val backResult = module.back()
        assertEquals(BackResult.NotHandled, backResult)
    }

    @Test
    fun `test getFiles loads from disk cache when memory cache is cleared`() = runTest {
        val cid = "folder_disk_only"
        val diskFiles = FilesBean(
            fileBeanList = arrayListOf(FileBean(fileId = "f_disk", name = "disk_saved_item", isFolder = false)),
            cid = cid,
            count = 1,
            order = "",
            path = listOf(PathBean(cid = cid, name = "磁盘目录", pid = "0"))
        )
        // 存入缓存并落盘
        FileCacheManager.put(cid, diskFiles)
        val diskFile = File(tempDir, "$cid.json")
        for (i in 0 until 50) {
            if (diskFile.exists() && diskFile.length() > 0) break
            Thread.sleep(20)
        }
        // 清空内存缓存，模拟冷启动或 LRU 内存淘汰
        FileCacheManager.clearMemoryOnly()

        assertFalse("内存缓存此时不应存在", FileCacheManager.containsKey(cid))

        // 通过 getFiles 加载该目录，应从磁盘成功读出而非依赖内存
        module.getFiles(cid)

        assertEquals(cid, stateHolder.currentContentState.currentCid)
        assertEquals(1, stateHolder.currentContentState.fileBeanList.size)
        assertEquals("f_disk", stateHolder.currentContentState.fileBeanList.first().fileId)
    }

    @Test
    fun `test updateOrder in search mode emits toast`() = runTest {
        stateHolder.updateContentState {
            copy(source = FileListSource.Search("0", "key"))
        }
        module.updateOrder(OrderBean(OrderEnum.update, 1))
        assertTrue(fakeToastProvider.messages.contains("暂不支持搜索模式排序"))
    }
}
