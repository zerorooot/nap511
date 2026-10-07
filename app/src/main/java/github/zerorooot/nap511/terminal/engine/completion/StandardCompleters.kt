package github.zerorooot.nap511.terminal.engine.completion

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.ParsedContext

/**
 * 常用标准补全器单例集合
 */
object StandardCompleters {

    /** 全量文件与目录（默认补全器，适用于 ls, rm, mv, stat, open, find 等） */
    val ALL: CommandCompleter = object : CommandCompleter {
        override fun getPathFilter(argIndex: Int): PathFilter = FileFilters.ALL
    }

    /** 仅目录补全器（适用于 cd） */
    val DIRECTORY_ONLY: CommandCompleter = object : CommandCompleter {
        override fun getPathFilter(argIndex: Int): PathFilter = FileFilters.DIRECTORY_ONLY
    }

    /** 文本文件与目录补全器（适用于 cat, head, tail, wc, sort） */
    val TEXT_FILES: CommandCompleter = object : CommandCompleter {
        override fun getPathFilter(argIndex: Int): PathFilter = FileFilters.TEXT_FILES
    }

    /** 压缩包文件与目录补全器（适用于 unzip） */
    val ARCHIVE_FILES: CommandCompleter = object : CommandCompleter {
        override fun getPathFilter(argIndex: Int): PathFilter = FileFilters.ARCHIVE_FILES
    }

    /** 无需任何文件/路径补全（适用于 echo, clear, exit, pwd, history 等命令） */
    val NONE: CommandCompleter = object : CommandCompleter {
        override fun getPathFilter(argIndex: Int): PathFilter = PathFilter { _, _ -> false }
        override suspend fun resolveCandidates(
            ctx: TerminalContext,
            parsedContext: ParsedContext,
            defaultFiles: List<FileBean>
        ): List<FileBean> = emptyList()
    }
}
