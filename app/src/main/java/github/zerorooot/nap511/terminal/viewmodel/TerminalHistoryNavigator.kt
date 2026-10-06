package github.zerorooot.nap511.terminal.viewmodel

/**
 * 终端命令历史漫游与草稿暂存管理器 (Terminal History Navigator)
 *
 * 职责：
 * 1. 管理内存中的命令历史列表 (`history`) 与导航指针 (`historyPointer`)。
 * 2. 在首次向上漫游历史时自动保存用户当前未提交的输入草稿 (`savedDraftInput`)。
 * 3. 向上 (`↑`) 与向下 (`↓`) 漫游历史命令，到底部时自动恢复用户草稿。
 * 4. 提供新增历史命令自动相邻去重与指针复位。
 * 5. 完全解耦 Compose 与 ViewModel，纯状态机设计，便于单独复用和单元测试。
 */
class TerminalHistoryNavigator {

    private val history = mutableListOf<String>()

    /**
     * 漫游指针：-1 表示当前未处于历史漫游状态（处于草稿行编辑态）
     */
    var historyPointer: Int = -1
        private set

    /**
     * 用户进入历史漫游前正在编辑的草稿暂存
     */
    var savedDraftInput: String = ""
        private set

    /**
     * 只读获取当前内存中的全部历史列表副本（供 GhostText 预测等消费）
     */
    val memoryHistory: List<String>
        get() = synchronized(history) { history.toList() }

    /**
     * 获取上一条提交的历史命令（供 Alt+. 参数提取等消费）
     */
    val lastCommand: String?
        get() = synchronized(history) { history.lastOrNull() }

    /**
     * 历史记录是否为空
     */
    val isEmpty: Boolean
        get() = synchronized(history) { history.isEmpty() }

    /**
     * 从持久化存储全量装载近期历史命令
     */
    fun load(items: List<String>) {
        synchronized(history) {
            history.clear()
            history.addAll(items)
            historyPointer = -1
        }
    }

    /**
     * 追加新的有效命令（相邻命令自动去重）
     */
    fun add(command: String) {
        synchronized(history) {
            if (history.lastOrNull() != command) {
                history.add(command)
            }
            historyPointer = -1
            savedDraftInput = ""
        }
    }

    /**
     * 重置漫游游标回位到编辑行，并清除暂存草稿
     */
    fun resetPointer() {
        synchronized(history) {
            historyPointer = -1
            savedDraftInput = ""
        }
    }

    /**
     * 清空内存中的所有历史与游标状态
     */
    fun clear() {
        synchronized(history) {
            history.clear()
            historyPointer = -1
            savedDraftInput = ""
        }
    }

    /**
     * 向上漫游历史命令 (Up Arrow)
     *
     * @param currentInput 当前输入框内容，首次向上翻时将作为草稿保存
     * @return 目标历史命令文本；若当前无历史命令则返回 null
     */
    fun navigateUp(currentInput: String): String? {
        synchronized(history) {
            if (history.isEmpty()) return null
            if (historyPointer == -1) {
                savedDraftInput = currentInput
                historyPointer = history.size - 1
            } else if (historyPointer > 0) {
                historyPointer--
            }
            return history.getOrNull(historyPointer)
        }
    }

    /**
     * 向下漫游历史命令 (Down Arrow)
     *
     * @return 目标历史命令文本，或在回到底部时返回已暂存的用户草稿；若当前未在漫游中则返回 null
     */
    fun navigateDown(): String? {
        synchronized(history) {
            if (historyPointer == -1) return null
            return if (historyPointer < history.size - 1) {
                historyPointer++
                history.getOrNull(historyPointer)
            } else {
                historyPointer = -1
                savedDraftInput
            }
        }
    }
}
