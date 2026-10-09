package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.commands.util.TableAlignment
import github.zerorooot.nap511.terminal.commands.util.TableFormatter
import github.zerorooot.nap511.terminal.commands.util.TerminalExceptionHandler
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.ParsedContext
import github.zerorooot.nap511.terminal.engine.archetype.ActionDispatchCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitSystem
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.FlowCollector
import java.util.Locale
import github.zerorooot.nap511.terminal.engine.completion.CommandCompleter

/**
 * 回收站操作密封类定义
 */
sealed interface TrashAction {
    /** 还原指定文件或 RID */
    data class Revert(val ridOrName: String) : TrashAction
    /** 清空回收站全部文件 */
    data object Clean : TrashAction
    /** 查看回收站列表 */
    data object List : TrashAction
}

/**
 * 网盘回收站管理命令（trash）
 *
 * 继承 [ActionDispatchCommand]，在编译期根据参数静态决议为 [TrashAction] 强类型密封动作，
 * 执行期通过 exhaustive when 完成动作分发，彻底消除交叉嵌套的 if-else 判定。
 */
class TrashCommand : ActionDispatchCommand<TrashAction>() {

    override val name: String = "trash"

    override val description: String = "网盘回收站管理（查看、还原或清空）"

    override val usage: String = "trash [-l] [-r <rid|name>] [-c]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "列出回收站中的文件列表及 rid"),
        CommandFlag("-r <rid|name>", "按 rid 或文件名还原文件到原目录"),
        CommandFlag("-c", "清空回收站全部文件")
    )

    override val valueOptions: Set<String> = setOf("-r")

    override val completer: CommandCompleter =
        object : CommandCompleter {
            override suspend fun resolveCandidates(
                ctx: TerminalContext,
                parsedContext: ParsedContext,
                defaultFiles: List<FileBean>
            ): List<FileBean> {
                return runCatching {
                    ctx.fileRepository.recycleList().recycleBeanList.map { item ->
                        FileBean(
                            name = item.fileName,
                            fileId = item.id,
                            size = item.fileSize,
                            isFolder = item.isFolder
                        )
                    }
                }.getOrDefault(emptyList())
            }
        }

    override fun compileAction(ast: CommandInvocationAst): Result<TrashAction> {
        // 1. 位置参数防御性拦截（高内聚、职责分明）：
        // trash 仅作为回收站内部数据管理工具（查看、还原、清空），不支持通过操作数移入回收站。
        // 工作区文件移入回收站的职责严格归属于 rm 命令，此处进行 Fail-Fast 拦截并友好指引。
        if (ast.positionalArgs.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException("不支持位置参数，若需将文件移入回收站请使用 'rm' 命令")
            )
        }

        // 2. 键值选项参数完整性校验：-r 必须附带待还原的 RID 或文件名
        if (ast.hasFlag("-r") && ast.getOption("-r") == null) {
            return Result.failure(
                IllegalArgumentException("option requires an argument -- r")
            )
        }

        val ridToRevert = ast.getOption("-r")
        if (ridToRevert != null) {
            return Result.success(TrashAction.Revert(ridToRevert))
        }
        if (ast.hasFlag("-c")) {
            return Result.success(TrashAction.Clean)
        }
        return Result.success(TrashAction.List)
    }

    override suspend fun dispatch(
        ctx: TerminalContext,
        action: TrashAction,
        collector: FlowCollector<TerminalOutput>
    ) {
        when (action) {
            is TrashAction.Revert -> handleRevert(ctx, action.ridOrName, collector)
            is TrashAction.Clean -> handleClean(ctx, collector)
            is TrashAction.List -> handleList(ctx, collector)
        }
    }

    private suspend fun handleRevert(
        ctx: TerminalContext,
        ridToRevert: String,
        collector: FlowCollector<TerminalOutput>
    ) {
        try {
            val recycleList = runCatching {
                ctx.fileRepository.recycleList().recycleBeanList
            }.getOrDefault(emptyList())

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
                collector.emitError("trash: 未在回收站中找到 '$ridToRevert'")
                return
            }

            val res = ctx.fileRepository.revert(actualRid)
            if (res.state) {
                val targetCid = matchedItem?.cid ?: recycleList.firstOrNull { it.id == actualRid }?.cid
                if (!targetCid.isNullOrEmpty()) {
                    // 更新缓存
                    ctx.listDirectory(targetCid, forceRefresh = true)
                }
                collector.emitSystem(successMessage)
            } else {
                collector.emitError("trash: 还原失败: ${TerminalExceptionHandler.extractErrorMessage(res, "操作失败")}")
            }
        } catch (e: Exception) {
            collector.emitError("trash: 还原异常: ${e.message}")
        }
    }

    private suspend fun handleClean(
        ctx: TerminalContext,
        collector: FlowCollector<TerminalOutput>
    ) {
        val confirmed = ctx.confirm("trash: 警告！确定要清空回收站中的全部文件吗？(yes/no): ")
        if (!confirmed) {
            collector.emitSystem("trash: 已取消清空操作")
            return
        }
        try {
            val res = ctx.fileRepository.recycleCleanAll("")
            if (res.state) {
                collector.emitSystem("trash: 回收站已成功清空")
            } else {
                collector.emitError("trash: 清空失败: ${TerminalExceptionHandler.extractErrorMessage(res, "操作失败")}")
            }
        } catch (e: Exception) {
            collector.emitError("trash: 清空异常: ${e.message}")
        }
    }

    private suspend fun handleList(
        ctx: TerminalContext,
        collector: FlowCollector<TerminalOutput>
    ) {
        try {
            val list = ctx.fileRepository.recycleList()
            if (list.recycleBeanList.isEmpty()) {
                collector.emitSystem("trash: 回收站为空")
            } else {
                collector.emitSystem("回收站项目列表（共 ${list.recycleBeanList.size} 项）：")
                for (item in list.recycleBeanList) {
                    val paddedRid = TableFormatter.padCell("rid: ${item.id}", 20, TableAlignment.LEFT)
                    collector.emitText("$paddedRid ${item.fileName} (${item.fileSize})")
                }
            }
        } catch (e: Exception) {
            collector.emitError("trash: 获取回收站列表失败: ${e.message}")
        }
    }
}
