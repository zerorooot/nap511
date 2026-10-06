package github.zerorooot.nap511.terminal.engine

/**
 * 管道与命令参数切分中的单个 Token 语法节点
 *
 * 保留 Token 字符串文本及引物语法状态（是否在原始输入中由单引号或双引号包裹），
 * 为上层管道引擎（PipelineEngine）按需决定是否执行 Shell 级通配符（Glob）展开提供可靠依据。
 *
 * @property text 词法节点文本内容（已去除外层引号与转义符）
 * @property isQuoted 该 Token 在原始命令行输入中是否由单引号 `'...'` 或双引号 `"..."` 包裹
 */
data class Token(
    val text: String,
    val isQuoted: Boolean = false
)

/**
 * 管道中的单个命令阶段数据结构
 *
 * @property command 命令名称（如 "ls", "grep"）
 * @property args 参数文本列表（纯字符串形式，保持对下游命令调用的兼容）
 * @property tokens 包含引号与语法标记信息的完整 Token 节点列表（面向管道引擎精确控制 Shell 展开）
 * @property rawStage 原始命令阶段未解析字符串
 */
data class PipelineStage(
    val command: String,
    val args: List<String>,
    val tokens: List<Token> = emptyList(),
    val rawStage: String
)

/**
 * 轻量级命令行词法解析器 (Lexer)
 *
 * 负责将输入的终端字符串拆分为结构化的管道阶段与参数 Token 节点。
 * 特性：
 * 1. 支持单引号 '...' 与双引号 "..." 包裹（保留空格与内部特殊字符字面量，并标记 isQuoted 状态）；
 * 2. 支持反斜杠 '\' 转义字符（如 \"、\'、包含空格的路径）；
 * 3. 支持管道符 '|' 外部隔离拆分；
 * 4. 自动过滤连续空白与空阶段。
 */
object Lexer {

    /**
     * 将整行输入切分为多个管道阶段 (PipelineStage)
     * 例如: `ls -l "My Folder" | find -name '*.txt' | wc -l`
     *
     * @param input 终端输入的完整命令行字符串
     * @return 管道阶段列表
     */
    fun parsePipeline(input: String): List<PipelineStage> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        val rawStages = splitByPipe(trimmed)
        return rawStages.mapNotNull { raw ->
            val tokenObjects = tokenizeWithQuoteInfo(raw)
            if (tokenObjects.isEmpty()) {
                null
            } else {
                PipelineStage(
                    command = tokenObjects.first().text,
                    args = tokenObjects.drop(1).map { it.text },
                    tokens = tokenObjects.drop(1),
                    rawStage = raw.trim()
                )
            }
        }
    }

    /**
     * 将单个命令字符串切分为带引号语义标记的 Token 列表
     * 支持单引号、双引号与反斜杠转义，并精确捕获每个 Token 的 isQuoted 状态。
     *
     * @param input 单个阶段的命令字符串
     * @return Token 节点列表
     */
    fun tokenizeWithQuoteInfo(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var isEscaped = false
        var wasQuoted = false

        var i = 0
        while (i < input.length) {
            val c = input[i]

            if (isEscaped) {
                // 反斜杠转义：直接追加目标字符
                current.append(c)
                isEscaped = false
            } else if (c == '\\') {
                if (inSingleQuote) {
                    // 单引号内部反斜杠为普通字符
                    current.append(c)
                } else {
                    isEscaped = true
                }
            } else if (c == '\'' && !inDoubleQuote) {
                // 单引号切换，标记包含引号
                inSingleQuote = !inSingleQuote
                wasQuoted = true
            } else if (c == '"' && !inSingleQuote) {
                // 双引号切换，标记包含引号
                inDoubleQuote = !inDoubleQuote
                wasQuoted = true
            } else if (c.isWhitespace() && !inSingleQuote && !inDoubleQuote) {
                // 引号外部的空白符：作为参数 Token 切分界限（即使 current 为空，若 wasQuoted==true 也代表有效的空字符串参数 '' 或 ""）
                if (current.isNotEmpty() || wasQuoted) {
                    tokens.add(Token(current.toString(), wasQuoted))
                    current.clear()
                    wasQuoted = false
                }
            } else {
                current.append(c)
            }
            i++
        }

        // 处理未消费的末尾转义符
        if (isEscaped) {
            current.append('\\')
        }

        if (current.isNotEmpty() || wasQuoted) {
            tokens.add(Token(current.toString(), wasQuoted))
        }

        return tokens
    }

    /**
     * 将单个命令字符串切分为纯文本参数 Token 列表（保持向下兼容）
     *
     * @param input 命令行输入
     * @return 纯文本 Token 列表
     */
    fun tokenize(input: String): List<String> {
        return tokenizeWithQuoteInfo(input).map { it.text }
    }

    /**
     * 在引号外部安全地按 '|' 管道符分割命令
     */
    private fun splitByPipe(input: String): List<String> {
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

        if (current.isNotEmpty()) {
            stages.add(current.toString())
        }

        return stages
    }
}
