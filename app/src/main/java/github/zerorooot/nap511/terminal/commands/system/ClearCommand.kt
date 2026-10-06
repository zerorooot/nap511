package github.zerorooot.nap511.terminal.commands.system

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.TerminalControlTokens
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 屏幕清空命令（clear）
 *
 * 向终端输出统一协议定义的屏幕清除标记 [TerminalControlTokens.CLEAR_SCREEN]，
 * 由终端 ViewModel 或 UI 观察者统一重置屏幕内容。
 */
class ClearCommand : TerminalCommand {

    override val name: String = "clear"

    override val description: String = "清空终端屏幕历史输出"

    override val usage: String = "clear"

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        // 输出统一控制标记通知终端视图清屏
        emit(TerminalOutput(TerminalControlTokens.CLEAR_SCREEN, TerminalLineType.System.INFO))
    }
}
