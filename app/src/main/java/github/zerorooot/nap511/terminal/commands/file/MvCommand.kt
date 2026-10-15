package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.terminal.commands.util.CommandMessages
import github.zerorooot.nap511.terminal.commands.util.MoveValidationResult
import github.zerorooot.nap511.terminal.commands.util.TargetItemResolver
import github.zerorooot.nap511.terminal.commands.util.TerminalExceptionHandler
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.context.TerminalPath
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.StreamTargetCollector
import github.zerorooot.nap511.terminal.engine.archetype.MutationCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.CancellationException
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
 *    - 支持 GNU 标准 `-t <dir>` / `--target-directory <dir>` 语法，使目标目录可前置声明，完美契约 `xargs -0 mv -t <dir>` 单批聚合；
 *    - 支持从管道 stdin 直连读取待移动项（如 `find ... -print0 | mv <dir>`），自动感知并剥离 `\0` NUL 定界符；
 *    - 委托 [TerminalBatchFileOps.moveGroup] 批量执行，避免 N 次串行 HTTP 往返；
 *    - 内置前置安全防御：拦截原地移动、拦截将目录移入自身或其下属子目录；
 *    - 细粒度回显：单项移动展示源与目标明细，批量移动展示汇总摘要。
 * 2. 重命名与跨目录移动重命名模式：当仅有一个源文件且目标非目录时：
 *    - 若目标路径包含父级路径（如 `mv a.txt exist_dir/b.txt`），执行「移动 -> 重命名 -> 失败仅逆向回滚」事务机制；
 *    - 若目标无父级路径（如 `mv a.txt b.txt`），执行同目录就地重命名并同步本地缓存；
 *    - 原地自移动防御：严格拦截 `mv a.txt a.txt`，输出 POSIX 标准 same file 错误。
 */
class MvCommand : MutationCommand<MvPlan>() {

    override val name: String = "mv"

    override val description: String = "重命名文件或将文件/目录移动至其他目录"

