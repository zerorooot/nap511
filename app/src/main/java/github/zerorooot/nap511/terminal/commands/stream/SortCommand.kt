package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * 文本行排序命令（sort）
 *
 * 支持字符字典序或数值排序（-n）、逆序排序（-r）以及重复行过滤（-u）。
 */
class SortCommand : TerminalCommand {

    override val name: String = "sort"

    override val description: String = "对输入行进行文本排序"

    override val usage: String = "sort [-r] [-n] [-u]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-r", "逆序排序"),
        CommandFlag("-n", "按数值大小排序"),
        CommandFlag("-u", "去重（唯一输出）")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        val reverse = cmdArgs.hasFlag("-r")
        val numeric = cmdArgs.hasFlag("-n")
        val unique = cmdArgs.hasFlag("-u")

        val lines = stdin.toList()
        val comparator = Comparator<String> { a, b ->
            if (numeric) {
                val numA = a.trim().toDoubleOrNull() ?: 0.0
                val numB = b.trim().toDoubleOrNull() ?: 0.0
                numA.compareTo(numB)
            } else {
                a.compareTo(b)
            }
        }

        val sorted = if (reverse) {
            lines.sortedWith(comparator.reversed())
        } else {
            lines.sortedWith(comparator)
        }

        val finalLines = if (unique) sorted.distinct() else sorted
        finalLines.forEach { emit(it) }
    }
}
