package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
}
