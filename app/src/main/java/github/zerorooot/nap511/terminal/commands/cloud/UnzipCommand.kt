package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.terminal.commands.util.TableAlignment
import github.zerorooot.nap511.terminal.commands.util.TableFormatter
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.StreamTargetCollector
import github.zerorooot.nap511.terminal.engine.archetype.ActionDispatchCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.engine.completion.CommandCompleter
import github.zerorooot.nap511.terminal.engine.completion.StandardCompleter
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.worker.UnzipQueueManager
import github.zerorooot.nap511.worker.UnzipTaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

/**
 * 115 云端解压动作密封接口
 */
sealed interface UnzipAction {
    /** 查看压缩包目录清单 (-l) */
    data class ListEntries(val filePaths: List<String>) : UnzipAction

    /** 提交后台异步解压任务 */
    data class SubmitExtract(
        val filePaths: List<String>,
        val password: String
    ) : UnzipAction
}

/**
 * 115 云端解压命令（unzip）
 *
 * 继承 [ActionDispatchCommand]，彻底切分预览与排队两类动作：
 * 1. 结构预览模式（-l）：通过 115 网盘接口直接预览压缩包内文件结构与目录清单；
 * 2. 异步解压模式：支持多文件解压，并可通过 -p 传递解压密码，任务统一提交至后台 WorkManager；
 * 3. 彻底废除内部手写通配符搜索，参数交由管道引擎统一多级展开；
 * 4. 支持管道输入（如 `find -name '*.zip' -print0 | unzip`），自动批量收集并处理。
 */
class UnzipCommand : ActionDispatchCommand<UnzipAction>() {

    override val name: String = "unzip"

    override val description: String = "云端解压（支持查看压缩包列表及提交云端解压）"

    override val usage: String = "unzip [-l] [-p password] <filename...>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "列出压缩包内的文件结构列表（不解压）"),
        CommandFlag("-p <password>", "设置解压密码")
    )

    override val valueOptions: Set<String> = setOf("-p")

    override val completer: CommandCompleter = StandardCompleter.ARCHIVE_FILES

    override fun compileAction(ast: CommandInvocationAst): Result<UnzipAction> {
        val isList = ast.hasFlag("-l")
        val password = ast.getOption("-p") ?: ""
        val filePaths = ast.rawPositionalValues

        val action = if (isList) {
            UnzipAction.ListEntries(filePaths)
        } else {
            UnzipAction.SubmitExtract(filePaths, password)
        }
        return Result.success(action)
    }

    override suspend fun dispatch(
        ctx: TerminalContext,
        action: UnzipAction,
        stdin: Flow<String>,
        collector: FlowCollector<TerminalOutput>
    ) {
        val targetPaths = when (action) {
            is UnzipAction.ListEntries -> action.filePaths.toMutableList()
            is UnzipAction.SubmitExtract -> action.filePaths.toMutableList()
        }

        // 管道支持：通过 StreamTargetCollector 摄取来自 stdin 的目标
        val stdinFiles = StreamTargetCollector.collectFromStdin(stdin)
        targetPaths.addAll(stdinFiles)

        if (targetPaths.isEmpty()) {
            collector.emitError("unzip: missing file operand")
            return
        }

        // 解析并收集所有 FileBean
        val fileBeansList = mutableListOf<FileBean>()
        var targetCid = ctx.currentCid

        for (path in targetPaths) {
            when (val resolved = ctx.resolveTarget(path)) {
                is ResolvedTarget.File -> {
                    targetCid = resolved.parentCid
                    fileBeansList.add(resolved.file)
                }

                is ResolvedTarget.Directory -> {
                    collector.emitError("unzip: '$path' is a directory, not an archive")
                }

                null -> {
                    collector.emitError("unzip: cannot find '$path': No such file")
                }
            }
        }

        // 去重 (根据 fileId 或 pickCode)
        val distinctFileBeans = fileBeansList.distinctBy { file ->
            file.fileId.ifEmpty { file.pickCode }
        }

        if (distinctFileBeans.isEmpty()) {
            collector.emitError("unzip: 未找到可解压的文件")
            return
        }

        when (action) {
            is UnzipAction.ListEntries -> {
                for (file in distinctFileBeans) {
                    val pickCode = file.pickCode
                    if (pickCode.isEmpty()) {
                        collector.emitError("unzip: 文件 '${file.name}' 缺失 pickCode，无法预览")
                        continue
                    }
                    try {
                        val zipBeanList = ctx.fileRepository.getZipListFile(pickCode = pickCode)
                        collector.emitText("Archive: ${file.name}")
                        if (zipBeanList.list.isNotEmpty()) {
                            val table = TableFormatter.Builder()
                                .addColumn("Length", TableAlignment.LEFT, minWidth = 12)
                                .addColumn("Date", TableAlignment.LEFT, minWidth = 16)
                                .addColumn("Name", TableAlignment.LEFT, minWidth = 4)
                            for (item in zipBeanList.list) {
                                table.addRow(item.sizeString.trim(), item.timeString, item.fileName)
                            }
                            collector.emitText("--------------------------------------------------")
                            for (line in table.build()) {
                                collector.emitText(line)
                            }
                        } else {
                            collector.emitText("unzip: 压缩包内无可显示文件或暂未完成分析")
                        }
                    } catch (e: Exception) {
                        collector.emitError("unzip: 预览 '${file.name}' 失败: ${e.message}")
                    }
                }
            }

            is UnzipAction.SubmitExtract -> {
                try {
                    collector.emitText("正在提交 ${distinctFileBeans.size} 个解压任务至后台...")

                    // 查找失败移动目录 CID
                    val moveFailFile = SettingsRepository.getDataSuspend(ConfigKeyUtil.MOVE_FAIL_FILE, "")
                    val errorCid = if (moveFailFile.isNotEmpty()) {
                        val currentFiles = ctx.listDirectory(targetCid)
                        currentFiles.firstOrNull { it.isFolder && it.name == moveFailFile }?.categoryId
                    } else null

                    // 1. 构造独立任务项
                    val taskItems = distinctFileBeans.map { file ->
                        UnzipTaskItem(
                            fileBean = file,
                            targetCid = targetCid,
                            password = action.password.takeIf { it.isNotEmpty() },
                            errorCid = errorCid
                        )
                    }

                    // 2. 追加至全局解压队列并触发 Worker (KEEP 策略)
                    UnzipQueueManager.enqueueAndStartWorker(taskItems)

                    ctx.invalidateCache(targetCid)
                    collector.emitText(
                        "unzip: 已成功提交 ${distinctFileBeans.size} 个解压任务到后台 UnzipAllFileWorker 处理 (${distinctFileBeans.joinToString { it.name }})"
                    )
                } catch (e: Exception) {
                    collector.emitError("unzip: 提交解压任务失败: ${e.message}")
                }
            }
        }
    }
}
