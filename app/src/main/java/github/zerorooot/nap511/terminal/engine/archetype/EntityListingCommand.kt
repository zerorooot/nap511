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
 * @property targetPath 目标查询路径或位置参数
 * @property forceRefresh 是否强制刷新数据源缓存
 * @property filter 实体过滤闭包（在编译期由 Flag 决议合成）
 * @property comparator 实体排序比较器（在编译期由排序选项合成）
 * @property renderer 最终终端输出发射器（在编译期由展示样式选项绑定）
 */
data class ListingPlan<T>(
    val targetPath: String?,
    val forceRefresh: Boolean = false,
    val filter: (T) -> Boolean = { true },
    val comparator: Comparator<T> = Comparator { _, _ -> 0 },
    val renderer: suspend FlowCollector<TerminalOutput>.(List<T>) -> Unit
)

/**
 * 实体列表展现命令原型抽象基类 (Entity Listing Archetype)
 *
 * 适用于：ls, trash -l 等目标对象列举、多维排序与格式化呈现命令。
 *
 * 核心特性与架构收益：
 * 1. 过滤与排序流水线自动化：基类统一执行 filter -> sortedWith -> renderer 标准函数式数据流；
 * 2. 彻底消除散落的 hasFlag：展现逻辑完全由 ListingPlan 静态装配；
 * 3. 错误拦截统一：统一处理找不到目录或网络异常，防止各命令重复手写 try-catch。
 */
abstract class EntityListingCommand<T> : TerminalCommand {

    /**
     * 编译期语法分析：将 AST 编译为静态展现计划
     */
    protected abstract fun compilePlan(ast: CommandInvocationAst): Result<ListingPlan<T>>

    /**
     * 根据上下文和目标路径拉取原始候选实体集合
     */
    protected abstract suspend fun fetchEntities(
        ctx: TerminalContext,
        targetPath: String?,
        forceRefresh: Boolean
    ): Result<List<T>>

    final override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val plan = compilePlan(ast).getOrElse {
            emitError("${name}: ${it.message ?: "invalid arguments"}")
            return@flow
        }

        val entities = fetchEntities(ctx, plan.targetPath, plan.forceRefresh).getOrElse {
            emitError(it.message ?: "${name}: failed to fetch entities")
            return@flow
        }

        val processed = entities.filter(plan.filter).sortedWith(plan.comparator)
        plan.renderer(this, processed)
    }
}
