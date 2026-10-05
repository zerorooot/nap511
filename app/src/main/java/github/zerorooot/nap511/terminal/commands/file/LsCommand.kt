package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.commands.util.CommandFormatUtil
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitFile
import github.zerorooot.nap511.terminal.viewmodel.emitLongListing
import github.zerorooot.nap511.terminal.viewmodel.emitPath
import github.zerorooot.nap511.terminal.viewmodel.emitText
import github.zerorooot.nap511.util.formatFileSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 目录与文件列表查看命令（ls）
 *
 * 列出当前目录或目标路径下的文件与文件夹。
 * 支持详细视图（-l）、包含隐藏文件（-a）、多维度排序（-t修改时间, -u访问时间, -S大小, -X后缀, -r反转）及强制缓存刷新（--refresh）。
 */
class LsCommand : TerminalCommand {

    override val name: String = "ls"

    override val description: String = "列出当前或指定目录下的文件与文件夹"

    override val usage: String = "ls [options] [path]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "使用详细列表格式显示（包含类型、大小、修改时间与名称）"),
        CommandFlag("-a", "显示全部文件（包括以 . 开头的隐藏项）"),
        CommandFlag("-t", "按文件修改时间排序"),
        CommandFlag("-u", "按文件访问/打开时间排序"),
        CommandFlag("-S", "按文件大小排序"),
        CommandFlag("-X", "按扩展名排序"),
        CommandFlag("-r", "反转排序结果"),
        CommandFlag("--refresh", "强制从网盘拉取最新数据并刷新本地缓存")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val cmdArgs = CommandArgs(args)
        val isLong = cmdArgs.hasFlag("-l")
        val isAll = cmdArgs.hasFlag("-a")
        val sortByMtime = cmdArgs.hasFlag("-t")
        val sortByAtime = cmdArgs.hasFlag("-u")
        val sortBySize = cmdArgs.hasFlag("-S")
        val sortByExt = cmdArgs.hasFlag("-X")
        val reverse = cmdArgs.hasFlag("-r")
        val forceRefresh = cmdArgs.hasFlag("--refresh")

        // 提取目标路径参数（第一个非选项参数）
        val targetPath = cmdArgs.firstPositional

        // 1. 根据路径解析待展示的文件集合
        val (candidateFiles, isSingleFile) = if (targetPath != null) {
            val resolved = ctx.resolveTarget(targetPath)
            if (resolved == null) {
                // 源头直接标注错误类型
                emitError("ls: cannot access '$targetPath': No such file or directory")
                return@flow
            }
            when (resolved) {
                is ResolvedTarget.Directory -> {
                    val files = ctx.listDirectory(resolved.cid, forceRefresh)
                    val filtered = if (isAll) files else files.filter { !it.name.startsWith(".") }
                    Pair(filtered, false)
                }

                is ResolvedTarget.File -> {
                    Pair(listOf(resolved.file), true)
                }
            }
        } else {
            val files = ctx.listDirectory(ctx.currentCid, forceRefresh)
            val filtered = if (isAll) files else files.filter { !it.name.startsWith(".") }
            Pair(filtered, false)
        }

        // 2. 排序处理：默认完全保持接口请求/缓存中的原始顺序；仅在显式传入选项时重排
        var sorted = when {
            sortByMtime -> candidateFiles.sortedByDescending {
                it.modifiedTime.toLongOrNull() ?: 0L
            }

            sortByAtime -> candidateFiles.sortedByDescending {
                it.updateTime.toLongOrNull() ?: 0L
            }

            sortBySize -> candidateFiles.sortedByDescending {
                it.size.toLongOrNull() ?: 0L
            }

            sortByExt -> candidateFiles.sortedBy { it.name.substringAfterLast(".", "") }
            else -> candidateFiles
        }

        if (reverse) {
            sorted = sorted.reversed()
        }

        // 3. 格式化输出：源头显式赋予对应的 TerminalLineType 语义
        if (isLong) {
            if (!isSingleFile) {
                // "total X" 属于普通文本信息
                emitText("total ${sorted.size}")
            }
            for (file in sorted) {
                val typeChar = if (file.isFolder) "d" else "-"
                val perm = "${typeChar}rwxr-xr-x"
                val sizeStr = if (file.isFolder) "-" else (file.size.toLongOrNull() ?: 0L).formatFileSize()
                val timeStr = CommandFormatUtil.formatTimestamp(file.modifiedTime)
                val nameStr = if (isSingleFile && targetPath != null) {
                    if (file.isFolder && !targetPath.endsWith("/")) "$targetPath/" else targetPath
                } else {
                    if (file.isFolder) "${file.name}/" else file.name
                }
                val formattedRow = String.format(
                    Locale.getDefault(),
                    "%-11s %10s %16s %s",
                    perm,
                    sizeStr,
                    timeStr,
                    nameStr
                )
                // 源头直接标注为 OUTPUT_LONG_LISTING
                emitLongListing(formattedRow)
            }
        } else {
            for (file in sorted) {
                val displayName = if (isSingleFile && targetPath != null) {
                    if (file.isFolder && !targetPath.endsWith("/")) "$targetPath/" else targetPath
                } else {
                    if (file.isFolder) "${file.name}/" else file.name
                }
                // 若包含多级斜杠路径，发射 OUTPUT_PATH_ENTRY；单文件/文件夹发射 OUTPUT_FILE_ENTRY
                if (displayName.trimEnd('/').contains('/')) {
                    emitPath(displayName)
                } else {
                    emitFile(displayName)
                }
            }
        }
    }
}
