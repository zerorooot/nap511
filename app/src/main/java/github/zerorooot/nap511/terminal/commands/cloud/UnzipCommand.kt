package github.zerorooot.nap511.terminal.commands.cloud

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.GlobMatcher
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.engine.completion.CommandCompleter
import github.zerorooot.nap511.terminal.engine.completion.StandardCompleter
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.worker.UnzipAllFileWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.Locale

/**
 * 115 云端解压命令（unzip）
 *
 * 支持双模式：
 * 1. 结构预览模式（-l）：通过 115 网盘接口直接预览压缩包内文件结构与目录清单。
 * 2. 异步解压模式：支持多文件、通配符批量匹配，并可通过 -p 传递解压密码，任务统一提交至后台 WorkManager (UnzipAllFileWorker)。
 */
class UnzipCommand : TerminalCommand {

    override val name: String = "unzip"

    override val description: String = "云端解压（支持查看压缩包列表及提交云端解压）"

    override val usage: String = "unzip [-l] [-p password] <filename...>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-l", "列出压缩包内的文件结构列表（不解压）"),
        CommandFlag("-p <password>", "设置解压密码")
    )

    override val valueOptions: Set<String> = setOf("-p")

    override val completer: CommandCompleter = StandardCompleter.ARCHIVE_FILES

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val isList = ast.hasFlag("-l")
        val password = ast.getOption("-p") ?: ""
        val fileArgs = ast.rawPositionalValues

        if (fileArgs.isEmpty()) {
            emitError("unzip: missing file operand")
            return@flow
        }

        // 收集匹配的所有 FileBean
        val fileBeansList = mutableListOf<FileBean>()
        var targetCid = ctx.currentCid

        for (fileArg in fileArgs) {
            if (GlobMatcher.hasGlobWildcards(fileArg)) {
                // 包含通配符，在当前目录（或指定父目录）按 GlobMatcher 匹配展开
                val parsed = github.zerorooot.nap511.terminal.context.TerminalPath.parse(fileArg)
                val dirPath = parsed.parentPathString
                val pattern = parsed.targetName

                val searchCid = if (dirPath.isEmpty()) {
                    ctx.currentCid
                } else {
                    ctx.resolvePath(dirPath)?.first
                }

                if (searchCid == null) {
                    emitError("unzip: '$dirPath': No such directory")
                    continue
                }

                targetCid = searchCid
                val dirFiles = ctx.listDirectory(searchCid)
                val matched = dirFiles.filter {
                    !it.isFolder && GlobMatcher.matches(
                        pattern,
                        it.name
                    )
                }
                if (matched.isEmpty()) {
                    emitError("unzip: no match found for '$fileArg'")
                } else {
                    fileBeansList.addAll(matched)
                }
            } else {
                // 非通配符直接解析目标
                when (val resolved = ctx.resolveTarget(fileArg)) {
                    is ResolvedTarget.File -> {
                        targetCid = resolved.parentCid
                        fileBeansList.add(resolved.file)
                    }

                    is ResolvedTarget.Directory -> {
                        emitError("unzip: '$fileArg' is a directory, not an archive")
                    }

                    null -> {
                        emitError("unzip: cannot find '$fileArg': No such file")
                    }
                }
            }
        }

        // 去重 (根据 fileId 或 pickCode)
        val distinctFileBeans = fileBeansList.distinctBy { file ->
            file.fileId.ifEmpty { file.pickCode }
        }

        if (distinctFileBeans.isEmpty()) {
            emitError("unzip: 未找到可解压的文件")
            return@flow
        }

        if (isList) {
            // 预览压缩包内文件结构列表
            for (file in distinctFileBeans) {
                val pickCode = file.pickCode
                if (pickCode.isEmpty()) {
                    emitError("unzip: 文件 '${file.name}' 缺失 pickCode，无法预览")
                    continue
                }
                try {
                    val zipBeanList = ctx.fileRepository.getZipListFile(
                        pickCode = pickCode
                    )
                    emitText("Archive: ${file.name}")
                    if (zipBeanList.list.isNotEmpty()) {
                        emitText(
                            String.format(
                                Locale.getDefault(),
                                "%-12s %-16s %s",
                                "Length",
                                "Date",
                                "Name"
                            )
                        )
                        emitText("--------------------------------------------------")
                        for (item in zipBeanList.list) {
                            emitText(
                                String.format(
                                    Locale.getDefault(),
                                    "%-12s %-16s %s",
                                    item.sizeString.trim(),
                                    item.timeString,
                                    item.fileName
                                )
                            )
                        }
                    } else {
                        emitText("unzip: 压缩包内无可显示文件或暂未完成分析")
                    }
                } catch (e: Exception) {
                    emitError("unzip: 预览 '${file.name}' 失败: ${e.message}")
                }
            }
        } else {
            // 提交云端解压任务至 UnzipAllFileWorker 统一处理
            try {
                emitText("正在提交 ${distinctFileBeans.size} 个解压任务至后台...")

                val listType = object : TypeToken<List<FileBean>>() {}.type
                val listJson = Gson().toJson(distinctFileBeans, listType)
                val cacheFile = File(
                    App.instance.cacheDir,
                    "unzip_tasks_${System.currentTimeMillis()}.json"
                )
                cacheFile.writeText(listJson)

                val dataBuilder = Data.Builder()
                    .putString("listPath", cacheFile.absolutePath)
                    .putString("cid", targetCid)

                if (password.isNotEmpty()) {
                    dataBuilder.putString("pwd", password)
                }

                // 查找失败移动目录 CID
                val moveFailFile =
                    SettingsRepository.getDataSuspend(ConfigKeyUtil.MOVE_FAIL_FILE, "")
                if (moveFailFile.isNotEmpty()) {
                    val currentFiles = ctx.listDirectory(targetCid)
                    val errorCid =
                        currentFiles.firstOrNull { it.isFolder && it.name == moveFailFile }?.categoryId
                    if (errorCid != null) {
                        dataBuilder.putString("errorCid", errorCid)
                    }
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequest.Builder(UnzipAllFileWorker::class.java)
                    .setConstraints(constraints)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag("UnzipAllFileWorkerOneTimeWorkRequest")
                    .setInputData(dataBuilder.build())
                    .build()

                val workManager =
                    WorkManager.getInstance(App.instance.applicationContext)
                workManager.enqueueUniqueWork(
                    "unzipAllFileWorker", ExistingWorkPolicy.APPEND_OR_REPLACE, request
                )

                ctx.invalidateCache(targetCid)
                emitText("unzip: 已成功提交 ${distinctFileBeans.size} 个解压任务到后台 UnzipAllFileWorker 处理 (${distinctFileBeans.joinToString { it.name }})")
            } catch (e: Exception) {
                emitError("unzip: 提交解压任务失败: ${e.message}")
            }
        }
    }
}
