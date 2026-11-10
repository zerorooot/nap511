package github.zerorooot.nap511.util.subtitle

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.bean.SubtitleStyleBean
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
import org.junit.Assert.assertFalse
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

    @Test
    fun `test resetForNewMedia clears state and keeps subtitleStyle`() {
        val delegate = SubtitleDelegate(SubtitleRepository.getInstance(), fakeToastProvider)
        // 预设用户自定义字幕样式与偏移
        delegate.updateSubtitleStyle(SubtitleStyleBean(textSizeSp = 30f, isBold = true))
        delegate.setSubtitleOffset(3000L)
        delegate.updateLocalSubtitles(listOf(createSubtitleItem("sub_1", "Sub1.srt")))

        // 切换媒体重置
        delegate.resetForNewMedia("VideoEpisode2")

        val state = delegate.state
        assertTrue(state.subtitles.isEmpty())
        assertNull(state.selectedSubtitle)
        assertTrue(state.currentLocalSubtitles.isEmpty())
        assertTrue(state.entries.isEmpty())
        assertEquals("", state.currentText)
        assertEquals(-1, state.currentIndex)
        assertEquals(0L, state.offsetMs)
        assertFalse(state.searchedSubtitle)
        assertFalse(state.isLoading)
        assertFalse(state.isSearchLoading)
        assertEquals("VideoEpisode2", state.defaultSearchKeyword)

        // 验证用户自定义样式保留
        assertEquals(30f, state.subtitleStyle.textSizeSp)
        assertTrue(state.subtitleStyle.isBold)
    }

    @Test
    fun `test resetForNewMedia cancels in-flight loadSubtitlesJob`() = runTest {
        val delayedRepo = object : SubtitleRepository() {
            override suspend fun getSubtitles(
                searchKeyword: String,
                oneOneFiveSubtitles: List<SubtitleItem>,
                videoDurationMs: Long
            ): List<SubtitleItem> {
                delay(5000)
                return listOf(createSubtitleItem("slow_sub", "SlowSub.srt"))
            }
        }

        val delegate = SubtitleDelegate(delayedRepo, fakeToastProvider)
        // 发起首个视频的字幕加载
        delegate.loadSubtitles(
            scope = this,
            mediaName = "VideoA.mp4",
            searchKeyword = "VideoA",
            localSubtitles = emptyList(),
            mediaDurationMs = 10000L,
            searchedSubtitle = true,
            positionAfterAt = false
        )

        testDispatcher.scheduler.advanceTimeBy(1000)
        assertTrue(delegate.state.isSearchLoading)

        // 切集：重置新媒体
        delegate.resetForNewMedia("VideoB")

        // 推进到慢速搜索理论完成的时间
        testDispatcher.scheduler.advanceUntilIdle()

        // 验证：慢速搜索结果被取消，未覆盖新视频状态
        assertTrue(delegate.state.subtitles.isEmpty())
        assertFalse(delegate.state.isSearchLoading)
        assertEquals("VideoB", delegate.state.defaultSearchKeyword)
    }
}
