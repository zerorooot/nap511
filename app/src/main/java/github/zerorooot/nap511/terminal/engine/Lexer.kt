package github.zerorooot.nap511.terminal.engine

/**
 * 管道与命令参数切分中的单个 Token 语法节点
 *
 * 保留 Token 字符串文本及字符级受保护掩码（quoteMask），
 * 精确记录每个字符是否在单引号 `'...'`、双引号 `"..."` 或反斜杠 `\` 转义保护下生成。
 * 为上层 AST（CommandInvocationAst）与管道引擎（PipelineEngine）提供可靠依据。
 *
 * @property text 词法节点文本内容（已去除外层引号与转义符）
 * @property quoteMask 与 text 等长的布尔掩码，quoteMask[i] == true 表示第 i 个字符受引号或转义保护
 */
data class Token(
    val text: String,
    val quoteMask: BooleanArray = BooleanArray(text.length) { false },
    val isExplicitlyQuoted: Boolean = false
) {
    companion object {
        /**
         * 构造显式受引号保护的字面量 Token
         * 将整段文本的 quoteMask 标为 true，且标记 isExplicitlyQuoted = true，
         * 用于 xargs 等命令安全注入外部文本，杜绝短横线前缀参数被误判为命令行选项。
         */
        fun quoted(text: String): Token = Token(
            text = text,
            quoteMask = BooleanArray(text.length) { true },
            isExplicitlyQuoted = true
        )

        /**
         * 构造未受引号保护的常规原始 Token
         */
        fun raw(text: String): Token = Token(
            text = text,
            quoteMask = BooleanArray(text.length) { false },
            isExplicitlyQuoted = false
        )
    }

    /**
     * 判定指定下标处的字符是否为未加引号或转义保护的真实通配符 ('*' 或 '?')
     */
    fun isUnquotedWildcard(index: Int): Boolean {
        if (index !in text.indices) return false
        val c = text[index]
        return (c == '*' || c == '?') && !quoteMask[index]
    }

    /**
     * 当前 Token 中是否包含至少一个未受保护的通配符
     */
    val hasUnquotedWildcards: Boolean
        get() = text.indices.any { isUnquotedWildcard(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Token) return false
        return text == other.text && quoteMask.contentEquals(other.quoteMask)
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + quoteMask.contentHashCode()
        return result
    }
}

/**
 * 管道中的单个命令阶段数据结构
 *
 * @property command 命令名称（如 "ls", "grep"）
 * @property args 参数文本列表（纯字符串形式，保持对下游命令调用的兼容）
 * @property tokens 包含字符级受保护掩码信息的完整 Token 节点列表
 * @property rawStage 原始命令阶段未解析字符串
 */
data class PipelineStage(
    val command: String,
    val args: List<String>,
    val tokens: List<Token> = emptyList(),
    val rawStage: String
)

/**
 * 管道语法解析结果密封接口
 */
sealed interface PipelineParseResult {
    /** 语法解析成功 */
    data class Success(val stages: List<PipelineStage>) : PipelineParseResult

    /** 语法错误（如前置管道、悬挂管道、连续空管道、未闭合引号） */
    data class SyntaxError(val token: String, val message: String) : PipelineParseResult
}

/**
 * 单阶段 Tokenize 结果密封接口
 */
sealed interface TokenizeResult {
    data class Success(val tokens: List<Token>) : TokenizeResult
    data class Error(val token: String, val message: String) : TokenizeResult
}

/**
 * 轻量级命令行词法解析器 (Lexer)
 *
 * 遵循 POSIX.1-2017 语法规范：
 * 1. 严格检查管道符 '|'：拦截前置管道（`| wc`）、悬挂管道（`ls |`）、连续空管道（`echo a || cat`）；
 * 2. 严格检查未闭合单双引号，遇到未闭合引号时即刻抛出 SyntaxError；
 * 3. 字符级精确捕获 quoteMask，彻底防御通配符误转义与注入风险。
 */
object Lexer {

    /**
     * 将整行输入切分为多个管道阶段 (PipelineStage)
     *
     * @param input 终端输入的完整命令行字符串
     * @return [PipelineParseResult]，若有语法错误返回 [PipelineParseResult.SyntaxError]
     */
    fun parsePipeline(input: String): PipelineParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return PipelineParseResult.Success(emptyList())

        // 1. 前置管道符防御
        if (trimmed.startsWith("|")) {
            return PipelineParseResult.SyntaxError("|", "terminal: syntax error near unexpected token '|'")
        }

        // 2. 尾部悬挂管道符防御（需排除被转义的 \|）
        if (trimmed.endsWith("|") && !isEscapedAt(trimmed, trimmed.length - 1)) {
            return PipelineParseResult.SyntaxError("|", "terminal: syntax error near unexpected token '|'")
        }

        // 3. 安全切分管道符（处理引号与转义）
        val rawStages = splitByPipeSafe(trimmed)
            ?: return PipelineParseResult.SyntaxError(
                "\"",
                "terminal: syntax error: unexpected EOF while looking for matching quote"
            )

