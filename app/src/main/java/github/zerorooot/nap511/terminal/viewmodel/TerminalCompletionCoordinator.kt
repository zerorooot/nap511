package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import github.zerorooot.nap511.terminal.engine.CompletionCandidate
import github.zerorooot.nap511.terminal.engine.CompletionEngine
import github.zerorooot.nap511.terminal.engine.ParsedContext

/**
 * 终端自动补全候选栏状态与协调器 (Terminal Completion Coordinator)
 *
 * 职责：
 * 1. 管理自动补全候选条 UI 状态（候选列表、可见性、当前高亮索引）。
 * 2. 维护基准输入与解析上下文锚点（用于在多候选轮换及公共前缀延展时进行稳定替换）。
 * 3. 封装候选项的应用计算与顺序轮换 (Cycling)。
 * 4. 纯状态机逻辑，与 Android ViewModel 生命周期解耦，便于单独复用和单测。
 */
class TerminalCompletionCoordinator {

    /**
     * 自动补全候选列表，由 Compose Snapshot 系统直接观察
     */
    val candidates: SnapshotStateList<CompletionCandidate> = mutableStateListOf()

    /**
     * 候选栏当前是否对用户可见
     */
    var isVisible: Boolean by mutableStateOf(false)
        private set

    /**
     * 当前选中的候选条目索引 (-1 表示未高亮选中任何条目)
     */
    var activeIndex: Int by mutableIntStateOf(-1)
        private set

    private var baseInputText = ""
    private var baseParsedContext: ParsedContext? = null

    /**
     * 关闭并重置自动补全候选栏
     */
    fun dismiss() {
        isVisible = false
        candidates.clear()
        activeIndex = -1
        baseInputText = ""
        baseParsedContext = null
    }

    /**
     * 展现新的候选列表并锁定上下文基准点
     */
    fun show(newCandidates: List<CompletionCandidate>, currentText: String, cursor: Int) {
        baseInputText = currentText
        baseParsedContext = CompletionEngine.parseContext(currentText, cursor)
        candidates.clear()
        candidates.addAll(newCandidates)
        activeIndex = -1
        isVisible = true
    }

    /**
     * 为级联子目录设置基准锚点
     */
    fun setCascadeAnchor(newText: String, newCursor: Int) {
        baseInputText = newText
        baseParsedContext = CompletionEngine.parseContext(newText, newCursor)
    }

    /**
     * 仅更新候选列表（如级联子目录加载完毕后），保留已设定的锚点
     */
    fun updateCandidates(newCandidates: List<CompletionCandidate>) {
        candidates.clear()
        candidates.addAll(newCandidates)
        activeIndex = -1
        isVisible = true
    }

    /**
     * 在已打开的候选项列表中按顺序轮转切换焦点，并返回替换后的文本与光标位置
     *
     * @param currentText 当前输入文本
     * @param cursor 当前光标位置
     * @return 替换后的 (新文本, 新光标位置)；若无候选项则返回 null
     */
    fun cycle(currentText: String, cursor: Int): Pair<String, Int>? {
        if (candidates.isEmpty()) return null
        activeIndex = (activeIndex + 1) % candidates.size
        val candidate = candidates[activeIndex]

        val targetParsed = baseParsedContext ?: CompletionEngine.parseContext(currentText, cursor)
        val targetOriginalText = baseInputText.ifEmpty { currentText }

        return CompletionEngine.applyCandidate(
            originalText = targetOriginalText,
            parsedContext = targetParsed,
            candidateToInsert = candidate.name,
            isDirectory = candidate.isDirectory
        )
    }

    /**
     * 选中并应用候选项，计算替换后的新文本与新光标位置
     *
     * @param candidate 选中的候选项
     * @param currentText 当前输入文本
     * @param cursor 当前光标位置
     * @return (新文本, 新光标位置)
     */
    fun applySelected(
        candidate: CompletionCandidate,
        currentText: String,
        cursor: Int
    ): Pair<String, Int> {
        val targetParsed = baseParsedContext ?: CompletionEngine.parseContext(currentText, cursor)
        val targetOriginalText = baseInputText.ifEmpty { currentText }

        return CompletionEngine.applyCandidate(
            originalText = targetOriginalText,
            parsedContext = targetParsed,
            candidateToInsert = candidate.name,
            isDirectory = candidate.isDirectory
        )
    }
}
