package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.commands.CommandRegistryFactory
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.context.TerminalPath
import github.zerorooot.nap511.terminal.context.currentCid
import github.zerorooot.nap511.terminal.context.toDisplayPath
import github.zerorooot.nap511.terminal.engine.AutosuggestionEngine
import github.zerorooot.nap511.terminal.engine.CompletionCandidate
import github.zerorooot.nap511.terminal.engine.CompletionContextType
import github.zerorooot.nap511.terminal.engine.CompletionEngine
import github.zerorooot.nap511.terminal.engine.CompletionResult
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import github.zerorooot.nap511.terminal.engine.TerminalLineEditor
import github.zerorooot.nap511.util.FileOpener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 终端核心 ViewModel (TerminalViewModel)
 *
 * 采用外观模式 (Facade Pattern) 与组件化委托设计：
 * 将原本单一庞大的终端逻辑分解为高内聚、低耦合的专业子组件：
 * 1. [screenBuffer] ([TerminalScreenBuffer])：管理展示行缓冲区与回滚上限截断。
 * 2. [modifiers] ([TerminalKeyModifiers])：管理 Termux 风格粘滞修饰键 (Ctrl / Alt) 状态机与按键分发。
 * 3. [historyNavigator] ([TerminalHistoryNavigator])：管理命令历史栈漫游与输入草稿暂存。
 * 4. [completionCoordinator] ([TerminalCompletionCoordinator])：管理补全候选栏状态与候选轮询/级联。
 * 5. [commandExecutor] ([TerminalCommandExecutor])：管理管道命令协程执行、微批次刷屏与确认挂起。
 *
 * 本 ViewModel 负责统筹以上子组件，对外保持 100% 的公开 API 与 Compose 状态观察兼容。
 */
