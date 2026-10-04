package github.zerorooot.nap511.terminal.engine

/**
 * Fish-shell 风格的行内幽灵文本（Inline Ghost Text）预测计算引擎
 */
object AutosuggestionEngine {

    /**
     * 计算幽灵文本提示后缀（仅当光标位于文本末尾时生效）
     * @param input 当前输入的文本
     * @param cursorPosition 当前光标位置
     * @param registeredCommands 系统已注册的所有命令名列表
     * @param directoryEntries 当前工作目录下的文件及文件夹名称列表
     * @param history 执行过的历史命令列表（最近执行的在最后）
     * @return 预测的补全文本后缀（不含用户已输入的文本），若无建议则返回空字符串
     */
    fun calculateGhostText(
        input: String,
        cursorPosition: Int,
        registeredCommands: List<String>,
        directoryEntries: List<String>,
        history: List<String>
    ): String {
        if (input.isEmpty() || cursorPosition != input.length) {
            return ""
        }

        // 1. 优先从最近的历史记录中反向查找匹配完整前缀的命令
        val historyMatch = history.asReversed().firstOrNull { it.startsWith(input) && it.length > input.length }
        if (historyMatch != null) {
            return historyMatch.substring(input.length)
        }

        // 2. 如果输入中无空格或管道，表示用户正在输入命令名称
        if (!input.contains(' ') && !input.contains('|')) {
            val cmdMatch = registeredCommands
                .filter { it.startsWith(input) && it.length > input.length }
                .minByOrNull { it.length }
            if (cmdMatch != null) {
                return cmdMatch.substring(input.length)
            }
        }

        // 3. 用户正在输入参数（如文件路径）
        // 提取最后一个 Token
        val lastToken = input.substringAfterLast(' ')
        if (lastToken.isNotEmpty() && !lastToken.startsWith("-")) {
            val cleanToken = lastToken.trimStart('\'', '"')
            val fileMatch = directoryEntries
                .filter { it.startsWith(cleanToken, ignoreCase = true) && it.length > cleanToken.length }
                .minByOrNull { it.length }

            if (fileMatch != null) {
                val suffix = fileMatch.substring(cleanToken.length)
                // 如果原始未闭合引号，则补全后自动补闭合引号
                return suffix
            }
        }

        return ""
    }
}
