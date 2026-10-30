package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.util.LazyScrollState
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
    fun `test initial state returns default location and click`() {
        val location = store.getLocation("0")
        assertEquals(0, location.firstVisibleItemIndex)
        assertEquals(0, location.firstVisibleItemScrollOffset)
        assertEquals(0, store.getClick("0"))
    }

    private class TestScrollState(
        override val firstVisibleItemIndex: Int,
        override val firstVisibleItemScrollOffset: Int
    ) : LazyScrollState {
        override suspend fun animateScrollToItem(index: Int, scrollOffset: Int) {}
        override suspend fun scrollToItem(index: Int, scrollOffset: Int) {}
        override fun requestScrollToItem(index: Int, scrollOffset: Int) {}
    }

    @Test
    fun `test set and get location`() {
        val state = TestScrollState(firstVisibleItemIndex = 15, firstVisibleItemScrollOffset = 120)
        store.setLocation("folder_1", state)

        val retrieved = store.getLocation("folder_1")
        assertEquals(15, retrieved.firstVisibleItemIndex)
        assertEquals(120, retrieved.firstVisibleItemScrollOffset)

        // other folder remains default
        val other = store.getLocation("folder_2")
        assertEquals(0, other.firstVisibleItemIndex)
        assertEquals(0, other.firstVisibleItemScrollOffset)
    }

    @Test
    fun `test set and get click`() {
        store.setClick("folder_1", 8)
        assertEquals(8, store.getClick("folder_1"))
        assertEquals(0, store.getClick("folder_2"))
    }

    @Test
    fun `test adjustClick delta calculation`() {
        store.setClick("folder_1", 5)
        store.adjustClick("folder_1", -1)
        assertEquals(4, store.getClick("folder_1"))

        store.adjustClick("folder_1", 2)
        assertEquals(6, store.getClick("folder_1"))
    }

    @Test
    fun `test bidirectional mapping between cid and paginationKey for location`() {
        val state1 = TestScrollState(firstVisibleItemIndex = 20, firstVisibleItemScrollOffset = 80)
        // Set with pure cid, retrieve with folder: prefix
        store.setLocation("folder:123", state1)
        val retrievedWithPrefix = store.getLocation("folder:123")
        assertEquals(20, retrievedWithPrefix.firstVisibleItemIndex)
        assertEquals(80, retrievedWithPrefix.firstVisibleItemScrollOffset)

        // Set with folder: prefix, retrieve with pure cid
        val state2 = TestScrollState(firstVisibleItemIndex = 35, firstVisibleItemScrollOffset = 150)
        store.setLocation("folder:456", state2)
        val retrievedPure = store.getLocation("folder:456")
        assertEquals(35, retrievedPure.firstVisibleItemIndex)
        assertEquals(150, retrievedPure.firstVisibleItemScrollOffset)
    }

    @Test
    fun `test bidirectional mapping between cid and paginationKey for click`() {
        store.setClick("123", 7)
        assertEquals(7, store.getClick("123"))

        store.setClick("folder:456", 12)
        assertEquals(12, store.getClick("folder:456"))

        store.adjustClick("123", -2)
        assertEquals(5, store.getClick("123"))
    }

    @Test
    fun `test clear resets all data`() {
        store.setLocation("folder_1", TestScrollState(5, 50))
        store.setClick("folder_1", 3)

        store.clear()

        assertEquals(0, store.getLocation("folder_1").firstVisibleItemIndex)
        assertEquals(0, store.getClick("folder_1"))
    }
}
