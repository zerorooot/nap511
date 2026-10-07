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
 * 管道参数转换与子命令批量执行工具（xargs）
 *
 * 遵循 POSIX 标准规范设计（POSIX.1-2017 / 2024）：
 * 1. 【POSIX Guideline 13 包装命令规范】：通过 [isWrapperCommand] 确立高阶命令契约，解析完 xargs 自身选项后，
 *    目标子命令及其携带的所有专属参数（如 `unzip -l` 中的 `-l`）原貌无损封包为 [CommandInvocationAst.subcommand]，绝不被提前劫持；
 * 2. 【-0 / --null 分隔符模式】：支持 NUL 字符 (`\0`) 作为输入界限，彻底杜绝网盘带空格文件名的切词污染；
 * 3. 【-p 交互式确认】：每次执行批次前调用 [TerminalContext.confirm] 挂起询问用户，用户确认后方才执行；
 * 4. 【-L 按行批处理模式】：以非空输入行为单位收集批次，支持指定单次传递的最大行数；
 * 5. 【-E 逻辑 EOF 终止符】：在输入流中遇到指定的字符串标记时立即终止读取；
 * 6. 【-I 占位符逐行替换模式】：逐行提取非空文本替换目标命令中的指定占位符，支持任意自定义占位符；
 * 7. 【-n 参数数量限制批处理】：默认收集所有输入 Token，支持限制每次传递的最大参数数量；
 * 8. 【POSIX 标准分词支持】：默认模式下支持单双引号包裹与反斜杠转义，精准保留文件名内嵌空格。
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

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        // 1. 提取 xargs 自身合法选项
        val replaceStr = ast.getOption("-I")
        val maxArgs = ast.getIntOption("-n")
        val maxLines = ast.getIntOption("-L")
        val eofStr = ast.getOption("-E")
        val nullDelimiter = ast.hasFlag("-0", "--null")
        val verbose = ast.hasFlag("-t")
        val interactive = ast.hasFlag("-p")

        // 2. 提取目标子命令名称与初始参数（优先从 SubcommandAst 获取以确保不丢失 -l 等选项）
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

        // 3. 校验目标子命令是否存在
        val targetCommand = registrySupplier().get(targetCommandName)
        if (targetCommand == null) {
            emitError("xargs: $targetCommandName: command not found")
            return@flow
        }

        /**
         * 统一子命令执行闭包：负责 -t 回显打印、-p 交互确认拦截及目标命令 AST 构建执行
         */
        suspend fun executeBatch(tokensToPass: List<Token>) {
            val displayCmd = "+ $targetCommandName ${tokensToPass.joinToString(" ") { it.text }}"
            if (verbose) {
                emitText(displayCmd)
            }
            if (interactive) {
                val confirmed = ctx.confirm("$displayCmd ?...")
                if (!confirmed) {
                    return // 用户取消当前批次
                }
            }
            val subAst = CommandAstParser.parse(targetCommandName, tokensToPass, targetCommand.valueOptions)
            val resultFlow = targetCommand.execute(ctx, subAst, emptyFlow())
            resultFlow.collect { emit(it) }
        }

        // 4. 根据 POSIX 选项分支处理标准输入并调度执行
        when {
            // 分支 A：-I 占位符逐行替换模式
            replaceStr != null -> {
                var stopReading = false
                stdin.collect { rawLine ->
                    if (stopReading) return@collect
                    val line = rawLine.trim()
                    if (eofStr != null && line == eofStr) {
                        stopReading = true
                        return@collect
                    }
                    if (line.isNotEmpty()) {
                        val hasPlaceholder = initialArgTokens.any { it.text.contains(replaceStr) }
                        val substitutedTokens = if (hasPlaceholder) {
                            initialArgTokens.map { token ->
                                if (token.text.contains(replaceStr)) {
                                    token.copy(text = token.text.replace(replaceStr, line))
                                } else {
                                    token
                                }
                            }
                        } else {
                            initialArgTokens + Token(line, isQuoted = false)
                        }
                        executeBatch(substitutedTokens)
                    }
                }
            }

            // 分支 B：-0 / --null NUL 字符切分模式（安全处理包含空格与换行的文件名）
            nullDelimiter -> {
                val buffer = StringBuilder()
                stdin.collect { chunk ->
                    buffer.append(chunk)
                }
                val allText = buffer.toString()
                val items = mutableListOf<String>()
                for (item in allText.split('\u0000')) {
                    if (eofStr != null && item == eofStr) {
                        break
                    }
                    if (item.isNotEmpty()) {
                        items.add(item)
                    }
                }
                if (items.isEmpty()) return@flow

                val batches = if (maxArgs != null && maxArgs > 0) items.chunked(maxArgs) else listOf(items)
                for (batch in batches) {
                    val batchTokens = initialArgTokens + batch.map { Token(it, isQuoted = false) }
                    executeBatch(batchTokens)
                }
            }

            // 分支 C：-L 按行批处理模式
            maxLines != null && maxLines > 0 -> {
                val lines = mutableListOf<String>()
                var stopReading = false
                stdin.collect { rawLine ->
                    if (stopReading) return@collect
                    val line = rawLine.trim()
                    if (eofStr != null && line == eofStr) {
                        stopReading = true
                        return@collect
                    }
                    if (line.isNotEmpty()) {
                        lines.add(line)
                    }
                }
                if (lines.isEmpty()) return@flow

                for (batch in lines.chunked(maxLines)) {
                    val batchTokens = initialArgTokens + batch.map { Token(it, isQuoted = false) }
                    executeBatch(batchTokens)
                }
            }

            // 分支 D：POSIX 默认空白分词模式（支持引号保留空格与反斜杠转义）
            else -> {
                val tokens = mutableListOf<Token>()
                var stopReading = false
                stdin.collect { rawLine ->
                    if (stopReading) return@collect
                    val line = rawLine.trim()
                    if (eofStr != null && line == eofStr) {
                        stopReading = true
                        return@collect
                    }
                    val lineTokens = Lexer.tokenizeWithQuoteInfo(rawLine)
                    for (t in lineTokens) {
                        if (eofStr != null && t.text == eofStr) {
                            stopReading = true
                            break
                        }
                        tokens.add(t)
                    }
                }

                if (tokens.isEmpty()) {
                    return@flow
                }

                val batches = if (maxArgs != null && maxArgs > 0) {
                    tokens.chunked(maxArgs)
                } else {
                    listOf(tokens)
                }

                for (batch in batches) {
                    val batchTokens = initialArgTokens + batch
                    executeBatch(batchTokens)
                }
            }
        }
    }
}
