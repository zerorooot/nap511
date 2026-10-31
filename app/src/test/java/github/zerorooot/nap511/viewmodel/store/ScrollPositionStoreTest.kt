package github.zerorooot.nap511.viewmodel.store

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
    fun `test initial state returns default location and click`() {
        val location = store.getLocation("folder:0")
        assertEquals(0, location.firstVisibleItemIndex)
        assertEquals(0, location.firstVisibleItemScrollOffset)
        assertEquals(0, store.getClick("folder:0"))
    }

    @Test
    fun `test set and get location`() {
        val locationBean = LocationBean(firstVisibleItemIndex = 15, firstVisibleItemScrollOffset = 120)
        store.setLocation("folder:1", locationBean)

        val retrieved = store.getLocation("folder:1")
        assertEquals(15, retrieved.firstVisibleItemIndex)
        assertEquals(120, retrieved.firstVisibleItemScrollOffset)

        // other key remains default
        val other = store.getLocation("folder:2")
        assertEquals(0, other.firstVisibleItemIndex)
        assertEquals(0, other.firstVisibleItemScrollOffset)
    }

    @Test
    fun `test set and get click`() {
        store.setClick("folder:1", 8)
        assertEquals(8, store.getClick("folder:1"))
        assertEquals(0, store.getClick("folder:2"))
    }

    @Test
    fun `test adjustClick delta calculation`() {
        store.setClick("folder:1", 5)
        store.adjustClick("folder:1", -1)
        assertEquals(4, store.getClick("folder:1"))

        store.adjustClick("folder:1", 2)
        assertEquals(6, store.getClick("folder:1"))
    }

    @Test
    fun `test store keys are stored and retrieved as exact pagination keys`() {
        val loc = LocationBean(firstVisibleItemIndex = 20, firstVisibleItemScrollOffset = 80)
        store.setLocation("folder:123", loc)
        val retrieved = store.getLocation("folder:123")
        assertEquals(20, retrieved.firstVisibleItemIndex)
        assertEquals(80, retrieved.firstVisibleItemScrollOffset)

        store.setClick("category:video", 12)
        assertEquals(12, store.getClick("category:video"))

        store.adjustClick("category:video", -2)
        assertEquals(10, store.getClick("category:video"))
    }

    @Test
    fun `test clear resets all data`() {
        store.setLocation("folder:1", LocationBean(5, 50))
        store.setClick("folder:1", 3)

        store.clear()

        assertEquals(0, store.getLocation("folder:1").firstVisibleItemIndex)
        assertEquals(0, store.getClick("folder:1"))
    }
}
