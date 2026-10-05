package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.commands.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
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
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        val ignoreCase = cmdArgs.hasFlag("-i")
        val invertMatch = cmdArgs.hasFlag("-v")
        val countOnly = cmdArgs.hasFlag("-c")

        // 提取模式匹配字符串（第一个非选项参数）
        val pattern = cmdArgs.firstPositional ?: ""

        var matchCount = 0
        stdin.collect { line ->
            val matched = if (ignoreCase) {
                line.contains(pattern, ignoreCase = true)
            } else {
                line.contains(pattern)
            }

            val isSuccess = if (invertMatch) !matched else matched
            if (isSuccess) {
                matchCount++
                if (!countOnly) {
                    emit(line)
                }
            }
        }

        // 若开启 -c 选项，仅输出匹配总行数
        if (countOnly) {
            emit(matchCount.toString())
        }
    }
}
