package github.zerorooot.nap511.terminal.commands.system

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import github.zerorooot.nap511.terminal.engine.archetype.ActionDispatchCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.FlowCollector

/**
 * 历史记录操作密封类定义
 */
sealed interface HistoryAction {
    /** 清空持久化与会话历史 */
    data object Clear : HistoryAction
    /** 查看或截取最近 N 条历史记录 */
    data class List(val limit: Int) : HistoryAction
}

/**
 * 命令历史记录查询与管理命令（history）
 *
 * 继承 [ActionDispatchCommand]，将参数解析为 [HistoryAction.Clear] 或 [HistoryAction.List]，
 * 在编译期完成数字截取与开关判定，执行期穷举分发。
 *
 * @param historyManager 终端历史记录管理器
 * @param onClearMemoryHistory 清空内存会话历史的回调闭包
 */
class HistoryCommand(
    private val historyManager: TerminalHistoryManager,
    private val onClearMemoryHistory: () -> Unit = {}
) : ActionDispatchCommand<HistoryAction>() {

    override val name: String = "history"

    override val description: String = "查看命令历史记录"

    override val usage: String = "history [-c | <N>]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-c", "清空持久化历史记录")
    )

    override fun compileAction(ast: CommandInvocationAst): Result<HistoryAction> {
        if (ast.hasFlag("-c")) {
            return Result.success(HistoryAction.Clear)
        }
        val limitArg = ast.positionalArgs.firstOrNull { it.text.toIntOrNull() != null }?.text?.toIntOrNull()
        val limit = if (limitArg != null && limitArg > 0) limitArg else Int.MAX_VALUE
        return Result.success(HistoryAction.List(limit))
    }

    override suspend fun dispatch(
        ctx: TerminalContext,
        action: HistoryAction,
        collector: FlowCollector<TerminalOutput>
    ) {
        when (action) {
            is HistoryAction.Clear -> {
                historyManager.clearHistory()
                onClearMemoryHistory()
                collector.emitText("terminal: history cleared")
            }
            is HistoryAction.List -> {
                historyManager.streamHistory(action.limit).collect { line ->
                    collector.emitText(line)
                }
            }
        }
    }
}
