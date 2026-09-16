package github.zerorooot.nap511.util.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 拦截 115 缩略图过期重定向图片 (err/401.png)
 */
class OneOneFiveImageExpirationInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 获取经过 302 重定向后的最终 URL
        val finalUrl = response.request.url.toString()

        // 匹配 115 防盗链过期的错误图片路径
        if (finalUrl.contains("err/401.png") || finalUrl.contains("/err/")) {
            // 必须先关闭原 response，避免 HTTP 连接/流资源泄漏
            response.close()

            // 抛出异常或手动构建一个 HTTP 401 错误响应
            // 这样能强制阻止 Coil 把这张错误图片写入本地磁盘缓存！
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("115 Image URL Expired: redirected to $finalUrl")
                .body("Image Expired".toResponseBody("text/plain".toMediaTypeOrNull()))
                .build()
        }

        return response
    }
}