package github.zerorooot.nap511.viewmodel

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.bean.VideoAttributeBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.VideoStarAction
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            XLog.init()
        }
    }

    private val testDispatcher = StandardTestDispatcher()
    private val fakeToastProvider = FakeToastProvider()
    private lateinit var viewModel: VideoViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = VideoViewModel(fakeToastProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createParams(videoList: List<VideoBean>, initialIndex: Int = 0): LaunchVideoParams {
        val currentVideo = videoList[initialIndex]
        return LaunchVideoParams(
            videoInfo = VideoInfoBean(
                fileName = currentVideo.name,
                pickCode = currentVideo.pickCode,
                index = initialIndex
            ),
            videoAttribute = VideoAttributeBean(),
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

        viewModel.initParams(params, isPortrait = false)
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

        viewModel.initParams(params, isPortrait = false)

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

        viewModel.initParams(params, isPortrait = false)
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

        viewModel.initParams(params, isPortrait = false)

        // 对 v1 加星
        viewModel.toggleCurrentVideoStar()

        // 移除 v1
        viewModel.discardCurrentVideo(isPortrait = false, currentPositionMs = 0L)
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
}
