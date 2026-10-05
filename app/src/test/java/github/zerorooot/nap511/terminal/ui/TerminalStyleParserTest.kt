package github.zerorooot.nap511.terminal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalStyleParserTest {

    private val theme = TerminalColorTheme.Default

    @Test
    fun testColorThemeMapping() {
        // 1. 文件夹 (#FFA726, Bold)
        val folderStyle = theme.getStyleForFilename("Movies/", isDirectory = true)
        assertEquals(Color(0xFFFFA726), folderStyle.color)
        assertEquals(FontWeight.Bold, folderStyle.fontWeight)

        // 2. 视频 (#EF5350)
        val videoStyle = theme.getStyleForFilename("movie.mp4", isDirectory = false)
        assertEquals(Color(0xFFEF5350), videoStyle.color)

        // 3. 音频 (#FF7043)
        val audioStyle = theme.getStyleForFilename("song.flac", isDirectory = false)
        assertEquals(Color(0xFFFF7043), audioStyle.color)

        // 4. 图片 (#26A69A)
        val imageStyle = theme.getStyleForFilename("photo.png", isDirectory = false)
        assertEquals(Color(0xFF26A69A), imageStyle.color)

        // 5. 压缩包 (#8D6E63)
        val archiveStyle = theme.getStyleForFilename("archive.7z", isDirectory = false)
        assertEquals(Color(0xFF8D6E63), archiveStyle.color)

        // 6. 安装包 (#66BB6A, Normal 不加粗)
        val apkStyle = theme.getStyleForFilename("app.apk", isDirectory = false)
        assertEquals(Color(0xFF66BB6A), apkStyle.color)
        assertEquals(FontWeight.Normal, apkStyle.fontWeight)

        // 7. 可执行程序 (#5C6BC0, Normal 不加粗)
        val execStyle = theme.getStyleForFilename("script.sh", isDirectory = false)
        assertEquals(Color(0xFF5C6BC0), execStyle.color)
        assertEquals(FontWeight.Normal, execStyle.fontWeight)

        // 8. 文本/文档 (#42A5F5)
        val docStyle = theme.getStyleForFilename("readme.txt", isDirectory = false)
        assertEquals(Color(0xFF42A5F5), docStyle.color)

        // 9. 网页 (#00BCD4)
        val webStyle = theme.getStyleForFilename("index.html", isDirectory = false)
        assertEquals(Color(0xFF00BCD4), webStyle.color)

        // 10. ISO 镜像 (#7E57C2)
        val isoStyle = theme.getStyleForFilename("ubuntu.iso", isDirectory = false)
        assertEquals(Color(0xFF7E57C2), isoStyle.color)

        // 11. 种子 (#43A047)
        val torrentStyle = theme.getStyleForFilename("download.torrent", isDirectory = false)
        assertEquals(Color(0xFF43A047), torrentStyle.color)

        // 12. 其他文件 (#BABABA)
        val otherStyle = theme.getStyleForFilename("data.dat", isDirectory = false)
        assertEquals(Color(0xFFBABABA), otherStyle.color)
    }

    @Test
    fun testParseLongListingLine() {
        val folderLine = "drwxr-xr-x          - 2026-10-05 12:00 Movies/"
        val annotatedFolder = TerminalStyleParser.parseLongListingLine(folderLine, theme)
        assertNotNull(annotatedFolder)
        assertEquals(folderLine.length, annotatedFolder.text.length)
        assertTrue(annotatedFolder.spanStyles.isNotEmpty())

        val fileLine = "-rwxr-xr-x    1.2 MB 2026-10-05 12:05 video.mp4"
        val annotatedFile = TerminalStyleParser.parseLongListingLine(fileLine, theme)
        assertNotNull(annotatedFile)
        assertEquals(fileLine.length, annotatedFile.text.length)
        assertTrue(annotatedFile.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseAnsiText() {
        val ansiText = "\u001B[31mRed Text\u001B[0m Plain \u001B[1;36mBold Cyan\u001B[0m"
        val parsed = TerminalStyleParser.parseAnsiText(ansiText, theme)
        assertEquals("Red Text Plain Bold Cyan", parsed.text)
        assertTrue(parsed.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseFileEntryLine() {
        val folderParsed = TerminalStyleParser.parseFileEntryLine("Documents/", theme)
        assertEquals("Documents/", folderParsed.text)
        assertEquals(1, folderParsed.spanStyles.size)
        assertEquals(theme.folderStyle, folderParsed.spanStyles[0].item)

        val apkParsed = TerminalStyleParser.parseFileEntryLine("nap511.apk", theme)
        assertEquals("nap511.apk", apkParsed.text)
        assertEquals(theme.apkStyle, apkParsed.spanStyles[0].item)

        // 验证通过 parseLine 统一入口以 OUTPUT_FILE_ENTRY 类型分发的结果一致
        val viaParseLine = TerminalStyleParser.parseLine("nap511.apk", TerminalLineType.OUTPUT_FILE_ENTRY, theme)
        assertEquals(apkParsed.spanStyles, viaParseLine.spanStyles)
    }

    @Test
    fun testParseMultiLineHelpOutputConsistentColor() {
        val helpText = """
            可用命令列表（在命令后添加 -h 可查看详细参数）：
            --------------------------------------------------
            cat            : 查看文件内容
            --------------------------------------------------
            快捷键指南（悬浮栏点亮 CTRL / ALT 或连接物理键盘）：
              Ctrl + A / E    光标跳到行首 / 行尾
              Alt + B / F     光标按单词向左 / 向右跳跃
        """.trimIndent()

        // 帮助文档行通过 TerminalLineType.HELP 显式类型分发，零跨行正则开销，不产生杂乱 SpanStyle
        val parsed = TerminalStyleParser.parseLine(helpText, TerminalLineType.HELP, theme)
        assertEquals(helpText, parsed.text)
        assertTrue("HELP 类型纯文本由屏幕层统一着色，无需内部附加多余 spanStyles", parsed.spanStyles.isEmpty())
    }

    @Test
    fun testOptionDescriptionLinesNotMisparsedAsPath() {
        val testLines = listOf(
            "  -filter <type> 按 115 业务分类筛选：1|doc(文档), 2|img(图片), 3|audio(音频), 4|video(视频), 5|zip(压缩), 6|app(软件)",
            "  -u           按文件访问/打开时间排序",
            "  -c           仅统计字符/字节数",
            "--------------------------------------------------",
            "命令描述: 重命名文件或将文件/目录移动至其他目录"
        )

        testLines.forEach { line ->
            // 在类型化架构中，帮助行与普通文本行显式分发，绝不会走路径高亮器，杜绝 metadataStyle 误染
            val parsedHelp = TerminalStyleParser.parseLine(line, TerminalLineType.HELP, theme)
            assertFalse(
                "HELP 类型的行 [$line] 不应包含 metadataStyle 路径样式",
                parsedHelp.spanStyles.any { it.item == theme.metadataStyle }
            )

            val parsedText = TerminalStyleParser.parseLine(line, TerminalLineType.OUTPUT_TEXT, theme)
            assertFalse(
                "OUTPUT_TEXT 类型的行 [$line] 不应包含 metadataStyle 路径样式",
                parsedText.spanStyles.any { it.item == theme.metadataStyle }
            )
        }
    }

    @Test
    fun testGenuineMultiLevelPathParsedCorrectly() {
        val pathLine = "/Movies/action/007.mp4"
        val parsed = TerminalStyleParser.parsePathEntryLine(pathLine, theme)
        assertEquals(pathLine, parsed.text)

        // 验证多级路径正常拆分：前缀使用 metadataStyle，文件名使用 videoStyle
        assertEquals(2, parsed.spanStyles.size)
        assertEquals(theme.metadataStyle, parsed.spanStyles[0].item)
        assertEquals("/Movies/action/", pathLine.substring(parsed.spanStyles[0].start, parsed.spanStyles[0].end))
        assertEquals(theme.videoStyle, parsed.spanStyles[1].item)
        assertEquals("007.mp4", pathLine.substring(parsed.spanStyles[1].start, parsed.spanStyles[1].end))

        // 同时验证通过 parseLine 统一入口以 TerminalLineType.OUTPUT_PATH_ENTRY 分发
        val viaParseLine = TerminalStyleParser.parseLine(pathLine, TerminalLineType.OUTPUT_PATH_ENTRY, theme)
        assertEquals(parsed.spanStyles, viaParseLine.spanStyles)
    }

    @Test
    fun testParseFindCategoryLine() {
        val dirCategoryLine = "[目录] Movies  (-)"
        val parsedDir = TerminalStyleParser.parseLine(dirCategoryLine, TerminalLineType.OUTPUT_FIND_CATEGORY, theme)
        assertEquals(dirCategoryLine, parsedDir.text)
        assertTrue(parsedDir.spanStyles.isNotEmpty())
        assertEquals(theme.metadataStyle, parsedDir.spanStyles[0].item)
        assertEquals(theme.folderStyle, parsedDir.spanStyles[1].item)

        val fileCategoryLine = "[文件] song.mp3  (3.5 MB)"
        val parsedFile = TerminalStyleParser.parseLine(fileCategoryLine, TerminalLineType.OUTPUT_FIND_CATEGORY, theme)
        assertEquals(fileCategoryLine, parsedFile.text)
        assertEquals(theme.metadataStyle, parsedFile.spanStyles[0].item)
        assertEquals(theme.audioStyle, parsedFile.spanStyles[1].item)
    }

    @Test
    fun testParseLineDirectOutputTextZeroOverhead() {
        val plainText = "total 42 items calculated"
        val types = listOf(
            TerminalLineType.OUTPUT_TEXT,
            TerminalLineType.SYSTEM,
            TerminalLineType.COMMAND,
            TerminalLineType.ERROR,
            TerminalLineType.PROMPT
        )

        for (type in types) {
            val parsed = TerminalStyleParser.parseLine(plainText, type, theme)
            assertEquals(plainText, parsed.text)
            assertTrue("Type $type 必须实现零开销直通，不得附加任何 SpanStyle", parsed.spanStyles.isEmpty())
        }
    }

    @Test
    fun testParseLineAnsiDispatch() {
        val ansiText = "\u001B[32mSuccess\u001B[0m"
        val parsed = TerminalStyleParser.parseLine(ansiText, TerminalLineType.OUTPUT_ANSI, theme)
        assertEquals("Success", parsed.text)
        assertEquals(1, parsed.spanStyles.size)
        assertEquals(TerminalColors.Ansi.Green, parsed.spanStyles[0].item.color)
    }

    @Test
    fun testParseLineMultilinePreservesLineBreaksAndStyles() {
        val multiline = "video.mp4\nsong.flac\npicture.png"
        val parsed = TerminalStyleParser.parseLine(multiline, TerminalLineType.OUTPUT_FILE_ENTRY, theme)
        assertEquals(multiline, parsed.text)
        // 3 行各自拥有对应的高亮样式
        assertEquals(3, parsed.spanStyles.size)
        assertEquals(theme.videoStyle, parsed.spanStyles[0].item)
        assertEquals(theme.audioStyle, parsed.spanStyles[1].item)
        assertEquals(theme.imageStyle, parsed.spanStyles[2].item)
    }

    @Test
    fun testParseEdgeCasesEmptyAndIncomplete() {
        // 空白行处理：原样保留文本且不施加额外样式
        val emptyFile = TerminalStyleParser.parseFileEntryLine("  ", theme)
        assertEquals("  ", emptyFile.text)
        assertTrue(emptyFile.spanStyles.isEmpty())

        val emptyPath = TerminalStyleParser.parsePathEntryLine("", theme)
        assertEquals("", emptyPath.text)
        assertTrue(emptyPath.spanStyles.isEmpty())

        val emptyLong = TerminalStyleParser.parseLongListingLine(" ", theme)
        assertEquals(" ", emptyLong.text)
        assertTrue(emptyLong.spanStyles.isEmpty())

        val emptyCat = TerminalStyleParser.parseFindCategoryLine("", theme)
        assertEquals("", emptyCat.text)
        assertTrue(emptyCat.spanStyles.isEmpty())

        // parseLongListingLine 列数不足兜底返回原文本
        val shortListing = "-rwxr-xr-x only_two_columns"
        val parsedShort = TerminalStyleParser.parseLongListingLine(shortListing, theme)
        assertEquals(shortListing, parsedShort.text)
        assertTrue(parsedShort.spanStyles.isEmpty())

        // parsePathEntryLine 不含斜杠兜底按纯文件名高亮
        val singleFileAsPath = "standalone_movie.mp4"
        val parsedSingle = TerminalStyleParser.parsePathEntryLine(singleFileAsPath, theme)
        assertEquals(singleFileAsPath, parsedSingle.text)
        assertEquals(1, parsedSingle.spanStyles.size)
        assertEquals(theme.videoStyle, parsedSingle.spanStyles[0].item)

        // parseFindCategoryLine 非 [目录] / [文件] 标签兜底返回原文本
        val unmatchedCategory = "Normal text without tag"
        val parsedUnmatched = TerminalStyleParser.parseFindCategoryLine(unmatchedCategory, theme)
        assertEquals(unmatchedCategory, parsedUnmatched.text)
        assertTrue(parsedUnmatched.spanStyles.isEmpty())
    }
}
