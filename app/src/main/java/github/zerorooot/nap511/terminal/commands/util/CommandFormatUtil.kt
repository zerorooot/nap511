package github.zerorooot.nap511.terminal.commands.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 终端命令输出格式化工具类
 *
 * 提供时间戳、表格列等通用的格式化工具方法，避免在各个命令内部重复定义格式化逻辑。
 */
object CommandFormatUtil {

    private const val DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm"

    /**
     * 将 115 网盘返回的秒级或毫秒级时间戳字符串转换为人类可读的日期格式
     *
     * @param timeStr 时间戳字符串（可能是秒级 10 位或毫秒级 13 位）
     * @param pattern 日期时间展示格式模板（默认 "yyyy-MM-dd HH:mm"）
     * @param locale 区域设置（默认系统当前 Locale）
     * @return 格式化后的时间字符串；若输入非有效数字则原样返回
     */
    fun formatTimestamp(
        timeStr: String,
        pattern: String = DEFAULT_DATE_FORMAT,
        locale: Locale = Locale.getDefault()
    ): String {
        val timestamp = timeStr.toLongOrNull() ?: return timeStr
        // 兼容处理：小于 100 亿（10000000000L）通常为秒级时间戳，需换算为毫秒
        val millis = if (timestamp < 10_000_000_000L) timestamp * 1000 else timestamp
        val dateFormat = SimpleDateFormat(pattern, locale)
        return dateFormat.format(Date(millis))
    }
}
