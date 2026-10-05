package github.zerorooot.nap511.terminal.commands.system

import github.zerorooot.nap511.terminal.commands.TerminalCommand
import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 退出终端命令（exit）
 *
 * 向终端输出特殊的退出标记 __TERMINAL_EXIT__，通知终端 ViewModel 关闭会话或退出页面。
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
        emit("__TERMINAL_EXIT__")
    }
}
