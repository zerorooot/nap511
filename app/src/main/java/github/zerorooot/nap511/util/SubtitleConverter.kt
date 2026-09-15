package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * 字幕格式转换工具类
 * GSYVideoPlayer (ExoPlayer) 原生仅完全支持 SRT / VTT 字幕格式。
 * 本工具类负责将 ASS / SSA / SUB 格式的字幕文件转换为标准的 SRT 格式。
 */
object SubtitleConverter {

    private val ASS_STYLE_TAG_PATTERN = Pattern.compile("\\{.*?\\}")

    /**
     * 根据输入文件的扩展名判断是否需要转换，并转为 SRT 文件保存至输出文件
     * @param inputFile 原始下载的字幕文件
     * @param outputFile 转换后的目标 SRT 文件
     * @param ext 文件扩展名 (ass, ssa, sub, srt, vtt)
     * @return 最终可供播放器使用的字幕文件 (如果无需转换则返回原文件)
     */
    fun convertToSrtIfNeeded(inputFile: File, outputFile: File, ext: String): File {
        val lowerExt = ext.lowercase(Locale.US)
        if (lowerExt == "srt" || lowerExt == "vtt") {
            return inputFile
        }

        return try {
            val content = inputFile.readText(Charsets.UTF_8)
            val srtText = when (lowerExt) {
                "ass", "ssa" -> convertAssToSrt(content)
                "sub" -> convertSubToSrt(content)
                else -> content
            }
            if (srtText.isNotBlank()) {
                outputFile.writeText(srtText, Charsets.UTF_8)
                XLog.i("SubtitleConverter: 成功将 [$ext] 字幕转换为 SRT: ${outputFile.absolutePath}")
                outputFile
            } else {
                XLog.w("SubtitleConverter: 转换输出文本为空，回退使用原文件")
                inputFile
            }
        } catch (e: Exception) {
            XLog.e("SubtitleConverter: 转换 [$ext] 字幕失败: ${e.message}", e)
            inputFile
        }
    }

    /**
     * 将 ASS/SSA 文本转换为标准 SRT 文本
     * ASS 格式行示例：
     * Dialogue: 0,0:01:23.45,0:01:25.67,Default,,0,0,0,,{\pos(100,200)}字幕内容\N第二行
     */
    fun convertAssToSrt(assContent: String): String {
        val lines = assContent.lines()
        val srtBuilder = StringBuilder()

        var startIndex = 1
        var endIndex = 2
        var textIndex = 9

        var inEventsSection = false
        var count = 1

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("[Events]", ignoreCase = true)) {
                inEventsSection = true
                continue
            }

            if (!inEventsSection) {
                // 部分格式可能没有标准 [Events] 标记，如果直接遇到 Dialogue，也开始处理
                if (line.startsWith("Dialogue:", ignoreCase = true)) {
                    inEventsSection = true
                } else {
                    continue
                }
            }

            // 解析 Format: 行确定 Start, End, Text 字段位置
            if (line.startsWith("Format:", ignoreCase = true)) {
                val formatParts = line.substring("Format:".length).split(",").map { it.trim().lowercase(Locale.US) }
                for ((idx, part) in formatParts.withIndex()) {
                    when (part) {
                        "start" -> startIndex = idx
                        "end" -> endIndex = idx
                        "text" -> textIndex = idx
                    }
                }
                continue
            }

