package github.zerorooot.nap511.terminal.engine

/**
 * 通配符 (Glob) 匹配与自动展开工具类
 *
 * 遵循 POSIX.1-2017 (2.13.3 Pattern Matching Notation) 规范：
 * 1. 前导点（Dotfiles）隔离保护：若 pattern 首字符不是点（如 `*` 或 `*.txt`），绝对禁止匹配以点开头的隐藏文件；
 * 2. 显式点前缀排除 `.` 和 `..`：若 pattern 显式以点开头（如 `.*`），允许匹配隐藏文件，但严格排除 `.` 和 `..`；
 * 3. 字符级掩码（quoteMask）支持：仅未加引号且未转义的 `*` 和 `?` 被视为通配符，受保护的字符全部转义为字面量；
 * 4. 路径隔离：`*` 与 `?` 严格不跨目录（`[^/]`），多层级路径通配由阶梯展开器（MultiLevelGlobExpander）逐级下潜；
 * 5. Nomatch Fallback：无匹配项时严格保留原模式字面量。
 */
object GlobMatcher {

    private val REGEX_SPECIAL_CHARS = setOf(
        '.', '(', ')', '+', '|', '^', '$', '@', '%', '[', ']', '{', '}'
    )

    /**
     * 判断字符串中是否包含未被引号或转义保护的有效通配符 ('*' 或 '?')
     *
     * @param text 待检测文本
     * @param quoteMask 字符级受保护掩码（默认全 false，即全未受保护）
     */
    fun hasGlobWildcards(
        text: String,
        quoteMask: BooleanArray = BooleanArray(text.length) { false }
    ): Boolean {
        return text.indices.any { idx ->
            val c = text[idx]
            (c == '*' || c == '?') && !quoteMask.getOrElse(idx) { false }
        }
    }

    /**
     * 将 Glob 模式转换为正则表达式
     *
     * @param glob 原始通配符模式
     * @param quoteMask 字符级受保护掩码（true 表示该位置受引号或反斜杠保护）
     * @param ignoreCase 是否忽略大小写
     */
    fun globToRegex(
        glob: String,
        quoteMask: BooleanArray = BooleanArray(glob.length) { false },
        ignoreCase: Boolean = false
    ): Regex {
        val isPrefixedWithDot = glob.startsWith(".")
        val sb = StringBuilder("^")

        // 1. POSIX 2.13.3 前导点隔离：模式不以点开头时，使用否定前瞻禁止匹配前导点
        if (!isPrefixedWithDot) {
            sb.append("(?![.])")
        } else if (glob != "." && glob != "..") {
            // 模式显式以点开头时，排除 "." 和 ".." 自身
            sb.append("(?!\\.{1,2}$)")
        }

        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            val isQuoted = quoteMask.getOrElse(i) { false }

            if (!isQuoted && c == '*') {
                sb.append("[^/]*")
            } else if (!isQuoted && c == '?') {
                sb.append("[^/]")
            } else if (c in REGEX_SPECIAL_CHARS) {
                sb.append("\\").append(c)
            } else if (c == '\\') {
                sb.append("\\\\")
            } else {
                sb.append(c)
            }
            i++
        }
        sb.append("$")

        return if (ignoreCase) {
            Regex(sb.toString(), RegexOption.IGNORE_CASE)
        } else {
            Regex(sb.toString())
        }
    }

    /**
     * 判断指定目标名称是否匹配该 Glob 模式
     */
    fun matches(
        glob: String,
        target: String,
        ignoreCase: Boolean = false
    ): Boolean = matches(glob, BooleanArray(glob.length) { false }, target, ignoreCase)

    /**
     * 判断指定目标名称是否匹配该 Glob 模式（支持 quoteMask 掩码）
     */
    fun matches(
        glob: String,
        quoteMask: BooleanArray,
        target: String,
        ignoreCase: Boolean = false
    ): Boolean {
        if (!hasGlobWildcards(glob, quoteMask)) {
            return glob.equals(target, ignoreCase = ignoreCase)
        }
        return globToRegex(glob, quoteMask, ignoreCase).matches(target)
    }

    /**
     * 在候选列表中展开通配符
     *
     * @param globPattern 待匹配的通配符模式（如 "*.mp4"）
     * @param candidates 候选名称列表（如当前目录下的所有文件名）
     * @param ignoreCase 是否忽略大小写
     * @return 匹配的文件名列表（已排序）；若无任何项匹配，则遵循 POSIX 规则返回包含原模式的单元素列表
     */
    fun expand(
        globPattern: String,
        candidates: List<String>,
        ignoreCase: Boolean = false
    ): List<String> = expand(globPattern, BooleanArray(globPattern.length) { false }, candidates, ignoreCase)

    /**
     * 在候选列表中展开通配符（支持 quoteMask 掩码）
     */
    fun expand(
        globPattern: String,
        quoteMask: BooleanArray,
        candidates: List<String>,
        ignoreCase: Boolean = false
    ): List<String> {
        if (!hasGlobWildcards(globPattern, quoteMask)) {
            return listOf(globPattern)
        }
        val regex = globToRegex(globPattern, quoteMask, ignoreCase)
        val matched = candidates.filter { regex.matches(it) }.sorted()
        return matched.ifEmpty { listOf(globPattern) }
    }
}
