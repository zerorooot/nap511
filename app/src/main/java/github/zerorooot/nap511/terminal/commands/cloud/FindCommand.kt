package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.terminal.commands.util.SizeParser
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.GlobMatcher
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitPath
import github.zerorooot.nap511.terminal.viewmodel.emitSystem
import github.zerorooot.nap511.viewmodel.formatFileBeanList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 待删除目标实体数据载体
 */
private data class DeletableTarget(
    val parentCid: String,
    val fid: String,
    val name: String,
    val isFolder: Boolean
)

/**
 * 网盘文件搜索与筛选命令（find）
 *
 * 支持按名称通配符（-name）、类型（-type f/d）、扩展名（-suffix）、大小（-size）、空文件/目录（-empty）、
 * 115官方分类（-filter）以及全盘全局搜索（-global）。
 * 原生支持批量删除操作（-delete），配合 -f 实现强制删除免确认。
 */
class FindCommand : TerminalCommand {

    override val name: String = "find"

    override val description: String =
        "网盘文件检索（支持按名称、类型、后缀、深度、115分类筛选、全盘全局搜索及 -delete 批量安全删除）"

    override val usage: String = "find [path] [options] [-delete] [-f]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-name <pattern>", "按文件名或通配符过滤匹配（如 -name '*.mp4'）"),
        CommandFlag("-type <f|d>", "按类型过滤，f 为普通文件，d 为目录"),
        CommandFlag("-suffix <ext>", "按文件扩展名筛选（如 -suffix apk）"),
        CommandFlag(
            "-filter <type>",
            "按 115 业务分类筛选：1|doc(文档), 2|img(图片), 3|audio(音频), 4|video(视频), 5|zip(压缩), 6|app(软件)"
        ),
        CommandFlag("-maxdepth <N>", "限制递归搜索的最大层级深度，默认为5"),
        CommandFlag("-empty", "只匹配空文件（大小为 0）或空目录（内容为空）"),
        CommandFlag("-size <[+|-]N[k|M|G]>", "按文件大小筛选（如 +100M 大于 100MB，-10k 小于 10KB）"),
        CommandFlag("-global", "在整个 115 网盘根目录进行全局云端搜索"),
        CommandFlag("-delete", "将查找到的匹配项批量删除至回收站（默认执行前进行交互式二次确认）"),
        CommandFlag("-f", "配合 -delete 使用，强制直接删除免二次确认（同 rm -f）")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        var namePattern: String? = null
        var typeFilter: String? = null
        var suffixFilter: String? = null
        var filterType: Int? = null
        var maxDepth = 5
        var isEmptyFilter = false
        var sizeFilterSpec: String? = null
        var pathArg: String? = null
        var isGlobal = false
        var isDelete = false
        var isForce = false

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
                arg == "-delete" -> isDelete = true
                arg == "-f" -> isForce = true
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
                    if (isDelete) {
                        if (!isForce) {
                            val confirmed = ctx.confirm("find: 是否确认删除 '${resolved.file.name}' 至回收站? (yes/no): ")
                            if (!confirmed) {
                                emitSystem("find: 已取消删除操作")
                                return@flow
                            }
                        }
                        val res = runCatching {
                            ctx.fileRepository.delete(pid = resolved.parentCid, fid = resolved.file.fileId)
                        }.getOrNull()
                        if (res?.state == true) {
                            ctx.removeCachedFile(parentCid = resolved.parentCid, fid = resolved.file.fileId, isFolder = false)
                            emitSystem("find: 已成功删除 '${resolved.file.name}' 至回收站")
                        } else {
                            emitError("find: 删除失败: ${res?.error ?: "未知错误"}")
                        }
                    } else {
                        emitPath(resolved.fullPath)
                    }
                    return@flow
                }

                null -> {
                    emitError("find: '$pathArg': No such file or directory")
                    return@flow
                }
            }
        } else {
            Pair(ctx.currentCid, ctx.currentPath)
        }

        val deletableTargets = mutableListOf<DeletableTarget>()

        // 1. 若指定了 -filter，参考 FileViewModel.filterFile 直接调用 fileRepository.filterFile
        if (filterType != null) {
            try {
                val res = ctx.fileRepository.filterFile(
                    cid = targetCid,
                    type = filterType
                )
                var list = formatFileBeanList(res.fileBeanList).toList()

                if (suffixFilter != null) {
                    list = list.filter {
                        it.name.substringAfterLast(".", "")
                            .equals(suffixFilter, ignoreCase = true)
                    }
                }
                if (namePattern != null) {
                    // 【问题修复说明】：
                    // - 背景：用户执行 `find -name ''` 传入空匹配模式进行检索。
                    // - 经过：此前代码直接使用 `it.name.contains("")` 兜底比对，但在 Java/Kotlin 中任意非空字符串调用 `.contains("")` 均恒为 true。
                    // - 结果：导致 `find -name ''` 误泛配并输出了全部文件，引发单元测试断言失败。
                    // - 为什么这么改：显式判定 `namePattern.isEmpty()`，在模式为空时直接返回空列表，阻止空字符串包含判定导致的泛配现象。
                    list = if (namePattern.isEmpty()) {
                        emptyList()
                    } else {
                        list.filter {
                            GlobMatcher.matches(
                                namePattern,
                                it.name
                            ) || it.name.contains(namePattern, ignoreCase = true)
                        }
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
                    emitSystem("find: 未找到匹配的分类文件 (filterType: $filterType)")
                    return@flow
                }

                if (isDelete) {
                    list.forEach { file ->
                        val fid = if (file.isFolder) file.categoryId else file.fileId
                        deletableTargets.add(
                            DeletableTarget(
                                parentCid = targetCid,
                                fid = fid,
                                name = file.name,
                                isFolder = file.isFolder
                            )
                        )
                    }
                    performBatchDelete(ctx, deletableTargets, isForce)
                } else {
                    emitSystem("分类筛选结果（共 ${list.size} 项，分类: $filterType）：")
                    for (file in list) {
                        val fullPath = if (searchRootPath == "/") "/${file.name}" else "$searchRootPath/${file.name}"
                        val isFolder = file.fileId.isEmpty()
                        emitPath(fullPath + if (isFolder) "/" else "")
                    }
                }
            } catch (e: Exception) {
                emitError("find: 分类筛选失败: ${e.message}")
            }
            return@flow
        }

        // 2. 若指定了 -global，调用 115 全局 search API
        if (isGlobal) {
            val queryKeyword = namePattern ?: suffixFilter ?: ""
            if (queryKeyword.isEmpty()) {
                emitError("find: -global 全局搜索需要提供 -name 或 -suffix 关键词")
                return@flow
            }
            try {
                val searchResult = ctx.fileRepository.search(
                    cid = "0",
                    searchValue = queryKeyword
                )
                val list = searchResult.fileBeanList
                if (list.isEmpty()) {
                    emitSystem("find: 未在网盘中找到匹配项")
                    return@flow
                }

                if (isDelete) {
                    list.forEach { file ->
                        val isFolder = file.fileId.isEmpty()
                        val fid = if (isFolder) file.categoryId else file.fileId
                        val parentCid = if (isFolder) "" else file.parentId
                        deletableTargets.add(
                            DeletableTarget(
                                parentCid = parentCid,
                                fid = fid,
                                name = file.name,
                                isFolder = isFolder
                            )
                        )
                    }
                    performBatchDelete(ctx, deletableTargets, isForce)
                } else {
                    emitSystem("全局搜索结果（共 ${list.size} 项）：")
                    list.forEach { file ->
                        val isFolder = file.fileId.isEmpty()
                        emitPath(file.name + if (isFolder) "/" else "")
                    }
                }
            } catch (e: Exception) {
                emitError("find: 全局搜索失败: ${e.message}")
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
                    // 【问题修复说明】：
                    // - 背景：用户执行 `find -name ''` 传入空匹配模式递归检索目录树。
                    // - 经过：此前代码使用 `!GlobMatcher.matches(...) && !file.name.contains(...)` 判断不匹配，但在 `namePattern` 为空字符串 `""` 时，
                    //   任意文件名的 `.contains("")` 均恒为 true，使得 `!file.name.contains("")` 恒为 false，避开了 `matches = false` 的设值。
                    // - 结果：导致 `find -name ''` 误将当前目录树下的全部文件当作匹配项输出，导致断言失败。
                    // - 为什么这么改：优先检查 `namePattern.isEmpty()`，在匹配模式为空时直接置 `matches = false`，杜绝空字符串全局泛配风险。
                    if (namePattern.isEmpty()) {
                        matches = false
                    } else if (!GlobMatcher.matches(
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
                    if (isDelete) {
                        val fid = if (file.isFolder) file.categoryId else file.fileId
                        deletableTargets.add(
                            DeletableTarget(
                                parentCid = currentCid,
                                fid = fid,
                                name = file.name,
                                isFolder = file.isFolder
                            )
                        )
                    } else {
                        emitPath(fullPath + if (file.isFolder) "/" else "")
                    }
                }

                if (file.isFolder && currentDepth < maxDepth) {
                    searchRecursive(file.categoryId, fullPath, currentDepth + 1)
                }
            }
        }

        searchRecursive(targetCid, searchRootPath, 1)

        if (isDelete) {
            performBatchDelete(ctx, deletableTargets, isForce)
        }
    }

    /**
     * 执行批量移入回收站安全操作（支持 -f 强制免确认）
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<TerminalOutput>.performBatchDelete(
        ctx: TerminalContext,
        targets: List<DeletableTarget>,
        isForce: Boolean
    ) {
        if (targets.isEmpty()) {
            emitSystem("find: 未找到匹配的删除目标")
            return
        }

        // 默认安全交互确认（类似 rm，若未指定 -f 则提示用户进行二次确认）
        if (!isForce) {
            val confirmed = ctx.confirm("find: 是否确认将匹配到的 ${targets.size} 个项目移入回收站? (yes/no): ")
            if (!confirmed) {
                emitSystem("find: 已取消删除操作")
                return
            }
        }

        var successCount = 0
        for (target in targets) {
            try {
                val res = ctx.fileRepository.delete(
                    pid = target.parentCid,
                    fid = target.fid
                )
                if (res.state) {
                    successCount++
                    // 就地从父目录缓存中剔除并级联清理文件夹缓存，无需整体失效
                    ctx.removeCachedFile(parentCid = target.parentCid, fid = target.fid, isFolder = target.isFolder)
                }
            } catch (_: Exception) {
            }
        }

        emitSystem("find: 已成功删除 $successCount / ${targets.size} 个项目至回收站")
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
