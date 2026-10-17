package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.terminal.commands.util.CommandMessages
import github.zerorooot.nap511.terminal.commands.util.TableAlignment
import github.zerorooot.nap511.terminal.commands.util.TableFormatter
import github.zerorooot.nap511.terminal.commands.util.TerminalExceptionHandler
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
import kotlinx.coroutines.CancellationException
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
            collector.emitError(CommandMessages.missingFileOperand("unzip"))
            return
        }

        // 收集待解压文件与其各自的所属父目录 CID
        data class ResolvedArchive(val file: FileBean, val targetCid: String)
        val archivesList = mutableListOf<ResolvedArchive>()

        for (path in targetPaths) {
            when (val resolved = ctx.resolveTarget(path)) {
                is ResolvedTarget.File -> {
                    val fileTargetCid = resolved.parentCid.ifEmpty {
                        resolved.file.categoryId.ifEmpty { ctx.currentCid }
                    }
                    archivesList.add(ResolvedArchive(resolved.file, fileTargetCid))
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
        val distinctArchives = archivesList.distinctBy { archive ->
            archive.file.fileId.ifEmpty { archive.file.pickCode }
        }

        if (distinctArchives.isEmpty()) {
            collector.emitError("unzip: 未找到可解压的文件")
            return
        }

        when (action) {
            is UnzipAction.ListEntries -> {
                for (archive in distinctArchives) {
                    val file = archive.file
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
                        if (e is CancellationException) throw e
                        collector.emitError("unzip: 预览 '${file.name}' 失败: ${TerminalExceptionHandler.extractErrorMessage(e)}")
                    }
                }
            }

            is UnzipAction.SubmitExtract -> {
                try {
                    collector.emitText("正在提交 ${distinctArchives.size} 个解压任务至后台...")

                    // 查找失败移动目录 CID（按 targetCid 缓存，避免重复网络请求，捕获测试环境无 Android Context 异常）
                    val moveFailFile = runCatching {
                        SettingsRepository.getDataSuspend(ConfigKeyUtil.MOVE_FAIL_FILE, "")
                    }.getOrDefault("")
                    val errorCidCache = mutableMapOf<String, String?>()

                    // 1. 构造独立任务项，每个任务项绑定自身准确的 targetCid
                    val taskItems = distinctArchives.map { archive ->
                        val errCid = if (moveFailFile.isNotEmpty()) {
                            errorCidCache.getOrPut(archive.targetCid) {
                                val currentFiles = ctx.listDirectory(archive.targetCid)
                                currentFiles.firstOrNull { it.isFolder && it.name == moveFailFile }?.categoryId
                            }
                        } else null

                        UnzipTaskItem(
                            fileBean = archive.file,
                            targetCid = archive.targetCid,
                            password = action.password.takeIf { it.isNotEmpty() },
                            errorCid = errCid
                        )
                    }

                    // 2. 追加至全局解压队列并触发 Worker (KEEP 策略)
                    UnzipQueueManager.enqueueAndStartWorker(taskItems)
                    val fileNames = distinctArchives.joinToString { it.file.name }
                    collector.emitText(
                        "unzip: 已成功提交 ${distinctArchives.size} 个解压任务到后台 UnzipAllFileWorker 处理 ($fileNames)"
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    collector.emitError("unzip: 提交解压任务失败: ${TerminalExceptionHandler.extractErrorMessage(e)}")
                }
            }
        }
    }
}
