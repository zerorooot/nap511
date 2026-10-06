package github.zerorooot.nap511.terminal.engine

/**
 * 终端命令注册中心 (CommandRegistry)
 *
 * 作为轻量高效的微内核服务注册容器，负责维护终端系统内所有已注册的 [TerminalCommand] 命令实例。
 *
 * 架构重构优化亮点：
 * 1. 【消灭冗余包装层】：彻底剔除旧版 CommandDefinition 与 CommandBuilder 产生的二次对象装配开销，
 *    使 [TerminalCommand] 成为系统唯一且完备的一等公民。
 * 2. 【统一别名映射】：注册命令时，自动将主命令名称与所有别名（如 "?" -> HelpCommand）建立直接映射，
 *    O(1) 复杂度极速检索。
 * 3. 【高内聚微内核设计】：提供标准注册、查询、遍历与全量帮助指南汇总能力，逻辑简明、边界清晰。
 */
class CommandRegistry {

    /** 内部存储命令名/别名至具体命令实现的哈希路由表 */
    private val commandsMap = mutableMapOf<String, TerminalCommand>()

    /** 对外暴露的只读命令映射视图 */
    val commands: Map<String, TerminalCommand>
        get() = commandsMap

    /**
     * 注册单个终端命令对象
     *
     * 自动将其主名称（[TerminalCommand.name]）以及所有别名（[TerminalCommand.aliases]）
     * 映射挂载到注册中心路由表中。
     *
     * @param command 遵循统一规范的终端命令实例
     * @return 当前注册中心实例，支持链式调用流式配置
     */
    fun register(command: TerminalCommand): CommandRegistry {
        commandsMap[command.name] = command
        for (alias in command.aliases) {
            commandsMap[alias] = command
        }
        return this
    }

    /**
     * 批量注册终端命令集合（变长参数）
     *
     * @param commands 待注册的终端命令实例列表
     * @return 当前注册中心实例，支持链式调用
     */
    fun registerAll(vararg commands: TerminalCommand): CommandRegistry {
        commands.forEach { register(it) }
        return this
    }

    /**
     * 根据命令名称或别名查找对应的命令实例
     *
     * @param name 命令名称或已注册的别名
     * @return 匹配的 [TerminalCommand] 实例，若未注册则返回 null
     */
    fun get(name: String): TerminalCommand? = commandsMap[name]

    /**
     * 检查指定命令名称或别名是否存在
     *
     * @param name 命令名称或别名
     * @return 若已注册返回 true，否则返回 false
     */
    fun hasCommand(name: String): Boolean = commandsMap.containsKey(name)

    /**
     * 获取所有已注册的唯一命令列表（排除别名产生的重复项，并按主命令名升序排列）
     */
    fun getAll(): List<TerminalCommand> = commandsMap.values
        .distinctBy { it.name }
        .sortedBy { it.name }

    /**
     * 汇总生成所有可用命令列表与快捷键操作指南
     *
     * 用于 '?'、'help' 系统内置帮助命令的主屏输出。
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
