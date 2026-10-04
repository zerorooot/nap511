package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.RenameBean
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
                    val targetCid = if (targetPath != null) {
                        val resolved = ctx.resolvePath(targetPath)
                        if (resolved == null) {
                            emit("ls: cannot access '$targetPath': No such file or directory")
                            return@flow
                        }
                        resolved.first
                    } else {
                        ctx.currentCid
                    }

                    val files = ctx.listDirectory(targetCid, forceRefresh)
                    val candidateFiles = if (isAll) files else files.filter { !it.name.startsWith(".") }

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
                        emit("total ${sorted.size}")
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        for (file in sorted) {
                            val typeChar = if (file.isFolder) "d" else "-"
                            val perm = "${typeChar}rwxr-xr-x"
                            val sizeStr = if (file.isFolder) "-" else formatFileSize(file.size.toLongOrNull() ?: 0L)
                            val timeStr = formatTimestamp(file.modifiedTime, dateFormat)
                            val nameStr = if (file.isFolder) "${file.name}/" else file.name
                            emit(String.format(Locale.getDefault(), "%-11s %10s %16s %s", perm, sizeStr, timeStr, nameStr))
                        }
                    } else {
                        for (file in sorted) {
                            val displayName = if (file.isFolder) "${file.name}/" else file.name
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
            description = "重命名文件或将文件移动至其他目录"
            usage = "mv <source> <target>"
            execute { ctx, args, _ ->
                flow {
                    val targets = args.filter { !it.startsWith("-") }
                    if (targets.size < 2) {
                        emit("mv: missing file operand")
                        return@flow
                    }

                    val source = targets[0]
                    val destination = targets[1]

                    val currentFiles = ctx.listDirectory(ctx.currentCid)
                    val sourceFile = currentFiles.firstOrNull { it.name == source }
                    if (sourceFile == null) {
                        emit("mv: cannot stat '$source': No such file or directory")
                        return@flow
                    }

                    val actualFid = if (sourceFile.isFolder) sourceFile.categoryId else sourceFile.fileId

                    // 判断目标是否为已存在的目录
                    val destFolder = currentFiles.firstOrNull { it.isFolder && it.name == destination }
                    if (destFolder != null) {
                        // 移动到该文件夹
                        try {
                            val moveMap = hashMapOf<String, String>()
                            moveMap["pid"] = destFolder.categoryId
                            moveMap["fid[0]"] = actualFid
                            val res = ctx.fileRepository.move(moveMap)
                            if (res.state) {
                                ctx.invalidateCache(ctx.currentCid)
                                ctx.invalidateCache(destFolder.categoryId)
                                emit("mv: '$source' -> '${destFolder.name}/'")
                            } else {
                                emit("mv: 移动失败: ${res.error}")
                            }
                        } catch (e: Exception) {
                            emit("mv: 移动失败: ${e.message}")
                        }
                    } else if (destination.startsWith("/") || destination.contains("/")) {
                        // 路径移动
                        val resolved = ctx.resolvePath(destination)
                        if (resolved != null) {
                            try {
                                val moveMap = hashMapOf<String, String>()
                                moveMap["pid"] = resolved.first
                                moveMap["fid[0]"] = actualFid
                                val res = ctx.fileRepository.move(moveMap)
                                if (res.state) {
                                    ctx.invalidateCache(ctx.currentCid)
                                    ctx.invalidateCache(resolved.first)
                                    emit("mv: '$source' -> '${resolved.second}/'")
                                } else {
                                    emit("mv: 移动失败: ${res.error}")
                                }
                            } catch (e: Exception) {
                                emit("mv: 移动失败: ${e.message}")
                            }
                        } else {
                            emit("mv: 目标路径不存在: $destination")
                        }
                    } else {
                        // 重命名
                        try {
                            val renameBean = RenameBean(actualFid, destination)
                            val res = ctx.fileRepository.rename(renameBean.toRequestBody())
                            if (res.state) {
                                ctx.invalidateCache(ctx.currentCid)
                                emit("mv: '$source' renamed to '$destination'")
                            } else {
                                emit("mv: 重命名失败: ${res.error}")
                            }
                        } catch (e: Exception) {
                            emit("mv: 重命名失败: ${e.message}")
                        }
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
