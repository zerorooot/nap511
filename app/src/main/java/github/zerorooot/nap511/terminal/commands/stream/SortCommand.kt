package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.archetype.StreamPipelineCommand
import github.zerorooot.nap511.terminal.engine.archetype.StreamPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.toList

/**
 * 文本行排序命令（sort）
 *
 * 继承 [StreamPipelineCommand]，在编译期根据 `-n` 与 `-r` 一次性构建静态不可变的比较器 Comparator，
 * 执行期无条件复用已编译好的比较器，完全消除比较过程中的分支判断与动态标志判定。
 */
class SortCommand : StreamPipelineCommand() {

    override val name: String = "sort"

    override val description: String = "对输入行进行文本排序"

    override val usage: String = "sort [-r] [-n] [-u]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-r", "逆序排序"),
        CommandFlag("-n", "按数值大小排序"),
        CommandFlag("-u", "去重（唯一输出）")
    )

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

        val plan = StreamPlan { stdin, collector ->
            val lines = stdin.toList()
            val sorted = lines.sortedWith(finalComparator)
            val finalLines = if (unique) sorted.distinct() else sorted
            finalLines.forEach { collector.emitText(it) }
        }

        return Result.success(plan)
    }
}
