package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import github.zerorooot.nap511.terminal.engine.Lexer
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.Token
import github.zerorooot.nap511.terminal.engine.ast.CommandAstParser
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * 标准输入分词定界策略 (Delimiter Policy)
 *
 * 负责从原始流中提取待处理的数据项，与后续子命令参数的组装调度彻底正交解耦。
 */
sealed interface XargsDelimiter {
    /** -0 / --null：以 NUL (\0) 字符定界，严格保留字符串内部及首尾的一切空白符（POSIX/GNU 标准规范） */
    object Null : XargsDelimiter

    /** -I 或 -L 模式：以换行符 (\n) 作为行定界符，按 POSIX 规范剔除行首尾空白 (trim) */
    object Line : XargsDelimiter

    /** 默认模式：按空白字符定界，支持单双引号包裹与反斜杠转义 */
    object Whitespace : XargsDelimiter
}

/**
 * 子命令批处理调度策略 (Batch Mode)
 *
 * 负责将提取到的纯净数据项打包、替换并组装为子命令的执行参数。
 */
sealed interface XargsBatchMode {
    /** -I <placeholder>：逐项替换目标占位符并逐次执行子命令 */
    data class Substitute(val placeholder: String) : XargsBatchMode

    /** -L <lines>：按行/项数量上限打包后分批执行 */
    data class ByLines(val maxLines: Int) : XargsBatchMode

    /** -n <num> 或默认模式：按参数数量上限打包分批执行（若为 null 则一次性传递全部） */
    data class ByArgs(val maxArgs: Int?) : XargsBatchMode
}

/**
 * xargs 强类型不可变执行计划 (Execution Plan)
 *
 * 在编译期基于 AST 语法树一次性合成，实现静态语法分析与运行期流调度的解耦。
 */
data class XargsPlan(
    val targetCommandName: String,
    val initialArgTokens: List<Token>,
    val delimiter: XargsDelimiter,
    val batchMode: XargsBatchMode,
    val eofStr: String?,
    val verbose: Boolean,
    val interactive: Boolean
)

/**
 * 管道参数转换与子命令批量执行工具（xargs）
 *
 * 遵循 POSIX 标准规范设计（POSIX.1-2017 / 2024）与强类型两阶段编译架构：
 * 1. 【两阶段编译解耦】：静态编译期解析 AST 并生成不可变的 [XargsPlan]，将输入定界策略 [XargsDelimiter]
 *    与批处理调度策略 [XargsBatchMode] 彻底正交解耦，彻底修复 `-0` 与 `-I` 选项互斥短路的缺陷；
 * 2. 【POSIX Guideline 13 包装命令规范】：通过 [isWrapperCommand] 确立高阶命令契约，解析完 xargs 自身选项后，
 *    目标子命令及其携带的所有专属参数（如 `unzip -l` 中的 `-l`）原貌无损封包为 [CommandInvocationAst.subcommand]，绝不被提前劫持；
 * 3. 【-0 / --null 分隔符模式】：流式支持 NUL 字符 (`\0`) 作为输入界限，严格保留文件名内嵌与首尾空格，杜绝网盘文件名切词污染；
 * 4. 【字面量参数安全隔离】：占位符替换和参数追加时强制标记为带引号字面量 (`isQuoted = true`)，杜绝短横线前缀文件名被误判为命令行选项；
 * 5. 【-p 交互式确认】：每次执行批次前调用 [TerminalContext.confirm] 挂起询问用户，用户确认后方才执行；
 * 6. 【-L 按行批处理模式】：以非空输入行为单位收集批次，支持指定单次传递的最大行数；
 * 7. 【-E 逻辑 EOF 终止符】：在输入流中遇到指定的字符串标记时立即终止读取；
 * 8. 【-I 占位符逐行替换模式】：逐行提取非空文本替换目标命令中的指定占位符，支持任意自定义占位符；
 * 9. 【-n 参数数量限制批处理】：默认收集所有输入 Token，支持限制每次传递的最大参数数量；
 * 10. 【POSIX 标准分词支持】：默认模式下支持单双引号包裹与反斜杠转义，精准保留文件名内嵌空格。
 *
 * @param registrySupplier 获取命令注册表的函数引用，用于动态查找目标命令的定义与执行体
 */
