package github.zerorooot.nap511.terminal.viewmodel

import kotlinx.coroutines.flow.FlowCollector

/**
 * 终端行语义类型规范 (Terminal Line Type)
 *
 * 采用密封接口结构化分层设计（高内聚、低耦合）：
 * 1. [TerminalLineType.System]：系统控制、生命周期、用户命令、交互确认与错误提示（非数据流，不流入管道 stdin）
 * 2. [TerminalLineType.Output]：细分的数据与内容输出类型（标准数据输出流，管道下游消费对象）
 */
sealed interface TerminalLineType {
    /** 标识是否属于可流入管道的数据流（即标准数据输出，非系统控制/诊断/错误提示） */
    val isPipeableData: Boolean get() = false

    /**
     * 1. 系统与交互控制行（不流入管道 stdin）
     */
    sealed interface System : TerminalLineType {
        override val isPipeableData: Boolean get() = false

        data object INFO : System        // 系统提示、操作回显、欢迎信息（青色单色高亮）
        data object COMMAND : System     // 用户输入的历史命令行（路径+提示符复合高亮）
        data object PROMPT : System      // 交互式输入确认提示（如 rm/find 确认，黄色警告色）

        /** 帮助文档与快捷键指南（对应 Linux stdout 帮助说明，允许作为标准数据流流入管道） */
        data object HELP : System {
            override val isPipeableData: Boolean get() = true
        }

        data object ERROR : System       // 命令或系统报错信息（红色高亮）
    }

    /**
     * 2. 细分的数据与内容输出类型（标准数据输出流，管道下游消费对象）
     */
    sealed interface Output : TerminalLineType {
        override val isPipeableData: Boolean get() = true

        data object TEXT : Output         // 普通标准文本（echo, wc, stat, 普通管道文本）
        data object FILE_ENTRY : Output   // 单个文件/目录条目（ls 模式）
        data object PATH_ENTRY : Output   // 规范路径条目（find 结果，可确定性切分路径）
        data object LONG_LISTING : Output // ls -l 详细列表行
        data object FIND_CATEGORY : Output// find 分类检索输出行（兼容富文本渲染）
        data object ANSI : Output         // 包含标准 ANSI SGR 转义序列文本
    }
}

/**
 * 终端命令流式输出数据载体 (Terminal Output Data Carrier)
 *
 * 遵循强类型流模型规范，用于命令层向引擎及 ViewModel 发射携带语义类型的输出。
 */
data class TerminalOutput(
    val text: String,
    val type: TerminalLineType = TerminalLineType.Output.TEXT
) : CharSequence by text {
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is TerminalOutput) return text == other.text && type == other.type
        if (other is CharSequence) return text == other.toString()
        return false
    }
    override fun hashCode(): Int = text.hashCode()
}

/**
 * FlowCollector 扩展函数：便捷发射不同语义类型的终端输出，简化命令层代码编写并增强可读性
 */
suspend fun FlowCollector<TerminalOutput>.emitText(text: String) =
    emit(TerminalOutput(text, TerminalLineType.Output.TEXT))

suspend fun FlowCollector<TerminalOutput>.emitFile(name: String) =
    emit(TerminalOutput(name, TerminalLineType.Output.FILE_ENTRY))

suspend fun FlowCollector<TerminalOutput>.emitPath(path: String) =
    emit(TerminalOutput(path, TerminalLineType.Output.PATH_ENTRY))

suspend fun FlowCollector<TerminalOutput>.emitLongListing(line: String) =
    emit(TerminalOutput(line, TerminalLineType.Output.LONG_LISTING))

suspend fun FlowCollector<TerminalOutput>.emitFindCategory(line: String) =
    emit(TerminalOutput(line, TerminalLineType.Output.FIND_CATEGORY))

suspend fun FlowCollector<TerminalOutput>.emitAnsi(text: String) =
    emit(TerminalOutput(text, TerminalLineType.Output.ANSI))

suspend fun FlowCollector<TerminalOutput>.emitHelp(text: String) =
    emit(TerminalOutput(text, TerminalLineType.System.HELP))

suspend fun FlowCollector<TerminalOutput>.emitError(msg: String) =
    emit(TerminalOutput(msg, TerminalLineType.System.ERROR))

suspend fun FlowCollector<TerminalOutput>.emitSystem(msg: String) =
    emit(TerminalOutput(msg, TerminalLineType.System.INFO))

suspend fun FlowCollector<TerminalOutput>.emitPrompt(prompt: String) =
    emit(TerminalOutput(prompt, TerminalLineType.System.PROMPT))

/**
 * 终端展示行模型
 */
data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.Output.TEXT,
    val id: Long = System.nanoTime()
)

