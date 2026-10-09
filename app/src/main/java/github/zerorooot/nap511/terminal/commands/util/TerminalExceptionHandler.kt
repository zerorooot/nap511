package github.zerorooot.nap511.terminal.commands.util

import github.zerorooot.nap511.bean.BaseReturnMessage

/**
 * 终端统一异常与服务端响应错误归一化提取工具类
 */
object TerminalExceptionHandler {

    /**
     * 从 115 开放接口返回消息模型中提取统一的可读错误信息
     *
     * @param response 服务端返回的 [BaseReturnMessage]
     * @param fallback 当无可用错误信息时的降级文案
     * @return 归一化错误提示字符串
     */
    fun extractErrorMessage(response: BaseReturnMessage, fallback: String = "操作失败"): String {
        return when {
            response.error.isNotBlank() -> response.error
            response.errorMsg.isNotBlank() -> response.errorMsg
            response.message.isNotBlank() -> response.message
            response.errno.isNotBlank() -> "错误码: ${response.errno}"
            else -> fallback
        }
    }
}
