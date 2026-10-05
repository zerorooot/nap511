package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandDefinition
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import kotlinx.coroutines.flow.Flow

/**
 * 终端命令统一接口规范
 *
 * 为每个具体命令提供高内聚的面向对象封装，支持声明式元数据（名称、描述、用法、参数选项、别名）
 * 以及基于协程 Flow 的异步流式命令执行体。
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

/**
 * 将 TerminalCommand 注册挂载至 CommandRegistry 命令中心
 *
 * 自动处理主命令名称及所有别名的注册，并复用已有的 DSL 拦截机制（如 -h 与 --help 帮助拦截）。
 */
fun CommandRegistry.register(command: TerminalCommand): List<CommandDefinition> {
    val registeredDefinitions = mutableListOf<CommandDefinition>()
    val allNames = listOf(command.name) + command.aliases

    for (cmdName in allNames) {
        val def = register(cmdName) {
            description = command.description
            usage = command.usage
            command.flags.forEach { flag(it.name, it.description) }
            execute { ctx, args, stdin ->
                command.execute(ctx, args, stdin)
            }
        }
        registeredDefinitions.add(def)
    }

    return registeredDefinitions
}
