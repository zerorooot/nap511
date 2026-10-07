package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.commands.util.ComparisonFilter
import github.zerorooot.nap511.terminal.commands.util.SizeParser
import github.zerorooot.nap511.terminal.commands.util.TimeParser
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.GlobMatcher
import java.util.Locale

/**
 * find 命令条件表达式语法节点抽象接口 (AST Node)
 *
 * 遵循 POSIX / GNU find 标准，将所有的筛选条件与逻辑操作符统一建模为表达式树。
 * 支持短路求值（Short-circuit evaluation）与协程异步判断（例如 -empty 需异步扫描子目录）。
 */
internal sealed interface FindExpression {
    /**
     * 对单个目标文件执行条件匹配评估
     *
     * @param file 待检查的文件项数据实体
     * @param ctx 终端上下文，用于查询目录内容或缓存状态
     * @return 若符合当前条件则返回 true，否则返回 false
     */
    suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean
}

/**
 * 恒真表达式节点
 * 当未指定任何匹配条件（如直接执行 `find` 或 `find /path`）时使用，匹配所有文件
 */
internal object AlwaysTrueExpression : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean = true
}

/**
 * 文件名匹配谓词（-name）
 *
 * 严格区分大小写（遵循 Unix find 标准），支持通配符（*、?）匹配以及全名精确比对。
 * 为避免空模式导致的全局泛配，当 pattern 为空时直接判定为不匹配。
 */
internal class NamePredicate(val pattern: String) : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        if (pattern.isEmpty()) return false
        return GlobMatcher.matches(pattern, file.name, ignoreCase = false)
    }
}

/**
 * 文件名匹配谓词（-iname）
 *
 * 忽略大小写（遵循 Unix find 标准），支持通配符（*、?）匹配以及全名精确比对。
 * 当 pattern 为空时直接判定为不匹配。
 */
internal class InamePredicate(val pattern: String) : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        if (pattern.isEmpty()) return false
        return GlobMatcher.matches(pattern, file.name, ignoreCase = true)
    }
}

/**
 * 文件类型匹配谓词（-type）
 *
 * @param type "f" 匹配普通文件，"d" 匹配文件夹目录
 */
internal class TypePredicate(val type: String) : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        return when (type) {
            "f" -> !file.isFolder
            "d" -> file.isFolder
            else -> false
        }
    }
}

/**
 * 文件扩展名匹配谓词（-suffix）
 *
 * 自动剥离前缀点号（如传入 ".txt" 或 "txt" 均可兼容），忽略大小写匹配
 */
internal class SuffixPredicate(val suffix: String) : FindExpression {
    private val normalized = suffix.trimStart('.')

    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        val ext = file.name.substringAfterLast(".", "")
        return ext.equals(normalized, ignoreCase = true)
    }
}

/**
 * 通用数值度量比较 AST 谓词节点
 *
 * 统一承载文件大小（-size）、媒体时长（-time）等所有基于单值数值度量的匹配评估。
 *
 * @param filter 统一的数值比较过滤器
 * @param extractor 从 FileBean 中提取比对数值的函数；若文件不具备该属性或不满足前置条件则返回 null
 */
internal class MetricPredicate<T : Comparable<T>>(
    val filter: ComparisonFilter<T>,
    val extractor: (FileBean) -> T?
) : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        val actualValue = extractor(file) ?: return false
        return filter.matches(actualValue)
    }
}

/**
 * 空文件/空目录匹配谓词（-empty）
 *
 * 普通文件：文件大小为 0 时命中；
 * 目录：若其内部无任何子文件/子目录时命中。
 */
internal object EmptyPredicate : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        return if (file.isFolder) {
            ctx.listDirectory(file.categoryId).isEmpty()
        } else {
            (file.size.toLongOrNull() ?: 0L) == 0L
        }
    }
}

/**
 * 文件业务/种类分类枚举
 *
 * 映射自 formatFileBeanList 的图标分类与 115 业务标准，支持丰富的文件类型筛选
 */
internal enum class FindFileCategory(val targetIco: Int, val label: String) {
    DOC(R.drawable.txt, "文档"),
    IMG(R.drawable.png, "图片"),
    AUDIO(R.drawable.mp3, "音频"),
    VIDEO(R.drawable.mp4, "视频"),
    ZIP(R.drawable.zip, "压缩包"),
    APP(R.drawable.apk, "软件应用"),
    WEB(R.drawable.web, "网页"),
    ISO(R.drawable.iso, "镜像"),
    TORRENT(R.drawable.torrent, "种子"),
    FOLDER(R.drawable.folder, "目录"),
    OTHER(R.drawable.other, "其它");

