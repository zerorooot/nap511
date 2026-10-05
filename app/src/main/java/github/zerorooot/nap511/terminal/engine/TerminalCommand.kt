package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.flow.Flow

/**
 * 终端命令统一规范接口（Terminal Command Interface）
 *
 * 遵循微内核架构规范，作为引擎层调度与具体业务命令实现之间的标准契约。
 * 声明式定义命令元数据（名称、描述、用法、参数选项、别名）及异步流式执行体。
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
     * 核心执行函数
     *
     * @param ctx 终端执行会话上下文（包含工作目录、云端接口仓库、本地缓存及交互确认等）
     * @param args 分词后的命令行参数列表（包含所有选项和位置参数）
     * @param stdin 来自管道上游的标准输入数据流
     * @return 产生终端文本输出的冷流 (Flow<String>)
     */
    suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String>
}
