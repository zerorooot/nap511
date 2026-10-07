package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.engine.completion.CommandCompleter
import github.zerorooot.nap511.terminal.engine.completion.StandardCompleter
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import kotlinx.coroutines.flow.Flow

/**
 * 命令行参数选项元数据
 *
 * @property name 选项参数全名或原型（如 "-l", "-n <NUM>"）
 * @property description 选项的用途简明描述
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
 * 终端命令统一规范接口（Terminal Command Interface）
 *
 * 遵循微内核架构规范，作为引擎层调度与具体业务命令实现之间的标准契约。
 * 作为终端命令系统的一等公民，声明式定义元数据（名称、描述、用法、参数选项、别名）及异步流式执行体。
 * 纯粹由强类型 [CommandInvocationAst] 抽象语法树驱动，杜绝过程式裸字符串解析。
 */
interface TerminalCommand {

    /** 命令主名称（例如："ls", "grep"） */
    val name: String

    /** 命令简明功能描述 */
    val description: String

    /** 命令行使用格式范例（例如："grep [-i] [-v] <pattern>"） */
    val usage: String? get() = null

    /** 该命令支持的所有选项与标志列表 */
    val flags: List<CommandFlag> get() = emptyList()

    /** 命令支持的别名列表（例如："help" 命令的别名包含 "?" 与 "？"） */
    val aliases: List<String> get() = emptyList()

    /** 该命令支持带参数值的选项集合（如 setOf("-n", "-I")） */
    val valueOptions: Set<String> get() = emptySet()

    /**
     * 标识当前命令是否为高阶包装命令（如 xargs, time, nohup）
     *
     * 包装命令的语法规则遵循 POSIX Guideline 13：
     * 选项解析器在识别完自身前置选项后，遇到的首个非选项操作数即被视为目标子命令名，
     * 目标命令之后的所有 Token 将直接封包为 [CommandInvocationAst.subcommand]，不再参与外层命令的 Flag/Option 提取。
     */
    val isWrapperCommand: Boolean get() = false

    /**
     * 该命令专属的参数补全器策略（默认使用全量路径补全）
     * 遵循微内核扩展规范，允许命令根据自身业务语义自由定制文件筛选或动态数据源。
     */
    val completer: CommandCompleter
        get() = StandardCompleter.ALL

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

    /**
     * 自动生成当前命令的详细帮助说明（用于 -h / --help 拦截输出）
     */
    fun buildHelpMessage(): String {
        val sb = StringBuilder()
        sb.appendLine("命令名称: $name")
        sb.appendLine("命令描述: $description")
        if (!usage.isNullOrBlank()) {
            sb.appendLine("使用格式: $usage")
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
