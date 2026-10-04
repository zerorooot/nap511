package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 命令行参数选项元数据
 */
data class CommandFlag(
    val name: String,
    val description: String
)

/**
 * 命令定义体
 */
class CommandDefinition(
    val name: String,
    val description: String,
    val flags: List<CommandFlag>,
    val usageExample: String?,
    private val executor: suspend (ctx: TerminalContext, args: List<String>, stdin: Flow<String>) -> Flow<String>
) {
    suspend fun execute(ctx: TerminalContext, args: List<String>, stdin: Flow<String>): Flow<String> {
        // 自动拦截 -h 与 --help 参数，输出该命令的参数说明与用法
        if (args.contains("-h") || args.contains("--help")) {
            return flow {
                emit(buildHelpMessage())
            }
        }
        return executor(ctx, args, stdin)
    }

    fun buildHelpMessage(): String {
        val sb = StringBuilder()
        sb.appendLine("命令名称: $name")
        sb.appendLine("命令描述: $description")
        if (!usageExample.isNullOrBlank()) {
            sb.appendLine("使用格式: $usageExample")
        }
        if (flags.isNotEmpty()) {
            sb.appendLine("可用选项:")
            flags.forEach { flag ->
                sb.appendLine("  ${flag.name.padEnd(12)} ${flag.description}")
            }
        }
        return sb.toString().trimEnd()
    }
}

/**
 * 函数式 DSL 命令构建器
 */
class CommandBuilder(val name: String) {
    var description: String = ""
    var usage: String? = null
    private val flags = mutableListOf<CommandFlag>()
    private var executor: (suspend (ctx: TerminalContext, args: List<String>, stdin: Flow<String>) -> Flow<String>)? = null

    /**
     * 声明命令支持的 Flag 参数选项
     */
    fun flag(name: String, description: String) {
        flags.add(CommandFlag(name, description))
    }

    /**
     * 声明命令的执行体
     */
    fun execute(block: suspend (ctx: TerminalContext, args: List<String>, stdin: Flow<String>) -> Flow<String>) {
        this.executor = block
    }

    fun build(): CommandDefinition {
        val exec = executor ?: { _, _, _ -> flow { emit("命令 $name 未定义实现") } }
        return CommandDefinition(
            name = name,
            description = description,
            flags = flags,
            usageExample = usage,
            executor = exec
        )
    }
}

/**
 * 命令注册中心
 */
class CommandRegistry {
    val commands: Map<String, CommandDefinition>
        field = mutableMapOf<String, CommandDefinition>()

    /**
     * 函数式 DSL 注册单个命令
     */
    fun register(name: String, init: CommandBuilder.() -> Unit): CommandDefinition {
        val builder = CommandBuilder(name)
        builder.init()
        val def = builder.build()
        commands[name] = def
        return def
    }

    fun get(name: String): CommandDefinition? = commands[name]

    fun getAll(): List<CommandDefinition> = commands.values.toList().sortedBy { it.name }

    /**
     * 汇总生成所有可用命令列表（用于 '?' 与 'help' 命令输出）
     */
    fun buildAllHelpMessage(): String {
        val sb = StringBuilder()
        sb.appendLine("可用命令列表（在命令后添加 -h 可查看详细参数）：")
        sb.appendLine("--------------------------------------------------")
        getAll().forEach { cmd ->
            sb.appendLine("${cmd.name.padEnd(14)} : ${cmd.description}")
        }
        sb.append("--------------------------------------------------")
        return sb.toString()
    }
}
