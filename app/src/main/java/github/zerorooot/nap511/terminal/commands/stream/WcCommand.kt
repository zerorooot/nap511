package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.commands.util.TableAlignment
import github.zerorooot.nap511.terminal.commands.util.TableFormatter
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.StreamSourceItem
import github.zerorooot.nap511.terminal.engine.archetype.StreamPipelineCommand
import github.zerorooot.nap511.terminal.engine.archetype.StreamPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.engine.completion.CommandCompleter
import github.zerorooot.nap511.terminal.engine.completion.StandardCompleter
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import java.util.Locale

/**
 * 文本字数统计命令（wc）
 *
 * 继承 [StreamPipelineCommand]，遵循 POSIX.1-2017 规范：
 * 1. 支持指定操作数 `wc [-l] [-w] [-c] [files...]`；
 * 2. 统计维度：
 *    - `-l`：仅统计行数
 *    - `-w`：仅统计单词数
 *    - `-c`：仅统计字符/字节数
 *    - 复合或全量：同时输出行数、词数、字符数三列
 * 3. 多文件与 stdin 汇总规则：
 *    - 单输入源且为 stdin 时：纯净输出统计数值（保持管道向下游流转的纯洁性）；
 *    - 单文件输入源时：输出统计数值与文件名；
 *    - 多输入源（>= 2）时：逐个输出统计数值与文件名，并在末尾追加全局总计 `total` 行。
 */
class WcCommand : StreamPipelineCommand() {

    override val name: String = "wc"

    override val description: String = "统计行数、单词数及字符数"

    override val usage: String = "wc [-l] [-w] [-c] [file...]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "仅统计行数"),
        CommandFlag("-w", "仅统计单词数"),
        CommandFlag("-c", "仅统计字符/字节数")
    )

    override val completer: CommandCompleter = StandardCompleter.TEXT_FILES

    override fun compilePlan(ast: CommandInvocationAst): Result<StreamPlan> {
        val linesOnly = ast.hasFlag("-l")
        val wordsOnly = ast.hasFlag("-w")
        val charsOnly = ast.hasFlag("-c")

        val plan = StreamPlan { sources, collector ->
            val whitespaceRegex = Regex("\\s+")
            val isSingleStdin = sources.size == 1 && sources[0].sourceName == "stdin"
            val hasMultipleSources = sources.size > 1

            when {
                // 专有高效路径：仅统计行数
                linesOnly && !wordsOnly && !charsOnly -> {
                    var totalLines = 0
                    for (source in sources) {
                        when (source) {
                            is StreamSourceItem.Error -> collector.emitError("wc: ${source.message}")
                            is StreamSourceItem.DataStream -> {
                                var count = 0
                                source.lines.collect { count++ }
                                totalLines += count
                                if (isSingleStdin) {
                                    collector.emitText(count.toString())
                                } else {
                                    collector.emitText(String.format(Locale.getDefault(), "%7d %s", count, source.sourceName))
                                }
                            }
                        }
                    }
                    if (hasMultipleSources) {
                        collector.emitText(String.format(Locale.getDefault(), "%7d total", totalLines))
                    }
                }

                // 专有路径：仅统计字符数
                charsOnly && !linesOnly && !wordsOnly -> {
                    var totalChars = 0
                    for (source in sources) {
                        when (source) {
                            is StreamSourceItem.Error -> collector.emitError("wc: ${source.message}")
                            is StreamSourceItem.DataStream -> {
                                var count = 0
                                source.lines.collect { line -> count += line.length + 1 }
                                totalChars += count
                                if (isSingleStdin) {
                                    collector.emitText(count.toString())
                                } else {
                                    collector.emitText(String.format(Locale.getDefault(), "%7d %s", count, source.sourceName))
                                }
                            }
                        }
                    }
                    if (hasMultipleSources) {
                        collector.emitText(String.format(Locale.getDefault(), "%7d total", totalChars))
                    }
                }

                // 专有路径：仅统计单词数
                wordsOnly && !linesOnly && !charsOnly -> {
                    var totalWords = 0
                    for (source in sources) {
                        when (source) {
                            is StreamSourceItem.Error -> collector.emitError("wc: ${source.message}")
                            is StreamSourceItem.DataStream -> {
                                var count = 0
                                source.lines.collect { line ->
                                    count += line.split(whitespaceRegex).filter { it.isNotEmpty() }.size
                                }
                                totalWords += count
                                if (isSingleStdin) {
                                    collector.emitText(count.toString())
                                } else {
                                    collector.emitText(String.format(Locale.getDefault(), "%7d %s", count, source.sourceName))
                                }
                            }
                        }
                    }
                    if (hasMultipleSources) {
                        collector.emitText(String.format(Locale.getDefault(), "%7d total", totalWords))
                    }
                }

                // 全量或多维度路径
                else -> {
                    var totalLines = 0
                    var totalWords = 0
                    var totalChars = 0

                    if (isSingleStdin) {
                        val source = sources[0] as StreamSourceItem.DataStream
                        var lineCount = 0
                        var wordCount = 0
                        var charCount = 0
                        source.lines.collect { line ->
                            lineCount++
                            charCount += line.length + 1
                            wordCount += line.split(whitespaceRegex).filter { it.isNotEmpty() }.size
                        }
                        collector.emitText("   Lines    Words    Chars")
                        collector.emitText(
                            String.format(
                                Locale.getDefault(),
                                "%8d %8d %8d",
                                lineCount,
                                wordCount,
                                charCount
                            )
                        )
                    } else {
                        val tableBuilder = TableFormatter.Builder()
                            .addColumn("Lines", TableAlignment.RIGHT, minWidth = 8)
                            .addColumn("Words", TableAlignment.RIGHT, minWidth = 8)
                            .addColumn("Chars", TableAlignment.RIGHT, minWidth = 8)
                            .addColumn("File", TableAlignment.LEFT, minWidth = 4)

                        for (source in sources) {
                            when (source) {
                                is StreamSourceItem.Error -> collector.emitError("wc: ${source.message}")
                                is StreamSourceItem.DataStream -> {
                                    var lineCount = 0
                                    var wordCount = 0
                                    var charCount = 0
                                    source.lines.collect { line ->
                                        lineCount++
                                        charCount += line.length + 1
                                        wordCount += line.split(whitespaceRegex).filter { it.isNotEmpty() }.size
                                    }
                                    totalLines += lineCount
                                    totalWords += wordCount
                                    totalChars += charCount

                                    tableBuilder.addRow(
                                        lineCount.toString(),
                                        wordCount.toString(),
                                        charCount.toString(),
                                        source.sourceName
                                    )
                                }
                            }
                        }

                        if (hasMultipleSources) {
                            tableBuilder.addRow(
                                totalLines.toString(),
                                totalWords.toString(),
                                totalChars.toString(),
                                "total"
                            )
                        }

                        val lines = tableBuilder.build()
                        for (line in lines) {
                            collector.emitText(line)
                        }
                    }
                }
            }
        }

        return Result.success(plan)
    }
}
