package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.helper.ExoPlayerInitializer
import github.zerorooot.nap511.activity.helper.SubtitlePanelController
import github.zerorooot.nap511.activity.helper.VideoDrawerController
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.dialog.CaptchaVideoContent
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.util.network.VideoErrorMapper
import github.zerorooot.nap511.util.network.isHandledException
import github.zerorooot.nap511.viewmodel.VideoUiEvent
import github.zerorooot.nap511.viewmodel.VideoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager

/**
 * 视频播放界面 Activity
 * 
 * 核心功能包括：
 * 1. 沉浸式全屏视频播放与系统状态栏/刘海屏适配；
 * 2. 结合 GSYVideoPlayer 与 ExoPlayer 进行网络/本地视频播放控制；
 * 3. 关联 VideoViewModel 进行状态监听（集数切换、返回保存历史记录、UI 事件提示等）；
 * 4. 结合 SubtitlePanelController 与 VideoDrawerController 实现字幕设置与侧边抽屉菜单；
 * 5. 处理横竖屏旋转与返回按键逻辑。
 */
class VideoActivity : AppCompatActivity() {

    /** 视频播放业务逻辑与状态管理 ViewModel */
    private val viewModel: VideoViewModel by viewModels()

    /** 自定义 GSY 视频播放器控件 */
    private lateinit var videoPlayer: MyGSYVideoPlayer

    /** 字幕面板控制器（负责字幕下载、选择与样式绑定） */
    private lateinit var subtitlePanelController: SubtitlePanelController

    /** 侧边抽屉面板控制器（负责选集列表、画面设置、字幕面板等抽屉交互） */
    private lateinit var videoDrawerController: VideoDrawerController

    /** 115 账号安全验证码 WebView 弹窗实例 */
    private var captchaDialog: Dialog? = null

    private var captchaComposeView: ComposeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启用 Edge-to-Edge 边缘到边缘全面屏设计
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        // ----------------------------------------------------
        // 1. 沉浸式全屏与刘海屏适配
        // ----------------------------------------------------
        // 隐藏系统状态栏，设置顶部边缘下滑可临时呼出状态栏（沉浸模式）
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 允许全屏画面延伸到屏幕刘海/打孔区域（适配 Android 9+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // ----------------------------------------------------
        // 2. 解析启动参数并初始化 ViewModel
        // ----------------------------------------------------
        val paramsJson = intent.getStringExtra("bean")
        val launchVideoParams = Gson().fromJson(paramsJson, LaunchVideoParams::class.java)

        // 判断当前屏幕方向是否为竖屏
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        viewModel.initParams(launchVideoParams, isPortrait)

        val videoInfo = launchVideoParams.videoInfo
        // 构建视频请求的网络 Http Header（携带 Cookie 和 User-Agent）
        val headerMap = hashMapOf(
            "cookie" to UserSessionManager.cookie,
            "User-Agent" to ConfigKeyUtil.USER_AGENT
        )
        // 播放地址优先选择在线播放链接，若为空则退而使用下载链接
        val address = videoInfo.videoUrl.ifEmpty { videoInfo.downloadUrl }
        val title = videoInfo.fileName

        // ----------------------------------------------------
        // 3. 播放器控件绑定与底层内核 (ExoPlayer) 初始化
        // ----------------------------------------------------
        videoPlayer = findViewById(R.id.pre_video_player)
        // 根据 ViewModel 配置设置是否隐藏加载中的等待视图
        videoPlayer.setHideLoadingView(viewModel.hideLoading)

        // 初始化 ExoPlayer 内核的 OkHttp 拦截器错误回调
        ExoPlayerInitializer.initGSYExoPlayerWithOkHttp { url, contentType, errorBody ->
            viewModel.handleInterceptorError(
                currentPositionMs = videoPlayer.currentPositionWhenPlaying,
                url = url,
                contentType = contentType,
                errorBody = errorBody
            )
        }
        // 设置 GSYVideoPlayer 使用 Exo2PlayerManager 作为播放引擎内核
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

        // ----------------------------------------------------
        // 4. 动态为顶部控制栏 (layout_top) 设置系统边距避让 (WindowInsets)
        // ----------------------------------------------------
        val layoutTop = videoPlayer.findViewById<View>(R.id.layout_top)
        val baseTopHeight = (56 * resources.displayMetrics.density).toInt() // 56dp 标准原始高度
        ViewCompat.setOnApplyWindowInsetsListener(videoPlayer) { _, insets ->
            val isPortrait =
                resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val topInset = if (isPortrait) {
                // 即使状态栏已隐藏，getInsetsIgnoringVisibility 依然能准确获取状态栏高度
                val statusBars =
                    insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                maxOf(statusBars.top, cutout.top)
            } else {
                0 // 横屏下顶部不需要状态栏避让
            }
            // 增加顶部内边距，并将控件总高度增加 topInset，防止标题和按钮被刘海遮挡
            layoutTop?.updatePadding(top = topInset)
            layoutTop?.updateLayoutParams {
                height = baseTopHeight + topInset
            }
            insets
        }

