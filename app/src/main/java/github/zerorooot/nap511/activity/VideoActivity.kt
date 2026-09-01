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
import androidx.annotation.OptIn
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import tv.danmaku.ijk.media.exo2.ExoMediaSourceInterceptListener
import tv.danmaku.ijk.media.exo2.ExoSourceManager
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

data class OssError(
    val code: String = "",
    val message: String = "",
    val requestId: String = "",
    val hostId: String = "",
    val actualObjectSize: Long = 0L,
    val rangeRequested: String = ""
)

/**
 * 标识已被拦截器接管并处理过的视频异常
 */
class HandledVideoException(message: String) : IOException(message)

/**
 * 视频请求错误拦截器
 * @param onErrorCallback 当状态码非 2xx 时触发回调：(url, httpCode, responseBody)
 */
class VideoErrorInterceptor(
    private val onErrorCallback: ((url: String, contentType: MediaType, errorBody: String) -> Boolean)
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        val response = chain.proceed(request)
        val body = response.body
        val contentType = body.contentType() ?: "application/null".toMediaType()
        val contentTypeString = contentType.toString().lowercase()

        // 2. 判断是否属于典型的“非视频/非音视频流”响应类型
        val isErrorContentType = isNonMediaContentType(contentTypeString)

        if (isErrorContentType) {
            // 使用 peekBody 窥探返回的错误信息（如 JSON 字符串或 HTML 网页）
            val errorBody = try {
                response.peekBody(1024 * 1024).string()
            } catch (e: Exception) {
                ""
            }
            // 回调业务层通知（比如提取 JSON 里的 code 和 msg）
            if (onErrorCallback.invoke(url, contentType, errorBody)) {
                // 这会让 ExoPlayer 在 open() 阶段直接捕获网络源头错误，阻止其继续尝试解码 JSON/HTML
                throw HandledVideoException("Invalid video Content-Type: '$contentType', Error Body: $errorBody")
            }
        }

        return response
    }

    /**
     * 判断是否为非媒体类型（即业务错误类型）
     */
    private fun isNonMediaContentType(contentType: String): Boolean {
        // 如果连 Content-Type 都没返回，或者返回了典型的文本/JSON 类型
        if (contentType.isEmpty()) return false

        // 1. 明确的黑名单（优先匹配典型的错误类型）
        val isBlacklisted = contentType.contains("application/json") ||
                contentType.contains("text/html") ||
                contentType.contains("text/plain") ||
                contentType.contains("application/xml") ||
                contentType.contains("text/xml")

        if (isBlacklisted) return true

        // 2. 白名单校验（如果不在黑名单，确保它属于合法媒体流类型）
        // 常见的合法视频/音频 Content-Type 包括:
        // - video/* (video/mp4, video/x-flv 等)
        // - audio/* (audio/mpeg 等)
        // - application/x-mpegurl, application/vnd.apple.mpegurl (HLS .m3u8)
        // - application/dash+xml (DASH)
        // - application/octet-stream (通用二进制流，部分 CDN 会强制返这个)
        val isMediaStream = contentType.contains("video/") ||
                contentType.contains("audio/") ||
                contentType.contains("mpegurl") ||
                contentType.contains("dash+xml") ||
                contentType.contains("application/octet-stream")

        // 如果既不是明确的媒体流，又不是流媒体格式，则判定为错误
        return !isMediaStream
    }
}

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
        val headerMap = hashMapOf(
            "cookie" to App.cookie,
            "User-Agent" to ConfigKeyUtil.USER_AGENT
        )
        val address = videoInfo.videoUrl.ifEmpty {
            videoInfo.downloadUrl
        }
        val title = videoInfo.fileName
        videoPlayer = findViewById(R.id.pre_video_player)

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

        videoPlayer.setVideoAllCallBack(gSYErrorCallBack)

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


    private fun back(nav: String = "", toast: String = "", resultCode: Int = RESULT_OK) {
        val currentDuration = (videoPlayer.currentPositionWhenPlaying / 1000).toInt()
        val fileBeanIndex = intent.getIntExtra("fileBeanIndex", -1)
// 1. 创建一个新的 Intent 用来装载要返回的数据
        val returnIntent = Intent().apply {
            putExtra("current_time", currentDuration)
            putExtra("fileBeanIndex", fileBeanIndex)
            putExtra("pickCode", videoInfo.pickCode)
            putExtra("nav", nav)
            putExtra("toast", toast)
        }
        // 2. 设置结果码为 RESULT_OK，并传入 Intent
        setResult(resultCode, returnIntent)
        //释放所有
        videoPlayer.setVideoAllCallBack(null);
        finish()
    }


    private fun isHandledException(throwable: Throwable?): Boolean {
        var cause = throwable
        while (cause != null) {
            if (cause is HandledVideoException) return true
            cause = cause.cause
        }
        return false
    }

    val gSYErrorCallBack = object : GSYSampleCallBack() {
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
                        ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "播放列表文件已损坏，可能需要验证(高级设置->视频播放验证)"
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
            Toast.makeText(baseContext, errorStatus, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * 全局配置 GSYVideoPlayer (ExoPlayer) 使用自定义的 OkHttpClient 拦截器
     */
    fun initGSYExoPlayerWithOkHttp(context: Context) {
        // 1. 创建包含错误拦截器的 OkHttpClient
        val customOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(VideoErrorInterceptor { url, contentType, errorBody ->
                XLog.d("GSY Player 网络请求失败 [$contentType] -> Body: $errorBody")
                if (errorBody.isEmpty()) {
                    back(
                        toast = "视频地址错误！请打开\"视频解析模式\"请求正确链接",
                        resultCode = RESULT_CANCELED
                    )
                    return@VideoErrorInterceptor true
                }

                runCatching { Gson().fromJson(errorBody, JsonObject::class.java) }
                    .onSuccess { fromJson ->
                        if (fromJson.has("error")) {
                            val message = fromJson.get("error").asString
                            back(
                                nav = "VerifyVideoAccount",
                                toast = message,
                                resultCode = RESULT_CANCELED
                            )
                            return@VideoErrorInterceptor true
                        }
                    }
                runCatching { parseOssErrorWithDom(errorBody).message }
                    .onSuccess { message ->
                        back(toast = message, resultCode = RESULT_CANCELED)
                        return@VideoErrorInterceptor true
                    }

                return@VideoErrorInterceptor false
            })
            .build()

        // 3. 拦截 GSYVideoPlayer 的 MediaSource 构建流程
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
                // 2. 将 OkHttpClient 包装为 ExoPlayer 的 HttpDataSource.Factory
                val okHttpDataSourceFactory = OkHttpDataSource.Factory(customOkHttpClient)
                // 如果有自定义的 Request Header，同步给 Factory
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

    fun parseOssErrorWithDom(xmlString: String): OssError {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlString.byteInputStream())
        doc.documentElement.normalize()

        fun getValue(tag: String): String {
            return doc.getElementsByTagName(tag).item(0)?.textContent.orEmpty()
        }

        return OssError(
            code = getValue("Code"),
            message = getValue("Message"),
            requestId = getValue("RequestId"),
            hostId = getValue("HostId"),
            actualObjectSize = getValue("ActualObjectSize").toLongOrNull() ?: 0L,
            rangeRequested = getValue("RangeRequested")
        )
    }

}