    companion object {
        fun from(raw: String): FindFileCategory? {
            return when (raw.lowercase(Locale.ROOT)) {
                "doc", "document", "txt", "text", "文档", "文本" -> DOC
                "img", "image", "pic", "photo", "图片" -> IMG
                "audio", "music", "mp3", "音频", "音乐" -> AUDIO
                "video", "movie", "mp4", "视频" -> VIDEO
                "zip", "archive", "rar", "7z", "tar", "压缩", "压缩包" -> ZIP
                "app", "apk", "software", "软件", "应用" -> APP
                "web", "html", "htm", "网页" -> WEB
                "iso", "镜像" -> ISO
                "torrent", "bt", "种子" -> TORRENT
                "dir", "directory", "folder", "目录", "文件夹" -> FOLDER
                "other", "其它", "其他" -> OTHER
                else -> null
            }
        }
    }
}

/**
 * 文件种类匹配谓词（-filter）
 *
 * 依托 [formatFileBeanList] 所标记的 [FileBean.fileIco]，对文件种类进行内存短路匹配。
 * 完全融入 AST 语法树，天然支持 -not, -or, -and 及括号分组。
 */
internal class CategoryPredicate(val category: FindFileCategory) : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        if (category == FindFileCategory.FOLDER) {
            return file.isFolder
        }
        if (file.isFolder) return false
        return file.fileIco == category.targetIco
    }
}

/**
 * 逻辑非表达式（-not / !）
 *
 * 一元前缀操作符，对内部子表达式的求值结果执行布尔取反。
 */
internal class NotExpression(val expr: FindExpression) : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        return !expr.evaluate(file, ctx)
    }
}

/**
 * 逻辑与表达式（-and / -a / 隐式连续相邻）
 *
 * 二元操作符，具备短路求值特性：当左侧子表达式求值为 false 时，不再评估右侧子表达式。
 */
internal class AndExpression(
    val left: FindExpression,
    val right: FindExpression
) : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        return left.evaluate(file, ctx) && right.evaluate(file, ctx)
    }
}

/**
 * 逻辑或表达式（-or / -o）
 *
 * 二元操作符，具备短路求值特性：当左侧子表达式求值为 true 时，直接返回 true，短路跳过右侧子表达式。
 */
internal class OrExpression(
    val left: FindExpression,
    val right: FindExpression
) : FindExpression {
    override suspend fun evaluate(file: FileBean, ctx: TerminalContext): Boolean {
        return left.evaluate(file, ctx) || right.evaluate(file, ctx)
    }
}

/**
 * 表达式解析中间 Token 定义
 */
internal sealed interface FindToken {
    /** 具体的原子谓词操作（包装为 AST 节点） */
    data class Predicate(val expr: FindExpression) : FindToken

    /** 逻辑非操作符（-not / !） */
    data object Not : FindToken

    /** 逻辑或操作符（-or / -o） */
    data object Or : FindToken

    /** 逻辑与操作符（-and / -a） */
    data object And : FindToken

    /** 左括号（"("） */
    data object OpenParen : FindToken

    /** 右括号（")"） */
    data object CloseParen : FindToken
}

/**
 * 语法与参数解析异常
 */
internal class FindParseException(message: String) : Exception(message)

/**
 * 递归下降语法解析器 (Recursive Descent Parser)
 *
 * 语法产生式 (LL(1) 文法，遵循 POSIX 优先级规则：Parentheses > NOT > AND > OR)：
 * ```
 * Expr       := OrExpr
 * OrExpr     := AndExpr ( ("-or" | "-o") AndExpr )*
 * AndExpr    := NotExpr ( ("-and" | "-a" | <implicit>) NotExpr )*
 * NotExpr    := ("-not" | "!") NotExpr | Primary
 * Primary    := "(" Expr ")" | Predicate
 * ```
 */
internal class FindExpressionParser(private val tokens: List<FindToken>) {
    private var index = 0

    private fun peek(): FindToken? = if (index < tokens.size) tokens[index] else null
    private fun consume(): FindToken = tokens[index++]
    private fun hasNext(): Boolean = index < tokens.size

    fun parse(): FindExpression {
        if (tokens.isEmpty()) return AlwaysTrueExpression
        val expr = parseOrExpr()
        if (hasNext()) {
            val leftover = consume()
            if (leftover is FindToken.CloseParen) {
                throw FindParseException("find: 多余的闭合括号 ')'")
            }
            throw FindParseException("find: 意外的语法标记 '$leftover'")
        }
        return expr
    }

