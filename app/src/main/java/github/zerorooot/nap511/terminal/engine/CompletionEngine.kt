package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.bean.FileBean

/**
 * 补全候选项类型
 */
enum class CandidateType {
    COMMAND,   // 终端已注册命令 ⚡
    DIRECTORY, // 文件夹 📁
    FILE,      // 普通文件 📄
    FLAG       // 命令参数选项 🚩
}

/**
 * 补全候选项实体
 *
 * @param name 原始名称（未转义，如 "My Documents", "test.mp4", "-l", "cd"）
 * @param displayText 列表/Chip 显示名称（如 "My Documents/", "test.mp4", "-l"）
 * @param insertText 补全到命令行中的文本（含空格转义与后缀，如 "My\\ Documents/", "test.mp4 "）
 * @param type 候选类型
 * @param isDirectory 是否是目录
 */
data class CompletionCandidate(
    val name: String,
    val displayText: String,
    val insertText: String,
    val type: CandidateType,
    val isDirectory: Boolean
)

/**
 * 补全上下文分类
 */
enum class CompletionContextType {
    COMMAND, // 正在输入命令名
    FLAG,    // 正在输入命令参数（如 -l）
    PATH     // 正在输入文件/路径
}

/**
 * 解析后的补全上下文信息
 *
 * @param contextType 当前补全类型
 * @param commandName 当前命令名称（如 cd, ls, open 等）
 * @param rawToken 当前光标所在的原始 Token（包含可能存在的反斜杠转义）
 * @param parentPath 若为路径补全，上一级路径前缀（如 "Documents/" 或 ""）
 * @param prefix 当前正在匹配的前缀过滤词（如 "Do" 或 ""）
 * @param tokenStartIndex 当前 Token 在整个 input 中的起始索引
 * @param tokenEndIndex 当前 Token 在整个 input 中的结束索引（即解析时的光标位）
 */
data class ParsedContext(
    val contextType: CompletionContextType,
    val commandName: String,
    val rawToken: String,
    val parentPath: String,
    val prefix: String,
    val tokenStartIndex: Int,
    val tokenEndIndex: Int
)

/**
 * 补全运算计算结果
 *
 * @param parsedContext 解析出的上下文元数据
 * @param candidates 匹配过滤后的候选列表
 * @param longestCommonPrefix 所有候选的最长公共前缀（未转义格式）
 * @param isUniqueMatch 是否仅有唯一一个匹配项
 */
data class CompletionResult(
    val parsedContext: ParsedContext,
    val candidates: List<CompletionCandidate>,
    val longestCommonPrefix: String,
    val isUniqueMatch: Boolean
)

/**
 * 终端智能自动补全核心计算引擎
 *
 * 遵循高内聚、低耦合原则，全量逻辑由无状态纯函数构成，便于全面进行单元测试与跨组件复用。
 */
object CompletionEngine {

    /**
     * 计算一组字符串的最长公共前缀 (Longest Common Prefix, LCP)
     *
     * @param strings 输入的候选字符串集合
     * @param ignoreCase 是否在匹配时忽略大小写（提取出的前缀保留首个元素的原始大小写）
     * @return 最长公共前缀；若集合为空或无公共字符则返回空字符串
     */
    fun longestCommonPrefix(strings: List<String>, ignoreCase: Boolean = true): String {
        if (strings.isEmpty()) return ""
        if (strings.size == 1) return strings[0]

        val first = strings[0]
        var prefixLength = 0
        for (i in first.indices) {
            val ch = first[i]
            val allMatch = strings.drop(1).all { str ->
                i < str.length && str[i].equals(ch, ignoreCase = ignoreCase)
            }
            if (allMatch) {
                prefixLength++
            } else {
                break
            }
        }
        return first.substring(0, prefixLength)
    }

    /**
     * 路径转义函数：对包含空格等特殊字符的文件/文件夹名称进行反斜杠转义，并追加标准结尾符号
     *
     * @param name 原始文件名或目录名
     * @param isDirectory 是否为文件夹（文件夹结尾追加 '/'，普通文件结尾追加空格 ' '）
     * @param isPartial 是否仅为部分公共前缀补全（为 true 时不追加结尾 '/' 或空格，避免提前闭合 Token）
     */
    fun escapePath(name: String, isDirectory: Boolean, isPartial: Boolean = false): String {
        val escaped = name.replace(" ", "\\ ")
        if (isPartial) {
            return escaped
        }
        val suffix = if (isDirectory) {
            if (escaped.endsWith("/")) "" else "/"
        } else {
            " "
        }
        return escaped + suffix
    }

