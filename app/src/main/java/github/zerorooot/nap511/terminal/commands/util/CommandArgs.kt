package github.zerorooot.nap511.terminal.commands.util

/**
 * 命令行参数解析工具类
 *
 * 封装命令行参数的提取、判断与转换逻辑，统一处理标志位（Flag）、带值选项（Option）以及位置参数（Positional Argument）。
 * 避免各命令重复手写数组索引与越界检查逻辑，提升代码复用性与安全性。
 *
 * @property rawArgs 原始命令行参数列表
 */
class CommandArgs(val rawArgs: List<String>) {

    /**
     * 首个选项结束符 "--" 的索引位置，若不存在则为 -1
     */
    val delimiterIndex: Int = rawArgs.indexOf("--")

    /**
     * 允许包含选项/标志的参数切片（仅取 "--" 之前的参数；若无 "--" 则为全量参数）
     */
    val optionTokens: List<String> = if (delimiterIndex >= 0) {
        rawArgs.subList(0, delimiterIndex)
    } else {
        rawArgs
    }

    /**
     * 是否包含选项结束符 "--"
     */
    val hasDelimiter: Boolean
        get() = delimiterIndex >= 0

    /**
     * 判断参数列表中是否包含指定的标志位（Flag）
     *
     * 仅在选项结束符 "--" 之前进行匹配。
     * 例如：args.hasFlag("-l", "-a")
     *
     * @param flags 需要检测的标志字符串集合
     * @return 只要包含其中任意一个标志即返回 true
     */
    fun hasFlag(vararg flags: String): Boolean {
        return optionTokens.any { it in flags }
    }

    /**
     * 判断是否包含任意复合开关参数（例如检查 rm 是否免确认："-y", "-f", "-rf", "-fr"）
     *
     * 仅在选项结束符 "--" 之前进行匹配。
     *
     * @param flags 允许的标志列表
     * @return 若参数包含其中任意一个则返回 true
     */
    fun hasAny(vararg flags: String): Boolean {
        return flags.any { optionTokens.contains(it) }
    }

    /**
     * 获取指定选项的值（例如：-n 10、-suffix mp4、-I {}）
     *
     * 仅在选项结束符 "--" 之前进行匹配。
     * 支持两种常见形式：
     * 1. 分开书写："-n", "10" -> 返回 "10"
     * 2. 紧凑书写："-n10" -> 返回 "10"（仅对以特定单字符前缀紧贴有效）
     *
     * @param flag 选项标志名称，如 "-n"
     * @return 选项对应的字符串值；若不存在或无有效后续参数则返回 null
     */
    fun getOption(flag: String): String? {
        val index = optionTokens.indexOf(flag)
        if (index >= 0 && index + 1 < optionTokens.size) {
            return optionTokens[index + 1]
        }
        // 尝试紧贴形式（例如 -I{} 或 -n5）
        val prefixMatch = optionTokens.firstOrNull { it.startsWith(flag) && it.length > flag.length }
        if (prefixMatch != null) {
            return prefixMatch.substring(flag.length)
        }
        return null
    }

    /**
     * 获取指定选项的整型数值
     *
     * @param flag 选项标志名称
     * @param default 选项缺失或解析失败时的默认值
     * @return 解析后的整型数值
     */
    fun getIntOption(flag: String, default: Int? = null): Int? {
        val value = getOption(flag) ?: return default
        return value.toIntOrNull() ?: default
    }

    /**
     * 获取所有位置参数（Positional Arguments）
     *
     * 若包含选项结束符 "--"：
     * 1. "--" 之前所有不以 "-" 开头的参数作为位置参数；
     * 2. "--" 之后的所有参数均作为位置参数（即使以 "-" 开头）；
     * 3. 选项结束符 "--" 本身不计入位置参数中。
     * 若不包含 "--"：过滤所有不以 "-" 开头的参数。
     *
     * @return 位置参数列表
     */
    val positionalArgs: List<String> by lazy {
        if (delimiterIndex >= 0) {
            val before = rawArgs.subList(0, delimiterIndex).filter { !it.startsWith("-") }
            val after = rawArgs.subList(delimiterIndex + 1, rawArgs.size)
            before + after
        } else {
            rawArgs.filter { !it.startsWith("-") }
        }
    }

    /**
     * 获取第一个位置参数（如果存在）
     */
    val firstPositional: String?
        get() = positionalArgs.firstOrNull()

    /**
     * 获取所有位置参数拼接的文本（常用于 echo 等命令）
     */
    fun joinPositional(separator: String = " "): String {
        return positionalArgs.joinToString(separator)
    }

    /**
     * 原始参数是否为空
     */
    val isEmpty: Boolean
        get() = rawArgs.isEmpty()

    /**
     * 原始参数元素数量
     */
    val size: Int
        get() = rawArgs.size

    /**
     * 是否包含特定字面量参数
     */
    operator fun contains(element: String): Boolean = rawArgs.contains(element)

    /**
     * 索引访问
     */
    operator fun get(index: Int): String = rawArgs[index]
}
