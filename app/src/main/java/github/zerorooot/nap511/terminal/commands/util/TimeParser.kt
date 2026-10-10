package github.zerorooot.nap511.terminal.commands.util

/**
 * 视频/音频时长解析与比对工具
 *
 * 用于解析 find 命令中的 "-playlong +10m"、"-playlong -30s"、"-playlong +01:30:00" 等参数规范，
 * 并将其转换为秒数（Double）比对逻辑。
 */
object TimeParser {

    /**
     * 解析形如 "+10m"、"-30s"、"+01:30:00"、"-10:00"、"300" 的时长表达式
     *
     * @param raw 用户输入的时间描述字符串
     * @return 解析成功返回 ComparisonFilter<Double>，格式非法时返回 null
     */
    fun parse(raw: String): ComparisonFilter<Double>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val operator = when (trimmed.first()) {
            '+' -> '+'
            '-' -> '-'
            '=' -> '='
            else -> '='
        }

        val content = if (trimmed.first() == '+' || trimmed.first() == '-' || trimmed.first() == '=') {
            trimmed.substring(1).trim()
        } else {
            trimmed
        }
        if (content.isEmpty()) return null

        // 1. 判断是否包含冒号：时分秒时间戳格式 [HH:]mm:ss
        if (content.contains(':')) {
            val parts = content.split(':')
            val seconds = when (parts.size) {
                2 -> {
                    val m = parts[0].toDoubleOrNull() ?: return null
                    val s = parts[1].toDoubleOrNull() ?: return null
                    if (m < 0.0 || s < 0.0) return null
                    m * 60.0 + s
                }
                3 -> {
                    val h = parts[0].toDoubleOrNull() ?: return null
                    val m = parts[1].toDoubleOrNull() ?: return null
                    val s = parts[2].toDoubleOrNull() ?: return null
                    if (h < 0.0 || m < 0.0 || s < 0.0) return null
                    h * 3600.0 + m * 60.0 + s
                }
                else -> return null
            }
            return ComparisonFilter(operator, seconds)
        }

        // 2. 单位后缀格式 [s|m|h] 或纯数字（秒）
        val lastChar = content.last()
        val (multiplier, numStr) = if (lastChar.isLetter()) {
            val mult = when (lastChar.lowercaseChar()) {
                's' -> 1.0
                'm' -> 60.0
                'h' -> 3600.0
                else -> return null
            }
            mult to content.dropLast(1).trim()
        } else {
            1.0 to content
        }

        val num = numStr.toDoubleOrNull() ?: return null
        if (num < 0.0) return null

        return ComparisonFilter(operator, num * multiplier)
    }

    /**
     * 判断实际媒体时长（秒）是否满足过滤条件
     */
    fun matches(durationSeconds: Double, filter: ComparisonFilter<Double>): Boolean {
        return filter.matches(durationSeconds)
    }
}
