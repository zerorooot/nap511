package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import github.zerorooot.nap511.util.formatFileSize
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 网盘基础核心文件管理命令（ls, cd, pwd, mkdir, rm, mv）
 * 严格遵循 Cache-First、参数排序及交互确认规范
 */
object FileCommands {

    /**
     * 注册所有基础文件管理命令
     */
    fun registerAll(registry: CommandRegistry) {
        registerLs(registry)
        registerCd(registry)
        registerPwd(registry)
        registerMkdir(registry)
        registerRm(registry)
        registerMv(registry)
    }

    /**
     * 注册 ls 命令：列出当前或指定目录下的文件与文件夹
     */
    fun registerLs(registry: CommandRegistry) {
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
                                val filtered =
                                    if (isAll) files else files.filter { !it.name.startsWith(".") }
                                Pair(filtered, false)
                            }

                            is ResolvedTarget.File -> {
                                Pair(listOf(resolved.file), true)
                            }
                        }
                    } else {
                        val files = ctx.listDirectory(ctx.currentCid, forceRefresh)
                        val filtered =
                            if (isAll) files else files.filter { !it.name.startsWith(".") }
                        Pair(filtered, false)
                    }

                    // 排序规则：默认完全保持接口请求/缓存中的原始顺序；仅在显式传入选项时重排
                    var sorted = when {
                        sortByMtime -> candidateFiles.sortedByDescending {
                            it.modifiedTime.toLongOrNull() ?: 0L
                        }

                        sortByAtime -> candidateFiles.sortedByDescending {
                            it.updateTime.toLongOrNull() ?: 0L
                        }

                        sortBySize -> candidateFiles.sortedByDescending {
                            it.size.toLongOrNull() ?: 0L
                        }

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
                            val sizeStr = if (file.isFolder) "-" else (file.size.toLongOrNull()
                                ?: 0L).formatFileSize()
                            val timeStr = formatTimestamp(file.modifiedTime, dateFormat)
                            val nameStr = if (isSingleFile && targetPath != null) {
                                if (file.isFolder && !targetPath.endsWith("/")) "$targetPath/" else targetPath
                            } else {
                                if (file.isFolder) "${file.name}/" else file.name
                            }
                            emit(
                                String.format(
                                    Locale.getDefault(),
                                    "%-11s %10s %16s %s",
                                    perm,
                                    sizeStr,
                                    timeStr,
                                    nameStr
                                )
                            )
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
    }

    /**
     * 注册 cd 命令：切换网盘当前工作目录
     */
    fun registerCd(registry: CommandRegistry) {
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
    }

    /**
     * 注册 pwd 命令：打印当前工作目录路径及 CID
     */
    fun registerPwd(registry: CommandRegistry) {
        registry.register("pwd") {
            description = "打印当前工作目录路径及 CID"
            usage = "pwd"
            execute { ctx, _, _ ->
                flow {
                    emit("${ctx.currentPath} (cid: ${ctx.currentCid})")
                }
            }
        }
    }

    /**
     * 注册 mkdir 命令：在当前目录或指定路径下新建文件夹
     */
    fun registerMkdir(registry: CommandRegistry) {
        registry.register("mkdir") {
            description = "在当前目录或指定路径下新建文件夹"
            usage = "mkdir [-p] <folder_name...>"
            flag("-p", "若目录已存在不报错，并支持递归创建父目录")
            execute { ctx, args, _ ->
                flow {
                    val isParents = args.contains("-p")
                    val folderNames = args.filter { !it.startsWith("-") }
                    if (folderNames.isEmpty()) {
                        emit("mkdir: missing operand")
                        return@flow
                    }

                    for (rawName in folderNames) {
                        // 去除末尾斜杠
                        val cleanTarget = rawName.trim().trimEnd('/')
                        if (cleanTarget.isEmpty() || cleanTarget == "/" || cleanTarget == "/根目录") {
                            if (!isParents) {
                                emit("mkdir: cannot create directory '$rawName': File exists")
                            }
                            continue
                        }

                        if (isParents) {
                            // -p 模式：支持逐层递归创建目录，若各层级已存在则直接沿用
                            val isAbsolute = cleanTarget.startsWith("/")
                            var rawSegments = cleanTarget.split("/").filter { it.isNotEmpty() && it != "." }
                            if (isAbsolute && rawSegments.firstOrNull() == "根目录") {
                                rawSegments = rawSegments.drop(1)
                            }
                            if (rawSegments.isEmpty()) continue

                            var curCid = if (isAbsolute) "0" else ctx.currentCid
                            var createSuccess = true

                            for (seg in rawSegments) {
                                if (seg == "..") {
                                    val resolved = ctx.resolvePath(seg)
                                    if (resolved != null) {
                                        curCid = resolved.first
                                    }
                                    continue
                                }
                                val existingFiles = ctx.listDirectory(curCid)
                                val existingFolder = existingFiles.firstOrNull { it.isFolder && it.name == seg }
                                if (existingFolder != null) {
                                    // 目录已存在，步入该目录
                                    curCid = existingFolder.categoryId
                                } else {
                                    val existingFile = existingFiles.firstOrNull { !it.isFolder && it.name == seg }
                                    if (existingFile != null) {
                                        emit("mkdir: cannot create directory '$rawName': File exists")
                                        createSuccess = false
                                        break
                                    }
                                    try {
                                        // 调用网盘接口创建该层级文件夹
                                        val res = ctx.fileRepository.createFolder(pid = curCid, folderName = seg)
                                        if (res.state) {
                                            ctx.invalidateCache(curCid)
                                            // 获取新建文件夹的 CID 以供下一层使用
                                            val nextCid = res.cid.ifEmpty {
                                                val refreshed = ctx.listDirectory(curCid, forceRefresh = true)
                                                refreshed.firstOrNull { it.isFolder && it.name == seg }?.categoryId ?: ""
                                            }
                                            if (nextCid.isNotEmpty()) {
                                                curCid = nextCid
                                            } else {
                                                break
                                            }
                                        } else {
                                            val err = res.error.ifEmpty { "创建失败" }
                                            emit("mkdir: cannot create directory '$rawName': $err")
                                            createSuccess = false
                                            break
                                        }
                                    } catch (e: Exception) {
                                        emit("mkdir: cannot create directory '$rawName': ${e.message}")
                                        createSuccess = false
                                        break
                                    }
                                }
                            }
                            if (createSuccess) {
                                emit("mkdir: created directory '$rawName'")
                            }
                        } else {
                            // 非 -p 模式：若带路径则解析其父目录，必须在已有父目录下创建
                            val parentCid: String
                            val folderName: String

                            if (cleanTarget.contains("/")) {
                                val parentPath = cleanTarget.substringBeforeLast('/')
                                folderName = cleanTarget.substringAfterLast('/')
                                val effectiveParentPath = parentPath.ifEmpty { "/" }
                                val resolvedParent = ctx.resolvePath(effectiveParentPath)
                                if (resolvedParent == null) {
                                    emit("mkdir: cannot create directory '$rawName': No such file or directory")
                                    continue
                                }
                                parentCid = resolvedParent.first
                            } else {
                                parentCid = ctx.currentCid
                                folderName = cleanTarget
                            }

                            // 校验目标父目录下是否已存在同名项
                            val existing = ctx.listDirectory(parentCid).firstOrNull { it.name == folderName }
                            if (existing != null) {
                                emit("mkdir: cannot create directory '$rawName': File exists")
                                continue
                            }

                            try {
                                val res = ctx.fileRepository.createFolder(
                                    pid = parentCid,
                                    folderName = folderName
                                )
                                if (res.state) {
                                    ctx.invalidateCache(parentCid)
                                    emit("mkdir: created directory '$rawName'")
                                } else {
                                    val err = res.error.ifEmpty { "创建失败" }
                                    emit("mkdir: cannot create directory '$rawName': $err")
                                }
                            } catch (e: Exception) {
                                emit("mkdir: cannot create directory '$rawName': ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 注册 rm 命令：删除当前目录或指定路径下的指定文件或文件夹至回收站
     * 支持绝对路径、相对路径、转义空格路径与末尾斜杠，并自动获取真实 parentCid 执行删除
     */
    fun registerRm(registry: CommandRegistry) {
        registry.register("rm") {
            description = "删除当前目录或指定路径下的指定文件或文件夹至回收站"
            usage = "rm [-y] [-r|-R] [-f] <file...>"
            flag("-y", "免确认直接删除")
            flag("-f", "强制删除，免确认")
            flag("-r", "支持递归删除目录")
            execute { ctx, args, _ ->
                flow {
                    // 支持 -y 与 -f 免确认参数
                    val autoConfirm = args.contains("-y") || args.contains("-f") ||
                            args.contains("-rf") || args.contains("-fr")
                    val targetNames = args.filter { !it.startsWith("-") }

                    if (targetNames.isEmpty()) {
                        emit("rm: missing operand")
                        return@flow
                    }

                    // 收集所有被影响的目录 CID（用于统一刷新本地缓存）
                    val affectedCids = mutableSetOf<String>()
                    var deletedCount = 0

                    for (target in targetNames) {
                        // 使用 resolveTarget 解析目标对象（支持路径解析与末尾斜杠）
                        val resolved = ctx.resolveTarget(target)
                        if (resolved == null) {
                            emit("rm: cannot remove '$target': No such file or directory")
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
                                // 安全校验：严禁删除根目录
                                if (resolved.cid == "0") {
                                    emit("rm: cannot remove '$target': Cannot remove root directory")
                                    continue
                                }
                                // 安全校验：禁止删除当前工作目录
                                if (resolved.cid == ctx.currentCid) {
                                    emit("rm: cannot remove '$target': Cannot remove current working directory")
                                    continue
                                }
                                if (resolved.parentCid == null) {
                                    emit("rm: cannot remove '$target': Cannot determine parent directory")
                                    continue
                                }
                                actualFid = resolved.cid
                                parentCid = resolved.parentCid
                                displayName = resolved.name.ifEmpty {
                                    target.trimEnd('/').substringAfterLast('/')
                                }
                                isFolder = true
                            }
                        }

                        // 若未输入 -y / -f 则交互确认
                        if (!autoConfirm) {
                            val confirmed =
                                ctx.confirm("rm: 是否确认删除 '$displayName'? (yes/no): ")
                            if (!confirmed) {
                                emit("rm: 已取消删除 '$displayName'")
                                continue
                            }
                        }

                        try {
                            // 传入目标真实的 parentCid 与 fid，确保跨目录删除成功
                            val res =
                                ctx.fileRepository.delete(pid = parentCid, fid = actualFid)
                            if (res.state) {
                                deletedCount++
                                affectedCids.add(parentCid)
                                if (isFolder) {
                                    affectedCids.add(actualFid)
                                }
                                emit("rm: 已移入回收站 '$displayName'")
                            } else {
                                val err = res.error.ifEmpty { res.message }
                                emit("rm: 删除失败 '$displayName': $err")
                            }
                        } catch (e: Exception) {
                            emit("rm: 删除失败 '$displayName': ${e.message}")
                        }
                    }

                    // 批量失效所有受影响目录的本地缓存，确保后续 ls 呈现最新数据
                    for (cid in affectedCids) {
                        ctx.invalidateCache(cid)
                    }
                }
            }
        }
    }

    /**
     * 注册 mv 命令：重命名文件或将文件/目录移动至其他目录
     */
    fun registerMv(registry: CommandRegistry) {
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

                    // 解析源文件/目录信息封装体（包含其原本所在目录的 parentCid）
                    data class ResolvedSource(val fid: String, val name: String, val parentCid: String?)

                    suspend fun resolveSourceItem(rawSrc: String): ResolvedSource? {
                        val cleanSrc = rawSrc.trim().trimEnd('/')
                        val localFile =
                            currentFiles.firstOrNull { it.name == cleanSrc || it.name == rawSrc.trim() }
                        if (localFile != null) {
                            val fid =
                                if (localFile.isFolder) localFile.categoryId else localFile.fileId
                            return ResolvedSource(fid, localFile.name, ctx.currentCid)
                        }

                        // 尝试路径解析（支持绝对路径与相对路径）
                        return when (val resolved = ctx.resolveTarget(cleanSrc)) {
                            is ResolvedTarget.Directory -> ResolvedSource(
                                resolved.cid,
                                resolved.name.ifEmpty { cleanSrc.substringAfterLast('/').ifEmpty { "/" } },
                                resolved.parentCid
                            )

                            is ResolvedTarget.File -> ResolvedSource(resolved.file.fileId, resolved.file.name, resolved.parentCid)
                            null -> null
                        }
                    }

                    // 判断目标是否为目录（支持当前目录下文件夹、上级目录 .. / ../、绝对路径或 ~）
                    var targetDestCid: String? = null
                    var destDisplayName = destination
                    val cleanDest = destination.trim().trimEnd('/')

                    val destFolder =
                        currentFiles.firstOrNull { it.isFolder && (it.name == cleanDest || it.name == destination.trim()) }
                    if (destFolder != null) {
                        targetDestCid = destFolder.categoryId
                        destDisplayName = destFolder.name
                    } else if (cleanDest == ".." || cleanDest == "." || cleanDest == "~" || cleanDest.startsWith(
                            "/"
                        ) || cleanDest.contains("/")
                    ) {
                        val resolvedPath = ctx.resolvePath(destination)
                        if (resolvedPath != null) {
                            targetDestCid = resolvedPath.first
                            destDisplayName = resolvedPath.second
                        }
                    }

                    if (targetDestCid != null) {
                        // 移动操作：将所有 sources 移入 targetDestCid 目录
                        val affectedCids = mutableSetOf(ctx.currentCid, targetDestCid)
                        for (src in sources) {
                            val resolvedSrc = resolveSourceItem(src)
                            if (resolvedSrc == null) {
                                emit("mv: cannot stat '$src': No such file or directory")
                                continue
                            }
                            try {
                                val moveMap = hashMapOf<String, String>()
                                moveMap["pid"] = targetDestCid
                                moveMap["fid[0]"] = resolvedSrc.fid
                                val res = ctx.fileRepository.move(moveMap)
                                if (res.state) {
                                    // 同时刷新源文件所在的父目录缓存
                                    resolvedSrc.parentCid?.let { affectedCids.add(it) }
                                    emit("mv: '$src' -> '$destDisplayName/'")
                                } else {
                                    val err =
                                        res.error.ifEmpty { res.errorMsg.ifEmpty { res.message } }
                                    emit("mv: 移动 '$src' 失败: $err")
                                }
                            } catch (e: Exception) {
                                emit("mv: 移动 '$src' 失败: ${e.message}")
                            }
                        }
                        for (cid in affectedCids) {
                            ctx.invalidateCache(cid)
                        }
                    } else if (sources.size == 1) {
                        // 单源且目标不是现有目录：执行重命名
                        if (destination.endsWith("/")) {
                            emit("mv: target '$destination' is not a directory")
                            return@flow
                        }
                        val src = sources[0]
                        val resolvedSrc = resolveSourceItem(src)
                        if (resolvedSrc == null) {
                            emit("mv: cannot stat '$src': No such file or directory")
                            return@flow
                        }
                        // 截取纯文件名，防止将路径名误作为文件名传入 rename API
                        val newName = destination.trimEnd('/').substringAfterLast('/')
                        try {
                            val renameBean = RenameBean(resolvedSrc.fid, newName)
                            val res = ctx.fileRepository.rename(renameBean.toRequestBody())
                            if (res.state) {
                                ctx.invalidateCache(ctx.currentCid)
                                resolvedSrc.parentCid?.let { ctx.invalidateCache(it) }
                                emit("mv: '$src' renamed to '$newName'")
                            } else {
                                val err = res.error.ifEmpty { res.errorMsg.ifEmpty { res.message } }
                                emit("mv: 重命名失败: $err")
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

    private fun formatTimestamp(timeStr: String, dateFormat: SimpleDateFormat): String {
        val timestamp = timeStr.toLongOrNull() ?: return timeStr
        val millis = if (timestamp < 10000000000L) timestamp * 1000 else timestamp
        return dateFormat.format(Date(millis))
    }
}
