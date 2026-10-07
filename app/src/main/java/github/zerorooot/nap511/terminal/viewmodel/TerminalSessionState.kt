package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.runtime.Immutable

/**
 * 终端会话运行状态模型 (Terminal Session State)
 *
 * 遵循 Unix/POSIX 控制终端状态机契约：
 * 1. [Idle]：空闲就绪态，前台无任务运行，展现完整上下文与 "$ " 提示符，接受新命令输入与补全；
 * 2. [Executing]：前台任务运行中，隐藏 "$ " 提示符与上下文以杜绝“伪就绪”误导，抑制普通字符输入与回车，
 *    仅响应中断信号 (Ctrl+C / SIGINT)，底层保持 IME 隧道连通允许软键盘自由唤起与收起；
 * 3. [Confirming]：前台任务发起交互式二次确认（如 rm 确认删除），展示 "confirm (yes/no): " 提示符并接收确认输入。
 */
@Immutable
sealed interface TerminalSessionState {
    /**
     * 空闲就绪态：前台无命令运行，展现完整 "$ " 提示符与上下文路径，接受命令输入与自动补全
     */
    data object Idle : TerminalSessionState

    /**
     * 前台任务执行中：隐藏提示符与上下文，仅允许 Ctrl+C 中断，抑制普通输入
     *
     * @param command 当前正在运行的命令行
     */
    data class Executing(val command: String = "") : TerminalSessionState

    /**
     * 二次确认挂起中：前台命令挂起等待用户交互确认，展现专属确认提示符
     *
     * @param prompt 确认提问文本
     */
    data class Confirming(val prompt: String = "") : TerminalSessionState

    val isExecuting: Boolean get() = this is Executing || this is Confirming
    val isWaitingConfirmation: Boolean get() = this is Confirming
}
