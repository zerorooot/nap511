package github.zerorooot.nap511.viewmodel

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.player.AudioGSYManager
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.service.AudioService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.SrtParser
import github.zerorooot.nap511.util.SubtitleEntry
import github.zerorooot.nap511.util.bus.AudioEvent
import github.zerorooot.nap511.util.bus.AudioEventBus
import github.zerorooot.nap511.util.network.UserSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val SEEK_STEP_MS = 15000L
    private val context = getApplication<Application>()

    val fileRepository: FileRepository by lazy {
        FileRepository.getInstance()
    }

    val subtitleRepository: SubtitleRepository by lazy {
        SubtitleRepository.getInstance()
    }

    var currentMusic by mutableStateOf<FileBean?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var currentPositionText by mutableStateOf("00:00")
        private set

    var playbackSpeed by mutableFloatStateOf(1.0f)
        private set

    var volume by mutableFloatStateOf(1.0f)
        private set

    private var progressJob: Job? = null

    // 记录用户是否正在拖动进度条
    var isUserSeeking by mutableStateOf(false)
        private set

    // 用户拖拽过程中的临时进度
    var userSeekProgress by mutableFloatStateOf(0f)
        private set

    // --- 字幕状态扩展 ---
    var subtitles by mutableStateOf<List<SubtitleItem>>(emptyList())
        private set

    var selectedSubtitle by mutableStateOf<SubtitleItem?>(null)
        private set

    var subtitleEntries by mutableStateOf<List<SubtitleEntry>>(emptyList())
        private set

    var currentSubtitleText by mutableStateOf("")
        private set

    var currentSubtitleIndex by mutableIntStateOf(-1)
        private set

    var subtitleOffsetMs by mutableLongStateOf(0L)
        private set

    var isSubtitleLoading by mutableStateOf(false)
        private set

    var isSubtitleSearchLoading by mutableStateOf(false)
        private set

    var currentLocalSubtitles: List<SubtitleItem> = emptyList()

    private val videoManger: AudioGSYManager = AudioGSYManager.instance()

    private val listener = object : GSYMediaPlayerListener {
        override fun onPrepared() {
            viewModelScope.launch {
                isLoading = false
                isPlaying = true
                videoManger.start() // 真正的启动播放
                startProgressTracker()
                startAudioService(currentMusic?.name ?: "")
            }
        }

        override fun onAutoCompletion() {
            viewModelScope.launch {
                isPlaying = false
                progress = 1f
                stopProgressTracker()
            }
        }

        override fun onError(what: Int, extra: Int) {
            viewModelScope.launch {
                stopAudioAndService()
                App.instance.toast("播放失败 ($what)")
            }
        }

        override fun onCompletion() {}
        override fun onBufferingUpdate(percent: Int) {}
        override fun onSeekComplete() {}
        override fun onInfo(what: Int, extra: Int) {}
        override fun onVideoSizeChanged() {}
        override fun onBackFullscreen() {}
        override fun onVideoPause() {}
        override fun onVideoResume() {}
        override fun onVideoResume(seek: Boolean) {}
    }

    // 1. 开始拖动
    fun onSeekStart() {
        isUserSeeking = true
        pause()
    }

    // 2. 拖动中改变数值
    fun onSeekChange(newProgress: Float) {
        userSeekProgress = newProgress
        val duration = videoManger.duration
        if (duration > 0) {
            val targetMs = (newProgress * duration).toLong()
            currentPositionText = "${formatTime(targetMs)}/${formatTime(duration)}"
            updateSubtitleForPosition(targetMs)
        }
    }

    // 3. 松开手指，执行 Seek 操作
    fun onSeekEnd() {
        val duration = videoManger.duration
        if (duration > 0) {
            val targetMs = (userSeekProgress * duration).toLong()
            videoManger.seekTo(targetMs)
            progress = userSeekProgress
            currentPositionText = "${formatTime(targetMs)}/${formatTime(duration)}"
            updateSubtitleForPosition(targetMs)
        }
        isUserSeeking = false
        togglePlayPause()
    }

    init {
        videoManger.initContext(context)
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
        videoManger.setListener(listener)
        // 监听来自 Service 的状态同步事件
        viewModelScope.launch {
            AudioEventBus.events.collect { event ->
                when (event) {
                    is AudioEvent.SyncState -> syncFromManager()
                    is AudioEvent.Stop -> {
                        stop()
                    }
                }
            }
        }
    }

    /**
     * 从单例播放器拉取最新状态，更新 ViewModel Compose State
     */
    private fun syncFromManager() {
        isPlaying = videoManger.isPlaying
        if (isPlaying) {
            startProgressTracker()
        } else {
            stopProgressTracker()
        }
    }

    fun playAudio(fileBean: FileBean, localSubtitles: List<SubtitleItem> = emptyList()) {
        // 防止在同一个文件加载中重复点击
        if (isLoading || currentMusic?.fileId == fileBean.fileId) return

        currentMusic = fileBean
        isLoading = true
        isPlaying = false
        progress = 0f
        currentPositionText = "00:00"
        removeSubtitle()
        subtitles = emptyList()
        currentLocalSubtitles = localSubtitles
        subtitleOffsetMs = 0L

        viewModelScope.launch {
            try {
                val playUrl = fileRepository.music(fileBean.pickCode)
                if (playUrl.isNotEmpty()) {
                    videoManger.prepare(
                        playUrl,        // url
                        mapOf("Cookie" to UserSessionManager.cookie),           // headers (Map<String, String>?)
                        false,          // loop (是否循环播放)
                        playbackSpeed,           // speed (播放速度)
                        false,           // cache (是否开启缓存)
                        null,           // cachePath (缓存路径，传 null 为默认)
                        fileBean.name   // title
                    )
                    // 自动加载/搜集字幕候选
                    loadSubtitles(localSubtitles = localSubtitles)
                } else {
                    App.instance.toast("无法获取音频播放地址")
                    currentMusic = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                App.instance.toast("加载失败: ${e.message}")
                currentMusic = null
            } finally {
                isLoading = false
            }
        }
    }

    fun pause() {
        videoManger.pause()
        isPlaying = false
        stopProgressTracker()
    }

    fun togglePlayPause() {
        if (isLoading) return
        if (isPlaying) {
            pause()
        } else {
            videoManger.start()
            isPlaying = true
            startProgressTracker()
        }
        startAudioService(currentMusic?.name ?: "")
    }

    private fun startAudioService(title: String) {
        val intent = Intent(App.instance, AudioService::class.java).apply {
            putExtra("title", title)
        }
        App.instance.startForegroundService(intent)
    }

    private fun stopAudioService() {
        val intent = Intent(App.instance, AudioService::class.java).apply {
            action = AudioService.ACTION_STOP
        }
        App.instance.startService(intent)
    }

    fun stopAudioAndService() {
        stop()
        stopAudioService()
        playbackSpeed = 1.0f
        volume = 1.0f
    }

    private fun stop() {
        stopProgressTracker()
        currentMusic = null
        isPlaying = false
        isLoading = false
        progress = 0f
        currentPositionText = "00:00"
        removeSubtitle()
        subtitles = emptyList()
        currentLocalSubtitles = emptyList()
        subtitleOffsetMs = 0L
        videoManger.releaseMediaPlayer()
    }

    fun updateAudioTime() {
        val duration = videoManger.duration
        val current = videoManger.currentPosition
        if (duration > 0 && !isUserSeeking) {
            progress = current.toFloat() / duration.toFloat()
            currentPositionText = "${formatTime(current)}/${formatTime(duration)}"
            updateSubtitleForPosition(current)
        }
    }

    fun onRewind() {
        videoManger.seekRelative(-SEEK_STEP_MS)
        updateAudioTime()
    }

    // 响应快进 15 秒
    fun onFastForward() {
        videoManger.seekRelative(SEEK_STEP_MS)
        updateAudioTime()
    }

    fun changeSpeed(speed: Float) {
        playbackSpeed = speed
        videoManger.setSpeed(speed, true)
    }

    fun changeVolume(v: Float) {
        volume = v
        videoManger.setVolume(v)
    }

    // --- 字幕业务函数 ---

    /**
     * 搜集/检索字幕（结合迅雷 API 与 115 同目录字幕）
     */
    fun loadSubtitles(
        searchKeyword: String = "",
        localSubtitles: List<SubtitleItem> = currentLocalSubtitles
    ) {
        currentLocalSubtitles = localSubtitles
        val musicName = currentMusic?.name ?: ""
        val keyword = searchKeyword.ifBlank {
            if (musicName.contains(".")) musicName.substringBeforeLast(".") else musicName
        }
        if (keyword.isBlank()) return

        isSubtitleSearchLoading = true
        viewModelScope.launch {
            runCatching {
                val durationMs = videoManger.duration
                val result = subtitleRepository.getSubtitles(
                    searchKeyword = keyword,
                    oneOneFiveSubtitles = localSubtitles,
                    videoDurationMs = if (durationMs > 0) durationMs else 0L
                )
                subtitles = result
            }.onFailure { e ->
                XLog.e("AudioViewModel: 检索字幕失败", e)
            }
            isSubtitleSearchLoading = false
        }
    }

    /**
     * 选择并下载准备字幕
     */
    fun selectSubtitle(cacheDirFile: File, item: SubtitleItem) {
        isSubtitleLoading = true
        viewModelScope.launch {
            val srtFile = subtitleRepository.downloadAndPrepareSubtitle(cacheDirFile, item)
            if (srtFile != null) {
                selectedSubtitle = item
                subtitleEntries = SrtParser.parse(srtFile)
                updateSubtitleForPosition()
            } else {
                App.instance.toast("加载字幕失败: ${item.simpleName}")
            }
            isSubtitleLoading = false
        }
    }

    /**
     * 移除当前选中的字幕
     */
    fun removeSubtitle() {
        selectedSubtitle = null
        subtitleEntries = emptyList()
        currentSubtitleText = ""
        currentSubtitleIndex = -1
    }

    /**
     * 设置时间偏移量 (ms)
     */
    fun setSubtitleOffset(offsetMs: Long) {
        subtitleOffsetMs = offsetMs
        updateSubtitleForPosition()
    }

    /**
     * 微调时间偏移量 (ms)
     */
    fun addSubtitleOffset(deltaMs: Long) {
        subtitleOffsetMs += deltaMs
        updateSubtitleForPosition()
    }

    /**
     * 上传字幕到 115 同目录
     */
    fun uploadSubtitleTo115(cacheDirFile: File, item: SubtitleItem, targetCid: String) {
        if (targetCid.isBlank() || targetCid == "0") {
            App.instance.toast("无法获取当前音频所在目录 ID")
            return
        }
        viewModelScope.launch {
            App.instance.toast("正在将字幕上传至 115 网盘...")
            val result = subtitleRepository.uploadSubtitleTo115(cacheDirFile, item, targetCid)
            if (result.state) {
                App.instance.toast("字幕上传成功！已保存到 115 当前目录")
                loadSubtitles(localSubtitles = currentLocalSubtitles)
            } else {
                App.instance.toast("字幕上传失败: ${result.message}")
            }
        }
    }

    /**
     * 点击某一字幕行跳转播放
     */
    fun seekToSubtitleEntry(entry: SubtitleEntry) {
        val targetMs = (entry.startMs - subtitleOffsetMs).coerceAtLeast(0L)
        videoManger.seekTo(targetMs)
        updateAudioTime()
    }

    /**
     * 根据当前播放时刻与偏移量，匹配当前显示的字幕文本与行号
     */
    fun updateSubtitleForPosition(currentMs: Long = videoManger.currentPosition) {
        if (subtitleEntries.isEmpty()) {
            if (currentSubtitleText.isNotEmpty()) currentSubtitleText = ""
            if (currentSubtitleIndex != -1) currentSubtitleIndex = -1
            return
        }

        val adjustedMs = currentMs + subtitleOffsetMs

        val index = subtitleEntries.indexOfLast { entry ->
            adjustedMs >= entry.startMs
        }

        if (index != -1) {
            val entry = subtitleEntries[index]
            currentSubtitleText = entry.text
            currentSubtitleIndex = index
        } else {
            currentSubtitleIndex = 0
            currentSubtitleText = subtitleEntries.firstOrNull()?.text ?: ""
        }
    }

    // 修改轮询进度逻辑：高频轮询同步字幕与进度
    private fun startProgressTracker() {
        stopProgressTracker()
        var lastNotifyTime = 0L
        progressJob = viewModelScope.launch {
            while (isPlaying) {
                updateAudioTime()
                val now = System.currentTimeMillis()
                if (now - lastNotifyTime >= 1000L) {
                    notifyServiceUpdateState()
                    lastNotifyTime = now
                }
                delay(250.milliseconds)
            }
        }
    }

    private fun notifyServiceUpdateState() {
        val intent = Intent(context, AudioService::class.java).apply {
            action = AudioService.ACTION_UPDATE_STATE
            putExtra("title", currentMusic?.name ?: "")
        }
        context.startService(intent)
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        stopAudioAndService()
    }
}
