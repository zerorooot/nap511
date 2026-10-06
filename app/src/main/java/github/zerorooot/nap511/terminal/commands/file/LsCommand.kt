package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.commands.util.CommandFormatUtil
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.archetype.EntityListingCommand
import github.zerorooot.nap511.terminal.engine.archetype.ListingPlan
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitFile
import github.zerorooot.nap511.terminal.viewmodel.emitLongListing
import github.zerorooot.nap511.terminal.viewmodel.emitPath
import github.zerorooot.nap511.terminal.viewmodel.emitText
import github.zerorooot.nap511.util.formatFileSize
import kotlinx.coroutines.flow.FlowCollector
import java.util.Locale

/**
 * 列表项封装实体，携带目标路径与单文件标记
 */
data class LsEntry(
    val file: FileBean,
    val isSingleTarget: Boolean = false,
    val queryPath: String? = null
)

/**
 * 目录与文件列表查看命令（ls）
 *
 * 继承 [EntityListingCommand]，将 8 个命令行选项及多维排序在编译期解析为静态不可变的 [ListingPlan]：
 * 1. 过滤流：根据 `-a` 选项编译隐藏文件过滤断言；
 * 2. 排序流：在编译期一次性合成时间/大小/扩展名字典序比较器与逆序修饰；
 * 3. 展现流：编译期绑定长列表（-l）或紧凑列表渲染器；
 * 彻底消灭数据遍历过程中的 `hasFlag` 反复查询与多重 `when` 分支。
 */
class LsCommand : EntityListingCommand<LsEntry>() {

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

    override fun compilePlan(ast: CommandInvocationAst): Result<ListingPlan<LsEntry>> {
        val isLong = ast.hasFlag("-l")
        val isAll = ast.hasFlag("-a")
        val sortByMtime = ast.hasFlag("-t")
        val sortByAtime = ast.hasFlag("-u")
        val sortBySize = ast.hasFlag("-S")
        val sortByExt = ast.hasFlag("-X")
        val reverse = ast.hasFlag("-r")
        val forceRefresh = ast.hasFlag("--refresh")
        val targetPath = ast.firstPositional

        // 1. 编译过滤断言：若未指定 -a，过滤以 . 开头的隐藏文件（单个显式指定的目标文件除外）
        val filter: (LsEntry) -> Boolean = if (isAll) {
            { true }
        } else {
            { entry -> entry.isSingleTarget || !entry.file.name.startsWith(".") }
        }

        // 2. 编译期比较器合成
        val baseComparator: Comparator<LsEntry> = when {
            sortByMtime -> Comparator { a, b ->
                val timeA = a.file.modifiedTime.toLongOrNull() ?: 0L
                val timeB = b.file.modifiedTime.toLongOrNull() ?: 0L
                timeB.compareTo(timeA) // 降序
            }
            sortByAtime -> Comparator { a, b ->
                val timeA = a.file.updateTime.toLongOrNull() ?: 0L
                val timeB = b.file.updateTime.toLongOrNull() ?: 0L
                timeB.compareTo(timeA) // 降序
            }
            sortBySize -> Comparator { a, b ->
                val sizeA = a.file.size.toLongOrNull() ?: 0L
                val sizeB = b.file.size.toLongOrNull() ?: 0L
                sizeB.compareTo(sizeA) // 降序
            }
            sortByExt -> Comparator { a, b ->
                val extA = a.file.name.substringAfterLast(".", "")
                val extB = b.file.name.substringAfterLast(".", "")
                extA.compareTo(extB) // 升序
            }
            else -> Comparator { _, _ -> 0 } // 保持拉取时的原始稳定顺序
        }

        val finalComparator = if (reverse) baseComparator.reversed() else baseComparator

        // 3. 编译期渲染器组装
        val renderer: suspend FlowCollector<TerminalOutput>.(List<LsEntry>) -> Unit = if (isLong) {
            { entries ->
                val isSingleFile = entries.size == 1 && entries.first().isSingleTarget
                if (!isSingleFile) {
                    emitText("total ${entries.size}")
                }
                for (entry in entries) {
                    val file = entry.file
                    val typeChar = if (file.isFolder) "d" else "-"
                    val perm = "${typeChar}rwxr-xr-x"
                    val sizeStr = if (file.isFolder) "-" else (file.size.toLongOrNull() ?: 0L).formatFileSize()
                    val timeStr = CommandFormatUtil.formatTimestamp(file.modifiedTime)
                    val nameStr = if (entry.isSingleTarget && entry.queryPath != null) {
                        if (file.isFolder && !entry.queryPath.endsWith("/")) "${entry.queryPath}/" else entry.queryPath
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
                    emitLongListing(formattedRow)
                }
            }
        } else {
            { entries ->
                for (entry in entries) {
                    val file = entry.file
                    val displayName = if (entry.isSingleTarget && entry.queryPath != null) {
                        if (file.isFolder && !entry.queryPath.endsWith("/")) "${entry.queryPath}/" else entry.queryPath
                    } else {
                        if (file.isFolder) "${file.name}/" else file.name
                    }
                    if (displayName.trimEnd('/').contains('/')) {
                        emitPath(displayName)
                    } else {
                        emitFile(displayName)
                    }
                }
            }
        }

        return Result.success(
            ListingPlan(
                targetPath = targetPath,
                forceRefresh = forceRefresh,
                filter = filter,
                comparator = finalComparator,
                renderer = renderer
            )
        )
    }

    override suspend fun fetchEntities(
        ctx: TerminalContext,
        targetPath: String?,
        forceRefresh: Boolean
    ): Result<List<LsEntry>> {
        if (targetPath != null) {
            val resolved = ctx.resolveTarget(targetPath)
                ?: return Result.failure(Exception("ls: cannot access '$targetPath': No such file or directory"))

            return when (resolved) {
                is ResolvedTarget.Directory -> {
                    val files = ctx.listDirectory(resolved.cid, forceRefresh)
                    Result.success(files.map { LsEntry(it, isSingleTarget = false, queryPath = targetPath) })
                }
                is ResolvedTarget.File -> {
                    Result.success(listOf(LsEntry(resolved.file, isSingleTarget = true, queryPath = targetPath)))
                }
            }
        }

        val files = ctx.listDirectory(ctx.currentCid, forceRefresh)
        return Result.success(files.map { LsEntry(it, isSingleTarget = false, queryPath = null) })
    }
}
