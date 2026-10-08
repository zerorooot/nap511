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
 * 实体列表展现执行计划
 *
 * @param T 实体数据类型（如 FileBean, RecycleBean）
 * @property targets 目标查询路径或位置参数列表（若为空则默认为当前工作上下文）
 * @property forceRefresh 是否强制刷新数据源缓存
 * @property filter 实体过滤闭包（在编译期由 Flag 决议合成）
 * @property comparator 实体排序比较器（在编译期由排序选项合成）
 * @property isLong 是否启用长列表格式展示
 */
data class ListingPlan<T>(
    val targets: List<String> = emptyList(),
    val forceRefresh: Boolean = false,
    val filter: (T) -> Boolean = { true },
    val comparator: Comparator<T> = Comparator { _, _ -> 0 },
    val isLong: Boolean = false
)

/**
 * 实体列表展现命令原型抽象基类 (Entity Listing Archetype)
 *
 * 适用于：ls 等目标对象列举、多维排序与格式化呈现命令。
 *
 * 核心特性与架构收益：
 * 1. 编译与执行两阶段解耦：子类在编译期一次性解析 AST 并组装为不可变 [ListingPlan]；
 * 2. 彻底消除散落的 hasFlag：展现逻辑完全由 ListingPlan 静态装配；
 * 3. 错误拦截统一：统一捕获参数非法异常，消除每个命令重复手写 try-catch；
 * 4. 多目标协同：原生支持无参（当前目录）、单目标与多目标（包含通配符展开后多个操作数）的分发调度。
 */
abstract class EntityListingCommand<T> : TerminalCommand {

    /**
     * 编译期语法分析：将 AST 编译为静态不可变展现计划
     */
    protected abstract fun compilePlan(ast: CommandInvocationAst): Result<ListingPlan<T>>

    /**
     * 运行期计划执行：基于已编译好的 [ListingPlan]，结合上下文执行目标分发、拉取与多态渲染
     */
    protected abstract suspend fun executePlan(
        ctx: TerminalContext,
        plan: ListingPlan<T>,
        collector: FlowCollector<TerminalOutput>
    )

    final override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val plan = compilePlan(ast).getOrElse {
            emitError("${name}: ${it.message ?: "invalid arguments"}")
            return@flow
        }
        executePlan(ctx, plan, this)
    }
}
