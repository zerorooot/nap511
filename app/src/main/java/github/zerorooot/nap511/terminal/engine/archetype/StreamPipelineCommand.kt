package github.zerorooot.nap511.terminal.engine.archetype

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.StreamSourceItem
import github.zerorooot.nap511.terminal.engine.StreamSourceResolver
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
 * 接收统一归一化后的数据源列表（包含 stdin 或多个文件流），在执行期无分支流转
 */
fun interface StreamPlan {
    suspend fun process(sources: List<StreamSourceItem>, collector: FlowCollector<TerminalOutput>)
}

/**
 * 流式管道命令原型抽象基类 (Stream Pipeline Archetype)
 *
 * 适用于：grep, sort, wc, head, tail 等标准 Unix 数据流过滤、转换与聚合工具。
 *
 * 核心特性与架构收益：
 * 1. 编译与执行两阶段解耦：子类在编译期一次性解析 AST 并组装为不可变 [StreamPlan]；
 * 2. 混合源统一注入：基类在 execute 阶段通过 [StreamSourceResolver] 统一解析文件操作数与 stdin，并隔离单个文件错误；
 * 3. 灵活操作数提取：子类可通过覆盖 [extractFileOperands] 明确哪些参数属于待读取的文件操作数；
 * 4. 彻底终结热循环分支：命令执行期数据直接流过合成闭包。
 */
abstract class StreamPipelineCommand : TerminalCommand {

    /**
     * 声明当前命令中作为待读取文件操作数的位置参数列表
     * 默认将 AST 中的全部位置参数作为文件路径；子类可按需覆盖（如 grep 排除首个模式参数）
     */
    protected open fun extractFileOperands(ast: CommandInvocationAst): List<String> =
        ast.rawPositionalValues

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
        val fileOperands = extractFileOperands(ast)
        val sources = StreamSourceResolver.resolveSources(ctx, fileOperands, stdin)
        plan.process(sources, this)
    }
}
