package github.zerorooot.nap511.terminal.engine.completion

/**
 * 默认补全器注册查找表 (Default Completer Registry)
 *
 * 为补全引擎提供基于命令名称的快速回退与兜底解析能力，
 * 确保在未显式传递特定命令实例或独立测试场景下，依然能准确映射到标准补全器。
 */
object DefaultCompleterRegistry {

    private val COMPLETER_MAP: Map<String, CommandCompleter> = mapOf(
        "cd" to StandardCompleters.DIRECTORY_ONLY,
        "cat" to StandardCompleters.TEXT_FILES,
        "head" to StandardCompleters.TEXT_FILES,
        "tail" to StandardCompleters.TEXT_FILES,
        "wc" to StandardCompleters.TEXT_FILES,
        "sort" to StandardCompleters.TEXT_FILES,
        "unzip" to StandardCompleters.ARCHIVE_FILES,
        "echo" to StandardCompleters.NONE,
        "clear" to StandardCompleters.NONE,
        "exit" to StandardCompleters.NONE,
        "pwd" to StandardCompleters.NONE,
        "history" to StandardCompleters.NONE
    )

    /**
     * 根据命令名称获取对应的默认补全器，若未匹配则默认返回 [StandardCompleters.ALL]
     */
    fun findCompleter(commandName: String): CommandCompleter {
        return COMPLETER_MAP[commandName.lowercase()] ?: StandardCompleters.ALL
    }

    /**
     * 根据命令名称与参数位置获取对应的路径过滤器
     */
    fun findFilter(commandName: String, argIndex: Int): PathFilter {
        return findCompleter(commandName).getPathFilter(argIndex)
    }
}
