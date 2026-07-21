package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException.CUSTOM_ERROR_CODE_BASE
import androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_UNSPECIFIED
import androidx.media3.common.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager


class VideoActivity : AppCompatActivity() {
    private lateinit var videoPlayer: MyGSYVideoPlayer
    private val videoInfo: VideoInfoBean by lazy {
        Gson().fromJson(
            intent.getStringExtra("bean")!!, VideoInfoBean::class.java
        )
    }
    private val isAutoRotate by lazy {
        videoInfo.isAutoRotate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        val originVideo = DataStoreUtil.getData(
            ConfigKeyUtil.ORIGIN_VIDEO, false
        )
        val address = if (originVideo) videoInfo.originFileUrl else videoInfo.videoUrl
        val title = videoInfo.fileName
        val cookie = App.cookie
        videoPlayer = findViewById(R.id.pre_video_player)

        videoPlayer.setVideoInfo(videoInfo)

        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

        videoPlayer.apply {
            setUp(address, false, null, mapOf("Cookie" to cookie), title)
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
                back()
            }
        }


        videoPlayer.startPlayLogic()

        //设置横屏
        lifecycleScope.launch {
            if (isAutoRotate) {
                val videoHeight = videoInfo.height
                val videoWidth = videoInfo.width
                if (videoWidth < videoHeight) {
                    rotateScreen()
                }
            }
        }


        videoPlayer.setVideoAllCallBack(object : GSYSampleCallBack() {
            override fun onPlayError(url: String?, vararg objects: Any?) {
                super.onPlayError(url, objects)
                val errorStatus =
                    if (objects[2] != null && videoPlayer.gsyVideoManager.player is Exo2PlayerManager) {
                        when (val code = (objects[2] as Int)) {
                            // 基础与通用错误
                            ERROR_CODE_UNSPECIFIED -> "发生未知错误"
                            ERROR_CODE_REMOTE_ERROR -> "服务器开小差了，请稍后再试"
                            ERROR_CODE_BEHIND_LIVE_WINDOW -> "当前直播已过期或进度太落后"
                            ERROR_CODE_TIMEOUT -> "操作超时，请检查网络"
                            ERROR_CODE_FAILED_RUNTIME_CHECK -> "系统运行环境异常"

                            // IO 与网络错误 (最常见的用户网络问题)
                            ERROR_CODE_IO_UNSPECIFIED -> "网络或文件读取发生未知错误"
                            ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败，请检查网络设置"
                            ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接超时，请重试"
                            ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "播放链接无效（服务器返回数据类型错误）"
                            ERROR_CODE_IO_BAD_HTTP_STATUS -> "服务器响应异常（视频可能已下架）"
                            ERROR_CODE_IO_FILE_NOT_FOUND -> "找不到该视频文件"
                            ERROR_CODE_IO_NO_PERMISSION -> "应用没有网络或文件读取权限"
                            ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "安全限制，不允许使用非加密的 HTTP 链接"
                            ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "视频数据读取出错"

                            // 解析错误 (文件格式问题)
                            ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "视频文件已损坏"
                            ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "播放列表文件已损坏"
                            ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "不支持该视频文件格式"
                            ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "不支持该播放列表格式"

                            // 解码与播放错误 (设备性能或兼容性问题)
                            ERROR_CODE_DECODER_INIT_FAILED -> "视频解码器初始化失败"
                            ERROR_CODE_DECODER_QUERY_FAILED -> "当前设备找不到合适的视频解码器"
                            ERROR_CODE_DECODING_FAILED -> "视频解码失败，无法播放"
                            ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "视频规格太高，当前设备性能不足以播放"
                            ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "当前设备不支持这种视频编码格式"
                            ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "音频播放初始化失败"
                            ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "音频数据输出失败"

                            // DRM (数字版权管理) 错误
                            ERROR_CODE_DRM_UNSPECIFIED -> "版权保护模块发生未知错误"
                            ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "当前设备不支持该视频的版权保护格式"
                            ERROR_CODE_DRM_PROVISIONING_FAILED -> "获取数字版权证书失败"
                            ERROR_CODE_DRM_CONTENT_ERROR -> "受版权保护的视频内容解密失败"
                            ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "获取视频播放许可证失败"
                            ERROR_CODE_DRM_DISALLOWED_OPERATION -> "因版权限制，不允许此操作"
                            ERROR_CODE_DRM_SYSTEM_ERROR -> "设备数字版权系统底层出错"
                            ERROR_CODE_DRM_DEVICE_REVOKED -> "当前设备的播放权限已被吊销"
                            ERROR_CODE_DRM_LICENSE_EXPIRED -> "该视频的播放许可证已过期"

                            // 自定义错误
                            CUSTOM_ERROR_CODE_BASE -> "发生自定义系统错误"
                            else -> "发生未记录的错误 (错误码: $code)" // 如果没有匹配项，保持原值（或者你可以替换为 "UNKNOWN_ERROR" 等默认字符串）
                        }
                    } else {
                        "UNKNOWN_ERROR"
                    }
                XLog.d("$title 播放失败 $errorStatus")
                Toast.makeText(baseContext, "$errorStatus 播放失败~", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        onBackPressedDispatcher.addCallback(this) {
            back()
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


    private fun back() {
        val currentDuration = (videoPlayer.currentPositionWhenPlaying / 1000).toInt()
        val fileBeanIndex = intent.getIntExtra("fileBeanIndex", -1)
// 1. 创建一个新的 Intent 用来装载要返回的数据
        val returnIntent = Intent().apply {
            putExtra("current_time", currentDuration)
            putExtra("fileBeanIndex", fileBeanIndex)
            putExtra("pickCode", videoInfo.pickCode)
        }
        // 2. 设置结果码为 RESULT_OK，并传入 Intent
        setResult(RESULT_OK, returnIntent)

        //释放所有
        videoPlayer.setVideoAllCallBack(null);
        finish()
    }


}