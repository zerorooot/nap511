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
import kotlinx.coroutines.flow.take

/**
 * 输出首部 N 行文本命令（head）
 *
 * 遵循 POSIX.1-2017 规范：
 * 1. 支持指定操作数 `head [-n <NUM>] [files...]`；
 * 2. 多文件时输出文件标头 `==> <filename> <==`，文件块之间空一行隔离；单文件或 stdin 输入时不显示标头；
 * 3. 即时流熔断：各输入源使用 `source.lines.take(limit)`，读取达到指定行数后立即终止该流读取，
 *    管道下游熔断时通过 CancellationException 自然取消上游协程。
 */
class HeadCommand : StreamPipelineCommand() {

    override val name: String = "head"

    override val description: String = "输出前 N 行（默认 10 行）"

    override val usage: String = "head [-n <行数>] [file...]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-n <NUM>", "指定输出的前 N 行数")
    )

    override val valueOptions: Set<String> = setOf("-n")

    override val completer: CommandCompleter = StandardCompleter.TEXT_FILES

    override fun compilePlan(ast: CommandInvocationAst): Result<StreamPlan> {
        val limit = ast.getIntOption("-n", default = 10) ?: 10

        val plan = StreamPlan { sources, collector ->
            if (limit <= 0) {
                return@StreamPlan
            }

            val multipleSources = sources.size > 1
            var printedBlockCount = 0

            for (source in sources) {
                when (source) {
                    is StreamSourceItem.Error -> {
                        collector.emitError("head: ${source.message}")
                    }

                    is StreamSourceItem.DataStream -> {
                        if (multipleSources) {
                            if (printedBlockCount > 0) {
                                collector.emitText("")
                            }
                            collector.emitText("==> ${source.sourceName} <==")
                        }
                        printedBlockCount++

                        source.lines.take(limit).collect { line ->
                            collector.emitText(line)
                        }
                    }
                }
            }
        }

        return Result.success(plan)
    }
}
