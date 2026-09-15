package github.zerorooot.nap511.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleStyleBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.VideoUiState
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.bus.DialogEvent
import github.zerorooot.nap511.util.bus.DialogEventBus
import github.zerorooot.nap511.util.network.parseOssErrorWithDom
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.util.subtitle.SubtitleDelegate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType
import java.io.File

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

class VideoViewModel : ViewModel() {
    internal val fileRepository: FileRepository = FileRepository.getInstance()
    internal val subtitleRepository: SubtitleRepository = SubtitleRepository.getInstance()

    private val subtitleDelegate = SubtitleDelegate(subtitleRepository) { newSubtitleState ->
        _uiState.update { it.copy(subtitle = newSubtitleState) }
    }

    lateinit var launchVideoParams: LaunchVideoParams
        private set

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    val videoList: List<VideoBean>
        get() = if (::launchVideoParams.isInitialized) launchVideoParams.videoList else emptyList()

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
        val currentIndex = initialVideoInfo.index
        val listSize = params.videoList.size

        _uiState.update { currentState ->
            currentState.copy(
                videoInfo = initialVideoInfo,
                fileBeanIndex = currentIndex,
                hasPrev = currentIndex - 1 in 0 until listSize,
                hasNext = currentIndex + 1 in 0 until listSize
            )
        }

        if (isAutoRotate) {
            if (initialVideoInfo.width < initialVideoInfo.height && isPortrait) {
                viewModelScope.launch {
                    _uiEvent.emit(VideoUiEvent.RotateScreen)
                }
            }
        }
    }

    fun playNextVideo(isNext: Boolean, isPortrait: Boolean, currentPositionMs: Long) {
        val currentIndex = _uiState.value.fileBeanIndex
        val nextIndex = if (isNext) currentIndex + 1 else currentIndex - 1
        playVideoAtIndex(nextIndex, isPortrait, currentPositionMs)
    }

    fun playVideoAtIndex(targetIndex: Int, isPortrait: Boolean, currentPositionMs: Long) {
        if (targetIndex == _uiState.value.fileBeanIndex) return
        val fileBean = launchVideoParams.videoList.getOrNull(targetIndex)
        if (fileBean == null) {
            App.instance.toast("找不到视频")
            return
        }
        val videoName = fileBean.name
        val listSize = launchVideoParams.videoList.size
        _uiState.update { currentState ->
            currentState.copy(
                fileBeanIndex = targetIndex,
                hasPrev = targetIndex - 1 in 0 until listSize,
                hasNext = targetIndex + 1 in 0 until listSize
            )
        }
        // 默认将视频文件名去除后缀作为初始搜索关键字
        val keyword = if (videoName.contains(".")) {
            videoName.substringBeforeLast(".")
        } else {
            videoName
        }

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
                        index = targetIndex,
                        fileName = name,
                        pickCode = pickCode,
                        videoUrl = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
                    )
                }
                _uiState.update {
                    it.copy(
                        videoInfo = video,
                        subtitle = it.subtitle.copy(
                            searchedSubtitle = false,
                            defaultSearchKeyword = keyword
                        )
                    )
                }
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
                val currentInfo = _uiState.value.videoInfo ?: return@launch
                val video = fileRepository.video(currentInfo.pickCode)
                XLog.i("playNewVideo $video")
                _uiEvent.emit(VideoUiEvent.PlayNext(video.downloadUrl, video.fileName))
            } catch (e: Exception) {
                isReloadingVideo = false
            }
        }
    }

    suspend fun updateVideoHistory(currentPositionMs: Long) {
        val currentInfo = _uiState.value.videoInfo ?: return
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
        currentPositionMs: Long, url: String, contentType: MediaType, errorBody: String
    ): Boolean {
        XLog.e(
            "GSY Player 网络请求 $url 失败: [$contentType] -> Body: ${
                errorBody.replace(
                    "\n", ""
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

        runCatching { Gson().fromJson(errorBody, JsonObject::class.java) }.onSuccess { fromJson ->
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
        runCatching { parseOssErrorWithDom(errorBody).message }.onSuccess { message ->
            back(
                currentPositionMs = currentPositionMs,
                toast = message,
                resultCode = Activity.RESULT_CANCELED
            )
            return true
        }

        return false
    }

    // --- 字幕业务函数代理 ---

    /**
     * 首次或默认初始化加载字幕列表（从迅雷 API 与 115 同目录获取）
     */
    fun loadSubtitles(videoDurationMs: Long) {
        val currentInfo = _uiState.value.videoInfo ?: return
        subtitleDelegate.loadSubtitles(
            scope = viewModelScope,
            mediaName = currentInfo.fileName,
            localSubtitles = launchVideoParams.localSubtitleItem,
            mediaDurationMs = videoDurationMs
        )
    }

    /**
     * 根据自定义输入的关键字进行字幕搜索
     */
    fun searchSubtitlesByName(keyword: String, videoDurationMs: Long) {
        val currentInfo = _uiState.value.videoInfo ?: return
        subtitleDelegate.loadSubtitles(
            scope = viewModelScope,
            mediaName = currentInfo.fileName,
            searchKeyword = keyword,
            localSubtitles = launchVideoParams.localSubtitleItem,
            mediaDurationMs = videoDurationMs
        )
    }

    /**
     * 下载选中的字幕文件并回调准备好的本地 File
     */
    fun selectSubtitle(cacheDirFile: File, item: SubtitleItem, onReady: (File) -> Unit) {
        subtitleDelegate.selectSubtitle(
            scope = viewModelScope,
            cacheDirFile = cacheDirFile,
            item = item,
            onSuccess = { srtFile ->
                if (srtFile != null) {
                    onReady(srtFile)
                } else {
                    viewModelScope.launch {
                        _uiEvent.emit(VideoUiEvent.Toast("下载或格式转换字幕失败: ${item.simpleName}"))
                    }
                }
            }
        )
    }

    /**
     * 设置精确的时间偏移量 (ms)
     */
    fun setSubtitleOffset(offsetMs: Long) {
        subtitleDelegate.setSubtitleOffset(offsetMs)
    }

    /**
     * 微调时间偏移量 (ms)
     */
    fun addSubtitleOffset(deltaMs: Long) {
        subtitleDelegate.addSubtitleOffset(deltaMs)
    }

    /**
     * 更新字幕样式（字号、颜色、字体、加粗、底色）
     */
    fun updateSubtitleStyle(styleBean: SubtitleStyleBean) {
        subtitleDelegate.updateSubtitleStyle(styleBean)
    }

    /**
     * 清除/移除当前字幕
     */
    fun removeSubtitle() {
        subtitleDelegate.removeSubtitle()
    }

    /**
     * 上传指定的字幕文件到当前视频所在的 115 目录 (parentCid)
     */
    fun uploadSubtitle(cacheDirFile: File, item: SubtitleItem) {
        val parentCid = launchVideoParams.categoryId
        subtitleDelegate.uploadSubtitleTo115(
            scope = viewModelScope,
            cacheDirFile = cacheDirFile,
            item = item,
            targetCid = parentCid,
            onSuccess = {
                viewModelScope.launch {
                    DialogEventBus.getInstance().emit(DialogEvent.RefreshFileList(parentCid))
                }
            }
        )
    }
}
