package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.LocationBean
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScrollPositionStoreTest {

    private lateinit var store: ScrollPositionStore

    @Before
    fun setUp() {
        store = ScrollPositionStore()
    }

    @Test
    fun `test initial state returns default location and empty highlight`() {
        val location = store.getLocation(FileListSource.folderKey("0"))
        assertEquals(0, location.firstVisibleItemIndex)
        assertEquals(0, location.firstVisibleItemScrollOffset)
        assertEquals(null, store.getHighlight(FileListSource.folderKey("0")))
    }

    @Test
    fun `test set and get location`() {
        val locationBean = LocationBean(firstVisibleItemIndex = 15, firstVisibleItemScrollOffset = 120)
        store.setLocation(FileListSource.folderKey("1"), locationBean)

        val retrieved = store.getLocation(FileListSource.folderKey("1"))
        assertEquals(15, retrieved.firstVisibleItemIndex)
        assertEquals(120, retrieved.firstVisibleItemScrollOffset)

        // other key remains default
        val other = store.getLocation(FileListSource.folderKey("2"))
        assertEquals(0, other.firstVisibleItemIndex)
        assertEquals(0, other.firstVisibleItemScrollOffset)
    }

    @Test
    fun `test set and get highlight`() {
        store.setHighlight(FileListSource.folderKey("1"), "fid_1001")
        assertEquals("fid_1001", store.getHighlight(FileListSource.folderKey("1")))
        assertEquals(null, store.getHighlight(FileListSource.folderKey("2")))
    }

    @Test
    fun `test clearHighlight removes specific highlight`() {
        store.setHighlight(FileListSource.folderKey("1"), "fid_1001")
        assertEquals("fid_1001", store.getHighlight(FileListSource.folderKey("1")))

        store.clearHighlight(FileListSource.folderKey("1"))
        assertEquals(null, store.getHighlight(FileListSource.folderKey("1")))
    }

    @Test
    fun `test store keys are stored and retrieved as exact pagination keys`() {
        val loc = LocationBean(firstVisibleItemIndex = 20, firstVisibleItemScrollOffset = 80)
        store.setLocation(FileListSource.folderKey("123"), loc)
        val retrieved = store.getLocation(FileListSource.folderKey("123"))
        assertEquals(20, retrieved.firstVisibleItemIndex)
        assertEquals(80, retrieved.firstVisibleItemScrollOffset)

        store.setHighlight("category:video", "fid_video_99")
        assertEquals("fid_video_99", store.getHighlight("category:video"))
    }

    @Test
    fun `test clear resets all data`() {
        store.setLocation(FileListSource.folderKey("1"), LocationBean(5, 50))
        store.setHighlight(FileListSource.folderKey("1"), "fid_1001")

        store.clear()

        assertEquals(0, store.getLocation(FileListSource.folderKey("1")).firstVisibleItemIndex)
        assertEquals(null, store.getHighlight(FileListSource.folderKey("1")))
    }
}
