package github.zerorooot.nap511.terminal.commands.system

import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 命令历史记录查询与管理命令（history）
 *
 * 支持流式输出持久化历史记录、截取最近 N 条记录，以及使用 -c 参数清空持久化历史。
 *
 * @param historyManager 终端历史记录管理器
 * @param onClearMemoryHistory 清空内存会话历史的回调闭包
 */
class HistoryCommand(
    private val historyManager: TerminalHistoryManager,
    private val onClearMemoryHistory: () -> Unit = {}
) : TerminalCommand {

    override val name: String = "history"

    override val description: String = "查看命令历史记录"

    override val usage: String = "history [-c | <N>]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-c", "清空持久化历史记录")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val cmdArgs = CommandArgs(args)
        // 1. 若携带 -c 参数，执行历史记录清空
        if (cmdArgs.hasFlag("-c")) {
            historyManager.clearHistory()
            onClearMemoryHistory()
            emitText("terminal: history cleared")
            return@flow
        }

        // 2. 检查是否指定了数量截取 <N>，例如: history 20
        val limitArg = cmdArgs.positionalArgs.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
        val limit = if (limitArg != null && limitArg > 0) limitArg else Int.MAX_VALUE

        // 3. 流式发射历史命令记录
        historyManager.streamHistory(limit).collect { line ->
            emitText(line)
        }
    }
}
