package github.zerorooot.nap511.terminal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 终端输出文本解析与多样式渲染转换器
 * 支持 ANSI 转义字符解析、ls -l 列表多列高亮、find/tree 检索高亮及普通文件名着色
 */
object TerminalStyleParser {

    private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*[a-zA-Z]")
    private val LONG_LISTING_REGEX = Regex("^([d\\-]r[w-][x-][r-][w-][x-][r-][w-][x-])\\s+(.+?)\\s+(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})\\s+(.+)$")
    private val FIND_CATEGORY_REGEX = Regex("^\\[(目录|文件)\\]\\s+(.+?)\\s*(\\([^)]*\\))?$")

    /**
     * 统一入口：根据文本内容智能组装带样式的 AnnotatedString
     */
    fun parseOutputLine(
        lineText: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        if (lineText.contains('\n')) {
            val lines = lineText.split('\n')
            return buildAnnotatedString {
                lines.forEachIndexed { index, line ->
                    append(parseOutputLine(line, theme))
                    if (index < lines.size - 1) {
                        append("\n")
                    }
                }
            }
        }

        return when {
            // 1. 包含 ANSI 转义序列
            lineText.contains("\u001B[") -> {
                parseAnsiText(lineText, theme)
            }
            // 2. 符合 ls -l 详细列表格式
            LONG_LISTING_REGEX.matches(lineText.trim()) -> {
                parseLongListingLine(lineText, theme)
            }
            // 3. 符合 find 分类输出格式 (如 [目录] xxx  (-) 或 [文件] xxx  (10 MB))
            FIND_CATEGORY_REGEX.matches(lineText.trim()) -> {
                parseFindCategoryLine(lineText, theme)
            }
            // 4. 普通输出行 (路径、简单文件名等)
            else -> {
                parseSimpleOutputLine(lineText, theme)
            }
        }
    }

