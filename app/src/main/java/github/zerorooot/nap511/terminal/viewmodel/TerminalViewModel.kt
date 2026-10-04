package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.runtime.getValue
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
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TerminalViewModel(
    initialCid: String = "0",
    initialPath: String = "/",
    initialPathList: List<PathBean> = emptyList(),
    val avatarBean: AvatarBean,
    private val onNavigateAction: ((Route) -> Unit)? = null
) : ViewModel() {
    val lines = mutableStateListOf<TerminalLine>()
    private val history = mutableListOf<String>()
    private var historyPointer = -1
    private var savedDraftInput = ""

    var inputState by mutableStateOf(TextFieldValue(""))
        private set

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

    // 缓存当前目录下的文件名，用于快速预测补全
    private val cachedDirectoryEntries = mutableListOf<String>()

    val context = TerminalContext(
        initialCid = initialCid,
        initialPath = initialPath,
        initialPathList = initialPathList,
        onNavigate = { route -> onNavigateAction?.invoke(route) },
        onConfirmRequest = { prompt ->
            isWaitingConfirmation = true
            lines.add(TerminalLine(prompt, TerminalLineType.PROMPT))
            val deferred = CompletableDeferred<Boolean>()
            confirmDeferred = deferred
            deferred.await()
        },
        onDirectoryChanged = { cid, path ->
            currentCid = cid
            currentPath = path
            refreshCachedEntries(cid)
        }
    )

    private val registry = CommandRegistryFactory.createDefaultRegistry { history }
    private val engine = PipelineEngine(registry)

    init {
        // 打印终端欢迎信息与快捷指引
        printWelcomeBanner()
        refreshCachedEntries(initialCid)
    }

    private fun printWelcomeBanner() {
        lines.add(TerminalLine("=== 115 Cloud Terminal (nap511) ===", TerminalLineType.SYSTEM))
        lines.add(
            TerminalLine(
                "欢迎使用网盘极客终端！输入 '?' 或 'help' 可查看全部支持的命令。",
                TerminalLineType.SYSTEM
            )
        )
        lines.add(
            TerminalLine(
                "提示：每个命令后面加 '-h' 可输出参数详情；支持 '|' 管道与通配符。",
                TerminalLineType.SYSTEM
            )
        )
        lines.add(
            TerminalLine(
                "当前工作目录: $currentPath (cid: $currentCid)\n",
                TerminalLineType.SYSTEM
            )
        )
    }

    fun promptText(): String {
        return "${avatarBean.userName}@${avatarBean.userId}:$currentPath$ "
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
        inputState = newValue
        updateGhostText(newValue.text, newValue.selection.end)
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
     * 执行命令提交 (Enter 回车)
     */
    fun submitInput() {
        val raw = inputState.text.trim()
        inputState = TextFieldValue("")
        ghostText = ""

        if (isWaitingConfirmation) {
            lines.add(TerminalLine(raw, TerminalLineType.COMMAND))
            val isConfirmed =
                raw.equals("yes", ignoreCase = true) || raw.equals("y", ignoreCase = true)
            isWaitingConfirmation = false
            confirmDeferred?.complete(isConfirmed)
            confirmDeferred = null
            return
        }

        if (raw.isEmpty()) {
            lines.add(TerminalLine(promptText(), TerminalLineType.COMMAND))
            return
        }

        // 记录历史
        if (history.lastOrNull() != raw) {
            history.add(raw)
        }
        historyPointer = -1

        lines.add(TerminalLine("${promptText()}$raw", TerminalLineType.COMMAND))

        isExecuting = true
        viewModelScope.launch {
            try {
                val flow = engine.execute(raw, context)
                flow.collect { line ->
                    if (line == "__TERMINAL_CLEAR_SCREEN__") {
                        lines.clear()
                    } else if (line.startsWith("terminal: command not found") || line.contains(": error:")) {
                        lines.add(TerminalLine(line, TerminalLineType.ERROR))
                    } else {
                        lines.add(TerminalLine(line, TerminalLineType.OUTPUT))
                    }
                }
            } catch (e: Exception) {
                lines.add(TerminalLine("execution error: ${e.message}", TerminalLineType.ERROR))
            } finally {
                isExecuting = false
                refreshCachedEntries(currentCid)
            }
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
        val current = inputState.text
        val cursor = inputState.selection.start.coerceIn(0, current.length)
        val newText = current.substring(0, cursor) + char + current.substring(cursor)
        val newCursor = cursor + char.length
        inputState = TextFieldValue(newText, selection = TextRange(newCursor))
        updateGhostText(newText, newCursor)
    }

    fun moveCursorHome() {
        inputState = inputState.copy(selection = TextRange(0))
        ghostText = ""
    }

    fun moveCursorEnd() {
        val len = inputState.text.length
        // 如果末尾且有幽灵文本，应直接采纳幽灵文本
        if (inputState.selection.end == len && ghostText.isNotEmpty()) {
            acceptGhostText()
            return
        }
        inputState = inputState.copy(selection = TextRange(len))
        updateGhostText(inputState.text, len)
    }

    fun moveCursorLeft() {
        val cursor = (inputState.selection.start - 1).coerceAtLeast(0)
        inputState = inputState.copy(selection = TextRange(cursor))
        ghostText = ""
    }

    fun moveCursorRight() {
        // 如果在最末尾且有幽灵文本，直接采纳幽灵文本
        if (inputState.selection.end == inputState.text.length && ghostText.isNotEmpty()) {
            acceptGhostText()
            return
        }
        val cursor = (inputState.selection.start + 1).coerceAtMost(inputState.text.length)
        inputState = inputState.copy(selection = TextRange(cursor))
        updateGhostText(inputState.text, cursor)
    }

    fun clearScreen() {
        lines.clear()
    }

    fun getAllTerminalText(): String {
        return lines.joinToString("\n") { it.text }
    }
}
