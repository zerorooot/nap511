package github.zerorooot.nap511.viewmodel.module

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderEnum
import github.zerorooot.nap511.bean.OrderType
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
    private val settingFlow = MutableStateFlow(SettingUiState())

    private val fakeRepo = object : FileRepository() {
        var orderCallCount = 0
        var lastOrderBody: Map<String, String>? = null

        override suspend fun order(body: Map<String, String>): BaseReturnMessage {
            orderCallCount++
            lastOrderBody = body
            return BaseReturnMessage(state = true)
        }

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
        override fun getStarTitle(): String ="星标文件"
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

        stateHolder = FileUiStateHolder(fakeStringProvider)
        pagingStateStore = PagingStateStore()
        imageCacheStore = ImageCacheStore()
        fakeToastProvider.clear()
        fakeRepo.orderCallCount = 0
        settingFlow.value = SettingUiState(keepFoldersOnTop = true)

        module = FileBrowserModule(
            stateHolder = stateHolder,
            pagingStateStore = pagingStateStore,
            imageCacheStore = imageCacheStore,
            fileRepository = fakeRepo,
            pagingCoordinator = fakeCoordinator,
            stringProvider = fakeStringProvider,
            coilCacheCleaner = fakeCoilCleaner,
            settingUiStateFlow = settingFlow,
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

    private fun createFileBean(
        id: String,
        name: String,
        isFolder: Boolean = false,
        isSelect: Boolean = false,
        star: Int = -1,
        playLong: Double = 0.0
    ): FileBean {
        return FileBean(
            fileId = id,
            name = name,
            isFolder = isFolder,
            isSelect = isSelect,
            pickCode = "pick_$id",
            star = star,
            isStar = star == 1,
            playLong = playLong
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
        assertEquals("0", stateHolder.currentContentState.appBarTitle)
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
    fun `test toggleSelectAll toggles between select all and zero selection keeping multi-select mode`() {
        val items = (1..3).map { createFileBean(it.toString(), "item$it") }
        stateHolder.updateContentState {
            copy(fileBeanList = items)
        }

        // 第一次调用：未全选 -> 全选
        module.toggleSelectAll()
        var content = stateHolder.currentContentState
        assertTrue(content.isLongClickState)
        assertEquals("3", content.appBarTitle)
        assertTrue(content.fileBeanList.all { it.isSelect })

        // 第二次调用：已全选 -> 0 项选中（保持多选态，不自动退出）
        module.toggleSelectAll()
        content = stateHolder.currentContentState
        assertTrue(content.isLongClickState)
        assertEquals("0", content.appBarTitle)
        assertTrue(content.fileBeanList.none { it.isSelect })

        // 在 0 选状态下调用 back：应正确退出多选模式
        val backResult = module.back()
        assertEquals(BackResult.Handled, backResult)
        assertFalse(stateHolder.currentContentState.isLongClickState)
        assertEquals("nap511", stateHolder.currentContentState.appBarTitle)
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
            copy(source = FileListSource.Filter("0", 4, "video", filterOrder, 0,fakeStringProvider))
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
                source = FileListSource.Folder(childCid,fakeStringProvider),
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
                source = FileListSource.Folder(childCid,fakeStringProvider),
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
                source = FileListSource.Folder("0",fakeStringProvider),
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
            copy(source = FileListSource.Search("0", "key",fakeStringProvider))
        }
        module.updateOrder(OrderBean(OrderEnum.update, 1),1)
        assertTrue(fakeToastProvider.messages.contains("暂不支持搜索模式排序"))
    }

    @Test
    fun `test local sort by star file descending with keepFoldersOnTop`() = runTest {
        settingFlow.value = SettingUiState(keepFoldersOnTop = true)
        val f1 = createFileBean("1", "unstarred_folder", isFolder = true, star = 0)
        val f2 = createFileBean("2", "starred_folder", isFolder = true, star = 1)
        val file1 = createFileBean("3", "unstarred_file", isFolder = false, star = 0)
        val file2 = createFileBean("4", "starred_file", isFolder = false, star = 1)

        stateHolder.updateContentState { copy(fileBeanList = listOf(f1, file1, f2, file2)) }

        module.updateOrder(OrderBean(OrderEnum.starFile, asc = 0), fcMix = module.currentFcMix)

        val result = stateHolder.currentContentState.fileBeanList
        // 文件夹优先（标星在未标星前），文件次之（标星在未标星前）
        assertEquals(listOf("2", "1", "4", "3"), result.map { it.fileId })
        // 本地排序不调用服务端 order 接口
        assertEquals(0, fakeRepo.orderCallCount)
        // 本地排序纯内存重排，不改写全局 orderBean（保持原有服务端排序）
        assertEquals(OrderEnum.name, stateHolder.currentContentState.orderBean.type)
    }

    @Test
    fun `test local sort by star file ascending with keepFoldersOnTop`() = runTest {
        settingFlow.value = SettingUiState(keepFoldersOnTop = true)
        val f1 = createFileBean("1", "unstarred_folder", isFolder = true, star = 0)
        val f2 = createFileBean("2", "starred_folder", isFolder = true, star = 1)
        val file1 = createFileBean("3", "unstarred_file", isFolder = false, star = 0)
        val file2 = createFileBean("4", "starred_file", isFolder = false, star = 1)

        stateHolder.updateContentState { copy(fileBeanList = listOf(f2, file2, f1, file1)) }

        module.updateOrder(OrderBean(OrderEnum.starFile, asc = 1), fcMix = module.currentFcMix)

        val result = stateHolder.currentContentState.fileBeanList
        // 文件夹优先（未标星在标星前），文件次之（未标星在标星前）
        assertEquals(listOf("1", "2", "3", "4"), result.map { it.fileId })
        assertEquals(0, fakeRepo.orderCallCount)
        assertEquals(OrderEnum.name, stateHolder.currentContentState.orderBean.type)
    }

    @Test
    fun `test local sort by star file without keepFoldersOnTop`() = runTest {
        settingFlow.value = SettingUiState(keepFoldersOnTop = false)
        val f1 = createFileBean("1", "unstarred_folder", isFolder = true, star = 0)
        val f2 = createFileBean("2", "starred_folder", isFolder = true, star = 1)
        val file1 = createFileBean("3", "unstarred_file", isFolder = false, star = 0)
        val file2 = createFileBean("4", "starred_file", isFolder = false, star = 1)

        stateHolder.updateContentState { copy(fileBeanList = listOf(f1, file1, f2, file2)) }

        module.updateOrder(OrderBean(OrderEnum.starFile, asc = 0), fcMix = module.currentFcMix)

        val result = stateHolder.currentContentState.fileBeanList
        // 混排模式：所有标星排在前面，未标星排在后面
        assertTrue(result[0].isStar && result[1].isStar)
        assertTrue(!result[2].isStar && !result[3].isStar)
        assertEquals(OrderEnum.name, stateHolder.currentContentState.orderBean.type)
    }

    @Test
    fun `test local sort by video duration descending`() = runTest {
        settingFlow.value = SettingUiState(keepFoldersOnTop = true)
        val folder = createFileBean("0", "folder", isFolder = true)
        val v1 = createFileBean("1", "short.mp4", playLong = 60.0)
        val v2 = createFileBean("2", "long.mp4", playLong = 3600.0)
        val v3 = createFileBean("3", "mid.mp4", playLong = 600.0)
        val doc = createFileBean("4", "text.txt", playLong = 0.0)

        stateHolder.updateContentState { copy(fileBeanList = listOf(v1, doc, folder, v2, v3)) }

        module.updateOrder(OrderBean(OrderEnum.videoDuration, asc = 0), fcMix = module.currentFcMix)

        val result = stateHolder.currentContentState.fileBeanList
        // 文件夹置顶，视频按时长从大到小，无时长的文档沉底
        assertEquals(listOf("0", "2", "3", "1", "4"), result.map { it.fileId })
        assertEquals(0, fakeRepo.orderCallCount)
        assertEquals(OrderEnum.name, stateHolder.currentContentState.orderBean.type)
    }

    @Test
    fun `test local sort by video duration ascending`() = runTest {
        settingFlow.value = SettingUiState(keepFoldersOnTop = true)
        val folder = createFileBean("0", "folder", isFolder = true)
        val v1 = createFileBean("1", "short.mp4", playLong = 60.0)
        val v2 = createFileBean("2", "long.mp4", playLong = 3600.0)
        val v3 = createFileBean("3", "mid.mp4", playLong = 600.0)
        val doc = createFileBean("4", "text.txt", playLong = 0.0)

        stateHolder.updateContentState { copy(fileBeanList = listOf(v2, folder, doc, v1, v3)) }

        module.updateOrder(OrderBean(OrderEnum.videoDuration, asc = 1), fcMix = module.currentFcMix)

        val result = stateHolder.currentContentState.fileBeanList
        // 文件夹置顶，视频按时长从小到大，无时长的文档沉底
        assertEquals(listOf("0", "1", "3", "2", "4"), result.map { it.fileId })
        assertEquals(0, fakeRepo.orderCallCount)
        assertEquals(OrderEnum.name, stateHolder.currentContentState.orderBean.type)
    }

    @Test
    fun `test local sort empty state shows toast and interrupts`() = runTest {
        val doc1 = createFileBean("1", "doc1.txt", playLong = 0.0, star = 0)
        val doc2 = createFileBean("2", "doc2.txt", playLong = 0.0, star = 0)
        val initialList = listOf(doc1, doc2)
        stateHolder.updateContentState { copy(fileBeanList = initialList) }

        fakeToastProvider.clear()
        module.updateOrder(OrderBean(OrderEnum.starFile, asc = 0), fcMix = module.currentFcMix)
        assertTrue(fakeToastProvider.messages.contains("当前列表没有星标文件"))
        // 列表未被变更
        assertEquals(initialList, stateHolder.currentContentState.fileBeanList)
        // orderBean 未被变更
        assertEquals(OrderEnum.name, stateHolder.currentContentState.orderBean.type)

        fakeToastProvider.clear()
        module.updateOrder(OrderBean(OrderEnum.videoDuration, asc = 0), fcMix = module.currentFcMix)
        assertTrue(fakeToastProvider.messages.contains("当前列表没有视频文件"))
        assertEquals(initialList, stateHolder.currentContentState.fileBeanList)
        assertEquals(OrderEnum.name, stateHolder.currentContentState.orderBean.type)
    }

    @Test
    fun `test local sort preserves existing server orderBean and does not pollute`() = runTest {
        // 模拟当前服务端排序为修改时间倒序
        stateHolder.updateContentState { copy(orderBean = OrderBean(OrderEnum.update, asc = 0)) }

        val v1 = createFileBean("1", "short.mp4", playLong = 60.0)
        val v2 = createFileBean("2", "long.mp4", playLong = 3600.0)
        stateHolder.updateContentState { copy(fileBeanList = listOf(v1, v2)) }

        module.updateOrder(OrderBean(OrderEnum.videoDuration, asc = 0), fcMix = module.currentFcMix)

        // 内存列表重排：时长大排在前
        assertEquals(listOf("2", "1"), stateHolder.currentContentState.fileBeanList.map { it.fileId })
        // orderBean 必须依然保持修改时间，绝不被本地排序篡改
        assertEquals(OrderEnum.update, stateHolder.currentContentState.orderBean.type)
        assertEquals(0, stateHolder.currentContentState.orderBean.asc)
        // currentOrderBean 用于弹窗回显，必须返回本地排序
        assertEquals(OrderEnum.videoDuration, module.currentOrderBean.type)
        assertEquals(0, module.currentOrderBean.asc)
    }

    @Test
    fun `test local sort echo resets to server order on setFiles`() = runTest {
        val v1 = createFileBean("1", "short.mp4", playLong = 60.0)
        stateHolder.updateContentState { copy(fileBeanList = listOf(v1)) }

        module.updateOrder(OrderBean(OrderEnum.videoDuration, asc = 0), fcMix = module.currentFcMix)
        assertEquals(OrderEnum.videoDuration, module.currentOrderBean.type)

        // 模拟进入新目录，返回服务端数据并触发 setFiles
        module.getFiles("100")

        // 重新进入新目录后，弹窗回显与底层排序均恢复为服务端真实排序
        assertEquals(OrderEnum.name, module.currentOrderBean.type)
        assertEquals(OrderEnum.name, stateHolder.currentContentState.orderBean.type)
    }

    @Test
    fun `test OrderType isLocalOnly flags and OrderBean parse`() {
        assertFalse(OrderType.NAME.isLocalOnly)
        assertFalse(OrderType.SIZE.isLocalOnly)
        assertFalse(OrderType.UPDATE.isLocalOnly)
        assertTrue(OrderType.VIDEO_DURATION.isLocalOnly)
        assertTrue(OrderType.STAR_FILE.isLocalOnly)

        val videoOrder = OrderBean.parseFromUiString("视频时长⬇️")
        assertEquals(OrderEnum.videoDuration, videoOrder.type)
        assertEquals(0, videoOrder.asc)

        val starOrder = OrderBean.parseFromUiString("标星文件⬆️")
        assertEquals(OrderEnum.starFile, starOrder.type)
        assertEquals(1, starOrder.asc)
    }

    @Test
    fun `test updateOrder passes explicit fcMix to repository order`() = runTest {
        // 初始处于 Folder 模式
        stateHolder.updateContentState {
            copy(source = FileListSource.Folder("123", fakeStringProvider), currentCid = "123")
        }

        // 显式传入混排模式 fcMix = 1
        module.updateOrder(OrderBean(OrderEnum.size, asc = 1), fcMix = 1)
        assertEquals(1, fakeRepo.orderCallCount)
        assertEquals("1", fakeRepo.lastOrderBody?.get("fc_mix"))
        assertEquals(OrderEnum.size, fakeRepo.lastOrderBody?.get("user_order"))
        assertEquals("1", fakeRepo.lastOrderBody?.get("user_asc"))

        // 显式传入置顶模式 fcMix = 0
        module.updateOrder(OrderBean(OrderEnum.name, asc = 0), fcMix = 0)
        assertEquals(2, fakeRepo.orderCallCount)
        assertEquals("0", fakeRepo.lastOrderBody?.get("fc_mix"))
        assertEquals(OrderEnum.name, fakeRepo.lastOrderBody?.get("user_order"))
        assertEquals("0", fakeRepo.lastOrderBody?.get("user_asc"))
    }

    @Test
    fun `test local sort respects explicit fcMix parameter`() = runTest {
        // 全局配置设为置顶，但显式向本地排序传递 fcMix = 1（混排）
        settingFlow.value = SettingUiState(keepFoldersOnTop = true)
        val f1 = createFileBean("1", "unstarred_folder", isFolder = true, star = 0)
        val f2 = createFileBean("2", "starred_folder", isFolder = true, star = 1)
        val file1 = createFileBean("3", "unstarred_file", isFolder = false, star = 0)
        val file2 = createFileBean("4", "starred_file", isFolder = false, star = 1)

        stateHolder.updateContentState { copy(fileBeanList = listOf(f1, file1, f2, file2)) }

        // 显式指定 fcMix = 1（混排），即使全局 keepFoldersOnTop 为 true，本地也必须执行混排
        module.updateOrder(OrderBean(OrderEnum.starFile, asc = 0), fcMix = 1)

        val mixedResult = stateHolder.currentContentState.fileBeanList
        // 混排模式：所有标星优先（无论文件夹与否）
        assertEquals(listOf("2", "4", "1", "3"), mixedResult.map { it.fileId })

        // 切换为显式 fcMix = 0（置顶）
        module.updateOrder(OrderBean(OrderEnum.starFile, asc = 0), fcMix = 0)
        val onTopResult = stateHolder.currentContentState.fileBeanList
        // 置顶模式：文件夹优先
        assertEquals(listOf("2", "1", "4", "3"), onTopResult.map { it.fileId })
    }

    @Test
    fun `test currentFilesBeanFcMix priority between cache and currentFcMix`() = runTest {
        val cid = "test_cid"
        stateHolder.updateContentState {
            copy(source = FileListSource.Folder(cid, fakeStringProvider), currentCid = cid)
        }

        // 1. currentFilesBeanFcMix 反映 stateHolder.currentContentState.fcMix 的值
        settingFlow.value = SettingUiState(keepFoldersOnTop = true)
        assertEquals(0, module.currentFcMix)
        assertEquals(0, module.currentFilesBeanFcMix)

        stateHolder.updateContentState { copy(fcMix = 1) }
        assertEquals(1, module.currentFilesBeanFcMix)

        // 2. 从缓存读取数据 (getFiles) 时，更新 stateHolder 并以缓存的 fcMix 为准
        stateHolder.updateContentState { copy(fcMix = 0) }
        val cachedFiles = FilesBean(
            fileBeanList = arrayListOf(),
            cid = cid,
            count = 0,
            order = OrderEnum.name,
            fcMix = 1,
            path = emptyList()
        )
        FileCacheManager.put(cid, cachedFiles)

        module.getFiles(cid)

        // 即使全局设置为 keepFoldersOnTop = true (对应 currentFcMix = 0)，加载缓存后仍返回缓存的 fcMix = 1
        settingFlow.value = SettingUiState(keepFoldersOnTop = true)
        assertEquals(0, module.currentFcMix)
        assertEquals(1, module.currentFilesBeanFcMix)
    }
}
