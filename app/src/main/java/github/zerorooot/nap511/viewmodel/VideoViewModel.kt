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
import github.zerorooot.nap511.util.DialogEvent
import github.zerorooot.nap511.util.DialogEventBus
import github.zerorooot.nap511.util.keyWord
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

/**
 * 视频播放页面的一次性 UI 事件定义
 */
sealed class VideoUiEvent {
    /** 播放下一个视频事件，包含视频链接和标题 */
    data class PlayNext(val videoUrl: String, val title: String) : VideoUiEvent()

    /** 显示 Toast 提示消息事件 */
    data class Toast(val message: String) : VideoUiEvent()

    /** 触发屏幕旋转事件 */
    object RotateScreen : VideoUiEvent()

    /** 结束当前页面并带回结果事件 */
    data class FinishWithResult(
        val resultCode: Int,
        val videoHistoryJson: String,
        val nav: String = "",
        val toast: String = ""
    ) : VideoUiEvent()
}

/**
 * 视频播放页面的 ViewModel
 *
 * 负责管理视频播放状态、切换视频、同步播放历史、处理请求拦截异常以及通过代理 [SubtitleDelegate] 管理字幕业务。
 */
class VideoViewModel : ViewModel() {
    internal val fileRepository: FileRepository = FileRepository.getInstance()
    internal val subtitleRepository: SubtitleRepository = SubtitleRepository.getInstance()

    /** 字幕业务逻辑代理类 */
    private val subtitleDelegate = SubtitleDelegate(subtitleRepository) { newSubtitleState ->
        _uiState.update { it.copy(subtitle = newSubtitleState) }
    }

    /** 启动视频播放页面时的配置参数 */
    lateinit var launchVideoParams: LaunchVideoParams
        private set

    private val _uiState = MutableStateFlow(VideoUiState())
    /** 视频播放页面的 UI 状态流 */
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    /** 当前播放列表中的所有视频 */
    val videoList: List<VideoBean>
        get() = if (::launchVideoParams.isInitialized) launchVideoParams.videoList else emptyList()

    private val _uiEvent = MutableSharedFlow<VideoUiEvent>()
    /** 视频播放页面的一次性 UI 事件流 */
    val uiEvent: SharedFlow<VideoUiEvent> = _uiEvent.asSharedFlow()

    /** 视频配置属性 */
    val videoAttribute by lazy { launchVideoParams.videoAttribute }
    /** 是否根据视频宽高比自动旋转屏幕 */
    val isAutoRotate by lazy { videoAttribute.isAutoRotate }
    /** 视频链接模式（true 为解析模式，false 为直接拼接 m3u8 模式） */
    val videoLinkMode by lazy { videoAttribute.videoLinkMode }
    /** 播放失败时是否自动重试跳转获取新链接 */
    val autoJumpRetry by lazy { videoAttribute.autoJumpRetry }
    /** 是否隐藏加载框 */
    val hideLoading by lazy { videoAttribute.hideLoading }

    /** 记录播放过的视频历史信息（pickCode -> VideoBean） */
    private val videoHistoryMap = mutableMapOf<String, VideoBean>()

    /** 标记是否正在重新加载获取新视频地址，防止重复发起 */
    @Volatile
    private var isReloadingVideo = false

    /**
     * 初始化视频播放参数及 UI 状态
     *
     * @param params 启动视频播放所需参数（包含视频信息、播放列表等）
     * @param isPortrait 当前屏幕方向是否为竖屏，用于自动旋转屏幕逻辑判断
     */
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

    /**
     * 切换播放上一个或下一个视频
     *
     * @param isNext true 表示播放下一个视频，false 表示播放上一个视频
     * @param isPortrait 当前屏幕方向是否为竖屏
     * @param currentPositionMs 当前视频已播放进度（毫秒），用于保存历史记录
     */
    fun playNextVideo(isNext: Boolean, isPortrait: Boolean, currentPositionMs: Long) {
        val currentIndex = _uiState.value.fileBeanIndex
        val nextIndex = if (isNext) currentIndex + 1 else currentIndex - 1
        playVideoAtIndex(nextIndex, isPortrait, currentPositionMs)
    }

