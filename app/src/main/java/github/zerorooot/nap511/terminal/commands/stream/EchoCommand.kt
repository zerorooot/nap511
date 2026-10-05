package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitAnsi
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 文本回显命令（echo）
 *
 * 将传入的所有参数拼接为空格分隔的字符串并输出到标准输出流。
 * 若输出内容包含标准 ANSI SGR 转义码，则标记为 OUTPUT_ANSI，否则标记为纯文本 OUTPUT_TEXT。
 */
class EchoCommand : TerminalCommand {

    override val name: String = "echo"

    override val description: String = "回显输出指定的文本"

    override val usage: String = "echo [text...]"

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val result = args.joinToString(" ")
        if (result.contains("\u001B[")) {
            emitAnsi(result)
        } else {
            emitText(result)
        }
    }
}