    /**
     * 反转义路径函数：将 "My\\ Documents/file" 还原为 "My Documents/file"
     */
    fun unescapePath(token: String): String {
        return token.replace("\\ ", " ")
    }

    /**
     * 解析当前输入在光标位置的补全上下文
     *
     * @param input 终端整行输入文本
     * @param cursorPosition 当前输入光标所在索引位置 (0..input.length)
     */
    fun parseContext(input: String, cursorPosition: Int): ParsedContext {
        val safeCursor = cursorPosition.coerceIn(0, input.length)
        val textBeforeCursor = input.substring(0, safeCursor)

        // 1. 若存在管道符 '|'，以最后一个管道符作为当前命令段的起始点
        val pipeIndex = textBeforeCursor.lastIndexOf('|')
        val segmentStartIndex = if (pipeIndex != -1) pipeIndex + 1 else 0
        val segment = textBeforeCursor.substring(segmentStartIndex)

        // 2. 寻找当前正在键入的 Token 的起始位置（支持 '\ ' 转义空格）
        var tokenStartInSegment = segment.length
        while (tokenStartInSegment > 0) {
            val prevChar = segment[tokenStartInSegment - 1]
            if (prevChar.isWhitespace()) {
                // 计算该空白字符前连续的反斜杠数量（奇数个说明空格被反斜杠转义，偶数个说明反斜杠自身被转义，空格是分隔符）
                var backslashCount = 0
                var checkPos = tokenStartInSegment - 2
                while (checkPos >= 0 && segment[checkPos] == '\\') {
                    backslashCount++
                    checkPos--
                }
                val isEscaped = (backslashCount % 2 != 0)
                if (!isEscaped) {
                    break
                }
            }
            tokenStartInSegment--
        }

        val rawToken = segment.substring(tokenStartInSegment)
        val tokenGlobalStart = segmentStartIndex + tokenStartInSegment

        // 3. 提取命令名称（当前管道段中第一个未转义的单词）
        val trimmedSegment = segment.trimStart()
        val commandWord = trimmedSegment.substringBefore(' ')
        val isFirstToken = (segmentStartIndex + (segment.length - trimmedSegment.length) == tokenGlobalStart)

        // 4. 判断上下文类型与前缀切分
        if (isFirstToken) {
            return ParsedContext(
                contextType = CompletionContextType.COMMAND,
                commandName = "",
                rawToken = rawToken,
                parentPath = "",
                prefix = rawToken,
                tokenStartIndex = tokenGlobalStart,
                tokenEndIndex = safeCursor
            )
        }

        // 非第一个单词，当前处于参数上下文
        if (rawToken.startsWith("-") && !rawToken.contains("/")) {
            return ParsedContext(
                contextType = CompletionContextType.FLAG,
                commandName = commandWord,
                rawToken = rawToken,
                parentPath = "",
                prefix = rawToken,
                tokenStartIndex = tokenGlobalStart,
                tokenEndIndex = safeCursor
            )
        }

        // 路径上下文：切分 parentPath 与前缀 prefix
        val unescapedToken = unescapePath(rawToken)
        val lastSlashIndex = unescapedToken.lastIndexOf('/')
        val parentPath = if (lastSlashIndex != -1) {
            unescapedToken.substring(0, lastSlashIndex + 1)
        } else {
            ""
        }
        val prefix = if (lastSlashIndex != -1) {
            unescapedToken.substring(lastSlashIndex + 1)
        } else {
            unescapedToken
        }

        return ParsedContext(
            contextType = CompletionContextType.PATH,
            commandName = commandWord,
            rawToken = rawToken,
            parentPath = parentPath,
            prefix = prefix,
            tokenStartIndex = tokenGlobalStart,
            tokenEndIndex = safeCursor
        )
    }

