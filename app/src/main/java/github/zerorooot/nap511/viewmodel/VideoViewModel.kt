package github.zerorooot.nap511.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.network.parseOssErrorWithDom
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType

sealed class VideoUiEvent {
    data class PlayNext(val videoUrl: String, val title: String) : VideoUiEvent()
    data class Toast(val message: String) : VideoUiEvent()
    object RotateScreen : VideoUiEvent()
    data class FinishWithResult(
        val resultCode: Int,
        val videoHistoryJson: String,
        val nav: String = "",
        val toast: String = ""
    ) : VideoUiEvent()
}

class VideoViewModel(
    internal val fileRepository: FileRepository = FileRepository.getInstance()
) : ViewModel() {

    lateinit var launchVideoParams: LaunchVideoParams
        private set

    private val _videoInfo = MutableStateFlow<VideoInfoBean?>(null)
    val videoInfo: StateFlow<VideoInfoBean?> = _videoInfo.asStateFlow()

    private val _fileBeanIndex = MutableStateFlow(-1)
//    val fileBeanIndex: StateFlow<Int> = _fileBeanIndex.asStateFlow()

    private val _hasPrev = MutableStateFlow(false)
    val hasPrev: StateFlow<Boolean> = _hasPrev.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _uiEvent = MutableSharedFlow<VideoUiEvent>()
    val uiEvent: SharedFlow<VideoUiEvent> = _uiEvent.asSharedFlow()

    val videoAttribute by lazy { launchVideoParams.videoAttribute }
    val isAutoRotate by lazy { videoAttribute.isAutoRotate }
    val videoLinkMode by lazy { videoAttribute.videoLinkMode }
    val autoJumpRetry by lazy { videoAttribute.autoJumpRetry }
    val hideLoading by lazy { videoAttribute.hideLoading }

    private val videoHistoryMap = mutableMapOf<String, VideoBean>()

    @Volatile
    private var isReloadingVideo = false

    fun initParams(params: LaunchVideoParams, isPortrait: Boolean) {
        launchVideoParams = params
        val initialVideoInfo = params.videoInfo
        _videoInfo.value = initialVideoInfo
        _fileBeanIndex.value = initialVideoInfo.index
        updatePrevNextButtonsState()

        if (isAutoRotate) {
            if (initialVideoInfo.width < initialVideoInfo.height && isPortrait) {
                viewModelScope.launch {
                    _uiEvent.emit(VideoUiEvent.RotateScreen)
                }
            }
        }
    }

    private fun updatePrevNextButtonsState() {
        val currentIndex = _fileBeanIndex.value
        val listSize = launchVideoParams.videoList.size
        _hasPrev.value = currentIndex - 1 in 0 until listSize
        _hasNext.value = currentIndex + 1 in 0 until listSize
    }

    fun playNextVideo(isNext: Boolean, isPortrait: Boolean, currentPositionMs: Long) {
        val currentIndex = _fileBeanIndex.value
        val nextIndex = if (isNext) currentIndex + 1 else currentIndex - 1
        val fileBean = launchVideoParams.videoList.getOrNull(nextIndex)
        if (fileBean == null) {
            App.instance.toast("找不到新视频")
            updatePrevNextButtonsState()
            return
        }
        _fileBeanIndex.value = nextIndex
        updatePrevNextButtonsState()

        viewModelScope.launch {
            runCatching {
                updateVideoHistory(currentPositionMs)

                val pickCode = fileBean.pickCode
                val name = fileBean.name
                val video = if (videoLinkMode) {
                    fileRepository.video(pickCode)
                } else {
                    val (width, height) = if (isPortrait) {
                        1080 to 1920
                    } else {
                        1920 to 1080
                    }
                    VideoInfoBean(
                        width = width,
                        height = height,
                        index = nextIndex,
                        fileName = name,
                        pickCode = pickCode,
                        videoUrl = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
                    )
                }
                _videoInfo.value = video
                _uiEvent.emit(VideoUiEvent.PlayNext(video.videoUrl, video.fileName))
            }.onFailureToastAndLog()
        }
    }

    fun rePlayNewVideo() {
        if (isReloadingVideo) return
        isReloadingVideo = true
        App.instance.toast("视频地址错误！正在重新获取新链接")
        viewModelScope.launch {
            try {
                val currentInfo = _videoInfo.value ?: return@launch
                val video = fileRepository.video(currentInfo.pickCode)
                XLog.i("playNewVideo $video")
                _uiEvent.emit(VideoUiEvent.PlayNext(video.downloadUrl, video.fileName))
            } catch (e: Exception) {
                isReloadingVideo = false
            }
        }
    }

    suspend fun updateVideoHistory(currentPositionMs: Long) {
        val currentInfo = _videoInfo.value ?: return
        val currentDuration = (currentPositionMs / 1000).toInt()
        val pickCode = currentInfo.pickCode
        val name = currentInfo.fileName
        val bean = VideoBean(currentDuration, pickCode)
        videoHistoryMap[pickCode] = bean

        val map = mapOf(
            "op" to "update",
            "pick_code" to pickCode,
            "time" to currentDuration.toString(),
            "category" to "1",
            "format" to "json"
        )
        runCatching {
            val videoHistory = fileRepository.videoHistory(map)
            if (!videoHistory.state) {
                XLog.e("更新视频时间失败！ name: $name, pickCode: $pickCode, result: $videoHistory")
            } else {
                XLog.d("更新视频时间成功 name: $name, pickCode: $pickCode, result: $videoHistory")
            }
        }.onFailure { e ->
            XLog.e("更新视频时间异常 name: $name, pickCode: $pickCode", e)
        }
    }

    fun back(
        currentPositionMs: Long,
        nav: String = "",
        toast: String = "",
        resultCode: Int = Activity.RESULT_OK
    ) {
        viewModelScope.launch {
            updateVideoHistory(currentPositionMs)
            val videoHistoryMapJson = Gson().toJson(videoHistoryMap)
            _uiEvent.emit(
                VideoUiEvent.FinishWithResult(
                    resultCode = resultCode,
                    videoHistoryJson = videoHistoryMapJson,
                    nav = nav,
                    toast = toast
                )
            )
        }
    }

    fun handleInterceptorError(
        currentPositionMs: Long,
        url: String,
        contentType: MediaType,
        errorBody: String
    ): Boolean {
        XLog.e(
            "GSY Player 网络请求 $url 失败: [$contentType] -> Body: ${
                errorBody.replace(
                    "\n",
                    ""
                )
            }"
        )
        if (errorBody.isEmpty()) {
            if (!videoLinkMode && autoJumpRetry) {
                rePlayNewVideo()
                return true
            }

            back(
                currentPositionMs = currentPositionMs,
                toast = "视频地址错误！请打开\"视频解析模式\"请求正确链接",
                resultCode = Activity.RESULT_CANCELED
            )
            return true
        }

        runCatching { Gson().fromJson(errorBody, JsonObject::class.java) }
            .onSuccess { fromJson ->
                if (fromJson.has("error")) {
                    val message = fromJson.get("error").asString
                    back(
                        currentPositionMs = currentPositionMs,
                        nav = "VerifyVideoAccount",
                        toast = message,
                        resultCode = Activity.RESULT_CANCELED
                    )
                    return true
                }
            }
        runCatching { parseOssErrorWithDom(errorBody).message }
            .onSuccess { message ->
                back(
                    currentPositionMs = currentPositionMs,
                    toast = message,
                    resultCode = Activity.RESULT_CANCELED
                )
                return true
            }

        return false
    }
}
