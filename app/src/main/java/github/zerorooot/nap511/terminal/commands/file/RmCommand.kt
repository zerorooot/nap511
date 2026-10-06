package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.context.TerminalPath
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.archetype.ConfirmPolicy
import github.zerorooot.nap511.terminal.engine.archetype.MutationCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

/**
 * 删除操作执行计划
 *
 * @property confirmPolicy 确认策略（FORCE 免确认或 INTERACTIVE 交互确认）
 * @property targets 待删除的目标路径列表
 */
data class RmPlan(
    val confirmPolicy: ConfirmPolicy,
    val targets: List<String>
)

/**
 * 删除文件或目录至回收站命令（rm）
 *
 * 继承 [MutationCommand]，在编译期根据 `-f` / `-rf` 开关提取安全确认策略，校验待删除操作数。
 * 针对危险操作执行前置安全校验（禁止删除根目录、禁止删除当前工作目录），默认触发交互式二次确认。
 */
class RmCommand : MutationCommand<RmPlan>() {

    override val name: String = "rm"

    override val description: String = "删除当前目录或指定路径下的指定文件或文件夹至回收站"

    override val usage: String = "rm [-r|-R] [-f] <file...>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-f", "强制删除，免确认"),
        CommandFlag("-r", "支持递归删除目录")
    )

    override suspend fun compilePlan(
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Result<RmPlan> {
        val targets = ast.rawPositionalValues
        if (targets.isEmpty()) {
            return Result.failure(Exception("missing operand"))
        }

        // 支持 -y、-f 以及复合开关 -rf / -fr 免确认参数
        val autoConfirm = ast.hasAny("-f", "-rf", "-fr")
        val policy = if (autoConfirm) ConfirmPolicy.FORCE else ConfirmPolicy.INTERACTIVE

        return Result.success(RmPlan(confirmPolicy = policy, targets = targets))
    }

    override suspend fun executePlan(
        ctx: TerminalContext,
        plan: RmPlan,
        collector: FlowCollector<TerminalOutput>
    ) {
        for (target in plan.targets) {
            val resolved = ctx.resolveTarget(target)
            if (resolved == null) {
                collector.emitError("rm: cannot remove '$target': No such file or directory")
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
                        collector.emitError("rm: cannot remove '$target': Cannot remove root directory")
                        continue
                    }
                    // 安全校验 2：禁止删除当前工作目录自身
                    if (resolved.cid == ctx.currentCid) {
                        collector.emitError("rm: cannot remove '$target': Cannot remove current working directory")
                        continue
                    }
                    if (resolved.parentCid == null) {
                        collector.emitError("rm: cannot remove '$target': Cannot determine parent directory")
                        continue
                    }
                    actualFid = resolved.cid
                    parentCid = resolved.parentCid
                    displayName = resolved.name.ifEmpty {
                        TerminalPath.parse(target).targetName
                    }
                    isFolder = true
                }
            }

            // 若未开启免确认，向终端触发交互确认
            if (plan.confirmPolicy == ConfirmPolicy.INTERACTIVE) {
                val confirmed = ctx.confirm("rm: 是否确认删除 '$displayName'? (yes/no): ")
                if (!confirmed) {
                    collector.emitText("rm: 已取消删除 '$displayName'")
                    continue
                }
            }

            try {
                // 传入目标真实的 parentCid 与 fid，确保跨目录删除成功
                val res = ctx.fileRepository.delete(pid = parentCid, fid = actualFid)
                if (res.state) {
                    // 就地从父目录缓存中剔除并级联清理文件夹缓存，无需网络重新拉取
                    ctx.removeCachedFile(parentCid = parentCid, fid = actualFid, isFolder = isFolder)
                    collector.emitText("rm: 已移入回收站 '$displayName'")
                } else {
                    val err = res.error.ifEmpty { res.message }
                    collector.emitError("rm: 删除失败 '$displayName': $err")
                }
            } catch (e: Exception) {
                collector.emitError("rm: 删除失败 '$displayName': ${e.message}")
            }
        }
    }
}
