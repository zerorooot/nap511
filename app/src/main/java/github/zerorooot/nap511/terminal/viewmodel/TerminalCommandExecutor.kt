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
 * 4. 交互冲刷屏障 (Flush Barrier)：在发起二次确认请求前，强制将微批次缓冲区中所有在途日志先全部刷屏，杜绝命令输出与交互提示乱序串行。
 * 5. 关键安全机制：保证在主线程 `uiDispatcher` 调度更新 Compose 的 SnapshotStateList，防止并发状态竞争导致 LazyColumn 内部 itemProvider 抛出 IndexOutOfBoundsException 崩溃。
 * 6. 拦截并响应终端带外控制令牌 (`CLEAR_SCREEN`、`EXIT`)。
 * 7. 支持 Ctrl+C 协程安全中断与资源清理。
 *
 * @param engine 管道解析与执行引擎
 * @param screenBuffer 屏幕终端行渲染缓冲区
 * @param uiDispatcher 主线程协程调度器，用于安全更新 Compose 状态
 * @param ioDispatcher 后台 I/O 协程调度器，用于执行耗时命令与网络操作
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
     * 当前正在执行的命令文本
     */
    var currentRunningCommand: String by mutableStateOf("")
        private set

    /**
     * 是否正在挂起等待用户交互式确认输入 (yes/no)
     */
    var isWaitingConfirmation: Boolean by mutableStateOf(false)
        private set

    /**
     * 当前交互式确认的提示词文本
     */
    var currentConfirmationPrompt: String by mutableStateOf("")
        private set

    private var confirmDeferred: CompletableDeferred<Boolean>? = null
    private var currentExecutionJob: Job? = null

    // 内部微批次聚合输出缓冲区及同步锁
    private val pendingBuffer = mutableListOf<TerminalLine>()
    private val bufferLock = Any()
    private var lastFlushTime = 0L

    /**
     * 立即将暂存在微批次缓冲区中的所有输出行在主线程刷入屏幕 (Flush Barrier)
     */
    suspend fun flushPendingBuffer() {
        val toAdd = synchronized(bufferLock) {
            if (pendingBuffer.isEmpty()) return@synchronized emptyList()
            val copy = pendingBuffer.toList()
            pendingBuffer.clear()
            copy
        }
        if (toAdd.isNotEmpty()) {
            // 【关键机制 - 请勿移除 withContext(uiDispatcher)】：
            // 必须在主线程调度更新 Compose 的 SnapshotStateList（lines）。
            // 若在 Dispatchers.IO 后台线程直接修改 lines，会与 Compose 主线程测量/布局发生并发状态竞争，
            // 导致 LazyColumn 内部 itemProvider 数量出现帧不同步并抛出 IndexOutOfBoundsException 崩溃。
            withContext(uiDispatcher) {
                screenBuffer.appendLines(toAdd)
            }
        }
    }

    /**
     * 发起交互式确认请求并挂起等待用户输入
     */
    suspend fun requestConfirmation(prompt: String): Boolean {
        // 1. 屏障：优先清空所有累积的待刷屏输出，确保在提问前所有前置日志（如 xargs -t 的跟踪行）均已上屏
        flushPendingBuffer()

        // 2. 在 UI 调度器中原子地将提示信息追加到屏幕末尾并标记挂起状态
        withContext(uiDispatcher) {
            screenBuffer.appendLine(TerminalLine(prompt, TerminalLineType.System.PROMPT))
            currentConfirmationPrompt = prompt
            isWaitingConfirmation = true
        }

        // 3. 挂起等待用户响应 (y/n)
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
        currentConfirmationPrompt = ""
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
            currentRunningCommand = ""
            if (isWaitingConfirmation) {
                isWaitingConfirmation = false
                currentConfirmationPrompt = ""
                confirmDeferred?.cancel()
                confirmDeferred = null
            }
            synchronized(bufferLock) {
                pendingBuffer.clear()
            }
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
        currentRunningCommand = command
        synchronized(bufferLock) {
            pendingBuffer.clear()
            lastFlushTime = System.currentTimeMillis()
        }

        currentExecutionJob = scope.launch(ioDispatcher) {
            try {
                val flow = engine.execute(command, context)

                flow.collect { output ->
                    if (output.text == TerminalControlTokens.CLEAR_SCREEN) {
                        flushPendingBuffer()
                        withContext(uiDispatcher) {
                            screenBuffer.clear()
                        }
                    } else if (output.text == TerminalControlTokens.EXIT) {
                        flushPendingBuffer()
                        withContext(uiDispatcher) {
                            onExit()
                        }
                    } else {
                        // 兼容处理可能包含换行符的输出，拆分为独立行并继承源头赋予的语义类型
                        val linesToAdd = if (output.text.contains('\n')) {
                            output.text.split('\n').map { TerminalLine(it, output.type) }
                        } else {
                            listOf(TerminalLine(output.text, output.type))
                        }

                        val shouldFlush = synchronized(bufferLock) {
                            pendingBuffer.addAll(linesToAdd)
                            val now = System.currentTimeMillis()
                            if (pendingBuffer.size >= 50 || now - lastFlushTime >= 32) {
                                lastFlushTime = now
                                true
                            } else {
                                false
                            }
                        }

                        if (shouldFlush) {
                            flushPendingBuffer()
                        }
                    }
                }
                flushPendingBuffer()
            } catch (e: CancellationException) {
                // 协程被 Ctrl+C 中断正常退出，不作为异常打印
            } catch (e: Exception) {
                withContext(uiDispatcher) {
                    screenBuffer.appendLine(
                        TerminalLine("execution error: ${e.message}", TerminalLineType.System.ERROR)
                    )
                }
            } finally {
                flushPendingBuffer()
                withContext(uiDispatcher) {
                    isExecuting = false
                    currentRunningCommand = ""
                    isWaitingConfirmation = false
                    currentConfirmationPrompt = ""
                    confirmDeferred = null
                    currentExecutionJob = null
                }
                onComplete()
            }
        }
    }
}
