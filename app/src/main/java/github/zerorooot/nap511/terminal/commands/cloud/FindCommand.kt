package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitPath
import github.zerorooot.nap511.terminal.viewmodel.emitSystem
import github.zerorooot.nap511.viewmodel.formatFileBeanList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
 * 遵循 POSIX / GNU find 标准语义设计：
 * 1. 条件谓词：按名称通配符（-name）、类型（-type f/d）、扩展名（-suffix）、大小（-size）、空文件/空目录（-empty）；
 * 2. 逻辑运算符：逻辑非（-not / !）、逻辑或（-or / -o）、隐式与显式逻辑与（-and / -a）、括号分组（( / )）；
 * 3. 网盘专项能力：115官方分类（-filter）、全盘全局搜索（-global）；
 * 4. 批量操作：原生支持批量移入回收站（-delete），配合 -f 实现免二次确认。
 */
class FindCommand : TerminalCommand {

    override val name: String = "find"

    override val description: String =
        "网盘文件检索（支持按名称、类型、后缀、大小、空项筛选，支持 -not/-or 复合逻辑，及 -delete 批量安全删除）"

    override val usage: String = "find [path] [expression] [-delete] [-f]"

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
        CommandFlag("-not / !", "对后续条件取反（非运算）"),
        CommandFlag("-or / -o", "逻辑或运算，匹配两边任一条件"),
        CommandFlag("-global", "在整个 115 网盘根目录进行全局云端搜索"),
        CommandFlag("-delete", "将查找到的匹配项批量删除至回收站（默认执行前进行交互式二次确认）"),
        CommandFlag("-f", "配合 -delete 使用，强制直接删除免二次确认（同 rm -f）")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        // 1. 词法与语法解析，将命令行参数切分为控制参数与 AST 条件表达式树
        val parseResult = FindCommandArgsParser.parse(ast.rawArgs)
        if (parseResult.isFailure) {
            val errorMsg = parseResult.exceptionOrNull()?.message ?: "find: 参数解析失败"
            emitError(errorMsg)
            return@flow
        }

        val parsed = parseResult.getOrThrow()
        val pathArg = parsed.pathArg
        val maxDepth = parsed.maxDepth
        val isGlobal = parsed.isGlobal
        val filterType = parsed.filterType
        val isDelete = parsed.isDelete
        val isForce = parsed.isForce
        val expression = parsed.expression
        val firstKeyword = parsed.firstKeyword

        // 2. 目标起始检索路径解析
        val (targetCid, searchRootPath) = if (isGlobal) {
            Pair("0", "/根目录")
        } else if (pathArg != null) {
            // 解析目标路径：若是目录则以此为根遍历，若是单个文件则直接检查表达式后输出或终止
            when (val resolved = ctx.resolveTarget(pathArg)) {
                is ResolvedTarget.Directory -> Pair(resolved.cid, resolved.path)
                is ResolvedTarget.File -> {
                    // 对单文件执行表达式评估，仅在命中时处理输出或删除
                    if (!expression.evaluate(resolved.file, ctx)) {
                        return@flow
                    }

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
                            ctx.removeCachedFile(
                                parentCid = resolved.parentCid,
                                fid = resolved.file.fileId,
                                isFolder = false
                            )
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

        // 3. 分支 A：若指定了 -filter，调用 115 分类检索 API 并由 AST 表达式统一内存过滤
        if (filterType != null) {
            try {
                val res = ctx.fileRepository.filterFile(
                    cid = targetCid,
                    type = filterType
                )
                val rawList = formatFileBeanList(res.fileBeanList).toList()
                val list = rawList.filter { file -> expression.evaluate(file, ctx) }

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

        // 4. 分支 B：若指定了 -global，调用 115 全局 search API，并由 AST 表达式过滤结果
        if (isGlobal) {
            val queryKeyword = firstKeyword ?: ""
            if (queryKeyword.isEmpty()) {
                emitError("find: -global 全局搜索需要提供 -name 或 -suffix 关键词")
                return@flow
            }
            try {
                val searchResult = ctx.fileRepository.search(
                    cid = "0",
                    searchValue = queryKeyword
                )
                val rawList = searchResult.fileBeanList
                val list = rawList.filter { file -> expression.evaluate(file, ctx) }

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

        // 5. 分支 C：常规递归目录树搜索（依托 AST 统一判断条件与短路求值）
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

                // 统一评估 AST 条件表达式（内置短路逻辑）
                val matches = expression.evaluate(file, ctx)

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

                // 只要当前项为目录且未达到最大层级深度，继续向下递归搜索
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
}
