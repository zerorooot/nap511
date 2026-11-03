package github.zerorooot.nap511.viewmodel.store

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileUiStateHolderTest {

    private lateinit var holder: FileUiStateHolder
    private val fakeStringProvider = object : AppStringProvider {
        override val defaultAppName: String = "nap511"
        override fun getSearchTitle(keyword: String): String = "搜索: $keyword"
        override fun getFilterTitle(name: String): String = "分类: $name"
        override fun getStarTitle(): String ="星标文件"
    }
    @Before
    fun setUp() {
        holder = FileUiStateHolder(fakeStringProvider)
    }

    @Test
    fun `test initial state initialization`() {
        assertEquals("nap511", holder.currentContentState.appBarTitle)
        assertFalse(holder.currentContentState.isRefreshing)
        assertEquals(FileDialogState.None, holder.currentDialogState.activeDialog)
    }

    @Test
    fun `test updateContentState reducer`() {
        holder.updateContentState {
            copy(
                appBarTitle = "Search: test",
                source = github.zerorooot.nap511.bean.FileListSource.Search("0", "test",fakeStringProvider)
            )
        }

        assertEquals("Search: test", holder.currentContentState.appBarTitle)
        assertTrue(holder.currentContentState.isQueryState)
    }

    @Test
    fun `test updateDialogState and closeDialog`() {
        holder.updateDialogState {
            copy(activeDialog = FileDialogState.CreateFolder)
        }
        assertEquals(FileDialogState.CreateFolder, holder.currentDialogState.activeDialog)

        holder.closeDialog()
        assertEquals(FileDialogState.None, holder.currentDialogState.activeDialog)
    }

    @Test
    fun `test setRefreshing`() {
        holder.setRefreshing(true)
        assertTrue(holder.currentContentState.isRefreshing)

        holder.setRefreshing(false)
        assertFalse(holder.currentContentState.isRefreshing)
    }

    @Test
    fun `test initial state populated from memory cache when available`() = runBlocking {
        XLog.init()
        val tempDir = Files.createTempDirectory("fui_test").toFile()
        FileCacheManager.init(tempDir)
        try {
            val file = FileBean(fileId = "root_file_1", name = "document.pdf", isFolder = false)
            val files = FilesBean(
                fileBeanList = arrayListOf(file),
                cid = "0",
                count = 1,
                order = "file_name",
                path = listOf(PathBean(cid = "0", name = "根目录", pid = "0"))
            )
            FileCacheManager["0"] = files

            val cachedHolder = FileUiStateHolder(fakeStringProvider)
            assertEquals("0", cachedHolder.currentContentState.currentCid)
            assertEquals(1, cachedHolder.currentContentState.fileBeanList.size)
            assertEquals("document.pdf", cachedHolder.currentContentState.fileBeanList[0].name)
            assertFalse(cachedHolder.currentContentState.isRefreshing)
        } finally {
            FileCacheManager.clearAll()
            tempDir.deleteRecursively()
        }
    }
}
