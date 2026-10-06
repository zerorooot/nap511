package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 命令行参数选项元数据
 */
data class CommandFlag(
    val name: String,
    val description: String
) {
    /**
     * 实际参数选项名（如从 "-n <NUM>" 中提取出的 "-n"）
     */
    val optionName: String
        get() = name.trim().substringBefore(' ')
}

/**
 * 命令定义体
 */
class CommandDefinition(
    val name: String,
    val description: String,
    val flags: List<CommandFlag>,
    val usageExample: String?,
    private val executor: suspend (ctx: TerminalContext, args: List<String>, stdin: Flow<String>) -> Flow<TerminalOutput>
) {
    suspend fun execute(ctx: TerminalContext, args: List<String>, stdin: Flow<String>): Flow<TerminalOutput> {
        // 当使用 --help 或 (当命令本身未定义 -h 选项且参数包含 -h) 时自动拦截输出参数说明与用法（需遵守 "--" 选项结束符规范）
        val delimiterIndex = args.indexOf("--")
        val optionTokens = if (delimiterIndex >= 0) args.subList(0, delimiterIndex) else args
        val hasHOption = flags.any { it.optionName == "-h" }
        if (optionTokens.contains("--help") || (!hasHOption && optionTokens.contains("-h"))) {
            return flow {
                // 源头直接发射带有 TerminalLineType.System.HELP 语义类型的帮助文档行
                for (subLine in buildHelpMessage().split('\n')) {
                    emit(TerminalOutput(subLine, TerminalLineType.System.HELP))
                }
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
    private var executor: (suspend (ctx: TerminalContext, args: List<String>, stdin: Flow<String>) -> Flow<TerminalOutput>)? = null

    /**
     * 声明命令支持的 Flag 参数选项
     */
    fun flag(name: String, description: String) {
        flags.add(CommandFlag(name, description))
    }

    /**
     * 声明命令的执行体
     */
    fun execute(block: suspend (ctx: TerminalContext, args: List<String>, stdin: Flow<String>) -> Flow<TerminalOutput>) {
        this.executor = block
    }

    fun build(): CommandDefinition {
        val exec = executor ?: { _, _, _ -> flow { emit(TerminalOutput("命令 $name 未定义实现", TerminalLineType.System.ERROR)) } }
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

    /**
     * 原生注册面向对象的 TerminalCommand 命令对象（及其所有别名）
     *
     * 将命令的声明式元数据（名称、描述、用法、Flags）和执行体挂载到注册中心，
     * 自动支持帮助拦截与 Tab 补全提示。
     *
     * @param command 遵循统一规范的终端命令实例
     * @return 注册生成的 CommandDefinition 列表（主命令 + 别名）
     */
    fun register(command: TerminalCommand): List<CommandDefinition> {
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

    fun get(name: String): CommandDefinition? = commands[name]

    fun hasCommand(name: String): Boolean = commands.containsKey(name)

    fun getAll(): List<CommandDefinition> = commands.values.toList().sortedBy { it.name }

    /**
     * 汇总生成所有可用命令列表与快捷键指南（用于 '?' 与 'help' 命令输出）
     */
    fun buildAllHelpMessage(): String {
        val sb = StringBuilder()
        sb.appendLine("可用命令列表（在命令后添加 -h 可查看详细参数）：")
        sb.appendLine("--------------------------------------------------")
        getAll().forEach { cmd ->
            sb.appendLine("${cmd.name.padEnd(14)} : ${cmd.description}")
        }
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("快捷键指南（悬浮栏点亮 CTRL / ALT 或连接物理键盘）：")
        sb.appendLine("  Ctrl + C        中断运行中的任务 / 放弃当前输入另起新行")
        sb.appendLine("  Ctrl + U        清除光标至行首")
        sb.appendLine("  Ctrl + K        清除光标至行尾")
        sb.appendLine("  Ctrl + W        向前删除一个单词")
        sb.appendLine("  Ctrl + L        清空屏幕输出 (等同 clear)")
        sb.appendLine("  Ctrl + A / E    光标跳到行首 / 行尾")
        sb.appendLine("  Ctrl + D        输入为空时退出终端 / 向后删除字符")
        sb.appendLine("  Alt + B / F     光标按单词向左 / 向右跳跃")
        sb.appendLine("  Alt + D         向后删除一个单词")
        sb.appendLine("  Alt + Backspace 向前删除一个单词 (同 Ctrl+W)")
        sb.appendLine("  Alt + .         召回并插入上一条命令的最后一个参数")
        sb.append("--------------------------------------------------")
        return sb.toString()
    }
}