    /**
     * 解析包含 ANSI SGR 颜色/样式转义序列的文本
     */
    fun parseAnsiText(
        text: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            var currentStyle = theme.otherStyle

            val matches = ANSI_REGEX.findAll(text)
            for (match in matches) {
                if (match.range.first > currentIndex) {
                    val rawSubstring = text.substring(currentIndex, match.range.first)
                    withStyle(currentStyle) {
                        append(rawSubstring)
                    }
                }

                // 解析 SGR 指令 (如 \u001b[31m, \u001b[1;36m, \u001b[0m)
                val seq = match.value.removePrefix("\u001B[").removeSuffix("m").removeSuffix("M")
                val codes = seq.split(";").mapNotNull { it.toIntOrNull() }

                currentStyle = if (codes.isEmpty() || codes.contains(0)) {
                    theme.otherStyle
                } else {
                    var color = currentStyle.color
                    var fontWeight = currentStyle.fontWeight

                    for (code in codes) {
                        when (code) {
                            1 -> fontWeight = FontWeight.Bold
                            22 -> fontWeight = FontWeight.Normal
                            30 -> color = Color(0xFF212121) // Black
                            31 -> color = Color(0xFFEF5350) // Red
                            32 -> color = Color(0xFF66BB6A) // Green
                            33 -> color = Color(0xFFFFA726) // Yellow
                            34 -> color = Color(0xFF42A5F5) // Blue
                            35 -> color = Color(0xFFAB47BC) // Magenta
                            36 -> color = Color(0xFF26A69A) // Cyan
                            37 -> color = Color(0xFFECEFF1) // White
                            39 -> color = theme.otherStyle.color // Default fg
                            90 -> color = Color(0xFF78909C) // Bright Black/Gray
                            91 -> color = Color(0xFFFF7043) // Bright Red
                            92 -> color = Color(0xFFB2FF59) // Bright Green
                            93 -> color = Color(0xFFFFD54F) // Bright Yellow
                            94 -> color = Color(0xFF90CAF9) // Bright Blue
                            95 -> color = Color(0xFFE040FB) // Bright Magenta
                            96 -> color = Color(0xFF4DD0E1) // Bright Cyan
                            97 -> color = Color(0xFFFFFFFF) // Bright White
                        }
                    }
                    SpanStyle(color = color, fontWeight = fontWeight)
                }

                currentIndex = match.range.last + 1
            }

            if (currentIndex < text.length) {
                withStyle(currentStyle) {
                    append(text.substring(currentIndex))
                }
            }
        }
    }

    /**
     * 智能格式化 ls -l 详细列表（权限、大小、时间采用元数据淡色，文件名按类型高亮）
     */
    fun parseLongListingLine(
        lineText: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        val trimmed = lineText.trim()
        val match = LONG_LISTING_REGEX.find(trimmed) ?: return AnnotatedString(lineText)

        val permGroup = match.groupValues[1]
        val nameGroup = match.groupValues[4]
        val isDir = permGroup.startsWith("d") || nameGroup.endsWith("/")

        val nameStart = match.groups[4]?.range?.first ?: trimmed.lastIndexOf(nameGroup)

        return buildAnnotatedString {
            // 前半部分 (权限、大小、修改时间等元数据)
            withStyle(theme.metadataStyle) {
                append(trimmed.substring(0, nameStart))
            }
            // 文件名/目录名按对应图标色彩渲染
            val nameStyle = theme.getStyleForFilename(nameGroup, isDir)
            withStyle(nameStyle) {
                append(nameGroup)
            }
        }
    }

    /**
     * 格式化 find 分类筛选行：如 [目录] Movies  (-) 或 [文件] song.mp3  (3.5 MB)
     */
    private fun parseFindCategoryLine(
        lineText: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        val trimmed = lineText.trim()
        val match = FIND_CATEGORY_REGEX.find(trimmed) ?: return AnnotatedString(lineText)

        val isDirTag = match.groupValues[1] == "目录"
        val nameGroup = match.groupValues[2]      // 文件名
        val extraGroup = match.groupValues[3]     // (大小信息)

        val nameStart = match.groups[2]?.range?.first ?: 0

        return buildAnnotatedString {
            // [目录] / [文件] 标签采用淡灰蓝元数据色彩
            withStyle(theme.metadataStyle) {
                append(trimmed.substring(0, nameStart))
            }
            // 文件名按主题渲染
            val nameStyle = theme.getStyleForFilename(nameGroup, isDirTag)
            withStyle(nameStyle) {
                append(nameGroup)
            }
            // 附加信息 (大小等)
            if (extraGroup.isNotEmpty()) {
                withStyle(theme.metadataStyle) {
                    append(trimmed.substring(nameStart + nameGroup.length))
                }
            }
        }
    }

    /**
     * 格式化简单文件名或路径输出
     */
    fun parseSimpleOutputLine(
        lineText: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        val trimmed = lineText.trim()
        if (trimmed.isEmpty()) return AnnotatedString(lineText)

        // 判断是否属于多级路径字符串 (如 /a/b/c/ 或 /a/b/c.mp4)
        // 排除非文件路径场景：斜线周围包含空格 (如 " / "、" /"、"/ ")，或包含命令说明分隔符 " : "
        val hasPathSlash = trimmed.trimEnd('/').contains('/')
                && !trimmed.contains(" /")
                && !trimmed.contains("/ ")
                && !trimmed.contains(" : ")

        if (hasPathSlash) {
            val isDir = trimmed.endsWith("/")
            val fileName = trimmed.trimEnd('/').substringAfterLast('/') + if (isDir) "/" else ""
            val prefix = trimmed.substring(0, trimmed.length - fileName.length)

            val style = theme.getStyleForFilename(fileName, isDir)
            return buildAnnotatedString {
                if (prefix.isNotEmpty()) {
                    withStyle(theme.metadataStyle) {
                        append(prefix)
                    }
                }
                if (fileName.isNotEmpty()) {
                    withStyle(style) {
                        append(fileName)
                    }
                }
            }
        }

        // 单层文件或目录名 (如 Documents/ 或 video.mp4)
        val isDir = trimmed.endsWith("/")
        val style = theme.getStyleForFilename(trimmed, isDir)
        return buildAnnotatedString {
            withStyle(style) {
                append(lineText)
            }
        }
    }
}
