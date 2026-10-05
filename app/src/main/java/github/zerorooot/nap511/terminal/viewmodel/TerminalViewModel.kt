package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.terminal.commands.CommandRegistryFactory
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.AutosuggestionEngine
import github.zerorooot.nap511.terminal.engine.CompletionCandidate
import github.zerorooot.nap511.terminal.engine.CompletionContextType
import github.zerorooot.nap511.terminal.engine.CompletionEngine
import github.zerorooot.nap511.terminal.engine.CompletionResult
import github.zerorooot.nap511.terminal.engine.ParsedContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import github.zerorooot.nap511.terminal.engine.TerminalLineEditor
import github.zerorooot.nap511.util.FileOpener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalViewModel(
    initialCid: String = "0",
    initialPath: String = "/",
    initialPathList: List<PathBean> = emptyList(),
    avatarBean: AvatarBean = AvatarBean(),
//    var onNavigateAction: ((Route) -> Unit)? = null,
    fileOpener: FileOpener? = null
) : ViewModel() {
    var avatarBean by mutableStateOf(avatarBean)
    var fileOpener: FileOpener? by mutableStateOf(fileOpener)
        private set

    fun updateFileOpener(opener: FileOpener?) {
        this.fileOpener = opener
        context.fileOpener = opener
    }

    var isSessionInitialized = false
        private set

    fun initDirectoryIfNeeded(cid: String, path: String, pathList: List<PathBean>) {
        if (!isSessionInitialized) {
            isSessionInitialized = true
            currentCid = cid
            currentPath = if (path.startsWith("/")) path else "/$path"
            context.updateDirectory(cid, path, pathList)
            lines.clear()
            printWelcomeBanner()
            refreshCachedEntries(cid)
        }
    }

    fun resetSession() {
        isSessionInitialized = false
        lines.clear()
        inputState = TextFieldValue("")
        ghostText = ""
        lastSubmittedText = ""
        resetModifiers()
        dismissCompletionBar()
    }

    val lines = mutableStateListOf<TerminalLine>()
    private val history = mutableListOf<String>()
    private var historyPointer = -1
    private var savedDraftInput = ""

    var onExitAction: (() -> Unit)? = null
    private var currentExecutionJob: Job? = null

    // Termux 风格粘滞修饰键状态 (CTRL / ALT)
    var isCtrlActive by mutableStateOf(false)
        private set

    var isAltActive by mutableStateOf(false)
        private set

    fun toggleCtrl() {
        isCtrlActive = !isCtrlActive
        if (isCtrlActive) isAltActive = false
    }

    fun toggleAlt() {
        isAltActive = !isAltActive
        if (isAltActive) isCtrlActive = false
    }

    fun resetModifiers() {
        isCtrlActive = false
        isAltActive = false
    }

    var inputState by mutableStateOf(TextFieldValue(""))
        private set

    private var lastSubmittedText = ""

    var ghostText by mutableStateOf("")
        private set

    var isExecuting by mutableStateOf(false)
        private set

    var currentPath by mutableStateOf(if (initialPath.startsWith("/")) initialPath else "/$initialPath")
        private set

    var currentCid by mutableStateOf(initialCid)
        private set

    var isWaitingConfirmation by mutableStateOf(false)
        private set

    private var confirmDeferred: CompletableDeferred<Boolean>? = null

    // 自动补全候选条状态
    val completionCandidates = mutableStateListOf<CompletionCandidate>()

    var isCompletionBarVisible by mutableStateOf(false)
        private set

    var activeCandidateIndex by mutableIntStateOf(-1)
        private set

    private var baseInputText = ""
    private var baseParsedContext: ParsedContext? = null

    // 缓存当前目录下的文件名，用于快速预测补全
    private val cachedDirectoryEntries = mutableListOf<String>()

    val context = TerminalContext(
        initialCid = initialCid,
        initialPath = initialPath,
        initialPathList = initialPathList,
        onConfirmRequest = { prompt ->
            isWaitingConfirmation = true
            appendTerminalLine(TerminalLine(prompt, TerminalLineType.PROMPT))
            val deferred = CompletableDeferred<Boolean>()
            confirmDeferred = deferred
            deferred.await()
        },
        onDirectoryChanged = { cid, path ->
            currentCid = cid
            currentPath = path
            refreshCachedEntries(cid)
        },
        fileOpener = fileOpener
    )

    val historyManager = TerminalHistoryManager()
    private val registry = CommandRegistryFactory.createDefaultRegistry(
        historyManager = historyManager,
        onClearMemoryHistory = {
            history.clear()
            historyPointer = -1
        }
    )
    private val engine = PipelineEngine(registry)

    init {
        // 打印终端欢迎信息与快捷指引
        printWelcomeBanner()
        refreshCachedEntries(initialCid)
        loadPersistentHistory()
    }

    private fun loadPersistentHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = historyManager.loadRecentHistory(1000)
            withContext(Dispatchers.Main) {
                history.clear()
                history.addAll(loaded)
                historyPointer = -1
                updateGhostText(inputState.text, inputState.selection.end)
            }
        }
    }

    companion object {
        /**
         * 终端屏幕输出最大保留行数上限 (Scrollback Limit)
         * 避免长时间运行或海量输出导致内存暴涨与掉帧
         */
        const val MAX_SCROLLBACK_LINES = 2000
    }

    /**
     * 安全向终端输出追加单行，带最大回滚行数截断保护，防止长期运行导致内存膨胀
     */
    fun appendTerminalLine(line: TerminalLine) {
        if (lines.size >= MAX_SCROLLBACK_LINES) {
            val removeCount = (lines.size - MAX_SCROLLBACK_LINES + 1).coerceAtLeast(1)
            lines.subList(0, removeCount.coerceAtMost(lines.size)).clear()
        }
        lines.add(line)
    }

    /**
     * 批量追加终端输出，降低 Compose 重组频率，保证海量输出流畅度
     */
    fun appendTerminalLines(newLines: List<TerminalLine>) {
        if (newLines.isEmpty()) return
        val effectiveNewLines = if (newLines.size > MAX_SCROLLBACK_LINES) {
            newLines.takeLast(MAX_SCROLLBACK_LINES)
        } else {
            newLines
        }
        val total = lines.size + effectiveNewLines.size
        if (total > MAX_SCROLLBACK_LINES) {
            val removeCount = (total - MAX_SCROLLBACK_LINES).coerceAtLeast(1)
            lines.subList(0, removeCount.coerceAtMost(lines.size)).clear()
        }
        lines.addAll(effectiveNewLines)
    }

    private fun printWelcomeBanner() {
        appendTerminalLine(TerminalLine("=== 115 Cloud Terminal (nap511) ===", TerminalLineType.SYSTEM))
        appendTerminalLine(
            TerminalLine(
                "欢迎使用网盘极客终端！输入 '?' 或 'help' 可查看命令列表与快捷键指南。",
                TerminalLineType.SYSTEM
            )
        )
        appendTerminalLine(
            TerminalLine(
                "提示：支持管道 '|' 与通配符；悬浮栏已内置 CTRL / ALT 粘滞键与常用 Readline 快捷键。",
                TerminalLineType.SYSTEM
            )
        )
        appendTerminalLine(
            TerminalLine(
                "当前工作目录: $currentPath (cid: $currentCid)\n",
                TerminalLineType.SYSTEM
            )
        )
    }

    /**
     * 上下文路径提示信息（用户名@用户ID:路径），用于第一行展示
     */
    fun contextPromptText(): String {
        return "${avatarBean.userName}@${avatarBean.userId}:$currentPath"
    }

    /**
     * 完整提示符文本（包含换行与提示符 $ ），保持向下兼容
     */
    fun promptText(): String {
        return "${contextPromptText()}\n$ "
    }

    private fun refreshCachedEntries(cid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = runCatching {
                context.listDirectory(cid).map { it.name }
            }.getOrDefault(emptyList())
            cachedDirectoryEntries.clear()
            cachedDirectoryEntries.addAll(entries)
            updateGhostText(inputState.text, inputState.selection.end)
        }
    }

    fun onInputChange(newValue: TextFieldValue) {
        // 拦截 IME 软键盘回弹的已提交旧文本，防止提交后输入框依然残留上一次输入的命令
        if (lastSubmittedText.isNotEmpty() && inputState.text.isEmpty()) {
            val trimmedNew = newValue.text.trim()
            val trimmedLast = lastSubmittedText.trim()
            if (trimmedNew == trimmedLast) {
                return
            }
        }
        lastSubmittedText = ""

        // 如果处于 CTRL 或 ALT 粘滞模式，拦截用户通过软键盘输入的对应按键
        if (isCtrlActive || isAltActive) {
            val oldText = inputState.text
            val newText = newValue.text

            // 1. 软键盘按键输入（文本增加 1 个字符）
            if (newText.length == oldText.length + 1) {
                val typedChar = TerminalLineEditor.findSingleInsertedChar(oldText, newText)
                if (typedChar != null) {
                    val handled = handleModifierChar(typedChar)
                    if (handled) return
                }
            }

            // 2. 软键盘退格删除（Alt + Backspace 组合场景）
            if (isAltActive && newText.length < oldText.length) {
                handleAltBackspace()
                return
            }

            // 如果未能匹配对应快捷键，重置修饰键并按常规输入处理
            resetModifiers()
        }

        if (isCompletionBarVisible) {
            dismissCompletionBar()
        }

        inputState = newValue
        updateGhostText(newValue.text, newValue.selection.end)
    }

    /**
     * 软键盘修饰键字符分发处理
     */
    private fun handleModifierChar(char: Char): Boolean {
        if (isCtrlActive) {
            when (char.lowercaseChar()) {
                'c' -> handleCtrlC()
                'u' -> handleCtrlU()
                'k' -> handleCtrlK()
                'w' -> handleCtrlW()
                'l' -> handleCtrlL()
                'a' -> handleCtrlA()
                'e' -> handleCtrlE()
                'd' -> handleCtrlD(onExitAction ?: {})
                else -> {
                    resetModifiers()
                    return false
                }
            }
            return true
        } else if (isAltActive) {
            when {
                char.equals('b', ignoreCase = true) -> handleAltB()
                char.equals('f', ignoreCase = true) -> handleAltF()
                char.equals('d', ignoreCase = true) -> handleAltD()
                char == '.' -> handleAltDot()
                else -> {
                    resetModifiers()
                    return false
                }
            }
            return true
        }
        return false
    }

    private fun updateGhostText(text: String, cursor: Int) {
        if (isWaitingConfirmation) {
            ghostText = ""
            return
        }
        ghostText = AutosuggestionEngine.calculateGhostText(
            input = text,
            cursorPosition = cursor,
            registeredCommands = registry.commands.keys.toList(),
            directoryEntries = cachedDirectoryEntries,
            history = history
        )
    }

    /**
     * 采纳行内幽灵文本建议 (Tab 或 右箭头)
     */
    fun acceptGhostText(): Boolean {
        if (ghostText.isNotEmpty()) {
            val newText = inputState.text + ghostText
            inputState = TextFieldValue(newText, selection = TextRange(newText.length))
            ghostText = ""
            updateGhostText(newText, newText.length)
            return true
        }
        return false
    }

    /**
     * 采纳幽灵文本的下一个单词 (Ctrl+Right 或 Alt+F)
     */
    fun acceptNextWordOfGhostText(): Boolean {
        if (ghostText.isNotEmpty()) {
            val chunk = TerminalLineEditor.extractNextWord(ghostText)
            if (chunk.isNotEmpty()) {
                val newText = inputState.text + chunk
                inputState = TextFieldValue(newText, selection = TextRange(newText.length))
                ghostText = ""
                updateGhostText(newText, newText.length)
                return true
            }
        }
        return false
    }

    /**
     * 关闭并重置自动补全候选栏
     */
    fun dismissCompletionBar() {
        isCompletionBarVisible = false
        completionCandidates.clear()
        activeCandidateIndex = -1
        baseInputText = ""
        baseParsedContext = null
    }

    /**
     * 选中并应用自动补全候选项
     */
    fun selectCandidate(candidate: CompletionCandidate) {
        val targetParsed = baseParsedContext ?: CompletionEngine.parseContext(
            inputState.text,
            inputState.selection.end
        )
        val targetOriginalText = baseInputText.ifEmpty { inputState.text }

        val (newText, newCursor) = CompletionEngine.applyCandidate(
            originalText = targetOriginalText,
            parsedContext = targetParsed,
            candidateToInsert = candidate.name,
            isDirectory = candidate.isDirectory
        )
        inputState = TextFieldValue(newText, selection = TextRange(newCursor))
        ghostText = ""
        updateGhostText(newText, newCursor)

        if (candidate.isDirectory) {
            // 目录补全：级联加载下一级子目录候选项
            viewModelScope.launch {
                baseInputText = newText
                baseParsedContext = CompletionEngine.parseContext(newText, newCursor)
                val nextResult = computeCompletions()
                if (nextResult.candidates.isNotEmpty()) {
                    completionCandidates.clear()
                    completionCandidates.addAll(nextResult.candidates)
                    activeCandidateIndex = -1
                    isCompletionBarVisible = true
                } else {
                    dismissCompletionBar()
                }
            }
        } else {
            dismissCompletionBar()
        }
    }

    /**
     * 在已打开的候选项列表中按顺序轮转切换焦点
     */
    private fun cycleCandidates() {
        if (completionCandidates.isEmpty()) return
        activeCandidateIndex = (activeCandidateIndex + 1) % completionCandidates.size
        val candidate = completionCandidates[activeCandidateIndex]

        val targetParsed = baseParsedContext ?: CompletionEngine.parseContext(
            inputState.text,
            inputState.selection.end
        )
        val targetOriginalText = baseInputText.ifEmpty { inputState.text }

        val (newText, newCursor) = CompletionEngine.applyCandidate(
            originalText = targetOriginalText,
            parsedContext = targetParsed,
            candidateToInsert = candidate.name,
            isDirectory = candidate.isDirectory
        )
        inputState = TextFieldValue(newText, selection = TextRange(newCursor))
        ghostText = ""
    }

    /**
     * Tab 键事件处理核心逻辑：
     * - 0. 若候选条当前已处于展示状态且已有候选项，继续按 Tab 视为切换焦点轮询候选
     * - 1. 异步计算匹配候选项：
     *      - 唯一匹配：直接补全替换并收起候选栏
     *      - 多个匹配：
     *          a. 先检查是否有可延伸的最长公共前缀 (LCP)，补全公共前缀
     *          b. 单击直接展开候选栏（Chips Bar），列出所有可能匹配的选项供点选或继续 Tab 轮询
     *      - 无匹配：若存在历史幽灵文本则采纳，否则收起候选栏
     */
    fun handleTabPress() {
        // 0. 若候选条当前已处于展示状态且已有候选项，继续按 Tab 视为切换焦点轮询候选
        if (isCompletionBarVisible && completionCandidates.isNotEmpty()) {
            cycleCandidates()
            return
        }

        // 1. 异步计算匹配候选
        viewModelScope.launch {
            val result = computeCompletions()
            if (result.candidates.isEmpty()) {
                if (ghostText.isNotEmpty()) {
                    acceptGhostText()
                } else {
                    dismissCompletionBar()
                }
                return@launch
            }

            if (result.isUniqueMatch) {
                // 唯一匹配：直接补全插入
                val candidate = result.candidates.first()
                val (newText, newCursor) = CompletionEngine.applyCandidate(
                    originalText = inputState.text,
                    parsedContext = result.parsedContext,
                    candidateToInsert = candidate.name,
                    isDirectory = candidate.isDirectory
                )
                inputState = TextFieldValue(newText, selection = TextRange(newCursor))
                ghostText = ""
                updateGhostText(newText, newCursor)
                dismissCompletionBar()
            } else {
                // 多个匹配：
                // A. 先检查是否有可延伸的最长公共前缀 (LCP)
                val lcp = result.longestCommonPrefix
                val currentPrefix = result.parsedContext.prefix
                val canExtendLcp = lcp.length > currentPrefix.length && lcp.startsWith(
                    currentPrefix,
                    ignoreCase = true
                )

                if (canExtendLcp) {
                    val (newText, newCursor) = CompletionEngine.applyCandidate(
                        originalText = inputState.text,
                        parsedContext = result.parsedContext,
                        candidateToInsert = lcp,
                        isDirectory = false,
                        isPartial = true
                    )
                    inputState = TextFieldValue(newText, selection = TextRange(newCursor))
                    ghostText = ""
                    updateGhostText(newText, newCursor)
                }

                // B. 单击 Tab 直接展开候选栏（Chips Bar）供点选或继续 Tab 轮询
                baseInputText = inputState.text
                baseParsedContext =
                    CompletionEngine.parseContext(inputState.text, inputState.selection.end)
                completionCandidates.clear()
                completionCandidates.addAll(result.candidates)
                activeCandidateIndex = -1
                isCompletionBarVisible = true
            }
        }
    }

    private suspend fun computeCompletions(): CompletionResult {
        val cursor = inputState.selection.end
        val text = inputState.text
        val parsed = CompletionEngine.parseContext(text, cursor)

        val registeredCommands = registry.commands.keys.toList()
        val commandFlagsMap = registry.commands.mapValues { entry ->
            entry.value.flags
        }

        val directoryFiles = if (parsed.contextType == CompletionContextType.PATH) {
            if (parsed.parentPath.isEmpty()) {
                context.listDirectory(currentCid)
            } else {
                val resolved = context.resolvePath(parsed.parentPath)
                if (resolved != null) {
                    context.listDirectory(resolved.first)
                } else {
                    emptyList()
                }
            }
        } else {
            emptyList()
        }

        return CompletionEngine.calculateCompletion(
            parsedContext = parsed,
            registeredCommands = registeredCommands,
            commandFlagsMap = commandFlagsMap,
            directoryFiles = directoryFiles
        )
    }

    /**
     * 执行命令提交 (Enter 回车)
     */
    fun submitInput() {
        val raw = inputState.text.trim()
        lastSubmittedText = inputState.text
        inputState = TextFieldValue("")
        ghostText = ""
        resetModifiers()
        dismissCompletionBar()

        if (isWaitingConfirmation) {
            appendTerminalLine(TerminalLine(raw, TerminalLineType.COMMAND))
            val isConfirmed =
                raw.equals("yes", ignoreCase = true) || raw.equals("y", ignoreCase = true)
            isWaitingConfirmation = false
            confirmDeferred?.complete(isConfirmed)
            confirmDeferred = null
            return
        }

        if (isExecuting) {
            // 已有命令在前台执行中，且非等待确认状态，忽略重复提交
            return
        }

        if (raw.isEmpty()) {
            appendTerminalLine(TerminalLine(promptText(), TerminalLineType.COMMAND))
            return
        }

        // 仅对语法合法且已注册的正确命令进行内存与持久化记录
        val isCommandValid = historyManager.isValidCommand(raw) { cmdName: String ->
            registry.hasCommand(cmdName)
        }

        if (isCommandValid) {
            if (history.lastOrNull() != raw) {
                history.add(raw)
            }
            historyPointer = -1
            viewModelScope.launch(Dispatchers.IO) {
                historyManager.appendCommand(raw)
            }
        }

        // 统一双行格式入屏：第 1 行完整路径上下文，第 2 行提示符与用户命令
        appendTerminalLine(TerminalLine("${contextPromptText()}\n$ $raw", TerminalLineType.COMMAND))

        isExecuting = true
        currentExecutionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val flow = engine.execute(raw, context)
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
                        withContext(Dispatchers.Main) {
                            appendTerminalLines(toAdd)
                        }
                    }
                }

                flow.collect { line ->
                    if (line == "__TERMINAL_CLEAR_SCREEN__") {
                        flushBuffer()
                        withContext(Dispatchers.Main) {
                            lines.clear()
                        }
                    } else if (line == "__TERMINAL_EXIT__") {
                        flushBuffer()
                        withContext(Dispatchers.Main) {
                            resetSession()
                            onExitAction?.invoke()
                        }
                    } else {
                        val lineType = if (line.startsWith("terminal: command not found") || line.contains(": error:")) {
                            TerminalLineType.ERROR
                        } else {
                            TerminalLineType.OUTPUT
                        }
                        buffer.add(TerminalLine(line, lineType))

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
                withContext(Dispatchers.Main) {
                    appendTerminalLine(TerminalLine("execution error: ${e.message}", TerminalLineType.ERROR))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isExecuting = false
                    currentExecutionJob = null
                }
                refreshCachedEntries(currentCid)
            }
        }
    }

    // --- Readline / Linux 风格快捷键处理 ---

    /**
     * Ctrl+C:
     * - 执行中：取消当前运行的协程 Job 并输出 "^C"
     * - 空闲中：带 "^C" 归档当前行到历史记录，另起新提示符
     */
    fun handleCtrlC() {
        resetModifiers()
        dismissCompletionBar()
        lastSubmittedText = ""
        if (isExecuting) {
            currentExecutionJob?.cancel()
            currentExecutionJob = null
            isExecuting = false
            appendTerminalLine(TerminalLine("^C", TerminalLineType.OUTPUT))
        } else {
            val raw = inputState.text
            appendTerminalLine(TerminalLine("${contextPromptText()}\n$ $raw^C", TerminalLineType.COMMAND))
            inputState = TextFieldValue("")
            ghostText = ""
        }
    }

    /**
     * Ctrl+U: 清除光标至行首
     */
    fun handleCtrlU() {
        resetModifiers()
        inputState = TerminalLineEditor.deleteToBeginning(inputState)
        updateGhostText(inputState.text, inputState.selection.end)
    }

    /**
     * Ctrl+K: 清除光标至行尾
     */
    fun handleCtrlK() {
        resetModifiers()
        inputState = TerminalLineEditor.deleteToEnd(inputState)
        updateGhostText(inputState.text, inputState.selection.end)
    }

    /**
     * Ctrl+W: 向前删除一个词
     */
    fun handleCtrlW() {
        resetModifiers()
        inputState = TerminalLineEditor.deleteWordBackward(inputState)
        updateGhostText(inputState.text, inputState.selection.end)
    }

    /**
     * Ctrl+L: 清屏
     */
    fun handleCtrlL() {
        resetModifiers()
        clearScreen()
    }

    /**
     * Ctrl+A: 光标移到行首
     */
    fun handleCtrlA() {
        resetModifiers()
        inputState = TerminalLineEditor.moveCursorHome(inputState)
        ghostText = ""
    }

    /**
     * Ctrl+E: 光标移到行尾
     */
    fun handleCtrlE() {
        resetModifiers()
        if (ghostText.isNotEmpty() && inputState.selection.end == inputState.text.length) {
            acceptGhostText()
        } else {
            inputState = TerminalLineEditor.moveCursorEnd(inputState)
            updateGhostText(inputState.text, inputState.selection.end)
        }
    }

    /**
     * Ctrl+D: 输入为空时退出终端；输入非空时向后删除一个字符
     */
    fun handleCtrlD(onExit: () -> Unit) {
        resetModifiers()
        if (inputState.text.isEmpty()) {
            onExit()
        } else {
            inputState = TerminalLineEditor.deleteCharacterForward(inputState)
            updateGhostText(inputState.text, inputState.selection.end)
        }
    }

    /**
     * Ctrl+Left: 光标向左跳跃一个单词
     */
    fun handleCtrlLeft() {
        resetModifiers()
        inputState = TerminalLineEditor.moveWordBackward(inputState)
        ghostText = ""
    }

    /**
     * Ctrl+Right: 光标向右跳跃一个单词（若光标在行尾且存在幽灵文本，则采纳幽灵文本的下一个单词）
     */
    fun handleCtrlRight() {
        resetModifiers()
        if (inputState.selection.end == inputState.text.length && ghostText.isNotEmpty()) {
            acceptNextWordOfGhostText()
        } else {
            inputState = TerminalLineEditor.moveWordForward(inputState)
            updateGhostText(inputState.text, inputState.selection.end)
        }
    }

    /**
     * Alt+B: 光标后退一个单词
     */
    fun handleAltB() {
        resetModifiers()
        inputState = TerminalLineEditor.moveWordBackward(inputState)
        ghostText = ""
    }

    /**
     * Alt+F: 光标前进一个单词（若光标在行尾且存在幽灵文本，则采纳幽灵文本的下一个单词）
     */
    fun handleAltF() {
        resetModifiers()
        if (inputState.selection.end == inputState.text.length && ghostText.isNotEmpty()) {
            acceptNextWordOfGhostText()
        } else {
            inputState = TerminalLineEditor.moveWordForward(inputState)
            updateGhostText(inputState.text, inputState.selection.end)
        }
    }

    /**
     * Alt+D: 向后删除一个单词
     */
    fun handleAltD() {
        resetModifiers()
        inputState = TerminalLineEditor.deleteWordForward(inputState)
        updateGhostText(inputState.text, inputState.selection.end)
    }

    /**
     * Alt+Backspace: 向前删除一个单词
     */
    fun handleAltBackspace() {
        resetModifiers()
        inputState = TerminalLineEditor.deleteWordBackward(inputState)
        updateGhostText(inputState.text, inputState.selection.end)
    }

    /**
     * Alt+.: 提取上一条历史命令的最后一个参数并插入
     */
    fun handleAltDot() {
        resetModifiers()
        val lastCmd = history.lastOrNull()
        val lastArg = TerminalLineEditor.extractLastArgument(lastCmd)
        if (!lastArg.isNullOrEmpty()) {
            inputState = TerminalLineEditor.insertTextAtCursor(inputState, lastArg)
            updateGhostText(inputState.text, inputState.selection.end)
        }
    }

    // 历史命令漫游 (↑ / ↓)
    fun navigateHistoryUp() {
        if (history.isEmpty()) return
        if (historyPointer == -1) {
            savedDraftInput = inputState.text
            historyPointer = history.size - 1
        } else if (historyPointer > 0) {
            historyPointer--
        }
        val target = history[historyPointer]
        inputState = TextFieldValue(target, selection = TextRange(target.length))
        ghostText = ""
    }

    fun navigateHistoryDown() {
        if (historyPointer == -1) return
        if (historyPointer < history.size - 1) {
            historyPointer++
            val target = history[historyPointer]
            inputState = TextFieldValue(target, selection = TextRange(target.length))
        } else {
            historyPointer = -1
            inputState =
                TextFieldValue(savedDraftInput, selection = TextRange(savedDraftInput.length))
        }
        ghostText = ""
    }

    // 悬浮工具栏按键动作
    fun insertCharacter(char: String) {
        inputState = TerminalLineEditor.insertTextAtCursor(inputState, char)
        updateGhostText(inputState.text, inputState.selection.end)
    }

    fun moveCursorHome() {
        handleCtrlA()
    }

    fun moveCursorEnd() {
        handleCtrlE()
    }

    fun moveCursorLeft() {
        if (isCtrlActive) {
            handleCtrlLeft()
            return
        }
        if (isAltActive) {
            handleAltB()
            return
        }
        inputState = TerminalLineEditor.moveCursorLeft(inputState)
        ghostText = ""
    }

    fun moveCursorRight() {
        if (isCtrlActive) {
            handleCtrlRight()
            return
        }
        if (isAltActive) {
            handleAltF()
            return
        }
        if (inputState.selection.end == inputState.text.length && ghostText.isNotEmpty()) {
            acceptGhostText()
            return
        }
        inputState = TerminalLineEditor.moveCursorRight(inputState)
        updateGhostText(inputState.text, inputState.selection.end)
    }

    fun clearScreen() {
        dismissCompletionBar()
        lines.clear()
    }

    fun getAllTerminalText(): String {
        return lines.joinToString("\n") { it.text }
    }
}
