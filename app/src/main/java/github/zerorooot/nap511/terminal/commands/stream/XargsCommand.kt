package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.CommandRegistry
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
 * 从标准输入读取数据，切分 token 或逐行解析，并将数据作为参数调用指定目标命令。
 * 基于强类型 [CommandInvocationAst] 解析 `-I`、`-n`、`-t` 等选项与目标子命令参数。
 *
 * @param registrySupplier 获取命令注册表的函数引用，用于动态查找目标命令的定义与执行体
 */
class XargsCommand(
    private val registrySupplier: () -> CommandRegistry
) : TerminalCommand {

    override val name: String = "xargs"

    override val description: String = "从标准输入读取数据并构建执行命令行"

    override val usage: String = "xargs [-I <replace-str>] [-n <num>] [-t] [command [initial-args...]]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-I <replace-str>", "将每行输入替换指定占位符并逐行执行命令（如 -I {}）"),
        CommandFlag("-n <num>", "每次调用命令传递的最大参数数量（默认一次性传递全部输入）"),
        CommandFlag("-t", "在执行前在屏幕打印即将运行的命令")
    )

    override val valueOptions: Set<String> = setOf("-I", "-n")

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val replaceStr = ast.getOption("-I")
        val maxArgs = ast.getIntOption("-n")
        val verbose = ast.hasFlag("-t")

        val commandTokens = ast.rawPositionalValues

        // 默认目标命令为 echo
        val targetCommandName = commandTokens.firstOrNull() ?: "echo"
        val initialArgs = commandTokens.drop(1)

        val targetCommandDef = registrySupplier().get(targetCommandName)
        if (targetCommandDef == null) {
            emitError("xargs: $targetCommandName: command not found")
            return@flow
        }

        if (replaceStr != null) {
            // -I 模式：逐行读取并替换占位符，每行独立调用一次目标命令
            stdin.collect { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty()) {
                    val substitutedArgs = if (initialArgs.any { it.contains(replaceStr) }) {
                        initialArgs.map { it.replace(replaceStr, line) }
                    } else {
                        initialArgs + line
                    }

                    if (verbose) {
                        emitText("+ $targetCommandName ${substitutedArgs.joinToString(" ")}")
                    }

                    val subTokens = substitutedArgs.map { Token(it, isQuoted = false) }
                    val subAst = CommandAstParser.parse(targetCommandName, subTokens, targetCommandDef.valueOptions)
                    val resultFlow = targetCommandDef.execute(ctx, subAst, emptyFlow())
                    resultFlow.collect { emit(it) }
                }
            }
        } else {
            // 默认批量模式：按空格/换行拆分收集所有 token，分批次执行
            val tokens = mutableListOf<String>()
            stdin.collect { rawLine ->
                val lineTokens = rawLine.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                tokens.addAll(lineTokens)
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
                val finalArgs = initialArgs + batch
                if (verbose) {
                    emitText("+ $targetCommandName ${finalArgs.joinToString(" ")}")
                }
                val batchTokens = finalArgs.map { Token(it, isQuoted = false) }
                val batchAst = CommandAstParser.parse(targetCommandName, batchTokens, targetCommandDef.valueOptions)
                val resultFlow = targetCommandDef.execute(ctx, batchAst, emptyFlow())
                resultFlow.collect { emit(it) }
            }
        }
    }
}
