package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 将 Throwable 转换为对用户友好的提示文案
 */
fun Throwable.toUserFriendlyMessage(): String {
    return when (this) {
        is SocketTimeoutException -> "网络连接超时，请重试"
        is UnknownHostException -> "无法连接到服务器，请检查网络"
        is IOException -> "网络请求失败: ${localizedMessage ?: "未知网络错误"}"
        else -> localizedMessage ?: message ?: "系统发生未知错误"
    }
}

/**
 * Result<T> 扩展：统一在 onFailure 时进行 Log 打印与 Toast 提示
 *
 * @param tag 日志 Tag 标识
 * @param customMsg 自定义提示文案（若为空则自动解析异常信息）
 */
fun <T> Result<T>.onFailureToastAndLog(
    tag: String = "AppError",
    customMsg: String? = null
): Result<T> = onFailure { e ->
    val userMsg = customMsg ?: e.toUserFriendlyMessage()
    XLog.e("[$tag] $userMsg", e)
    App.instance.toast(userMsg)
}

/**
 * 包装函数：直接执行 Block 并自动在 onFailure 时处理 Log 打印与 Toast 提示
 */
inline fun <T> runCatchingWithToast(
    tag: String = "AppError",
    customMsg: String? = null,
    block: () -> T
): Result<T> {
    return runCatching(block).onFailureToastAndLog(tag, customMsg)
}
