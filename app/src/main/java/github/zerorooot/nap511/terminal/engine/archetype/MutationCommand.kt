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
 * 变更操作确认策略
 *
 * 用于 rm、find -delete 等高危状态变更命令的前置安全策略控制
 */
enum class ConfirmPolicy {
    /** 交互式询问确认（默认） */
    INTERACTIVE,
    /** 强制执行，免确认（如指定了 -f, -y, -rf 等） */
    FORCE
}

/**
 * 状态变更命令原型抽象基类 (Mutation Command Archetype)
 *
 * 适用于：rm, mkdir, mv 等对网盘文件系统状态进行写操作、创建、重命名、移动或删除的命令。
 *
 * 核心特性与架构收益：
 * 1. 编译期语法与操作数校验：在执行前统一校验操作数有效性（如缺少操作数 `missing operand`）；
 * 2. 统一确认策略抽象：标准化 `ConfirmPolicy.FORCE` 与 `INTERACTIVE`，消除散落的布尔判定；
 * 3. 错误流式发射隔离：标准统一的异常防御体系，保护协程管道平稳运行。
 */
abstract class MutationCommand<TPlan : Any> : TerminalCommand {

    /**
     * 编译期操作计划构建
     *
     * @param ast 经过词法切分与 POSIX 语法树构建的强类型调用节点
     * @param stdin 管道标准输入流（支持从上游管道提取操作数，如 `find ... | mv <dest>`）
     * @return 编译成功返回包含强类型计划的 [Result.success]，参数缺失或不合法返回包含 POSIX 标准错误信息的 [Result.failure]
     */
    protected abstract suspend fun compilePlan(
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Result<TPlan>

    /**
     * 执行具体变更计划
     *
     * @param ctx 终端执行会话上下文
     * @param plan 已编译好的强类型状态变更计划
     * @param collector 终端输出收集器
     */
    protected abstract suspend fun executePlan(
        ctx: TerminalContext,
        plan: TPlan,
        collector: FlowCollector<TerminalOutput>
    )

    final override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val plan = compilePlan(ast, stdin).getOrElse {
            emitError("${name}: ${it.message ?: "invalid arguments"}")
            return@flow
        }
        executePlan(ctx, plan, this)
    }
}
