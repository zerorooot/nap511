package github.zerorooot.nap511.terminal.engine.archetype

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * 多动作调度命令原型抽象基类 (Action Dispatch Archetype)
 *
 * 适用于：trash (查看/还原/清空), history (查看/截取/清空) 等具备多个排他性独立动作分发的命令。
 *
 * 核心特性与架构收益：
 * 1. 彻底消灭交叉 if-else：将命令行的 Flag 判定在编译期映射为 Kotlin 强类型密封类（Sealed Action）；
 * 2. 穷举分发与安全保障：在 dispatch 中通过 when(action) 穷举各个动作，编译器静态保证无遗漏。
 */
abstract class ActionDispatchCommand<TAction : Any> : TerminalCommand {

    /**
     * 编译期动作决议：将 AST 映射为具体的密封类动作实例
     */
    protected abstract fun compileAction(ast: CommandInvocationAst): Result<TAction>

    /**
     * 动作分发执行体
     */
    protected abstract suspend fun dispatch(
        ctx: TerminalContext,
        action: TAction,
        collector: FlowCollector<TerminalOutput>
    )

    final override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val action = compileAction(ast).getOrElse {
            emitError("${name}: ${it.message ?: "invalid arguments"}")
            return@flow
        }
        dispatch(ctx, action, this)
    }
}
