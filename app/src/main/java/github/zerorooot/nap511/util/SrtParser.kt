package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import java.io.File
import java.util.regex.Pattern

data class SubtitleEntry(
    val index: Int = 0,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val text: String = ""
)

/**
 * SRT 标准字幕解析工具
 */
object SrtParser {

    private val TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")

    /**
     * 解析本地 SRT 文件为 SubtitleEntry 列表
     */
    fun parse(file: File): List<SubtitleEntry> {
        if (!file.exists() || !file.isFile) return emptyList()
        return try {
            val content = SubtitleConverter.decodeTextSmart(file.readBytes())
            parse(content)
        } catch (e: Exception) {
            XLog.e("SrtParser: 读取/解析字幕文件失败: ${file.absolutePath}", e)
            emptyList()
        }
    }

    /**
     * 解析 SRT 格式文本
     */
    fun parse(content: String): List<SubtitleEntry> {
        if (content.isBlank()) return emptyList()

        val entries = mutableListOf<SubtitleEntry>()
        // 按空行分割数据块
        val blocks = content.replace("\r\n", "\n").split(Regex("\n\\s*\n"))

        var entryIdx = 1
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue

            // 寻找包含 "-->" 的时间行
            var timeLineIdx = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timeLineIdx = i
                    break
                }
            }

            if (timeLineIdx == -1) continue

            val timeLine = lines[timeLineIdx]
            val timeParts = timeLine.split("-->")
            if (timeParts.size != 2) continue

            val startMs = parseTimeToMs(timeParts[0].trim())
            val endMs = parseTimeToMs(timeParts[1].trim())

            if (startMs < 0 || endMs < 0) continue

            // 时间行之后的所有非空行做为文本内容
            val textLines = lines.subList(timeLineIdx + 1, lines.size)
            val text = textLines.joinToString("\n")

            if (text.isNotBlank()) {
                entries.add(
                    SubtitleEntry(
                        index = entryIdx++,
                        startMs = startMs,
                        endMs = endMs,
                        text = text
                    )
                )
            }
        }

        return entries.sortedBy { it.startMs }
    }

    /**
     * 将 "00:01:23,450" 或 "0:01:23.450" 转化为毫秒数
     */
    fun parseTimeToMs(timeStr: String): Long {
        if (timeStr.isBlank()) return -1L
        val matcher = TIME_PATTERN.matcher(timeStr)
        if (!matcher.find()) return -1L

        return try {
            val hours = matcher.group(1)?.toLong() ?: 0L
            val minutes = matcher.group(2)?.toLong() ?: 0L
            val seconds = matcher.group(3)?.toLong() ?: 0L
            var millisStr = matcher.group(4) ?: "0"

            if (millisStr.length == 1) millisStr += "00"
            else if (millisStr.length == 2) millisStr += "0"
            else if (millisStr.length > 3) millisStr = millisStr.substring(0, 3)

            val millis = millisStr.toLong()

            (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
        } catch (e: Exception) {
            -1L
        }
    }
}
