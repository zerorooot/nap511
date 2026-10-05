package github.zerorooot.nap511.terminal.commands

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
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import github.zerorooot.nap511.terminal.engine.GlobMatcher
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.FileOpenResult
import github.zerorooot.nap511.util.formatFileSize
import github.zerorooot.nap511.worker.UnzipAllFileWorker
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.Locale

/**
 * 115 网盘特色命令集（df, find, trash, stat, unzip, open）
 */
object CloudCommands {

    /**
     * 注册所有 115 网盘特色命令
     */
    fun registerAll(registry: CommandRegistry) {
        registerDf(registry)
        registerFind(registry)
        registerTrash(registry)
        registerStat(registry)
        registerUnzip(registry)
        registerOpen(registry)
    }

    /**
     * 注册 df 命令：查看网盘容量配额、已用空间与剩余空间
     */
    fun registerDf(registry: CommandRegistry) {
        registry.register("df") {
            description = "查看网盘容量配额、已用空间与剩余空间"
            usage = "df [-h]"
            flag("-h", "人性化容量单位显示")
            execute { ctx, args, _ ->
                flow {
                    try {
                        val json = ctx.fileRepository.remainingSpace(1)
                        val spaceInfoJson =
                            json.getAsJsonObject("data")?.getAsJsonObject("space_info")
                        if (spaceInfoJson != null) {
                            val bean =
                                Gson().fromJson(spaceInfoJson, RemainingSpaceBean::class.java)
                            val totalBytes = bean.total.size.toDouble().coerceAtLeast(1.0)
                            val usedBytes = bean.use.size.toDouble()
                            val pct = ((usedBytes / totalBytes) * 100).toInt()

                            val isHuman = args.contains("-h")
                            val totalStr = if (isHuman) {
                                bean.total.sizeFormat.ifEmpty { bean.total.size.formatFileSize() }
                            } else {
                                "${bean.total.size} B"
                            }
                            val usedStr = if (isHuman) {
                                bean.use.sizeFormat.ifEmpty { bean.use.size.formatFileSize() }
                            } else {
                                "${bean.use.size} B"
                            }
                            val availStr = if (isHuman) {
                                bean.remain.sizeFormat.ifEmpty { bean.remain.size.formatFileSize() }
                            } else {
                                "${bean.remain.size} B"
                            }

                            emit(
                                String.format(
                                    Locale.getDefault(),
                                    "%-18s %10s %10s %10s %5s %s",
                                    "Filesystem",
                                    "Size",
                                    "Used",
                                    "Avail",
                                    "Use%",
                                    "Mounted on"
                                )
                            )
                            emit(
                                String.format(
                                    Locale.getDefault(),
                                    "%-18s %10s %10s %10s %4d%% %s",
                                    "115:CloudDrive",
                                    totalStr,
                                    usedStr,
                                    availStr,
                                    pct,
                                    "/"
                                )
                            )
                        } else {
                            emit("df: 无法解析网盘空间配额数据")
                        }
                    } catch (e: Exception) {
                        emit("df: 获取网盘配额失败: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 注册 find 命令：网盘文件检索（支持按名称、类型、后缀、深度、115分类筛选及全盘全局搜索）
     */
    fun registerFind(registry: CommandRegistry) {
        registry.register("find") {
            description = "网盘文件检索（支持按名称、类型、后缀、深度、115分类筛选及全盘全局搜索）"
            usage = "find [path] [options]"
            flag("-name <pattern>", "按文件名或通配符过滤匹配（如 -name '*.mp4'）")
            flag("-type <f|d>", "按类型过滤，f 为普通文件，d 为目录")
            flag("-suffix <ext>", "按文件扩展名筛选（如 -suffix apk）")
            flag(
                "-filter <type>",
                "按 115 业务分类筛选：1/doc(文档), 2/img(图片), 3/audio(音频), 4/video(视频), 5/zip(压缩), 6/app(软件)"
            )
            flag("-maxdepth <N>", "限制递归搜索的最大层级深度，默认为5")
            flag("-empty", "只匹配空文件（大小为 0）或空目录（内容为空）")
            flag("-size <[+|-]N[k|M|G]>", "按文件大小筛选（如 +100M 大于 100MB，-10k 小于 10KB）")
            flag("-global", "在整个 115 网盘根目录进行全局云端搜索")
            execute { ctx, args, _ ->
                flow {
                    var namePattern: String? = null
                    var typeFilter: String? = null
                    var suffixFilter: String? = null
                    var filterType: Int? = null
                    var maxDepth = 5
                    var isEmptyFilter = false
                    var sizeFilterSpec: String? = null
                    var pathArg: String? = null
                    var isGlobal = false

                    var i = 0
                    while (i < args.size) {
                        val arg = args[i]
                        when {
                            arg == "-name" && i + 1 < args.size -> namePattern = args[++i]
                            arg == "-type" && i + 1 < args.size -> typeFilter = args[++i]
                            arg == "-suffix" && i + 1 < args.size -> suffixFilter =
                                args[++i].trimStart('.')

                            arg == "-filter" && i + 1 < args.size -> filterType =
                                parseFilterType(args[++i])

                            arg == "-maxdepth" && i + 1 < args.size -> maxDepth =
                                args[++i].toIntOrNull() ?: 5

                            arg == "-size" && i + 1 < args.size -> sizeFilterSpec = args[++i]
                            arg == "-empty" -> isEmptyFilter = true
                            arg == "-global" -> isGlobal = true
                            !arg.startsWith("-") && pathArg == null -> pathArg = arg
                        }
                        i++
                    }

                    val sizeFilter = sizeFilterSpec?.let { parseSizeFilter(it) }

                    val (targetCid, searchRootPath) = if (isGlobal) {
                        Pair("0", "/根目录")
                    } else if (pathArg != null) {
                        // 解析目标路径：若是目录则以此为根遍历，若是单个文件则直接输出并终止
                        when (val resolved = ctx.resolveTarget(pathArg)) {
                            is ResolvedTarget.Directory -> Pair(resolved.cid, resolved.path)
                            is ResolvedTarget.File -> {
                                emit(resolved.fullPath)
                                return@flow
                            }
                            null -> {
                                emit("find: '$pathArg': No such file or directory")
                                return@flow
                            }
                        }
                    } else {
                        Pair(ctx.currentCid, ctx.currentPath)
                    }

                    // 1. 若指定了 -filter，参考 FileViewModel.filterFile 直接调用 fileRepository.filterFile
                    if (filterType != null) {
                        try {
                            val res = ctx.fileRepository.filterFile(
                                cid = targetCid,
                                type = filterType
                            )
                            var list = res.fileBeanList.toList()

                            if (suffixFilter != null) {
                                list = list.filter {
                                    it.name.substringAfterLast(".", "")
                                        .equals(suffixFilter, ignoreCase = true)
                                }
                            }
                            if (namePattern != null) {
                                list = list.filter {
                                    GlobMatcher.matches(
                                        namePattern,
                                        it.name
                                    ) || it.name.contains(namePattern, ignoreCase = true)
                                }
                            }
                            if (sizeFilter != null) {
                                list = list.filter {
                                    val sz = it.size.toLongOrNull() ?: 0L
                                    matchesSize(sz, sizeFilter)
                                }
                            }
                            if (isEmptyFilter) {
                                list = list.filter {
                                    if (it.isFolder) {
                                        ctx.listDirectory(it.categoryId).isEmpty()
                                    } else {
                                        (it.size.toLongOrNull() ?: 0L) == 0L
                                    }
                                }
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
                                searchValue = queryKeyword
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
                    suspend fun searchRecursive(
                        currentCid: String,
                        currentPrefix: String,
                        currentDepth: Int
                    ) {
                        if (currentDepth > maxDepth) return
                        val files = ctx.listDirectory(currentCid)
                        for (file in files) {
                            val fullPath =
                                if (currentPrefix == "/") "/${file.name}" else "$currentPrefix/${file.name}"

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
                                if (!GlobMatcher.matches(
                                        namePattern,
                                        file.name
                                    ) && !file.name.contains(namePattern, ignoreCase = true)
                                ) {
                                    matches = false
                                }
                            }
                            if (sizeFilter != null) {
                                val fileSize = file.size.toLongOrNull() ?: 0L
                                if (!matchesSize(fileSize, sizeFilter)) {
                                    matches = false
                                }
                            }
                            if (isEmptyFilter && matches) {
                                if (file.isFolder) {
                                    val subFiles = ctx.listDirectory(file.categoryId)
                                    if (subFiles.isNotEmpty()) {
                                        matches = false
                                    }
                                } else {
                                    val fileSize = file.size.toLongOrNull() ?: 0L
                                    if (fileSize != 0L) {
                                        matches = false
                                    }
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

                    searchRecursive(targetCid, searchRootPath, 1)
                }
            }
        }
    }

    /**
     * 注册 trash 命令：网盘回收站管理（查看、还原或清空）
     */
    fun registerTrash(registry: CommandRegistry) {
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
                        val confirmed =
                            ctx.confirm("trash: 警告！确定要清空回收站中的全部文件吗？(yes/no): ")
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
                                    emit(
                                        String.format(
                                            Locale.getDefault(),
                                            "rid: %-15s %s (%s)",
                                            item.id,
                                            item.fileName,
                                            item.fileSize
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            emit("trash: 获取回收站列表失败: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * 注册 stat 命令：查看文件或目录的详细元数据
     */
    fun registerStat(registry: CommandRegistry) {
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

                    val file = when (val resolved = ctx.resolveTarget(targetName)) {
                        is ResolvedTarget.File -> resolved.file
                        is ResolvedTarget.Directory -> {
                            // 优先使用 resolveTarget 获取的完整 folderBean，若无则使用解析所得真实目录名，防止末尾斜杠导致截断为空
                            resolved.folderBean ?: FileBean(
                                name = resolved.name.ifEmpty { targetName.trimEnd('/').substringAfterLast('/').ifEmpty { "根目录" } },
                                categoryId = resolved.cid,
                                isFolder = true
                            )
                        }

                        null -> {
                            emit("stat: cannot stat '$targetName': No such file or directory")
                            return@flow
                        }
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
    }

    /**
     * 注册 unzip 命令：云端解压（支持查看压缩包列表及提交云端解压）
     */
    fun registerUnzip(registry: CommandRegistry) {
        registry.register("unzip") {
            description = "云端解压（支持查看压缩包列表及提交云端解压）"
            usage = "unzip [-l] [-p password] <filename...>"
            flag("-l", "列出压缩包内的文件结构列表（不解压）")
            flag("-p <password>", "设置解压密码")
            execute { ctx, args, _ ->
                flow {
                    var isList = false
                    var password = ""
                    val fileArgs = mutableListOf<String>()

                    var idx = 0
                    while (idx < args.size) {
                        when (val arg = args[idx]) {
                            "-l" -> isList = true
                            "-p" -> {
                                if (idx + 1 < args.size) {
                                    password = args[++idx]
                                }
                            }

                            else -> {
                                if (!arg.startsWith("-")) {
                                    fileArgs.add(arg)
                                }
                            }
                        }
                        idx++
                    }

                    if (fileArgs.isEmpty()) {
                        emit("unzip: missing file operand")
                        return@flow
                    }

                    // 收集匹配的所有 FileBean
                    val fileBeansList = mutableListOf<FileBean>()
                    var targetCid = ctx.currentCid

                    for (fileArg in fileArgs) {
                        if (GlobMatcher.hasGlobWildcards(fileArg)) {
                            // 包含通配符，在当前目录（或指定父目录）按 GlobMatcher 匹配展开
                            val dirPath = if (fileArg.contains("/")) {
                                val before = fileArg.substringBeforeLast("/")
                                before.ifEmpty { "/" }
                            } else ""
                            val pattern =
                                if (fileArg.contains("/")) fileArg.substringAfterLast("/") else fileArg

                            val searchCid = if (dirPath.isEmpty()) {
                                ctx.currentCid
                            } else {
                                ctx.resolvePath(dirPath)?.first
                            }

                            if (searchCid == null) {
                                emit("unzip: '$dirPath': No such directory")
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
                                emit("unzip: no match found for '$fileArg'")
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
                                    emit("unzip: '$fileArg' is a directory, not an archive")
                                }

                                null -> {
                                    emit("unzip: cannot find '$fileArg': No such file")
                                }
                            }
                        }
                    }

                    // 去重 (根据 fileId 或 pickCode)
                    val distinctFileBeans = fileBeansList.distinctBy { file ->
                        file.fileId.ifEmpty { file.pickCode }
                    }

                    if (distinctFileBeans.isEmpty()) {
                        emit("unzip: 未找到可解压的文件")
                        return@flow
                    }

                    if (isList) {
                        // 预览压缩包内文件结构列表
                        for (file in distinctFileBeans) {
                            val pickCode = file.pickCode
                            if (pickCode.isEmpty()) {
                                emit("unzip: 文件 '${file.name}' 缺失 pickCode，无法预览")
                                continue
                            }
                            try {
                                val zipBeanList = ctx.fileRepository.getZipListFile(
                                    pickCode = pickCode,
                                    fileName = file.name
                                )
                                emit("Archive: ${file.name}")
                                if (zipBeanList.list.isNotEmpty()) {
                                    emit(
                                        String.format(
                                            Locale.getDefault(),
                                            "%-12s %-16s %s",
                                            "Length",
                                            "Date",
                                            "Name"
                                        )
                                    )
                                    emit("--------------------------------------------------")
                                    for (item in zipBeanList.list) {
                                        emit(
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
                                    emit("unzip: 压缩包内无可显示文件或暂未完成分析")
                                }
                            } catch (e: Exception) {
                                emit("unzip: 预览 '${file.name}' 失败: ${e.message}")
                            }
                        }
                    } else {
                        // 提交云端解压任务至 UnzipAllFileWorker 统一处理
                        try {
                            emit("正在提交 ${distinctFileBeans.size} 个解压任务至后台...")

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
                            emit("unzip: 已成功提交 ${distinctFileBeans.size} 个解压任务到后台 UnzipAllFileWorker 处理 (${distinctFileBeans.joinToString { it.name }})")
                        } catch (e: Exception) {
                            emit("unzip: 提交解压任务失败: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * 注册 open 命令：根据文件类型自动打开对应的查看器或预览页面
     */
    fun registerOpen(registry: CommandRegistry) {
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

                    val resolved = ctx.resolveTarget(fileName)
                    if (resolved == null) {
                        emit("open: cannot find '$fileName': No such file or directory")
                        return@flow
                    }
                    val fileOpener = ctx.fileOpener
                    if (fileOpener == null) {
                        emit("open: 当前终端环境未配置文件打开器")
                        return@flow
                    }

                    when (resolved) {
                        is ResolvedTarget.Directory -> {
                            emit("已跳转至文件夹: $fileName")
                            fileOpener.openFolder(resolved.cid)
                            return@flow
                        }

                        is ResolvedTarget.File -> {
                            val file = resolved.file
                            emit("正在准备打开: ${file.name}...")
                            val siblings = ctx.listDirectory(resolved.parentCid)
                            when (val result =
                                fileOpener.open(file, siblings, fromTerminal = true)) {
                                is FileOpenResult.Success -> {
                                    emit("open: ${result.message}")
                                }

                                is FileOpenResult.Failure -> {
                                    emit("open: ${result.message}")
                                }

                                is FileOpenResult.Unsupported -> {
                                    emit("open: 未能识别该文件的专用预览器 (${result.fileName})")
                                }
                            }
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

    private data class SizeFilter(
        val operator: Char,
        val targetBytes: Long
    )

    private fun parseSizeFilter(raw: String): SizeFilter? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val operator = when (trimmed.first()) {
            '+' -> '+'
            '-' -> '-'
            else -> '='
        }
        val numberAndUnit = if (trimmed.first() == '+' || trimmed.first() == '-') {
            trimmed.substring(1)
        } else {
            trimmed
        }
        if (numberAndUnit.isEmpty()) return null

        var multiplier = 1L
        val lastChar = numberAndUnit.last()
        val numStr = if (lastChar.isLetter()) {
            multiplier = when (lastChar.lowercaseChar()) {
                'k' -> 1024L
                'm' -> 1024L * 1024L
                'g' -> 1024L * 1024L * 1024L
                'b', 'c' -> 1L
                else -> return null
            }
            numberAndUnit.dropLast(1)
        } else {
            numberAndUnit
        }

        val num = numStr.toLongOrNull() ?: return null
        return SizeFilter(operator, num * multiplier)
    }

    private fun matchesSize(fileSize: Long, filter: SizeFilter): Boolean {
        return when (filter.operator) {
            '+' -> fileSize > filter.targetBytes
            '-' -> fileSize < filter.targetBytes
            '=' -> fileSize == filter.targetBytes
            else -> false
        }
    }
}