class TerminalViewModel(
    initialPathList: List<PathBean> = emptyList(),
    avatarBean: AvatarBean = AvatarBean(),
    fileOpener: FileOpener? = null,
    fileRepository: FileRepository = FileRepository.getInstance(),
    private val mainDispatcher: CoroutineDispatcher? = null
) : ViewModel() {
    private val uiDispatcher: CoroutineDispatcher
        get() = mainDispatcher ?: runCatching { Dispatchers.Main }.getOrDefault(Dispatchers.Default)

    private val ioDispatcher: CoroutineDispatcher
        get() = mainDispatcher ?: Dispatchers.IO

    // --- 独立高内聚子组件装配 ---
    val screenBuffer = TerminalScreenBuffer(TerminalScreenBuffer.DEFAULT_MAX_SCROLLBACK_LINES)
    val modifiers = TerminalKeyModifiers()
    val historyNavigator = TerminalHistoryNavigator()
    val completionCoordinator = TerminalCompletionCoordinator()

    /**
     * 响应式终端屏幕输出行列表，供 Compose 直接观察，保持向下兼容
     */
    val lines: SnapshotStateList<TerminalLine> = screenBuffer.lines

    var avatarBean by mutableStateOf(avatarBean)
    var fileOpener: FileOpener? by mutableStateOf(fileOpener)
        private set

    fun updateFileOpener(opener: FileOpener?) {
        this.fileOpener = opener
        context.fileOpener = opener
    }

    var isSessionInitialized = false
        private set

    /**
     * 根据全局面包屑路径链表初始化工作目录
     */
    fun initDirectoryIfNeeded(pathList: List<PathBean>) {
        if (!isSessionInitialized) {
            isSessionInitialized = true
            currentCid = pathList.currentCid()
            currentPath = pathList.toDisplayPath()
            context.updateDirectory(pathList)
            screenBuffer.clear()
            printWelcomeBanner()
            refreshCachedEntries(currentCid)
        }
    }

    fun resetSession() {
        isSessionInitialized = false
        screenBuffer.clear()
        inputState = TextFieldValue("")
        ghostText = ""
        lastSubmittedText = ""
        resetModifiers()
        dismissCompletionBar()
        historyNavigator.resetPointer()
    }

    var onExitAction: (() -> Unit)? = null

    // Termux 风格粘滞修饰键状态 (CTRL / ALT)
    val isCtrlActive: Boolean get() = modifiers.isCtrlActive
    val isAltActive: Boolean get() = modifiers.isAltActive

    fun toggleCtrl() = modifiers.toggleCtrl()
    fun toggleAlt() = modifiers.toggleAlt()
    fun resetModifiers() = modifiers.reset()

    var inputState by mutableStateOf(TextFieldValue(""))
        private set

    private var lastSubmittedText = ""

    var ghostText by mutableStateOf("")
        private set

    val isExecuting: Boolean get() = commandExecutor.isExecuting

    var currentPath: String by mutableStateOf(initialPathList.toDisplayPath())
        private set

    var currentCid: String by mutableStateOf(initialPathList.currentCid())
        private set

    val isWaitingConfirmation: Boolean get() = commandExecutor.isWaitingConfirmation

    // 自动补全候选条状态
    val completionCandidates: SnapshotStateList<CompletionCandidate> = completionCoordinator.candidates
    val isCompletionBarVisible: Boolean get() = completionCoordinator.isVisible
    val activeCandidateIndex: Int get() = completionCoordinator.activeIndex

    // 缓存当前目录下的文件名，用于快速预测补全
    private val cachedDirectoryEntries = mutableListOf<String>()

    val context = TerminalContext(
        initialPathList = initialPathList,
        fileRepository = fileRepository,
        onConfirmRequest = { prompt ->
            commandExecutor.requestConfirmation(prompt)
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
            historyNavigator.clear()
        }
    )
    private val engine = PipelineEngine(registry)

    private val commandExecutor = TerminalCommandExecutor(
        engine = engine,
        screenBuffer = screenBuffer,
        uiDispatcher = uiDispatcher,
        ioDispatcher = ioDispatcher
    )

    init {
        // 打印终端欢迎信息与快捷指引
        printWelcomeBanner()
        refreshCachedEntries(currentCid)
        loadPersistentHistory()
    }

    private fun loadPersistentHistory() {
        viewModelScope.launch(ioDispatcher) {
            val loaded = historyManager.loadRecentHistory(1000)
            withContext(uiDispatcher) {
                historyNavigator.load(loaded)
                updateGhostText(inputState.text, inputState.selection.end)
            }
        }
    }

    companion object {
        /**
         * 终端屏幕输出最大保留行数上限 (Scrollback Limit)
         * 避免长时间运行或海量输出导致内存暴涨与掉帧
         */
        const val MAX_SCROLLBACK_LINES = TerminalScreenBuffer.DEFAULT_MAX_SCROLLBACK_LINES
    }

    /**
     * 安全向终端输出追加单行，带最大回滚行数截断保护，防止长期运行导致内存膨胀
     */
    fun appendTerminalLine(line: TerminalLine) {
        screenBuffer.appendLine(line)
    }

    /**
     * 批量追加终端输出，降低 Compose 重组频率，保证海量输出流畅度
     */
    fun appendTerminalLines(newLines: List<TerminalLine>) {
        screenBuffer.appendLines(newLines)
    }

    private fun printWelcomeBanner() {
        appendTerminalLine(TerminalLine("=== 115 Cloud Terminal (nap511) ===", TerminalLineType.System.INFO))
        appendTerminalLine(
            TerminalLine(
                "欢迎使用网盘终端！输入 '?' 'help' 或 'man' 可查看命令列表与快捷键指南。终端尚不稳定，目前还在测试中",
                TerminalLineType.System.INFO
            )
        )
        appendTerminalLine(
            TerminalLine(
                "提示：支持管道 '|' 与通配符；悬浮栏已内置 CTRL / ALT 粘滞键与常用 Readline 快捷键。",
                TerminalLineType.System.INFO
            )
        )
        appendTerminalLine(
            TerminalLine(
                "当前工作目录: $currentPath (cid: $currentCid)\n",
                TerminalLineType.System.INFO
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
            synchronized(cachedDirectoryEntries) {
                cachedDirectoryEntries.clear()
                cachedDirectoryEntries.addAll(entries)
            }
            withContext(uiDispatcher) {
                updateGhostText(inputState.text, inputState.selection.end)
            }
        }
    }

    /**
     * 修饰键动作委托派发契约实现
     */
    private val modifierActionHandler = object : TerminalModifierActionHandler {
        override fun onCtrlC() = handleCtrlC()
        override fun onCtrlU() = handleCtrlU()
        override fun onCtrlK() = handleCtrlK()
        override fun onCtrlW() = handleCtrlW()
        override fun onCtrlL() = handleCtrlL()
        override fun onCtrlA() = handleCtrlA()
        override fun onCtrlE() = handleCtrlE()
        override fun onCtrlD() = handleCtrlD(onExitAction ?: {})
        override fun onAltB() = handleAltB()
        override fun onAltF() = handleAltF()
        override fun onAltD() = handleAltD()
        override fun onAltBackspace() = handleAltBackspace()
        override fun onAltDot() = handleAltDot()
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
                    val handled = modifiers.dispatchChar(typedChar, modifierActionHandler)
                    if (handled) return
                }
            }

            // 2. 软键盘退格删除（Alt + Backspace 组合场景）
            if (isAltActive && newText.length < oldText.length) {
                val handled = modifiers.dispatchBackspace { handleAltBackspace() }
                if (handled) return
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

    private fun updateGhostText(text: String, cursor: Int) {
        if (isWaitingConfirmation) {
            ghostText = ""
            return
        }
        val currentEntries = synchronized(cachedDirectoryEntries) { cachedDirectoryEntries.toList() }
        ghostText = AutosuggestionEngine.calculateGhostText(
            input = text,
            cursorPosition = cursor,
            registeredCommands = registry.commands.keys.toList(),
            directoryEntries = currentEntries,
            history = historyNavigator.memoryHistory
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
        completionCoordinator.dismiss()
    }

    /**
     * 选中并应用自动补全候选项
     */
    fun selectCandidate(candidate: CompletionCandidate) {
        val (newText, newCursor) = completionCoordinator.applySelected(
            candidate = candidate,
            currentText = inputState.text,
            cursor = inputState.selection.end
        )
        inputState = TextFieldValue(newText, selection = TextRange(newCursor))
        ghostText = ""
        updateGhostText(newText, newCursor)

        if (candidate.isDirectory) {
            // 目录补全：级联加载下一级子目录候选项
            viewModelScope.launch(uiDispatcher) {
                completionCoordinator.setCascadeAnchor(newText, newCursor)
                val nextResult = computeCompletions()
                if (nextResult.candidates.isNotEmpty()) {
                    completionCoordinator.updateCandidates(nextResult.candidates)
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
        val cycled = completionCoordinator.cycle(inputState.text, inputState.selection.end)
        if (cycled != null) {
            val (newText, newCursor) = cycled
            inputState = TextFieldValue(newText, selection = TextRange(newCursor))
            ghostText = ""
        }
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
        viewModelScope.launch(uiDispatcher) {
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
                completionCoordinator.show(result.candidates, inputState.text, inputState.selection.end)
            }
        }
    }

    internal suspend fun computeCompletions(): CompletionResult {
        val cursor = inputState.selection.end
        val text = inputState.text
        val parsed = CompletionEngine.parseContext(text, cursor)

        val registeredCommands = registry.commands.keys.toList()
        val commandFlagsMap = registry.commands.mapValues { entry ->
            entry.value.flags
        }

        val directoryFiles = if (parsed.contextType == CompletionContextType.PATH) {
            if (parsed.commandName == "trash") {
                // 回收站管理专属智能预测：拉取回收站中的真实待清理文件列表供补全与还原
                runCatching {
                    context.fileRepository.recycleList().recycleBeanList.map { item ->
                        FileBean(
                            name = item.fileName,
                            fileId = item.id,
                            size = item.fileSize,
                            isFolder = item.isFolder
                        )
                    }
                }.getOrDefault(emptyList())
            } else if (parsed.parentPath.isEmpty()) {
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
            commandExecutor.resolveConfirmation(raw)
            return
        }

        if (isExecuting) {
            // 已有命令在前台执行中，且非等待确认状态，忽略重复提交
            return
        }

        if (raw.isEmpty()) {
            historyNavigator.resetPointer()
            appendTerminalLine(TerminalLine(promptText(), TerminalLineType.System.COMMAND))
            return
        }

        // 仅对语法合法且已注册的正确命令进行内存与持久化记录
        val isCommandValid = historyManager.isValidCommand(raw) { cmdName: String ->
            registry.hasCommand(cmdName)
        }

        if (isCommandValid) {
            historyNavigator.add(raw)
            viewModelScope.launch(Dispatchers.IO) {
                historyManager.appendCommand(raw)
            }
        } else {
            historyNavigator.resetPointer()
        }

        // 统一双行格式入屏：第 1 行完整路径上下文，第 2 行提示符与用户命令
        appendTerminalLine(TerminalLine("${contextPromptText()}\n$ $raw", TerminalLineType.System.COMMAND))

        commandExecutor.execute(
            scope = viewModelScope,
            command = raw,
            context = context,
            onExit = {
                resetSession()
                onExitAction?.invoke()
            },
            onComplete = {
                refreshCachedEntries(currentCid)
            }
        )
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
        historyNavigator.resetPointer()
        lastSubmittedText = ""
        if (commandExecutor.cancelExecution()) {
            appendTerminalLine(TerminalLine("^C", TerminalLineType.Output.TEXT))
        } else {
            val raw = inputState.text
            appendTerminalLine(TerminalLine("${contextPromptText()}\n$ $raw^C", TerminalLineType.System.COMMAND))
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
        val lastCmd = historyNavigator.lastCommand
        val lastArg = TerminalLineEditor.extractLastArgument(lastCmd)
        if (!lastArg.isNullOrEmpty()) {
            inputState = TerminalLineEditor.insertTextAtCursor(inputState, lastArg)
            updateGhostText(inputState.text, inputState.selection.end)
        }
    }

    // 历史命令漫游 (↑ / ↓)
    fun navigateHistoryUp() {
        val target = historyNavigator.navigateUp(inputState.text)
        if (target != null) {
            inputState = TextFieldValue(target, selection = TextRange(target.length))
            ghostText = ""
        }
    }

    fun navigateHistoryDown() {
        val target = historyNavigator.navigateDown()
        if (target != null) {
            inputState = TextFieldValue(target, selection = TextRange(target.length))
            ghostText = ""
        }
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
        screenBuffer.clear()
    }

    fun getAllTerminalText(): String {
        return screenBuffer.getAllText()
    }
}
