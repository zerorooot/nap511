package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import github.zerorooot.nap511.terminal.engine.TerminalControlTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 终端管道命令执行与微批次流调度器 (Terminal Command Executor)
 *
 * 职责：
 * 1. 管理命令执行协程 Job 与后台执行状态 (`isExecuting`)。
 * 2. 管理交互式确认对话框挂起与恢复 (`isWaitingConfirmation`, `CompletableDeferred`)。
 * 3. 调度管道微批次刷屏缓冲（满 50 行或间隔 32ms 刷屏），保障海量输出流畅度。
 * 4. 关键机制：保证在主线程 `uiDispatcher` 调度更新 Compose 的 SnapshotStateList，防止并发状态竞争崩溃。
 * 5. 拦截并响应终端带外控制令牌 (`CLEAR_SCREEN`、`EXIT`)。
 * 6. 支持 Ctrl+C 协程安全中断与资源清理。
 */
class TerminalCommandExecutor(
    private val engine: PipelineEngine,
    private val screenBuffer: TerminalScreenBuffer,
    private val uiDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * 是否正在执行后台管道命令
     */
    var isExecuting: Boolean by mutableStateOf(false)
        private set

    /**
     * 是否正在挂起等待用户交互式确认输入 (yes/no)
     */
    var isWaitingConfirmation: Boolean by mutableStateOf(false)
        private set

    private var confirmDeferred: CompletableDeferred<Boolean>? = null
    private var currentExecutionJob: Job? = null

    /**
     * 发起交互式确认请求并挂起等待用户输入
     */
    suspend fun requestConfirmation(prompt: String): Boolean {
        isWaitingConfirmation = true
        screenBuffer.appendLine(TerminalLine(prompt, TerminalLineType.System.PROMPT))
        val deferred = CompletableDeferred<Boolean>()
        confirmDeferred = deferred
        return deferred.await()
    }

    /**
     * 解决当前挂起的交互确认输入
     *
     * @param raw 用户键入的确认文本
     * @return 是否已成功消费并处理确认
     */
    fun resolveConfirmation(raw: String): Boolean {
        if (!isWaitingConfirmation) return false
        screenBuffer.appendLine(TerminalLine(raw, TerminalLineType.System.COMMAND))
        val isConfirmed = raw.equals("yes", ignoreCase = true) || raw.equals("y", ignoreCase = true)
        isWaitingConfirmation = false
        confirmDeferred?.complete(isConfirmed)
        confirmDeferred = null
        return true
    }

    /**
     * 中断取消当前正在执行的命令 Job
     *
     * @return true 表示成功中断了正在运行的任务；false 表示当前无任务运行
     */
    fun cancelExecution(): Boolean {
        if (isExecuting) {
            currentExecutionJob?.cancel()
            currentExecutionJob = null
            isExecuting = false
            return true
        }
        return false
    }

    /**
     * 提交并异步执行命令行
     *
     * @param scope 协程作用域（通常为 ViewModelScope）
     * @param command 用户命令
     * @param context 终端运行上下文
     * @param onExit 遇到退出令牌时的回调
     * @param onComplete 命令执行完毕后的回调（用于刷新目录缓存等）
     */
    fun execute(
        scope: CoroutineScope,
        command: String,
        context: TerminalContext,
        onExit: () -> Unit,
        onComplete: () -> Unit
    ) {
        if (isExecuting) return

        isExecuting = true
        currentExecutionJob = scope.launch(ioDispatcher) {
            try {
                val flow = engine.execute(command, context)
                // 采用微批次聚合输出机制（缓冲区满 50 行或间隔 32ms 即刷屏），保障大量输出时的高帧率渲染
                val buffer = mutableListOf<TerminalLine>()
                var lastFlushTime = System.currentTimeMillis()

                // 【关键机制 - 请勿移除 withContext(Dispatchers.Main)】：
                // 必须在主线程调度更新 Compose 的 SnapshotStateList（lines）。
                // 若在 Dispatchers.IO 后台线程直接修改 lines，会与 Compose 主线程测量/布局发生并发状态竞争，
                // 导致 LazyColumn 内部 itemProvider 数量出现帧不同步并抛出 IndexOutOfBoundsException 崩溃。
                suspend fun flushBuffer() {
                    if (buffer.isNotEmpty()) {
                        val toAdd = buffer.toList()
                        buffer.clear()
                        withContext(uiDispatcher) {
                            screenBuffer.appendLines(toAdd)
                        }
                    }
                }

                flow.collect { output ->
                    if (output.text == TerminalControlTokens.CLEAR_SCREEN) {
                        flushBuffer()
                        withContext(uiDispatcher) {
                            screenBuffer.clear()
                        }
                    } else if (output.text == TerminalControlTokens.EXIT) {
                        flushBuffer()
                        withContext(uiDispatcher) {
                            onExit()
                        }
                    } else {
                        // 兼容处理可能包含换行符的输出，拆分为独立行并继承源头赋予的语义类型
                        if (output.text.contains('\n')) {
                            for (subLine in output.text.split('\n')) {
                                buffer.add(TerminalLine(subLine, output.type))
                            }
                        } else {
                            buffer.add(TerminalLine(output.text, output.type))
                        }

                        val now = System.currentTimeMillis()
                        if (buffer.size >= 50 || now - lastFlushTime >= 32) {
                            flushBuffer()
                            lastFlushTime = now
                        }
                    }
                }
                flushBuffer()
            } catch (e: CancellationException) {
                // 协程被 Ctrl+C 中断正常退出，不作为异常打印
            } catch (e: Exception) {
                withContext(uiDispatcher) {
                    screenBuffer.appendLine(
                        TerminalLine("execution error: ${e.message}", TerminalLineType.System.ERROR)
                    )
                }
            } finally {
                withContext(uiDispatcher) {
                    isExecuting = false
                    currentExecutionJob = null
                }
                onComplete()
            }
        }
    }
}
