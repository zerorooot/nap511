package github.zerorooot.nap511.terminal.commands

import com.google.gson.Gson
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import github.zerorooot.nap511.terminal.engine.GlobMatcher
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 115 网盘特色命令集（df, find, trash, stat, unzip, open）
 */
object CloudCommands {

    fun registerAll(registry: CommandRegistry) {
        // 1. df
        registry.register("df") {
            description = "查看网盘容量配额、已用空间与剩余空间"
            usage = "df [-h]"
            flag("-h", "人性化容量单位显示")
            execute { ctx, _, _ ->
                flow {
                    try {
                        val json = ctx.fileRepository.remainingSpace(1)
                        val spaceInfoJson = json.getAsJsonObject("data")?.getAsJsonObject("space_info")
                        if (spaceInfoJson != null) {
                            val bean = Gson().fromJson(spaceInfoJson, RemainingSpaceBean::class.java)
                            val totalBytes = bean.total.size.toDouble().coerceAtLeast(1.0)
                            val usedBytes = bean.use.size.toDouble()
                            val pct = ((usedBytes / totalBytes) * 100).toInt()

                            emit(String.format(Locale.getDefault(), "%-18s %10s %10s %10s %5s %s", "Filesystem", "Size", "Used", "Avail", "Use%", "Mounted on"))
                            emit(String.format(Locale.getDefault(), "%-18s %10s %10s %10s %4d%% %s", "115:CloudDrive", bean.total.sizeFormat, bean.use.sizeFormat, bean.remain.sizeFormat, pct, "/"))
                        } else {
                            emit("df: 无法解析网盘空间配额数据")
                        }
                    } catch (e: Exception) {
                        emit("df: 获取网盘配额失败: ${e.message}")
                    }
                }
            }
        }

        // 2. find
        registry.register("find") {
            description = "网盘文件检索（支持按名称、类型、后缀、深度、115分类筛选及全盘全局搜索）"
            usage = "find [path] [options]"
            flag("-name <pattern>", "按文件名或通配符过滤匹配（如 -name '*.mp4'）")
            flag("-type <f|d>", "按类型过滤，f 为普通文件，d 为目录")
            flag("-suffix <ext>", "按文件扩展名筛选（如 -suffix apk）")
            flag("-filter <type>", "按 115 业务分类筛选：1/doc(文档), 2/img(图片), 3/audio(音频), 4/video(视频), 5/zip(压缩), 6/app(软件)")
            flag("-maxdepth <N>", "限制递归搜索的最大层级深度")
            flag("-global", "在整个 115 网盘根目录进行全局云端搜索")
            execute { ctx, args, _ ->
                flow {
                    var namePattern: String? = null
                    var typeFilter: String? = null
                    var suffixFilter: String? = null
                    var filterType: Int? = null
                    var maxDepth = 5
                    val isGlobal = args.contains("-global")

                    var i = 0
                    while (i < args.size) {
                        when (args[i]) {
                            "-name" -> if (i + 1 < args.size) namePattern = args[++i]
                            "-type" -> if (i + 1 < args.size) typeFilter = args[++i]
                            "-suffix" -> if (i + 1 < args.size) suffixFilter = args[++i].trimStart('.')
                            "-filter" -> if (i + 1 < args.size) filterType = parseFilterType(args[++i])
                            "-maxdepth" -> if (i + 1 < args.size) maxDepth = args[++i].toIntOrNull() ?: 5
                        }
                        i++
                    }

                    // 提取目标路径（非选项参数）
                    val pathArg = args.firstOrNull { !it.startsWith("-") && it != args.getOrNull(args.indexOf("-name") + 1)
                            && it != args.getOrNull(args.indexOf("-type") + 1)
                            && it != args.getOrNull(args.indexOf("-suffix") + 1)
                            && it != args.getOrNull(args.indexOf("-filter") + 1)
                            && it != args.getOrNull(args.indexOf("-maxdepth") + 1) }

                    val targetCid = if (isGlobal) {
                        "0"
                    } else if (pathArg != null) {
                        ctx.resolvePath(pathArg)?.first ?: run {
                            emit("find: '$pathArg': No such file or directory")
                            return@flow
                        }
                    } else {
                        ctx.currentCid
                    }

                    // 1. 若指定了 -filter，参考 FileViewModel.filterFile 直接调用 fileRepository.filterFile
                    if (filterType != null) {
                        try {
                            val res = ctx.fileRepository.filterFile(cid = targetCid, type = filterType, limit = 999)
                            var list = res.fileBeanList.toList()

                            if (suffixFilter != null) {
                                list = list.filter { it.name.substringAfterLast(".", "").equals(suffixFilter, ignoreCase = true) }
                            }
                            if (namePattern != null) {
                                list = list.filter { GlobMatcher.matches(namePattern, it.name) || it.name.contains(namePattern, ignoreCase = true) }
                            }

                            if (list.isEmpty()) {
                                emit("find: 未找到匹配的分类文件 (filterType: $filterType)")
                            } else {
                                emit("分类筛选结果（共 ${list.size} 项，分类: $filterType）：")
                                for (file in list) {
                                    val isFolder = file.fileId.isEmpty()
                                    val prefix = if (isFolder) "[目录] " else "[文件] "
                                    val sizeStr = if (isFolder) "-" else file.sizeString.trim()
                                    emit("$prefix${file.name}  ($sizeStr)")
                                }
                            }
                        } catch (e: Exception) {
                            emit("find: 分类筛选失败: ${e.message}")
                        }
                        return@flow
                    }

                    if (isGlobal) {
                        // 使用 115 全局 search API
                        val queryKeyword = namePattern ?: suffixFilter ?: ""
                        if (queryKeyword.isEmpty()) {
                            emit("find: -global 全局搜索需要提供 -name 或 -suffix 关键词")
                            return@flow
                        }
                        try {
                            val searchResult = ctx.fileRepository.search(
                                cid = "0",
                                searchValue = queryKeyword,
                                aid = 1,
                                asc = 1,
                                limit = 100
                            )
                            val list = searchResult.fileBeanList
                            if (list.isEmpty()) {
                                emit("find: 未在网盘中找到匹配项")
                            } else {
                                list.forEach { file ->
                                    val isFolder = file.fileId.isEmpty()
                                    val prefix = if (isFolder) "[目录] " else "[文件] "
                                    emit("$prefix${file.name} (cid: ${if (isFolder) file.categoryId else file.parentId})")
                                }
                            }
                        } catch (e: Exception) {
                            emit("find: 全局搜索失败: ${e.message}")
                        }
                        return@flow
                    }

                    // 递归目录搜索
                    suspend fun searchRecursive(currentCid: String, currentPrefix: String, currentDepth: Int) {
                        if (currentDepth > maxDepth) return
                        val files = ctx.listDirectory(currentCid)
                        for (file in files) {
                            val fullPath = if (currentPrefix == "/") "/${file.name}" else "$currentPrefix/${file.name}"

                            // 校验筛选条件
                            var matches = true
                            if (typeFilter != null) {
                                if (typeFilter == "f" && file.isFolder) matches = false
                                if (typeFilter == "d" && !file.isFolder) matches = false
                            }
                            if (suffixFilter != null) {
                                val ext = file.name.substringAfterLast(".", "")
                                if (!ext.equals(suffixFilter, ignoreCase = true)) matches = false
                            }
                            if (namePattern != null) {
                                if (!GlobMatcher.matches(namePattern, file.name) && !file.name.contains(namePattern, ignoreCase = true)) {
                                    matches = false
                                }
                            }

                            if (matches) {
                                emit(fullPath + if (file.isFolder) "/" else "")
                            }

                            if (file.isFolder && currentDepth < maxDepth) {
                                searchRecursive(file.categoryId, fullPath, currentDepth + 1)
                            }
                        }
                    }

                    val searchRootPath = if (pathArg != null) pathArg else ctx.currentPath
                    searchRecursive(targetCid, searchRootPath, 1)
                }
            }
        }

        // 3. trash
        registry.register("trash") {
            description = "网盘回收站管理（查看、还原或清空）"
            usage = "trash [-l] [-r <rid>] [-c]"
            flag("-l", "列出回收站中的文件列表及 rid")
            flag("-r <rid>", "按 rid 还原文件到原目录")
            flag("-c", "清空回收站全部文件")
            execute { ctx, args, _ ->
                flow {
                    val isList = args.contains("-l") || args.isEmpty()
                    val revertIndex = args.indexOf("-r")
                    val isClean = args.contains("-c")

                    if (revertIndex >= 0 && revertIndex + 1 < args.size) {
                        val rid = args[revertIndex + 1]
                        try {
                            val res = ctx.fileRepository.revert(rid)
                            if (res.state) {
                                emit("trash: 已成功还原项 rid: $rid")
                            } else {
                                emit("trash: 还原失败: ${res.error}")
                            }
                        } catch (e: Exception) {
                            emit("trash: 还原异常: ${e.message}")
                        }
                        return@flow
                    }

                    if (isClean) {
                        val confirmed = ctx.confirm("trash: 警告！确定要清空回收站中的全部文件吗？(yes/no): ")
                        if (!confirmed) {
                            emit("trash: 已取消清空操作")
                            return@flow
                        }
                        try {
                            val res = ctx.fileRepository.recycleCleanAll("")
                            if (res.state) {
                                emit("trash: 回收站已成功清空")
                            } else {
                                emit("trash: 清空失败: ${res.error}")
                            }
                        } catch (e: Exception) {
                            emit("trash: 清空异常: ${e.message}")
                        }
                        return@flow
                    }

                    if (isList) {
                        try {
                            val list = ctx.fileRepository.recycleList()
                            if (list.recycleBeanList.isEmpty()) {
                                emit("trash: 回收站为空")
                            } else {
                                emit("回收站项目列表（共 ${list.recycleBeanList.size} 项）：")
                                for (item in list.recycleBeanList) {
                                    emit(String.format(Locale.getDefault(), "rid: %-15s %s (%s)", item.id, item.fileName, item.fileSize))
                                }
                            }
                        } catch (e: Exception) {
                            emit("trash: 获取回收站列表失败: ${e.message}")
                        }
                    }
                }
            }
        }

        // 4. stat / info
        registry.register("stat") {
            description = "查看文件或目录的详细元数据"
            usage = "stat <filename>"
            execute { ctx, args, _ ->
                flow {
                    val targetName = args.firstOrNull { !it.startsWith("-") }
                    if (targetName == null) {
                        emit("stat: missing operand")
                        return@flow
                    }

                    val files = ctx.listDirectory(ctx.currentCid)
                    val file = files.firstOrNull { it.name == targetName }
                    if (file == null) {
                        emit("stat: cannot stat '$targetName': No such file or directory")
                        return@flow
                    }

                    emit("  File: ${file.name}")
                    emit("  Type: ${if (file.isFolder) "Directory" else "Regular File"}")
                    emit("  Size: ${file.size} bytes (${file.sizeString.trim()})")
                    emit("  CID:  ${file.categoryId}  |  FID: ${file.fileId}")
                    if (file.pickCode.isNotEmpty()) {
                        emit("  PickCode: ${file.pickCode}")
                    }
                    if (file.sha1.isNotEmpty()) {
                        emit("  SHA-1:    ${file.sha1}")
                    }
                    if (file.modifiedTime.isNotEmpty()) {
                        emit("  Modify:   ${file.modifiedTimeString}")
                    }
                    if (file.createTime.isNotEmpty()) {
                        emit("  Created:  ${file.createTimeString}")
                    }
                }
            }
        }

        // 5. unzip
        registry.register("unzip") {
            description = "云端解压（支持查看压缩包列表及提交云端解压）"
            usage = "unzip [-l] <filename>"
            flag("-l", "列出压缩包内的文件结构列表（不解压）")
            execute { ctx, args, _ ->
                flow {
                    val isList = args.contains("-l")
                    val fileName = args.firstOrNull { !it.startsWith("-") }
                    if (fileName == null) {
                        emit("unzip: missing file operand")
                        return@flow
                    }

                    val files = ctx.listDirectory(ctx.currentCid)
                    val file = files.firstOrNull { it.name == fileName }
                    if (file == null) {
                        emit("unzip: cannot find '$fileName': No such file")
                        return@flow
                    }

                    if (file.isFolder) {
                        emit("unzip: '$fileName' is a directory, not an archive")
                        return@flow
                    }

                    val pickCode = file.pickCode
                    if (pickCode.isEmpty()) {
                        emit("unzip: 文件缺失 pickCode，无法操作")
                        return@flow
                    }

                    if (isList) {
                        // 预览压缩包内文件
                        try {
                            val zipBeanList = ctx.fileRepository.getZipListFile(pickCode = pickCode, fileName = file.name)
                            if (zipBeanList.list.isNotEmpty()) {
                                emit("Archive: $fileName")
                                emit(String.format(Locale.getDefault(), "%-12s %-16s %s", "Length", "Date", "Name"))
                                emit("--------------------------------------------------")
                                for (item in zipBeanList.list) {
                                    emit(String.format(Locale.getDefault(), "%-12s %-16s %s", item.sizeString.trim(), item.timeString, item.fileName))
                                }
                            } else {
                                emit("unzip: 压缩包内无可显示文件或暂未完成分析")
                            }
                        } catch (e: Exception) {
                            emit("unzip: 预览压缩包失败: ${e.message}")
                        }
                    } else {
                        // 提交云端解压
                        try {
                            emit("正在提交云端解压任务: $fileName...")
                            val (success, msg) = ctx.fileRepository.unzipFile(
                                pickCode = pickCode,
                                zipFileCid = ctx.currentCid,
                                files = null,
                                dirs = null,
                                unzipFolderName = file.name.substringBeforeLast("."),
                                showToast = false
                            )
                            if (success) {
                                ctx.invalidateCache(ctx.currentCid)
                                emit("unzip: $msg")
                            } else {
                                emit("unzip: 解压失败: $msg")
                            }
                        } catch (e: Exception) {
                            emit("unzip: 提交解压失败: ${e.message}")
                        }
                    }
                }
            }
        }

        // 6. open
        registry.register("open") {
            description = "根据文件类型自动打开对应的查看器或预览页面"
            usage = "open <filename>"
            execute { ctx, args, _ ->
                flow {
                    val fileName = args.firstOrNull { !it.startsWith("-") }
                    if (fileName == null) {
                        emit("open: missing file operand")
                        return@flow
                    }

                    val files = ctx.listDirectory(ctx.currentCid)
                    val file = files.firstOrNull { it.name == fileName }
                    if (file == null) {
                        emit("open: cannot find '$fileName': No such file or directory")
                        return@flow
                    }

                    if (file.isFolder) {
                        // 目录直接进入
                        val newPath = if (ctx.currentPath == "/") "/${file.name}" else "${ctx.currentPath}/${file.name}"
                        ctx.updateDirectory(file.categoryId, newPath)
                        emit("已进入目录: ${file.name}")
                        return@flow
                    }

                    emit("正在打开: ${file.name}")
                    when {
                        file.photoThumb.isNotEmpty() || file.fileIco == R.drawable.png -> {
                            ctx.onNavigate?.invoke(Route.Photo)
                        }
                        file.fileIco == R.drawable.mp3 -> {
                            ctx.onNavigate?.invoke(Route.MusicDetail)
                        }
                        file.fileIco == R.drawable.txt -> {
                            ctx.onNavigate?.invoke(Route.TxtReader)
                        }
                        file.fileIco == R.drawable.web -> {
                            ctx.onNavigate?.invoke(Route.HtmlWebViewScreen)
                        }
                        file.isVideo == 1 -> {
                            emit("视频文件: 可在文件列表中点击以使用播放器播放")
                        }
                        file.fileIco == R.drawable.zip -> {
                            emit("压缩文件: 输入 'unzip -l \"${file.name}\"' 可预览，输入 'unzip \"${file.name}\"' 可云端解压")
                        }
                        else -> {
                            emit("未能识别该文件的专用预览器 (${file.name})")
                        }
                    }
                }
            }
        }
    }

    private fun parseFilterType(raw: String): Int? {
        return when (raw.lowercase(Locale.ROOT)) {
            "1", "doc", "document", "txt", "文档" -> 1
            "2", "img", "image", "pic", "photo", "图片" -> 2
            "3", "audio", "music", "mp3", "音频" -> 3
            "4", "video", "movie", "mp4", "视频" -> 4
            "5", "zip", "archive", "rar", "7z", "压缩" -> 5
            "6", "app", "apk", "software", "软件" -> 6
            else -> raw.toIntOrNull()
        }
    }
}
