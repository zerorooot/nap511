package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 协程与 Flow 管道命令执行引擎
 */
class PipelineEngine(
    val registry: CommandRegistry
) {
    /**
     * 执行整行终端输入命令（支持管道串联与通配符展开）
     * @param input 原始终端输入指令字符串（如 `ls -l | grep "*.mp4" | wc -l`）
     * @param ctx 终端上下文
     * @return 最终管道输出的标准行文本流 Flow<String>
     */
    suspend fun execute(input: String, ctx: TerminalContext): Flow<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return emptyFlow()
        }

        val stages = Lexer.parsePipeline(trimmed)
        if (stages.isEmpty()) {
            return emptyFlow()
        }

        // 当前目录文件候选集，用于自动展开 Glob 通配符（* 和 ?）
        val candidates = runCatching {
            ctx.listDirectory(ctx.currentCid).map { it.name }
        }.getOrDefault(emptyList())

        var currentStdin: Flow<String> = emptyFlow()

        for (stage in stages) {
            val commandDef = registry.get(stage.command) ?: return flow {
                emit("terminal: command not found: ${stage.command}")
                emit("输入 '?' 或 'help' 可查看所有支持的命令")
            }

            // 对参数列表中的 Glob 通配符（*.mp4 等）进行自动展开
            val expandedArgs = mutableListOf<String>()
            for (arg in stage.args) {
                if (GlobMatcher.hasGlobWildcards(arg)) {
                    expandedArgs.addAll(GlobMatcher.expand(arg, candidates))
                } else {
                    expandedArgs.add(arg)
                }
            }

            val nextStdout = executeStage(commandDef, ctx, expandedArgs, currentStdin)
            currentStdin = nextStdout
        }

        return currentStdin
    }

    /**
     * 使用 ChannelFlow 与 Buffer 隔离管道阶段，确保各级命令并发流畅流动，避免死锁
     */
    private suspend fun executeStage(
        commandDef: CommandDefinition,
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = channelFlow {
        try {
            // 管道流按行规范化展开，确保包含 \n 的输出在下游以独立单行流转
            val lineStream = kotlinx.coroutines.flow.flow {
                stdin.collect { chunk ->
                    for (line in chunk.split('\n')) {
                        emit(line)
                    }
                }
            }
            val stdoutFlow = commandDef.execute(ctx, args, lineStream.buffer())
            stdoutFlow.collect { line ->
                send(line)
            }
        } catch (e: Exception) {
            send("${commandDef.name}: error: ${e.message ?: e.javaClass.simpleName}")
        }
    }.flowOn(Dispatchers.IO)
}
