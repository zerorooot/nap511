package github.zerorooot.nap511.terminal.viewmodel

import kotlinx.coroutines.flow.FlowCollector

/**
 * 终端行语义类型规范 (Terminal Line Type)
 *
 * 由命令发射源头显式指定，彻底杜绝下游 UI 依靠正则和字符串启发式匹配猜词。
 */
enum class TerminalLineType {
    // --- 1. 系统与交互行 ---
    SYSTEM,               // 系统提示、欢迎信息（青色高亮，整行单色）
    COMMAND,              // 用户输入的历史命令行（双行复合样式：路径青色 + 提示符绿色 + 命令白色）
    PROMPT,               // 交互式输入确认提示（如 rm 确认询问，黄色警告色）
    HELP,                 // 帮助文档与快捷键指南（柔和次要色，纯净直发）
    ERROR,                // 命令或系统报错信息（红色高亮，整行单色）

    // --- 2. 细分的数据与内容输出类型（替代原单一模糊的 OUTPUT）---
    OUTPUT_TEXT,          // 普通标准文本（echo, wc, stat, 普通管道文本；原生渲染，零正则零开销）
    OUTPUT_FILE_ENTRY,    // 单个文件/目录条目（ls 简洁模式，直接按名称后缀映射图标色）
    OUTPUT_PATH_ENTRY,    // 完整/相对路径条目（find 递归结果，确定性切分目录前缀与末尾文件名）
    OUTPUT_LONG_LISTING,  // ls -l 详细列表行（权限/大小/日期使用元数据灰色，末尾文件名按类型着色）
    OUTPUT_FIND_CATEGORY, // find 分类检索输出行（[目录]/[文件] 标签淡青蓝，文件名按类型着色）
    OUTPUT_ANSI,          // 包含标准 ANSI SGR 转义序列的文本（仅解析转义控制符）

    @Deprecated("使用 OUTPUT_TEXT 或具体的 OUTPUT_* 类型替代", ReplaceWith("OUTPUT_TEXT"))
    OUTPUT
}

/**
 * 终端命令流式输出数据载体 (Terminal Output Data Carrier)
 *
 * 遵循强类型流模型规范（方案 A），用于命令层向引擎及 ViewModel 发射携带语义类型的输出。
 */
data class TerminalOutput(
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT_TEXT
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
    emit(TerminalOutput(text, TerminalLineType.OUTPUT_TEXT))

suspend fun FlowCollector<TerminalOutput>.emitFile(name: String) =
    emit(TerminalOutput(name, TerminalLineType.OUTPUT_FILE_ENTRY))

suspend fun FlowCollector<TerminalOutput>.emitPath(path: String) =
    emit(TerminalOutput(path, TerminalLineType.OUTPUT_PATH_ENTRY))

suspend fun FlowCollector<TerminalOutput>.emitLongListing(line: String) =
    emit(TerminalOutput(line, TerminalLineType.OUTPUT_LONG_LISTING))

suspend fun FlowCollector<TerminalOutput>.emitFindCategory(line: String) =
    emit(TerminalOutput(line, TerminalLineType.OUTPUT_FIND_CATEGORY))

suspend fun FlowCollector<TerminalOutput>.emitAnsi(text: String) =
    emit(TerminalOutput(text, TerminalLineType.OUTPUT_ANSI))

suspend fun FlowCollector<TerminalOutput>.emitHelp(text: String) =
    emit(TerminalOutput(text, TerminalLineType.HELP))

suspend fun FlowCollector<TerminalOutput>.emitError(msg: String) =
    emit(TerminalOutput(msg, TerminalLineType.ERROR))

suspend fun FlowCollector<TerminalOutput>.emitSystem(msg: String) =
    emit(TerminalOutput(msg, TerminalLineType.SYSTEM))

/**
 * 终端展示行模型
 */
data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT_TEXT,
    val id: Long = System.nanoTime()
)