    override val usage: String = "mv [-t <dir>] <source...> [target]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-t <dir>, --target-directory <dir>", "指定移动的目标目录，允许后续所有参数均作为待移动源文件")
    )

    override val valueOptions: Set<String> = setOf("-t", "--target-directory")

    override suspend fun compilePlan(
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Result<MvPlan> {
        val targetDirectoryOpt = ast.getOption("-t") ?: ast.getOption("--target-directory")
        val positional = ast.rawPositionalValues
        // 通过通用 StreamTargetCollector 提取管道标准输入，自动支持 \0 (NUL) 与 \n 定界
        val stdinSources = StreamTargetCollector.collectFromStdin(stdin)

        val destination: String
        val sources: List<String>

        if (targetDirectoryOpt != null) {
            // 分支 1：GNU 标准前置目标目录模式 (mv -t <dest> <src...>)
            destination = targetDirectoryOpt
            sources = positional + stdinSources
        } else {
            // 分支 2：传统 POSIX 模式 (mv <src...> <dest>) 或 单目标管道输入 (find ... | mv <dest>)
            val combinedTargets = positional.toMutableList()
            if (combinedTargets.size == 1 && stdinSources.isNotEmpty()) {
                destination = combinedTargets[0]
                sources = stdinSources
            } else if (combinedTargets.size >= 2) {
                destination = combinedTargets.last()
                sources = combinedTargets.dropLast(1) + stdinSources
            } else {
                return Result.failure(Exception("missing file operand"))
            }
        }

        if (sources.isEmpty()) {
            return Result.failure(Exception("missing file operand"))
        }

        return Result.success(MvPlan(sources = sources, destination = destination))
    }

    override suspend fun executePlan(
        ctx: TerminalContext,
        plan: MvPlan,
        collector: FlowCollector<TerminalOutput>
    ) {
        val destination = plan.destination
        val sources = plan.sources
        val destParsed = TerminalPath.parse(destination)

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
                val resolvedSrc = TargetItemResolver.resolveToBatchItem(ctx, src)
                if (resolvedSrc == null) {
                    collector.emitError(CommandMessages.cannotStat("mv", src))
                    continue
                }

                when (val validation = TargetItemResolver.validateMove(resolvedSrc, targetDestCid, resolvedDestDir, ctx, src, destination)) {
                    is MoveValidationResult.Error -> {
                        collector.emitError(validation.message)
                        continue
                    }
                    is MoveValidationResult.Valid -> {
                        validItems.add(resolvedSrc)
                        validRawSources.add(src)
                    }
                }
            }

            if (validItems.isEmpty()) return

            // 阶段二：委托 TerminalBatchFileOps 执行批量移动与缓存维护
            if (validItems.size == 1) {
                val single = validItems.first()
                val singleRaw = validRawSources.first()
                val result = TerminalBatchFileOps.moveGroup(ctx, targetDestCid, listOf(single))
                if (result.isAllSuccess) {
                    collector.emitText(CommandMessages.singleMoveSuccess("mv", singleRaw, formattedDest))
                } else {
                    collector.emitError("mv: 移动 '$singleRaw' 失败: ${result.error}")
                }
            } else {
                val result = TerminalBatchFileOps.moveGroup(ctx, targetDestCid, validItems)
                when {
                    result.isAllSuccess -> {
                        collector.emitText(CommandMessages.batchMoveSuccess("mv", result.successCount, formattedDest))
                    }
                    result.isPartialSuccess -> {
                        collector.emitText(CommandMessages.batchMovePartial("mv", result.successCount, result.totalCount, formattedDest))
                        collector.emitError(CommandMessages.batchMoveFailed("mv", result.failureCount, result.error))
                    }
                    else -> {
                        collector.emitError(CommandMessages.batchMoveFailed("mv", validItems.size, result.error))
                    }
                }
            }
        } else if (sources.size == 1) {
            // === 模式 2：重命名或跨目录移动重命名模式（单源且目标不是现有目录） ===
            if (destParsed.hasTrailingSlash) {
                collector.emitError(CommandMessages.notADirectory("mv", destination))
                return
            }

            val src = sources[0]
            val resolvedSrc = TargetItemResolver.resolveToBatchItem(ctx, src)
            if (resolvedSrc == null) {
                collector.emitError(CommandMessages.cannotStat("mv", src))
                return
            }

            // 高危目录前置拦截
            if (TargetItemResolver.isDotOrDotDot(src)) {
                collector.emitError(CommandMessages.cannotMoveDotOrDotDot("mv", src))
                return
            }
            if (TargetItemResolver.isRoot(resolvedSrc)) {
                collector.emitError(CommandMessages.cannotMoveRoot("mv", src))
                return
            }
            if (TargetItemResolver.isCurrentDirectory(ctx, resolvedSrc)) {
                collector.emitError(CommandMessages.cannotMoveCwd("mv", src))
                return
            }
            if (TargetItemResolver.isAncestorOfCurrent(ctx, resolvedSrc)) {
                collector.emitError(CommandMessages.cannotMoveAncestor("mv", src))
                return
            }

            val newName = destParsed.targetName

            // 判断目标是否指定了跨目录路径（带父级路径）
            val parentPath = destParsed.parentPathString
            if (parentPath.isNotEmpty()) {
                // 子分支 2A：跨目录移动并重命名（mv a.txt exist_dir/b.txt）
                val resolvedDestParent = ctx.resolveDirectory(parentPath)
                if (resolvedDestParent == null) {
                    collector.emitError(CommandMessages.cannotAccess("mv", parentPath))
                    return
                }

                // 检查原地同名短路拦截
                if (resolvedSrc.parentCid == resolvedDestParent.cid && resolvedSrc.displayName == newName) {
                    collector.emitError(CommandMessages.sameFile("mv", src, destination))
                    return
                }

                // 若源文件已在目标父目录下，直接执行同目录就地重命名
                if (resolvedSrc.parentCid == resolvedDestParent.cid) {
                    executeInPlaceRename(ctx, resolvedSrc, src, newName, collector)
                    return
                }

                // 校验将源移入目标父目录的合法性（防自嵌套等）
                if (resolvedSrc.isFolder && (resolvedSrc.fid == resolvedDestParent.cid || resolvedDestParent.pathList.any { it.cid == resolvedSrc.fid })) {
                    collector.emitError(CommandMessages.selfNestingError("mv", src))
                    return
                }

                // 步骤 1：先移动文件至目标目录（遵循 115 同名自动追加后缀容忍机制）
                val moveResult = TerminalBatchFileOps.moveGroup(ctx, resolvedDestParent.cid, listOf(resolvedSrc))
                if (!moveResult.isAllSuccess) {
                    collector.emitError("mv: 移动 '$src' 失败: ${moveResult.error}")
                    return
                }

                // 步骤 2：在目标目录下执行重命名为指定名称
                try {
                    val renameBean = RenameBean(resolvedSrc.fid, newName)
                    val renameRes = ctx.fileRepository.rename(renameBean.toRequestBody())
                    if (renameRes.state) {
                        ctx.fileCacheManager.renameItem(
                            parentCid = resolvedDestParent.cid,
                            fid = resolvedSrc.fid,
                            newName = newName
                        )
                        collector.emitText(CommandMessages.singleMoveSuccess("mv", src, destination))
                    } else {
                        val renameErr = TerminalExceptionHandler.extractErrorMessage(renameRes, "重命名失败")
                        // 步骤 3：失败逆向回滚：将文件移回原目录（不强求恢复原文件名）
                        val originalParentCid = resolvedSrc.parentCid
                        if (!originalParentCid.isNullOrEmpty()) {
                            val rollbackItem = resolvedSrc.copy(parentCid = resolvedDestParent.cid)
                            val rollbackResult = TerminalBatchFileOps.moveGroup(ctx, originalParentCid, listOf(rollbackItem))
                            if (rollbackResult.isAllSuccess) {
                                collector.emitText(CommandMessages.moveRollbackSuccess("mv", src, originalParentCid))
                                collector.emitError("mv: 重命名失败: $renameErr")
                            } else {
                                collector.emitError(CommandMessages.moveRollbackFailed("mv", src, resolvedDestParent.path, rollbackResult.error))
                            }
                        } else {
                            collector.emitError("mv: 重命名失败: $renameErr")
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val errStr = TerminalExceptionHandler.extractErrorMessage(e)
                    collector.emitError("mv: 重命名失败: $errStr")
                }
            } else {
                // 子分支 2B：同目录就地重命名（如 mv a.txt b.txt）
                if (src == destination || resolvedSrc.displayName == newName) {
                    collector.emitError(CommandMessages.sameFile("mv", src, destination))
                    return
                }
                executeInPlaceRename(ctx, resolvedSrc, src, newName, collector)
            }
        } else {
            // 多源但目标非目录，无法执行移动
            collector.emitError(CommandMessages.notADirectory("mv", destination))
        }
    }

    /**
     * 执行同目录文件就地重命名并同步本地缓存
     */
    private suspend fun executeInPlaceRename(
        ctx: TerminalContext,
        resolvedSrc: BatchTargetItem,
        srcDisplay: String,
        newName: String,
        collector: FlowCollector<TerminalOutput>
    ) {
        try {
            val renameBean = RenameBean(resolvedSrc.fid, newName)
            val res = ctx.fileRepository.rename(renameBean.toRequestBody())
            if (res.state) {
                val parentCid = resolvedSrc.parentCid.orEmpty().ifEmpty { ctx.currentCid }
                // 就地在父目录缓存中重命名该文件/文件夹（对齐 FileViewModel 的 rename 逻辑）
                ctx.fileCacheManager.renameItem(parentCid = parentCid, fid = resolvedSrc.fid, newName = newName)
                collector.emitText(CommandMessages.renamed("mv", srcDisplay, newName))
            } else {
                val err = TerminalExceptionHandler.extractErrorMessage(res, "重命名失败")
                collector.emitError("mv: 重命名失败: $err")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            collector.emitError("mv: 重命名失败: ${TerminalExceptionHandler.extractErrorMessage(e)}")
        }
    }
}
