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
 * 适用于：trash (查看/还原/清空), history (查看/截取/清空), unzip (预览清单/提交后台解压) 等具备多个排他性独立动作分发的命令。
 *
 * 核心特性与架构收益：
 * 1. 彻底消灭交叉 if-else：将命令行的 Flag 判定在编译期映射为 Kotlin 强类型密封类（Sealed Action）；
 * 2. 穷举分发与安全保障：在 dispatch 中通过 when(action) 穷举各个动作，编译器静态保证无遗漏；
 * 3. 灵活管道协同：默认分发支持纯输出，亦支持子类按需覆盖处理 stdin 输入流。
 */
abstract class ActionDispatchCommand<TAction : Any> : TerminalCommand {

    /**
     * 编译期动作决议：将 AST 映射为具体的密封类动作实例
     */
    protected abstract fun compileAction(ast: CommandInvocationAst): Result<TAction>

    /**
     * 基础动作分发执行体
     */
    protected open suspend fun dispatch(
        ctx: TerminalContext,
        action: TAction,
        collector: FlowCollector<TerminalOutput>
    ) {}

    /**
     * 支持 stdin 输入流的动作分发执行体（默认向下委托至无 stdin 版本）
     */
    protected open suspend fun dispatch(
        ctx: TerminalContext,
        action: TAction,
        stdin: Flow<String>,
        collector: FlowCollector<TerminalOutput>
    ) {
        dispatch(ctx, action, collector)
    }

    final override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val action = compileAction(ast).getOrElse {
            emitError("${name}: ${it.message ?: "invalid arguments"}")
            return@flow
        }
        dispatch(ctx, action, stdin, this)
    }
}
