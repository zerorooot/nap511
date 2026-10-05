package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.commands.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 输出尾部 N 行文本命令（tail）
 *
 * 默认截取末尾 10 行，支持使用 -n <NUM> 显式指定行数。
 */
class TailCommand : TerminalCommand {

    override val name: String = "tail"

    override val description: String = "输出末尾 N 行（默认 10 行）"

    override val usage: String = "tail [-n <行数>]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-n <NUM>", "指定输出的后 N 行数")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        val limit = cmdArgs.getIntOption("-n", default = 10) ?: 10

        // 维护定长滑动窗口缓冲末尾数据
        val buffer = mutableListOf<String>()
        stdin.collect { line ->
            buffer.add(line)
            if (buffer.size > limit) {
                buffer.removeAt(0)
            }
        }
        buffer.forEach { emit(it) }
    }
}
