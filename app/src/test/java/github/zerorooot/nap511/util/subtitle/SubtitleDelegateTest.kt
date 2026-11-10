package github.zerorooot.nap511.util.subtitle

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleDelegateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeToastProvider = FakeToastProvider()

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {
        }
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSubtitleItem(id: String, name: String): SubtitleItem {
        return SubtitleItem(
            id = id,
            name = name,
            simpleName = name,
            sourceType = SubtitleSourceType.ONE_ONE_FIVE,
            pickCode = "pc_$id",
            fileId = "fid_$id",
            ext = "srt",
            durationMs = 120000L
        )
    }

    @Test
    fun `test removeSubtitle resets offsetMs to zero and clears subtitle state`() {
        val delegate = SubtitleDelegate(SubtitleRepository.getInstance(), fakeToastProvider)
        delegate.setSubtitleOffset(1500L)
        assertEquals(1500L, delegate.state.offsetMs)

        delegate.removeSubtitle()

        assertEquals(0L, delegate.state.offsetMs)
        assertNull(delegate.state.selectedSubtitle)
        assertTrue(delegate.state.entries.isEmpty())
        assertEquals("", delegate.state.currentText)
        assertEquals(-1, delegate.state.currentIndex)
    }

    @Test
    fun `test selectSubtitle cancels previous in-flight job on fast switch`() = runTest {
        val tempDir = File.createTempFile("test_sub", "").parentFile ?: File(".")
        val srtFileB = File(tempDir, "test_b.srt").apply {
            writeText("1\n00:00:01,000 --> 00:00:05,000\nHello World\n\n")
            deleteOnExit()
        }

        val delayedRepo = object : SubtitleRepository() {
            override suspend fun downloadAndPrepareSubtitle(
                cacheDirFile: File,
                item: SubtitleItem
            ): File? {
                if (item.id == "sub_a") {
                    // 模拟 A 字幕网络耗时较长
                    delay(5000)
                    return null
                }
                return srtFileB
            }
        }

        val delegate = SubtitleDelegate(delayedRepo, fakeToastProvider)
        val itemA = createSubtitleItem("sub_a", "Song_A.srt")
        val itemB = createSubtitleItem("sub_b", "Song_B.srt")

        var aSuccessCalled = false
        var bSuccessCalled = false

        // 1. 触发 A
        delegate.selectSubtitle(this, tempDir, itemA) {
            aSuccessCalled = true
        }

        // 2. 推进少许时间，A 仍在下载中
        testDispatcher.scheduler.advanceTimeBy(1000)

        // 3. 快速切歌触发 B，A 应该被取消
        delegate.selectSubtitle(this, tempDir, itemB) {
            bSuccessCalled = true
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // 验证：A 被取消，回调未被执行；B 成功更新
        assertTrue(!aSuccessCalled)
        assertTrue(bSuccessCalled)
        assertEquals("sub_b", delegate.state.selectedSubtitle?.id)
        assertEquals(1, delegate.state.entries.size)
        assertEquals("Hello World", delegate.state.entries[0].text)

        srtFileB.delete()
    }

    @Test
    fun `test removeSubtitle cancels in-flight download job`() = runTest {
        val tempDir = File.createTempFile("test_sub2", "").parentFile ?: File(".")
        val repoEntered = CompletableDeferred<Unit>()

        val delayedRepo = object : SubtitleRepository() {
            override suspend fun downloadAndPrepareSubtitle(
                cacheDirFile: File,
                item: SubtitleItem
            ): File? {
                repoEntered.complete(Unit)
                delay(5000)
                return null
            }
        }

        val delegate = SubtitleDelegate(delayedRepo, fakeToastProvider)
        val itemA = createSubtitleItem("sub_a", "Song_A.srt")

        var successCallbackInvoked = false
        delegate.selectSubtitle(this, tempDir, itemA) {
            successCallbackInvoked = true
        }

        testDispatcher.scheduler.advanceTimeBy(1000)
        // 用户切歌或主动移除字幕
        delegate.removeSubtitle()

        testDispatcher.scheduler.advanceUntilIdle()

        // 验证：回调未被调用，状态保持空
        assertTrue(!successCallbackInvoked)
        assertNull(delegate.state.selectedSubtitle)
        assertEquals(0L, delegate.state.offsetMs)
    }
}
