package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.parentCid
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.commands.file.BatchTargetItem
import github.zerorooot.nap511.terminal.commands.file.TerminalBatchFileOps
import github.zerorooot.nap511.terminal.commands.util.CommandMessages
import github.zerorooot.nap511.terminal.commands.util.TerminalExceptionHandler
import github.zerorooot.nap511.terminal.viewmodel.emitPath
import github.zerorooot.nap511.terminal.viewmodel.emitSystem
import github.zerorooot.nap511.viewmodel.formatFileBeanList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * 待删除目标实体数据载体
 *
 * @property parentCid 所在父目录 ID，根目录或未知时为 null
 * @property fid 待删除项的真实文件/目录唯一标识符
 * @property name 文件或目录名称
 * @property isFolder 是否为文件夹
 * @property path 文件或目录的完整路径（用于内存拓扑剪枝）
 */
internal data class DeletableTarget(
    val parentCid: String?,
    val fid: String,
    val name: String,
    val isFolder: Boolean,
    val path: String = ""
)

/**
 * find 命令检索数据源策略 (Search Strategy)
 *
 * 抽象并隔离 3 种完全异构的数据拉取途径，避免多层条件嵌套穿透：
 */
private sealed interface FindSearchStrategy {
    /** 1. 常规目录树递归检索（通过 DFS 逐层下潜扫描网盘目录，限制深度在 [minDepth] 至 [maxDepth] 之间） */
    data class Tree(
        val minDepth: Int,
        val maxDepth: Int,
        val isMaxDepthAutoAdjusted: Boolean
    ) : FindSearchStrategy

    /** 2. 全盘云端全局搜索（调用官方 search API 在网盘根目录下全文检索指定关键词） */
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
    data class Directory(
        val targetCid: String,
        val searchRootPath: String,
        val targetParentCid: String?,
        val depth0Name: String
    ) : TargetResolution

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
 * @property isPrint0 是否启用 -print0，以 NUL 字符定界输出路径
 */
private data class FindPlan(
    val targetPath: String?,
    val strategy: FindSearchStrategy,
    val expression: FindExpression,
    val deletePolicy: FindDeletePolicy,
    val isPrint0: Boolean = false
)

/**
 * 网盘文件搜索与筛选命令（find）
 *
 * 遵循 POSIX / GNU find 标准语义设计：
 * 1. 条件谓词：按名称通配符（-name）、类型（-type f/d）、扩展名（-suffix）、大小（-size）、空文件/空目录（-empty）；
 * 2. 逻辑运算符：逻辑非（-not / !）、逻辑或（-or / -o）、隐式与显式逻辑与（-and / -a）、括号分组（( / )）；
 * 3. 网盘专项能力：115官方分类（-filter）、全盘全局搜索（-global）；
 * 4. 批量操作：原生支持批量移入回收站（-delete），配合 -f 实现免二次确认；
 * 5. 纯净管道集成：原生支持 -print0，以 NUL (\0) 分界安全透传包含空格的文件名至 xargs -0。
 *
 * 架构重构亮点：
 * - 采用“两阶段设计”：编译期提取不可变 [FindPlan]，运行期按策略多态分发执行；
 * - 彻底告别巨型闭包：将树遍历、分类搜索、全局搜索与批量删除拆解为独立高内聚方法。
 */
class FindCommand : TerminalCommand {

    override val name: String = "find"

    override val description: String =
        "网盘文件检索（支持按名称、大小、时长、类型、后缀、空项筛选，支持 -not/-or 复合逻辑，及 -delete 批量安全删除）"

