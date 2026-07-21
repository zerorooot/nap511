package github.zerorooot.nap511.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import kotlin.time.Duration.Companion.milliseconds

class AudioViewModel(val cookie: String, val context: Context) : ViewModel() {

    // 假设你有 Repository 或 Api 实例，也可以通过 Hilt/Koin 注入
    val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(cookie)
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

    private var progressJob: Job? = null

    // 记录用户是否正在拖动进度条
    var isUserSeeking by mutableStateOf(false)
        private set

    // 用户拖拽过程中的临时进度
    var userSeekProgress by mutableFloatStateOf(0f)
        private set

    private var videoManger: GSYVideoManager = GSYVideoManager.instance()

    private val listener = object : GSYMediaPlayerListener {
        override fun onPrepared() {
            viewModelScope.launch {
                isLoading = false
                isPlaying = true
                videoManger.start() // 真正的启动播放
                startProgressTracker()
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
                isLoading = false
                isPlaying = false
                stopProgressTracker()
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
    }

    // 2. 拖动中改变数值
    fun onSeekChange(newProgress: Float) {
        userSeekProgress = newProgress
    }

    // 3. 松开手指，执行 Seek 操作
    fun onSeekEnd() {
        val duration = videoManger.duration
        if (duration > 0) {
            val targetMs = (userSeekProgress * duration).toLong()
            videoManger.seekTo(targetMs)
            progress = userSeekProgress
        }
        isUserSeeking = false
    }

    init {
        videoManger.initContext(context)
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
        videoManger.setListener(listener)
    }

    fun playAudio(fileBean: FileBean) {
        // 防止在同一个文件加载中重复点击
        if (isLoading && currentMusic?.fileId == fileBean.fileId) return

        currentMusic = fileBean
        isLoading = true
        isPlaying = false
        progress = 0f

        viewModelScope.launch {
            try {
                val playUrl = fileRepository.music(fileBean.pickCode)
                if (playUrl.isNotEmpty()) {
                    // 修正点：使用 GSYVideoManager 的 prepare 方法
                    videoManger.prepare(
                        playUrl,        // url
                        mapOf("Cookie" to cookie),           // headers (Map<String, String>?)
                        false,          // loop (是否循环播放)
                        1.0f,           // speed (播放速度)
                        false,           // cache (是否开启缓存)
                        null,           // cachePath (缓存路径，传 null 为默认)
                        fileBean.name   // title
                    )
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

    fun togglePlayPause() {
        if (isLoading) return

        if (isPlaying) {
            videoManger.pause()
            isPlaying = false
            stopProgressTracker()
        } else {
            videoManger.start()
            isPlaying = true
            startProgressTracker()
        }
    }

    fun stop() {
        stopProgressTracker()
        currentMusic = null
        isPlaying = false
        isLoading = false
        progress = 0f
        videoManger.releaseMediaPlayer()
    }

    // 修改轮询进度逻辑：用户拖拽时跳过自动赋值
    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = viewModelScope.launch {
            while (isPlaying) {
                val duration = videoManger.duration
                val current = videoManger.currentPosition
                if (duration > 0 && !isUserSeeking) { // <--- 增加了 !isUserSeeking 判断
                    progress = current.toFloat() / duration.toFloat()
                    currentPositionText = "${formatTime(current)}/${formatTime(duration)}"
                }
                delay(500.milliseconds)
            }
        }
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
        stop()
        super.onCleared()
    }
}