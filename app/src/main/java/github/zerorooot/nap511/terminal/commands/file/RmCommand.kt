package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.commands.util.CommandMessages
import github.zerorooot.nap511.terminal.commands.util.RemoveValidationResult
import github.zerorooot.nap511.terminal.commands.util.TargetItemResolver
import github.zerorooot.nap511.terminal.commands.util.TerminalExceptionHandler
import github.zerorooot.nap511.terminal.context.TerminalContext
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
 * rm 命令确认策略
 */
enum class RmConfirmPolicy {
    /** 显式 -f：强制免确认 */
    FORCE,
    /** 默认无选项：单次汇总确认（避免多文件弹窗轰炸） */
    SUMMARY,
    /** 显式 -i：逐项交互确认（保留用户精细挑选保留项的控制权） */
    STEP_BY_STEP
}

/**
 * 删除操作执行计划
 *
 * @property confirmPolicy 确认策略（FORCE 免确认、SUMMARY 默认汇总确认、STEP_BY_STEP 逐项确认）
 * @property recursive 是否递归删除目录（对应 -r / -R 开关）
 * @property targets 待删除的目标路径列表
 */
data class RmPlan(
    val confirmPolicy: RmConfirmPolicy,
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
     * 默认无选项时采用 SUMMARY 单次汇总确认，避免通配符多文件展开弹窗轰炸；
     * 显式指定 -i 时采用 STEP_BY_STEP 逐项精细挑选确认。
     */
    private fun resolveConfirmPolicy(ast: CommandInvocationAst): RmConfirmPolicy {
        var lastPolicy: RmConfirmPolicy? = null
        for (arg in ast.rawArgs) {
            if (arg == "--") break
            if (arg.startsWith("-") && arg.length > 1 && !arg.startsWith("--")) {
                for (ch in arg.substring(1)) {
                    when (ch) {
                        'f', 'y' -> lastPolicy = RmConfirmPolicy.FORCE
                        'i' -> lastPolicy = RmConfirmPolicy.STEP_BY_STEP
                    }
                }
            }
        }
        return lastPolicy ?: RmConfirmPolicy.SUMMARY
    }

    override suspend fun executePlan(
        ctx: TerminalContext,
        plan: RmPlan,
        collector: FlowCollector<TerminalOutput>
    ) {
        val isForce = plan.confirmPolicy == RmConfirmPolicy.FORCE
        val validItems = mutableListOf<BatchTargetItem>()

        // 阶段一：前置解析与 POSIX 容错式校验（即时报告错误，收集有效合法目标）
        for (target in plan.targets) {
            val resolvedItem: BatchTargetItem? = try {
                // TargetItemResolver 已在源头注入完整路径 (fullPath / path)，无需任何外部补丁
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
                    validItems.add(resolvedItem)
                }
            }
        }

        if (validItems.isEmpty()) return

        // 内存拓扑剪枝：基于 path 前缀及 parentCid 剔除已被包含的深层子孙条目（内置 FID 去重与自身排除）
        val prunedItems = TerminalBatchFileOps.pruneBatchItems(validItems)
        val confirmedItems = mutableListOf<BatchTargetItem>()

        // 阶段二：分层交互确认
        when (plan.confirmPolicy) {
            RmConfirmPolicy.FORCE -> {
                confirmedItems.addAll(prunedItems)
            }
            RmConfirmPolicy.SUMMARY -> {
                val prompt = if (prunedItems.size == 1) {
                    "rm: 是否确认删除 '${prunedItems.first().displayName}'? (yes/no): "
                } else {
                    "rm: 是否确认删除匹配到的 ${prunedItems.size} 个项目至回收站? (yes/no): "
                }
                val confirmed = ctx.confirm(prompt)
                if (!confirmed) {
                    // 精细化取消提示：单项时输出 cancelDelete，多项汇总时输出 cancelOperation
                    if (prunedItems.size == 1) {
                        collector.emitText(CommandMessages.cancelDelete("rm", prunedItems.first().displayName))
                    } else {
                        collector.emitText(CommandMessages.cancelOperation("rm"))
                    }
                    return
                }
                confirmedItems.addAll(prunedItems)
            }
            RmConfirmPolicy.STEP_BY_STEP -> {
                // 显式 -i：逐项询问确认，赋予用户精细挑选保留项的控制权
                for (item in prunedItems) {
                    val confirmed = ctx.confirm("rm: 是否确认删除 '${item.displayName}'? (yes/no): ")
                    if (confirmed) {
                        confirmedItems.add(item)
                    } else {
                        collector.emitText(CommandMessages.cancelDelete("rm", item.displayName))
                    }
                }
                if (confirmedItems.isEmpty()) return
            }
        }

        // 阶段三：按父目录分组执行批量删除流水线
        val groupedByParent = confirmedItems.groupBy { it.parentCid }
        var totalSuccessCount = 0
        var hasAnyFailure = false

        for ((parentCid, items) in groupedByParent) {
            try {
                val result = TerminalBatchFileOps.deleteGroup(ctx, parentCid, items)
                totalSuccessCount += result.successCount
                if (result.failureCount > 0) {
                    hasAnyFailure = true
                    // 精细化错误回显：单项失败精确显示单文件名，批量失败输出批量汇总
                    if (items.size == 1) {
                        val single = items.first()
                        val err = result.error.ifEmpty { "未知错误" }
                        collector.emitError("rm: 删除失败 '${single.displayName}': $err")
                    } else {
                        collector.emitError(CommandMessages.batchDeleteFailed("rm", result.failureCount, result.error))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                hasAnyFailure = true
                collector.emitError("rm: 删除失败: ${TerminalExceptionHandler.extractErrorMessage(e)}")
            }
        }

        // 阶段四：全局统一汇总回显（无论单目录还是跨目录批量删除，均统一输出单次原子汇总）
        if (!hasAnyFailure) {
            if (confirmedItems.size == 1) {
                collector.emitText(CommandMessages.movedToTrash("rm", confirmedItems.first().displayName))
            } else {
                collector.emitText(CommandMessages.batchDeleteSuccess("rm", totalSuccessCount))
            }
        } else {
            if (totalSuccessCount > 0) {
                collector.emitText(CommandMessages.batchDeletePartial("rm", totalSuccessCount, confirmedItems.size))
            }
        }
    }
}
