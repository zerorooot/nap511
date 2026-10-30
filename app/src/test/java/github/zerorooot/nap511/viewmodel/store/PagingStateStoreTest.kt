package github.zerorooot.nap511.viewmodel.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PagingStateStoreTest {

    private lateinit var store: PagingStateStore

    @Before
    fun setUp() {
        store = PagingStateStore()
    }

    @Test
    fun `test complete state flags`() {
        assertFalse(store.isComplete("cid_0"))
        store.markComplete("cid_0")
        assertTrue(store.isComplete("cid_0"))
        assertFalse(store.isComplete("cid_1"))
    }

    @Test
    fun `test physical offset tracking`() {
        assertEquals(30, store.getOrInitOffset("key_1", 30))
        // subsequent getOrInit returns existing offset
        assertEquals(30, store.getOrInitOffset("key_1", 60))

        store.setOffset("key_1", 60)
        assertEquals(60, store.getOrInitOffset("key_1", 0))
    }

    @Test
    fun `test fetch lock concurrency simulation`() {
        assertFalse(store.isFetchingMore)

        assertTrue(store.tryAcquireFetch("key_1"))
        assertTrue(store.isFetchingMore)

        // re-entrant fetch on same key fails
        assertFalse(store.tryAcquireFetch("key_1"))

        // fetch on different key succeeds
        assertTrue(store.tryAcquireFetch("key_2"))

        store.releaseFetch("key_1")
        assertTrue(store.isFetchingMore)

        store.releaseFetch("key_2")
        assertFalse(store.isFetchingMore)
    }

    @Test
    fun `test reset clears state for specific key`() {
        store.markComplete("key_1")
        store.setOffset("key_1", 100)
        store.tryAcquireFetch("key_1")

        store.reset("key_1")

        assertFalse(store.isComplete("key_1"))
        assertEquals(20, store.getOrInitOffset("key_1", 20))
        assertFalse(store.isFetchingMore)
    }
}
