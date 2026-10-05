package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import github.zerorooot.nap511.terminal.engine.TerminalCommand
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

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        var replaceStr: String? = null
        var maxArgs: Int? = null
        var verbose = false

        val commandTokens = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg == "-I" -> {
                    if (i + 1 < args.size) {
                        replaceStr = args[++i]
                    }
                }
                arg.startsWith("-I") && arg.length > 2 -> {
                    replaceStr = arg.substring(2)
                }
                arg == "-n" -> {
                    if (i + 1 < args.size) {
                        maxArgs = args[++i].toIntOrNull()
                    }
                }
                arg.startsWith("-n") && arg.length > 2 -> {
                    maxArgs = arg.substring(2).toIntOrNull()
                }
                arg == "-t" -> {
                    verbose = true
                }
                else -> {
                    commandTokens.add(arg)
                }
            }
            i++
        }

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

                    val resultFlow = targetCommandDef.execute(ctx, substitutedArgs, emptyFlow())
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
                val resultFlow = targetCommandDef.execute(ctx, finalArgs, emptyFlow())
                resultFlow.collect { emit(it) }
            }
        }
    }
}
