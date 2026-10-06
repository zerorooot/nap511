package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
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
    ): Flow<TerminalOutput> = flow {
        val cmdArgs = CommandArgs(args)
        // 支持 -y、-f 以及复合开关 -rf / -fr 免确认参数
        val autoConfirm = cmdArgs.hasAny("-f", "-rf", "-fr")
        val targetNames = cmdArgs.positionalArgs

        if (targetNames.isEmpty()) {
            emitError("rm: missing operand")
            return@flow
        }


        for (target in targetNames) {
            val resolved = ctx.resolveTarget(target)
            if (resolved == null) {
                emitError("rm: cannot remove '$target': No such file or directory")
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
                        emitError("rm: cannot remove '$target': Cannot remove root directory")
                        continue
                    }
                    // 安全校验 2：禁止删除当前工作目录自身
                    if (resolved.cid == ctx.currentCid) {
                        emitError("rm: cannot remove '$target': Cannot remove current working directory")
                        continue
                    }
                    if (resolved.parentCid == null) {
                        emitError("rm: cannot remove '$target': Cannot determine parent directory")
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
                    emitText("rm: 已取消删除 '$displayName'")
                    continue
                }
            }

            try {
                // 传入目标真实的 parentCid 与 fid，确保跨目录删除成功
                val res = ctx.fileRepository.delete(pid = parentCid, fid = actualFid)
                if (res.state) {
                    // 就地从父目录缓存中剔除并级联清理文件夹缓存，无需网络重新拉取
                    ctx.removeCachedFile(parentCid = parentCid, fid = actualFid, isFolder = isFolder)
                    emitText("rm: 已移入回收站 '$displayName'")
                } else {
                    val err = res.error.ifEmpty { res.message }
                    emitError("rm: 删除失败 '$displayName': $err")
                }
            } catch (e: Exception) {
                emitError("rm: 删除失败 '$displayName': ${e.message}")
            }
        }
    }
}
