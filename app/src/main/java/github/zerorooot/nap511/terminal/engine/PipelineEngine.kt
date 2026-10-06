package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * 协程与 Flow 管道命令执行引擎
 *
 * 遵循强类型流模型规范：
 * 1. 管道中继传递纯文本 (Flow<String>) 保证 Unix 流式计算互操作性；
 * 2. 管道终点输出保留强类型语义 (Flow<TerminalOutput>) 供给上层 ViewModel 精准渲染。
 */
class PipelineEngine(
    val registry: CommandRegistry
) {
    /**
     * 执行整行终端输入命令（支持管道串联与通配符展开）
     *
     * @param input 原始终端输入指令字符串（如 `ls -l | grep "*.mp4" | wc -l`）
     * @param ctx 终端上下文
     * @return 最终管道输出的标准语义行冷流 Flow<TerminalOutput>
     */
    suspend fun execute(input: String, ctx: TerminalContext): Flow<TerminalOutput> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return emptyFlow()
        }

        val stages = Lexer.parsePipeline(trimmed)
        if (stages.isEmpty()) {
            return emptyFlow()
        }

        // 仅在参数列表中确实包含 Glob 通配符（* 和 ?）时才按需查询目录候选集，避免无谓的网络与缓存开销
        val hasWildcards = stages.any { stage -> stage.args.any { GlobMatcher.hasGlobWildcards(it) } }
        val candidates = if (hasWildcards) {
            runCatching {
                ctx.listDirectory(ctx.currentCid).map { it.name }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        var currentStdin: Flow<String> = emptyFlow()
        var lastStdout: Flow<TerminalOutput> = emptyFlow()

        for (stage in stages) {
            val commandDef = registry.get(stage.command) ?: return flow {
                emit(TerminalOutput("terminal: command not found: ${stage.command}", TerminalLineType.System.ERROR))
                emit(TerminalOutput("输入 '?' 或 'help' 可查看所有支持的命令", TerminalLineType.System.HELP))
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

            // 【关键机制 - 不可变局部变量绑定】：
            // 使用局部只读 val stageStdout 接收当前阶段的输出流，保证随后赋值给 currentStdin 的流闭包
            // 严格捕获上一级的只读引用，绝不会因为外层 var 变量被下一轮循环重写而导致将自己作为自己的 stdin 陷入自循环死锁。
            val stageStdout = executeStage(commandDef, ctx, expandedArgs, currentStdin)
            lastStdout = stageStdout

            // 将当前阶段的输出转换为纯文本行流供给下一阶段作为 stdin
            // 【核心通道隔离机制】：仅 Output 数据类型流入下一级管道 stdin；
            // System 提示、诊断头、错误信息自动隔离在当前屏幕展示，绝不污染下游数据流。
            currentStdin = flow {
                stageStdout.collect { output ->
                    if (output.type is TerminalLineType.Output) {
                        for (subLine in output.text.split('\n')) {
                            if (subLine.isNotEmpty()) {
                                emit(subLine)
                            }
                        }
                    }
                }
            }
        }

        return lastStdout
    }

    /**
     * 管道命令阶段执行：结合 lineStream.buffer() 隔离输入流，确保各级命令协作流畅流动，避免死锁；
     * 输出端采用同步直通 Flow，保障交互确认与日志跟踪严格保序。
     */
    private fun executeStage(
        commandDef: CommandDefinition,
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        try {
            // 管道流按行规范化展开，确保包含 \n 的输出在下游以独立单行流转
            val lineStream = flow {
                stdin.collect { chunk ->
                    for (line in chunk.split('\n')) {
                        emit(line)
                    }
                }
            }
            val stdoutFlow = commandDef.execute(ctx, args, lineStream.buffer())
            stdoutFlow.collect { output ->
                emit(output)
            }
        } catch (e: Exception) {
            emit(TerminalOutput("${commandDef.name}: error: ${e.message ?: e.javaClass.simpleName}", TerminalLineType.System.ERROR))
        }
    }
}
