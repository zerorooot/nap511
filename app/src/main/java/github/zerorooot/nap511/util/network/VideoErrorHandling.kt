package github.zerorooot.nap511.util.network

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
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
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

fun isHandledException(throwable: Throwable?): Boolean {
    var cause = throwable
    while (cause != null) {
        if (cause is HandledVideoException) return true
        cause = cause.cause
    }
    return false
}

/**
 * 视频请求错误拦截器
 * @param onErrorCallback 当状态码非 2xx 时触发回调：(url, contentType, errorBody)
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
        if (contentType.isEmpty()) return false

        val isBlacklisted = contentType.contains("application/json") ||
                contentType.contains("text/html") ||
                contentType.contains("text/plain") ||
                contentType.contains("application/xml") ||
                contentType.contains("text/xml")

        if (isBlacklisted) return true

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

        return !isMediaStream
    }
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

object VideoErrorMapper {
    val playbackErrorMessageMap: Map<Int, String> = mapOf(
        // 基础与通用错误
        ERROR_CODE_UNSPECIFIED to "发生未知错误",
        ERROR_CODE_REMOTE_ERROR to "服务器开小差了，请稍后再试",
        ERROR_CODE_BEHIND_LIVE_WINDOW to "当前直播已过期或进度太落后",
        ERROR_CODE_TIMEOUT to "操作超时，请检查网络",
        ERROR_CODE_FAILED_RUNTIME_CHECK to "系统运行环境异常",
        // IO 与网络错误 (最常见的用户网络问题)
        ERROR_CODE_IO_UNSPECIFIED to "网络或文件读取发生未知错误",
        ERROR_CODE_IO_NETWORK_CONNECTION_FAILED to "网络连接失败，请检查网络设置",
        ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT to "网络连接超时，请重试",
        ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE to "播放链接无效（服务器返回数据类型错误）",
        ERROR_CODE_IO_BAD_HTTP_STATUS to "服务器响应异常（视频可能已下架）",
        ERROR_CODE_IO_FILE_NOT_FOUND to "找不到该视频文件",
        ERROR_CODE_IO_NO_PERMISSION to "应用没有网络或文件读取权限",
        ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED to "安全限制，不允许使用非加密的 HTTP 链接",
        ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE to "视频数据读取出错",

        // 解析错误 (文件格式问题)
        ERROR_CODE_PARSING_CONTAINER_MALFORMED to "视频文件已损坏",
        ERROR_CODE_PARSING_MANIFEST_MALFORMED to "播放列表文件已损坏，可能需要验证(高级设置->视频播放验证)",
        ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED to "不支持该视频文件格式",
        ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED to "不支持该播放列表格式",

        // 解码与播放错误 (设备性能或兼容性问题)
        ERROR_CODE_DECODER_INIT_FAILED to "视频解码器初始化失败",
        ERROR_CODE_DECODER_QUERY_FAILED to "当前设备找不到合适的视频解码器",
        ERROR_CODE_DECODING_FAILED to "视频解码失败，无法播放",
        ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES to "视频规格太高，当前设备性能不足以播放",
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED to "当前设备不支持这种视频编码格式",
        ERROR_CODE_AUDIO_TRACK_INIT_FAILED to "音频播放初始化失败",
        ERROR_CODE_AUDIO_TRACK_WRITE_FAILED to "音频数据输出失败",

        // DRM (数字版权管理) 错误
        ERROR_CODE_DRM_UNSPECIFIED to "版权保护模块发生未知错误",
        ERROR_CODE_DRM_SCHEME_UNSUPPORTED to "当前设备不支持该视频的版权保护格式",
        ERROR_CODE_DRM_PROVISIONING_FAILED to "获取数字版权证书失败",
        ERROR_CODE_DRM_CONTENT_ERROR to "受版权保护的视频内容解密失败",
        ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED to "获取视频播放许可证失败",
        ERROR_CODE_DRM_DISALLOWED_OPERATION to "因版权限制，不允许此操作",
        ERROR_CODE_DRM_SYSTEM_ERROR to "设备数字版权系统底层出错",
        ERROR_CODE_DRM_DEVICE_REVOKED to "当前设备的播放权限已被吊销",
        ERROR_CODE_DRM_LICENSE_EXPIRED to "该视频的播放许可证已过期",

        // 自定义错误
        CUSTOM_ERROR_CODE_BASE to "发生自定义系统错误"
    )

    fun getErrorMessage(code: Int): String {
        return playbackErrorMessageMap.getOrDefault(code, "").ifEmpty { "发生未记录的错误 (错误码: $code)" }
    }
}
