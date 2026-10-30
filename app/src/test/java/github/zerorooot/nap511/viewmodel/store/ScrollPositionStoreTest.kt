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
    fun `test clear resets all data`() {
        store.setLocation("folder_1", TestScrollState(5, 50))
        store.setClick("folder_1", 3)

        store.clear()

        assertEquals(0, store.getLocation("folder_1").firstVisibleItemIndex)
        assertEquals(0, store.getClick("folder_1"))
    }
}
