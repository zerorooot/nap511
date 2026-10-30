package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.TorrentFileBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TorrentCacheStoreTest {

    private lateinit var store: TorrentCacheStore

    @Before
    fun setUp() {
        store = TorrentCacheStore()
    }

    @Test
    fun `test put get and check contains`() {
        val torrent = TorrentFileBean(fileCount = 5)
        store["sha1_abc"] = torrent

        assertTrue(store.containsKey("sha1_abc"))
        assertFalse(store.containsKey("sha1_xyz"))

        val retrieved = store["sha1_abc"]
        assertEquals(5, retrieved?.fileCount)
    }

    @Test
    fun `test remove and clear`() {
        store["sha1_1"] = TorrentFileBean(fileCount = 1)
        store["sha1_2"] = TorrentFileBean(fileCount = 2)

        store.remove("sha1_1")
        assertNull(store["sha1_1"])
        assertTrue(store.containsKey("sha1_2"))

        store.clear()
        assertFalse(store.containsKey("sha1_2"))
    }
}
