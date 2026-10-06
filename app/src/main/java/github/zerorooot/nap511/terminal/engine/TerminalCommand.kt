package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import kotlinx.coroutines.flow.Flow

/**
 * 终端命令统一规范接口（Terminal Command Interface）
 *
 * 遵循微内核架构规范，作为引擎层调度与具体业务命令实现之间的标准契约。
 * 声明式定义命令元数据（名称、描述、用法、参数选项、别名）及异步流式执行体。
 * 纯粹由强类型 [CommandInvocationAst] 抽象语法树驱动，杜绝过程式裸字符串解析。
 */
interface TerminalCommand {

    /**
     * 命令主名称（例如："ls", "grep"）
     */
    val name: String

    /**
     * 命令简明功能描述
     */
    val description: String

    /**
     * 命令行使用格式范例（例如："grep [-i] [-v] <pattern>"）
     */
    val usage: String? get() = null

    /**
     * 该命令支持的所有选项与标志列表
     */
    val flags: List<CommandFlag> get() = emptyList()

    /**
     * 命令支持的别名列表（例如："help" 命令的别名包含 "?" 与 "？"）
     */
    val aliases: List<String> get() = emptyList()

    /**
     * 该命令支持带参数值的选项集合（如 setOf("-n", "-I")）
     * 引擎解析器根据此配置自动切分紧贴参数（如 -n10）与后续值（如 -n 10）
     */
    val valueOptions: Set<String> get() = emptySet()

    /**
     * 核心 AST 执行函数
     *
     * @param ctx 终端执行会话上下文
     * @param ast 已经由引擎完成词法切分与 POSIX 语法树构建的强类型调用节点
     * @param stdin 来自管道上游的标准输入数据流
     * @return 产生终端结构化输出的冷流 (Flow<TerminalOutput>)
     */
    suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput>
}
