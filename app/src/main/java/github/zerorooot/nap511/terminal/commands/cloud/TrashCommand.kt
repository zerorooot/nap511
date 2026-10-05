package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 网盘回收站管理命令（trash）
 *
 * 支持列出回收站待清理文件（-l 或缺省）、通过文件专属 rid 进行原位还原（-r <rid>），
 * 以及带有交互式确认的清空回收站操作（-c）。
 */
class TrashCommand : TerminalCommand {

    override val name: String = "trash"

    override val description: String = "网盘回收站管理（查看、还原或清空）"

    override val usage: String = "trash [-l] [-r <rid>] [-c]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "列出回收站中的文件列表及 rid"),
        CommandFlag("-r <rid>", "按 rid 还原文件到原目录"),
        CommandFlag("-c", "清空回收站全部文件")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        val isList = cmdArgs.hasFlag("-l") || args.isEmpty()
        val ridToRevert = cmdArgs.getOption("-r")
        val isClean = cmdArgs.hasFlag("-c")

        // 1. 还原特定 RID 文件
        if (ridToRevert != null) {
            try {
                val res = ctx.fileRepository.revert(ridToRevert)
                if (res.state) {
                    emit("trash: 已成功还原项 rid: $ridToRevert")
                } else {
                    emit("trash: 还原失败: ${res.error}")
                }
            } catch (e: Exception) {
                emit("trash: 还原异常: ${e.message}")
            }
            return@flow
        }

        // 2. 清空回收站（二次危险确认）
        if (isClean) {
            val confirmed =
                ctx.confirm("trash: 警告！确定要清空回收站中的全部文件吗？(yes/no): ")
            if (!confirmed) {
                emit("trash: 已取消清空操作")
                return@flow
            }
            try {
                val res = ctx.fileRepository.recycleCleanAll("")
                if (res.state) {
                    emit("trash: 回收站已成功清空")
                } else {
                    emit("trash: 清空失败: ${res.error}")
                }
            } catch (e: Exception) {
                emit("trash: 清空异常: ${e.message}")
            }
            return@flow
        }

        // 3. 列出回收站列表
        if (isList) {
            try {
                val list = ctx.fileRepository.recycleList()
                if (list.recycleBeanList.isEmpty()) {
                    emit("trash: 回收站为空")
                } else {
                    emit("回收站项目列表（共 ${list.recycleBeanList.size} 项）：")
                    for (item in list.recycleBeanList) {
                        emit(
                            String.format(
                                Locale.getDefault(),
                                "rid: %-15s %s (%s)",
                                item.id,
                                item.fileName,
                                item.fileSize
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                emit("trash: 获取回收站列表失败: ${e.message}")
            }
        }
    }
}
