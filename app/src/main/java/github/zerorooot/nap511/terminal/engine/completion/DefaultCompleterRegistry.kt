package github.zerorooot.nap511.terminal.engine.completion

/**
 * 默认补全器注册查找表 (Default Completer Registry)
 *
 * 为补全引擎提供基于命令名称的快速回退与兜底解析能力，
 * 确保在未显式传递特定命令实例或独立测试场景下，依然能准确映射到标准补全器。
 */
object DefaultCompleterRegistry {

    private val COMPLETER_MAP: Map<String, CommandCompleter> = mapOf(
        "cd" to StandardCompleter.DIRECTORY_ONLY,
        "cat" to StandardCompleter.TEXT_FILES,
        "head" to StandardCompleter.TEXT_FILES,
        "tail" to StandardCompleter.TEXT_FILES,
        "wc" to StandardCompleter.TEXT_FILES,
        "sort" to StandardCompleter.TEXT_FILES,
        "unzip" to StandardCompleter.ARCHIVE_FILES,
        "echo" to StandardCompleter.NONE,
        "clear" to StandardCompleter.NONE,
        "exit" to StandardCompleter.NONE,
        "pwd" to StandardCompleter.NONE,
        "history" to StandardCompleter.NONE
    )

    /**
     * 根据命令名称获取对应的默认补全器，若未匹配则默认返回 [StandardCompleter.ALL]
     */
    fun findCompleter(commandName: String): CommandCompleter {
        return COMPLETER_MAP[commandName.lowercase()] ?: StandardCompleter.ALL
    }

    /**
     * 根据命令名称与参数位置获取对应的路径过滤器
     */
    fun findFilter(commandName: String, argIndex: Int): PathFilter {
        return findCompleter(commandName).getPathFilter(argIndex)
    }
}
