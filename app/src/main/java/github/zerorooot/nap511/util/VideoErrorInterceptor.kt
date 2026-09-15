package github.zerorooot.nap511.util

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
