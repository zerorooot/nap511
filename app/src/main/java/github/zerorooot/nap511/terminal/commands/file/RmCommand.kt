package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.commands.util.CommandMessages
import github.zerorooot.nap511.terminal.commands.util.RemoveValidationResult
import github.zerorooot.nap511.terminal.commands.util.TargetItemResolver
import github.zerorooot.nap511.terminal.commands.util.TerminalExceptionHandler
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.StreamTargetCollector
import github.zerorooot.nap511.terminal.engine.archetype.ConfirmPolicy
import github.zerorooot.nap511.terminal.engine.archetype.MutationCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

/**
 * 删除操作执行计划
 *
 * @property confirmPolicy 确认策略（FORCE 免确认或 INTERACTIVE 交互确认）
 * @property recursive 是否递归删除目录（对应 -r / -R 开关）
 * @property targets 待删除的目标路径列表
 */
data class RmPlan(
    val confirmPolicy: ConfirmPolicy,
    val recursive: Boolean = false,
    val targets: List<String>
)

/**
 * 删除文件或目录至回收站命令（rm）
 *
 * 继承 [MutationCommand]，在编译期根据 `-r`/`-R` 递归开关及 `-f`/`-i` 选项提取安全确认策略与待删除操作数。
 *
 * 核心规范与安全防御（严格对齐 POSIX.1-2017/2024 与 115 安全基线）：
 * 1. 递归删除约束：删除目录必须携带 `-r` 或 `-R` 开关，未指定时强行拦截并报错 `'$target': Is a directory`；
 * 2. 健壮静默机制：目标在网盘中确认不存在且包含 `-f` 时静默跳过，但若遇到弱网超时或连接异常，无论是否带 `-f` 均强制报错，杜绝“假删除”；
 * 3. POSIX 覆盖规则：回溯参数序列以最后出现的 `-i` 或 `-f` 为准（如 `rm -f -i` 提示确认，`rm -i -f` 免确认）；
 * 4. 高危边界拦截：严禁删除 `.`、`..`、根目录 `/`、当前工作目录以及祖先目录；
 * 5. 管道支持：通过 [StreamTargetCollector] 从标准输入 stdin 摄取待删除目标（如 `find ... -print0 | rm -f`）。
 */
class RmCommand : MutationCommand<RmPlan>() {

    override val name: String = "rm"

    override val description: String = "删除当前目录或指定路径下的指定文件或文件夹至回收站"

