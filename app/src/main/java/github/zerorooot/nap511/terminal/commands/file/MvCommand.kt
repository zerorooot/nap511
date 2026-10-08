package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.context.TerminalPath
import github.zerorooot.nap511.terminal.engine.archetype.MutationCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

/**
 * 移动与重命名执行计划
 *
 * @property sources 待移动的源路径集合
 * @property destination 目标文件或目录路径
 */
data class MvPlan(
    val sources: List<String>,
    val destination: String
)

/**
 * 移动文件或重命名命令（mv）
 *
 * 继承 [MutationCommand]，在编译期提取位置参数与管道 stdin 输入，组装为不可变 [MvPlan]。
 * 支持双模式操作：
 * 1. 移动模式：当目标为已存在的目录时，支持单个或批量移动源文件至目标目录；
 *    - 委托 [TerminalBatchFileOps.moveGroup] 批量执行，避免 N 次串行 HTTP 往返；
 *    - 内置前置安全防御：拦截原地移动、拦截将目录移入自身或其下属子目录；
 *    - 细粒度回显：单项移动展示源与目标明细，批量移动展示汇总摘要。
 * 2. 重命名模式：当仅有一个源文件且目标非目录时，执行文件/文件夹就地重命名操作。
 */
class MvCommand : MutationCommand<MvPlan>() {

    override val name: String = "mv"

    override val description: String = "重命名文件或将文件/目录移动至其他目录"

    override val usage: String = "mv <source...> <target>"

    override suspend fun compilePlan(
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Result<MvPlan> {
        val targets = ast.rawPositionalValues.toMutableList()

        // 管道支持：若命令行参数仅提供了 1 个目标目录（例如 find ... | mv ../），且上游管道存在输入，智能从 stdin 获取源列表
        if (targets.size < 2) {
            val stdinSources = mutableListOf<String>()
            stdin.collect { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    stdinSources.add(trimmed)
                }
            }
            if (stdinSources.isNotEmpty() && targets.size == 1) {
                val destination = targets[0]
                targets.clear()
                targets.addAll(stdinSources)
                targets.add(destination)
            }
        }

        if (targets.size < 2) {
            return Result.failure(Exception("missing file operand"))
        }

        val destination = targets.last()
        val sources = targets.dropLast(1)
        return Result.success(MvPlan(sources = sources, destination = destination))
    }

