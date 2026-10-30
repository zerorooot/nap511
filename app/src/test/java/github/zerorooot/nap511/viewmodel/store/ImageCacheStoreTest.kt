package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.ImageBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImageCacheStoreTest {

    private lateinit var store: ImageCacheStore

    @Before
    fun setUp() {
        store = ImageCacheStore()
    }

    @Test
    fun `test put and get image bean by cid and pickCode`() {
        val bean = ImageBean(originUrl = "http://example.com/img1.jpg")
        store.put("cid_1", "pick_1", bean)

        val retrieved = store.get("cid_1", "pick_1")
        assertNotNull(retrieved)
        assertEquals("http://example.com/img1.jpg", retrieved?.originUrl)

        assertNull(store.get("cid_1", "pick_2"))
        assertNull(store.get("cid_2", "pick_1"))
    }

    @Test
    fun `test tryMarkLoading and removeLoading`() {
        assertTrue(store.tryMarkLoading("pick_1"))
        assertFalse(store.tryMarkLoading("pick_1")) // already loading

        store.removeLoading("pick_1")
        assertTrue(store.tryMarkLoading("pick_1")) // can mark again
    }

    @Test
    fun `test remove single image`() {
        val bean = ImageBean(originUrl = "http://example.com/img1.jpg")
        store.put("cid_1", "pick_1", bean)
        store.remove("cid_1", "pick_1")

        assertNull(store.get("cid_1", "pick_1"))
    }

    @Test
    fun `test clear specific cid cache`() {
        store.put("cid_1", "pick_1", ImageBean(originUrl = "url1"))
        store.put("cid_2", "pick_2", ImageBean(originUrl = "url2"))

        store.clear("cid_1")

        assertNull(store.get("cid_1", "pick_1"))
        assertNotNull(store.get("cid_2", "pick_2"))
    }
}
