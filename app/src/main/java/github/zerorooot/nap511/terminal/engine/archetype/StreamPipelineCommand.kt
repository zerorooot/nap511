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
 * 流式处理命令执行计划接口
 *
 * 封装已在编译期合成好的过滤、排序或聚合逻辑，数据在热循环中无分支流转
 */
fun interface StreamPlan {
    suspend fun process(stdin: Flow<String>, collector: FlowCollector<TerminalOutput>)
}

/**
 * 流式管道命令原型抽象基类 (Stream Pipeline Archetype)
 *
 * 适用于：grep, sort, wc, head, tail 等标准 Unix 数据流过滤与转换工具。
 *
 * 核心特性与架构收益：
 * 1. 编译与执行两阶段解耦：子类仅需实现 [compilePlan]，在编译期一次性解析 AST 并组装为不可变 [StreamPlan]；
 * 2. 异常与参数拦截下沉：基类统一捕获编译期参数缺失并标准报错，消除每个命令各自手写的防御性样板代码；
 * 3. 彻底终结热循环分支：命令执行期数据直接流过合成闭包，循环体内部 0 个 hasFlag，0 个 if 分支。
 */
abstract class StreamPipelineCommand : TerminalCommand {

    /**
     * 编译期语法分析：将输入的 AST 编译为不可变的流式执行计划
     *
     * @param ast 已经由引擎完成解析的语法树节点
     * @return 编译成功返回 [StreamPlan]，失败返回包装了 POSIX 标准错误信息的 [Result.failure]
     */
    protected abstract fun compilePlan(ast: CommandInvocationAst): Result<StreamPlan>

    final override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val plan = compilePlan(ast).getOrElse {
            emitError("${name}: ${it.message ?: "invalid arguments"}")
            return@flow
        }
        plan.process(stdin, this)
    }
}
