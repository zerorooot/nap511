package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 移动文件或重命名命令（mv）
 *
 * 支持双模式操作：
 * 1. 移动模式：当目标为已存在的目录时，支持单个或批量移动源文件至目标目录；亦支持从标准输入（stdin）管道读取待移动源文件列表。
 * 2. 重命名模式：当仅有一个源文件且目标非目录时，执行文件重命名操作。
 */
class MvCommand : TerminalCommand {

    override val name: String = "mv"

    override val description: String = "重命名文件或将文件/目录移动至其他目录"

    override val usage: String = "mv <source...> <target>"

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val targets = args.filter { !it.startsWith("-") }.toMutableList()

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
            emitError("mv: missing file operand")
            return@flow
        }

        val destination = targets.last()
        val sources = targets.dropLast(1)
        val currentFiles = ctx.listDirectory(ctx.currentCid)

        // 解析源文件/目录信息封装体（包含其原本所在目录的 parentCid）
        data class ResolvedSource(val fid: String, val name: String, val parentCid: String?)

        suspend fun resolveSourceItem(rawSrc: String): ResolvedSource? {
            val cleanSrc = rawSrc.trim().trimEnd('/')
            val localFile =
                currentFiles.firstOrNull { it.name == cleanSrc || it.name == rawSrc.trim() }
            if (localFile != null) {
                val fid = if (localFile.isFolder) localFile.categoryId else localFile.fileId
                return ResolvedSource(fid, localFile.name, ctx.currentCid)
            }

            // 尝试路径解析（支持绝对路径与相对路径）
            return when (val resolved = ctx.resolveTarget(cleanSrc)) {
                is ResolvedTarget.Directory -> ResolvedSource(
                    resolved.cid,
                    resolved.name.ifEmpty { cleanSrc.substringAfterLast('/').ifEmpty { "/" } },
                    resolved.parentCid
                )

                is ResolvedTarget.File -> ResolvedSource(
                    resolved.file.fileId,
                    resolved.file.name,
                    resolved.parentCid
                )

                null -> null
            }
        }

        // 判断目标是否为目录（支持当前目录下文件夹、上级目录 .. / ../、绝对路径或 ~）
        var targetDestCid: String? = null
        var destDisplayName = destination
        val cleanDest = destination.trim().trimEnd('/')

        val destFolder =
            currentFiles.firstOrNull { it.isFolder && (it.name == cleanDest || it.name == destination.trim()) }
        if (destFolder != null) {
            targetDestCid = destFolder.categoryId
            destDisplayName = destFolder.name
        } else if (cleanDest == ".." || cleanDest == "." || cleanDest == "~" || cleanDest.startsWith(
                "/"
            ) || cleanDest.contains("/")
        ) {
            val resolvedPath = ctx.resolvePath(destination)
            if (resolvedPath != null) {
                targetDestCid = resolvedPath.first
                destDisplayName = resolvedPath.second
            }
        }

        if (targetDestCid != null) {
            // 移动操作：将所有 sources 移入 targetDestCid 目录
            val affectedCids = mutableSetOf(ctx.currentCid, targetDestCid)
            for (src in sources) {
                val resolvedSrc = resolveSourceItem(src)
                if (resolvedSrc == null) {
                    emitError("mv: cannot stat '$src': No such file or directory")
                    continue
                }
                try {
                    val moveMap = hashMapOf<String, String>()
                    moveMap["pid"] = targetDestCid
                    moveMap["fid[0]"] = resolvedSrc.fid
                    val res = ctx.fileRepository.move(moveMap)
                    if (res.state) {
                        // 同时记录源文件所在的父目录，后续一并失效缓存
                        resolvedSrc.parentCid?.let { affectedCids.add(it) }
                        emitText("mv: '$src' -> '$destDisplayName/'")
                    } else {
                        val err =
                            res.error.ifEmpty { res.errorMsg.ifEmpty { res.message } }
                        emitError("mv: 移动 '$src' 失败: $err")
                    }
                } catch (e: Exception) {
                    emitError("mv: 移动 '$src' 失败: ${e.message}")
                }
            }
            for (cid in affectedCids) {
                ctx.invalidateCache(cid)
            }
        } else if (sources.size == 1) {
            // 单源且目标不是现有目录：执行重命名
            if (destination.endsWith("/")) {
                emitError("mv: target '$destination' is not a directory")
                return@flow
            }
            val src = sources[0]
            val resolvedSrc = resolveSourceItem(src)
            if (resolvedSrc == null) {
                emitError("mv: cannot stat '$src': No such file or directory")
                return@flow
            }
            // 截取纯文件名，防止将路径名误作为文件名传入 rename API
            val newName = destination.trimEnd('/').substringAfterLast('/')
            try {
                val renameBean = RenameBean(resolvedSrc.fid, newName)
                val res = ctx.fileRepository.rename(renameBean.toRequestBody())
                if (res.state) {
                    ctx.invalidateCache(ctx.currentCid)
                    resolvedSrc.parentCid?.let { ctx.invalidateCache(it) }
                    emitText("mv: '$src' renamed to '$newName'")
                } else {
                    val err = res.error.ifEmpty { res.errorMsg.ifEmpty { res.message } }
                    emitError("mv: 重命名失败: $err")
                }
            } catch (e: Exception) {
                emitError("mv: 重命名失败: ${e.message}")
            }
        } else {
            emitError("mv: target '$destination' is not a directory")
        }
    }
}
