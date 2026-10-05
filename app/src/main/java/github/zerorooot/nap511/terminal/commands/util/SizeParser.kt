package github.zerorooot.nap511.terminal.commands.util

/**
 * 文件大小比对过滤条件
 *
 * @property operator 比较运算符：'+'（大于）、'-'（小于）、'='（等于）
 * @property targetBytes 目标字节数阈值
 */
data class SizeFilter(
    val operator: Char,
    val targetBytes: Long
)

/**
 * 文件大小解析与比对工具
 *
 * 用于解析类似于 find 命令中的 "-size +100M"、"-size -10k" 等参数规范，
 * 并将其转换为字节数比对逻辑。
 */
object SizeParser {

    /**
     * 解析形如 "+100M"、"-10k"、"500b" 的大小表达式
     *
     * @param raw 用户输入的原始大小描述字符串
     * @return 解析成功返回 SizeFilter 对象，表达式非法时返回 null
     */
    fun parse(raw: String): SizeFilter? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val operator = when (trimmed.first()) {
            '+' -> '+'
            '-' -> '-'
            else -> '='
        }

        val numberAndUnit = if (trimmed.first() == '+' || trimmed.first() == '-') {
            trimmed.substring(1)
        } else {
            trimmed
        }
        if (numberAndUnit.isEmpty()) return null

        val lastChar = numberAndUnit.last()
        val (multiplier, numStr) = if (lastChar.isLetter()) {
            val mult = when (lastChar.lowercaseChar()) {
                'k' -> 1024L
                'm' -> 1024L * 1024L
                'g' -> 1024L * 1024L * 1024L
                'b', 'c' -> 1L
                else -> return null
            }
            mult to numberAndUnit.dropLast(1)
        } else {
            1L to numberAndUnit
        }

        val num = numStr.toLongOrNull() ?: return null
        return SizeFilter(operator, num * multiplier)
    }

    /**
     * 判断指定文件的字节大小是否满足过滤条件
     *
     * @param fileSize 文件实际字节数
     * @param filter 已解析的大小过滤条件
     * @return 满足条件返回 true，否则返回 false
     */
    fun matches(fileSize: Long, filter: SizeFilter): Boolean {
        return when (filter.operator) {
            '+' -> fileSize > filter.targetBytes
            '-' -> fileSize < filter.targetBytes
            '=' -> fileSize == filter.targetBytes
            else -> false
        }
    }
}
