package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.SizeParser
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.GlobMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 网盘文件搜索与筛选命令（find）
 *
 * 支持按名称通配符（-name）、类型（-type f/d）、扩展名（-suffix）、大小（-size）、空文件/目录（-empty）、
 * 115官方分类（-filter）以及全盘全局搜索（-global）。
 */
class FindCommand : TerminalCommand {

    override val name: String = "find"

    override val description: String = "网盘文件检索（支持按名称、类型、后缀、深度、115分类筛选及全盘全局搜索）"

    override val usage: String = "find [path] [options]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-name <pattern>", "按文件名或通配符过滤匹配（如 -name '*.mp4'）"),
        CommandFlag("-type <f|d>", "按类型过滤，f 为普通文件，d 为目录"),
        CommandFlag("-suffix <ext>", "按文件扩展名筛选（如 -suffix apk）"),
        CommandFlag(
            "-filter <type>",
            "按 115 业务分类筛选：1/doc(文档), 2/img(图片), 3/audio(音频), 4/video(视频), 5/zip(压缩), 6/app(软件)"
        ),
        CommandFlag("-maxdepth <N>", "限制递归搜索的最大层级深度，默认为5"),
        CommandFlag("-empty", "只匹配空文件（大小为 0）或空目录（内容为空）"),
        CommandFlag("-size <[+|-]N[k|M|G]>", "按文件大小筛选（如 +100M 大于 100MB，-10k 小于 10KB）"),
        CommandFlag("-global", "在整个 115 网盘根目录进行全局云端搜索")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
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

        val sizeFilter = sizeFilterSpec?.let { SizeParser.parse(it) }

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
                        SizeParser.matches(sz, sizeFilter)
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

        // 2. 若指定了 -global，调用 115 全局 search API
        if (isGlobal) {
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

        // 3. 递归本地缓存/网络目录树搜索
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
                    if (!SizeParser.matches(fileSize, sizeFilter)) {
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
