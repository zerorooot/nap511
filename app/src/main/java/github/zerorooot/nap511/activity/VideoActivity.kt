package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.util.network.VideoErrorInterceptor
import github.zerorooot.nap511.util.network.VideoErrorMapper
import github.zerorooot.nap511.util.network.isHandledException
import github.zerorooot.nap511.viewmodel.VideoUiEvent
import github.zerorooot.nap511.viewmodel.VideoViewModel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import tv.danmaku.ijk.media.exo2.ExoMediaSourceInterceptListener
import tv.danmaku.ijk.media.exo2.ExoSourceManager
import java.io.File

class VideoActivity : AppCompatActivity() {
    private val viewModel: VideoViewModel by viewModels()
    private lateinit var videoPlayer: MyGSYVideoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        val paramsJson = intent.getStringExtra("bean")
        val launchVideoParams = Gson().fromJson(paramsJson, LaunchVideoParams::class.java)

        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        viewModel.initParams(launchVideoParams, isPortrait)

        val videoInfo = launchVideoParams.videoInfo
        val headerMap = hashMapOf(
            "cookie" to UserSessionManager.cookie,
            "User-Agent" to ConfigKeyUtil.USER_AGENT
        )
        val address = videoInfo.videoUrl.ifEmpty { videoInfo.downloadUrl }
        val title = videoInfo.fileName

        videoPlayer = findViewById(R.id.pre_video_player)
        videoPlayer.setHideLoadingView(viewModel.hideLoading)

        initGSYExoPlayerWithOkHttp(this.applicationContext)
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

        videoPlayer.apply {
            setUp(address, false, null, headerMap, title)
            //增加title
            titleTextView.visibility = View.VISIBLE
            titleTextView.isSelected = true
            seekRatio = 10f
            //设置返回键
            backButton.visibility = View.VISIBLE
            isShowFullAnimation = false

            fullscreenButton.setOnClickListener {
                rotateScreen()
            }
            //设置返回按键功能
            backButton.setOnClickListener {
                performBack()
            }
            // 上一集 / 下一集
            findViewById<View>(R.id.prev_episode)?.setOnClickListener {
                playNextVideo(false)
            }
            findViewById<View>(R.id.next_episode)?.setOnClickListener {
                playNextVideo(true)
            }
        }

        videoPlayer.setVideoAllCallBack(gSYErrorCallBack)
        videoPlayer.startPlayLogic()

        onBackPressedDispatcher.addCallback(this) {
            performBack()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.hasPrev.collect { hasPrev ->
                        videoPlayer.findViewById<View>(R.id.prev_episode)?.apply {
                            isEnabled = hasPrev
                            alpha = if (hasPrev) 1.0f else 0.3f
                        }
                    }
                }
                launch {
                    viewModel.hasNext.collect { hasNext ->
                        videoPlayer.findViewById<View>(R.id.next_episode)?.apply {
                            isEnabled = hasNext
                            alpha = if (hasNext) 1.0f else 0.3f
                        }
                    }
                }
                launch {
                    viewModel.uiEvent.collect { event ->
                        when (event) {
                            is VideoUiEvent.PlayNext -> {
                                videoPlayer.playNext(event.videoUrl, event.title)
                            }

                            is VideoUiEvent.Toast -> {
                                Toast.makeText(
                                    this@VideoActivity,
                                    event.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            is VideoUiEvent.RotateScreen -> {
                                rotateScreen()
                            }

                            is VideoUiEvent.FinishWithResult -> {
                                val returnIntent = Intent().apply {
                                    putExtra("videoHistory", event.videoHistoryJson)
                                    putExtra("nav", event.nav)
                                    putExtra("toast", event.toast)
                                }
                                setResult(event.resultCode, returnIntent)
                                videoPlayer.setVideoAllCallBack(null)
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    fun rotateScreen() {
        // 获取当前屏幕方向
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 当前是竖屏，强制转为横屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        } else {
            // 当前是横屏，强制转为竖屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }
    }

    private fun playNextVideo(isNext: Boolean) {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        viewModel.playNextVideo(isNext, isPortrait, videoPlayer.currentPositionWhenPlaying)
    }

    private fun performBack(nav: String = "", toast: String = "", resultCode: Int = RESULT_OK) {
        viewModel.back(videoPlayer.currentPositionWhenPlaying, nav, toast, resultCode)
    }

    private val gSYErrorCallBack = object : GSYSampleCallBack() {
        override fun onPlayError(url: String?, vararg objects: Any?) {
            val playerManager = videoPlayer.gsyVideoManager.player as? Exo2PlayerManager
            val exoPlayer = playerManager?.mediaPlayer as? ExoPlayer
            val exoError = exoPlayer?.playerError
            if (isHandledException(exoError)) {
                return
            }

            super.onPlayError(url, objects)
            val errorStatus =
                if (objects[2] != null && videoPlayer.gsyVideoManager.player is Exo2PlayerManager) {
                    val code = (objects[2] as Int)
                    VideoErrorMapper.getErrorMessage(code)
                } else {
                    "UNKNOWN_ERROR"
                }
            val title = viewModel.videoInfo.value?.fileName ?: ""
            XLog.e("$title 播放失败 $errorStatus")
            Toast.makeText(baseContext, errorStatus, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initGSYExoPlayerWithOkHttp(context: Context) {
        val customOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(VideoErrorInterceptor { url, contentType, errorBody ->
                viewModel.handleInterceptorError(
                    currentPositionMs = videoPlayer.currentPositionWhenPlaying,
                    url = url,
                    contentType = contentType,
                    errorBody = errorBody
                )
            })
            .build()

        ExoSourceManager.setExoMediaSourceInterceptListener(object :
            ExoMediaSourceInterceptListener {
            override fun getMediaSource(
                dataSource: String?,
                preview: Boolean,
                cacheEnable: Boolean,
                isLooping: Boolean,
                cacheDir: File?
            ): MediaSource? {
                return null
            }

            @OptIn(UnstableApi::class)
            override fun getHttpDataSourceFactory(
                userAgent: String?,
                listener: TransferListener?,
                connectTimeoutMillis: Int,
                readTimeoutMillis: Int,
                mapHeadData: Map<String?, String?>?,
                allowCrossProtocolRedirects: Boolean
            ): DataSource.Factory {
                val okHttpDataSourceFactory = OkHttpDataSource.Factory(customOkHttpClient)
                mapHeadData?.let {
                    okHttpDataSourceFactory.setDefaultRequestProperties(it as Map<String, String>)
                }
                return okHttpDataSourceFactory
            }

            @OptIn(UnstableApi::class)
            override fun cacheWriteDataSinkFactory(
                cachePath: String?,
                url: String?
            ): DataSink.Factory? {
                return null
            }
        })
    }

    override fun onPause() {
        videoPlayer.onVideoPause()
        super.onPause()
    }

    override fun onResume() {
        videoPlayer.onVideoResume()
        super.onResume()
    }

    override fun onDestroy() {
        GSYVideoManager.releaseAllVideos()
        super.onDestroy()
    }
}
