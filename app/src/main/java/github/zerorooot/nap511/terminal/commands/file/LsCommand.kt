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
import github.zerorooot.nap511.terminal.viewmodel.emitError
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
 * 继承 [EntityListingCommand]，严格对齐 POSIX / GNU ls 标准语义：
 * 1. 过滤流：根据 `-a` 选项编译隐藏文件过滤断言（显式指定的单文件除外）；
 * 2. 排序流：在编译期一次性合成时间/大小/扩展名字典序比较器与逆序修饰；
 * 3. 展现流：编译期标记长列表（-l）或紧凑列表；
 * 4. 多操作数与通配符支持：
 *    - 无参数：列出当前工作目录；
 *    - 单目录：列出该目录内容，无多余头部；
 *    - 单文件 / 多文件：汇聚文件集合统一排序展现（-l 模式不显示多余的 total 统计头）；
 *    - 混合目标 / 多目录：多目录分别打印标头与内容，段落间空行隔开；
 *    - 容错处理：某个目标不存在时输出错误提示，不阻断其余合法项展现。
 */
class LsCommand : EntityListingCommand<LsEntry>() {

    override val name: String = "ls"

    override val description: String = "列出当前或指定目录下的文件与文件夹"

    override val usage: String = "ls [options] [path...]"

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
        // 关键改进：接收全部位置参数（支持多文件或通配符展开后的多个操作数）
        val targets = ast.rawPositionalValues

        // 1. 编译过滤断言：若未指定 -a，过滤以 . 开头的隐藏文件（显式指定的目标文件除外）
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

