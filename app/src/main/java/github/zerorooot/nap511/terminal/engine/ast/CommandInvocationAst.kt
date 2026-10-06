package github.zerorooot.nap511.terminal.engine.ast

/**
 * 选项参数值节点
 *
 * @property optionName 选项名称（如 "-n", "-I", "--format"）
 * @property value 选项参数值内容（如 "10", "{}", "json"）
 * @property isQuoted 在原始命令行中该值是否由引号包裹
 */
data class OptionValueNode(
    val optionName: String,
    val value: String,
    val isQuoted: Boolean = false
)

/**
 * 位置参数语法节点
 *
 * 保留原始文本、引号状态以及是否位于 "--" 选项结束符之后，为下游路径解析、文件名匹配与 Glob 展开提供精准依据。
 *
 * @property text 参数文本
 * @property isQuoted 是否带引号（带引号的参数不参与 Shell 级 Glob 展开）
 * @property fromDelimiter 是否位于选项结束符 "--" 之后（此类参数即使以 "-" 开头，也恒定为位置参数）
 */
data class PositionalArgumentNode(
    val text: String,
    val isQuoted: Boolean = false,
    val fromDelimiter: Boolean = false
)

/**
 * 终端命令调用抽象语法树（Command Invocation AST）
 *
 * 遵循 POSIX Utility Syntax Guidelines 规范，在引擎层一次性将输入的 Token 序列切分为结构化的 AST 树：
 * 1. 布尔标志位（flags）：如 -l, -a, --refresh，支持复合简写拆分（-rf 自动展开为 -r 与 -f）；
 * 2. 键值选项（options）：如 -n 10, -I {}，支持紧贴形式与空格分隔形式；
 * 3. 位置参数（positionalArgs）：如文件路径、目标命令等；
 * 4. 选项结束符（hasDelimiter）：严格隔离 "--" 前后的语法语义。
 *
 * 彻底消除各命令中散落的 CommandArgs 与过程式字符串比对。
 *
 * @property commandName 命令标识符（如 "ls", "grep"）
 * @property flags 启用的所有单字符或长名称标志集合
 * @property options 带参数值的选项字典
 * @property positionalArgs 位置参数语法节点列表
 * @property hasDelimiter 是否显式提供了 "--" 选项结束符
 */
data class CommandInvocationAst(
    val commandName: String,
    val flags: Set<String> = emptySet(),
    val options: Map<String, OptionValueNode> = emptyMap(),
    val positionalArgs: List<PositionalArgumentNode> = emptyList(),
    val hasDelimiter: Boolean = false,
    val rawArgs: List<String> = emptyList()
) {
    /**
     * 判断是否包含指定的任一标志位（Flag）
     * 例如：ast.hasFlag("-l", "-a")
     */
    fun hasFlag(vararg names: String): Boolean =
        names.any { it in flags }

    /**
     * 判断是否包含任一复合开关标志
     */
    fun hasAny(vararg names: String): Boolean =
        names.any { it in flags }

    /**
     * 获取指定选项的字符串值（优先匹配首个存在的选项名称）
     */
    fun getOption(vararg names: String): String? =
        names.firstNotNullOfOrNull { options[it]?.value }

    /**
     * 获取指定选项的整型数值
     *
     * @param names 选项别名列表（如 "-n"）
     * @param default 选项缺失或解析失败时的默认值
     */
    fun getIntOption(vararg names: String, default: Int? = null): Int? =
        getOption(*names)?.toIntOrNull() ?: default

    /**
     * 提取所有位置参数的纯文本列表
     */
    val rawPositionalValues: List<String>
        get() = positionalArgs.map { it.text }

    /**
     * 获取首个位置参数（通常为命令的核心操作目标或主路径）
     */
    val firstPositional: String?
        get() = positionalArgs.firstOrNull()?.text
}