    override suspend fun executePlan(
        ctx: TerminalContext,
        plan: MvPlan,
        collector: FlowCollector<TerminalOutput>
    ) {
        val destination = plan.destination
        val sources = plan.sources
        val currentFiles = ctx.listDirectory(ctx.currentCid)
        val destParsed = TerminalPath.parse(destination)

        /**
         * 解析源文件/目录，将其统一转换为 [BatchTargetItem] 元数据实体
         */
        suspend fun resolveSourceItem(rawSrc: String): BatchTargetItem? {
            val srcParsed = TerminalPath.parse(rawSrc)
            val localFile = currentFiles.firstOrNull { it.name == srcParsed.targetName || it.name == rawSrc.trim() }
            if (localFile != null && !srcParsed.isMultiSegment && !srcParsed.isAbsolute) {
                val fid = if (localFile.isFolder) localFile.categoryId else localFile.fileId
                return BatchTargetItem(
                    parentCid = ctx.currentCid,
                    fid = fid,
                    displayName = localFile.name,
                    isFolder = localFile.isFolder
                )
            }

            // 尝试路径解析（支持绝对路径与相对路径）
            return when (val resolved = ctx.resolveTarget(rawSrc)) {
                is ResolvedTarget.Directory -> {
                    val effectiveParentCid = resolved.parentCid
                        ?: resolved.pathList.dropLast(1).lastOrNull()?.cid
                        ?: ctx.currentCid
                    BatchTargetItem(
                        parentCid = effectiveParentCid,
                        fid = resolved.cid,
                        displayName = resolved.name.ifEmpty { srcParsed.targetName.ifEmpty { "/" } },
                        isFolder = true
                    )
                }

                is ResolvedTarget.File -> {
                    val effectiveParentCid = resolved.parentCid.ifEmpty { resolved.file.categoryId }
                    BatchTargetItem(
                        parentCid = effectiveParentCid,
                        fid = resolved.file.fileId,
                        displayName = resolved.file.name,
                        isFolder = false
                    )
                }

                null -> null
            }
        }

        // 解析目标目录（通过强类型 Directory 返回完整路径面包屑 pathList）
        val resolvedDestDir = ctx.resolveDirectory(destination)

        if (resolvedDestDir != null) {
            // === 模式 1：移动模式（目标为有效目录） ===
            val targetDestCid = resolvedDestDir.cid
            val destDisplayName = if (!destParsed.isMultiSegment && !destParsed.isAbsolute && !destParsed.isRoot && !destParsed.isCurrentDirectory && resolvedDestDir.name.isNotEmpty()) {
                resolvedDestDir.name
            } else {
                resolvedDestDir.path
            }
            val formattedDest = if (destDisplayName == "/") "/" else "${destDisplayName.trimEnd('/')}/"

            val validItems = mutableListOf<BatchTargetItem>()
            val validRawSources = mutableListOf<String>()

            // 阶段一：前置多源安全校验与合法条目收集
            for (src in sources) {
                val resolvedSrc = resolveSourceItem(src)
                if (resolvedSrc == null) {
                    collector.emitError("mv: cannot stat '$src': No such file or directory")
                    continue
                }

                // 前置安全校验 1：原地移动防护（源条目已位于目标目录中）
                if (resolvedSrc.parentCid == targetDestCid) {
                    collector.emitText("mv: '$src' 与目标目录相同，已跳过")
                    continue
                }

                // 前置安全校验 2：自嵌套与移入子目录防护（目标目录自身包含在源目录的层级链条中）
                if (resolvedSrc.isFolder && resolvedDestDir.pathList.any { it.cid == resolvedSrc.fid }) {
                    collector.emitError("mv: 无法将目录 '$src' 移动至其自身或子目录下")
                    continue
                }

                validItems.add(resolvedSrc)
                validRawSources.add(src)
            }

            if (validItems.isEmpty()) return

            // 阶段二：委托 TerminalBatchFileOps 执行批量移动与缓存维护
            if (validItems.size == 1) {
                val single = validItems.first()
                val singleRaw = validRawSources.first()
                val result = TerminalBatchFileOps.moveGroup(ctx, targetDestCid, listOf(single))
                if (result.isAllSuccess) {
                    collector.emitText("mv: '$singleRaw' -> '$formattedDest'")
                } else {
                    collector.emitError("mv: 移动 '$singleRaw' 失败: ${result.error}")
                }
            } else {
                val result = TerminalBatchFileOps.moveGroup(ctx, targetDestCid, validItems)
                when {
                    result.isAllSuccess -> {
                        collector.emitText("mv: 已成功移动 ${result.successCount} 个文件/文件夹至 '$formattedDest'")
                    }
                    result.isPartialSuccess -> {
                        collector.emitText("mv: 部分移动成功 (${result.successCount}/${result.totalCount}) 至 '$formattedDest'")
                        collector.emitError("mv: 其余 ${result.failureCount} 个项目移动失败: ${result.error}")
                    }
                    else -> {
                        collector.emitError("mv: 批量移动失败 (${validItems.size} 个项目): ${result.error}")
                    }
                }
            }
        } else if (sources.size == 1) {
            // === 模式 2：重命名模式（单源且目标不是现有目录） ===
            if (destParsed.hasTrailingSlash) {
                collector.emitError("mv: target '$destination' is not a directory")
                return
            }
            val src = sources[0]
            val resolvedSrc = resolveSourceItem(src)
            if (resolvedSrc == null) {
                collector.emitError("mv: cannot stat '$src': No such file or directory")
                return
            }
            // 截取纯文件名，防止将路径名误作为文件名传入 rename API
            val newName = destParsed.targetName
            try {
                val renameBean = RenameBean(resolvedSrc.fid, newName)
                val res = ctx.fileRepository.rename(renameBean.toRequestBody())
                if (res.state) {
                    val parentCid = resolvedSrc.parentCid.ifEmpty { ctx.currentCid }
                    // 就地在父目录缓存中重命名该文件/文件夹（对齐 FileViewModel 的 rename 逻辑）
                    ctx.fileCacheManager.renameItem(parentCid = parentCid, fid = resolvedSrc.fid, newName = newName)
                    collector.emitText("mv: '$src' renamed to '$newName'")
                } else {
                    val err = res.error.ifEmpty { res.errorMsg.ifEmpty { res.message } }
                    collector.emitError("mv: 重命名失败: $err")
                }
            } catch (e: Exception) {
                collector.emitError("mv: 重命名失败: ${e.message}")
            }
        } else {
            // 多源但目标非目录，无法执行移动
            collector.emitError("mv: target '$destination' is not a directory")
        }
    }
}