    /**
     * 解析逻辑或表达式 (OrExpr)
     * 优先级最低，左结合
     */
    private fun parseOrExpr(): FindExpression {
        var left = parseAndExpr()
        while (peek() is FindToken.Or) {
            consume() // 消耗 -or
            if (!hasNext() || peek() is FindToken.Or || peek() is FindToken.CloseParen) {
                throw FindParseException("find: '-or' 运算符右侧缺少表达式")
            }
            val right = parseAndExpr()
            left = OrExpression(left, right)
        }
        return left
    }

    /**
     * 解析逻辑与表达式 (AndExpr)
     * 优先级高于 Or，左结合。支持显式 `-and` / `-a` 以及隐式连续相邻谓词。
     */
    private fun parseAndExpr(): FindExpression {
        var left = parseNotExpr()
        while (hasNext() && peek() !is FindToken.Or && peek() !is FindToken.CloseParen) {
            if (peek() is FindToken.And) {
                consume() // 消耗显式 -and
                if (!hasNext() || peek() is FindToken.Or || peek() is FindToken.CloseParen) {
                    throw FindParseException("find: '-and' 运算符右侧缺少表达式")
                }
            }
            // 隐式 AND 或显式 AND 后的下一个表达式
            val right = parseNotExpr()
            left = AndExpression(left, right)
        }
        return left
    }

    /**
     * 解析逻辑非表达式 (NotExpr)
     * 优先级高于 And，右结合，支持连续取反（如 ! ! A）
     */
    private fun parseNotExpr(): FindExpression {
        if (peek() is FindToken.Not) {
            consume() // 消耗 -not / !
            if (!hasNext() || peek() is FindToken.Or || peek() is FindToken.CloseParen) {
                throw FindParseException("find: '-not' 运算符后面缺少表达式")
            }
            val inner = parseNotExpr()
            return NotExpression(inner)
        }
        return parsePrimary()
    }

    /**
     * 解析基础单元 (Primary: 括号分组 或 原子谓词)
     */
    private fun parsePrimary(): FindExpression {
        val token = peek() ?: throw FindParseException("find: 缺少表达式")
        return when (token) {
            is FindToken.OpenParen -> {
                consume() // 消耗 '('
                if (peek() is FindToken.CloseParen) {
                    throw FindParseException("find: 括号内表达式不能为空")
                }
                val innerExpr = parseOrExpr()
                if (peek() !is FindToken.CloseParen) {
                    throw FindParseException("find: 缺少闭合括号 ')'")
                }
                consume() // 消耗 ')'
                innerExpr
            }

            is FindToken.Predicate -> {
                consume()
                token.expr
            }

            is FindToken.Or -> {
                throw FindParseException("find: '-or' 运算符左侧缺少表达式")
            }

            is FindToken.And -> {
                throw FindParseException("find: '-and' 运算符左侧缺少表达式")
            }

            is FindToken.CloseParen -> {
                throw FindParseException("find: 多余的闭合括号 ')'")
            }

            else -> throw FindParseException("find: 意外的语法标记 '$token'")
        }
    }
}

/**
 * find 完整命令行参数解析后实体载体
 *
 * 将全局搜索范围/控制选项与过滤表达式 AST 分离解耦。
 */
internal data class ParsedFindCommand(
    val pathArg: String?,
    val maxDepth: Int,
    val isGlobal: Boolean,
    val isDelete: Boolean,
    val isForce: Boolean,
    val isPrint0: Boolean = false,
    val expression: FindExpression,
    val firstKeyword: String?
)

/**
 * 命令行参数分类器与构建器
 */
internal object FindCommandArgsParser {

