package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 文本过滤匹配命令（grep）
 *
 * 从管道上游标准输入逐行读取文本，支持模式匹配、大小写忽略（-i）、反向过滤（-v）以及行数统计（-c）。
 */
class GrepCommand : TerminalCommand {

    override val name: String = "grep"

    override val description: String = "文本匹配与正则过滤"

    override val usage: String = "grep [-i] [-v] [-c] <pattern>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-i", "忽略大小写"),
        CommandFlag("-v", "反向匹配，输出不匹配的行"),
        CommandFlag("-c", "仅输出匹配行的总数")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val cmdArgs = CommandArgs(args)
        val ignoreCase = cmdArgs.hasFlag("-i")
        val invertMatch = cmdArgs.hasFlag("-v")
        val countOnly = cmdArgs.hasFlag("-c")

        // 提取模式匹配字符串（第一个非选项参数）
        val pattern = cmdArgs.firstPositional ?: ""

        // 构建正则表达式选项（如忽略大小写）
        val regexOptions = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()

        // 尝试将 POSIX BRE 模式转换为 Kotlin 标准正则表达式并编译；若语法非法则降级为 null
        val regex = runCatching {
            Regex(convertBreToRegexPattern(pattern), regexOptions)
        }.getOrNull()

        var matchCount = 0
        stdin.collect { line ->
            // 优先使用编译成功的正则表达式进行模式匹配；若正则编译失败，则降级使用普通字符串包含判断
            val matched = regex?.containsMatchIn(line)
                ?: if (ignoreCase) {
                    line.contains(pattern, ignoreCase = true)
                } else {
                    line.contains(pattern)
                }

            // 支持 -v 反向匹配逻辑：匹配成功且非反向，或匹配失败且反向
            val isSuccess = if (invertMatch) !matched else matched
            if (isSuccess) {
                matchCount++
                if (!countOnly) {
                    emitText(line)
                }
            }
        }

        // 若开启 -c 选项，仅输出匹配总行数
        if (countOnly) {
            emitText(matchCount.toString())
        }
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