    override val usage: String = "find [path] [expression] [-print0] [-delete] [-f]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-name <pattern>", "按文件名或通配符过滤匹配（区分大小写，如 -name '*.mp4'）"),
        CommandFlag("-iname <pattern>", "按文件名或通配符过滤匹配（忽略大小写，如 -iname '*.mp4'）"),
        CommandFlag("-type <f|d>", "按类型过滤，f 为普通文件，d 为目录"),
        CommandFlag("-suffix <ext>", "按文件扩展名筛选（如 -suffix apk）"),
        CommandFlag(
            "-filter <type>",
            "按文件种类筛选（基于图标与扩展名）。支持分类与别名：\n" +
                "               doc/document/txt/text/文档/文本 -> 文档\n" +
                "               img/image/pic/photo/图片 -> 图片\n" +
                "               audio/music/mp3/音频/音乐 -> 音频\n" +
                "               video/movie/mp4/视频 -> 视频\n" +
                "               zip/archive/rar/7z/tar/压缩/压缩包 -> 压缩包\n" +
                "               app/apk/software/软件/应用 -> 软件应用\n" +
                "               web/html/htm/网页 -> 网页\n" +
                "               iso/镜像 -> 镜像\n" +
                "               torrent/bt/种子 -> 种子\n" +
                "               dir/directory/folder/目录/文件夹 -> 目录\n" +
                "               other/其它/其他 -> 其它"
        ),
        CommandFlag("-mindepth <N>", "限制递归搜索的最小层级深度，起始项本身为 0，默认为 0"),
        CommandFlag("-maxdepth <N>", "限制递归搜索的最大层级深度，默认为 5"),
        CommandFlag("-empty", "只匹配空文件（大小为 0）或空目录（内容为空）"),
        CommandFlag("-size <[+|-]N[k|M|G]>", "按文件大小筛选（如 +100M 大于 100MB，-10k 小于 10KB）"),
        CommandFlag("-playlong <[+|-]N[s|m|h]|HH:mm:ss>", "按视频/音频时长筛选（如 +30m 大于 30 分钟，-10:00 小于 10 分钟）"),
        CommandFlag("-not / !", "对后续条件取反（非运算）"),
        CommandFlag("-or / -o", "逻辑或运算，匹配两边任一条件"),
        CommandFlag("-global", "在整个 115 网盘根目录进行全局云端搜索"),
        CommandFlag("-print0", "以 NUL (\\0) 字符作为各匹配项的结尾，配合 xargs -0 安全处理含空格文件名"),
        CommandFlag("-delete", "将查找到的匹配项批量删除至回收站（默认执行前进行交互式二次确认）"),
        CommandFlag("-f", "配合 -delete 使用，强制直接删除免二次确认（同 rm -f）")
    )

    /**
     * 该命令支持带参数值的选项集合
     *
     * 明确声明后，AST 解析器 [CommandAstParser] 会将 "-mindepth" 等连同后置值规范提取，
     * 避免将其当作复合布尔开关（例如拆分为 -m, -i, -n, -d, -e, -p, -t, -h）而意外触发帮助拦截。
     */
    override val valueOptions: Set<String> = setOf(
        "-name",
        "-iname",
        "-type",
        "-suffix",
        "-filter",
        "-mindepth",
        "-maxdepth",
        "-size",
        "-playlong"
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
            is FindSearchStrategy.Global -> {
                executeGlobalSearch(ctx, strategy.keyword, plan, deletableTargets)
            }
            is FindSearchStrategy.Tree -> {
                // 倒置深度区间短路优化：minDepth > maxDepth 时直接短路返回空，绝不发起网络请求
                if (strategy.minDepth > strategy.maxDepth) {
                    return@flow
                }
                executeTreeSearch(
                    ctx = ctx,
                    targetCid = dirResolution.targetCid,
                    searchRootPath = dirResolution.searchRootPath,
                    targetParentCid = dirResolution.targetParentCid,
                    depth0Name = dirResolution.depth0Name,
                    minDepth = strategy.minDepth,
                    maxDepth = strategy.maxDepth,
                    isMaxDepthAutoAdjusted = strategy.isMaxDepthAutoAdjusted,
                    plan = plan,
                    deletableTargets = deletableTargets
                )
            }
        }

        // 5. 若配置了删除策略，统一执行批量安全删除流水线
        if (plan.deletePolicy != FindDeletePolicy.None) {
            val searchRootCid = if (plan.strategy is FindSearchStrategy.Tree) dirResolution.targetCid else null
            performBatchDelete(ctx, deletableTargets, plan.deletePolicy, searchRootCid)
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

        // 决议搜索策略（优先级：Global > Tree）
        val strategy = when {
            parsed.isGlobal -> {
                val keyword = parsed.firstKeyword.orEmpty()
                if (keyword.isEmpty()) {
                    return Result.failure(IllegalArgumentException("find: -global 全局搜索需要提供 -name、-iname 或 -suffix 关键词"))
                }
                FindSearchStrategy.Global(keyword)
            }
            else -> FindSearchStrategy.Tree(
                minDepth = parsed.minDepth,
                maxDepth = parsed.maxDepth,
                isMaxDepthAutoAdjusted = parsed.isMaxDepthAutoAdjusted
            )
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
                deletePolicy = deletePolicy,
                isPrint0 = parsed.isPrint0
            )
        )
    }

    /**
     * 提取路径参数的基准文件名（basename）
     * 严格对标 GNU find：用于 Depth 0 虚拟 FileBean 的名称评估
     */
    private fun extractBasename(path: String): String {
        val trimmed = path.trimEnd('/')
        if (trimmed.isEmpty()) return "/"
        return trimmed.substringAfterLast('/')
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
            return TargetResolution.Directory(
                targetCid = "0",
                searchRootPath = "/根目录",
                targetParentCid = null,
                depth0Name = "根目录"
            )
        }

        // 遵循 GNU find 标准：若用户未显式传起始路径参数，默认以 "." 作为相对路径基准前缀
        val pathArg = plan.targetPath ?: return TargetResolution.Directory(
            targetCid = ctx.currentCid,
            searchRootPath = ".",
            targetParentCid = ctx.currentPathList.parentCid(),
            depth0Name = "."
        )

        return when (val resolved = ctx.resolveTarget(pathArg)) {
            is ResolvedTarget.Directory -> {
                TargetResolution.Directory(
                    targetCid = resolved.cid,
                    searchRootPath = pathArg,
                    targetParentCid = resolved.parentCid,
                    depth0Name = extractBasename(pathArg)
                )
            }

            is ResolvedTarget.File -> {
                val minDepth = if (plan.strategy is FindSearchStrategy.Tree) plan.strategy.minDepth else 0
                val maxDepth = if (plan.strategy is FindSearchStrategy.Tree) plan.strategy.maxDepth else 5
                handleSingleFile(ctx, resolved, plan, minDepth, maxDepth)
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
     *
     * 遵循 GNU find 语义：单个文件目标被视为 Depth 0。
     * 当且仅当 minDepth <= 0 <= maxDepth 时才执行求值与输出，否则跳过。
     */
    private suspend fun FlowCollector<TerminalOutput>.handleSingleFile(
        ctx: TerminalContext,
        resolved: ResolvedTarget.File,
        plan: FindPlan,
        minDepth: Int,
        maxDepth: Int
    ) {
        // 单文件视为 Depth 0，超出范围直接返回
        if (minDepth > 0 || maxDepth < 0) {
            return
        }

        if (!plan.expression.evaluate(resolved.file, ctx)) {
            return
        }

        when (plan.deletePolicy) {
            is FindDeletePolicy.None -> {
                // 保留用户原始输入路径（例如 find file.txt 输出 file.txt 而非展开为全路径）
                val pathStr = plan.targetPath ?: resolved.file.name
                if (plan.isPrint0) {
                    emitPath("$pathStr\u0000")
                } else {
                    emitPath(pathStr)
                }
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
        val effectivePid = resolved.parentCid.ifEmpty { resolved.file.categoryId }
        val item = BatchTargetItem(
            parentCid = effectivePid.takeIf { it.isNotEmpty() },
            fid = resolved.file.fileId,
            displayName = resolved.file.name,
            isFolder = false
        )
        val res = TerminalBatchFileOps.deleteGroup(ctx, effectivePid, listOf(item))
        if (res.isAllSuccess) {
            emitSystem("find: 已成功删除 '${resolved.file.name}' 至回收站")
        } else {
            val err = res.error.ifEmpty { "未知错误" }
            emitError("find: 删除失败: $err")
        }
    }

    /**
     * 分支 A：115 全局云端搜索（调用 search API）
     */
    private suspend fun FlowCollector<TerminalOutput>.executeGlobalSearch(
        ctx: TerminalContext,
        keyword: String,
        plan: FindPlan,
        deletableTargets: MutableList<DeletableTarget>
    ) {
        try {
            val searchResult = ctx.fileRepository.search(cid = "0", searchValue = keyword)
            val formattedList = formatFileBeanList(searchResult.fileBeanList)
            val matchedList = formattedList.filter { file -> plan.expression.evaluate(file, ctx) }

            if (matchedList.isEmpty()) {
                emitSystem("find: 未在网盘中找到匹配项")
                return
            }

            if (plan.deletePolicy != FindDeletePolicy.None) {
                matchedList.forEach { file ->
                    val isFolder = file.isFolder || file.fileId.isEmpty()
                    val fid = if (isFolder) file.categoryId else file.fileId
                    // 核心事实：普通文件使用 categoryId 作为 parentCid；目录使用 parentId 作为 parentCid
                    val parentCid = if (isFolder) file.parentId else file.categoryId.ifEmpty { file.parentId }
                    deletableTargets.add(
                        DeletableTarget(
                            parentCid = parentCid.takeIf { it.isNotEmpty() },
                            fid = fid,
                            name = file.name,
                            isFolder = isFolder
                        )
                    )
                }
            } else {
                // -print0 模式下抑制非路径的统计提示头，保障管道数据流纯净
                if (!plan.isPrint0) {
                    emitSystem("全局搜索结果（共 ${matchedList.size} 项）：")
                }
                matchedList.forEach { file ->
                    // 遵循 GNU find 标准：目录输出无尾随斜杠
                    val pathStr = file.name
                    if (plan.isPrint0) {
                        emitPath("$pathStr\u0000")
                    } else {
                        emitPath(pathStr)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emitError("find: 全局搜索失败: ${TerminalExceptionHandler.extractErrorMessage(e)}")
        }
    }

    /**
     * 分支 B：常规递归目录树检索（DFS 深度优先扫描，内置短路求值与 Depth 0 评估）
     */
    private suspend fun FlowCollector<TerminalOutput>.executeTreeSearch(
        ctx: TerminalContext,
        targetCid: String,
        searchRootPath: String,
        targetParentCid: String?,
        depth0Name: String,
        minDepth: Int,
        maxDepth: Int,
        isMaxDepthAutoAdjusted: Boolean,
        plan: FindPlan,
        deletableTargets: MutableList<DeletableTarget>
    ) {
        // 1. 若触发了云端防雪崩动态滑动窗口扩展，且非 print0 纯净模式，输出系统友好提醒
        if (isMaxDepthAutoAdjusted && !plan.isPrint0) {
            emitSystem("find: 已自动将最大搜索深度 -maxdepth 设置为 $maxDepth（如需调整请显式指定 -maxdepth）")
        }

        // 2. Depth 0 起始项本身评估（遵循 GNU find 标准：当且仅当 minDepth <= 0 时评估起始目录本身）
        if (minDepth <= 0) {
            // 构造规范的 Depth 0 虚拟 FileBean：
            // - name 提取自用户命令行输入的 basename，保证 find . -name "." 正确命中
            // - categoryId 设为真实 targetCid，支持 -empty 空目录检测
            // - parentId 设为 targetParentCid，保证待删除信息完整
            val depth0Bean = FileBean(
                fileId = "",
                categoryId = targetCid,
                parentId = targetParentCid ?: "",
                name = depth0Name,
                size = "0",
                playLong = 0.0,
                isFolder = true
            )

            if (plan.expression.evaluate(depth0Bean, ctx)) {
                if (plan.deletePolicy != FindDeletePolicy.None) {
                    // 双重永久安全保护：网盘根目录 ("0") 与当前工作目录 (ctx.currentCid) 严禁加入删除队列
                    if (targetCid != "0" && targetCid != ctx.currentCid) {
                        deletableTargets.add(
                            DeletableTarget(
                                parentCid = targetParentCid?.takeIf { it.isNotEmpty() },
                                fid = targetCid,
                                name = depth0Name,
                                isFolder = true,
                                path = searchRootPath
                            )
                        )
                    }
                } else {
                    // 只读模式：输出基准起始路径（无尾随斜杠）
                    if (plan.isPrint0) {
                        emitPath("$searchRootPath\u0000")
                    } else {
                        emitPath(searchRootPath)
                    }
                }
            }
        }

        // 3. Depth 1..N 递归子项扫描（仅当 maxDepth >= 1 时才需要发起 DFS 递归）
        if (maxDepth >= 1) {
            suspend fun searchRecursive(currentCid: String, currentPrefix: String, currentDepth: Int) {
                if (currentDepth > maxDepth) return
                val files = ctx.listDirectory(currentCid)
                for (file in files) {
                    // 防双斜杠规范化路径拼接：去除当前前缀末尾多余斜杠
                    val prefixForChild = if (currentPrefix == "/") "" else currentPrefix.trimEnd('/')
                    val fullPath = "$prefixForChild/${file.name}"

                    // 仅在 currentDepth >= minDepth 时执行条件表达式评估与输出/收集
                    if (currentDepth >= minDepth) {
                        val matches = plan.expression.evaluate(file, ctx)
                        if (matches) {
                            if (plan.deletePolicy != FindDeletePolicy.None) {
                                val fid = if (file.isFolder) file.categoryId else file.fileId
                                deletableTargets.add(
                                    DeletableTarget(
                                        parentCid = currentCid.takeIf { it.isNotEmpty() },
                                        fid = fid,
                                        name = file.name,
                                        isFolder = file.isFolder,
                                        path = fullPath
                                    )
                                )
                            } else {
                                // 遵循 GNU find 标准：目录输出无尾随斜杠
                                val pathStr = fullPath
                                if (plan.isPrint0) {
                                    emitPath("$pathStr\u0000")
                                } else {
                                    emitPath(pathStr)
                                }
                            }
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
    }

    /**
     * 批量安全移入回收站
     *
     * 架构考量（高内聚、高复用、高性能）：
     * 1. 递归树搜索 (Tree)：拥有明确的搜索起点目录 [searchRootCid]（例如 find ../../../ 或 find .）。
     *    传给 API 的 pid 统一为搜索起点目录 CID，由底层切片（每批 100 项）批量执行删除，
     *    避免不同深层子目录产生多次串行 HTTP 往返；同时底层结合各条目原本的 parentCid 自动进行多目录本地缓存清理；
     * 2. 全盘云端搜索 (Global)：匹配条目散落全盘无单一目标根目录，继续按各自所在目录的 parentCid 分组提交。
     */
    private suspend fun FlowCollector<TerminalOutput>.performBatchDelete(
        ctx: TerminalContext,
        targets: List<DeletableTarget>,
        policy: FindDeletePolicy,
        searchRootCid: String? = null
    ) {
        if (targets.isEmpty()) {
            emitSystem("find: 未找到匹配的删除目标")
            return
        }

        // 将目标转换为包含完整路径的 BatchTargetItem，统一交由底层公共组件进行双保险拓扑剪枝
        val convertedItems = targets.map {
            BatchTargetItem(
                parentCid = it.parentCid,
                fid = it.fid,
                displayName = it.name,
                isFolder = it.isFolder,
                path = it.path
            )
        }

        // 内存拓扑剪枝：直接复用公共拓扑剪枝组件剔除已被父级包含的深层子孙项
        val prunedItems = TerminalBatchFileOps.pruneBatchItems(convertedItems)

        // 若为交互式删除策略，执行前提示用户二次确认（展示剪枝后的真实待删顶层项数量）
        if (policy is FindDeletePolicy.Interactive) {
            val confirmed = ctx.confirm("find: 是否确认将匹配到的 ${prunedItems.size} 个项目移入回收站? (yes/no): ")
            if (!confirmed) {
                emitSystem(CommandMessages.cancelOperation("find"))
                return
            }
        }

        var successCount = 0

        if (!searchRootCid.isNullOrEmpty()) {
            // 分支 1：递归树搜索模式，以搜索起点目录 searchRootCid 作为统一 pid 进行单批/切片聚合删除
            try {
                val res = TerminalBatchFileOps.deleteGroup(ctx, searchRootCid, prunedItems)
                successCount = res.successCount
                if (res.failureCount > 0) {
                    val err = res.error.ifEmpty { "未知错误" }
                    emitError("find: 目录 [ID:$searchRootCid] 下的 ${res.failureCount} 个项目删除失败: $err")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emitError("find: 删除异常 [ID:$searchRootCid]: ${TerminalExceptionHandler.extractErrorMessage(e)}")
            }
        } else {
            // 分支 2：全盘云端搜索模式，按各条目所在 parentCid 分组批量删除
            val grouped = prunedItems.groupBy { it.parentCid }
            for ((parentCid, items) in grouped) {
                try {
                    val res = TerminalBatchFileOps.deleteGroup(ctx, parentCid, items)
                    successCount += res.successCount
                    if (res.failureCount > 0) {
                        val err = res.error.ifEmpty { "未知错误" }
                        emitError("find: 目录 [ID:${parentCid ?: "0"}] 下的 ${res.failureCount} 个项目删除失败: $err")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emitError("find: 删除异常 [ID:${parentCid ?: "0"}]: ${TerminalExceptionHandler.extractErrorMessage(e)}")
                }
            }
        }

        emitSystem("find: 已成功删除 $successCount / ${prunedItems.size} 个项目至回收站")
    }
}
