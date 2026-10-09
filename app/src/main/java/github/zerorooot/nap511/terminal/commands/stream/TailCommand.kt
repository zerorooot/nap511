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

/**
 * 输出尾部 N 行文本命令（tail）
 *
 * 遵循 POSIX.1-2017 规范：
 * 1. 支持指定操作数 `tail [-n <NUM>] [files...]`；
 * 2. 多文件时输出文件标头 `==> <filename> <==`，文件块之间空一行隔离；单文件或 stdin 输入时不显示标头；
 * 3. 维护每个数据源定长为 limit 的滑动窗口，收集末尾 N 行文本输出。
 */
class TailCommand : StreamPipelineCommand() {

    override val name: String = "tail"

    override val description: String = "输出末尾 N 行（默认 10 行）"

    override val usage: String = "tail [-n <行数>] [file...]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-n <NUM>", "指定输出的后 N 行数")
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
                        collector.emitError("tail: ${source.message}")
                    }

                    is StreamSourceItem.DataStream -> {
                        if (multipleSources) {
                            if (printedBlockCount > 0) {
                                collector.emitText("")
                            }
                            collector.emitText("==> ${source.sourceName} <==")
                        }
                        printedBlockCount++

                        // 维护定长滑动窗口缓冲末尾数据
                        val buffer = ArrayDeque<String>(limit + 1)
                        source.lines.collect { line ->
                            buffer.addLast(line)
                            if (buffer.size > limit) {
                                buffer.removeFirst()
                            }
                        }
                        buffer.forEach { collector.emitText(it) }
                    }
                }
            }
        }

        return Result.success(plan)
    }
}
