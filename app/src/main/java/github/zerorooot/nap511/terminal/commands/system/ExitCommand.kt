package github.zerorooot.nap511.terminal.commands.system

import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.TerminalControlTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 退出终端命令（exit）
 *
 * 向终端输出统一协议定义的退出标记 [TerminalControlTokens.EXIT]，
 * 通知终端 ViewModel 优雅关闭终端会话或退出页面。
 */
class ExitCommand : TerminalCommand {

    override val name: String = "exit"

    override val description: String = "退出终端"

    override val usage: String = "exit"

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        // 输出统一控制标记通知终端会话退出
        emit(TerminalControlTokens.EXIT)
    }
}
