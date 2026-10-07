package github.zerorooot.nap511.terminal.commands.util

import kotlin.math.abs

/**
 * 通用数值比较过滤器
 *
 * @param T 数值类型（如 Long 字节数，Double 秒数等）
 * @property operator 比较运算符：'+'（大于）、'-'（小于）、'='（等于）
 * @property target 目标阈值
 */
data class ComparisonFilter<T : Comparable<T>>(
    val operator: Char,
    val target: T
) {
    /**
     * 判断指定实际数值是否满足过滤条件
     */
    fun matches(actual: T): Boolean = when (operator) {
        '+' -> actual > target
        '-' -> actual < target
        '=' -> {
            if (target is Double && actual is Double) {
                abs(actual - target) < 1.0
            } else {
                actual == target
            }
        }
        else -> false
    }
}
