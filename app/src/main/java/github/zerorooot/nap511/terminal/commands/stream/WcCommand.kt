package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.archetype.StreamPipelineCommand
import github.zerorooot.nap511.terminal.engine.archetype.StreamPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.emitText
import java.util.Locale

/**
 * 文本字数统计命令（wc）
 *
 * 继承 [StreamPipelineCommand]，在编译期根据选项直接生成专用收集计划：
 * - 纯行数统计（-l）：跳过开销昂贵的正则表达式单词切分与字符统计，极致提升流式处理性能；
 * - 纯单词数统计（-w）：只进行单词正则切分；
 * - 纯字符数统计（-c）：只累加字符长度；
 * - 复合或全量统计：汇总输出完整的三列指标。
 */
class WcCommand : StreamPipelineCommand() {

    override val name: String = "wc"

    override val description: String = "统计行数、单词数及字符数"

    override val usage: String = "wc [-l] [-w] [-c]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "仅统计行数"),
        CommandFlag("-w", "仅统计单词数"),
        CommandFlag("-c", "仅统计字符/字节数")
    )

    override val completer: github.zerorooot.nap511.terminal.engine.completion.CommandCompleter =
        github.zerorooot.nap511.terminal.engine.completion.StandardCompleters.TEXT_FILES

    override fun compilePlan(ast: CommandInvocationAst): Result<StreamPlan> {
        val linesOnly = ast.hasFlag("-l")
        val wordsOnly = ast.hasFlag("-w")
        val charsOnly = ast.hasFlag("-c")

        val plan = when {
            // 专有高效路径：仅统计行数，跳过正则与字符串长度计算
            linesOnly && !wordsOnly && !charsOnly -> StreamPlan { stdin, collector ->
                var lineCount = 0
                stdin.collect { lineCount++ }
                collector.emitText(lineCount.toString())
            }

            // 专有路径：仅统计字符数
            charsOnly && !linesOnly && !wordsOnly -> StreamPlan { stdin, collector ->
                var charCount = 0
                stdin.collect { line ->
                    charCount += line.length + 1
                }
                collector.emitText(charCount.toString())
            }

            // 专有路径：仅统计单词数
            wordsOnly && !linesOnly && !charsOnly -> StreamPlan { stdin, collector ->
                val whitespaceRegex = Regex("\\s+")
                var wordCount = 0
                stdin.collect { line ->
                    wordCount += line.split(whitespaceRegex).filter { it.isNotEmpty() }.size
                }
                collector.emitText(wordCount.toString())
            }

            // 全量或多维度路径
            else -> StreamPlan { stdin, collector ->
                val whitespaceRegex = Regex("\\s+")
                var lineCount = 0
                var wordCount = 0
                var charCount = 0

                stdin.collect { line ->
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
            }
        }

        return Result.success(plan)
    }
}
