package github.zerorooot.nap511.terminal.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType

/**
 * 终端输出文本样式装配转换器
 *
 * 遵循无状态纯函数设计，直接基于显式的 TerminalLineType 类型进行确定性样式组装，
 * 彻底消除基于正则表达式和启发式字符串猜词带来的误判与性能开销。
 */
object TerminalStyleParser {

    /**
     * ANSI SGR 颜色/控制字符匹配正则（用于标准终端彩色输出转义）
     */
    private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*[a-zA-Z]")

    /**
     * 核心统一入口：根据源头指定的 TerminalLineType 进行确定性样式装配
     * 绝不执行任何猜词或正则表达式匹配测试，实现零分支预测失误的极速渲染
     *
     * @param text 待渲染的文本内容
     * @param type 源头显式赋予的终端行语义类型
     * @param theme 终端配色主题
     */
    fun parseLine(
        text: String,
        type: TerminalLineType,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        if (text.contains('\n')) {
            val lines = text.split('\n')
            return buildAnnotatedString {
                lines.forEachIndexed { index, line ->
                    append(parseLine(line, type, theme))
                    if (index < lines.size - 1) {
                        append("\n")
                    }
                }
            }
        }

        return when (type) {
            TerminalLineType.OUTPUT_TEXT,
            TerminalLineType.SYSTEM,
            TerminalLineType.COMMAND,
            TerminalLineType.HELP,
            TerminalLineType.ERROR,
            TerminalLineType.PROMPT -> {
                // 普通纯文本与单色行：直接返回无附加 SpanStyle 的纯文本，零计算开销
                AnnotatedString(text)
            }

            TerminalLineType.OUTPUT_FILE_ENTRY -> {
                // 单个文件或目录条目（ls 紧凑列表）
                parseFileEntryLine(text, theme)
            }

            TerminalLineType.OUTPUT_PATH_ENTRY -> {
                // 完整多级路径条目（find 检索结果）
                parsePathEntryLine(text, theme)
            }

            TerminalLineType.OUTPUT_LONG_LISTING -> {
                // ls -l 详细列表行
                parseLongListingLine(text, theme)
            }

            TerminalLineType.OUTPUT_FIND_CATEGORY -> {
                // find 分类检索行
                parseFindCategoryLine(text, theme)
            }

            TerminalLineType.OUTPUT_ANSI -> {
                // 包含 ANSI 转义序列的文本
                parseAnsiText(text, theme)
            }

            @Suppress("DEPRECATION")
            TerminalLineType.OUTPUT -> {
                // 兼容旧枚举：若含 ANSI 则解析 ANSI，否则作为普通文本
                if (text.contains("\u001B[")) {
                    parseAnsiText(text, theme)
                } else {
                    AnnotatedString(text)
                }
            }
        }
    }

    /**
     * 格式化单个文件或文件夹条目（如 ls 简洁模式输出）
     * 直接通过文件后缀或末尾斜杠匹配应用内对应图标颜色
     */
    fun parseFileEntryLine(
        name: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AnnotatedString(name)

        val isDir = trimmed.endsWith("/")
        val style = theme.getStyleForFilename(trimmed, isDir)
        return buildAnnotatedString {
            withStyle(style) {
                append(name)
            }
        }
    }

    /**
     * 格式化已知的文件路径条目（如 find 递归检索输出）
     * 确定性切分：前缀路径淡化为元数据灰蓝色彩，末尾文件名按其类型高亮，彻底废除黑名单排除逻辑
     */
    fun parsePathEntryLine(
        path: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return AnnotatedString(path)

        val isDir = trimmed.endsWith("/")
        val clean = trimmed.trimEnd('/')
        val lastSlashIndex = clean.lastIndexOf('/')

        if (lastSlashIndex >= 0) {
            val prefix = trimmed.substring(0, lastSlashIndex + 1)
            val fileName = trimmed.substring(lastSlashIndex + 1)
            val nameStyle = theme.getStyleForFilename(fileName, isDir)

            return buildAnnotatedString {
                if (prefix.isNotEmpty()) {
                    withStyle(theme.metadataStyle) {
                        append(prefix)
                    }
                }
                if (fileName.isNotEmpty()) {
                    withStyle(nameStyle) {
                        append(fileName)
                    }
                }
            }
        } else {
            return parseFileEntryLine(trimmed, theme)
        }
    }