        return Result.success(
            ListingPlan(
                targets = targets,
                forceRefresh = forceRefresh,
                filter = filter,
                comparator = finalComparator,
                isLong = isLong
            )
        )
    }

    override suspend fun executePlan(
        ctx: TerminalContext,
        plan: ListingPlan<LsEntry>,
        collector: FlowCollector<TerminalOutput>
    ) {
        val targets = plan.targets

        // 场景 1：无参数，列出当前工作目录
        if (targets.isEmpty()) {
            renderDirectoryContents(
                ctx = ctx,
                cid = ctx.currentCid,
                queryPath = null,
                plan = plan,
                collector = collector
            )
            return
        }

        // 场景 2：单参数快捷路径（最常见情况：ls dir 或 ls file）
        if (targets.size == 1) {
            val singleTarget = targets.first()
            val resolved = ctx.resolveTarget(singleTarget)
            if (resolved == null) {
                collector.emitError("ls: cannot access '$singleTarget': No such file or directory")
                return
            }

            when (resolved) {
                is ResolvedTarget.Directory -> {
                    // 单个目录：直接平铺列出该目录内容，无需目录标头
                    renderDirectoryContents(
                        ctx = ctx,
                        cid = resolved.cid,
                        queryPath = singleTarget,
                        plan = plan,
                        collector = collector
                    )
                }
                is ResolvedTarget.File -> {
                    // 单个文件：直接渲染单文件条目
                    val entry = LsEntry(resolved.file, isSingleTarget = true, queryPath = singleTarget)
                    renderFilesGroup(listOf(entry), plan, collector)
                }
            }
            return
        }

        // 场景 3：多参数处理（如通配符展开后多个文件，或用户指定多个目录与文件）
        // 遵循 GNU ls 标准行为分流：不存在项 -> 文件组 -> 目录组
        val notFoundErrors = mutableListOf<String>()
        val fileEntries = mutableListOf<LsEntry>()
        val dirTargets = mutableListOf<Pair<String, ResolvedTarget.Directory>>()

        for (target in targets) {
            val resolved = ctx.resolveTarget(target)
            when (resolved) {
                null -> notFoundErrors.add("ls: cannot access '$target': No such file or directory")
                is ResolvedTarget.File -> fileEntries.add(
                    LsEntry(resolved.file, isSingleTarget = true, queryPath = target)
                )
                is ResolvedTarget.Directory -> dirTargets.add(target to resolved)
            }
        }

        // 3.1 容错输出：优先输出不存在的路径错误，不中断其余合法目标的展示
        for (err in notFoundErrors) {
            collector.emitError(err)
        }

        // 3.2 文件项优先输出：将所有普通文件汇聚统一排序并渲染
        var hasPrecedingSection = false
        if (fileEntries.isNotEmpty()) {
            val processedFiles = fileEntries.filter(plan.filter).sortedWith(plan.comparator)
            renderFilesGroup(processedFiles, plan, collector)
            hasPrecedingSection = true
        }

        // 3.3 目录项逐个分块输出：若总目标多于 1 个，各目录带有 "dir:" 标头，段落间空行隔开
        for ((queryPath, dirResolved) in dirTargets) {
            if (hasPrecedingSection) {
                collector.emitText("")
            }
            collector.emitText("$queryPath:")
            renderDirectoryContents(
                ctx = ctx,
                cid = dirResolved.cid,
                queryPath = queryPath,
                plan = plan,
                collector = collector
            )
            hasPrecedingSection = true
        }
    }

    /**
     * 渲染独立文件组（GNU ls 标准：显式指定独立文件时，-l 模式不输出 "total X" 标头）
     */
    private suspend fun renderFilesGroup(
        entries: List<LsEntry>,
        plan: ListingPlan<LsEntry>,
        collector: FlowCollector<TerminalOutput>
    ) {
        if (entries.isEmpty()) return
        if (plan.isLong) {
            for (entry in entries) {
                collector.emitLongListing(formatLongListingRow(entry))
            }
        } else {
            for (entry in entries) {
                val displayName = formatDisplayName(entry)
                if (displayName.trimEnd('/').contains('/')) {
                    collector.emitPath(displayName)
                } else {
                    collector.emitFile(displayName)
                }
            }
        }
    }

    /**
     * 渲染指定目录内的条目集合
     */
    private suspend fun renderDirectoryContents(
        ctx: TerminalContext,
        cid: String,
        queryPath: String?,
        plan: ListingPlan<LsEntry>,
        collector: FlowCollector<TerminalOutput>
    ) {
        val files = ctx.listDirectory(cid, plan.forceRefresh)
        val entries = files.map { LsEntry(it, isSingleTarget = false, queryPath = queryPath) }
        val processed = entries.filter(plan.filter).sortedWith(plan.comparator)

        if (plan.isLong) {
            collector.emitText("total ${processed.size}")
            for (entry in processed) {
                collector.emitLongListing(formatLongListingRow(entry))
            }
        } else {
            for (entry in processed) {
                val displayName = formatDisplayName(entry)
                if (displayName.trimEnd('/').contains('/')) {
                    collector.emitPath(displayName)
                } else {
                    collector.emitFile(displayName)
                }
            }
        }
    }

    /**
     * 计算条目展现名称：单文件显式保留查询前缀，目录内容默认取自身名称
     */
    private fun formatDisplayName(entry: LsEntry): String {
        val file = entry.file
        return if (entry.isSingleTarget && entry.queryPath != null) {
            if (file.isFolder && !entry.queryPath.endsWith("/")) "${entry.queryPath}/" else entry.queryPath
        } else {
            if (file.isFolder) "${file.name}/" else file.name
        }
    }

    /**
     * 格式化长列表展示行（权限、大小、修改时间、名称）
     */
    private fun formatLongListingRow(entry: LsEntry): String {
        val file = entry.file
        val typeChar = if (file.isFolder) "d" else "-"
        val perm = "${typeChar}rwxr-xr-x"
        val sizeStr = if (file.isFolder) "-" else (file.size.toLongOrNull() ?: 0L).formatFileSize()
        val timeStr = CommandFormatUtil.formatTimestamp(file.modifiedTime)
        val nameStr = formatDisplayName(entry)
        return String.format(
            Locale.getDefault(),
            "%-11s %10s %16s %s",
            perm,
            sizeStr,
            timeStr,
            nameStr
        )
    }
}
