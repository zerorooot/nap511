package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.StreamSourceItem
import github.zerorooot.nap511.terminal.engine.archetype.StreamPipelineCommand
import github.zerorooot.nap511.terminal.engine.archetype.StreamPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.engine.completion.CommandCompleter
import github.zerorooot.nap511.terminal.engine.completion.FileFilters
import github.zerorooot.nap511.terminal.engine.completion.PathFilter
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText

/**
 * 文本过滤匹配命令（grep）
 *
 * 继承 [StreamPipelineCommand]，遵循 POSIX.1-2017 规范：
 * 1. 语法：`grep [-i] [-v] [-c] <pattern> [file...]`；
 * 2. 位置参数划分：首个位置参数为模式 pattern，后续位置参数为待匹配文件；若未指定文件则读取标准输入 stdin；
 * 3. 多文件输出前缀：当指定 2 个及以上输入源时，每行匹配项自动附带文件名 `<file>:<content>`（若 -c 则为 `<file>:<count>`）；
 *    单文件或 stdin 输入时不附带文件名前缀。
 */
class GrepCommand : StreamPipelineCommand() {

    override val name: String = "grep"

    override val description: String = "文本匹配与正则过滤"

    override val usage: String = "grep [-i] [-v] [-c] <pattern> [file...]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-i", "忽略大小写"),
        CommandFlag("-v", "反向匹配，输出不匹配的行"),
        CommandFlag("-c", "仅输出匹配行的总数")
    )

    override val completer: CommandCompleter =
        object : CommandCompleter {
            override fun getPathFilter(argIndex: Int): PathFilter {
                return if (argIndex == 0) {
                    PathFilter { _, _ -> false }
                } else {
                    FileFilters.TEXT_FILES
                }
            }
        }

    override fun extractFileOperands(ast: CommandInvocationAst): List<String> {
        return if (ast.positionalArgs.size > 1) {
            ast.rawPositionalValues.drop(1)
        } else {
            emptyList()
        }
    }

    override fun compilePlan(ast: CommandInvocationAst): Result<StreamPlan> {
        val ignoreCase = ast.hasFlag("-i")
        val invertMatch = ast.hasFlag("-v")
        val countOnly = ast.hasFlag("-c")

        // 提取模式匹配字符串（首个位置参数，若无则默认为空串匹配全部）
        val pattern = ast.firstPositional ?: ""

        // 构建正则表达式选项（如忽略大小写）
        val regexOptions = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()

        // 尝试将 POSIX BRE 模式转换为 Kotlin 标准正则表达式并编译；若语法非法则降级为普通字符串包含
        val regex = runCatching {
            Regex(convertBreToRegexPattern(pattern), regexOptions)
        }.getOrNull()

        // 编译期合成单体判定闭包：将正向/反向、正则/普通字符串包含预先组合
        val rawPredicate: (String) -> Boolean = if (regex != null) {
            { line -> regex.containsMatchIn(line) }
        } else {
            { line -> line.contains(pattern, ignoreCase = ignoreCase) }
        }

        val matchPredicate: (String) -> Boolean = if (invertMatch) {
            { line -> !rawPredicate(line) }
        } else {
            rawPredicate
        }

        val plan = StreamPlan { sources, collector ->
            val hasMultipleSources = sources.size > 1

            for (source in sources) {
                when (source) {
                    is StreamSourceItem.Error -> {
                        collector.emitError("grep: ${source.message}")
                    }

                    is StreamSourceItem.DataStream -> {
                        if (countOnly) {
                            var matchCount = 0
                            source.lines.collect { line ->
                                if (matchPredicate(line)) {
                                    matchCount++
                                }
                            }
                            if (hasMultipleSources) {
                                collector.emitText("${source.sourceName}:$matchCount")
                            } else {
                                collector.emitText(matchCount.toString())
                            }
                        } else {
                            source.lines.collect { line ->
                                if (matchPredicate(line)) {
                                    if (hasMultipleSources) {
                                        collector.emitText("${source.sourceName}:$line")
                                    } else {
                                        collector.emitText(line)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return Result.success(plan)
    }

    /**
     * 将 POSIX BRE（基本正则表达式）规则转换为 Kotlin/Java 标准正则表达式（ERE）规则
     */
    private fun convertBreToRegexPattern(bre: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = bre.length
        while (i < len) {
            when (val c = bre[i]) {
                '\\' if i + 1 < len -> {
                    when (val next = bre[i + 1]) {
                        '+', '?', '|', '(', ')', '{', '}' -> {
                            sb.append(next)
                            i += 2
                        }
                        else -> {
                            sb.append('\\').append(next)
                            i += 2
                        }
                    }
                }
                '+', '?', '|', '(', ')' -> {
                    sb.append('\\').append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }
}
