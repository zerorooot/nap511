package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 输出首部 N 行文本命令（head）
 *
 * 默认截取前 10 行，支持使用 -n <NUM> 显式指定行数。
 */
class HeadCommand : TerminalCommand {

    override val name: String = "head"

    override val description: String = "输出前 N 行（默认 10 行）"

    override val usage: String = "head [-n <行数>]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-n <NUM>", "指定输出的前 N 行数")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        val limit = cmdArgs.getIntOption("-n", default = 10) ?: 10

        var count = 0
        stdin.collect { line ->
            if (count < limit) {
                emit(line)
                count++
            }
        }
    }
}