        val stages = mutableListOf<PipelineStage>()
        for (raw in rawStages) {
            // 4. 连续空管道检测（如 `a || b` 或 `a |   | b`）
            if (raw.isBlank()) {
                return PipelineParseResult.SyntaxError("|", "terminal: syntax error near unexpected token '|'")
            }

            when (val tokenResult = tokenizeWithQuoteMask(raw)) {
                is TokenizeResult.Error -> {
                    return PipelineParseResult.SyntaxError(tokenResult.token, tokenResult.message)
                }
                is TokenizeResult.Success -> {
                    val tokenObjects = tokenResult.tokens
                    if (tokenObjects.isNotEmpty()) {
                        stages.add(
                            PipelineStage(
                                command = tokenObjects.first().text,
                                args = tokenObjects.drop(1).map { it.text },
                                tokens = tokenObjects.drop(1),
                                rawStage = raw.trim()
                            )
                        )
                    }
                }
            }
        }

        return PipelineParseResult.Success(stages)
    }

    /**
     * 将单个命令阶段字符串切分为带字符级受保护掩码（quoteMask）的 Token 列表
     *
     * @param input 单个阶段的命令字符串
     * @return [TokenizeResult]
     */
    fun tokenizeWithQuoteMask(input: String): TokenizeResult {
        val tokens = mutableListOf<Token>()
        val current = StringBuilder()
        val maskList = mutableListOf<Boolean>()
        var inSingleQuote = false
        var inDoubleQuote = false
        var isEscaped = false
        var wasQuoted = false

        var i = 0
        while (i < input.length) {
            val c = input[i]

            if (isEscaped) {
                // 反斜杠转义：直接追加目标字符，并标记为受保护
                current.append(c)
                maskList.add(true)
                isEscaped = false
            } else if (c == '\\') {
                if (inSingleQuote) {
                    // 单引号内部反斜杠为字面量字符
                    current.append(c)
                    maskList.add(true)
                } else {
                    isEscaped = true
                }
            } else if (c == '\'' && !inDoubleQuote) {
                // 单引号切换
                inSingleQuote = !inSingleQuote
                wasQuoted = true
            } else if (c == '"' && !inSingleQuote) {
                // 双引号切换
                inDoubleQuote = !inDoubleQuote
                wasQuoted = true
            } else if (c.isWhitespace() && !inSingleQuote && !inDoubleQuote) {
                // 引号外部的空白符：作为参数 Token 切分界限
                if (current.isNotEmpty() || wasQuoted) {
                    tokens.add(
                        Token(
                            text = current.toString(),
                            quoteMask = maskList.toBooleanArray(),
                            isExplicitlyQuoted = wasQuoted
                        )
                    )
                    current.clear()
                    maskList.clear()
                    wasQuoted = false
                }
            } else {
                // 普通字符：如果在引号内部则标记为受保护，否则未受保护
                current.append(c)
                maskList.add(inSingleQuote || inDoubleQuote)
            }
            i++
        }

        // 未闭合引号语法检查
        if (inSingleQuote) {
            return TokenizeResult.Error("'", "terminal: syntax error: unexpected EOF while looking for matching single quote `'`")
        }
        if (inDoubleQuote) {
            return TokenizeResult.Error("\"", "terminal: syntax error: unexpected EOF while looking for matching double quote `\"`")
        }

        // 末尾遗留反斜杠转义符
        if (isEscaped) {
            return TokenizeResult.Error("\\", "terminal: syntax error: unexpected EOF near '\\'")
        }

        if (current.isNotEmpty() || wasQuoted) {
            tokens.add(
                Token(
                    text = current.toString(),
                    quoteMask = maskList.toBooleanArray(),
                    isExplicitlyQuoted = wasQuoted
                )
            )
        }

        return TokenizeResult.Success(tokens)
    }

    /**
     * 在引号外部安全地按 '|' 管道符分割命令
     * 若存在未闭合引号则返回 null
     */
    private fun splitByPipeSafe(input: String): List<String>? {
        val stages = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var isEscaped = false

        for (c in input) {
            if (isEscaped) {
                current.append(c)
                isEscaped = false
            } else if (c == '\\') {
                current.append(c)
                if (!inSingleQuote) {
                    isEscaped = true
                }
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
                current.append(c)
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
                current.append(c)
            } else if (c == '|' && !inSingleQuote && !inDoubleQuote) {
                stages.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
        }

        if (inSingleQuote || inDoubleQuote) {
            return null
        }

        if (current.isNotEmpty()) {
            stages.add(current.toString())
        }

        return stages
    }

    /**
     * 校验指定下标的字符是否被偶数个或奇数个反斜杠转义
     */
    private fun isEscapedAt(str: String, index: Int): Boolean {
        var backslashCount = 0
        var i = index - 1
        while (i >= 0 && str[i] == '\\') {
            backslashCount++
            i--
        }
        return backslashCount % 2 != 0
    }
}
