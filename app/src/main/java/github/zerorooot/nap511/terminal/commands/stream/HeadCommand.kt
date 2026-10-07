package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.archetype.StreamPipelineCommand
import github.zerorooot.nap511.terminal.engine.archetype.StreamPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.emitText
import github.zerorooot.nap511.terminal.engine.completion.CommandCompleter
import github.zerorooot.nap511.terminal.engine.completion.StandardCompleter
/**
 * 输出首部 N 行文本命令（head）
 *
 * 继承 [StreamPipelineCommand]，在编译期提取行数限制并装配为流式截断计划。
 * 默认截取前 10 行，支持使用 -n <NUM> 显式指定行数。
 */
class HeadCommand : StreamPipelineCommand() {

    override val name: String = "head"

    override val description: String = "输出前 N 行（默认 10 行）"

    override val usage: String = "head [-n <行数>]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-n <NUM>", "指定输出的前 N 行数")
    )

    override val valueOptions: Set<String> = setOf("-n")

    override val completer: CommandCompleter = StandardCompleter.TEXT_FILES

    override fun compilePlan(ast: CommandInvocationAst): Result<StreamPlan> {
        val limit = ast.getIntOption("-n", default = 10) ?: 10

        val plan = StreamPlan { stdin, collector ->
            if (limit <= 0) {
                return@StreamPlan
            }
            var count = 0
            stdin.collect { line ->
                if (count < limit) {
                    collector.emitText(line)
                    count++
                }
            }
        }

        return Result.success(plan)
    }
}
