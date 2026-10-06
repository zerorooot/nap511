package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitSystem
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 网盘回收站管理命令（trash）
 *
 * 支持列出回收站待清理文件（-l 或缺省）、通过文件专属 rid 或文件名进行原位还原（-r <rid|name>），
 * 以及带有交互式确认的清空回收站操作（-c）。
 */
class TrashCommand : TerminalCommand {

    override val name: String = "trash"

    override val description: String = "网盘回收站管理（查看、还原或清空）"

    override val usage: String = "trash [-l] [-r <rid|name>] [-c]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "列出回收站中的文件列表及 rid"),
        CommandFlag("-r <rid|name>", "按 rid 或文件名还原文件到原目录"),
        CommandFlag("-c", "清空回收站全部文件")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val cmdArgs = CommandArgs(args)
        val isList = cmdArgs.hasFlag("-l") || args.isEmpty()
        val ridToRevert = cmdArgs.getOption("-r")
        val isClean = cmdArgs.hasFlag("-c")

        // 1. 还原特定 RID 或文件名的回收站项
        if (ridToRevert != null) {
            try {
                val recycleList = runCatching { ctx.fileRepository.recycleList().recycleBeanList }.getOrDefault(emptyList())
                val matchedItem = recycleList.firstOrNull { it.fileName.equals(ridToRevert, ignoreCase = true) }
                val actualRid: String
                val successMessage: String

                if (matchedItem != null) {
                    actualRid = matchedItem.id
                    successMessage = "trash: 已还原 '${matchedItem.fileName}' (rid: $actualRid)"
                } else if (ridToRevert.toLongOrNull() != null) {
                    actualRid = ridToRevert
                    successMessage = "trash: 已还原 '$actualRid'"
                } else {
                    emitError("trash: 未在回收站中找到 '$ridToRevert'")
                    return@flow
                }

                val res = ctx.fileRepository.revert(actualRid)
                if (res.state) {
                    if (matchedItem != null && matchedItem.cid.isNotEmpty()) {
                        val isFolder = matchedItem.isFolder || matchedItem.type.equals("folder", ignoreCase = true)
                        if (isFolder) {
                            ctx.addCachedFolder(matchedItem.cid, matchedItem.fileName, matchedItem.id)
                        } else {
                            val restoredBean = FileBean(
                                fileId = matchedItem.id,
                                categoryId = matchedItem.cid,
                                name = matchedItem.fileName,
                                size = matchedItem.fileSize,
                                isFolder = false,
                                icoString = matchedItem.ico
                            )
                            ctx.addCachedFile(matchedItem.cid, restoredBean)
                        }
                    }
                    emitSystem(successMessage)
                } else {
                    emitError("trash: 还原失败: ${res.error.ifEmpty { res.message }}")
                }
            } catch (e: Exception) {
                emitError("trash: 还原异常: ${e.message}")
            }
            return@flow
        }

        // 2. 清空回收站（二次危险确认）
        if (isClean) {
            val confirmed =
                ctx.confirm("trash: 警告！确定要清空回收站中的全部文件吗？(yes/no): ")
            if (!confirmed) {
                emitSystem("trash: 已取消清空操作")
                return@flow
            }
            try {
                val res = ctx.fileRepository.recycleCleanAll("")
                if (res.state) {
                    emitSystem("trash: 回收站已成功清空")
                } else {
                    emitError("trash: 清空失败: ${res.error}")
                }
            } catch (e: Exception) {
                emitError("trash: 清空异常: ${e.message}")
            }
            return@flow
        }

        // 3. 列出回收站列表
        if (isList) {
            try {
                val list = ctx.fileRepository.recycleList()
                if (list.recycleBeanList.isEmpty()) {
                    emitSystem("trash: 回收站为空")
                } else {
                    emitSystem("回收站项目列表（共 ${list.recycleBeanList.size} 项）：")
                    for (item in list.recycleBeanList) {
                        emitText(
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
                emitError("trash: 获取回收站列表失败: ${e.message}")
            }
        }
    }
}
