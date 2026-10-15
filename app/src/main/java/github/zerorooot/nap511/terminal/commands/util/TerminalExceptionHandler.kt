package github.zerorooot.nap511.terminal.commands.util

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 终端统一异常与服务端响应错误归一化提取工具类
 */
object TerminalExceptionHandler {

    /**
     * 从 115 开放接口返回消息模型中提取统一的可读错误信息
     * 支持识别登录态与凭据失效场景并输出引导提示
     *
     * @param response 服务端返回的 [BaseReturnMessage]
     * @param fallback 当无可用错误信息时的降级文案
     * @return 归一化错误提示字符串
     */
    fun extractErrorMessage(response: BaseReturnMessage, fallback: String = "操作失败"): String {
        // 识别常见的 115 凭据失效错误码与提示文本
        if (response.errno == "990001" || response.errno == "990002" ||
            response.error.contains("登录") || response.message.contains("登录") || response.errorMsg.contains("登录")) {
            return CommandMessages.authExpired()
        }
        return when {
            response.error.isNotBlank() -> response.error
            response.errorMsg.isNotBlank() -> response.errorMsg
            response.message.isNotBlank() -> response.message
            response.errno.isNotBlank() -> "错误码: ${response.errno}"
            else -> fallback
        }
    }

    /**
     * 从 115 文件夹创建响应模型中提取统一的可读错误信息
     *
     * @param response 服务端返回的 [CreateFolderMessage]
     * @param fallback 当无可用错误信息时的降级文案
     * @return 归一化错误提示字符串
     */
    fun extractErrorMessage(response: CreateFolderMessage, fallback: String = "创建失败"): String {
        if (response.errno == "990001" || response.errno == "990002" || response.error.contains("登录")) {
            return CommandMessages.authExpired()
        }
        return when {
            response.error.isNotBlank() -> response.error
            response.errno.isNotBlank() -> "错误码: ${response.errno}"
            else -> fallback
        }
    }

    /**
     * 从底层 Throwable 中提取终端友好错误信息
     * 关键安全规范：必须显式重抛 CancellationException，确保终端任务中断（Ctrl+C）与超时取消畅通
     *
     * @param throwable 捕获的底层异常
     * @param fallback 当无可用错误信息时的降级文案
     * @return 归一化错误提示字符串
     */
    fun extractErrorMessage(throwable: Throwable, fallback: String = "网络操作异常"): String {
        if (throwable is CancellationException) {
            throw throwable
        }
        return when (throwable) {
            is UnknownHostException -> "无法连接到服务器，请检查网络连接"
            is SocketTimeoutException -> "网络请求超时，请稍后重试"
            is ConnectException -> "网络连接失败，请确认服务是否正常"
            is SSLException -> "SSL 握手或安全证书异常"
            is HttpException -> "HTTP 请求异常 (${throwable.code()} ${throwable.message()})"
            is IOException -> throwable.message.takeUnless { it.isNullOrBlank() } ?: "网络 I/O 读写异常"
            else -> throwable.message.takeUnless { it.isNullOrBlank() } ?: fallback
        }
    }
}