        // ----------------------------------------------------
        // 5. 播放器属性设置与按钮点击监听绑定
        // ----------------------------------------------------
        videoPlayer.apply {
            setUp(address, false, null, headerMap, title)
            titleTextView.visibility = View.VISIBLE
            titleTextView.isSelected = true // 开启文本跑马灯滚动效果
            seekRatio = 10f // 设置滑动手势快进/快退的灵敏比例
            backButton.visibility = View.VISIBLE
            isShowFullAnimation = false // 关闭全屏切换默认动画

            // 全屏/旋转屏幕按钮
            fullscreenButton.setOnClickListener {
                rotateScreen()
            }
            // 顶部返回按钮
            backButton.setOnClickListener {
                performBack()
            }
            // 上一集按钮
            findViewById<View>(R.id.prev_episode)?.setOnClickListener {
                playNextVideo(false)
            }
            // 下一集按钮
            findViewById<View>(R.id.next_episode)?.setOnClickListener {
                playNextVideo(true)
            }
        }

        // 绑定 GSY 播放回调并开始播放
        videoPlayer.setVideoAllCallBack(gSYErrorCallBack)
        videoPlayer.startPlayLogic()

        // ----------------------------------------------------
        // 6. 控制器初始化（字幕与侧边抽屉）
        // ----------------------------------------------------
        subtitlePanelController = SubtitlePanelController(this, videoPlayer, viewModel)
        videoDrawerController =
            VideoDrawerController(this, videoPlayer, viewModel, subtitlePanelController)

        // 若传入了本地字幕列表，默认加载并应用首条本地字幕
        if (launchVideoParams.localSubtitleItem.isNotEmpty()) {
            viewModel.loadSubtitles(0L, false)
            subtitlePanelController.applySubtitle(
                launchVideoParams.localSubtitleItem.first(),
                showToast = false
            )
        }

        // ----------------------------------------------------
        // 7. 系统返回键按压处理
        // ----------------------------------------------------
        onBackPressedDispatcher.addCallback(this) {
            // 如果侧边抽屉面板正处于显示状态，优先关闭抽屉；否则执行标准返回操作
            if (videoPlayer.isAnyDrawerShowing) {
                videoPlayer.hideAllDrawers()
            } else {
                performBack()
            }
        }

