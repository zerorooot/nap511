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
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * 待删除目标实体数据载体
 *
 * @property parentCid 所在父目录 ID
 * @property fid 待删除项的真实文件/目录唯一标识符
 * @property name 文件或目录名称
 * @property isFolder 是否为文件夹
 */
private data class DeletableTarget(
    val parentCid: String,
    val fid: String,
    val name: String,
    val isFolder: Boolean
)

/**
 * find 命令检索数据源策略 (Search Strategy)
 *
 * 抽象并隔离 3 种完全异构的数据拉取途径，避免多层条件嵌套穿透：
 */
private sealed interface FindSearchStrategy {
    /** 1. 常规目录树递归检索（通过 DFS 逐层下潜扫描网盘目录，限制深度为 [maxDepth]） */
    data class Tree(val maxDepth: Int) : FindSearchStrategy

    /** 2. 115 业务分类筛选（调用官方 filterFile API，按文档/图片/视频/音乐/压缩包/软件分类） */
    data class Category(val filterType: Int) : FindSearchStrategy

    /** 3. 全盘云端全局搜索（调用官方 search API 在网盘根目录下全文检索指定关键词） */
    data class Global(val keyword: String) : FindSearchStrategy
}

/**
 * find 命令状态变更安全策略 (Delete Policy)
 *
 * 控制 `-delete` 标志在执行删除操作时的安全确认级别：
 */
private sealed interface FindDeletePolicy {
    /** 仅只读检索输出，不触发任何删除操作 */
    object None : FindDeletePolicy

    /** 交互式安全确认（默认模式：执行前提示用户确认，输入 yes 后才实施物理移入回收站） */
    object Interactive : FindDeletePolicy

    /** 强制免确认删除（指定了 `-f` 标志，直接批量删除） */
    object Force : FindDeletePolicy
}

/**
 * 目标路径解析结果载体
 */
private sealed interface TargetResolution {
    /** 目标解析为目录：以此目录作为遍历起点 */
    data class Directory(val targetCid: String, val searchRootPath: String) : TargetResolution

    /** 目标解析为单个独立文件：已就地完成求值与处理，流程可直接退出 */
    object SingleFileFinished : TargetResolution

    /** 路径不存在或解析失败 */
    object Failed : TargetResolution
}

/**
 * find 命令强类型不可变执行计划 (FindPlan)
 *
 * 在编译期根据 AST 语法树一次性合成，与具体执行上下文彻底解耦：
 * 1. 结构不可变，无副作用，保证幂等；
 * 2. 策略清晰解耦：检索策略、条件表达式树与删除策略完全正交独立。
 *
 * @property targetPath 起始目标路径参数（若为 null 则默认为当前工作目录）
 * @property strategy 数据源检索策略（递归树 / 业务分类 / 云端全局）
 * @property expression AST 条件表达式语法树（内置短路求值与优先级计算）
 * @property deletePolicy 删除操作策略（只读 / 交互确认 / 强制删除）
 */
private data class FindPlan(
    val targetPath: String?,
    val strategy: FindSearchStrategy,
    val expression: FindExpression,
    val deletePolicy: FindDeletePolicy
)