    /**
     * 基于上下文与候选源生成补全结果
     *
     * @param parsedContext 上下文解析结果
     * @param registeredCommands 系统已注册的所有命令名称列表
     * @param commandFlagsMap 每个命令对应的所有参数列表（如 "ls" -> ["-l", "-a", "-t", "-S", "-u", "-X", "-r"]）
     * @param directoryFiles 当前作用目录下的 FileBean 文件列表
     */
    fun calculateCompletion(
        parsedContext: ParsedContext,
        registeredCommands: List<String>,
        commandFlagsMap: Map<String, List<CommandFlag>>,
        directoryFiles: List<FileBean>
    ): CompletionResult {
        val candidates = mutableListOf<CompletionCandidate>()

        when (parsedContext.contextType) {
            CompletionContextType.COMMAND -> {
                val prefix = parsedContext.prefix
                registeredCommands
                    .filter { it.startsWith(prefix, ignoreCase = true) }
                    .sorted()
                    .forEach { cmd ->
                        candidates.add(
                            CompletionCandidate(
                                name = cmd,
                                displayText = cmd,
                                insertText = "$cmd ",
                                type = CandidateType.COMMAND,
                                isDirectory = false
                            )
                        )
                    }
            }

            CompletionContextType.FLAG -> {
                val flags = commandFlagsMap[parsedContext.commandName] ?: emptyList()
                val prefix = parsedContext.prefix
                flags
                    .filter { it.optionName.startsWith(prefix, ignoreCase = true) }
                    .distinctBy { it.optionName }
                    .sortedBy { it.optionName }
                    .forEach { flag ->
                        candidates.add(
                            CompletionCandidate(
                                name = flag.optionName,
                                displayText = flag.name,
                                insertText = "${flag.optionName} ",
                                type = CandidateType.FLAG,
                                isDirectory = false
                            )
                        )
                    }
            }

            CompletionContextType.PATH -> {
                val prefix = parsedContext.prefix
                val isCd = parsedContext.commandName == "cd"

                directoryFiles
                    .filter { file ->
                        // 1. 如果是 cd，自动过滤所有非文件夹
                        if (isCd && !file.isFolder) {
                            return@filter false
                        }
                        // 2. 前缀过滤（忽略大小写）
                        file.name.startsWith(prefix, ignoreCase = true)
                    }
                    .forEach { file ->
                        val escapedName = escapePath(file.name, file.isFolder)
                        candidates.add(
                            CompletionCandidate(
                                name = file.name,
                                displayText = if (file.isFolder) "${file.name}/" else file.name,
                                insertText = escapedName,
                                type = if (file.isFolder) CandidateType.DIRECTORY else CandidateType.FILE,
                                isDirectory = file.isFolder
                            )
                        )
                    }
            }
        }

        // 计算所有匹配候选名称的最长公共前缀
        val candidateNames = candidates.map { it.name }
        val lcp = longestCommonPrefix(candidateNames, ignoreCase = true)

        return CompletionResult(
            parsedContext = parsedContext,
            candidates = candidates,
            longestCommonPrefix = lcp,
            isUniqueMatch = candidates.size == 1
        )
    }

    /**
     * 将候选替换结果应用到原始整行输入中，生成全新的 TextFieldValue
     *
     * @param originalText 原始输入的整行文本
     * @param parsedContext 上下文信息
     * @param candidateToInsert 需要插入的候选（或最长公共前缀）
     * @param isDirectory 是否为文件夹
     * @param isPartial 是否仅为部分公共前缀补全（为 true 时不追加结尾 '/' 或空格，避免提前闭合 Token）
     * @return Pair(全新替换后的字符串, 新的光标索引位置)
     */
    fun applyCandidate(
        originalText: String,
        parsedContext: ParsedContext,
        candidateToInsert: String,
        isDirectory: Boolean = false,
        isPartial: Boolean = false
    ): Pair<String, Int> {
        val safeStart = parsedContext.tokenStartIndex.coerceIn(0, originalText.length)
        val safeEnd = parsedContext.tokenEndIndex.coerceIn(safeStart, originalText.length)
        val prefixBeforeToken = originalText.substring(0, safeStart)
        val textAfterCursor = originalText.substring(safeEnd)

        val fullReplacement = when (parsedContext.contextType) {
            CompletionContextType.PATH -> {
                // 路径补全需拼接 parentPath 的转义形式 + 候选的转义形式
                val escapedParent = escapeParentPath(parsedContext.parentPath)
                val escapedName = escapePath(candidateToInsert, isDirectory = isDirectory, isPartial = isPartial)
                escapedParent + escapedName
            }
            CompletionContextType.COMMAND -> {
                if (isPartial) candidateToInsert else if (candidateToInsert.endsWith(" ")) candidateToInsert else "$candidateToInsert "
            }
            CompletionContextType.FLAG -> {
                if (isPartial) candidateToInsert else if (candidateToInsert.endsWith(" ")) candidateToInsert else "$candidateToInsert "
            }
        }

        val newText = prefixBeforeToken + fullReplacement + textAfterCursor
        val newCursor = prefixBeforeToken.length + fullReplacement.length
        return Pair(newText, newCursor)
    }

    /**
     * 将 parentPath（如 "My Documents/sub/"）中的各个分段进行反斜杠转义
     */
    fun escapeParentPath(parentPath: String): String {
        if (parentPath.isEmpty()) return ""
        val segments = parentPath.split("/")
        return segments.mapIndexed { index, seg ->
            if (index == segments.size - 1 && seg.isEmpty()) {
                "" // 最后一个斜杠后的空串
            } else {
                seg.replace(" ", "\\ ")
            }
        }.joinToString("/")
    }
}
