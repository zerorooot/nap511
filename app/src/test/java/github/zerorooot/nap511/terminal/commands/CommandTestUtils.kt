package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.flow.toList

/**
 * 单元测试辅助扩展函数：将命令管道执行的 Flow<TerminalOutput> 转换为纯文本列表 List<String>，便于断言比较
 */
internal suspend fun PipelineEngine.executeStrings(cmd: String, ctx: TerminalContext): List<String> =
    execute(cmd, ctx).toList().map { it.text }
