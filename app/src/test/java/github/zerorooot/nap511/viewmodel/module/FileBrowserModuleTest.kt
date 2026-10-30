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

class FileBrowserModuleTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            XLog.init()
        }
    }

    private lateinit var stateHolder: FileUiStateHolder
    private lateinit var pagingStateStore: PagingStateStore
    private lateinit var imageCacheStore: ImageCacheStore
    private lateinit var module: FileBrowserModule

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
        stateHolder = FileUiStateHolder("nap511")
        pagingStateStore = PagingStateStore()
        imageCacheStore = ImageCacheStore()

        module = FileBrowserModule(
            stateHolder = stateHolder,
            pagingStateStore = pagingStateStore,
            imageCacheStore = imageCacheStore,
            fileRepository = FileRepository(),
            pagingCoordinator = fakeCoordinator,
            stringProvider = fakeStringProvider,
            coilCacheCleaner = fakeCoilCleaner,
            settingUiStateFlow =  MutableStateFlow(SettingUiState())
        )
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
}