        // ----------------------------------------------------
        // 8. 观察 ViewModel 状态与 UI 事件
        // ----------------------------------------------------
        observeViewModel()
    }

    /**
     * 订阅并监听 [VideoViewModel] 的状态 ([VideoViewModel.uiState]) 与单次 UI 事件 ([VideoViewModel.uiEvent])
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            // 在生命周期处于 STARTED 状态时收集数据，处于 STOPPED 时自动暂停
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. 监听 UI 状态变化
                launch {
                    viewModel.uiState.collect { state ->
                        // 更新抽屉菜单中选中的剧集索引
                        videoDrawerController.updateEpisodeSelectedIndex(state.fileBeanIndex)

                        // 更新"上一集"按钮的启用状态与透明度
                        videoPlayer.findViewById<View>(R.id.prev_episode)?.apply {
                            isEnabled = state.hasPrev
                            alpha = if (state.hasPrev) 1.0f else 0.3f
                        }
                        // 更新"下一集"按钮的启用状态与透明度
                        videoPlayer.findViewById<View>(R.id.next_episode)?.apply {
                            isEnabled = state.hasNext
                            alpha = if (state.hasNext) 1.0f else 0.3f
                        }

                        // 将最新的状态同步更新给字幕面板控制器
                        subtitlePanelController.bindState(state)
                    }
                }
                // 2. 监听单次 UI 事件
                launch {
                    viewModel.uiEvent.collect { event ->
                        when (event) {
                            // 切换播放下一集/上一集视频
                            is VideoUiEvent.PlayNext -> {
                                videoPlayer.playNext(event.videoUrl, event.title)
                            }

                            // 弹出 Toast 消息
                            is VideoUiEvent.Toast -> {
                                Toast.makeText(
                                    this@VideoActivity,
                                    event.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            // 旋转屏幕
                            is VideoUiEvent.RotateScreen -> {
                                rotateScreen()
                            }

                            // 弹出 115 账号安全验证码弹窗
                            is VideoUiEvent.ShowCaptchaDialog -> {
                                XLog.i("Video Activity ShowCaptchaDialog")
                                showCaptchaDialog()
                            }

                            // 携带播放历史及结果返回上个界面
                            is VideoUiEvent.FinishWithResult -> {
                                val returnIntent = Intent().apply {
                                    putExtra("videoHistory", event.videoHistoryJson)
                                    putExtra("nav", event.nav)
                                    putExtra("toast", event.toast)
                                    putExtra("pickCode", event.pickCode)
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

    /**
     * 弹出 115 视频验证码 WebView 弹窗
     *
     * 1. 切换至主线程，暂停视频播放；
     * 2. 构建包含 [CaptchaVideoContent] 的 Compose 原生 Dialog 弹窗；
     * 3. 验证成功：关闭弹窗，获取当前视频播放地址与标题，调用 [videoPlayer.playNext] 重新播放；
     * 4. 验证取消/失败：关闭弹窗，调用 [performBack] 退出 Activity。
     *
     */
    private fun showCaptchaDialog() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (captchaDialog?.isShowing == true) return@launch

            // 释放视频播放器，停止 ExoPlayer 在后台重复重试请求
            GSYVideoManager.releaseAllVideos()

            val dialog = Dialog(this@VideoActivity, android.R.style.Theme_Translucent_NoTitleBar)
            captchaDialog = dialog

            val composeView = ComposeView(this@VideoActivity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                // 显式为 ComposeView 绑定 ViewTreeOwner，规避 Dialog 独立 Window 查找 ViewTree 失败导致的 IllegalStateException
                setViewTreeLifecycleOwner(this@VideoActivity)
                setViewTreeViewModelStoreOwner(this@VideoActivity)
                setViewTreeSavedStateRegistryOwner(this@VideoActivity)

                setContent {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CaptchaVideoContent(
                            onDismiss = {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    captchaDialog?.dismiss()
                                    captchaDialog = null
                                    performBack(toast = "验证取消", resultCode = RESULT_CANCELED)
                                }
                            },
                            onSuccess = {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    captchaDialog?.dismiss()
                                    captchaDialog = null
                                    val currentInfo = viewModel.uiState.value.videoInfo
                                    val address =
                                        currentInfo?.videoUrl?.ifEmpty { currentInfo.downloadUrl }
                                            ?: ""
                                    val title = currentInfo?.fileName ?: ""
                                    if (address.isNotEmpty()) {
                                        videoPlayer.playNext(address, title)
                                    } else {
                                        viewModel.rePlayNewVideo()
                                    }
                                }
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.window?.apply {
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                decorView.setViewTreeLifecycleOwner(this@VideoActivity)
                decorView.setViewTreeViewModelStoreOwner(this@VideoActivity)
                decorView.setViewTreeSavedStateRegistryOwner(this@VideoActivity)
            }
            dialog.setOnCancelListener {
                performBack(toast = "验证取消", resultCode = RESULT_CANCELED)
            }
            dialog.show()
        }
    }

    /**
     * 旋转切换屏幕方向：
     * 若当前为竖屏，则强制切换为横屏；若当前为横屏，则强制切换为竖屏。
     */
    @SuppressLint("SourceLockedOrientationActivity")
    fun rotateScreen() {
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 当前是竖屏，强制转为横屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        } else {
            // 当前是横屏，强制转为竖屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }
    }

    /**
     * 播放上一集或下一集
     *
     * @param isNext true 表示下一集，false 表示上一集
     */
    private fun playNextVideo(isNext: Boolean) {
        val isPortrait =
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        viewModel.playNextVideo(isNext, isPortrait, videoPlayer.currentPositionWhenPlaying)
    }

    /**
     * 执行退出/返回操作，将当前播放进度保存至历史记录，并关闭当前 Activity
     */
    private fun performBack(nav: String = "", toast: String = "", resultCode: Int = RESULT_OK) {
        viewModel.back(videoPlayer.currentPositionWhenPlaying, nav, toast, resultCode)
    }

    /**
     * GSYVideoPlayer 播放器全局异常错误监听回调
     */
    private val gSYErrorCallBack = object : GSYSampleCallBack() {
        override fun onPlayError(url: String?, vararg objects: Any?) {
            // 如果验证码弹窗正在显示，忽略播放错误回调，避免在验证码处理过程中退出 Activity
            if (captchaDialog?.isShowing == true) {
                return
            }

            // 尝试获取底层 ExoPlayer 实例及其错误对象
            val playerManager = videoPlayer.gsyVideoManager.player as? Exo2PlayerManager
            val exoPlayer = playerManager?.mediaPlayer as? ExoPlayer
            val exoError = exoPlayer?.playerError
            // 如果该异常已被 OkHttp 拦截器或其他逻辑处理，则无需重复提示处理
            if (isHandledException(exoError)) {
                return
            }

            super.onPlayError(url, objects)
            // 解析错误码获取对应中文或可读性强的错误描述
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

    override fun onPause() {
        videoPlayer.onVideoPause()
        super.onPause()
    }

    override fun onResume() {
        videoPlayer.onVideoResume()
        super.onResume()
    }

    override fun onDestroy() {
        captchaDialog?.dismiss()
        captchaDialog = null
        GSYVideoManager.releaseAllVideos()
        super.onDestroy()
    }
}

