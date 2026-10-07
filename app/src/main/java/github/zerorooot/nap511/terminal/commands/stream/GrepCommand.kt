package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.archetype.StreamPipelineCommand
import github.zerorooot.nap511.terminal.engine.archetype.StreamPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.emitText

/**
 * 文本过滤匹配命令（grep）
 *
 * 继承 [StreamPipelineCommand]，在编译期将模式匹配与开关选项静态装配为不可变 [StreamPlan]。
 * 消除数据处理热循环中的分支判断（无 hasFlag、无 if(countOnly)、无 if(invertMatch)），实现高性能流式过滤。
 */
class GrepCommand : StreamPipelineCommand() {

    override val name: String = "grep"

    override val description: String = "文本匹配与正则过滤"

    override val usage: String = "grep [-i] [-v] [-c] <pattern>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-i", "忽略大小写"),
        CommandFlag("-v", "反向匹配，输出不匹配的行"),
        CommandFlag("-c", "仅输出匹配行的总数")
    )

    override val completer: github.zerorooot.nap511.terminal.engine.completion.CommandCompleter =
        object : github.zerorooot.nap511.terminal.engine.completion.CommandCompleter {
            override fun getPathFilter(argIndex: Int): github.zerorooot.nap511.terminal.engine.completion.PathFilter {
                return if (argIndex == 0) {
                    github.zerorooot.nap511.terminal.engine.completion.PathFilter { _, _ -> false }
                } else {
                    github.zerorooot.nap511.terminal.engine.completion.FileFilters.TEXT_FILES
                }
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

        // 编译期分离计划实现，彻底避免热循环内 if (countOnly) 分支判断
        val plan = if (countOnly) {
            StreamPlan { stdin, collector ->
                var matchCount = 0
                stdin.collect { line ->
                    if (matchPredicate(line)) {
                        matchCount++
                    }
                }
                collector.emitText(matchCount.toString())
            }
        } else {
            StreamPlan { stdin, collector ->
                stdin.collect { line ->
                    if (matchPredicate(line)) {
                        collector.emitText(line)
                    }
                }
            }
        }

        return Result.success(plan)
    }

    /**
     * 将 POSIX BRE（基本正则表达式）规则转换为 Kotlin/Java 标准正则表达式（ERE）规则
     *
     * 规则说明：
     * 1. 在 POSIX BRE 中，`\+`, `\?`, `\|`, `\(`, `\)`, `\{`, `\}` 代表正则元字符（加号、问号、分支、分组、限定符），
     *    而在 Java/Kotlin Regex 中这些元字符不需要前缀反斜杠，因此转换时去掉反斜杠。
     * 2. 其他带反斜杠的转义（如 `\.`, `\*`, `\\`, `\$`, `\[`）保留反斜杠，供 Kotlin Regex 精准匹配字面量字符。
     * 3. 在 POSIX BRE 中未转义的 `+`, `?`, `|`, `(`, `)` 是普通字面量字符，但在 Kotlin Regex 中是元字符，
     *    因此转换时自动加上反斜杠 `\+`, `\?`, `\|`, `\(`, `\)` 进行转义。
     */
    private fun convertBreToRegexPattern(bre: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = bre.length
        while (i < len) {
            when (val c = bre[i]) {
                '\\' if i + 1 < len -> {
                    when (val next = bre[i + 1]) {
                        // BRE 中的 \+, \?, \|, \(, \), \{, \} 转换为标准正则元字符 +, ?, |, (, ), {, }
                        '+', '?', '|', '(', ')', '{', '}' -> {
                            sb.append(next)
                            i += 2
                        }
                        // 其他反斜杠转义（如 \., \*, \\, \$ 等）保留原样，作为 Kotlin Regex 的字面量转义
                        else -> {
                            sb.append('\\').append(next)
                            i += 2
                        }
                    }
                }
                '+', '?', '|', '(', ')' -> {
                    // BRE 中未经转义的 +, ?, |, (, ) 是普通字面量字符，在 Kotlin Regex 中需要加反斜杠转义
                    sb.append('\\').append(c)
                    i++
                }
                else -> {
                    // 其他普通字符（如字母、数字、点号 .、星号 * 等保持原样）
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }
}