            if (line.startsWith("Dialogue:", ignoreCase = true)) {
                val payload = line.substring("Dialogue:".length).trim()
                val parts = splitAssDialogue(payload, textIndex)
                if (parts.size > textIndex) {
                    val rawStart = parts.getOrNull(startIndex)?.trim() ?: ""
                    val rawEnd = parts.getOrNull(endIndex)?.trim() ?: ""
                    val rawText = parts.getOrNull(textIndex)?.trim() ?: ""

                    val startSrtTime = formatAssTimeToSrt(rawStart)
                    val endSrtTime = formatAssTimeToSrt(rawEnd)

                    // 清理 ASS 各种样式控制标签，例如 {\pos(200,300)}，并将 \N / \n 转换为换行符
                    val cleanedText = cleanAssText(rawText)

                    if (startSrtTime.isNotBlank() && endSrtTime.isNotBlank() && cleanedText.isNotBlank()) {
                        srtBuilder.append(count++).append("\n")
                        srtBuilder.append(startSrtTime).append(" --> ").append(endSrtTime).append("\n")
                        srtBuilder.append(cleanedText).append("\n\n")
                    }
                }
            }
        }

        return srtBuilder.toString()
    }

    /**
     * 将 MicroDVD .sub 格式转换成 SRT (默认帧率 25.0)
     * MicroDVD 格式示例: {100}{200}字幕文本|第二行文本
     */
    fun convertSubToSrt(subContent: String, fps: Double = 25.0): String {
        val lines = subContent.lines()
        val srtBuilder = StringBuilder()
        var count = 1

        val pattern = Pattern.compile("^\\{(\\d+)\\}\\{(\\d+)\\}(.*)$")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val startFrame = matcher.group(1)?.toLongOrNull() ?: continue
                val endFrame = matcher.group(2)?.toLongOrNull() ?: continue
                val rawText = matcher.group(3) ?: ""

                val startMs = (startFrame * 1000.0 / fps).toLong()
                val endMs = (endFrame * 1000.0 / fps).toLong()

                val startTimeStr = formatMsToSrtTime(startMs)
                val endTimeStr = formatMsToSrtTime(endMs)
                val cleanText = rawText.replace("|", "\n").trim()

                if (cleanText.isNotBlank()) {
                    srtBuilder.append(count++).append("\n")
                    srtBuilder.append(startTimeStr).append(" --> ").append(endTimeStr).append("\n")
                    srtBuilder.append(cleanText).append("\n\n")
                }
            }
        }

        return srtBuilder.toString()
    }

    /**
     * 将 ASS 逗号分隔的 Dialogue 行按最大项分割，保证最后一个字段为 Text
     */
    private fun splitAssDialogue(payload: String, textIndex: Int): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var commas = 0

        for (ch in payload) {
            if (ch == ',' && commas < textIndex) {
                result.add(current.toString())
                current = StringBuilder()
                commas++
            } else {
                current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * 将 ASS 时间格式 (H:MM:SS.cs，例如 0:01:23.45) 转换为 SRT 时间格式 (00:01:23,450)
     */
    private fun formatAssTimeToSrt(assTime: String): String {
        if (assTime.isBlank()) return ""
        val parts = assTime.split(":")
        if (parts.size != 3) return ""

        val hours = parts[0].toIntOrNull() ?: 0
        val minutes = parts[1].toIntOrNull() ?: 0

        val secAndCs = parts[2].split(".")
        if (secAndCs.isEmpty()) return ""

        val seconds = secAndCs[0].toIntOrNull() ?: 0
        var csStr = secAndCs.getOrNull(1) ?: "0"
        if (csStr.length == 1) csStr += "00"
        else if (csStr.length == 2) csStr += "0"
        else if (csStr.length > 3) csStr = csStr.substring(0, 3)

        val millis = csStr.toIntOrNull() ?: 0

        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    /**
     * 将毫秒时间戳转换为 SRT 时间格式 (00:01:23,450)
     */
    private fun formatMsToSrtTime(timeMs: Long): String {
        val totalSec = timeMs / 1000
        val millis = timeMs % 1000
        val seconds = totalSec % 60
        val totalMin = totalSec / 60
        val minutes = totalMin % 60
        val hours = totalMin / 60

        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    /**
     * 清理 ASS 内部样式与效果代码 (例如 {\pos(...)}、{\fad(...)}、\N)
     */
    private fun cleanAssText(text: String): String {
        var clean = ASS_STYLE_TAG_PATTERN.matcher(text).replaceAll("")
        clean = clean.replace("\\N", "\n").replace("\\n", "\n")
        return clean.trim()
    }
}