    override val usage: String = "rm [-r|-R] [-f|-i] <file...>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-f", "强制删除，免确认并静默忽略不存在的文件"),
        CommandFlag("-i", "交互式删除，每次删除前进行二次确认"),
        CommandFlag("-r, -R", "支持递归删除目录")
    )

    override suspend fun compilePlan(
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Result<RmPlan> {
        val targets = ast.rawPositionalValues.toMutableList()
        // 管道支持：通过 StreamTargetCollector 摄取来自 stdin 的目标，自动兼容 \0 (NUL) 与 \n 定界
        val stdinTargets = StreamTargetCollector.collectFromStdin(stdin)
        targets.addAll(stdinTargets)

        if (targets.isEmpty()) {
            return Result.failure(Exception("missing operand"))
        }

        val recursive = ast.hasFlag("-r", "-R")
        val policy = resolveConfirmPolicy(ast)

        return Result.success(RmPlan(confirmPolicy = policy, recursive = recursive, targets = targets))
    }

    /**
     * 遵循 POSIX Guideline 11 解析确认策略：
     * 命令行中后出现的选项覆盖先出现的选项（以 ast.rawArgs 中的最终选项为准）；
     * 默认策略为 INTERACTIVE 交互确认，保护云端网盘数据。
     */
    private fun resolveConfirmPolicy(ast: CommandInvocationAst): ConfirmPolicy {
        var lastPolicy: ConfirmPolicy? = null
        for (arg in ast.rawArgs) {
            if (arg == "--") break
            if (arg.startsWith("-") && arg.length > 1 && !arg.startsWith("--")) {
                for (ch in arg.substring(1)) {
                    when (ch) {
                        'f', 'y' -> lastPolicy = ConfirmPolicy.FORCE
                        'i' -> lastPolicy = ConfirmPolicy.INTERACTIVE
                    }
                }
            }
        }
        return lastPolicy ?: ConfirmPolicy.INTERACTIVE
    }

    override suspend fun executePlan(
        ctx: TerminalContext,
        plan: RmPlan,
        collector: FlowCollector<TerminalOutput>
    ) {
        val isForce = plan.confirmPolicy == ConfirmPolicy.FORCE
        val confirmedItems = mutableListOf<BatchTargetItem>()

        // 阶段一：目标解析、前置安全校验及交互确认（收集确认删除项）
        for (target in plan.targets) {
            val resolvedItem: BatchTargetItem? = try {
                TargetItemResolver.resolveToBatchItem(ctx, target)
            } catch (e: Throwable) {
                // 协程取消或中断（Ctrl+C）必须重抛，保证终端即时响应
                if (e is CancellationException) throw e
                // 严密防御：网络层异常无论是否携带 -f 均强制报错，杜绝假删除误导
                val errMsg = TerminalExceptionHandler.extractErrorMessage(e)
                collector.emitError("rm: cannot access '$target': $errMsg")
                continue
            }

            // 文件在网盘中确实不存在的判定
            if (resolvedItem == null) {
                if (isForce) {
                    // POSIX 规范：rm -f 静默跳过不存在的操作数
                    continue
                } else {
                    collector.emitError(CommandMessages.cannotRemove("rm", target))
                    continue
                }
            }

            // 委托 TargetItemResolver 执行高危边界与 -r 递归删除安全校验
            when (val validation = TargetItemResolver.validateRemove(resolvedItem, plan.recursive, ctx, target)) {
                is RemoveValidationResult.Error -> {
                    collector.emitError(validation.message)
                    continue
                }
                is RemoveValidationResult.Valid -> {
                    // 校验通过
                }
            }

            // 若未开启免确认，向终端触发交互确认
            if (plan.confirmPolicy == ConfirmPolicy.INTERACTIVE) {
                val confirmed = ctx.confirm("rm: 是否确认删除 '${resolvedItem.displayName}'? (yes/no): ")
                if (!confirmed) {
                    collector.emitText(CommandMessages.cancelDelete("rm", resolvedItem.displayName))
                    continue
                }
            }

            confirmedItems.add(resolvedItem)
        }

        if (confirmedItems.isEmpty()) return

        // 阶段二：按有效父目录 parentCid 分组聚合
        val groupedByParent = confirmedItems.groupBy { it.parentCid }

        // 阶段三：分目录执行批量删除或降级单项删除
        for ((parentCid, items) in groupedByParent) {
            try {
                if (items.size > 1 && !parentCid.isNullOrEmpty()) {
                    // 同目录多项：组装 deleteMultiple 批量删除
                    val res = TerminalBatchFileOps.deleteGroup(ctx, parentCid, items)
                    if (res.state) {
                        collector.emitText(CommandMessages.batchMovedToTrash("rm", items.size))
                    } else {
                        val err = TerminalExceptionHandler.extractErrorMessage(res, "未知错误")
                        collector.emitError("rm: 批量删除失败 (${items.size} 个项目): $err")
                    }
                } else {
                    // 单项处理：保持传统单项提示
                    val single = items.first()
                    val res = TerminalBatchFileOps.deleteGroup(ctx, parentCid, listOf(single))
                    if (res.state) {
                        collector.emitText(CommandMessages.movedToTrash("rm", single.displayName))
                    } else {
                        val err = TerminalExceptionHandler.extractErrorMessage(res, "未知错误")
                        collector.emitError("rm: 删除失败 '${single.displayName}': $err")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                collector.emitError("rm: 删除失败: ${TerminalExceptionHandler.extractErrorMessage(e)}")
            }
        }
    }
}
