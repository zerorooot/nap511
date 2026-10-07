package github.zerorooot.nap511.terminal.engine

import java.util.regex.Pattern

/**
 * 通配符 (Glob) 匹配与自动展开工具类
 *
 * 支持:
 * - `*` 匹配 0 个或多个任意非路径分隔符字符
 * - `?` 匹配单个任意非路径分隔符字符
 */
object GlobMatcher {

    /**
     * 判断字符串中是否包含未转义的通配符字符
     */
    fun hasGlobWildcards(text: String): Boolean {
        return text.contains('*') || text.contains('?')
    }

    /**
     * 将 Glob 模式转换为正则表达式
     */
    fun globToRegex(glob: String, ignoreCase: Boolean = false): Regex {
        val pattern = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> pattern.append(".*")
                '?' -> pattern.append(".")
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '[', ']', '{', '}' -> {
                    pattern.append("\\").append(c)
                }
                '\\' -> {
                    if (i + 1 < glob.length) {
                        pattern.append("\\").append(glob[i + 1])
                        i++
                    } else {
                        pattern.append("\\\\")
                    }
                }
                else -> pattern.append(c)
            }
            i++
        }
        pattern.append("$")
        return if (ignoreCase) {
            Regex(pattern.toString(), RegexOption.IGNORE_CASE)
        } else {
            Regex(pattern.toString())
        }
    }

    /**
     * 判断指定名称是否匹配该 Glob 模式
     */
    fun matches(glob: String, target: String, ignoreCase: Boolean = false): Boolean {
        if (!hasGlobWildcards(glob)) {
            return glob.equals(target, ignoreCase = ignoreCase)
        }
        return globToRegex(glob, ignoreCase).matches(target)
    }

    /**
     * 在候选列表中展开通配符
     * @param globPattern 待匹配的通配符模式（如 "*.mp4"）
     * @param candidates 候选名称列表（如当前目录下的所有文件名）
     * @return 匹配的文件名列表（已排序）；若无任何项匹配，则返回包含原模式的单元素列表
     */
    fun expand(globPattern: String, candidates: List<String>, ignoreCase: Boolean = false): List<String> {
        if (!hasGlobWildcards(globPattern)) {
            return listOf(globPattern)
        }
        val regex = globToRegex(globPattern, ignoreCase)
        val matched = candidates.filter { regex.matches(it) }.sorted()
        return matched.ifEmpty { listOf(globPattern) }
    }
}
