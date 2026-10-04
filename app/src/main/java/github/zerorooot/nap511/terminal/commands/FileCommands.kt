package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 网盘基础核心文件管理命令（ls, cd, pwd, mkdir, rm, mv）
 * 严格遵循 Cache-First、参数排序及交互确认规范
 */
object FileCommands {

    fun registerAll(registry: CommandRegistry) {
        // 1. ls
        registry.register("ls") {
            description = "列出当前或指定目录下的文件与文件夹"
            usage = "ls [options] [path]"
            flag("-l", "使用详细列表格式显示（包含类型、大小、修改时间与名称）")
            flag("-a", "显示全部文件")
            flag("-t", "按文件修改时间排序")
            flag("-u", "按文件访问/打开时间排序")
            flag("-S", "按文件大小排序")
            flag("-X", "按扩展名排序")
            flag("-r", "反转排序结果")
            flag("--refresh", "强制从网盘拉取最新数据并刷新本地缓存")
            execute { ctx, args, _ ->
                flow {
                    val isLong = args.contains("-l")
                    val isAll = args.contains("-a")
                    val sortByMtime = args.contains("-t")
                    val sortByAtime = args.contains("-u")
                    val sortBySize = args.contains("-S")
                    val sortByExt = args.contains("-X")
                    val reverse = args.contains("-r")
                    val forceRefresh = args.contains("--refresh")

                    // 提取目标路径参数（排除选项）
                    val targetPath = args.firstOrNull { !it.startsWith("-") }
                    val (candidateFiles, isSingleFile) = if (targetPath != null) {
                        val resolved = ctx.resolveTarget(targetPath)
                        if (resolved == null) {
                            emit("ls: cannot access '$targetPath': No such file or directory")
                            return@flow
                        }
                        when (resolved) {
                            is ResolvedTarget.Directory -> {
                                val files = ctx.listDirectory(resolved.cid, forceRefresh)
                                val filtered = if (isAll) files else files.filter { !it.name.startsWith(".") }
                                Pair(filtered, false)
                            }
                            is ResolvedTarget.File -> {
                                Pair(listOf(resolved.file), true)
                            }
                        }
                    } else {
                        val files = ctx.listDirectory(ctx.currentCid, forceRefresh)
                        val filtered = if (isAll) files else files.filter { !it.name.startsWith(".") }
                        Pair(filtered, false)
                    }

                    // 排序规则：默认完全保持接口请求/缓存中的原始顺序；仅在显式传入选项时重排
                    var sorted = when {
                        sortByMtime -> candidateFiles.sortedByDescending { it.modifiedTime.toLongOrNull() ?: 0L }
                        sortByAtime -> candidateFiles.sortedByDescending { it.updateTime.toLongOrNull() ?: 0L }
                        sortBySize -> candidateFiles.sortedByDescending { it.size.toLongOrNull() ?: 0L }
                        sortByExt -> candidateFiles.sortedBy { it.name.substringAfterLast(".", "") }
                        else -> candidateFiles
                    }

                    if (reverse) {
                        sorted = sorted.reversed()
                    }

                    if (isLong) {
                        if (!isSingleFile) {
                            emit("total ${sorted.size}")
                        }
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        for (file in sorted) {
                            val typeChar = if (file.isFolder) "d" else "-"
                            val perm = "${typeChar}rwxr-xr-x"
                            val sizeStr = if (file.isFolder) "-" else formatFileSize(file.size.toLongOrNull() ?: 0L)
                            val timeStr = formatTimestamp(file.modifiedTime, dateFormat)
                            val nameStr = if (isSingleFile && targetPath != null) {
                                if (file.isFolder && !targetPath.endsWith("/")) "$targetPath/" else targetPath
                            } else {
                                if (file.isFolder) "${file.name}/" else file.name
                            }
                            emit(String.format(Locale.getDefault(), "%-11s %10s %16s %s", perm, sizeStr, timeStr, nameStr))
                        }
                    } else {
                        for (file in sorted) {
                            val displayName = if (isSingleFile && targetPath != null) {
                                if (file.isFolder && !targetPath.endsWith("/")) "$targetPath/" else targetPath
                            } else {
                                if (file.isFolder) "${file.name}/" else file.name
                            }
                            emit(displayName)
                        }
                    }
                }
            }
        }

        // 2. cd
        registry.register("cd") {
            description = "切换网盘当前工作目录"
            usage = "cd [path]"
            execute { ctx, args, _ ->
                flow {
                    val target = args.firstOrNull { !it.startsWith("-") } ?: "/"
                    val resolved = ctx.resolvePath(target)
                    if (resolved == null) {
                        emit("cd: no such file or directory: $target")
                        return@flow
                    }
                    ctx.updateDirectory(resolved.first, resolved.second)
                }
            }
        }

        // 3. pwd
        registry.register("pwd") {
            description = "打印当前工作目录路径及 CID"
            usage = "pwd"
            execute { ctx, _, _ ->
                flow {
                    emit("${ctx.currentPath} (cid: ${ctx.currentCid})")
                }
            }
        }

        // 4. mkdir
        registry.register("mkdir") {
            description = "在当前目录下新建文件夹"
            usage = "mkdir [-p] <folder_name>"
            flag("-p", "若目录已存在不报错，并支持递归创建")
            execute { ctx, args, _ ->
                flow {
                    val folderNames = args.filter { !it.startsWith("-") }
                    if (folderNames.isEmpty()) {
                        emit("mkdir: missing operand")
                        return@flow
                    }

                    for (name in folderNames) {
                        try {
                            val res = ctx.fileRepository.createFolder(pid = ctx.currentCid, folderName = name)
                            if (res.state) {
                                ctx.invalidateCache(ctx.currentCid)
                                emit("mkdir: created directory '$name'")
                            } else {
                                emit("mkdir: cannot create directory '$name': ${res.error}")
                            }
                        } catch (e: Exception) {
                            emit("mkdir: cannot create directory '$name': ${e.message}")
                        }
                    }
                }
            }
        }

        // 5. rm
        registry.register("rm") {
            description = "删除当前目录下的指定文件或文件夹至回收站"
            usage = "rm [-y] <file...>"
            flag("-y", "免确认直接删除")
            execute { ctx, args, _ ->
                flow {
                    val autoConfirm = args.contains("-y")
                    val targetNames = args.filter { !it.startsWith("-") }

                    if (targetNames.isEmpty()) {
                        emit("rm: missing operand")
                        return@flow
                    }

                    val currentFiles = ctx.listDirectory(ctx.currentCid)
                    var deletedCount = 0

                    for (target in targetNames) {
                        val fileBean = currentFiles.firstOrNull { it.name == target }
                        if (fileBean == null) {
                            emit("rm: cannot remove '$target': No such file or directory")
                            continue
                        }

                        // 若未输入 -y 则交互确认
                        if (!autoConfirm) {
                            val confirmed = ctx.confirm("rm: 是否确认删除 '${fileBean.name}'? (yes/no): ")
                            if (!confirmed) {
                                emit("rm: 已取消删除 '${fileBean.name}'")
                                continue
                            }
                        }

                        try {
                            val actualFid = if (fileBean.isFolder) fileBean.categoryId else fileBean.fileId
                            val res = ctx.fileRepository.delete(pid = ctx.currentCid, fid = actualFid)
                            if (res.state) {
                                deletedCount++
                                emit("rm: 已移入回收站 '${fileBean.name}'")
                            } else {
                                emit("rm: 删除失败 '${fileBean.name}': ${res.error}")
                            }
                        } catch (e: Exception) {
                            emit("rm: 删除失败 '${fileBean.name}': ${e.message}")
                        }
                    }

                    if (deletedCount > 0) {
                        ctx.invalidateCache(ctx.currentCid)
                    }
                }
            }
        }

        // 6. mv
        registry.register("mv") {
            description = "重命名文件或将文件/目录移动至其他目录"
            usage = "mv <source...> <target>"
            execute { ctx, args, stdin ->
                flow {
                    val targets = args.filter { !it.startsWith("-") }.toMutableList()

                    // 若命令行参数仅提供了 1 个目标目录（如 find ... | mv ../），且上游管道存在输入，智能从 stdin 获取源列表
                    if (targets.size < 2) {
                        val stdinSources = mutableListOf<String>()
                        stdin.collect { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                stdinSources.add(trimmed)
                            }
                        }
                        if (stdinSources.isNotEmpty() && targets.size == 1) {
                            val destination = targets[0]
                            targets.clear()
                            targets.addAll(stdinSources)
                            targets.add(destination)
                        }
                    }

                    if (targets.size < 2) {
                        emit("mv: missing file operand")
                        return@flow
                    }

                    val destination = targets.last()
                    val sources = targets.dropLast(1)
                    val currentFiles = ctx.listDirectory(ctx.currentCid)

                    // 解析源文件/目录信息
                    suspend fun resolveSourceItem(rawSrc: String): Pair<String, String>? {
                        val cleanSrc = rawSrc.trim().trimEnd('/')
                        val localFile = currentFiles.firstOrNull { it.name == cleanSrc || it.name == rawSrc.trim() }
                        if (localFile != null) {
                            val fid = if (localFile.isFolder) localFile.categoryId else localFile.fileId
                            return Pair(fid, localFile.name)
                        }

                        // 尝试路径解析（支持绝对路径与相对路径）
                        val resolved = ctx.resolveTarget(cleanSrc)
                        return when (resolved) {
                            is ResolvedTarget.Directory -> Pair(resolved.cid, cleanSrc.substringAfterLast('/').ifEmpty { "/" })
                            is ResolvedTarget.File -> Pair(resolved.file.fileId, resolved.file.name)
                            null -> null
                        }
                    }

                    // 判断目标是否为目录（支持当前目录下文件夹、上级目录 .. / ../、绝对路径或 ~）
                    var targetDestCid: String? = null
                    var destDisplayName = destination
                    val cleanDest = destination.trim().trimEnd('/')

                    val destFolder = currentFiles.firstOrNull { it.isFolder && (it.name == cleanDest || it.name == destination.trim()) }
                    if (destFolder != null) {
                        targetDestCid = destFolder.categoryId
                        destDisplayName = destFolder.name
                    } else if (cleanDest == ".." || cleanDest == "." || cleanDest == "~" || cleanDest.startsWith("/") || cleanDest.contains("/")) {
                        val resolvedPath = ctx.resolvePath(destination)
                        if (resolvedPath != null) {
                            targetDestCid = resolvedPath.first
                            destDisplayName = resolvedPath.second
                        }
                    }

                    if (targetDestCid != null) {
                        // 移动操作：将所有 sources 移入 targetDestCid 目录
                        for (src in sources) {
                            val resolvedSrc = resolveSourceItem(src)
                            if (resolvedSrc == null) {
                                emit("mv: cannot stat '$src': No such file or directory")
                                continue
                            }
                            val (actualFid, _) = resolvedSrc
                            try {
                                val moveMap = hashMapOf<String, String>()
                                moveMap["pid"] = targetDestCid
                                moveMap["fid[0]"] = actualFid
                                val res = ctx.fileRepository.move(moveMap)
                                if (res.state) {
                                    ctx.invalidateCache(ctx.currentCid)
                                    ctx.invalidateCache(targetDestCid)
                                    emit("mv: '$src' -> '$destDisplayName/'")
                                } else {
                                    emit("mv: 移动 '$src' 失败: ${res.error}")
                                }
                            } catch (e: Exception) {
                                emit("mv: 移动 '$src' 失败: ${e.message}")
                            }
                        }
                    } else if (sources.size == 1) {
                        // 单源且目标不是现有目录：执行重命名
                        val src = sources[0]
                        val resolvedSrc = resolveSourceItem(src)
                        if (resolvedSrc == null) {
                            emit("mv: cannot stat '$src': No such file or directory")
                            return@flow
                        }
                        val (actualFid, _) = resolvedSrc
                        try {
                            val renameBean = RenameBean(actualFid, destination)
                            val res = ctx.fileRepository.rename(renameBean.toRequestBody())
                            if (res.state) {
                                ctx.invalidateCache(ctx.currentCid)
                                emit("mv: '$src' renamed to '$destination'")
                            } else {
                                emit("mv: 重命名失败: ${res.error}")
                            }
                        } catch (e: Exception) {
                            emit("mv: 重命名失败: ${e.message}")
                        }
                    } else {
                        emit("mv: target '$destination' is not a directory")
                    }
                }
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var s = size.toDouble()
        var unitIndex = 0
        while (s >= 1024 && unitIndex < units.size - 1) {
            s /= 1024
            unitIndex++
        }
        return String.format(Locale.getDefault(), "%.1f %s", s, units[unitIndex])
    }

    private fun formatTimestamp(timeStr: String, dateFormat: SimpleDateFormat): String {
        val timestamp = timeStr.toLongOrNull() ?: return timeStr
        val millis = if (timestamp < 10000000000L) timestamp * 1000 else timestamp
        return dateFormat.format(Date(millis))
    }
}
