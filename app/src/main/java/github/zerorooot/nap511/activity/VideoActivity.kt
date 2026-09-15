package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import com.shuyu.gsyvideoplayer.subtitle.GSYSubtitleMime
import com.shuyu.gsyvideoplayer.subtitle.GSYSubtitleSource
import github.zerorooot.nap511.R
import github.zerorooot.nap511.adapter.SubtitleAdapter
import github.zerorooot.nap511.adapter.VideoOptionAdapter
import github.zerorooot.nap511.bean.FontFamilyType
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.bean.SubtitleStyleBean
import github.zerorooot.nap511.bean.VideoUiState
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.util.App
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
    private lateinit var episodeAdapter: VideoOptionAdapter
    private lateinit var subtitleAdapter: SubtitleAdapter

    // 1. 字号按钮
    private val sizeMap = mapOf(
        R.id.btn_style_size_small to 22f,
        R.id.btn_style_size_medium to 24f,
        R.id.btn_style_size_large to 26f
    )

    // 2. 颜色按钮 (白, 黄, 绿, 青)
    private val colorMap = mapOf(
        R.id.btn_style_color_white to Color.WHITE,
        R.id.btn_style_color_yellow to Color.YELLOW,
        R.id.btn_style_color_green to Color.GREEN,
        R.id.btn_style_color_cyan to Color.CYAN
    )

    // 3. 字体按钮 (默认, 无衬线, 衬线, 等宽)
    private val fontMap = mapOf(
        R.id.btn_style_font_default to FontFamilyType.DEFAULT,
        R.id.btn_style_font_sans to FontFamilyType.SANS_SERIF,
        R.id.btn_style_font_serif to FontFamilyType.SERIF,
        R.id.btn_style_font_mono to FontFamilyType.MONOSPACE
    )

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

        // 通用合并抽屉面板设置：初始化单一 RecyclerView 及其各项适配器
        val rvDrawer = videoPlayer.findViewById<RecyclerView>(R.id.rv_drawer)
        if (rvDrawer != null) {
            rvDrawer.layoutManager = LinearLayoutManager(this)

            // 1. 选集适配器
            val episodeTitles = viewModel.videoList.mapIndexed { index, item ->
                val displayIndex = index + 1
                if (item.name.isNotEmpty()) {
                    "P$displayIndex  ${item.name}"
                } else {
                    "第 $displayIndex 集"
                }
            }
            episodeAdapter = VideoOptionAdapter(
                options = episodeTitles,
                selectedIndex = viewModel.uiState.value.fileBeanIndex
            ) { index, _ ->
                val isPortrait =
                    resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                viewModel.playVideoAtIndex(
                    index,
                    isPortrait,
                    videoPlayer.currentPositionWhenPlaying
                )
                videoPlayer.hideAllDrawers()
            }

            // 2. 倍速适配器
            val speedValues =
                floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f)
            val speedTitles = listOf(
                "0.5X",
                "0.75X",
                "1.0X",
                "1.25X",
                "1.5X",
                "1.75X",
                "2.0X",
                "2.25X",
                "2.5X",
                "3.0X"
            )
            val defaultSpeedIndex = 2
            val speedAdapter = VideoOptionAdapter(speedTitles, defaultSpeedIndex) { index, _ ->
                val speedVal = speedValues[index]
                videoPlayer.currentPlayer.setSpeed(speedVal, true)
                videoPlayer.setSpeedText(if (speedVal == 1.0f) "倍速" else "${speedVal}X")
                videoPlayer.hideAllDrawers()
            }

            // 3. 画面比例适配器
            val scaleTypes = intArrayOf(0, 1, 2, 3, 4)
            val scaleTitles = listOf("默认", "16:9", "4:3", "全屏", "拉伸")
            val defaultScaleIndex = 0
            val scaleAdapter = VideoOptionAdapter(scaleTitles, defaultScaleIndex) { index, _ ->
                videoPlayer.setAspectScale(scaleTypes[index])
                videoPlayer.hideAllDrawers()
            }

            // 4. 初始化字幕面板控件与适配器
            initSubtitleControls()

            val layoutSubtitlePanel = videoPlayer.findViewById<View>(R.id.layout_subtitle_panel)

            // 5. 监听抽屉打开事件，根据抽屉类型 (选集/倍速/画面比例/字幕) 动态切换 rvDrawer 的 Adapter 及操作面板
            videoPlayer.setOnDrawerOpenListener { type ->
                val rvParams = rvDrawer.layoutParams as? FrameLayout.LayoutParams
                if (type == MyGSYVideoPlayer.DrawerType.SUBTITLE) {
                    rvParams?.gravity = Gravity.TOP
                } else {
                    rvParams?.gravity = Gravity.CENTER
                }
                rvDrawer.layoutParams = rvParams
                layoutSubtitlePanel?.visibility = View.GONE

                when (type) {
                    MyGSYVideoPlayer.DrawerType.EPISODE -> {
                        rvDrawer.adapter = episodeAdapter
                        val currentIndex = viewModel.uiState.value.fileBeanIndex
                        if (currentIndex >= 0) {
                            rvDrawer.scrollToPosition(currentIndex)
                        }
                    }

                    MyGSYVideoPlayer.DrawerType.SPEED -> {
                        rvDrawer.adapter = speedAdapter
                    }

                    MyGSYVideoPlayer.DrawerType.SCALE -> {
                        rvDrawer.adapter = scaleAdapter
                    }

                    MyGSYVideoPlayer.DrawerType.SUBTITLE -> {
                        layoutSubtitlePanel?.visibility = View.VISIBLE
                        rvDrawer.adapter = subtitleAdapter
                        if (viewModel.uiState.value.subtitles.isEmpty()) {
                            viewModel.loadSubtitles(videoPlayer.duration)
                        }
                        val etSearch = videoPlayer.findViewById<EditText>(R.id.et_subtitle_search)
                        if (etSearch != null && etSearch.text.isNullOrEmpty()) {
                            etSearch.setText(viewModel.uiState.value.defaultSearchKeyword)
                        }
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (videoPlayer.isAnyDrawerShowing) {
                videoPlayer.hideAllDrawers()
            } else {
                performBack()
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (::episodeAdapter.isInitialized) {
                            episodeAdapter.updateSelectedIndex(state.fileBeanIndex)
                            if (state.fileBeanIndex >= 0 && videoPlayer.currentDrawerType == MyGSYVideoPlayer.DrawerType.EPISODE) {
                                videoPlayer.findViewById<RecyclerView>(R.id.rv_drawer)
                                    ?.scrollToPosition(state.fileBeanIndex)
                            }
                        }
                        videoPlayer.findViewById<View>(R.id.prev_episode)?.apply {
                            isEnabled = state.hasPrev
                            alpha = if (state.hasPrev) 1.0f else 0.3f
                        }
                        videoPlayer.findViewById<View>(R.id.next_episode)?.apply {
                            isEnabled = state.hasNext
                            alpha = if (state.hasNext) 1.0f else 0.3f
                        }
                        if (::subtitleAdapter.isInitialized) {
                            subtitleAdapter.updateData(
                                state.subtitles,
                                state.selectedSubtitle?.id ?: ""
                            )
                            subtitleAdapter.updateSelectedId(state.selectedSubtitle?.id ?: "")
                        }
                        updateSubtitleEmptyState(state)

                        val tvOffsetLabel =
                            videoPlayer.findViewById<TextView>(R.id.tv_subtitle_offset_label)
                        tvOffsetLabel?.text = "偏移: ${state.subtitleOffsetMs}ms"
                        videoPlayer.setSubtitleOffsetMs(state.subtitleOffsetMs)

                        videoPlayer.applySubtitleStyle(state.subtitleStyle)
                        updateSubtitleStyleButtonVisuals(state.subtitleStyle)

                        val etSearch = videoPlayer.findViewById<EditText>(R.id.et_subtitle_search)
                        if (etSearch != null && etSearch.text.isNullOrEmpty()) {
                            etSearch.setText(state.defaultSearchKeyword)
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

    private fun updateSubtitleEmptyState(state: VideoUiState = viewModel.uiState.value) {
        val tvEmpty = videoPlayer.findViewById<TextView>(R.id.tv_subtitle_empty) ?: return
        val rvDrawer = videoPlayer.findViewById<RecyclerView>(R.id.rv_drawer) ?: return

        if (videoPlayer.currentDrawerType != MyGSYVideoPlayer.DrawerType.SUBTITLE) {
            tvEmpty.visibility = View.GONE
            rvDrawer.visibility = View.VISIBLE
            return
        }

        val isLoading = state.isSubtitleLoading
        val list = state.subtitles

        if (isLoading) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "正在搜索获取字幕..."
            rvDrawer.visibility = View.GONE
        } else if (list.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "未找到相关字幕，可尝试使用搜索框重新搜索"
            rvDrawer.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvDrawer.visibility = View.VISIBLE
        }
    }

    private fun updateSubtitleStyleButtonVisuals(styleBean: SubtitleStyleBean) {
        val activeColor = "#42A5F5".toColorInt()
        val defaultColor = Color.WHITE

        sizeMap.forEach { (id, size) ->
            val btn = videoPlayer.findViewById<TextView>(id) ?: return@forEach
            val isSelected = styleBean.textSizeSp == size
            btn.setTextColor(if (isSelected) activeColor else defaultColor)
            btn.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }

        colorMap.forEach { (id, color) ->
            val btn = videoPlayer.findViewById<TextView>(id) ?: return@forEach
            val isSelected = styleBean.textColor == color
            btn.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            btn.setBackgroundColor(if (isSelected) "#3342A5F5".toColorInt() else Color.TRANSPARENT)
        }

        fontMap.forEach { (id, font) ->
            val btn = videoPlayer.findViewById<TextView>(id) ?: return@forEach
            val isSelected = styleBean.fontFamily == font
            btn.setTextColor(if (isSelected) activeColor else defaultColor)
            btn.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }

        // 4. 加粗按钮
        val btnBold = videoPlayer.findViewById<TextView>(R.id.btn_style_bold)
        btnBold?.setTextColor(if (styleBean.isBold) activeColor else defaultColor)
        btnBold?.setTypeface(null, if (styleBean.isBold) Typeface.BOLD else Typeface.NORMAL)

        // 5. 半透底按钮
        val btnBg = videoPlayer.findViewById<TextView>(R.id.btn_style_bg_translucent)
        val hasBg = styleBean.backgroundColor != Color.TRANSPARENT
        btnBg?.setTextColor(if (hasBg) activeColor else defaultColor)
        btnBg?.setTypeface(null, if (hasBg) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun initSubtitleControls() {
        val uiState = viewModel.uiState.value
        subtitleAdapter = SubtitleAdapter(
            items = uiState.subtitles,
            selectedId = uiState.selectedSubtitle?.id ?: "",
            onUploadClick = { subtitleItem ->
                viewModel.uploadSubtitle(this.cacheDir, subtitleItem)
            }
        ) { subtitleItem ->
            viewModel.selectSubtitle(this.cacheDir, subtitleItem) { srtFile ->
                val source = GSYSubtitleSource.Builder(android.net.Uri.fromFile(srtFile).toString())
                    .setLabel(subtitleItem.simpleName)
                    .setId(subtitleItem.id)
                    .setMimeType(GSYSubtitleMime.APPLICATION_SUBRIP)
                    .setLanguage("zh")
                    .setCharsetName("UTF-8")
                    .setOffsetMs(viewModel.uiState.value.subtitleOffsetMs)
                    .setDefault(true)
                    .build()
                videoPlayer.setSubtitleSource(source)
                videoPlayer.setSubtitleEnabled(true)
                App.instance.toast("字幕切换成功")
            }
        }

        val etSearch = videoPlayer.findViewById<EditText>(R.id.et_subtitle_search)
        val btnSearch = videoPlayer.findViewById<View>(R.id.btn_subtitle_search)
        val executeSearch = {
            val keyword = etSearch?.text?.toString()?.trim() ?: ""
            if (keyword.isNotEmpty()) {
                viewModel.searchSubtitlesByName(keyword, videoPlayer.duration)
            }
        }
        btnSearch?.setOnClickListener { executeSearch() }
        etSearch?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executeSearch()
                true
            } else false
        }

        // Offset 按钮与重置精简绑定
        val offsetDeltas = mapOf(
            R.id.btn_offset_minus_1s to -1000L,
            R.id.btn_offset_minus_100ms to -100L,
            R.id.btn_offset_plus_100ms to 100L,
            R.id.btn_offset_plus_1s to 1000L
        )
        offsetDeltas.forEach { (id, delta) ->
            videoPlayer.findViewById<View>(id)
                ?.setOnClickListener { viewModel.addSubtitleOffset(delta) }
        }
        videoPlayer.findViewById<View>(R.id.btn_offset_reset)
            ?.setOnClickListener { viewModel.setSubtitleOffset(0L) }

        sizeMap.forEach { (id, size) ->
            videoPlayer.findViewById<View>(id)?.setOnClickListener {
                viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(textSizeSp = size))
            }
        }

        colorMap.forEach { (id, color) ->
            videoPlayer.findViewById<View>(id)?.setOnClickListener {
                viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(textColor = color))
            }
        }

        fontMap.forEach { (id, font) ->
            videoPlayer.findViewById<View>(id)?.setOnClickListener {
                viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(fontFamily = font))
            }
        }

        // 加粗 & 半透底
        videoPlayer.findViewById<View>(R.id.btn_style_bold)?.setOnClickListener {
            val curBold = viewModel.uiState.value.subtitleStyle.isBold
            viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(isBold = !curBold))
        }
        videoPlayer.findViewById<View>(R.id.btn_style_bg_translucent)?.setOnClickListener {
            val curBg = viewModel.uiState.value.subtitleStyle.backgroundColor
            val nextBg =
                if (curBg == Color.TRANSPARENT) "#80000000".toColorInt() else Color.TRANSPARENT
            viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(backgroundColor = nextBg))
        }

        // 移除当前字幕
        videoPlayer.findViewById<View>(R.id.btn_remove_subtitle)?.setOnClickListener {
            videoPlayer.setSubtitleEnabled(false)
            viewModel.removeSubtitle()
            subtitleAdapter.updateSelectedId("")
//            videoPlayer.hideAllDrawers()
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
            val title = viewModel.uiState.value.videoInfo?.fileName ?: ""
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
