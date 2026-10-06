package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.archetype.StreamPipelineCommand
import github.zerorooot.nap511.terminal.engine.archetype.StreamPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.emitText

/**
 * 输出尾部 N 行文本命令（tail）
 *
 * 继承 [StreamPipelineCommand]，在编译期提取行数限制并装配滑动窗口收集计划。
 * 默认截取末尾 10 行，支持使用 -n <NUM> 显式指定行数。
 */
class TailCommand : StreamPipelineCommand() {

    override val name: String = "tail"

    override val description: String = "输出末尾 N 行（默认 10 行）"

    override val usage: String = "tail [-n <行数>]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-n <NUM>", "指定输出的后 N 行数")
    )

    override val valueOptions: Set<String> = setOf("-n")

    override fun compilePlan(ast: CommandInvocationAst): Result<StreamPlan> {
        val limit = ast.getIntOption("-n", default = 10) ?: 10

        val plan = StreamPlan { stdin, collector ->
            if (limit <= 0) {
                return@StreamPlan
            }
            // 维护定长滑动窗口缓冲末尾数据
            val buffer = ArrayDeque<String>(limit + 1)
            stdin.collect { line ->
                buffer.addLast(line)
                if (buffer.size > limit) {
                    buffer.removeFirst()
                }
            }
            buffer.forEach { collector.emitText(it) }
        }

        return Result.success(plan)
    }
}
