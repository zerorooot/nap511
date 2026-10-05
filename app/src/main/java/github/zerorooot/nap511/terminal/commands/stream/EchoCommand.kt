package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.commands.TerminalCommand
import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 文本回显命令（echo）
 *
 * 将传入的所有参数拼接为空格分隔的字符串并输出到标准输出流。
 */
class EchoCommand : TerminalCommand {

    override val name: String = "echo"

    override val description: String = "回显输出指定的文本"

    override val usage: String = "echo [text...]"

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        emit(args.joinToString(" "))
    }
}
