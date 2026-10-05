package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.commands.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 删除文件或目录至回收站命令（rm）
 *
 * 支持多目标批量删除、绝对/相对路径解析及末尾斜杠安全处理。
 * 针对危险操作执行前置安全校验（禁止删除根目录、禁止删除当前工作目录），默认触发交互式二次确认（支持 -y / -f 免确认）。
 */
class RmCommand : TerminalCommand {

    override val name: String = "rm"

    override val description: String = "删除当前目录或指定路径下的指定文件或文件夹至回收站"

    override val usage: String = "rm [-r|-R] [-f] <file...>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-f", "强制删除，免确认"),
        CommandFlag("-r", "支持递归删除目录")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        // 支持 -y、-f 以及复合开关 -rf / -fr 免确认参数
        val autoConfirm = cmdArgs.hasAny("-f", "-rf", "-fr")
        val targetNames = cmdArgs.positionalArgs

        if (targetNames.isEmpty()) {
            emit("rm: missing operand")
            return@flow
        }

        // 收集所有被影响的目录 CID（用于统一批量刷新本地缓存）
        val affectedCids = mutableSetOf<String>()
        var deletedCount = 0

        for (target in targetNames) {
            val resolved = ctx.resolveTarget(target)
            if (resolved == null) {
                emit("rm: cannot remove '$target': No such file or directory")
                continue
            }

            val actualFid: String
            val parentCid: String
            val displayName: String
            val isFolder: Boolean

            when (resolved) {
                is ResolvedTarget.File -> {
                    actualFid = resolved.file.fileId
                    parentCid = resolved.parentCid
                    displayName = resolved.file.name
                    isFolder = false
                }

                is ResolvedTarget.Directory -> {
                    // 安全校验 1：严禁删除根目录
                    if (resolved.cid == "0") {
                        emit("rm: cannot remove '$target': Cannot remove root directory")
                        continue
                    }
                    // 安全校验 2：禁止删除当前工作目录自身
                    if (resolved.cid == ctx.currentCid) {
                        emit("rm: cannot remove '$target': Cannot remove current working directory")
                        continue
                    }
                    if (resolved.parentCid == null) {
                        emit("rm: cannot remove '$target': Cannot determine parent directory")
                        continue
                    }
                    actualFid = resolved.cid
                    parentCid = resolved.parentCid
                    displayName = resolved.name.ifEmpty {
                        target.trimEnd('/').substringAfterLast('/')
                    }
                    isFolder = true
                }
            }

            // 若未开启免确认，向终端触发交互确认
            if (!autoConfirm) {
                val confirmed = ctx.confirm("rm: 是否确认删除 '$displayName'? (yes/no): ")
                if (!confirmed) {
                    emit("rm: 已取消删除 '$displayName'")
                    continue
                }
            }

            try {
                // 传入目标真实的 parentCid 与 fid，确保跨目录删除成功
                val res = ctx.fileRepository.delete(pid = parentCid, fid = actualFid)
                if (res.state) {
                    deletedCount++
                    affectedCids.add(parentCid)
                    if (isFolder) {
                        affectedCids.add(actualFid)
                    }
                    emit("rm: 已移入回收站 '$displayName'")
                } else {
                    val err = res.error.ifEmpty { res.message }
                    emit("rm: 删除失败 '$displayName': $err")
                }
            } catch (e: Exception) {
                emit("rm: 删除失败 '$displayName': ${e.message}")
            }
        }

        // 批量失效所有受影响目录的本地缓存，确保后续 ls 呈现最新数据
        for (cid in affectedCids) {
            ctx.invalidateCache(cid)
        }
    }
}
