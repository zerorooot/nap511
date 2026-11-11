package github.zerorooot.nap511.viewmodel

import android.app.Application
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.bean.VideoAttributeBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.VideoStarAction
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
import github.zerorooot.nap511.worker.UnzipQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import org.junit.BeforeClass
import org.junit.Test
import java.lang.reflect.Type

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewModelTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            try {
                XLog.init()
            } catch (_: Throwable) {}
        }
    }

    private val testDispatcher = StandardTestDispatcher()
    private val fakeToastProvider = FakeToastProvider()
    private lateinit var viewModel: VideoViewModel

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        UnzipQueueManager.clear()
        Dispatchers.setMain(testDispatcher)
        viewModel = VideoViewModel(
            application = Application(),
            fileRepository = FileRepository.getInstance(),
            subtitleRepository = SubtitleRepository.getInstance(),
            toastProvider = fakeToastProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createParams(
        videoList: List<VideoBean>,
        initialIndex: Int = 0,
        isAutoRotate: Boolean = false,
        width: Int = 0,
        height: Int = 0,
        userRotate: Int = 0
    ): LaunchVideoParams {
        val currentVideo = videoList[initialIndex]
        return LaunchVideoParams(
            videoInfo = VideoInfoBean(
                fileName = currentVideo.name,
                pickCode = currentVideo.pickCode,
                index = initialIndex,
                width = width,
                height = height,
                userRotate = userRotate
            ),
            videoAttribute = VideoAttributeBean(isAutoRotate = isAutoRotate),
            localSubtitleItem = emptyList(),
            videoList = videoList,
            categoryId = "cat_1"
        )
    }

    @Test
    fun `test toggleCurrentVideoStar adds star to unstarred video`() = runTest {
        val v1 = VideoBean(name = "v1.mp4", pickCode = "p1", fileId = "fid1", isStar = false)
        val v2 = VideoBean(name = "v2.mp4", pickCode = "p2", fileId = "fid2", isStar = true)
        val params = createParams(listOf(v1, v2), initialIndex = 0)

        viewModel.initParams(params)
        assertFalse(viewModel.uiState.value.isCurrentVideoStar)

        // 点击加星
        viewModel.toggleCurrentVideoStar()
        assertTrue(viewModel.uiState.value.isCurrentVideoStar)
        assertTrue(viewModel.videoList[0].isStar)

        // 退出页面并验证 FinishWithResult 中包含 star=true
        var finishEvent: VideoUiEvent.FinishWithResult? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.FinishWithResult) {
                    finishEvent = it
                }
            }
        }

        viewModel.back(0L)
        testDispatcher.scheduler.advanceUntilIdle()

        val type: Type = object : TypeToken<List<VideoStarAction>>() {}.type
        val starActions: List<VideoStarAction> = Gson().fromJson(finishEvent?.starJson, type)
        assertEquals(1, starActions.size)
        assertEquals("fid1", starActions[0].fid)
        assertTrue(starActions[0].star)

        job.cancel()
    }

    @Test
    fun `test toggleCurrentVideoStar twice cancels and yields empty starList`() = runTest {
        val v1 = VideoBean(name = "v1.mp4", pickCode = "p1", fileId = "fid1", isStar = false)
        val params = createParams(listOf(v1), initialIndex = 0)

        viewModel.initParams(params)

        // 加星
        viewModel.toggleCurrentVideoStar()
        assertTrue(viewModel.uiState.value.isCurrentVideoStar)

        // 再次点击取消加星（抵消）
        viewModel.toggleCurrentVideoStar()
        assertFalse(viewModel.uiState.value.isCurrentVideoStar)
        assertFalse(viewModel.videoList[0].isStar)

        var finishEvent: VideoUiEvent.FinishWithResult? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.FinishWithResult) {
                    finishEvent = it
                }
            }
        }

        viewModel.back(0L)
        testDispatcher.scheduler.advanceUntilIdle()

        val type: Type = object : TypeToken<List<VideoStarAction>>() {}.type
        val starActions: List<VideoStarAction> = Gson().fromJson(finishEvent?.starJson, type)
        assertTrue(starActions.isEmpty())

        job.cancel()
    }

    @Test
    fun `test unstarring initially starred video yields star false`() = runTest {
        val v1 = VideoBean(name = "v1.mp4", pickCode = "p1", fileId = "fid1", isStar = true)
        val params = createParams(listOf(v1), initialIndex = 0)

        viewModel.initParams(params)
        assertTrue(viewModel.uiState.value.isCurrentVideoStar)

        // 点击取消加星
        viewModel.toggleCurrentVideoStar()
        assertFalse(viewModel.uiState.value.isCurrentVideoStar)

        var finishEvent: VideoUiEvent.FinishWithResult? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.FinishWithResult) {
                    finishEvent = it
                }
            }
        }

        viewModel.back(0L)
        testDispatcher.scheduler.advanceUntilIdle()

        val type: Type = object : TypeToken<List<VideoStarAction>>() {}.type
        val starActions: List<VideoStarAction> = Gson().fromJson(finishEvent?.starJson, type)
        assertEquals(1, starActions.size)
        assertEquals("fid1", starActions[0].fid)
        assertFalse(starActions[0].star)

        job.cancel()
    }

    @Test
    fun `test starring video then discarding retains star action`() = runTest {
        val v1 = VideoBean(name = "v1.mp4", pickCode = "p1", fileId = "fid1", isStar = false)
        val v2 = VideoBean(name = "v2.mp4", pickCode = "p2", fileId = "fid2", isStar = false)
        val params = createParams(listOf(v1, v2), initialIndex = 0)

        viewModel.initParams(params)

        // 对 v1 加星
        viewModel.toggleCurrentVideoStar()

        // 移除 v1
        viewModel.discardCurrentVideo(currentPositionMs = 0L)
        testDispatcher.scheduler.advanceUntilIdle()

        // 退出页面并检查 starJson 依然保留对 v1 的加星
        var finishEvent: VideoUiEvent.FinishWithResult? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.FinishWithResult) {
                    finishEvent = it
                }
            }
        }

        viewModel.back(0L)
        testDispatcher.scheduler.advanceUntilIdle()

        val type: Type = object : TypeToken<List<VideoStarAction>>() {}.type
        val starActions: List<VideoStarAction> = Gson().fromJson(finishEvent?.starJson, type)
        assertEquals(1, starActions.size)
        assertEquals("fid1", starActions[0].fid)
        assertTrue(starActions[0].star)

        job.cancel()
    }

    @Test
    fun `test autoRotateEnabled with API landscape video emits RequestOrientation landscape`() = runTest {
        val v1 = VideoBean(name = "landscape.mp4", pickCode = "p1")
        val params = createParams(listOf(v1), isAutoRotate = true, width = 1920, height = 1080)

        var orientationEvent: VideoUiEvent.RequestOrientation? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.RequestOrientation) {
                    orientationEvent = it
                }
            }
        }

        viewModel.initParams(params)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, orientationEvent?.targetIsLandscape)
        job.cancel()
    }

    @Test
    fun `test autoRotateEnabled with API portrait video emits RequestOrientation portrait`() = runTest {
        val v1 = VideoBean(name = "portrait.mp4", pickCode = "p1")
        val params = createParams(listOf(v1), isAutoRotate = true, width = 1080, height = 1920)

        var orientationEvent: VideoUiEvent.RequestOrientation? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.RequestOrientation) {
                    orientationEvent = it
                }
            }
        }

        viewModel.initParams(params)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, orientationEvent?.targetIsLandscape)
        job.cancel()
    }

    @Test
    fun `test autoRotateEnabled with 1-to-1 square video does not emit RequestOrientation`() = runTest {
        val v1 = VideoBean(name = "square.mp4", pickCode = "p1")
        val params = createParams(listOf(v1), isAutoRotate = true, width = 1080, height = 1080)

        var orientationEvent: VideoUiEvent.RequestOrientation? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.RequestOrientation) {
                    orientationEvent = it
                }
            }
        }

        viewModel.initParams(params)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(orientationEvent)
        job.cancel()
    }

    @Test
    fun `test autoRotateDisabled does not emit RequestOrientation`() = runTest {
        val v1 = VideoBean(name = "landscape.mp4", pickCode = "p1")
        val params = createParams(listOf(v1), isAutoRotate = false, width = 1920, height = 1080)

        var orientationEvent: VideoUiEvent.RequestOrientation? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.RequestOrientation) {
                    orientationEvent = it
                }
            }
        }

        viewModel.initParams(params)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(orientationEvent)
        job.cancel()
    }

    @Test
    fun `test userRotate 90 degrees flips landscape video to portrait`() = runTest {
        val v1 = VideoBean(name = "user_rotate_90.mp4", pickCode = "p1")
        val params = createParams(listOf(v1), isAutoRotate = true, width = 1920, height = 1080, userRotate = 90)

        var orientationEvent: VideoUiEvent.RequestOrientation? = null
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.RequestOrientation) {
                    orientationEvent = it
                }
            }
        }

        viewModel.initParams(params)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, orientationEvent?.targetIsLandscape)
        job.cancel()
    }

    @Test
    fun `test direct link 0x0 video emits on onRealVideoDimensionsLoaded`() = runTest {
        val v1 = VideoBean(name = "direct_link.mp4", pickCode = "p1")
        val params = createParams(listOf(v1), isAutoRotate = true, width = 0, height = 0)

        val events = mutableListOf<VideoUiEvent.RequestOrientation>()
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.RequestOrientation) {
                    events.add(it)
                }
            }
        }

        viewModel.initParams(params)
        testDispatcher.scheduler.advanceUntilIdle()
        // 初始 0x0 不发射事件
        assertTrue(events.isEmpty())

        // 解码器解码出 1920x1080
        viewModel.onRealVideoDimensionsLoaded(rawWidth = 1920, rawHeight = 1080, effectiveRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(true, events[0].targetIsLandscape)
        job.cancel()
    }

    @Test
    fun `test 90 degree track rotation on onRealVideoDimensionsLoaded corrects landscape to portrait`() = runTest {
        val v1 = VideoBean(name = "rotated_track.mp4", pickCode = "p1")
        val params = createParams(listOf(v1), isAutoRotate = true, width = 0, height = 0)

        val events = mutableListOf<VideoUiEvent.RequestOrientation>()
        val job = launch {
            viewModel.uiEvent.collect {
                if (it is VideoUiEvent.RequestOrientation) {
                    events.add(it)
                }
            }
        }

        viewModel.initParams(params)
        testDispatcher.scheduler.advanceUntilIdle()

        // 第一步：onVideoSizeChanged 先上报 1920x1080, rotation=0 (算作横屏)
        viewModel.onRealVideoDimensionsLoaded(rawWidth = 1920, rawHeight = 1080, effectiveRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, events.size)
        assertEquals(true, events[0].targetIsLandscape)

        // 第二步：onInfo 稍后到达，rotation=90 (换算视觉为 1080x1920，纠偏为竖屏)
        viewModel.onRealVideoDimensionsLoaded(rawWidth = 1920, rawHeight = 1080, effectiveRotation = 90)
        testDispatcher.scheduler.advanceUntilIdle()

        // 验证纠偏事件成功发射
        assertEquals(2, events.size)
        assertEquals(false, events[1].targetIsLandscape)
        job.cancel()
    }
}
