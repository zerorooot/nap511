package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.StreamSourceItem
import github.zerorooot.nap511.terminal.engine.archetype.StreamPipelineCommand
import github.zerorooot.nap511.terminal.engine.archetype.StreamPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.engine.completion.CommandCompleter
import github.zerorooot.nap511.terminal.engine.completion.StandardCompleter
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.toList

/**
 * 文本行排序命令（sort）
 *
 * 继承 [StreamPipelineCommand]，遵循 POSIX.1-2017 规范：
 * 1. 支持指定操作数 `sort [-r] [-n] [-u] [files...]`；
 * 2. 汇聚所有输入数据源（stdin 或多文件）至同一个全局列表进行统一排序；
 * 3. 编译期构建比较器（数值 `-n` / 逆序 `-r` / 去重 `-u`）。
 */
class SortCommand : StreamPipelineCommand() {

    override val name: String = "sort"

    override val description: String = "对输入行进行文本排序"

    override val usage: String = "sort [-r] [-n] [-u] [file...]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-r", "逆序排序"),
        CommandFlag("-n", "按数值大小排序"),
        CommandFlag("-u", "去重（唯一输出）")
    )

    override val completer: CommandCompleter = StandardCompleter.TEXT_FILES

    override fun compilePlan(ast: CommandInvocationAst): Result<StreamPlan> {
        val reverse = ast.hasFlag("-r")
        val numeric = ast.hasFlag("-n")
        val unique = ast.hasFlag("-u")

        // 编译期合成比较器：如果是数值比较则按浮点数值转换，否则按纯字典序
        val baseComparator: Comparator<String> = if (numeric) {
            Comparator { a, b ->
                val numA = a.trim().toDoubleOrNull() ?: 0.0
                val numB = b.trim().toDoubleOrNull() ?: 0.0
                numA.compareTo(numB)
            }
        } else {
            Comparator { a, b -> a.compareTo(b) }
        }

        val finalComparator = if (reverse) baseComparator.reversed() else baseComparator

        val plan = StreamPlan { sources, collector ->
            val allLines = mutableListOf<String>()

            for (source in sources) {
                when (source) {
                    is StreamSourceItem.Error -> {
                        collector.emitError("sort: ${source.message}")
                    }

                    is StreamSourceItem.DataStream -> {
                        allLines.addAll(source.lines.toList())
                    }
                }
            }

            val sorted = allLines.sortedWith(finalComparator)
            val finalLines = if (unique) sorted.distinct() else sorted
            finalLines.forEach { collector.emitText(it) }
        }

        return Result.success(plan)
    }
}
