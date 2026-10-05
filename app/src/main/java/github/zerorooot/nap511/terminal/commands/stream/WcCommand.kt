package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 文本字数统计命令（wc）
 *
 * 统计标准输入中的行数（-l）、单词数（-w）及字符数（-c）。
 */
class WcCommand : TerminalCommand {

    override val name: String = "wc"

    override val description: String = "统计行数、单词数及字符数"

    override val usage: String = "wc [-l] [-w] [-c]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "仅统计行数"),
        CommandFlag("-w", "仅统计单词数"),
        CommandFlag("-c", "仅统计字符/字节数")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val cmdArgs = CommandArgs(args)
        val linesOnly = cmdArgs.hasFlag("-l")
        val wordsOnly = cmdArgs.hasFlag("-w")
        val charsOnly = cmdArgs.hasFlag("-c")

        var lineCount = 0
        var wordCount = 0
        var charCount = 0

        stdin.collect { line ->
            lineCount++
            charCount += line.length + 1 // 模拟换行符字符数
            wordCount += line.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        }

        // 根据选项过滤输出，若未单独指定某个维度则格式化输出全部三列
        when {
            linesOnly && !wordsOnly && !charsOnly -> emitText(lineCount.toString())
            wordsOnly && !linesOnly && !charsOnly -> emitText(wordCount.toString())
            charsOnly && !linesOnly && !wordsOnly -> emitText(charCount.toString())
            else -> {
                emitText("   Lines    Words    Chars")
                emitText(
                    String.format(
                        Locale.getDefault(),
                        "%8d %8d %8d",
                        lineCount,
                        wordCount,
                        charCount
                    )
                )
            }
        }
    }
}