    /**
     * 格式化 ls -l 详细列表行
     * 前半部分权限、大小、日期等元数据统一使用淡色，末尾文件名按类型色彩高亮
     */
    fun parseLongListingLine(
        lineText: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        val trimmed = lineText.trim()
        if (trimmed.isEmpty()) return AnnotatedString(lineText)

        // ls -l 标准格式列：权限(10-11位) 大小 日期 时间 文件名
        // 通过寻找第四个空白字段边界确定性分离元数据与文件名，避免复杂全行正则回溯
        val parts = trimmed.split(Regex("\\s+"), limit = 4)
        if (parts.size < 4) {
            return AnnotatedString(lineText)
        }

        val perm = parts[0]
        val fileName = parts[3]
        val isDir = perm.startsWith("d") || fileName.endsWith("/")
        val metadataLength = trimmed.length - fileName.length

        return buildAnnotatedString {
            // 前半部元数据区（权限、大小、修改时间）
            withStyle(theme.metadataStyle) {
                append(trimmed.substring(0, metadataLength))
            }
            // 后半部文件名区
            withStyle(theme.getStyleForFilename(fileName, isDir)) {
                append(fileName)
            }
        }
    }

    /**
     * 格式化 find 分类筛选行：如 [目录] Movies  (-) 或 [文件] song.mp3  (3.5 MB)
     * 标签使用元数据灰蓝色彩，文件名按主题色彩渲染，附加大小信息使用元数据色彩
     */
    fun parseFindCategoryLine(
        lineText: String,
        theme: TerminalColorTheme = TerminalColorTheme.Default
    ): AnnotatedString {
        val trimmed = lineText.trim()
        val isDirTag = trimmed.startsWith("[目录]")
        val isFileTag = trimmed.startsWith("[文件]")
        if (!isDirTag && !isFileTag) {
            return AnnotatedString(lineText)
        }

        val tagEnd = 4 // "[目录]" 或 "[文件]" 占 4 字符
        val lastOpenParen = trimmed.lastIndexOf('(')

        return buildAnnotatedString {
            // 1. [目录] / [文件] 标签
            withStyle(theme.metadataStyle) {
                append(trimmed.substring(0, tagEnd))
            }

            // 2. 文件名与其前导空格
            if (lastOpenParen > tagEnd) {
                val nameSegment = trimmed.substring(tagEnd, lastOpenParen)
                val cleanName = nameSegment.trim()
                withStyle(theme.getStyleForFilename(cleanName, isDirTag)) {
                    append(nameSegment)
                }
                // 3. 附加信息（如大小 "(3.5 MB)"）
                withStyle(theme.metadataStyle) {
                    append(trimmed.substring(lastOpenParen))
                }
            } else {
                val nameSegment = trimmed.substring(tagEnd)
                val cleanName = nameSegment.trim()
                withStyle(theme.getStyleForFilename(cleanName, isDirTag)) {
                    append(nameSegment)
                }
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
                            30 -> color = TerminalColors.Ansi.Black
                            31 -> color = TerminalColors.Ansi.Red
                            32 -> color = TerminalColors.Ansi.Green
                            33 -> color = TerminalColors.Ansi.Yellow
                            34 -> color = TerminalColors.Ansi.Blue
                            35 -> color = TerminalColors.Ansi.Magenta
                            36 -> color = TerminalColors.Ansi.Cyan
                            37 -> color = TerminalColors.Ansi.White
                            39 -> color = theme.otherStyle.color // Default fg
                            90 -> color = TerminalColors.Ansi.BrightBlack
                            91 -> color = TerminalColors.Ansi.BrightRed
                            92 -> color = TerminalColors.Ansi.BrightGreen
                            93 -> color = TerminalColors.Ansi.BrightYellow
                            94 -> color = TerminalColors.Ansi.BrightBlue
                            95 -> color = TerminalColors.Ansi.BrightMagenta
                            96 -> color = TerminalColors.Ansi.BrightCyan
                            97 -> color = TerminalColors.Ansi.BrightWhite
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
}