    /**
     * 切换播放列表中指定索引位置的视频
     *
     * @param targetIndex 目标视频在列表中的索引
     * @param isPortrait 当前屏幕方向是否为竖屏
     * @param currentPositionMs 当前视频已播放进度（毫秒），用于保存历史记录
     */
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
        val keyword = videoName.keyWord(launchVideoParams.videoAttribute.positionAfterAt)

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

    /**
     * 当视频地址错误或失效时，重新从服务器获取新链接并重新播放
     */
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
            } catch (_: Exception) {
                isReloadingVideo = false
            }
        }
    }

    /**
     * 更新并提交当前视频的播放进度和历史记录
     *
     * @param currentPositionMs 当前播放进度（毫秒）
     */
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

    /**
     * 退出当前视频播放页，更新播放历史记录，并触发带有播放历史结果的退出事件
     *
     * @param currentPositionMs 当前播放进度（毫秒）
     * @param nav 跳转的目标路由/页面（可选）
     * @param toast 退出时提示的信息（可选）
     * @param resultCode Activity 结果代码，默认为 [Activity.RESULT_OK]
     */
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

    /**
     * 处理网络拦截器捕获到的播放错误（如地址无效、账号未验证、OSS 错误等）
     *
     * @param currentPositionMs 当前播放进度（毫秒）
     * @param url 请求错误的 URL
     * @param contentType 响应体的 MediaType
     * @param errorBody 错误响应体文本
     * @return true 表示错误已成功处理（如重新加载或退出页面），false 表示未匹配到特定错误规则
     */
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
     *
     * @param videoDurationMs 视频总时长（毫秒）
     * @param searchedSubtitle 是否标识为已搜索过字幕，默认为 true
     */
    fun loadSubtitles(videoDurationMs: Long, searchedSubtitle: Boolean = true) {
        val currentInfo = _uiState.value.videoInfo ?: return
        subtitleDelegate.loadSubtitles(
            scope = viewModelScope,
            mediaName = currentInfo.fileName,
            localSubtitles = launchVideoParams.localSubtitleItem,
            mediaDurationMs = videoDurationMs,
            searchedSubtitle = searchedSubtitle
        )
    }

    /**
     * 根据自定义输入的关键字进行字幕搜索
     *
     * @param keyword 搜索关键字
     * @param videoDurationMs 视频总时长（毫秒）
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
     * 下载选中的字幕文件并在准备好本地 File 后进行回调
     *
     * @param cacheDirFile 字幕缓存目录文件
     * @param item 选中的字幕项
     * @param onReady 下载并处理成功后的回调函数，参数为准备好的本地字幕 File
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
     * 设置字幕的精确时间偏移量 (ms)
     *
     * @param offsetMs 时间偏移量（毫秒）
     */
    fun setSubtitleOffset(offsetMs: Long) {
        subtitleDelegate.setSubtitleOffset(offsetMs)
    }

    /**
     * 微调字幕的时间偏移量 (ms)
     *
     * @param deltaMs 偏移增量（毫秒）
     */
    fun addSubtitleOffset(deltaMs: Long) {
        subtitleDelegate.addSubtitleOffset(deltaMs)
    }

    /**
     * 更新字幕样式（字号、颜色、字体、加粗、底色等）
     *
     * @param styleBean 字幕样式配置对象
     */
    fun updateSubtitleStyle(styleBean: SubtitleStyleBean) {
        subtitleDelegate.updateSubtitleStyle(styleBean)
    }

    /**
     * 清除/移除当前加载的字幕
     */
    fun removeSubtitle() {
        subtitleDelegate.removeSubtitle()
    }

    /**
     * 保存并上传当前字幕（连同本地时间偏移修正）至当前视频所在的 115 目录
     *
     * @param cacheDirFile 字幕缓存目录文件
     */
    fun saveAndUploadSubtitle(cacheDirFile: File) {
        val parentCid = launchVideoParams.categoryId
        val videoFileName =
            _uiState.value.videoInfo?.fileName ?: launchVideoParams.videoInfo.fileName
        subtitleDelegate.saveAndUploadCurrentSubtitle(
            scope = viewModelScope,
            cacheDirFile = cacheDirFile,
            videoFileName = videoFileName,
            targetCid = parentCid,
            onSuccess = {
                viewModelScope.launch {
                    DialogEventBus.getInstance().emit(DialogEvent.RefreshFileList(parentCid))
                }
            }
        )
    }
}
