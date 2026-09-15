package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

/**
 * 字幕/歌词格式转换与编码识别工具类
 * 负责自动识别各种编码（GBK/GB18030/UTF-8/UTF-16/BIG5），并统一转码为标准的 UTF-8 SRT 格式。
 */
object SubtitleConverter {

    private val ASS_STYLE_TAG_PATTERN = Pattern.compile("\\{.*?\\}")

    /**
     * 智能解码字节数组为 String，自动处理 BOM 并尝试 UTF-8、GB18030/GBK、BIG5 等多重编码，解决中文歌词/字幕乱码问题
     */
    fun decodeTextSmart(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // 1. 检查 BOM (Byte Order Mark)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }

        // 2. 尝试严谨的 UTF-8 校验解码（遇到非法字节时抛出异常）
        try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (_: Exception) {
            // UTF-8 校验失败，继续尝试 GBK/GB18030
        }

        // 3. 尝试 GB18030 / GBK 编码（覆盖绝大多数非 UTF-8 中文字幕和歌词）
        try {
            val gbCharset = Charset.forName("GB18030")
            val decoder = gbCharset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (_: Exception) {
        }

        // 4. 尝试 Big5 繁体中文
        try {
            val big5Charset = Charset.forName("BIG5")
            val decoder = big5Charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (_: Exception) {
        }

        // 5. 回退到 GB18030 解码
        return String(bytes, Charset.forName("GB18030"))
    }

    /**
     * 根据输入文件的扩展名判断是否需要转换，并统一转为标准 UTF-8 编码的 SRT 文件保存至输出文件
     * @param inputFile 原始下载的字幕/歌词文件
     * @param outputFile 转换后的目标 SRT 文件
     * @param ext 文件扩展名 (ass, ssa, sub, lrc, srt, vtt)
     * @return 最终标准 UTF-8 SRT File
     */
    fun convertToSrtIfNeeded(inputFile: File, outputFile: File, ext: String): File {
        val lowerExt = ext.lowercase(Locale.US)

        return try {
            val rawBytes = inputFile.readBytes()
            val content = decodeTextSmart(rawBytes)

            val srtText = when (lowerExt) {
                "ass", "ssa" -> convertAssToSrt(content)
                "sub" -> convertSubToSrt(content)
                "lrc" -> convertLrcToSrt(content)
                else -> content
            }

            if (srtText.isNotBlank()) {
                outputFile.writeText(srtText, Charsets.UTF_8)
                XLog.i("SubtitleConverter: 成功将 [$ext] 字幕处理并转码为 UTF-8 SRT: ${outputFile.absolutePath}")
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
     * 将 LRC 歌词文本转换为标准 SRT 文本
     */
    fun convertLrcToSrt(lrcContent: String): String {
        val lines = lrcContent.lines()
        val srtBuilder = StringBuilder()
        var count = 1

        val timePattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

        data class LrcItem(val timeMs: Long, val text: String)
        val items = mutableListOf<LrcItem>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matcher = timePattern.matcher(trimmed)
            val timeMatches = mutableListOf<Long>()
            var lastEnd = 0

            while (matcher.find()) {
                val min = matcher.group(1)?.toLong() ?: 0L
                val sec = matcher.group(2)?.toLong() ?: 0L
                var csStr = matcher.group(3) ?: "0"
                if (csStr.length == 1) csStr += "00"
                else if (csStr.length == 2) csStr += "0"
                else if (csStr.length > 3) csStr = csStr.substring(0, 3)
                val ms = csStr.toLong()

                val totalMs = (min * 60 + sec) * 1000 + ms
                timeMatches.add(totalMs)
                lastEnd = matcher.end()
            }

            if (timeMatches.isNotEmpty()) {
                val text = trimmed.substring(lastEnd).trim()
                if (text.isNotBlank()) {
                    for (timeMs in timeMatches) {
                        items.add(LrcItem(timeMs, text))
                    }
                }
            }
        }

        val sortedItems = items.sortedBy { it.timeMs }
        for (i in sortedItems.indices) {
            val item = sortedItems[i]
            val startMs = item.timeMs
            val endMs = if (i + 1 < sortedItems.size) {
                sortedItems[i + 1].timeMs.coerceAtMost(startMs + 10000L)
            } else {
                startMs + 5000L
            }

            srtBuilder.append(count++).append("\n")
            srtBuilder.append(formatMsToSrtTime(startMs)).append(" --> ").append(formatMsToSrtTime(endMs)).append("\n")
            srtBuilder.append(item.text).append("\n\n")
        }

        return srtBuilder.toString()
    }

    /**
     * 将 ASS/SSA 文本转换为标准 SRT 文本
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
                if (line.startsWith("Dialogue:", ignoreCase = true)) {
                    inEventsSection = true
                } else {
                    continue
                }
            }

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

    private fun formatMsToSrtTime(timeMs: Long): String {
        val totalSec = timeMs / 1000
        val millis = timeMs % 1000
        val seconds = totalSec % 60
        val totalMin = totalSec / 60
        val minutes = totalMin % 60
        val hours = totalMin / 60

        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun cleanAssText(text: String): String {
        var clean = ASS_STYLE_TAG_PATTERN.matcher(text).replaceAll("")
        clean = clean.replace("\\N", "\n").replace("\\n", "\n")
        return clean.trim()
    }
}
