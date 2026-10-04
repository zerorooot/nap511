package github.zerorooot.nap511.terminal.engine

/**
 * 管道中的单个命令阶段
 * @param command 命令名称（如 "ls", "grep"）
 * @param args 参数列表（已去除引号和转义）
 * @param rawStage 原始命令阶段字符串
 */
data class PipelineStage(
    val command: String,
    val args: List<String>,
    val rawStage: String
)

/**
 * 轻量级词法解析器
 *
 * 支持：
 * 1. 单引号 '...' 与双引号 "..." 包裹（保留空格）
 * 2. 反斜杠转义（如 \"、\'、\ 包含空格的路径）
 * 3. 管道符 '|' 拆分
 * 4. 自动过滤多余空白
 */
object Lexer {

    /**
     * 将整行输入切分为多个管道阶段
     * 例如: `ls -l "My Folder" | grep *.mp4 | wc -l`
     */
    fun parsePipeline(input: String): List<PipelineStage> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        val rawStages = splitByPipe(trimmed)
        return rawStages.mapNotNull { raw ->
            val tokens = tokenize(raw)
            if (tokens.isEmpty()) {
                null
            } else {
                PipelineStage(
                    command = tokens.first(),
                    args = tokens.drop(1),
                    rawStage = raw.trim()
                )
            }
        }
    }

    /**
     * 将单个命令字符串切分为独立的参数 Token 列表
     * 支持双引号、单引号与反斜杠转义
     */
    fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var isEscaped = false

        var i = 0
        while (i < input.length) {
            val c = input[i]

            if (isEscaped) {
                // 转义字符直接追加
                current.append(c)
                isEscaped = false
            } else if (c == '\\') {
                if (inSingleQuote) {
                    // 单引号内反斜杠为普通字符
                    current.append(c)
                } else {
                    isEscaped = true
                }
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (c.isWhitespace() && !inSingleQuote && !inDoubleQuote) {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            } else {
                current.append(c)
            }
            i++
        }

        // 如果以未消费的转义符结尾
        if (isEscaped) {
            current.append('\\')
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
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
