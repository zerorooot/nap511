package github.zerorooot.nap511.terminal.commands.system

import github.zerorooot.nap511.terminal.commands.TerminalCommand
import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 屏幕清空命令（clear）
 *
 * 向终端输出特殊的屏幕清除标记 __TERMINAL_CLEAR_SCREEN__，由 UI 观察者统一重置屏幕内容。
 */
class ClearCommand : TerminalCommand {

    override val name: String = "clear"

    override val description: String = "清空终端屏幕历史输出"

    override val usage: String = "clear"

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        emit("__TERMINAL_CLEAR_SCREEN__")
    }
}
