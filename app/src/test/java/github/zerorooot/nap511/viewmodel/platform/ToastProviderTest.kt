package github.zerorooot.nap511.viewmodel.platform

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.util.runCatchingWithToast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.IOException

class ToastProviderTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            XLog.init()
        }
    }

    private lateinit var toastProvider: FakeToastProvider

    @Before
    fun setUp() {
        toastProvider = FakeToastProvider()
    }

    @Test
    fun `test FakeToastProvider collects and clears messages`() {
        toastProvider.showToast("Test message 1")
        toastProvider.showToast("Test message 2")

        assertEquals(2, toastProvider.messages.size)
        assertEquals("Test message 1", toastProvider.messages[0])
        assertEquals("Test message 2", toastProvider.messages[1])

        toastProvider.clear()
        assertTrue(toastProvider.messages.isEmpty())
    }

    @Test
    fun `test onFailureToastAndLog captures exception message and logs`() {
        val failureResult = Result.failure<String>(IOException("网络连接超时"))

        failureResult.onFailureToastAndLog(toastProvider)

        assertEquals(1, toastProvider.messages.size)
        assertTrue(toastProvider.messages[0].contains("网络连接超时"))
    }

    @Test
    fun `test onFailureToastAndLog with custom message uses custom message`() {
        val failureResult = Result.failure<String>(RuntimeException("Database locked"))

        failureResult.onFailureToastAndLog(toastProvider, customMsg = "操作失败，数据库忙")

        assertEquals(1, toastProvider.messages.size)
        assertEquals("操作失败，数据库忙", toastProvider.messages[0])
    }

    @Test
    fun `test runCatchingWithToast catches exception and emits toast`() {
        val result = runCatchingWithToast(toastProvider, customMsg = "加载失败") {
            throw IllegalStateException("State error")
        }

        assertTrue(result.isFailure)
        assertEquals(1, toastProvider.messages.size)
        assertEquals("加载失败", toastProvider.messages[0])
    }
}
