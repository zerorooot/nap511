package github.zerorooot.nap511.viewmodel

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleUiState
import github.zerorooot.nap511.player.AudioGSYManager
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.service.AudioService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.subtitle.SubtitleEntry
import github.zerorooot.nap511.util.bus.AudioEvent
import github.zerorooot.nap511.util.bus.AudioEventBus
import github.zerorooot.nap511.util.bus.DialogEvent
import github.zerorooot.nap511.util.bus.DialogEventBus
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.util.subtitle.SubtitleDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

data class AudioPlaybackUiState(
    val currentMusic: FileBean? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val currentPositionText: String = "00:00",
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isUserSeeking: Boolean = false,
    val userSeekProgress: Float = 0f
) {
    val displayProgress: Float
        get() = if (isUserSeeking) userSeekProgress else progress
}

data class AudioUiState(
    val playback: AudioPlaybackUiState = AudioPlaybackUiState(),
    val subtitle: SubtitleUiState = SubtitleUiState()
)

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val SEEK_STEP_MS = 15000L
    private val context = getApplication<Application>()

    val fileRepository: FileRepository by lazy {
        FileRepository.getInstance()
    }

    val subtitleRepository: SubtitleRepository by lazy {
        SubtitleRepository.getInstance()
    }

    private val subtitleDelegate = SubtitleDelegate(subtitleRepository) { newSubtitleState ->
        uiState = uiState.copy(subtitle = newSubtitleState)
    }

    var uiState by mutableStateOf(AudioUiState())
        private set

    // 常用属性重定向（兼容与内部便捷访问）
    val currentMusic: FileBean? get() = uiState.playback.currentMusic
    val isPlaying: Boolean get() = uiState.playback.isPlaying
    val isLoading: Boolean get() = uiState.playback.isLoading
    val progress: Float get() = uiState.playback.progress
    val currentPositionText: String get() = uiState.playback.currentPositionText
    val playbackSpeed: Float get() = uiState.playback.playbackSpeed
    val isUserSeeking: Boolean get() = uiState.playback.isUserSeeking
    val userSeekProgress: Float get() = uiState.playback.userSeekProgress

    val subtitleOffsetMs: Long get() = uiState.subtitle.offsetMs
    val currentLocalSubtitles: List<SubtitleItem> get() = uiState.subtitle.currentLocalSubtitles

    private var progressJob: Job? = null
    private val videoManger: AudioGSYManager = AudioGSYManager.instance()

    val durationMs: Long
        get() = videoManger.duration.coerceAtLeast(0L)

    private fun updatePlayback(update: AudioPlaybackUiState.() -> AudioPlaybackUiState) {
        uiState = uiState.copy(playback = uiState.playback.update())
    }

    private val listener = object : GSYMediaPlayerListener {
        override fun onPrepared() {
            viewModelScope.launch {
                updatePlayback { copy(isLoading = false, isPlaying = true) }
                videoManger.start() // 真正的启动播放
                startProgressTracker()
                startAudioService(currentMusic?.name ?: "")
            }
        }

        override fun onAutoCompletion() {
            viewModelScope.launch {
                updatePlayback { copy(isPlaying = false, progress = 1f) }
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
        updatePlayback { copy(isUserSeeking = true) }
        pause()
    }

    // 2. 拖动中改变数值
    fun onSeekChange(newProgress: Float) {
        val duration = videoManger.duration
        val posText = if (duration > 0) {
            val targetMs = (newProgress * duration).toLong()
            updateSubtitleForPosition(targetMs)
            "${formatTime(targetMs)}/${formatTime(duration)}"
        } else {
            currentPositionText
        }
        updatePlayback { copy(userSeekProgress = newProgress, currentPositionText = posText) }
    }

    // 3. 松开手指，执行 Seek 操作
    fun onSeekEnd() {
        val duration = videoManger.duration
        var targetProgress = progress
        var posText = currentPositionText
        if (duration > 0) {
            val targetMs = (userSeekProgress * duration).toLong()
            videoManger.seekTo(targetMs)
            targetProgress = userSeekProgress
            posText = "${formatTime(targetMs)}/${formatTime(duration)}"
            updateSubtitleForPosition(targetMs)
        }
        updatePlayback {
            copy(
                isUserSeeking = false,
                progress = targetProgress,
                currentPositionText = posText
            )
        }
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
        val playing = videoManger.isPlaying
        updatePlayback { copy(isPlaying = playing) }
        if (playing) {
            startProgressTracker()
        } else {
            stopProgressTracker()
        }
    }

    fun playAudio(fileBean: FileBean, localSubtitles: List<SubtitleItem> = emptyList()) {
        // 防止在同一个文件加载中重复点击
        if (isLoading || currentMusic?.fileId == fileBean.fileId) return

        removeSubtitle()
        updatePlayback {
            copy(
                currentMusic = fileBean,
                isLoading = true,
                isPlaying = false,
                progress = 0f,
                currentPositionText = "00:00"
            )
        }

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
                    updatePlayback { copy(currentMusic = null) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                App.instance.toast("加载失败: ${e.message}")
                updatePlayback { copy(currentMusic = null) }
            } finally {
                updatePlayback { copy(isLoading = false) }
            }
        }
    }

    fun pause() {
        videoManger.pause()
        updatePlayback { copy(isPlaying = false) }
        stopProgressTracker()
    }

    fun togglePlayPause() {
        if (isLoading) return
        if (isPlaying) {
            pause()
        } else {
            videoManger.start()
            updatePlayback { copy(isPlaying = true) }
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
        updatePlayback { copy(playbackSpeed = 1.0f, volume = 1.0f) }
    }

    private fun stop() {
        stopProgressTracker()
        removeSubtitle()
        updatePlayback {
            copy(
                currentMusic = null,
                isPlaying = false,
                isLoading = false,
                progress = 0f,
                currentPositionText = "00:00"
            )
        }
        videoManger.releaseMediaPlayer()
    }

    fun updateAudioTime() {
        val duration = videoManger.duration
        val current = videoManger.currentPosition
        if (duration > 0 && !isUserSeeking) {
            val newProgress = current.toFloat() / duration.toFloat()
            val newPosText = "${formatTime(current)}/${formatTime(duration)}"
            updatePlayback { copy(progress = newProgress, currentPositionText = newPosText) }
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
        updatePlayback { copy(playbackSpeed = speed) }
        videoManger.setSpeed(speed, true)
    }

    fun changeVolume(v: Float) {
        updatePlayback { copy(volume = v) }
        videoManger.setVolume(v)
    }

    // --- 字幕业务代理函数 ---

    /**
     * 搜集/检索字幕（结合迅雷 API 与 115 同目录字幕）
     */
    fun loadSubtitles(
        searchKeyword: String = "",
        localSubtitles: List<SubtitleItem> = currentLocalSubtitles
    ) {
        subtitleDelegate.loadSubtitles(
            scope = viewModelScope,
            mediaName = currentMusic?.name ?: "",
            searchKeyword = searchKeyword,
            localSubtitles = localSubtitles,
            mediaDurationMs = videoManger.duration
        )
    }

    /**
     * 选择并下载准备字幕
     */
    fun selectSubtitle(cacheDirFile: File, item: SubtitleItem) {
        subtitleDelegate.selectSubtitle(
            scope = viewModelScope,
            cacheDirFile = cacheDirFile,
            item = item,
            currentPositionMs = videoManger.currentPosition
        )
    }

    /**
     * 移除当前选中的字幕
     */
    fun removeSubtitle() {
        subtitleDelegate.removeSubtitle()
    }

    /**
     * 设置时间偏移量 (ms)
     */
    fun setSubtitleOffset(offsetMs: Long) {
        subtitleDelegate.setSubtitleOffset(offsetMs, videoManger.currentPosition)
    }

    /**
     * 微调时间偏移量 (ms)
     */
    fun addSubtitleOffset(deltaMs: Long) {
        subtitleDelegate.addSubtitleOffset(deltaMs, videoManger.currentPosition)
    }

    /**
     * 保存并上传当前字幕（连同本地时间偏移修正）至当前音乐所在的 115 目录
     */
    fun saveAndUploadCurrentSubtitle(cacheDirFile: File, targetCid: String) {
        val musicName = currentMusic?.name ?: ""
        subtitleDelegate.saveAndUploadCurrentSubtitle(
            scope = viewModelScope,
            cacheDirFile = cacheDirFile,
            videoFileName = musicName,
            targetCid = targetCid,
            onSuccess = {
                viewModelScope.launch {
                    DialogEventBus.getInstance().emit(DialogEvent.RefreshFileList(targetCid))
                }
            }
        )
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
        subtitleDelegate.updateSubtitleForPosition(currentMs)
    }

    // 修改轮询进度逻辑：高频轮询同步字幕与进度
    private fun startProgressTracker() {
        stopProgressTracker()
        var lastNotifyTime = 0L
        progressJob = viewModelScope.launch {
            while (isPlaying) {
                updateAudioTime()
                val now = System.currentTimeMillis()
                if (now - lastNotifyTime >= 300L) {
                    notifyServiceUpdateState()
                    lastNotifyTime = now
                }
                delay(150.milliseconds)
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