    /**
     * 将命令行纯文本参数切分为结构化控制选项与 AST 过滤树
     *
     * @param args 命令行纯参数列表
     * @return 解析成功返回 [ParsedFindCommand]，失败返回包装了友好错误描述的 [Result.failure]
     */
    fun parse(args: List<String>): Result<ParsedFindCommand> {
        var pathArg: String? = null
        var maxDepth = 5
        var isGlobal = false
        var isDelete = false
        var isForce = false
        var isPrint0 = false
        var firstKeyword: String? = null
        val expressionTokens = mutableListOf<FindToken>()

        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg == "--" -> {
                    // -- 选项结束符：其后所有参数不再作为 Flag 解析，优先作为搜索路径
                    for (k in (i + 1) until args.size) {
                        if (pathArg == null) {
                            pathArg = args[k]
                            break
                        }
                    }
                    break
                }

                arg == "-maxdepth" -> {
                    if (i + 1 >= args.size) {
                        return Result.failure(FindParseException("find: '-maxdepth' 缺少参数"))
                    }
                    val depthStr = args[++i]
                    val depth = depthStr.toIntOrNull()
                    if (depth == null || depth < 0) {
                        return Result.failure(FindParseException("find: 非法的深度值 '$depthStr'"))
                    }
                    maxDepth = depth
                }

                arg == "-global" -> {
                    isGlobal = true
                }

                arg == "-delete" -> {
                    isDelete = true
                }

                arg == "-f" -> {
                    isForce = true
                }

                arg == "-print0" -> {
                    isPrint0 = true
                }

                arg == "-filter" -> {
                    if (i + 1 >= args.size) {
                        return Result.failure(FindParseException("find: '-filter' 缺少参数"))
                    }
                    val rawFilter = args[++i]
                    val category = FindFileCategory.from(rawFilter)
                        ?: return Result.failure(FindParseException("find: 未知的分类 '$rawFilter'"))
                    expressionTokens.add(FindToken.Predicate(CategoryPredicate(category)))
                }

                arg == "-name" -> {
                    if (i + 1 >= args.size) {
                        return Result.failure(FindParseException("find: '-name' 缺少参数"))
                    }
                    val pattern = args[++i]
                    if (firstKeyword == null) firstKeyword = pattern
                    expressionTokens.add(FindToken.Predicate(NamePredicate(pattern)))
                }

                arg == "-iname" -> {
                    if (i + 1 >= args.size) {
                        return Result.failure(FindParseException("find: '-iname' 缺少参数"))
                    }
                    val pattern = args[++i]
                    if (firstKeyword == null) firstKeyword = pattern
                    expressionTokens.add(FindToken.Predicate(InamePredicate(pattern)))
                }

                arg == "-type" -> {
                    if (i + 1 >= args.size) {
                        return Result.failure(FindParseException("find: '-type' 缺少参数"))
                    }
                    val type = args[++i]
                    if (type != "f" && type != "d") {
                        return Result.failure(FindParseException("find: 未知的类型 '$type'，有效值为 f 或 d"))
                    }
                    expressionTokens.add(FindToken.Predicate(TypePredicate(type)))
                }

                arg == "-suffix" -> {
                    if (i + 1 >= args.size) {
                        return Result.failure(FindParseException("find: '-suffix' 缺少参数"))
                    }
                    val suffix = args[++i].trimStart('.')
                    if (firstKeyword == null) firstKeyword = suffix
                    expressionTokens.add(FindToken.Predicate(SuffixPredicate(suffix)))
                }

                arg == "-size" -> {
                    if (i + 1 >= args.size) {
                        return Result.failure(FindParseException("find: '-size' 缺少参数"))
                    }
                    val spec = args[++i]
                    val filter = SizeParser.parse(spec)
                        ?: return Result.failure(FindParseException("find: 无效的文件大小格式 '$spec'"))
                    expressionTokens.add(FindToken.Predicate(MetricPredicate(filter) { file ->
                        file.size.toLongOrNull() ?: 0L
                    }))
                }

                arg == "-time" -> {
                    if (i + 1 >= args.size) {
                        return Result.failure(FindParseException("find: '-time' 缺少参数"))
                    }
                    val spec = args[++i]
                    val filter = TimeParser.parse(spec)
                        ?: return Result.failure(FindParseException("find: 无效的时间格式 '$spec'"))
                    expressionTokens.add(FindToken.Predicate(MetricPredicate(filter) { file ->
                        if (file.isFolder || file.playLong <= 0.0) null else file.playLong
                    }))
                }

                arg == "-empty" -> {
                    expressionTokens.add(FindToken.Predicate(EmptyPredicate))
                }

                arg == "-not" || arg == "!" -> {
                    expressionTokens.add(FindToken.Not)
                }

                arg == "-or" || arg == "-o" -> {
                    expressionTokens.add(FindToken.Or)
                }

                arg == "-and" || arg == "-a" -> {
                    expressionTokens.add(FindToken.And)
                }

                arg == "(" -> {
                    expressionTokens.add(FindToken.OpenParen)
                }

                arg == ")" -> {
                    expressionTokens.add(FindToken.CloseParen)
                }

                pathArg == null && !arg.startsWith("-") -> {
                    // 第一个非选项、非逻辑操作符参数作为搜索起始路径
                    pathArg = arg
                }

                arg.startsWith("-") -> {
                    return Result.failure(FindParseException("find: 未知选项 '$arg'"))
                }

                else -> {
                    return Result.failure(FindParseException("find: 意外的参数 '$arg'"))
                }
            }
            i++
        }

        return try {
            val ast = FindExpressionParser(expressionTokens).parse()
            Result.success(
                ParsedFindCommand(
                    pathArg = pathArg,
                    maxDepth = maxDepth,
                    isGlobal = isGlobal,
                    isDelete = isDelete,
                    isForce = isForce,
                    isPrint0 = isPrint0,
                    expression = ast,
                    firstKeyword = firstKeyword
                )
            )
        } catch (e: FindParseException) {
            Result.failure(e)
        }
    }
}