/**
 * 网盘文件搜索与筛选命令（find）
 *
 * 遵循 POSIX / GNU find 标准语义设计：
 * 1. 条件谓词：按名称通配符（-name）、类型（-type f/d）、扩展名（-suffix）、大小（-size）、空文件/空目录（-empty）；
 * 2. 逻辑运算符：逻辑非（-not / !）、逻辑或（-or / -o）、隐式与显式逻辑与（-and / -a）、括号分组（( / )）；
 * 3. 网盘专项能力：115官方分类（-filter）、全盘全局搜索（-global）；
 * 4. 批量操作：原生支持批量移入回收站（-delete），配合 -f 实现免二次确认。
 *
 * 架构重构亮点：
 * - 采用“两阶段设计”：编译期提取不可变 [FindPlan]，运行期按策略多态分发执行；
 * - 彻底告别巨型闭包：将树遍历、分类搜索、全局搜索与批量删除拆解为独立高内聚方法。
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

    /**
     * 主执行流程调度入口
     *
     * 遵循管道标准冷流规范，流程高度清晰直观：
     * 1. 计划编译 -> 2. 起始路径决议 -> 3. 策略检索分发 -> 4. 批量安全删除（若开启）
     */
    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        // 1. 编译期语法分析：将 AST 切分为强类型不可变 FindPlan
        val plan = compilePlan(ast).getOrElse { error ->
            emitError(error.message ?: "find: 参数解析失败")
            return@flow
        }

        // 2. 目标检索起点决议（包含对单个具体文件的特殊短路处理）
        val dirResolution = when (val resolution = resolveSearchTarget(ctx, plan)) {
            is TargetResolution.Directory -> resolution
            is TargetResolution.SingleFileFinished -> return@flow
            is TargetResolution.Failed -> return@flow
        }

        // 3. 收集容器：用于在 -delete 模式下汇聚匹配到的待删除实体
        val deletableTargets = mutableListOf<DeletableTarget>()

        // 4. 根据执行计划中的搜索策略分发具体检索逻辑
        when (val strategy = plan.strategy) {
            is FindSearchStrategy.Category -> {
                executeCategorySearch(ctx, dirResolution.targetCid, dirResolution.searchRootPath, strategy.filterType, plan, deletableTargets)
            }
            is FindSearchStrategy.Global -> {
                executeGlobalSearch(ctx, strategy.keyword, plan, deletableTargets)
            }
            is FindSearchStrategy.Tree -> {
                executeTreeSearch(ctx, dirResolution.targetCid, dirResolution.searchRootPath, strategy.maxDepth, plan, deletableTargets)
            }
        }

        // 5. 若配置了删除策略，统一执行批量安全删除流水线
        if (plan.deletePolicy != FindDeletePolicy.None) {
            performBatchDelete(ctx, deletableTargets, plan.deletePolicy)
        }
    }

    /**
     * 编译期计划构建：将命令行参数解析决议为结构化不可变 [FindPlan]
     */
    private fun compilePlan(ast: CommandInvocationAst): Result<FindPlan> {
        val parseResult = FindCommandArgsParser.parse(ast.rawArgs)
        if (parseResult.isFailure) {
            return Result.failure(parseResult.exceptionOrNull() ?: IllegalArgumentException("find: 参数解析失败"))
        }

        val parsed = parseResult.getOrThrow()

        // 决议搜索策略（优先级：Category > Global > Tree）
        val strategy = when {
            parsed.filterType != null -> FindSearchStrategy.Category(parsed.filterType)
            parsed.isGlobal -> {
                val keyword = parsed.firstKeyword.orEmpty()
                if (keyword.isEmpty()) {
                    return Result.failure(IllegalArgumentException("find: -global 全局搜索需要提供 -name 或 -suffix 关键词"))
                }
                FindSearchStrategy.Global(keyword)
            }
            else -> FindSearchStrategy.Tree(parsed.maxDepth)
        }

        // 决议删除安全策略
        val deletePolicy = when {
            !parsed.isDelete -> FindDeletePolicy.None
            parsed.isForce -> FindDeletePolicy.Force
            else -> FindDeletePolicy.Interactive
        }

        return Result.success(
            FindPlan(
                targetPath = parsed.pathArg,
                strategy = strategy,
                expression = parsed.expression,
                deletePolicy = deletePolicy
            )
        )
    }

    /**
     * 起始路径解析：将输入的路径字符串转换为目标 CID 与规范化路径
     * 若目标直接为一个文件，则就地执行表达式求值并处理输出或删除，返回 [TargetResolution.SingleFileFinished]。
     */
    private suspend fun FlowCollector<TerminalOutput>.resolveSearchTarget(
        ctx: TerminalContext,
        plan: FindPlan
    ): TargetResolution {
        // 全局搜索固定从根目录 ("0") 展开
        if (plan.strategy is FindSearchStrategy.Global) {
            return TargetResolution.Directory(targetCid = "0", searchRootPath = "/根目录")
        }

        val pathArg = plan.targetPath ?: return TargetResolution.Directory(
            targetCid = ctx.currentCid,
            searchRootPath = ctx.currentPath
        )

        return when (val resolved = ctx.resolveTarget(pathArg)) {
            is ResolvedTarget.Directory -> {
                TargetResolution.Directory(targetCid = resolved.cid, searchRootPath = resolved.path)
            }

            is ResolvedTarget.File -> {
                handleSingleFile(ctx, resolved, plan)
                TargetResolution.SingleFileFinished
            }

            null -> {
                emitError("find: '$pathArg': No such file or directory")
                TargetResolution.Failed
            }
        }
    }

    /**
     * 单文件目标处理：对目标单个文件执行 AST 表达式匹配，并在命中时执行输出或二次确认删除
     */
    private suspend fun FlowCollector<TerminalOutput>.handleSingleFile(
        ctx: TerminalContext,
        resolved: ResolvedTarget.File,
        plan: FindPlan
    ) {
        if (!plan.expression.evaluate(resolved.file, ctx)) {
            return
        }

        when (plan.deletePolicy) {
            is FindDeletePolicy.None -> {
                emitPath(resolved.fullPath)
            }

            is FindDeletePolicy.Interactive -> {
                val confirmed = ctx.confirm("find: 是否确认删除 '${resolved.file.name}' 至回收站? (yes/no): ")
                if (!confirmed) {
                    emitSystem("find: 已取消删除操作")
                    return
                }
                executeSingleFileDelete(ctx, resolved)
            }

            is FindDeletePolicy.Force -> {
                executeSingleFileDelete(ctx, resolved)
            }
        }
    }

    /**
     * 执行单个文件的物理删除与父目录缓存同步
     */
    private suspend fun FlowCollector<TerminalOutput>.executeSingleFileDelete(
        ctx: TerminalContext,
        resolved: ResolvedTarget.File
    ) {
        val res = runCatching {
            ctx.fileRepository.delete(pid = resolved.parentCid, fid = resolved.file.fileId)
        }.getOrNull()

        if (res?.state == true) {
            ctx.removeCachedFile(parentCid = resolved.parentCid, fid = resolved.file.fileId, isFolder = false)
            emitSystem("find: 已成功删除 '${resolved.file.name}' 至回收站")
        } else {
            emitError("find: 删除失败: ${res?.error ?: "未知错误"}")
        }
    }

    /**
     * 分支 A：115 业务分类检索（调用 filterFile API）
     */
    private suspend fun FlowCollector<TerminalOutput>.executeCategorySearch(
        ctx: TerminalContext,
        targetCid: String,
        searchRootPath: String,
        filterType: Int,
        plan: FindPlan,
        deletableTargets: MutableList<DeletableTarget>
    ) {
        try {
            val res = ctx.fileRepository.filterFile(cid = targetCid, type = filterType)
            val rawList = formatFileBeanList(res.fileBeanList).toList()
            val matchedList = rawList.filter { file -> plan.expression.evaluate(file, ctx) }

            if (matchedList.isEmpty()) {
                emitSystem("find: 未找到匹配的分类文件 (filterType: $filterType)")
                return
            }

            if (plan.deletePolicy != FindDeletePolicy.None) {
                matchedList.forEach { file ->
                    val fid = if (file.isFolder) file.categoryId else file.fileId
                    deletableTargets.add(
                        DeletableTarget(parentCid = targetCid, fid = fid, name = file.name, isFolder = file.isFolder)
                    )
                }
            } else {
                emitSystem("分类筛选结果（共 ${matchedList.size} 项，分类: $filterType）：")
                for (file in matchedList) {
                    val fullPath = if (searchRootPath == "/") "/${file.name}" else "$searchRootPath/${file.name}"
                    val isFolder = file.fileId.isEmpty()
                    emitPath(fullPath + if (isFolder) "/" else "")
                }
            }
        } catch (e: Exception) {
            emitError("find: 分类筛选失败: ${e.message}")
        }
    }

    /**
     * 分支 B：115 全局云端搜索（调用 search API）
     */
    private suspend fun FlowCollector<TerminalOutput>.executeGlobalSearch(
        ctx: TerminalContext,
        keyword: String,
        plan: FindPlan,
        deletableTargets: MutableList<DeletableTarget>
    ) {
        try {
            val searchResult = ctx.fileRepository.search(cid = "0", searchValue = keyword)
            val matchedList = searchResult.fileBeanList.filter { file -> plan.expression.evaluate(file, ctx) }

            if (matchedList.isEmpty()) {
                emitSystem("find: 未在网盘中找到匹配项")
                return
            }

            if (plan.deletePolicy != FindDeletePolicy.None) {
                matchedList.forEach { file ->
                    val isFolder = file.fileId.isEmpty()
                    val fid = if (isFolder) file.categoryId else file.fileId
                    val parentCid = if (isFolder) "" else file.parentId
                    deletableTargets.add(
                        DeletableTarget(parentCid = parentCid, fid = fid, name = file.name, isFolder = isFolder)
                    )
                }
            } else {
                emitSystem("全局搜索结果（共 ${matchedList.size} 项）：")
                matchedList.forEach { file ->
                    val isFolder = file.fileId.isEmpty()
                    emitPath(file.name + if (isFolder) "/" else "")
                }
            }
        } catch (e: Exception) {
            emitError("find: 全局搜索失败: ${e.message}")
        }
    }

    /**
     * 分支 C：常规递归目录树检索（DFS 深度优先扫描，内置短路求值）
     */
    private suspend fun FlowCollector<TerminalOutput>.executeTreeSearch(
        ctx: TerminalContext,
        targetCid: String,
        searchRootPath: String,
        maxDepth: Int,
        plan: FindPlan,
        deletableTargets: MutableList<DeletableTarget>
    ) {
        suspend fun searchRecursive(currentCid: String, currentPrefix: String, currentDepth: Int) {
            if (currentDepth > maxDepth) return
            val files = ctx.listDirectory(currentCid)
            for (file in files) {
                val fullPath = if (currentPrefix == "/") "/${file.name}" else "$currentPrefix/${file.name}"

                // 统一评估 AST 条件表达式（内置短路逻辑）
                val matches = plan.expression.evaluate(file, ctx)

                if (matches) {
                    if (plan.deletePolicy != FindDeletePolicy.None) {
                        val fid = if (file.isFolder) file.categoryId else file.fileId
                        deletableTargets.add(
                            DeletableTarget(parentCid = currentCid, fid = fid, name = file.name, isFolder = file.isFolder)
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
    }

    /**
     * 批量安全移入回收站（统一处理交互确认、网络删除及本地目录缓存级联失效）
     */
    private suspend fun FlowCollector<TerminalOutput>.performBatchDelete(
        ctx: TerminalContext,
        targets: List<DeletableTarget>,
        policy: FindDeletePolicy
    ) {
        if (targets.isEmpty()) {
            emitSystem("find: 未找到匹配的删除目标")
            return
        }

        // 若为交互式删除策略，执行前提示用户二次确认
        if (policy is FindDeletePolicy.Interactive) {
            val confirmed = ctx.confirm("find: 是否确认将匹配到的 ${targets.size} 个项目移入回收站? (yes/no): ")
            if (!confirmed) {
                emitSystem("find: 已取消删除操作")
                return
            }
        }

        var successCount = 0
        for (target in targets) {
            try {
                val res = ctx.fileRepository.delete(pid = target.parentCid, fid = target.fid)
                if (res.state) {
                    successCount++
                    // 就地从父目录缓存中剔除并级联清理文件夹缓存，保持会话状态一致
                    ctx.removeCachedFile(parentCid = target.parentCid, fid = target.fid, isFolder = target.isFolder)
                }
            } catch (_: Exception) {
            }
        }

        emitSystem("find: 已成功删除 $successCount / ${targets.size} 个项目至回收站")
    }
}