class XargsCommand(
    private val registrySupplier: () -> CommandRegistry
) : TerminalCommand {

    override val name: String = "xargs"

    override val description: String = "从标准输入读取数据并构建执行命令行（POSIX 规范）"

    override val usage: String = "xargs [-0|--null] [-t] [-p] [-I <repl>] [-n <num>] [-L <num>] [-E <eof>] [command [args...]]"

    /** 声明为高阶包装命令，指导语法分析器在定位目标命令后停止提取选项 */
    override val isWrapperCommand: Boolean = true

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-I <replace-str>", "将每行输入替换指定占位符并逐行执行命令（如 -I {}、-I _）"),
        CommandFlag("-n <num>", "每次调用命令传递的最大参数数量（默认一次性传递全部输入）"),
        CommandFlag("-L <num>", "每次调用命令传递的最大输入行数（按行分批执行）"),
        CommandFlag("-E <eof-str>", "逻辑文件结束符（输入遇到此标记即停止读取）"),
        CommandFlag("-0, --null", "以 NUL (\\0) 字符作为输入分隔符，安全支持含空格的文件名"),
        CommandFlag("-t", "在执行前在屏幕打印即将运行的命令"),
        CommandFlag("-p", "每次执行命令前询问用户确认 (yes/no)")
    )

    override val valueOptions: Set<String> = setOf("-I", "-n", "-L", "-E")

    /**
     * 编译期计划提取（Compile Plan）
     *
     * 将命令行 AST 静态语法树解析为强类型不可变执行计划 [XargsPlan]：
     * 保证“输入定界策略”与“命令分发调度模式”完全解耦，消除多分支互斥缺陷。
     */
    private fun compilePlan(ast: CommandInvocationAst): Result<XargsPlan> {
        val replaceStr = ast.getOption("-I")
        val maxArgs = ast.getIntOption("-n")
        val maxLines = ast.getIntOption("-L")
        val eofStr = ast.getOption("-E")
        val nullDelimiter = ast.hasFlag("-0", "--null")
        val verbose = ast.hasFlag("-t")
        val interactive = ast.hasFlag("-p")

        // 1. 决议目标命令与初始参数（优先从 SubcommandAst 获取以确保不丢失子命令专属选项）
        val subcommand = ast.subcommand
        val targetCommandName = subcommand?.name ?: ast.firstPositional ?: "echo"
        val initialArgTokens = if (subcommand != null) {
            subcommand.rawTokens
        } else {
            val positional = ast.rawPositionalValues
            if (positional.isNotEmpty()) {
                positional.drop(1).map { Token(it, isQuoted = false) }
            } else {
                emptyList()
            }
        }

        // 2. 决议输入定界策略（正交独立，-0 优先级最高）
        val delimiter = when {
            nullDelimiter -> XargsDelimiter.Null
            replaceStr != null || (maxLines != null && maxLines > 0) -> XargsDelimiter.Line
            else -> XargsDelimiter.Whitespace
        }

        // 3. 决议批处理调度策略（正交独立，-I 模式优先于 -L 与 -n）
        val batchMode = when {
            replaceStr != null -> XargsBatchMode.Substitute(replaceStr)
            maxLines != null && maxLines > 0 -> XargsBatchMode.ByLines(maxLines)
            else -> XargsBatchMode.ByArgs(maxArgs)
        }

        return Result.success(
            XargsPlan(
                targetCommandName = targetCommandName,
                initialArgTokens = initialArgTokens,
                delimiter = delimiter,
                batchMode = batchMode,
                eofStr = eofStr,
                verbose = verbose,
                interactive = interactive
            )
        )
    }

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        // 1. 编译期 AST 执行计划构建
        val plan = compilePlan(ast).getOrElse { error ->
            emitError("xargs: ${error.message ?: "参数解析失败"}")
            return@flow
        }

        // 2. 校验目标子命令是否存在
        val targetCommand = registrySupplier().get(plan.targetCommandName)
        if (targetCommand == null) {
            emitError("xargs: ${plan.targetCommandName}: command not found")
            return@flow
        }

        /**
         * 统一子命令执行闭包：负责 -t 回显打印、-p 交互确认拦截及目标命令 AST 构建执行
         */
        suspend fun executeBatch(tokensToPass: List<Token>) {
            val displayCmd = "+ ${plan.targetCommandName} ${tokensToPass.joinToString(" ") { it.text }}"
            if (plan.verbose) {
                emitText(displayCmd)
            }
            if (plan.interactive) {
                val confirmed = ctx.confirm("$displayCmd ?...")
                if (!confirmed) {
                    return // 用户取消当前批次
                }
            }
            val subAst = CommandAstParser.parse(plan.targetCommandName, tokensToPass, targetCommand.valueOptions)
            val resultFlow = targetCommand.execute(ctx, subAst, emptyFlow())
            resultFlow.collect { emit(it) }
        }

        // 3. 阶段一：根据 plan.delimiter 从 stdin 提取纯净项
        val items = mutableListOf<String>()
        var stopReading = false

        when (plan.delimiter) {
            is XargsDelimiter.Null -> {
                // 【流式 NUL 切分与规范保护】：
                // 1. 逐字符流式拆分，保持内存低开销；
                // 2. 遵循 POSIX/GNU xargs 规范：-0 模式下严格保留字符串内及首尾空格，严禁执行 trim()；
                // 3. 遵循 GNU 规范：-0 模式下显式忽略 -E (eofStr)。
                val current = StringBuilder()
                stdin.collect { chunk ->
                    for (ch in chunk) {
                        if (ch == '\u0000') {
                            val item = current.toString()
                            current.clear()
                            if (item.isNotEmpty()) {
                                items.add(item)
                            }
                        } else {
                            current.append(ch)
                        }
                    }
                }
                if (current.isNotEmpty()) {
                    items.add(current.toString())
                }
            }

            is XargsDelimiter.Line -> {
                // POSIX 规范：-I 与 -L 模式下剥离行首尾空白 (stripping leading and trailing blanks)
                stdin.collect { rawLine ->
                    if (stopReading) return@collect
                    val clean = rawLine.trim()
                    if (plan.eofStr != null && clean == plan.eofStr) {
                        stopReading = true
                        return@collect
                    }
                    if (clean.isNotEmpty()) {
                        items.add(clean)
                    }
                }
            }

            is XargsDelimiter.Whitespace -> {
                stdin.collect { rawLine ->
                    if (stopReading) return@collect
                    val clean = rawLine.trim()
                    if (plan.eofStr != null && clean == plan.eofStr) {
                        stopReading = true
                        return@collect
                    }
                    val lineTokens = Lexer.tokenizeWithQuoteInfo(clean)
                    for (t in lineTokens) {
                        if (plan.eofStr != null && t.text == plan.eofStr) {
                            stopReading = true
                            break
                        }
                        items.add(t.text)
                    }
                }
            }
        }

        if (items.isEmpty()) return@flow

        // 4. 阶段二：根据 plan.batchMode 调度构建参数并分发执行
        when (val mode = plan.batchMode) {
            is XargsBatchMode.Substitute -> {
                for (item in items) {
                    val hasPlaceholder = plan.initialArgTokens.any { it.text.contains(mode.placeholder) }
                    // 【参数注入防护机制】：
                    // 将占位符替换结果以及追加项显式标记为 isQuoted = true。
                    // 确保下发给 CommandAstParser 时无条件作为位置参数，杜绝 "-filename" 等短横线文件名被误判为命令选项 (Option)。
                    val substitutedTokens = if (hasPlaceholder) {
                        plan.initialArgTokens.map { token ->
                            if (token.text.contains(mode.placeholder)) {
                                token.copy(
                                    text = token.text.replace(mode.placeholder, item),
                                    isQuoted = true
                                )
                            } else {
                                token
                            }
                        }
                    } else {
                        plan.initialArgTokens + Token(item, isQuoted = true)
                    }
                    executeBatch(substitutedTokens)
                }
            }

            is XargsBatchMode.ByLines -> {
                for (batch in items.chunked(mode.maxLines)) {
                    val batchTokens = plan.initialArgTokens + batch.map { Token(it, isQuoted = true) }
                    executeBatch(batchTokens)
                }
            }

            is XargsBatchMode.ByArgs -> {
                val batches = if (mode.maxArgs != null && mode.maxArgs > 0) {
                    items.chunked(mode.maxArgs)
                } else {
                    listOf(items)
                }
                for (batch in batches) {
                    val batchTokens = plan.initialArgTokens + batch.map { Token(it, isQuoted = true) }
                    executeBatch(batchTokens)
                }
            }
        }
    }
}
